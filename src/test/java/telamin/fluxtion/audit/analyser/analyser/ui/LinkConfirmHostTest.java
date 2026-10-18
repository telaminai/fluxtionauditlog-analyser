package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review PR #150 R1: the confirmation's "Scheme and host" line is the host a browser reaches. A browser reads
 * {@code https:///evil.example} as {@code https://evil.example} and decodes a percent-encoded host, while the hand-parsed
 * line showed an empty host or the encoded form — so neither shape is offered at all.
 */
class LinkConfirmHostTest {

    /** ★ Witness: drop the host check in {@link LinkConfirm#refusal} — these links are offered and this fails. */
    @Test
    @DisplayName("★ a link whose host line would be empty or percent-encoded is never offered")
    void noPlainHostIsRefused() {
        for (String url : List.of("https:///evil.example/x", "https:////evil.example", "https://%65vil.example/",
                "https://%D0%B0pple.example", "https://:443/x", "https://user@/x", "https://user@:443/x",
                "https://[fe80::1%25en0]/")) {
            assertNotNull(LinkConfirm.refusal(url), url);
            List<URI> opened = new ArrayList<>();
            String said = LinkConfirm.click(null, url, (p, m) -> true, opened::add);
            assertTrue(said.contains("no plain host"), url + " -> " + said);
            assertEquals(0, opened.size(), url + " is never opened");
        }
        // the host line is read as a browser reads it: the extra slash is skipped, never an empty host
        assertEquals("https://evil.example", LinkConfirm.origin("https:///evil.example/x"));
    }

    @Test
    @DisplayName("an ordinary, IDN or user-info link is still offered, its host shown")
    void plainHostsStillOffered() {
        assertNull(LinkConfirm.refusal("https://docs.example/a?b"));
        assertNull(LinkConfirm.refusal("https://\u0430pple.example/x"));
        assertNull(LinkConfirm.refusal("https://u:p@proxy.example:8443/v1"));
        assertNull(LinkConfirm.refusal("https://u%40x:p@proxy.example/v1"), "a percent in the user info is not the host");
        assertNull(LinkConfirm.refusal("https://[::1]:8443/"));
        assertNull(LinkConfirm.refusal("https://docs.example"));
        assertEquals("https://proxy.example:8443", LinkConfirm.origin("https://u:p@proxy.example:8443/v1"));
    }
}
