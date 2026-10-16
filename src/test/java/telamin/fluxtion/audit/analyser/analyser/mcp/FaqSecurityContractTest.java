package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The governance rule from review_handoff_16_aug_2026 D2: any verb marked destructive to MCP clients
 * MUST be named in the FAQ's security answer — the sentence a security evaluator reads. This test makes
 * the security answer mechanically un-forgettable: add a mutating/file-writing verb without documenting
 * it there and the build fails.
 */
class FaqSecurityContractTest {

    @Test
    void everyDestructiveVerbIsNamedInTheFaqSecurityAnswer() throws Exception {
        String faq = Files.readString(Path.of("docs/site/faq.md"));
        int start = faq.indexOf("## Is the assistant's action socket safe to enable?");
        assertTrue(start >= 0, "the FAQ security answer heading moved or was removed — update this test "
                + "AND make sure the answer still exists; it is the user-facing security contract");
        int end = faq.indexOf("\n## ", start + 1);
        String answer = (end > 0 ? faq.substring(start, end) : faq.substring(start))
                .replaceAll("\\s+", " ");   // prose reflows; the CONTRACT must survive a line-wrap
        // M29 D-F4 (review F1): the read rule is part of the security contract — external reads share
        // the write opt-in and its directory, and the FAQ must keep saying so
        assertTrue(answer.contains("read only from that same directory")
                        || answer.contains("reads are confined"),
                "the FAQ security answer no longer states the external-read confinement rule");
        // M31 D-P3: the plugin trust boundary is part of the security contract
        assertTrue(answer.contains("arbitrary code execution"),
                "the FAQ security answer must state the plugin trust boundary in plain words");
        assertTrue(answer.contains("cannot add verbs"),
                "…and that plugins can never widen the verb surface");
        // M73 (spec-create-project.md §5): creating a project — the switch, every write, and the preview's read reach
        assertTrue(answer.contains("Allow the assistant to create projects"),
                "the FAQ security answer must name the switch that gates an assistant's create");
        assertTrue(answer.contains("never in a project profile, a shared settings file or an evidence bundle"),
                "…and that it is machine-only");
        for (String write : new String[]{"<dir>/.analyser/project.fluxtion-settings", "<dir>/CLAUDE.md",
                "pending edits", "recent-projects", "activated at the next start", "session-recovery journal"}) {
            assertTrue(answer.contains(write), "the FAQ security answer must state every write a create causes: " + write);
        }
        assertTrue(answer.contains("skills to depth 7") && answer.contains("GraphML to depth 12")
                        && answer.contains("32 MB"),
                "…and the preview's read reach");
        for (String verb : McpTools.destructiveVerbs()) {
            assertTrue(answer.contains("`" + verb + "`"),
                    "destructive verb '" + verb + "' is not named in the FAQ's security answer — "
                            + "document what it can touch (docs/site/faq.md) in the same change that adds it");
        }
    }
}
