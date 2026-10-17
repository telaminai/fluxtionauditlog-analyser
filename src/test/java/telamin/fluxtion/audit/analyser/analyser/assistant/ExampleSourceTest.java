package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/** M74 P2b (owner request 2026-10-17): the example entry's source — a pinned commit's archive on an allowed host. */
class ExampleSourceTest {

    static final String C = "0123456789abcdef0123456789abcdef01234567";

    @Test
    @DisplayName("the accepted shapes: github /archive/<commit>.zip, codeload /zip/<commit>, gitlab /-/archive/<commit>/…")
    void acceptedShapes() {
        var g = ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip", null).source();
        assertEquals("github.com", g.host());
        assertEquals("example", g.owner());
        assertEquals("demo", g.repo());
        assertEquals(C, g.commit());
        assertEquals("github.com / example / demo @ 0123456", g.publisher());
        assertNotNull(ExampleSource.parse("https://codeload.github.com/example/demo/zip/" + C, null).source());
        assertNotNull(ExampleSource.parse("https://gitlab.com/example/demo/-/archive/" + C + "/demo-" + C + ".zip", null).source());
        var withDigest = ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip",
                "A".repeat(64)).source();
        assertEquals("a".repeat(64), withDigest.sha256(), "kept, lower-cased");
    }

    /** ★ Witness: accept any ref (drop the HEX40 check) — every moving ref is accepted. */
    @Test
    @DisplayName("★ a moving ref — a branch, a tag, a short commit — is refused: only a 40-hex commit")
    void movingRefsRefused() {
        for (String ref : new String[]{"main", "v1.2.0", "0123456", "refs/heads/main", C + "0"}) {
            var p = ExampleSource.parse("https://github.com/example/demo/archive/" + ref + ".zip", null);
            assertEquals(ExampleSource.Refusal.MOVING_REF, p.refusal(), ref);
        }
    }

    /** ★ Witness: drop the ALLOWED_HOSTS check — evil.example is accepted. */
    @Test
    @DisplayName("★ a host off the allowed list, plain http, user info, a port, a query or a fragment is refused")
    void hostsAndShapesRefused() {
        assertEquals(ExampleSource.Refusal.HOST_NOT_ALLOWED,
                ExampleSource.parse("https://evil.example/example/demo/archive/" + C + ".zip", null).refusal());
        assertEquals(ExampleSource.Refusal.HOST_NOT_ALLOWED,
                ExampleSource.parse("https://github.com.evil.example/example/demo/archive/" + C + ".zip", null).refusal());
        assertEquals(ExampleSource.Refusal.NOT_HTTPS,
                ExampleSource.parse("http://github.com/example/demo/archive/" + C + ".zip", null).refusal());
        assertEquals(ExampleSource.Refusal.USER_INFO_OR_PORT,
                ExampleSource.parse("https://u:p@github.com/example/demo/archive/" + C + ".zip", null).refusal());
        assertEquals(ExampleSource.Refusal.USER_INFO_OR_PORT,
                ExampleSource.parse("https://github.com:8443/example/demo/archive/" + C + ".zip", null).refusal());
        assertEquals(ExampleSource.Refusal.QUERY_OR_FRAGMENT,
                ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip?x=1", null).refusal());
        assertEquals(ExampleSource.Refusal.NOT_AN_ARCHIVE,
                ExampleSource.parse("https://github.com/example/demo/blob/" + C + "/README.md", null).refusal());
        assertEquals(ExampleSource.Refusal.BAD_DIGEST,
                ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip", "abc").refusal());
    }

    /** ★ Witness: thirdParty() always false — the third-party assertion fails. */
    @Test
    @DisplayName("★ anything not from the Telamin GitHub organisation is third-party; nothing is ever 'validated'")
    void thirdPartyLabel() {
        assertTrue(ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip", null).source().thirdParty());
        assertTrue(ExampleSource.parse("https://gitlab.com/telaminai/demo/-/archive/" + C + "/demo-" + C + ".zip", null)
                .source().thirdParty(), "Telamin's org is on GitHub; the same name elsewhere is someone else");
        var telamin = ExampleSource.parse("https://github.com/telaminai/fluxtion-examples/archive/" + C + ".zip", null).source();
        assertFalse(telamin.thirdParty());
        assertEquals("Telamin", telamin.publisherVerdict());
        assertEquals("third-party", ExampleSource.parse("https://github.com/x/y/archive/" + C + ".zip", null).source()
                .publisherVerdict());
    }

    @Test
    @DisplayName("a redirect is followed only to an allowed https host naming the same commit")
    void redirects() {
        var s = ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip", null).source();
        assertTrue(s.mayRedirectTo(URI.create("https://codeload.github.com/example/demo/zip/" + C)));
        assertFalse(s.mayRedirectTo(URI.create("https://codeload.github.com/example/demo/zip/refs/heads/main")));
        assertFalse(s.mayRedirectTo(URI.create("https://objects.evil.example/" + C)));
        assertFalse(s.mayRedirectTo(URI.create("http://codeload.github.com/example/demo/zip/" + C)));
    }
}
