package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #73: the recent evidence bundles have to be tellable apart. Headless — the naming is a pure function,
 * deliberately, so it is tested without a frame.
 */
class StartPanelNamesTest {

    @Test
    @DisplayName("two bundles with the same file name are widened until they differ")
    void sameFileNameIsWidened() {
        List<String> names = StartPanel.distinctNames(List.of(
                "/w/scratch-demo/bundles/recorded-run.fexp",
                "/w/replay-divergence-demo/recorded-run.fexp"));

        assertEquals(List.of("bundles/recorded-run.fexp", "replay-divergence-demo/recorded-run.fexp"),
                names, "collidingNamesAreWidenedByOneSegment");
    }

    @Test
    @DisplayName("a name that is already unique stays short")
    void uniqueNamesAreNotWidened() {
        List<String> names = StartPanel.distinctNames(List.of(
                "/w/one/alpha.fexp", "/w/two/beta.fexp"));

        assertEquals(List.of("alpha.fexp", "beta.fexp"), names, "noNeedlessWidening");
    }

    @Test
    @DisplayName("widening continues past a shared parent")
    void widensPastASharedParent() {
        List<String> names = StartPanel.distinctNames(List.of(
                "/w/a/bundles/run.fexp", "/w/b/bundles/run.fexp"));

        assertEquals(List.of("a/bundles/run.fexp", "b/bundles/run.fexp"), names,
                "widensUntilTheyActuallyDiffer");
    }

    @Test
    @DisplayName("one bundle needs no context at all")
    void singleEntry() {
        assertEquals(List.of("run.fexp"), StartPanel.distinctNames(List.of("/w/a/run.fexp")));
    }

    /**
     * ★ r8 F7: every no-AI fallback is a BUTTON that runs an EXISTING start-page action — never a menu path (the start
     * page hides the menu bar). Witness: route BUNDLE_GUIDE to nothing — its call is missing and this fails.
     */
    @Test
    @DisplayName("★ each no-AI fallback runs an existing start-page action")
    void fallbacksRunExistingActions() {
        List<String> calls = new java.util.ArrayList<>();
        StartPanel.Actions actions = new StartPanel.Actions() {
            public void openDemo(java.nio.file.Path p, boolean g) { }
            public void showTab(String n) { }
            public void openOwnLog() { }
            public void openSettings() { calls.add("settings"); }
            public void openMcpSetup(McpSetupDialog.Target t) { calls.add("mcp:" + t.name()); }
            public void openFluxtionKey() { }
            public boolean fluxtionKeyPresent() { return false; }
            public void backToRecords() { }
            public void newProject() { calls.add("new"); }
            public void newProjectFromTemplate() { calls.add("template"); }
            public void openUserGuide(String page) { calls.add("guide:" + page); }
            public void copyStartPrompt(String entry, String text) { calls.add("copy:" + entry + ":" + text); }
            public void installClaudeCode() { calls.add("install"); }
            public void openGuidedTour() { calls.add("tour"); }
            public void openSampleProject() { calls.add("sample"); }
        };
        for (var f : telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Fallback.values()) {
            StartPanel.runFallback(actions, f, "S6", "q");
        }
        StartPanel.runFallback(actions, telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Fallback.CONNECT,
                "INSTALL", "");
        assertEquals(List.of("settings", "mcp:CLAUDE_CODE", "copy:S6:q", "new", "guide:evidence-bundles/opening/",
                "template", "guide:", "tour", "sample", "install"), calls);
    }

    /**
     * ★ P2 (docs-IA product findings): a fallback that opens a docs-site page says so in its tooltip, never an in-app
     * Help path it does not open. Witness: put back {@code fallback.bundleGuide.tip=Help ▸ User guide ▸ Evidence
     * bundles} — the bundle guide opens {@code evidence-bundles/opening/} on the docs site, and this fails.
     */
    @Test
    @DisplayName("★ a fallback that opens a docs-site page says so, not a Help menu path")
    void aDocsSiteFallbackSaysWhereItGoes() {
        int docsPages = 0;
        for (var f : telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Fallback.values()) {
            List<String> pages = new java.util.ArrayList<>();
            StartPanel.Actions actions = new StartPanel.Actions() {
                public void openDemo(java.nio.file.Path p, boolean g) { }
                public void showTab(String n) { }
                public void openOwnLog() { }
                public void openSettings() { }
                public void openMcpSetup(McpSetupDialog.Target t) { }
                public void openFluxtionKey() { }
                public boolean fluxtionKeyPresent() { return false; }
                public void backToRecords() { }
                public void openUserGuide(String page) { pages.add(page); }
            };
            StartPanel.runFallback(actions, f, "S6", "q");
            if (pages.isEmpty() || pages.get(0).isBlank()) continue;   // "" is the in-app Help ▸ User guide
            docsPages++;
            String tip = telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.text(
                    StartPromptView.fallbackKey(f) + ".tip");
            assertFalse(tip.startsWith("Help ▸"), f + " opens the docs site (" + pages.get(0) + "), but its tooltip is: " + tip);
            assertTrue(tip.contains("docs site"), f + "'s tooltip must say it opens the docs site: " + tip);
        }
        assertEquals(1, docsPages, "the bundle guide is the one fallback that opens a docs-site page");
        assertEquals("Opens the docs site's page Opening a bundle you were sent, in your browser",
                telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.text("fallback.bundleGuide.tip"));
    }
}
