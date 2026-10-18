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
 *       {@code spring-authoring/SKILL.md} ({@link #SPRING_AUTHORING_SKILL}). They are bundled with the publisher's
 *       permission (owner, 2026-10-18); the playground's licence statement is pending (UP-PG-10).</li>
 * </ul>
 *
 * <p><b>One substitution, after the pins (owner, 2026-10-18).</b> {@code add-a-node} carries the bundle marker that asks for
 * this project's regeneration command and its key need ({@link #REGENERATION_MARKER}). Once every selected byte matched
 * its pin, that ONE fenced block — and nothing else — is replaced by fixed text built from the analyser's own
 * first-run record for the starter ({@code TemplateArchive.commandsFor}, the post-install command list) and the
 * template's {@code keyNeed} ({@link #regenerationBlock}). With no record naming {@code ./generate.sh}, the marker is
 * kept as it is. {@code .claude/skills/.vendored.json} ({@link #RECORD}) lists, for every file written, the pinned
 * source digest and the written file's digest, and says which file was substituted.
 *
 * <p><b>Never overwrite.</b> A skill whose {@code SKILL.md} is already there is kept whole; any other file that exists is
 * kept. A linked {@code .claude} or {@code .claude/skills} refuses the whole set; a linked skill folder is kept
 * without writing through it. A resource that does not match its digest refuses the whole set before any write.
 * Filesystem writes are not transactional: a later failure can leave earlier files, without a new digest record.
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

    /** The canonical skill that carries {@link #REGENERATION_MARKER}. */
    public static final String ADD_A_NODE = "spring/add-a-node/SKILL.md";

    /** The bundle marker {@code add-a-node} carries: the whole fenced block, byte for byte as pinned. */
    public static final String REGENERATION_MARKER = """
            ```
            TODO(bundle): substitute this project's exact regeneration command (the Maven profile or script that
            invokes the Fluxtion source generator), and say whether it needs a Fluxtion API key.
            ```
            """;

    /** The digest record, under {@code .claude/skills}: never overwritten. */
    public static final String RECORD = ".vendored.json";
    public static final String SUBSTITUTED = "substituted: regeneration command";
    public static final String MARKER_KEPT = "marker kept: no first-run record for this starter";

    /**
     * The first-run steps that regenerate, in the record's order: fixed analyser strings, the only command tokens the
     * substituted text may carry. The run script is not regeneration and never appears.
     */
    static final List<String> REGENERATION_STEPS = List.of("./check-fluxtion-key.sh", "./setup.sh", "./validate.sh",
            "./generate.sh");

    public static final String KEY_BUILD = "**Fluxtion API key: needed.** The starter declares that its build needs a "
            + "Fluxtion API key (`keyNeed: build`), so regenerating needs one.";
    public static final String KEY_RUN = "**Fluxtion API key:** the starter declares that one is needed when the "
            + "processor runs (`keyNeed: run`).";
    public static final String KEY_NONE = "**Fluxtion API key: not needed.** The starter declares that its build needs "
            + "no Fluxtion API key (`keyNeed: none`).";
    public static final String KEY_UNDECLARED = "**Fluxtion API key:** the starter does not declare whether its build "
            + "needs one.";
    public static final String KEY_CHECK = "`./check-fluxtion-key.sh` checks whether one is set.";

    /**
     * The starter's own first-run record and key declaration — what the marker is substituted from. {@code firstRun} is
     * the post-install command list ({@code TemplateArchive.commandsFor}); {@code keyNeed} the template's word
     * ({@code build}, {@code run}, {@code none}, {@code other} or "").
     */
    public record FirstRun(List<String> firstRun, String keyNeed) {
        public FirstRun {
            firstRun = List.copyOf(firstRun == null ? List.of() : firstRun);
            keyNeed = keyNeed == null ? "" : keyNeed;
        }
    }

    /**
     * What vendoring did: skill names written and kept, each written file's sha-256 prefix, a note per file whose
     * bytes are not the pinned ones (or whose marker was kept), or why nothing was written.
     */
    public record Report(List<String> written, List<String> kept, List<String> digests, List<String> notes,
                         String refusal) {
        public Report {
            written = List.copyOf(written);
            kept = List.copyOf(kept);
            digests = List.copyOf(digests);
            notes = List.copyOf(notes);
        }

        public Report(List<String> written, List<String> kept, List<String> digests, String refusal) {
            this(written, kept, digests, List.of(), refusal);
        }
    }

    /** One file to write: its bytes, where they came from, the pinned digest of that source (null: the analyser's own). */
    record Source(byte[] bytes, String from, String pinned, String note) {
    }

    /** One skill to write: its name (the folder under {@code .claude/skills}) and its files, relative to that folder. */
    record Skill(String name, Map<String, Source> files) {
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

    /** Write the starter's skills with no first-run record: the regeneration marker is kept. */
    public Report vendor(Path projectRoot, String authoring, String host) {
        return vendor(projectRoot, authoring, host, null);
    }

    /**
     * The fixed text that replaces {@link #REGENERATION_MARKER}: the record's regeneration steps in a fenced block, then
     * the key note — or null when the record names no {@code ./generate.sh} (no record: the marker stays).
     */
    public static String regenerationBlock(FirstRun record) {
        if (record == null) return null;
        List<String> steps = new ArrayList<>();
        for (String command : record.firstRun()) {
            for (String known : REGENERATION_STEPS) {
                if (known.equals(command) && !steps.contains(known)) steps.add(known);    // the fixed string, never the input
            }
        }
        if (!steps.contains("./generate.sh")) return null;
        String key = switch (record.keyNeed()) {
            case "build" -> KEY_BUILD;
            case "run" -> KEY_RUN;
            case "none" -> KEY_NONE;
            default -> KEY_UNDECLARED;
        };
        if (!"none".equals(record.keyNeed()) && steps.contains("./check-fluxtion-key.sh")) key += " " + KEY_CHECK;
        return "```\n" + String.join("\n", steps) + "\n```\n\n"
                + "These are this project's regeneration steps, in order, from the analyser's first-run record for its "
                + "starter.\n" + key + "\n";
    }

    /**
     * Write the starter's skills into {@code projectRoot}; never throws for a refusal — the report says it. Every
     * selected byte is checked against its pin first; only then is {@code add-a-node}'s marker substituted from
     * {@code record} (null: no first-run record, the marker kept).
     */
    @SuppressWarnings("unchecked")
    public Report vendor(Path projectRoot, String authoring, String host, FirstRun record) {
        List<Skill> skills;
        try {
            byte[] indexBytes = resources.apply(INDEX);
            if (indexBytes == null) return refused("the skills index is not in this analyser");
            Map<String, Object> index = (Map<String, Object>) Json.parse(new String(indexBytes, StandardCharsets.UTF_8));
            Map<String, Object> pinned = (Map<String, Object>) index.get("sha256");
            Map<String, Map<String, Source>> byName = new LinkedHashMap<>();
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
                byName.computeIfAbsent(parts[1], k -> new LinkedHashMap<>()).put(parts[2],
                        new Source(bytes, INDEX + " " + path, w, null));
            }
            // the pins all held: now, and only now, the one marker block — never anything else
            String block = regenerationBlock(record);
            Map<String, Source> addANode = byName.get("add-a-node");
            Source pinnedSkill = addANode == null ? null : addANode.get("SKILL.md");
            if (pinnedSkill != null && pinnedSkill.from().endsWith(" " + ADD_A_NODE)) {
                String text = new String(pinnedSkill.bytes(), StandardCharsets.UTF_8);
                int at = text.indexOf(REGENERATION_MARKER);
                boolean once = at >= 0 && text.indexOf(REGENERATION_MARKER, at + 1) < 0;
                if (block != null && once) {
                    byte[] out = (text.substring(0, at) + block + text.substring(at + REGENERATION_MARKER.length()))
                            .getBytes(StandardCharsets.UTF_8);
                    addANode.put("SKILL.md", new Source(out, pinnedSkill.from(), pinnedSkill.pinned(), SUBSTITUTED));
                } else if (once) {
                    addANode.put("SKILL.md", new Source(pinnedSkill.bytes(), pinnedSkill.from(), pinnedSkill.pinned(),
                            MARKER_KEPT));
                }
            }
            if (Starters.SPRING.equals(authoring)) {
                Map<String, Source> files = new LinkedHashMap<>();
                files.put("SKILL.md", new Source(SPRING_AUTHORING_SKILL.getBytes(StandardCharsets.UTF_8),
                        "the analyser's own introduction", null, null));
                for (var e : PLAYGROUND.entrySet()) {
                    byte[] bytes = resources.apply(e.getValue()[1]);
                    if (bytes == null) return refused("a bundled playground page is missing: " + e.getValue()[0]);
                    if (!e.getValue()[2].equals(sha256(bytes))) {
                        return refused("a bundled playground page does not match its pinned digest: " + e.getValue()[0]);
                    }
                    files.put(e.getKey().substring("spring-authoring/".length()),
                            new Source(bytes, e.getValue()[0], e.getValue()[2], null));
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
        List<String> notes = new ArrayList<>();
        List<Object> record = new ArrayList<>();
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
                for (Map.Entry<String, Source> f : s.files().entrySet()) {
                    Path target = dir.resolve(f.getKey()).normalize();
                    if (!target.startsWith(dir)) return refused("an unexpected skill file: " + f.getKey());
                    Path parent = target.getParent();
                    if (Files.isSymbolicLink(parent)) continue;
                    Files.createDirectories(parent);
                    try {
                        Source src = f.getValue();
                        Files.write(target, src.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                        String writtenDigest = sha256(src.bytes());
                        digests.add(s.name() + "/" + f.getKey() + " " + writtenDigest.substring(0, 16));
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("path", s.name() + "/" + f.getKey());
                        row.put("source", src.from());
                        row.put("pinnedSha256", src.pinned());
                        row.put("writtenSha256", writtenDigest);
                        if (src.note() != null) {
                            row.put("note", src.note());
                            notes.add(s.name() + "/" + f.getKey() + ": " + src.note());
                        }
                        record.add(row);
                        any = true;
                    } catch (FileAlreadyExistsException exists) {
                        // never overwritten: an existing file stays exactly as it was
                    }
                }
                if (any) written.add(s.name());
                else kept.add(s.name());
            }
            if (!record.isEmpty()) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("vendoredBy", "Fluxtion Audit Log Analyser");
                doc.put("files", record);
                try {
                    Files.write(base.resolve(RECORD), (Json.write(doc) + "\n").getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (FileAlreadyExistsException exists) {
                    notes.add(RECORD + ": kept, it was already there");     // never overwritten, like every file here
                }
            }
        } catch (IOException e) {
            return new Report(written, kept, digests, notes, "the skills could not be written (" + e.getMessage() + ")");
        }
        return new Report(written, kept, digests, notes, null);
    }

    private static Report refused(String why) {
        return new Report(List.of(), List.of(), List.of(), List.of(), why);
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
