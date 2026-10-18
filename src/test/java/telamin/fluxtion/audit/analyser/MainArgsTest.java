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
        String usage = Main.usage().replaceAll("\\s+", " ");
        assertTrue(usage.contains(Main.MCP_FLAG), "the MCP mode is discoverable from --help");
        assertTrue(usage.contains("--help"));
        assertTrue(usage.contains("log-file"));
        assertTrue(usage.contains("running separately with External MCP/REST access on"),
                "says the app must be running for --mcp to be useful");
    }

    /**
     * ★ P4 (docs-IA product findings): {@code --help} names the switch as Settings does, and names every field the
     * replay comparison excepts. Witnesses: put back "the REST transport" — the first assertion fails; add a field to
     * {@code ReplayCompare.EXCEPTED_FIELDS} (or put back "every record exact but endTime") — the loop fails.
     */
    @Test
    void usageNamesTheSwitchAndEveryExceptedField() {
        String usage = Main.usage().replaceAll("\\s+", " ");
        assertFalse(usage.contains("REST transport"), "one name for the switch: " + usage);
        assertTrue(usage.contains("with External MCP/REST access ON"), usage);
        assertTrue(usage.contains("External MCP/REST access on: Settings ▸ Assistant"), usage);
        assertEquals(java.util.List.of("endTime", "thread"),
                telamin.fluxtion.audit.analyser.bundle.ReplayCompare.EXCEPTED_FIELDS, "change the help with the rule");
        assertTrue(usage.contains("every record exact except its endTime and thread lines"), usage);
        for (String field : telamin.fluxtion.audit.analyser.bundle.ReplayCompare.EXCEPTED_FIELDS) {
            assertTrue(usage.contains(field), "--help must name the excepted field " + field);
        }
        assertFalse(usage.contains("exact but endTime"), usage);
    }
}
