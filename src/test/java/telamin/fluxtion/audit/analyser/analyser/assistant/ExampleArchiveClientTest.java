package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M74 P2b: the example fetch — https, allowed hosts, a pinned commit through redirects, a cap, the digest computed. */
class ExampleArchiveClientTest {

    static final String C = ExampleSourceTest.C;

    static ExampleSource github() {
        return ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip", null).source();
    }

    @Test
    @DisplayName("GitHub's redirect to codeload is followed; the bytes and their SHA-256 come back")
    void followsTheCodeloadRedirect() {
        List<URI> asked = new ArrayList<>();
        byte[] body = "PK-demo".getBytes();
        var client = new ExampleArchiveClient((uri, max) -> {
            asked.add(uri);
            return uri.getHost().equals("github.com")
                    ? new ExampleArchiveClient.Reply(302, "https://codeload.github.com/example/demo/zip/" + C, new byte[0])
                    : new ExampleArchiveClient.Reply(200, null, body);
        });
        var f = client.fetch(github(), 1024);
        assertTrue(f.ok(), f.failure());
        assertArrayEquals(body, f.bytes());
        assertEquals(ExampleArchiveClient.sha256(body), f.sha256());
        assertEquals(2, asked.size());
    }

    /** ★ Witness: follow every redirect (drop mayRedirectTo) — the evil host is contacted. */
    @Test
    @DisplayName("★ a redirect off the allowed hosts, or to another ref, is not followed and nothing is fetched from it")
    void aRedirectElsewhereIsRefused() {
        for (String location : new String[]{"https://objects.evil.example/" + C,
                "https://codeload.github.com/example/demo/zip/refs/heads/main"}) {
            List<URI> asked = new ArrayList<>();
            var client = new ExampleArchiveClient((uri, max) -> {
                asked.add(uri);
                return new ExampleArchiveClient.Reply(302, location, new byte[0]);
            });
            var f = client.fetch(github(), 1024);
            assertFalse(f.ok());
            assertTrue(f.failure().contains("not followed"), f.failure());
            assertEquals(1, asked.size(), "only the pinned address was asked: " + asked);
        }
    }

    @Test
    @DisplayName("over the cap is refused with the cap named; a server error is named")
    void capsAndErrors() {
        var tooBig = new ExampleArchiveClient((uri, max) -> {
            throw new ExampleArchiveClient.TooLarge();
        });
        assertTrue(tooBig.fetch(github(), 2L * 1024 * 1024).failure().contains("over 2 MB"));
        var notFound = new ExampleArchiveClient((uri, max) -> new ExampleArchiveClient.Reply(404, null, new byte[0]));
        assertEquals("the server answered 404", notFound.fetch(github(), 1024).failure());
    }

    @Test
    @DisplayName("readCapped stops at the cap")
    void readCapped() {
        assertThrows(ExampleArchiveClient.TooLarge.class,
                () -> ExampleArchiveClient.readCapped(new java.io.ByteArrayInputStream(new byte[100]), 99));
    }
}
