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
                // owner 2026-10-18: the analyser's standing instruction, said as what it is
                "it adds one standing instruction", "a request to the model, not a sandbox",
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
        // Docs audit 2026-10-17 §4 item 8: claims the substring pins above could not see were wrong — walk "writes no
        // file", "nothing outside the loaded log" though source rereads the source roots, no word on propose, a control
        // called "Profile…" that no longer exists. Witness: restore any of those sentences; its assertion names it.
        assertTrue(answer.contains("`walk {reel}`") && answer.contains("writes a walk as an HTML page"),
                "the FAQ must say walk {reel} writes a file");
        assertFalse(answer.contains("writes no file of its own"), "walk {reel} writes a file");
        assertTrue(answer.contains("`source` can reread the XML and Java files **under the source roots you configured**"),
                "the FAQ must say source reads beyond the loaded log, and where");
        assertFalse(answer.contains("nothing outside the loaded log"), "source reads the source roots");
        assertTrue(answer.contains("`propose` is offered only to Claude Code in the Assistant tab")
                        && answer.contains("never on `/manifest`") && answer.contains("**changes nothing**"),
                "the FAQ must say what propose is and who is offered it");
        assertTrue(answer.contains("**Assistant: … ▾**"), "the header control's real label");
        assertFalse(answer.contains("Profile…"), "Profile… was replaced by Assistant: <profile> ▾");
        assertTrue(answer.contains("*External MCP/REST access*"), "the REST switch's one name");
        assertFalse(answer.contains("REST transport that lets"), "the switch is External MCP/REST access");
        assertTrue(answer.contains("Claude Code does not need External MCP/REST access on"));
        String whole = faq.replaceAll("\\s+", " ");
        assertFalse(whole.contains("local transport") || whole.contains("Assistant / LLM"), "the FAQ's old switch and tab names");
        int offline = whole.indexOf("## Does it work offline?");
        String offlineAnswer = whole.substring(offline, whole.indexOf(" ## ", offline + 1));
        assertTrue(offlineAnswer.contains("Claude Code") && offlineAnswer.contains("contacts Anthropic"),
                "offline: Start contacts Anthropic");
        for (String verb : McpTools.destructiveVerbs()) {
            assertTrue(answer.contains("`" + verb + "`"),
                    "destructive verb '" + verb + "' is not named in the FAQ's security answer — "
                            + "document what it can touch (docs/site/faq.md) in the same change that adds it");
        }
    }

    /**
     * Review P3+UX S3 and holistic B6 (2026-10-17): the claims P3 made untrue are gone from every surface that made them,
     * and what replaced them is pinned. Witness: put back the old Safe card line ("Claude Code reads the evidence only")
     * — the first assertion and the scan both fail.
     */
    @Test
    void theProfileClaimsSayWhatP3MadeTrue() throws Exception {
        String safe = "reads your evidence; it changes what is open or writes a file only after you approve each call";
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.ProfileOverlay.SAFE_CARD_LINE.contains(safe),
                "the Safe analysis card");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.SAFE_LIMITS.contains(safe),
                "the Safe analysis limits line");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.ProfileOverlay.FULL_CARD_LINE
                .contains("When Claude Code asks for approval, the analyser asks you"), "the Full Claude Code card");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.ProfileOverlay.OFF_CARD_LINE
                .startsWith("No assistant in the analyser"), "Off is about the assistant in the analyser (B6)");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.ProfileOverlay.OFF_CARD_LINE
                .contains("external MCP and REST clients are not affected"), "…and says the external loop is apart");
        String changelog = Files.readString(Path.of("CHANGELOG.md"));
        String unreleased = changelog.substring(changelog.indexOf("## [Unreleased]"), changelog.indexOf("\n## [", changelog
                .indexOf("## [Unreleased]") + 1));
        String guide = Files.readString(Path.of("docs/site/user-guide/assistant.md"));
        assertTrue(guide.replaceAll("\\s+", " ").contains(safe), "the guide's Safe analysis row");
        String ui = "src/main/java/telamin/fluxtion/audit/analyser/analyser/";
        java.util.Map<String, String> surfaces = new java.util.LinkedHashMap<>();
        surfaces.put("CHANGELOG [Unreleased]", unreleased);
        surfaces.put("assistant.md", guide);
        for (String f : new String[]{"ui/ProfileOverlay.java", "ui/LocalAgentPanel.java", "ui/ConfigPanel.java",
                "session/AssistantProfile.java"}) {
            surfaces.put(f, Files.readString(Path.of(ui + f)));
        }
        for (var e : surfaces.entrySet()) {
            String text = e.getValue().replaceAll("\\s+", " ").replace("\" + \"", "");
            for (String untrue : new String[]{"reads the evidence only", "Safe analysis reads only",
                    "approve each action in the analyser", "denied until approvals", "non-destructive verbs"}) {
                assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains(untrue.toLowerCase(java.util.Locale.ROOT)),
                        e.getKey() + " still says \"" + untrue + "\", which P3 made untrue");
            }
        }
    }

    /**
     * External utility review (owner-approved 2026-10-17): "Safe analysis" is not local-only, and every surface that
     * describes a profile says where your words and evidence go. Witness: drop the not-local-only bullet from the FAQ's
     * security answer, or the provider sentence from the Safe card's details — the assertion that names it fails.
     */
    @Test
    void noProfileClaimsToBeLocalOnly() throws Exception {
        String cc = telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CLAUDE_CODE_SENDS;
        String inApp = telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.IN_APP_SENDS;
        assertTrue(cc.contains("what you ask, and the evidence Claude reads through the analyser, is sent to the model "
                + "provider (Anthropic, on your Claude plan or API key), as in any Claude Code session"), cc);
        assertTrue(inApp.contains("is sent to the model provider you chose"), inApp);
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.SAFE_LIMITS.contains(cc), "the limits line");
        String faq = Files.readString(Path.of("docs/site/faq.md")).replaceAll("\\s+", " ");
        int start = faq.indexOf("## Is the assistant's action socket safe to enable?");
        String answer = faq.substring(start, faq.indexOf(" ## ", start + 1));
        assertTrue(answer.contains("No assistant profile is local-only, Safe analysis included."), "the FAQ security answer");
        assertTrue(answer.contains("is sent to the model provider (Anthropic, on your Claude plan or API key), as in any "
                + "Claude Code session"), "…names the provider for Claude Code");
        assertTrue(answer.contains("to the model provider you chose"), "…and for the in-app assistant");
        int offline = faq.indexOf("## Does it work offline?");
        assertTrue(faq.substring(offline, faq.indexOf(" ## ", offline + 1)).contains("No assistant profile is local-only"),
                "the offline answer");
        String guide = Files.readString(Path.of("docs/site/user-guide/assistant.md")).replaceAll("\\s+", " ");
        assertTrue(guide.contains("| **Safe analysis** (the default) |") && guide.contains("**Not local-only:** what you ask, "
                + "and the evidence Claude reads through the analyser, is sent to the model provider (Anthropic"),
                "the guide's Safe analysis row");
        assertTrue(guide.contains("the evidence the assistant reads through the analyser, is sent to the model provider you "
                + "chose"), "the guide's In-app row");
        assertTrue(guide.contains("**no profile is local-only**"), "the guide's limits-line bullet");
        String help = Files.readString(Path.of("src/main/resources/help/help.html")).replaceAll("\\s+", " ");
        assertTrue(help.contains("<b>No profile is local-only:</b>"), "the in-app help");
        String settings = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ConfigPanel.java"));
        assertTrue(settings.contains("AssistantProfiles.CLAUDE_CODE_SENDS") && settings.contains("AssistantProfiles.IN_APP_SENDS"),
                "Settings ▸ Assistant says it for Claude Code and for the in-app assistant");
        for (String untrue : new String[]{"local-only analysis", "nothing leaves this machine", "stays on your machine",
                "never leaves your machine"}) {
            for (String f : new String[]{"docs/site/faq.md", "docs/site/user-guide/assistant.md",
                    "docs/site/user-guide/start-page.md", "docs/site/getting-started.md"}) {
                assertFalse(Files.readString(Path.of(f)).replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT)
                        .contains(untrue), f + " claims \"" + untrue + "\"");
            }
        }
    }

    /**
     * ★ Owner 2026-10-18: the standing instruction a Full session carries is FIXED analyser text — pinned here word for
     * word, so a change to it is a reviewed change to the security contract — with no placeholder a person's or a
     * model's words could be put into, and plain text (no markup the CLI could read as more than words). Witness: put a
     * {@code %s} or a folder name into {@code ClaudeCodeProvider.STAY_IN_PROJECT_NOTE} — this fails.
     */
    @Test
    void theFullSessionsStandingInstructionIsFixedText() {
        String note = telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.STAY_IN_PROJECT_NOTE;
        assertTrue(note.equals("Work only inside this project folder: do not list, search or read files outside it, or "
                + "the environment. If the build needs the Fluxtion API key, the Fluxtion Maven plugin reads it; never "
                + "search for it, print it or read key files. Use ./mvnw and the project's own scripts to build and "
                + "generate."), "the pinned instruction changed: review it as a security-contract change");
        for (char ch : "{}<>%$`\\\n\r".toCharArray()) {
            assertTrue(note.indexOf(ch) < 0, "plain text: no placeholder, markup or escape: " + ch);
        }
        assertTrue(telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.FULL_SYSTEM_NOTE
                .endsWith(note), "the Full note carries it");
        assertFalse(telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.SYSTEM_NOTE
                .contains(note), "Safe has no shell and --restricted: it does not");
    }
}
