package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Where a menu item a caller asked for actually is, when it is not where they looked.
 *
 * <p>1.20.0 split the File menu into Project, Sources and Audit log, and renamed Reset. A {@code menu:File:…}
 * spotlight was refused with the new menu names, but nothing said where an item had gone, so a careful assistant
 * concluded "Reset doesn't exist" although it had just read "Close log and topology" in the Project list — and a
 * less careful one guessed a path. This turns a miss into a pointer: the same item spelled differently in the menu
 * asked for, the same item under another menu, or a renamed item's new name.
 *
 * <p>A hint states IDENTITY ("that item is this one"), so matching is deliberately narrow. Only case, repeated
 * whitespace, a trailing ellipsis and a one-character keyboard shortcut such as {@code (F)} are ignored. Every
 * other parenthetical is part of the name: {@code (CSV)} and {@code (YAML)} are different actions, and "Close log
 * (and topology)" is not Close log (PR #19 review, R1). Old spellings are recognised only through the explicit
 * {@link #RENAMES} list. When nothing matches exactly in that sense, there is no hint.
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

    /** An item whose NAME changed. An item that only moved needs no entry: it is found by name in the live menus. */
    record Rename(String oldName, List<String> spellings, String newName) {
    }

    static final List<Rename> RENAMES = List.of(
            new Rename("Reset", List.of("Reset", "Reset (close log + graph)"), "Close log and topology"));

    private static final Pattern ELLIPSIS = Pattern.compile("(…|\\.\\.\\.)$");
    private static final Pattern SHORTCUT = Pattern.compile("\\s*\\(\\w\\)$");   // "(F)" — one character only

    /**
     * Case, repeated whitespace, a trailing ellipsis and a one-character shortcut are not part of an item's identity;
     * anything else, including every other parenthetical, is. Applied until nothing changes, so the order in which a
     * label carries these decorations cannot change the result.
     */
    static String normalize(String label) {
        if (label == null) return "";
        String s = label.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        while (true) {
            String t = SHORTCUT.matcher(ELLIPSIS.matcher(s).replaceAll("").strip()).replaceAll("").strip();
            if (t.equals(s)) return s;
            s = t;
        }
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
        String asked = askedMenu == null ? "" : askedMenu.strip();
        // 1. the menu asked for holds it, spelled differently: stay in that menu
        for (Map.Entry<String, List<String>> menu : menus.entrySet()) {
            if (!menu.getKey().equalsIgnoreCase(asked)) continue;
            for (String item : menu.getValue()) {
                if (normalize(item).equals(wanted)) {
                    return "the " + menu.getKey() + " menu spells it '" + item + "' — light menu:" + menu.getKey() + ":" + item;
                }
            }
        }
        // 2. another menu holds it
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, List<String>> menu : menus.entrySet()) {
            if (menu.getKey().equalsIgnoreCase(asked)) continue;
            for (String item : menu.getValue()) {
                if (normalize(item).equals(wanted)) found.add("menu:" + menu.getKey() + ":" + item);
            }
        }
        if (!found.isEmpty()) {
            return "'" + askedItem.strip() + "' is in the " + menuOf(found.get(0)) + " menu"
                    + (found.size() > 1 ? " (also " + String.join(", ", found.subList(1, found.size())) + ")" : "")
                    + " — light " + found.get(0);
        }
        // 3. it was renamed, and its new name is on the menu bar
        for (Rename rename : RENAMES) {
            if (rename.spellings().stream().noneMatch(s -> normalize(s).equals(wanted))) continue;
            String[] at = locate(rename.newName(), menus);
            if (at != null) {
                return "'" + askedItem.strip() + "' was renamed in 1.20.0: it is now " + at[0] + " > " + at[1]
                        + " — light menu:" + at[0] + ":" + at[1];
            }
        }
        return null;
    }

    /**
     * The menu changes a reader of {@code context.menus} would otherwise miss: retired menus and renamed items. A
     * model that reads the menu map first never asks for an old path, so the hint on a miss never reaches it
     * (virgin-LLM check on PR #19: "File > Reset does not exist", with no replacement named). A rename is listed
     * only while its new name is on the menu bar.
     */
    static List<String> changes(Map<String, List<String>> menus) {
        List<String> out = new ArrayList<>(RETIRED_MENUS.values());
        for (Rename rename : RENAMES) {
            String[] at = locate(rename.newName(), menus);
            if (at != null) {
                out.add("File > " + rename.oldName() + " was renamed in 1.20.0: it is now " + at[0] + " > " + at[1]);
            }
        }
        return out;
    }

    /** The note for a menu that no longer exists, or null. */
    static String retired(String askedMenu) {
        return askedMenu == null ? null : RETIRED_MENUS.get(askedMenu.strip().toLowerCase(Locale.ROOT));
    }

    /** {menu, item} for the first item whose normalized name is {@code name}'s, or null. */
    private static String[] locate(String name, Map<String, List<String>> menus) {
        for (Map.Entry<String, List<String>> menu : menus.entrySet()) {
            for (String item : menu.getValue()) {
                if (normalize(item).equals(normalize(name))) return new String[]{menu.getKey(), item};
            }
        }
        return null;
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
