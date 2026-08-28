package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M43's acceptance criteria that are not about storage: D-AI2, D-AI3 and D-AI4.
 *
 * <p>Swing is not unit-tested here (rule 4), so these test the two things that CAN be tested without a
 * screen: the decisions, which now live in {@link AiMenuModel} rather than inside a listener, and the
 * menu's SOURCE, read as text — the same technique {@code ProjectPanelIsRevealOnlyTest} uses, because a
 * test that clicked buttons would pass while a forbidden call sat on a branch it did not take.
 */
class AiMenuTest {

    private static final Path MAIN_FRAME =
            Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java");

    /** The body of buildAiMenu(), as source text. */
    private static String menuSource() throws IOException {
        String src = Files.readString(MAIN_FRAME);
        int start = src.indexOf("private JMenu buildAiMenu()");
        assertTrue(start > 0, "buildAiMenu() has been renamed — this test must follow it, not be deleted");
        int end = src.indexOf("\n    }", start);
        assertTrue(end > start);
        return src.substring(start, end);
    }

    // ---- D-AI3: disabled WITH THE REASON, never a dialog that explains itself after the click ------

    @Test
    void withNoProjectThePointerItemsAreDisabledAndNameTheREMEDY() {
        AiMenuModel.Item item = AiMenuModel.pointers(false);
        assertFalse(item.enabled());
        assertTrue(item.tooltip().contains("File ▸ Open project"),
                "a disabled item must say what to DO, not restate that it is disabled: " + item.tooltip());
    }

    @Test
    void withAProjectTheyAreUsableAndSayWhatTheyStore() {
        AiMenuModel.Item item = AiMenuModel.pointers(true);
        assertTrue(item.enabled());
        assertTrue(item.tooltip().contains("never contents"),
                "the D-C2 promise belongs where the person is about to act: " + item.tooltip());
    }

    @Test
    void theExchangeItemDistinguishesOffFromUnset_becauseTheRemediesDiffer() {
        AppConfig off = new AppConfig();
        off.assistantExports = false;
        assertFalse(AiMenuModel.showExchange(off).enabled());
        assertTrue(AiMenuModel.showExchange(off).tooltip().contains("off"), "say it is off");

        AppConfig on = new AppConfig();
        on.assistantExports = true;
        on.assistantExportDir = "  ";
        assertFalse(AiMenuModel.showExchange(on).enabled());
        assertTrue(AiMenuModel.showExchange(on).tooltip().contains("No exchange directory"),
                "on-but-unset is a DIFFERENT remedy from off, and collapsing them sends people to the "
                        + "wrong switch: " + AiMenuModel.showExchange(on).tooltip());

        AppConfig ready = new AppConfig();
        ready.assistantExports = true;
        ready.assistantExportDir = "/tmp/exchange";
        assertEquals(new AiMenuModel.Item(true, "/tmp/exchange"), AiMenuModel.showExchange(ready));
    }

    @Test
    void everyDisabledStateNamesSomethingTheUserCanGoAndDo() {
        // the general rule, not one case of it: a disabled item that only says "no" is the modal M35
        // removed, moved into a tooltip
        AppConfig blank = new AppConfig();
        for (AiMenuModel.Item item : List.of(AiMenuModel.pointers(false), AiMenuModel.showExchange(blank))) {
            assertFalse(item.enabled());
            assertTrue(item.tooltip().contains("▸") || item.tooltip().contains("…"),
                    "no route offered: " + item.tooltip());
        }
    }

    @Test
    void aNullConfigIsNotAnExplosion() {
        assertFalse(AiMenuModel.showExchange(null).enabled());
        assertFalse(AiMenuModel.transportTicked(null));
    }

    // ---- D-AI2: bound to the config value, never a second copy ------------------------------------

    @Test
    void theTransportCheckboxRendersTheConfigValueRatherThanRememberingOne() {
        AppConfig c = new AppConfig();
        c.assistantActionsRest = false;
        assertFalse(AiMenuModel.transportTicked(c));
        c.assistantActionsRest = true;
        assertTrue(AiMenuModel.transportTicked(c), "the menu must follow the model, not hold its own flag");
    }

    @Test
    void theMenuDeclaresNoFieldOfItsOwn() throws IOException {
        // D-AI2 structurally: a boolean/String remembered across openings is a second copy of state, and
        // the copy nobody updates is the one still in use (the reason KnownKeys and D-C10 exist)
        String body = menuSource();
        Matcher m = Pattern.compile("\\b(static\\s+)?(boolean|String)\\s+\\w+\\s*=").matcher(body);
        assertFalse(m.find(), "buildAiMenu() is keeping state: " + (m.reset().find() ? m.group() : ""));
    }

    // ---- D-AI4: the menu runs nothing -------------------------------------------------------------

    @Test
    void nothingOnTheMenuExecutesARunbookOrAnAnalysis() throws IOException {
        String body = menuSource().toLowerCase(Locale.ROOT);
        for (String forbidden : List.of("processbuilder", "runtime.getruntime", "runanalysis",
                "exec(", "desktop.getdesktop().edit")) {
            assertFalse(body.contains(forbidden),
                    "the AI menu must not run anything (D-AI4) — found '" + forbidden + "'. The analyser "
                            + "serves a pointer and never the instructions, and this menu is exactly where "
                            + "'just add a Run item' becomes tempting.");
        }
    }

    @Test
    void noItemIsLabelledAsRunningSomething() throws IOException {
        Matcher m = Pattern.compile("new JMenuItem\\(\"([^\"]+)\"\\)").matcher(menuSource());
        int items = 0;
        while (m.find()) {
            items++;
            String label = m.group(1).toLowerCase(Locale.ROOT);
            assertFalse(label.startsWith("run ") || label.startsWith("execute"),
                    "menu item '" + m.group(1) + "' reads as an executor");
        }
        assertTrue(items >= 5, "found only " + items + " items — has the menu moved? A test that matches "
                + "nothing proves nothing.");
    }

    // ---- D-AI6: the dialog shows the refusal reason -----------------------------------------------

    @Test
    void thePointerDialogRendersTheGatesREASON_ratherThanJustRefusing() throws IOException {
        String src = Files.readString(
                Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/PointerDialog.java"));
        assertTrue(src.contains("Runbooks.refuse("), "it must use the existing gate, not invent a fourth one");
        assertTrue(src.contains("Runbooks.refuseDescription("));
        assertTrue(src.contains("problem.setText("),
                "the refusal must reach the user; a control that refuses silently reads as broken");
    }

    @Test
    void thePointerDialogNeverNamesMainFrame() throws IOException {
        // it edits the profile through AppConfig and a callback — it has no business reaching the frame
        URL url = PointerDialog.class.getResource("PointerDialog.class");
        assertNotNull(url);
        try (InputStream in = url.openStream()) {
            String bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(bytes.contains("ui/MainFrame"), "PointerDialog must not reference MainFrame");
        }
    }
}
