package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * M43.7 — find the skill-shaped runbooks a project already has, so <i>Add runbook…</i> can OFFER them.
 *
 * <h2>Why this exists</h2>
 * A runbook may be written as a skill — a {@code SKILL.md} with `name`/`description` frontmatter — so one
 * file serves the team and an AI harness alike. But a project can already contain several of those and the
 * analyser made you go and find them with a file chooser. If a project template ships a standard skill,
 * that is a file the user may not even know is there.
 *
 * <h2>Harness-neutral by construction — do NOT add vendor paths here</h2>
 * A skill is recognised by its <b>file name</b>, anywhere under the project root. No path segment is
 * privileged: {@code .claude/skills/restart/SKILL.md} and {@code ops/restart/SKILL.md} are found on
 * identical terms, and adding a hardcoded list of vendor directories would be a regression rather than a
 * feature — it would rank one tool's layout above another's while excluding every project that uses
 * neither.
 *
 * <p>This follows the rule M42 already established for MCP clients: the analyser never encodes a
 * third-party harness's file layout ({@code ClaudeMcpClient} does not parse {@code ~/.claude.json};
 * {@code CodexMcpClient} does not read {@code config.toml}). Layouts are theirs to change.
 *
 * <p>The neutral channel is the analyser itself: confirmed runbooks are served in
 * {@code context.runbooks[]} with their descriptions, so <b>an agent is told which runbooks to load and
 * never has to know any convention</b>. That is what makes this work for a harness we have never heard of.
 *
 * <h2>It offers; it never selects (M35.4)</h2>
 * This returns candidates. Nothing is added to the profile until a person picks one and confirms the
 * name and description — the same rule graph discovery follows, and for the same reason: a tool that
 * silently adopts what it found is a tool you have to check up on.
 *
 * <h2>Bounded on purpose</h2>
 * A project root can be a monorepo. The walk is depth- and count-limited, skips the directories that
 * make a tree large without making it interesting, and never leaves the project root. A discovery
 * feature that hangs the UI on somebody's workspace would be worse than no discovery feature.
 */
public final class SkillDiscovery {

    private SkillDiscovery() {
    }

    /** Deep enough for a {@code <dir>/skills/<name>/SKILL.md} layout and a few levels of project nesting. */
    static final int MAX_DEPTH = 7;
    /** More than any project needs to show in a list; the caller says when it truncated. */
    public static final int MAX_RESULTS = 50;

    /**
     * The one thing that makes a file a skill. Compared case-insensitively — people write skill.md too.
     * Deliberately the ONLY criterion: see the harness-neutrality note on the class.
     */
    private static final String SKILL_FILE = "skill.md";

    /** Big, uninteresting, or not ours. Skipping these is what keeps the walk cheap. */
    private static final Set<String> SKIP = Set.of(
            ".git", "target", "build", "out", "node_modules", ".idea", ".gradle", ".mvn", "venv",
            ".venv", "__pycache__", "dist", ".DS_Store");

    /**
     * @param path        project-relative, forward-slashed — the form a pointer is stored in
     * @param name        a name for it, from frontmatter where there is one, else from its directory
     * @param description from frontmatter, or null
     * @param declared    already a runbook in this profile: SHOWN, not hidden, so a file that is present
     *                    but missing from the list never leaves the user wondering why
     */
    public record Candidate(String path, String name, String description, boolean declared) {
    }

    public record Found(List<Candidate> candidates, boolean truncated) {
    }

    /** Walk {@code projectRoot} for skill-shaped files. Never throws: a scan is a convenience. */
    public static Found find(Path projectRoot, Map<String, Runbooks.Pointer> declared) {
        List<Candidate> out = new ArrayList<>();
        if (projectRoot == null || !Files.isDirectory(projectRoot)) return new Found(List.of(), false);
        Path root = projectRoot.toAbsolutePath().normalize();

        Set<String> declaredPaths = declared == null ? Set.of()
                : declared.values().stream().filter(java.util.Objects::nonNull)
                        .map(Runbooks.Pointer::path).collect(java.util.stream.Collectors.toSet());

        boolean[] truncated = {false};
        try {
            Files.walkFileTree(root, Set.of(), MAX_DEPTH, new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                    if (out.size() >= MAX_RESULTS) { truncated[0] = true; return FileVisitResult.TERMINATE; }
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    // a symlink could point anywhere, including outside the project — do not follow it
                    if (!dir.equals(root) && (SKIP.contains(name) || Files.isSymbolicLink(dir))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                    if (out.size() >= MAX_RESULTS) { truncated[0] = true; return FileVisitResult.TERMINATE; }
                    Path fileName = file.getFileName();
                    if (fileName == null
                        || !SKILL_FILE.equals(fileName.toString().toLowerCase(Locale.ROOT))) {
                        return FileVisitResult.CONTINUE;
                    }
                    // review F1: symlinked DIRECTORIES were skipped but a symlinked FILE was not, so a
                    // link could still have the frontmatter read from outside the project. Same rule,
                    // both shapes — a pointer must stay inside the project, so discovery must too.
                    if (Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE;
                    String rel = root.relativize(file).toString().replace('\\', '/');
                    // only offer what could actually be STORED: showing a candidate the gate would refuse
                    // puts a refusal in front of the user for something they did not type
                    if (Runbooks.refusePointer("skill", rel).isPresent()) return FileVisitResult.CONTINUE;

                    var suggestion = SkillFrontmatter.read(file);
                    String name = SkillFrontmatter.usableName(suggestion.name())
                            .or(() -> SkillFrontmatter.usableName(directoryName(file)))
                            .orElse(null);
                    if (name == null) return FileVisitResult.CONTINUE;   // nothing nameable — do not offer
                    out.add(new Candidate(rel, name,
                            SkillFrontmatter.usableDescription(suggestion.description()).orElse(null),
                            declaredPaths.contains(rel)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;    // unreadable is not a reason to abandon the scan
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            // a partial list is still useful; a discovery that throws would take the dialog with it
        }
        out.sort(Comparator.comparing(Candidate::path));
        return new Found(List.copyOf(out), truncated[0]);
    }

    /** {@code .claude/skills/restart/SKILL.md} → {@code restart}: the directory names the skill. */
    private static String directoryName(Path skillFile) {
        Path parent = skillFile.getParent();
        return parent == null || parent.getFileName() == null ? null : parent.getFileName().toString();
    }
}
