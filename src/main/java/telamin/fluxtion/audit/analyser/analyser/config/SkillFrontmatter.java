package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * M43.4 — reads the {@code name}/{@code description} frontmatter of a skill-shaped runbook, so the
 * <i>Add runbook…</i> dialog can OFFER them.
 *
 * <h2>This is a suggestion, never a source of truth (D-AI5)</h2>
 * The documented convention is that a runbook may be written in the shape an AI harness already loads as
 * a skill — frontmatter with {@code name} and {@code description}, then the steps — so one file can be
 * both the team's runbook and a Claude Code skill. It would be easy to read the description out of that
 * file whenever {@code context} is served. <b>That would be wrong.</b> The fact would be INFERRED, and
 * what the analyser reports would change whenever somebody edited a file, with nobody declaring it —
 * exactly what D-A2 exists to prevent.
 *
 * <p>So this class is only ever called with a person looking at a dialog. It prefills two fields; the
 * person accepts or edits them; what is stored is what they declared. This is M35.4's rule applied to a
 * fact rather than a file: <b>discovery offers, and never selects.</b> A consequence worth stating,
 * because it is the reason the rule is worth its cost: editing the file afterwards does not silently
 * change what {@code context} says about it.
 *
 * <p>Nothing here executes, resolves or fetches anything. It reads the head of one file the user chose.
 */
public final class SkillFrontmatter {

    private SkillFrontmatter() {
    }

    /** Frontmatter must start on line 1, and we never read far into a file to find it. */
    private static final int MAX_HEAD_LINES = 40;

    /**
     * @param name        the frontmatter's {@code name}, or null — a suggested runbook name
     * @param description the frontmatter's {@code description}, or null
     */
    public record Suggestion(String name, String description) {
        public boolean isEmpty() {
            return name == null && description == null;
        }
    }

    public static final Suggestion NONE = new Suggestion(null, null);

    /** Reads {@code file}'s frontmatter. Any problem yields {@link #NONE}: a prefill that cannot be made
     *  is not an error the user needs to hear about — they are about to type the fields anyway. */
    public static Suggestion read(Path file) {
        if (file == null || !Files.isRegularFile(file)) return NONE;
        try (var lines = Files.lines(file)) {
            return parse(lines.limit(MAX_HEAD_LINES).toList());
        } catch (Exception e) {
            return NONE;               // binary, unreadable, wrong encoding — all just "no suggestion"
        }
    }

    static Suggestion parse(java.util.List<String> head) {
        if (head.isEmpty() || !"---".equals(head.get(0).trim())) return NONE;
        String name = null, description = null;
        for (int i = 1; i < head.size(); i++) {
            String line = head.get(i);
            if ("---".equals(line.trim())) break;                    // end of the block
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = unquote(line.substring(colon + 1).trim());
            if (value.isEmpty()) continue;
            if ("name".equals(key) && name == null) name = value;
            else if ("description".equals(key) && description == null) description = value;
        }
        return new Suggestion(name, description);
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1).trim();
        }
        return v;
    }

    /**
     * The suggested name, reduced to something {@link Runbooks#refuse} will accept, or empty when it
     * cannot be. Skill names are conventionally already in this shape ({@code restart-quote-service});
     * this only trims what is obviously unusable rather than inventing a name the user did not write.
     */
    public static Optional<String> usableName(String suggested) {
        if (suggested == null) return Optional.empty();
        String cleaned = suggested.trim().replaceAll("[^A-Za-z0-9_-]", "-").replaceAll("-{2,}", "-");
        while (cleaned.startsWith("-") || cleaned.startsWith("_")) cleaned = cleaned.substring(1);
        if (cleaned.length() > 40) cleaned = cleaned.substring(0, 40);
        while (cleaned.endsWith("-") || cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned.isEmpty() || Runbooks.refuse(cleaned, "ok.md").isPresent()
                ? Optional.empty() : Optional.of(cleaned);
    }

    /** The suggested description, or empty when it is not one this profile could store. */
    public static Optional<String> usableDescription(String suggested) {
        if (suggested == null || suggested.isBlank()) return Optional.empty();
        String one = suggested.trim();
        return Runbooks.refuseDescription("suggested", one).isPresent() ? Optional.empty() : Optional.of(one);
    }
}
