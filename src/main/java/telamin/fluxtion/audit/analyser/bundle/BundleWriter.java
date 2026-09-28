package telamin.fluxtion.audit.analyser.bundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evidence bundle capture, the FILE work (convergence, 2026-09-28): what the capture skill's steps 3 and 5 did in prose,
 * as one method. It decides nothing. The session's {@code evidenceCapture} node decided that this capture may run, and
 * decides afterwards whether it stands; this assembles the members, packs, and reports what it did.
 *
 * <p>Everything read from the live session was taken before this runs, on the session's thread: the settings bytes
 * AFTER the project's pending write was flushed, and, for an excerpt, its text and digests. So this may run on any
 * thread without reading state another event could change under it.
 *
 * <p>The working folder is created beside the output and deleted in every case, success or not; on any failure the
 * output is deleted too, so a refused capture leaves nothing behind.
 */
public final class BundleWriter {

    private BundleWriter() {
    }

    /**
     * @param out            the {@code .fexp} to write; never overwritten
     * @param log            the open log's file, copied whole when {@code excerpt} is null
     * @param graph          the open graph's file, or null
     * @param settingsName   the settings file's name ({@code *.fluxtion-settings} for a project profile)
     * @param settingsBytes  its bytes, read after the project's pending write was flushed
     * @param notes          the author's account, packed as {@code notes/NOTES.md}, or null/blank for none
     * @param excerpt        what was taken for a time-window excerpt, or null for the whole log
     */
    public record Job(Path out, Path log, Path graph, String settingsName, byte[] settingsBytes, String notes,
                      BundleExcerpt.Taken excerpt, Instant createdAt, String version, int thresholdMb,
                      String expectedLogSha256) { }

    /** What was written: the identity, and every line the author must see (left out, dangling, redacted, excerpt). */
    public record Written(String identity, List<String> lines) { }

    public static final String NOTES = "notes/NOTES.md";

    public static Written write(Job job) throws IOException {
        if (Files.exists(job.out())) throw new IOException("will not overwrite " + job.out());
        Path parent = job.out().toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path folder = Files.createTempDirectory(parent, ".capture-");
        try {
            List<String> lines = new ArrayList<>();
            Path logDir = Files.createDirectories(folder.resolve("log"));
            Path logMember = logDir.resolve(job.log().getFileName().toString());
            BundleProfile.Rebase rebase = null;
            Map<String, Object> cut = null;
            if (job.excerpt() == null) {
                Files.copy(job.log(), logMember);
                // pausing Follow stops the analyser reading, not the producer writing: say so when the copy is not the
                // bytes the session first read, because steps bound to that read may not be current on the other side
                if (job.expectedLogSha256() != null) {
                    String got;
                    try (var in = Files.newInputStream(logMember)) {
                        got = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                .digest(in.readAllBytes()));
                    } catch (java.security.NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    }
                    if (!got.equalsIgnoreCase(job.expectedLogSha256())) {
                        lines.add("note: the copied log is not the bytes first read (it grew under Follow, or changed);"
                                + " a step bound to the first read may not be current on the other side");
                    }
                }
            } else {
                BundleExcerpt.Range r = job.excerpt().range();
                BundleExcerpt.Checked checked = BundleExcerpt.write(job.excerpt(), logMember, job.thresholdMb());
                rebase = new BundleProfile.Rebase(r.first(), r.last(), checked.runBasis(), checked.index());
                cut = new LinkedHashMap<>();
                cut.put("firstRecord", r.first());
                cut.put("lastRecord", r.last());
                cut.put("sourceRecords", r.sourceRecords());
                if (r.from() != null) cut.put("from", r.from());
                if (r.to() != null) cut.put("to", r.to());
                lines.add("excerpt: records " + r.first() + ".." + r.last() + " of " + r.sourceRecords()
                        + ", re-read and matched record by record; walks and reports are re-based onto it");
            }
            if (job.graph() != null) {
                Path graphDir = Files.createDirectories(folder.resolve("graph"));
                Files.copy(job.graph(), graphDir.resolve(job.graph().getFileName().toString()));
            }
            // the settings as flushed, under a name the profile loader reads the same way the original was read
            Path settings = Files.createDirectories(folder.resolve(".settings/.analyser")).resolve(job.settingsName());
            Files.write(settings, job.settingsBytes());
            Path profileDir = Files.createDirectories(folder.resolve("profile"));
            BundleProfile.Export x = BundleProfile.export(settings, profileDir.resolve("project.fluxtion-settings"), rebase);
            deleteTree(folder.resolve(".settings"));
            for (String l : x.leftOut()) lines.add("left out: " + l);
            for (String d : x.dangling()) lines.add("dangling: " + d);
            for (String r : x.redacted()) lines.add("redacted: " + r);
            if (job.notes() != null && !job.notes().isBlank()) {
                Path notes = Files.createDirectories(folder.resolve("notes")).resolve("NOTES.md");
                Files.writeString(notes, job.notes().endsWith("\n") ? job.notes() : job.notes() + "\n", StandardCharsets.UTF_8);
            }
            String identity = EvidenceBundle.pack(folder, job.out(), job.createdAt(), job.version(), cut);
            return new Written(identity, List.copyOf(lines));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(job.out());
            throw e;
        } finally {
            deleteTree(folder);
        }
    }

    /** Remove a bundle this capture wrote, and its working folder if one is left: what a refused capture must not keep. */
    public static void delete(Path out) throws IOException {
        Files.deleteIfExists(out);
        Path parent = out.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) return;
        try (var list = Files.list(parent)) {
            for (Path p : list.filter(p -> p.getFileName().toString().startsWith(".capture-")).toList()) deleteTree(p);
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
