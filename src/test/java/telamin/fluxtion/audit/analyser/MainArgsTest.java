package telamin.fluxtion.audit.analyser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Command-line argument handling. Headless-testable because it is pure string logic — the Swing bootstrap
 * it guards is not reached.
 */
class MainArgsTest {

    @Test
    void aLeadingDashIsAnOptionNotALogFile() {
        // the regression this guards: an older build treated --mcp as a filename and launched the GUI
        // trying to open a file called "--mcp"
        assertTrue(Main.looksLikeFlag("--mcp"));
        assertTrue(Main.looksLikeFlag("--verbose"));
        assertTrue(Main.looksLikeFlag("-x"));
    }

    @Test
    void ordinaryPathsAreNotFlags() {
        assertFalse(Main.looksLikeFlag("sample.yml"));
        assertFalse(Main.looksLikeFlag("/var/log/audit.yaml"));
        assertFalse(Main.looksLikeFlag("./run-1.yml"));
        assertFalse(Main.looksLikeFlag("s3://bucket/key"));
        assertFalse(Main.looksLikeFlag("-"), "a bare dash is not a flag");
    }

    @Test
    void helpIsRecognisedBothWays() {
        assertTrue(Main.isHelpFlag("--help"));
        assertTrue(Main.isHelpFlag("-h"));
        assertFalse(Main.isHelpFlag("--mcp"));
        assertFalse(Main.isHelpFlag("help"));
    }

    @Test
    void usageNamesEveryLaunchMode() {
        String usage = Main.usage();
        assertTrue(usage.contains(Main.MCP_FLAG), "the MCP mode is discoverable from --help");
        assertTrue(usage.contains("--help"));
        assertTrue(usage.contains("log-file"));
        assertTrue(usage.contains("REST transport"), "says the app must be running for --mcp to be useful");
    }
}
