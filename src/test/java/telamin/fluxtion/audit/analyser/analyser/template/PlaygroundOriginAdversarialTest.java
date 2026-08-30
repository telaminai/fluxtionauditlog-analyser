package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Review-added attacks on D-AX10's origin override. The existing suite covers the three loopback
 * spellings and proves plain http is refused elsewhere; these are the forms an attacker reaches for
 * when a loopback check is written as a string comparison — a host that merely LOOKS like loopback,
 * and the numeric encodings of 127.0.0.1 that every browser accepts.
 *
 * <p>String equality is the conservative choice here and these confirm it: a decimal or octal
 * encoding is refused rather than resolved, so the rule cannot be widened by an encoding trick.
 */
class PlaygroundOriginAdversarialTest {

    @AfterEach
    void clear() {
        System.clearProperty(TemplateClient.ORIGIN_PROPERTY);
    }

    private static void set(String value) {
        System.setProperty(TemplateClient.ORIGIN_PROPERTY, value);
    }

    /** The classic: a host that merely begins with a loopback literal but resolves anywhere. */
    @Test
    void aHostThatOnlyLOOKSLikeLoopbackOverPlainHttpIsRefused() {
        for (String host : new String[]{
                "127.0.0.1.evil.example",
                "localhost.evil.example",
                "evil.example#127.0.0.1",
                "notlocalhost",
                "localhost.",
                "127.0.0.11"
        }) {
            set("http://" + host);
            assertThrows(RuntimeException.class, TemplateClient::configuredOrigin,
                    "plain http must be refused for " + host);
        }
    }

    /**
     * Numeric encodings of 127.0.0.1 that a browser or curl will happily dial. A loopback check that
     * parsed the address instead of comparing the string would accept these, and each is a
     * different-looking origin to a reader auditing a command line.
     */
    @Test
    void numericAndOctalEncodingsOfLoopbackAreRefusedRatherThanResolved() {
        for (String host : new String[]{"2130706433", "0x7f000001", "127.1", "0177.0.0.1", "0.0.0.0"}) {
            set("http://" + host);
            assertThrows(RuntimeException.class, TemplateClient::configuredOrigin,
                    "encoded loopback must not be accepted over plain http: " + host);
        }
    }

    /** A path, query, fragment or user-info turns an "origin" into a location with a payload. */
    @Test
    void anythingBeyondABareOriginIsRefusedEvenOnLoopback() {
        for (String url : new String[]{
                "http://localhost/starter-templates",
                "http://localhost?x=1",
                "http://localhost#f",
                "http://user@localhost",
                "http://user:pw@localhost",
                "http://localhost/../etc"
        }) {
            set(url);
            assertThrows(RuntimeException.class, TemplateClient::configuredOrigin,
                    "not a bare origin: " + url);
        }
    }

    /** The accepted forms still are accepted, and normalise to one trailing-slash shape. */
    @Test
    void theAcceptedLoopbackFormsNormaliseIdentically() {
        for (String url : new String[]{"http://localhost:8080", "http://localhost:8080/"}) {
            set(url);
            assertEquals("http://localhost:8080/", TemplateClient.configuredOrigin().toString());
        }
        set("http://LOCALHOST:8080"); // scheme/host case is not a security boundary
        assertEquals("http://LOCALHOST:8080/", TemplateClient.configuredOrigin().toString());
    }

    /** With no property the shipped origin stands — the override must be opt-in, never a default. */
    @Test
    void theShippedOriginIsUnchangedWithoutTheProperty() {
        System.clearProperty(TemplateClient.ORIGIN_PROPERTY);
        assertEquals(TemplateClient.PLAYGROUND, TemplateClient.configuredOrigin());
    }
}
