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

    public static List<Resource> agreed() {
        return all().stream().filter(Resource::agreed).toList();
    }

    /**
     * The block a project's {@code CLAUDE.md} carries: links and one reason each, nothing restated.
     *
     * @return empty when nothing is agreed — callers must treat that as "offer nothing", not "write a
     *     heading with no links under it".
     */
    public static String markdown() {
        List<Resource> agreed = agreed();
        if (agreed.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        out.append("# Working in this project\n\n")
                .append("**Read these first — the canonical Fluxtion authoring resources. This file does ")
                .append("not repeat them, so that improving them improves every project at once.**\n\n");
        for (Resource r : agreed) {
            out.append("- <").append(r.url()).append("> — ").append(r.why());
            if (r.appliesTo() != null && !r.appliesTo().isBlank()) {
                out.append(" *(").append(r.appliesTo()).append("-authored projects)*");
            }
            out.append('\n');
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
        if (offer(projectRoot) != Outcome.CAN_CREATE) return false;
        String body = markdown();
        if (body.isBlank()) return false;
        Path target = projectRoot.resolve(FILE_NAME);
        // createNew: the offer/create pair is not atomic, and losing a race must not cost the author
        // their file. Failing here is correct — it means someone else wrote one in between.
        Files.writeString(target, body, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        return true;
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
