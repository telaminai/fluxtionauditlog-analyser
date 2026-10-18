package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review PR #150 R5: the client checks the scheme and host of every hop itself, not only through
 * {@link ExampleSource#parse}. {@code ExampleSource} is a public record, so a source can be built without parsing; the
 * client's own check is then the only guard, and it had no witness (removing it left every test green).
 */
class ExampleArchiveClientHostGuardTest {

    private static final String COMMIT = "0123456789012345678901234567890123456789";

    /** ★ Witness: delete the per-hop host check in fetch — the transport is called for evil.example and this fails. */
    @Test
    @DisplayName("★ a source that was never parsed still cannot reach a host off the allow-list")
    void unparsedSourceCannotReachAnotherHost() {
        for (String url : List.of("https://evil.example/o/r/archive/" + COMMIT + ".zip",
                "http://github.com/o/r/archive/" + COMMIT + ".zip")) {
            List<URI> contacted = new ArrayList<>();
            ExampleArchiveClient client = new ExampleArchiveClient((uri, max) -> {
                contacted.add(uri);
                return new ExampleArchiveClient.Reply(200, null, new byte[]{1});
            });
            var hand = new ExampleSource(url, "github.com", "o", "r", COMMIT, null);
            var fetched = client.fetch(hand, 1024);
            assertFalse(fetched.ok(), url);
            assertTrue(fetched.failure().startsWith("refused to contact"), fetched.failure());
            assertEquals(List.of(), contacted, "nothing is contacted for " + url);
        }
    }
}
