package telamin.fluxtion.audit.analyser.analyser.config;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * D-AX1c/D-AX1d — the published Fluxtion authoring resources a new project POINTS at.
 *
 * <h2>Why this exists at all</h2>
 * Measured across nine fresh LLM sessions on a real generated project: the material that answers most
 * Fluxtion authoring questions is already published, and the project did not reference it. Sessions given
 * the resources avoided or resolved failures that sessions without them fixed by inventing workarounds
 * they could not explain. So a generated project's job is to POINT — one edit upstream then improves
 * every project, rather than being re-delivered to each one.
 *
 * <h2>What this class will and will not write</h2>
 * The analyser has never written documentation into a user's repository; it writes profiles. This is the
 * first exception and it is deliberately the narrowest one that works:
 * <ul>
 *   <li><b>Only {@code agreed} resources are ever written.</b> {@code proposed} entries await the owner's
 *       sign-off (D-AX1c), so until that happens this class produces nothing — the feature cannot ship
 *       guidance nobody approved.</li>
 *   <li><b>It never overwrites.</b> An existing {@code CLAUDE.md} is the author's, and silently rewriting
 *       it would be the worst thing this could do. {@link #offer(Path)} reports {@link Outcome#EXISTS}
 *       and stops.</li>
 *   <li><b>It writes references, never content.</b> No restated rules — that is exactly the duplication
 *       that produced four wrong versions of the audit contract in this repo (D-AX1b).</li>
 * </ul>
 */
public final class ReferenceSet {

    /** Canonical, on the classpath so the jar ships it and nothing keeps a second copy that can drift. */
    private static final String RESOURCE = "/reference-set.json";
    public static final String FILE_NAME = "CLAUDE.md";

    public record Resource(String id, String url, String why, String status, String appliesTo, String note) {
        public boolean agreed() {
            return "agreed".equals(status);
        }
    }

    /** What {@link #offer(Path)} found, so a caller can say why nothing will happen. */
    public enum Outcome {
        /** Nothing is signed off yet, so there is nothing to offer. */
        NOTHING_AGREED,
        /** The project already has one; it is the author's and is left alone. */
        EXISTS,
        /** No file, and there are agreed resources — a create can be offered. */
        CAN_CREATE
    }

    private ReferenceSet() {
    }

    @SuppressWarnings("unchecked")
    public static List<Resource> all() {
        try (InputStream in = ReferenceSet.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return List.of();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> root = (Map<String, Object>) Json.parse(text);
            Object raw = root.get("resources");
            if (!(raw instanceof List<?> list)) return List.of();
            List<Resource> resources = new ArrayList<>();
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> m)) continue;
                resources.add(new Resource(str(m, "id"), str(m, "url"), str(m, "why"),
                        str(m, "status"), str(m, "appliesTo"), str(m, "note")));
            }
            return List.copyOf(resources);
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** Agreed resources that apply to every project — those with no {@code appliesTo}. */
    public static List<Resource> agreed() {
        return agreedFor(null);
    }

    /**
     * Agreed resources for a project of the given kind (review N1).
     *
     * <p>{@code appliesTo} SELECTS rather than annotates: a Spring-only link rendered into a non-Spring
     * project charges every reader for an irrelevant fourth link, and an always-in-context file is a tax
     * on every turn. An entry with no {@code appliesTo} is always in.
     *
     * @param kind the project's authoring style, e.g. {@code "spring"}; null or blank selects only the
     *     always-on set.
     */
    public static List<Resource> agreedFor(String kind) {
        String k = kind == null ? "" : kind.strip().toLowerCase(java.util.Locale.ROOT);
        return all().stream()
                .filter(Resource::agreed)
                .filter(r -> r.appliesTo() == null || r.appliesTo().isBlank()
                        || r.appliesTo().strip().toLowerCase(java.util.Locale.ROOT).equals(k))
                .toList();
    }

    /**
     * The block a project's {@code CLAUDE.md} carries: links and one reason each, nothing restated.
     *
     * @return empty when nothing is agreed — callers must treat that as "offer nothing", not "write a
     *     heading with no links under it".
     */
    public static String markdown() {
        return markdown(null);
    }

    /** As {@link #markdown()}, for a project of the given kind (review N1). */
    public static String markdown(String kind) {
        List<Resource> agreed = agreedFor(kind);
        if (agreed.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        out.append("# Working in this project\n\n")
                .append("**Read these first — the canonical Fluxtion authoring resources. This file does ")
                .append("not repeat them, so that improving them improves every project at once.**\n\n");
        for (Resource r : agreed) {
            out.append("- <").append(r.url()).append("> — ").append(r.why()).append('\n');
        }
        out.append("\nBelow this line belongs only what those do **not** cover: this project's own paths, ")
                .append("commands and graph.\n");
        return out.toString();
    }

    /** Reports what could happen. Never writes — discovery offers, a person chooses (M35.4). */
    public static Outcome offer(Path projectRoot) {
        if (projectRoot == null) return Outcome.NOTHING_AGREED;
        // EXISTS is checked FIRST, deliberately. The author's file is the strongest fact here and does
        // not depend on whether anything has been signed off — ordering it after the agreed() check
        // would make the never-overwrite guarantee conditional, and leave it unexercised until the day
        // the set goes live, which is the worst possible day to first discover it.
        if (Files.exists(projectRoot.resolve(FILE_NAME))) return Outcome.EXISTS;
        return agreed().isEmpty() ? Outcome.NOTHING_AGREED : Outcome.CAN_CREATE;
    }

    /**
     * Creates the file, and refuses in every case {@link #offer(Path)} did not green-light.
     *
     * @return true when a file was written.
     */
    public static boolean create(Path projectRoot) throws IOException {
        return create(projectRoot, null);
    }

    /** As {@link #create(Path)}, selecting the links for a project of the given kind (review N1). */
    public static boolean create(Path projectRoot, String kind) throws IOException {
        if (offer(projectRoot) != Outcome.CAN_CREATE) return false;
        String body = markdown(kind);
        if (body.isBlank()) return false;
        writeNew(projectRoot.resolve(FILE_NAME), body);
        return true;
    }

    /**
     * The only place this class writes, split out so the last-resort guard is reachable by a test.
     *
     * <p>{@code create} re-checks {@link #offer} and returns false for a file that already exists, so this
     * is defence in depth for the window between that check and the write. {@code CREATE_NEW} makes losing
     * that race cost the write rather than the author's file — throwing here is the correct outcome,
     * because it means someone else wrote one in between (review F6).
     */
    static void writeNew(Path target, String body) throws IOException {
        Files.writeString(target, body, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
