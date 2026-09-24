package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where a missed menu item is, against the menu bar as {@link MenuInventory} pins it (the shared test contract the
 * live-menu checks use), in menu-bar order.
 */
class MenuHintsTest {

    private static final Map<String, List<String>> MENUS = new LinkedHashMap<>();

    static {
        for (String menu : List.of("Project", "Sources", "Audit log", "Records", "Theme", "AI", "Help")) {
            MENUS.put(menu, MenuInventory.labels(menu));
        }
    }

    @Test
    void theRenamedResetPointsAtItsNewName_whateverSpellingWasUsed() {
        for (String asked : List.of("Reset", "reset", "Reset…", "Reset (close log + graph)", "Reset (close log + graph)…")) {
            String hint = MenuHints.whereIs("File", asked, MENUS);
            assertNotNull(hint, asked);
            assertTrue(hint.contains("renamed in 1.20.0") && hint.endsWith("light menu:Project:Close log and topology"), hint);
        }
    }

    @Test
    void aMovedItemIsFoundByItsName_inItsNewMenu() {
        assertEquals("'Follow (tail)' is in the Audit log menu — light menu:Audit log:Follow (tail)",
                MenuHints.whereIs("File", "Follow (tail)", MENUS));
        assertTrue(MenuHints.whereIs("File", "follow (TAIL)…", MENUS).endsWith("light menu:Audit log:Follow (tail)"),
                "case and a trailing ellipsis are not part of the name");
        assertTrue(MenuHints.whereIs("Records", "Source roots", MENUS).endsWith("light menu:Sources:Source roots…"));
    }

    /** PR #19 review, R1: a parenthetical can be the action. CSV is never YAML, with or without the ellipsis. */
    @Test
    void theExportFormatIsPartOfTheName_withOrWithoutTheEllipsis() {
        for (String asked : List.of("Export records (CSV)…", "Export records (CSV)", "export records (csv)...")) {
            String hint = MenuHints.whereIs("File", asked, MENUS);
            assertNotNull(hint, asked);
            assertTrue(hint.contains("Export records (CSV)…") && !hint.contains("YAML"), asked + " -> " + hint);
        }
        for (String asked : List.of("Export records (YAML)…", "Export records (YAML)")) {
            String hint = MenuHints.whereIs("File", asked, MENUS);
            assertNotNull(hint, asked);
            assertTrue(hint.contains("Export records (YAML)…") && !hint.contains("CSV"), asked + " -> " + hint);
        }
        assertNull(MenuHints.whereIs("File", "Export records", MENUS), "a name without its format names no action");
    }

    /** PR #19 review, R1: "Close log (and topology)" is not Close log — a hint must never change the action. */
    @Test
    void anUnknownQualifierGetsNoIdentityClaim() {
        assertNull(MenuHints.whereIs("File", "Close log (and topology)", MENUS));
        assertNull(MenuHints.whereIs("File", "Follow", MENUS), "a partial name is not an identity either");
        assertTrue(MenuHints.whereIs("Audit log", "Close log and topology", MENUS)
                .endsWith("light menu:Project:Close log and topology"), "the close-both item is found where it is");
    }

    @Test
    void aOneCharacterShortcutAndSpacingAreNotPartOfTheName() {
        for (String asked : List.of("Flag / unflag selected (F)", "Flag / unflag selected", "flag /  unflag selected  (f)")) {
            String hint = MenuHints.whereIs("File", asked, MENUS);
            assertNotNull(hint, asked);
            assertTrue(hint.endsWith("light menu:Records:Flag / unflag selected  (F)"), asked + " -> " + hint);
        }
    }

    /** PR #19 review, O1: a spelling miss in the menu asked for stays in that menu, not a copy elsewhere. */
    @Test
    void aSpellingMissInsideTheAskedMenuStaysInThatMenu() {
        String hint = MenuHints.whereIs("Audit log", "Export records (CSV)", MENUS);
        assertEquals("the Audit log menu spells it 'Export records (CSV)…' — light menu:Audit log:Export records (CSV)…", hint);
        assertTrue(MenuHints.whereIs("Records", "export records (yaml)", MENUS).endsWith("light menu:Records:Export records (YAML)…"));
    }

    @Test
    void anItemInTwoMenusNamesBoth_andNothingIsInvented() {
        String hint = MenuHints.whereIs("Project", "Export records (CSV)…", MENUS);
        assertTrue(hint.contains("menu:Audit log:Export records (CSV)…") && hint.contains("menu:Records:Export records (CSV)…"), hint);
        assertNull(MenuHints.whereIs("Project", "Frobnicate", MENUS));
        assertNull(MenuHints.whereIs("File", "", MENUS));
        Map<String, List<String>> withoutTarget = new LinkedHashMap<>(MENUS);
        withoutTarget.put("Project", List.of("Exit"));
        assertNull(MenuHints.whereIs("File", "Reset", withoutTarget),
                "a rename is only reported when its new name is really on the menu bar");
    }

    @Test
    void theChangesListNamesTheRenameOnce_andOnlyWhileItsNewNameIsOnTheMenuBar() {
        List<String> changes = MenuHints.changes(MENUS);
        assertEquals(List.of("the File menu was split in 1.20.0 into Project, Sources and Audit log",
                "File > Reset was renamed in 1.20.0: it is now Project > Close log and topology"), changes);
        Map<String, List<String>> without = new LinkedHashMap<>(MENUS);
        without.put("Project", List.of("Exit"));
        assertTrue(MenuHints.changes(without).stream().noneMatch(c -> c.contains("Reset")), "never points at a missing item");
    }

    @Test
    void theRetiredFileMenuSaysWhatReplacedIt() {
        assertEquals("the File menu was split in 1.20.0 into Project, Sources and Audit log", MenuHints.retired(" file "));
        assertNull(MenuHints.retired("Project"));
    }

    /** Every one of the 26 pre-1.20 File items is either on the new menu bar by name or a listed rename. */
    @Test
    void everyOldFileItemHasAHint() {
        for (String old : MenuInventory.BASE_FILE) {
            assertNotNull(MenuHints.whereIs("File", old, MENUS), "no hint for old File item " + old);
        }
    }
}
