package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M37 D-L1 and D-L3, both structural.
 *
 * <p>D-L3: nothing on the panel can mutate the app. The panel's only way out is {@link ProjectPanel.Navigator}
 * (four navigation methods), and its bytecode never names MainFrame — the same constant-pool check
 * McpBridgeHeadlessTest uses, because a test that merely clicked buttons would pass while a reference
 * sat on a branch it did not take.
 *
 * <p>D-L1: the model reads only keys {@code context} puts. Every dotted key in {@link ProjectModel#KEYS_READ}
 * must appear as a {@code put("leaf"} in MainFrame's context() or SessionFacts' maps — read as source
 * text, so a renamed key fails here rather than as a silently empty row.
 */
class ProjectPanelIsRevealOnlyTest {

    private static String bytecodeOf(Class<?> type) throws IOException {
        URL url = type.getResource(type.getSimpleName() + ".class");
        assertNotNull(url);
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Test
    void thePanelNeverNamesMainFrame_itsOnlyExitIsTheTwoMethodNavigator() throws IOException {
        for (Class<?> c : new Class<?>[]{ProjectPanel.class, ProjectModel.class}) {
            String bytes = bytecodeOf(c);
            assertFalse(bytes.contains("ui/MainFrame"), c.getSimpleName() + " must not reference MainFrame");
            assertFalse(bytes.contains("ActionExecutor") || bytes.contains("AppControl"),
                    c.getSimpleName() + " must not reach the action surface — it renders, it does not act");
        }
        // D-L3, RELAXED by the owner on 2026-09-30, and the reason is worth keeping.
        //
        // The rule was "the Navigator moves the eye, not the state", which kept the panel honest while it
        // was purely a rendering of `context`. In use it made the panel a place that could only ever say
        // "go to Settings and find this again by eye": you SEE the unexpected source root here, and the
        // row that shows it was the one place you could not act on it. The owner's call: "it is good what
        // we have, relax the rule — it is what any user would want when they are experienced."
        //
        // What is still pinned, and what the assertion below actually holds:
        //   * the panel and the model never name MainFrame, ActionExecutor or AppControl (above) — the
        //     panel still cannot ACT, it can only ask the Navigator, which is the frame's own adapter;
        //   * the model stays pure Swing-free (below);
        //   * the Navigator's surface is still a CLOSED, named list. Adding to it remains a spec change,
        //     and this test is still the place that makes you say so out loud.
        // What changed is only that a named action MAY edit or discard project state, where the row that
        // carries it is where a person can see what they are acting on.
        //
        // Named by NAME, not by Set.of over the method array: openSettings now has two overloads, and
        // Set.of threw "duplicate element: openSettings" -- the guard died before it could report
        // anything at all, which is worse than a guard that fails loudly (review, 2026-09-30).
        Set<String> navigator = new TreeSet<>(java.util.Arrays.stream(
                        ProjectPanel.Navigator.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList());
        assertEquals(new TreeSet<>(Set.of("showTab", "openSettings", "showReport", "showGraph",
                        "removeSourceRoot", "openProcessorSource", "setActiveProcessor", "removeProcessor")),
                navigator,
                "the Navigator's surface is a closed list; adding a method here is a spec change (D-L3). "
                        + "showReport/showGraph were added deliberately (owner, 2026-09-24, 35eeb320): revealing a "
                        + "SPECIFIC item is still moving the eye. The four state-changing actions were added "
                        + "deliberately too (owner, 2026-09-30): a row that shows you a source root you did not "
                        + "expect is exactly where removing it belongs. The panel still cannot act by itself — "
                        + "it asks the frame, which is what the bytecode assertions above hold.");
        assertFalse(bytecodeOf(ProjectModel.class).contains("javax/swing"), "the model is pure");
    }

    @Test
    void everyKeyTheModelReadsIsOneContextPuts() throws IOException {
        String mainFrame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        String facts = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/llm/SessionFacts.java"));
        int start = mainFrame.indexOf("ActionResult context() {");
        assertTrue(start > 0);
        // context() plus the helpers it assembles from (runbooksForContext, …): the whole file is the honest
        // scope once the builder is split — a key put nowhere in MainFrame is still a key context cannot serve
        String context = mainFrame;
        // M48.7: the handoff section is assembled by CanvasHandoff.toContext, the third place context is built
        String handoff = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/llm/CanvasHandoff.java"));
        // The generated session graph owns recovery state; the panel renders its context echo.
        String recovery = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/SessionRecovery.java"));
        // M69 S4: context.walks is assembled by WalkVerb.context, beside the verb that edits the walks
        String walks = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/WalkVerb.java"));
        Set<String> put = new TreeSet<>();
        Matcher m = Pattern.compile("put\\(\"([A-Za-z]+)\"").matcher(context + facts + handoff + recovery + walks);
        while (m.find()) put.add(m.group(1));
        // Map.of literals inside context() — `Map.of("path", r, "tier", ...)` — put keys without put(
        Matcher lit = Pattern.compile("Map\\.of\\(\"([A-Za-z]+)\", [^,]+, \"([A-Za-z]+)\"").matcher(context);
        while (lit.find()) { put.add(lit.group(1)); put.add(lit.group(2)); }

        for (String key : new TreeSet<>(ProjectModel.KEYS_READ)) {
            for (String leaf : key.split("\\.")) {
                assertTrue(put.contains(leaf), "ProjectModel reads `" + key + "` but context() never puts `" + leaf
                        + "` — add it to context first (D-L1), then read it");
            }
        }
    }
}
