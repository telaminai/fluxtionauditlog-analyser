package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a menu item a caller asked for actually is, when it is not where they looked.
 *
 * <p>1.20.0 split the File menu into Project, Sources and Audit log, and renamed Reset. A {@code menu:File:…}
 * spotlight was refused with the new menu names, but nothing said where an item had gone, so a careful assistant
 * concluded "Reset doesn't exist" although it had just read "Close log and topology" in the Project list — and a
 * less careful one guessed a path. This turns a miss into a pointer: the same item under another menu, or a
 * renamed item's new name.
 *
 * <p>Pure: it takes the live menu map, so it is testable without a frame, and it can never point at an item the
 * menu bar does not actually hold.
 */
final class MenuHints {

    private MenuHints() {
    }

    /** Menus that no longer exist, and what replaced them. */
    static final Map<String, String> RETIRED_MENUS = Map.of(
            "file", "the File menu was split in 1.20.0 into Project, Sources and Audit log");

    /**
     * Items whose NAME changed, by normalized old name. An item that only moved needs no entry here: it is found by
     * name in the live menus.
     */
    static final Map<String, String> RENAMED_ITEMS = Map.of(
            "reset", "Close log and topology");

    /** Case, a trailing ellipsis and a trailing parenthetical are not part of an item's identity. */
    static String normalize(String label) {
        if (label == null) return "";
        String s = label.strip().toLowerCase(Locale.ROOT);
        int paren = s.indexOf(" (");
        if (paren > 0 && s.endsWith(")")) s = s.substring(0, paren);
        while (s.endsWith("…") || s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.strip();
    }

    /**
     * A sentence saying where {@code askedItem} is, or null when the live menus hold nothing that matches.
     *
     * @param askedMenu the menu the caller named (it may not exist)
     * @param askedItem the item the caller named
     * @param menus     the live menu bar: menu name → item texts, in order
     */
    static String whereIs(String askedMenu, String askedItem, Map<String, List<String>> menus) {
        String wanted = normalize(askedItem);
        if (wanted.isEmpty()) return null;
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, List<String>> menu : menus.entrySet()) {
            if (menu.getKey().equalsIgnoreCase(askedMenu == null ? "" : askedMenu.strip())) continue;
            for (String item : menu.getValue()) {
                if (normalize(item).equals(wanted)) found.add("menu:" + menu.getKey() + ":" + item);
            }
        }
        if (!found.isEmpty()) {
            return "'" + askedItem.strip() + "' is in the " + menuOf(found.get(0)) + " menu"
                    + (found.size() > 1 ? " (also " + String.join(", ", found.subList(1, found.size())) + ")" : "")
                    + " — light " + found.get(0);
        }
        String renamed = RENAMED_ITEMS.get(wanted);
        if (renamed != null) {
            for (Map.Entry<String, List<String>> menu : menus.entrySet()) {
                for (String item : menu.getValue()) {
                    if (normalize(item).equals(normalize(renamed))) {
                        return "'" + askedItem.strip() + "' was renamed in 1.20.0: it is now " + menu.getKey() + " > "
                                + item + " — light menu:" + menu.getKey() + ":" + item;
                    }
                }
            }
        }
        return null;
    }

    /** The note for a menu that no longer exists, or null. */
    static String retired(String askedMenu) {
        return askedMenu == null ? null : RETIRED_MENUS.get(askedMenu.strip().toLowerCase(Locale.ROOT));
    }

    private static String menuOf(String target) {
        String rest = target.substring("menu:".length());
        return rest.substring(0, rest.indexOf(':'));
    }

    /** An ordered copy, for building the map from a menu bar. */
    static Map<String, List<String>> ordered() {
        return new LinkedHashMap<>();
    }
}
