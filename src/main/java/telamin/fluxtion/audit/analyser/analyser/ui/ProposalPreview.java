package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * M74 P2b (spec §5.6 step 5): the analyser's OWN read-only check of each proposed item — what the review dialog shows as
 * the analyser's verdict, beside Claude's reason. It OBSERVES (exists, the right type, inside the set-up folder after
 * resolving links, a count, a class that resolves) and reports facts; {@code onboardingProposal} decides what they mean.
 * Nothing is opened, written or run. A verdict that cannot be computed is {@code UNKNOWN}, never "probably" (D-A2).
 */
public final class ProposalPreview {

    private ProposalPreview() {
    }

    /** One item to check, its words resolved from the store by the frame (the effect carries references, R6). */
    public record Item(String itemId, String kind, String path, String className) {
    }

    /** One checked item, before the proposal id is attached. */
    public record Checked(String itemId, String verdict, boolean outside, String refusal) {
        SessionEvents.ProposalItemPreviewed withProposal(String proposalId) {
            return new SessionEvents.ProposalItemPreviewed(proposalId, itemId, verdict, outside, refusal);
        }
    }

    private static final int MAX_COUNT = 10_000;

    public static List<Checked> check(Path setupDir, List<Item> items, Predicate<String> resolves) {
        List<Checked> out = new ArrayList<>();
        Path base = real(setupDir);
        for (Item i : items) out.add(one(base, i, resolves));
        return out;
    }

    static Checked one(Path base, Item i, Predicate<String> resolves) {
        if ("processor".equals(i.kind()) && i.path() == null) {
            boolean found = i.className() != null && resolves != null && resolves.test(i.className());
            return new Checked(i.itemId(), found ? "RESOLVES (pairing UNKNOWN)" : "NOT_IN_SOURCES", false,
                    null);
        }
        if (base == null) return new Checked(i.itemId(), "UNKNOWN", true, null);
        Path given = Path.of(i.path());
        Path at = (given.isAbsolute() ? given : base.resolve(given)).normalize();
        if (!Files.exists(at)) {
            // a path that does not exist is judged outside when it names a place outside, else not found
            return new Checked(i.itemId(), "NOT_FOUND", !at.startsWith(base), at.startsWith(base) ? "NOT_FOUND" : null);
        }
        Path real = real(at);
        boolean outside = real == null || !real.startsWith(base);
        if (outside) return new Checked(i.itemId(), "OUTSIDE", true, null);
        return switch (i.kind()) {
            case "sourceRoot" -> telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.tier1(real,
                    telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.home()) != null
                    // P5: never the home folder or a credential folder, whatever the set-up folder is
                    ? new Checked(i.itemId(), "SECRET_FOLDER", false, "SECRET_FOLDER")
                    : Files.isDirectory(real)
                    ? new Checked(i.itemId(), "DIRECTORY (" + javaFiles(real) + " .java files)", false, null)
                    : new Checked(i.itemId(), "NOT_A_DIRECTORY", false, "WRONG_TYPE");
            case "graphml" -> Files.isRegularFile(real) && name(real).endsWith(".graphml")
                    ? new Checked(i.itemId(), "READABLE (fit UNKNOWN until opened)", false, null)
                    : new Checked(i.itemId(), "NOT_GRAPHML", false, "WRONG_TYPE");
            case "runbook" -> Files.isRegularFile(real) && name(real).endsWith(".md")
                    ? new Checked(i.itemId(), "MARKDOWN", false, null)
                    : new Checked(i.itemId(), "NOT_MARKDOWN", false, "WRONG_TYPE");
            case "logToOpen" -> Files.isRegularFile(real)
                    ? new Checked(i.itemId(), "READABLE (" + size(real) + " bytes, not opened)", false, null)
                    : new Checked(i.itemId(), "NOT_A_FILE", false, "WRONG_TYPE");
            case "processor" -> {
                if (!Files.isRegularFile(real) || !name(real).endsWith(".java")) {
                    yield new Checked(i.itemId(), "NOT_JAVA", false, "WRONG_TYPE");
                }
                String fqn = i.className() != null ? i.className() : fqnOf(real);
                boolean found = fqn != null && resolves != null && resolves.test(fqn);
                yield new Checked(i.itemId(), found ? "RESOLVES (pairing UNKNOWN)" : "SOURCE_FILE (not in the source roots yet)",
                        false, null);
            }
            default -> new Checked(i.itemId(), "UNKNOWN", false, "WRONG_TYPE");
        };
    }

    /**
     * Review S3 (P2, 2026-10-17): where an item's path is AT APPLY, re-checked — the preview's verdict is minutes old, and a
     * link swapped in since then must not carry the apply outside the set-up folder. The path is resolved against the
     * set-up folder's REAL path and then made real itself (links followed); the result is that real path, or a refusal.
     *
     * @return {@code {real path, null}} inside the folder, or {@code {null, refusal}}
     */
    public record AtApply(Path real, String refusal) {
    }

    public static AtApply atApply(Path setupDir, String itemPath) {
        Path base = real(setupDir);
        if (base == null || itemPath == null) return new AtApply(null, "there is no set-up folder to apply it in");
        Path given = Path.of(itemPath);
        Path at = (given.isAbsolute() ? given : base.resolve(given)).normalize();
        if (!Files.exists(at)) {
            return new AtApply(null, at.startsWith(base) ? "not found" : "OUTSIDE the set-up folder");
        }
        Path real = real(at);
        if (real == null || !real.startsWith(base)) {
            return new AtApply(null, "OUTSIDE the set-up folder (it resolves elsewhere now)");
        }
        return new AtApply(real, null);
    }

    /** The most of a Java file read for its package line (review N-d): a package line is at the top. */
    static final int FQN_READ_LIMIT = 64 * 1024;

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    /** A Java file's class name from its package line and file name, or null. */
    static String fqnOf(Path javaFile) {
        if (javaFile == null || !Files.isRegularFile(javaFile)) return null;
        String file = name(javaFile);
        if (!file.endsWith(".java")) return null;
        String simple = javaFile.getFileName().toString();
        simple = simple.substring(0, simple.length() - ".java".length());
        try {
            String head;
            try (var in = Files.newInputStream(javaFile)) {
                head = new String(in.readNBytes(FQN_READ_LIMIT), java.nio.charset.StandardCharsets.UTF_8);
            }
            Matcher m = PACKAGE.matcher(head);
            return m.find() ? m.group(1) + "." + simple : simple;
        } catch (IOException e) {
            return null;
        }
    }

    private static long javaFiles(Path dir) {
        try (Stream<Path> s = Files.walk(dir, 32)) {
            return s.limit(MAX_COUNT * 4L).filter(p -> name(p).endsWith(".java")).limit(MAX_COUNT).count();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static long size(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }

    private static String name(Path p) {
        return p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private static Path real(Path p) {
        if (p == null) return null;
        try {
            return p.toRealPath();
        } catch (IOException e) {
            try {
                return p.toRealPath(LinkOption.NOFOLLOW_LINKS);
            } catch (IOException ex) {
                return null;
            }
        }
    }
}
