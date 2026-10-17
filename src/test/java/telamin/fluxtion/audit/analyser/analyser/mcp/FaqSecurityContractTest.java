package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // M74 P1a (spec-claude-code-provider.md §7): what Start does, the default posture's limits, and that the analyser
        // runs the person's own CLI and never touches its credentials
        for (String claim : new String[]{"Allow the analyser to start Claude Code", "your own installed `claude`",
                "never reads or stores your Claude Code credentials", "before your first Send",
                "no record content is sent until you press Send", "Read, Grep and Glob confined to the folder",
                "no Bash", "none of your skills, hooks, settings or MCP servers",
                "never treated as Claude Code's", "cannot delete a chart", "a bundle's working copy is refused"}) {
            assertTrue(answer.contains(claim), "the FAQ security answer must state, for Claude Code: " + claim);
        }
        // M74 P1b (spec-claude-code-provider.md §7): the full posture is a separate switch, and the FAQ says what it runs,
        // what it may use, that the analyser does not confine it, that bypassPermissions is never offered and that only the
        // Start button starts it (warm start cut, OD-74.25); the preview label went with P3 (OD-74.26, r12)
        for (String claim : new String[]{"Full Claude Code is a separate switch with its own warning",
                "your settings, skills, hooks and MCP servers", "the folder's hooks and MCP servers run when you press Start",
                "asks again if those files change", "your own tools, including Bash",
                "as far as your permission rules and the mode you chose allow",
                "the analyser does not confine it to the folder", "bypassPermissions is never offered",
                "Start button starts full Claude Code"}) {
            assertTrue(answer.contains(claim), "the FAQ security answer must state, for full Claude Code: " + claim);
        }
        assertFalse(answer.contains("(preview)") || answer.contains("not for customer release"),
                "P3 shipped: the full posture is no longer called a preview (OD-74.26, r12)");
        // M74 P3 (§7): the analyser asks before Claude Code runs a verb that changes what is open or writes files, an
        // approval covers one call, Deny is the default, and a call the person's own rules or hooks approve is not asked
        for (String claim : new String[]{"before Claude Code runs any of these verbs the analyser asks you",
                "an approval covers one call", "Deny is the default",
                "a call your own rules or hooks approve is not asked in the analyser"}) {
            assertTrue(answer.toLowerCase(java.util.Locale.ROOT).contains(claim.toLowerCase(java.util.Locale.ROOT)),
                    "the FAQ security answer must state, for approvals in the analyser: " + claim);
        }
        for (String verb : McpTools.destructiveVerbs()) {
            assertTrue(answer.contains("`" + verb + "`"),
                    "destructive verb '" + verb + "' is not named in the FAQ's security answer — "
                            + "document what it can touch (docs/site/faq.md) in the same change that adds it");
        }
    }
}
