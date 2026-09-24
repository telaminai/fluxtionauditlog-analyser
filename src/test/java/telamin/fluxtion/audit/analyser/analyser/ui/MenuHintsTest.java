package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Where a missed menu item is, against 1.20.0's real menu bar (as its spotlight refusals list it). */
class MenuHintsTest {

    private static final Map<String, List<String>> MENUS = new LinkedHashMap<>();

    static {
        MENUS.put("Project", List.of("Open project…", "Open recent project", "New project from template…", "New project…",
                "Save project as…", "Close project", "Close log and topology", "Run analysis", "Settings…",
                "Export settings…", "Import settings…", "Exit"));
        MENUS.put("Sources", List.of("Source roots…", "Event processor…", "Maven repos…", "Open GraphML…",
                "Open recent GraphML", "Open design…", "Open producer diagnostics…", "Find GraphML in source roots…",
                "Close graph"));
        MENUS.put("Audit log", List.of("Open log…", "Open recent audit log", "Open log from S3…", "Add series from CSV…",
                "Close log", "Follow (tail)", "Export records (CSV)…", "Export records (YAML)…"));
        MENUS.put("Records", List.of("Flag / unflag selected  (F)", "Show flagged only", "Clear all flags",
                "Export records (CSV)…", "Export records (YAML)…"));
    }

    @Test
    void theRenamedResetPointsAtItsNewName_whateverSpellingWasUsed() {
        for (String asked : List.of("Reset", "reset", "Reset (close log + graph)", "Reset…")) {
            String hint = MenuHints.whereIs("File", asked, MENUS);
            assertNotNull(hint, asked);
            assertTrue(hint.contains("renamed in 1.20.0") && hint.endsWith("light menu:Project:Close log and topology"), hint);
        }
    }

    @Test
    void aMovedItemIsFoundByName_inItsNewMenu() {
        assertEquals("'Follow (tail)' is in the Audit log menu — light menu:Audit log:Follow (tail)",
                MenuHints.whereIs("File", "Follow (tail)", MENUS));
        assertTrue(MenuHints.whereIs("File", "follow", MENUS).endsWith("light menu:Audit log:Follow (tail)"),
                "case and a trailing parenthetical are not part of the name");
        assertTrue(MenuHints.whereIs("Records", "Source roots", MENUS).endsWith("light menu:Sources:Source roots…"));
    }

    @Test
    void anItemInTwoMenusNamesBoth_andNeverTheMenuItWasAskedIn() {
        String hint = MenuHints.whereIs("Project", "Export records (CSV)…", MENUS);
        assertTrue(hint.contains("menu:Audit log:Export records (CSV)…") && hint.contains("menu:Records:Export records (CSV)…"), hint);
        assertNull(MenuHints.whereIs("Audit log", "Close logs and graphs", MENUS), "no near-miss guessing");
    }

    @Test
    void nothingIsInvented() {
        assertNull(MenuHints.whereIs("Project", "Frobnicate", MENUS));
        assertNull(MenuHints.whereIs("File", "", MENUS));
        Map<String, List<String>> withoutTarget = new LinkedHashMap<>(MENUS);
        withoutTarget.put("Project", List.of("Exit"));
        assertNull(MenuHints.whereIs("File", "Reset", withoutTarget),
                "a rename is only reported when its new name is really on the menu bar");
    }

    @Test
    void theRetiredFileMenuSaysWhatReplacedIt() {
        assertEquals("the File menu was split in 1.20.0 into Project, Sources and Audit log", MenuHints.retired(" file "));
        assertNull(MenuHints.retired("Project"));
    }
}
