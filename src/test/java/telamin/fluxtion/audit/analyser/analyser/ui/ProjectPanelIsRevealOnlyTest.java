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
 * (two navigation methods), and its bytecode never names MainFrame — the same constant-pool check
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
        assertEquals(Set.of("showTab", "openSettings"),
                Set.of(java.util.Arrays.stream(ProjectPanel.Navigator.class.getDeclaredMethods())
                        .map(java.lang.reflect.Method::getName).toArray(String[]::new)),
                "the Navigator moves the eye, not the state; adding a method here is a spec change (D-L3)");
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
        Set<String> put = new TreeSet<>();
        Matcher m = Pattern.compile("put\\(\"([A-Za-z]+)\"").matcher(context + facts);
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
