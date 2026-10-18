package telamin.fluxtion.audit.analyser.analyser.template;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Option 2 (owner, 2026-10-18: "start with a template, then load the skills"): the skills a new starter project gets,
 * written from the analyser's OWN jar — never fetched at run time — under {@code <project>/.claude/skills/}, the layout
 * a harness auto-loads (docs/skills/README.md).
 *
 * <ul>
 *   <li><b>The canonical set</b>, {@code m19-skills/2}: {@code common} always, plus the specialisations the starter's
 *       facts name — {@code spring} for a Spring-authored graph, {@code mongoose} or {@code embedded} by host. Every
 *       selected file is checked against the index's per-path sha-256 before anything is written
 *       ({@code CanonicalSkillsTest} pins the jar's copies to the same index).</li>
 *   <li><b>The playground's Spring design skill and contract</b>, for a Spring starter: verbatim copies of
 *       {@code fluxtion-playground.dev/spring-authoring/skill.md} and {@code contract.md}, pinned by sha-256 when they were
 *       fetched ({@link #PLAYGROUND}), under {@code spring-authoring/reference/}, introduced by the analyser's own
 *       {@code spring-authoring/SKILL.md} ({@link #SPRING_AUTHORING_SKILL}).</li>
 * </ul>
 *
 * <p><b>Never overwrite.</b> A skill whose {@code SKILL.md} is already there is kept whole; any other file that exists is
 * kept. A link anywhere on the way ({@code .claude}, {@code .claude/skills}, a skill's folder) refuses the whole set, as
 * does one resource that does not match its digest: a tampered jar writes nothing.
 */
public final class StarterSkills {

    /** Where the canonical skills sit in the jar (the pom copies {@code docs/skills} there). */
    public static final String ROOT = "/starter-skills/";
    public static final String INDEX = "m19-skills/2/index.json";

    /** The playground pages, as fetched and pinned (2026-10-18): published path → {source URL, sha-256}. */
    public static final Map<String, String[]> PLAYGROUND;

    static {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("spring-authoring/reference/skill.md", new String[]{
                "https://fluxtion-playground.dev/spring-authoring/skill.md", "playground/spring-authoring/skill.md",
                "9875abf6d5ad4f9662ee985e21013c4e6292d8a1843cb7a1ec115610a636929e"});
        m.put("spring-authoring/reference/contract.md", new String[]{
                "https://fluxtion-playground.dev/spring-authoring/contract.md", "playground/spring-authoring/contract.md",
                "a00cfdecfdf80183491757cf6d93c210bf95b61293c61a45359cd1bdb258f0a8"});
        PLAYGROUND = java.util.Collections.unmodifiableMap(m);
    }

    /** The analyser's own introduction to the playground's two pages: fixed text, nothing interpolated. */
    public static final String SPRING_AUTHORING_SKILL = """
            ---
            name: spring-authoring
            description: Lead the Spring design conversation for this Fluxtion project as a design partner, and emit the one Spring XML design the starter imports. Use at the start of a new Spring project, or when the person wants to add or change nodes, events or services in the design.
            ---

            # Spring design partner

            The two files in `reference/` are verbatim copies of the Fluxtion playground's Spring authoring pages, pinned
            by sha-256 when this analyser was built:

            - [reference/skill.md](reference/skill.md) — the procedure: how to run the design conversation
              (https://fluxtion-playground.dev/spring-authoring/skill.md)
            - [reference/contract.md](reference/contract.md) — the exact Spring XML shape the starter imports
              (https://fluxtion-playground.dev/spring-authoring/contract.md)

            Read `reference/skill.md` first and follow it; `reference/contract.md` is authoritative for what to emit.
            This project's PROJECT.md and RUNBOOK.md name its own commands: never invent one.
            """;

    /** What vendoring did: skill names written and kept, each written file's sha-256 prefix, or why nothing was. */
    public record Report(List<String> written, List<String> kept, List<String> digests, String refusal) {
        public Report {
            written = List.copyOf(written);
            kept = List.copyOf(kept);
            digests = List.copyOf(digests);
        }
    }

    /** One skill to write: its name (the folder under {@code .claude/skills}) and its files, relative to that folder. */
    record Skill(String name, Map<String, byte[]> files) {
    }

    private final Function<String, byte[]> resources;

    /** The analyser's own jar. */
    public StarterSkills() {
        this(StarterSkills::classpath);
    }

    /** A test seam: {@code resources} answers a path under {@link #ROOT}, or null when absent. */
    public StarterSkills(Function<String, byte[]> resources) {
        this.resources = resources;
    }

    /**
     * The selection for a starter: {@code common}, then {@code spring} when {@code authoring} is spring, then
     * {@code mongoose} or {@code embedded} by {@code host} — paths under {@link #ROOT}, in that order.
     */
    @SuppressWarnings("unchecked")
    List<String> selection(Map<String, Object> index, String authoring, String host) {
        List<String> out = new ArrayList<>((List<String>) index.get("common"));
        Map<String, Object> spec = (Map<String, Object>) index.get("specialisations");
        List<String> names = new ArrayList<>();
        if (Starters.SPRING.equals(authoring)) names.add("spring");
        names.add("mongoose".equals(host) ? "mongoose" : "embedded");
        for (String n : names) {
            Map<String, Object> s = (Map<String, Object>) spec.get(n);
            if (s == null) continue;
            out.addAll((List<String>) s.get("skills"));
            if (s.get("files") instanceof List<?> files) for (Object f : files) out.add((String) f);
        }
        return out;
    }

    /** Write the starter's skills into {@code projectRoot}; never throws for a refusal — the report says it. */
    @SuppressWarnings("unchecked")
    public Report vendor(Path projectRoot, String authoring, String host) {
        List<Skill> skills;
        try {
            byte[] indexBytes = resources.apply(INDEX);
            if (indexBytes == null) return refused("the skills index is not in this analyser");
            Map<String, Object> index = (Map<String, Object>) Json.parse(new String(indexBytes, StandardCharsets.UTF_8));
            Map<String, Object> pinned = (Map<String, Object>) index.get("sha256");
            Map<String, Map<String, byte[]>> byName = new LinkedHashMap<>();
            for (String path : selection(index, authoring, host)) {
                byte[] bytes = resources.apply(path);
                Object want = pinned == null ? null : pinned.get(path);
                if (bytes == null) return refused("a bundled skill is missing: " + path);
                if (!(want instanceof String w) || !w.equals(sha256(bytes))) {
                    return refused("a bundled skill does not match its pinned digest: " + path);
                }
                String[] parts = path.split("/");
                // <tier>/<skill>/<file>: the skill's folder is its own name, as a harness loads it
                if (parts.length != 3) return refused("an unexpected skill path: " + path);
                byName.computeIfAbsent(parts[1], k -> new LinkedHashMap<>()).put(parts[2], bytes);
            }
            if (Starters.SPRING.equals(authoring)) {
                Map<String, byte[]> files = new LinkedHashMap<>();
                files.put("SKILL.md", SPRING_AUTHORING_SKILL.getBytes(StandardCharsets.UTF_8));
                for (var e : PLAYGROUND.entrySet()) {
                    byte[] bytes = resources.apply(e.getValue()[1]);
                    if (bytes == null) return refused("a bundled playground page is missing: " + e.getValue()[0]);
                    if (!e.getValue()[2].equals(sha256(bytes))) {
                        return refused("a bundled playground page does not match its pinned digest: " + e.getValue()[0]);
                    }
                    files.put(e.getKey().substring("spring-authoring/".length()), bytes);
                }
                byName.put("spring-authoring", files);
            }
            skills = new ArrayList<>();
            for (var e : byName.entrySet()) skills.add(new Skill(e.getKey(), e.getValue()));
        } catch (RuntimeException e) {
            return refused("the skills index could not be read (" + e.getMessage() + ")");
        }
        return write(projectRoot, skills);
    }

    private static Report write(Path projectRoot, List<Skill> skills) {
        List<String> written = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        List<String> digests = new ArrayList<>();
        try {
            Path root = projectRoot.toRealPath();
            Path claude = root.resolve(".claude");
            Path base = claude.resolve("skills");
            for (Path p : List.of(claude, base)) {
                if (Files.isSymbolicLink(p)) return refused("a link was found at " + root.relativize(p) + ", so no skill was written");
                if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    return refused(root.relativize(p) + " is not a folder, so no skill was written");
                }
            }
            Files.createDirectories(base);
            for (Skill s : skills) {
                Path dir = base.resolve(s.name());
                if (Files.isSymbolicLink(dir)) {
                    kept.add(s.name());
                    continue;
                }
                if (Files.exists(dir.resolve("SKILL.md"), LinkOption.NOFOLLOW_LINKS)) {
                    kept.add(s.name());                    // a skill already there is the project's: never overwritten
                    continue;
                }
                boolean any = false;
                for (var f : s.files().entrySet()) {
                    Path target = dir.resolve(f.getKey()).normalize();
                    if (!target.startsWith(dir)) return refused("an unexpected skill file: " + f.getKey());
                    Path parent = target.getParent();
                    if (Files.isSymbolicLink(parent)) continue;
                    Files.createDirectories(parent);
                    try {
                        Files.write(target, f.getValue(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                        digests.add(s.name() + "/" + f.getKey() + " " + sha256(f.getValue()).substring(0, 16));
                        any = true;
                    } catch (FileAlreadyExistsException exists) {
                        // never overwritten: an existing file stays exactly as it was
                    }
                }
                if (any) written.add(s.name());
                else kept.add(s.name());
            }
        } catch (IOException e) {
            return new Report(written, kept, digests, "the skills could not be written (" + e.getMessage() + ")");
        }
        return new Report(written, kept, digests, null);
    }

    private static Report refused(String why) {
        return new Report(List.of(), List.of(), List.of(), why);
    }

    static byte[] classpath(String path) {
        try (InputStream in = StarterSkills.class.getResourceAsStream(ROOT + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
