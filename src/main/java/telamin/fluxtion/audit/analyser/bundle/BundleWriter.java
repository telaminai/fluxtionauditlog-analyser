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
 *
 * <p><b>Working folders are OWNED (convergence review, 2026-09-28).</b> Two analysers can share one exchange
 * directory (a project-relative exchange directory, #21, is shared by everyone who opens the project), so a folder
 * beside the output may be a neighbour's capture in progress. Each capture writes an {@link #OWNER} marker naming its
 * host and holds an exclusive OS lock on it for as long as it runs; the OS releases the lock when the process ends,
 * however it ends. A folder is reaped only on positive evidence that its owner is dead: a marker from THIS host whose
 * lock can be taken. Anything else, unmarked, another host's, or unreadable, is left alone. No clock is read, so no
 * clock step, coarse timestamp or server-side mtime can make a live capture look dead, and no guessed age is needed:
 * nothing bounds how long a whole-log copy to a slow mount takes.
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
                      String expectedLogSha256, boolean readSoFar) {
        /** A capture of a log that is not still growing. */
        public Job(Path out, Path log, Path graph, String settingsName, byte[] settingsBytes, String notes,
                   BundleExcerpt.Taken excerpt, Instant createdAt, String version, int thresholdMb, String expectedLogSha256) {
            this(out, log, graph, settingsName, settingsBytes, notes, excerpt, createdAt, version, thresholdMb,
                    expectedLogSha256, false);
        }
    }

    /** What was written: the identity, and every line the author must see (left out, dangling, redacted, excerpt). */
    public record Written(String identity, List<String> lines) { }

    public static final String NOTES = "notes/NOTES.md";

    /** The marker in a working folder: its host, under an exclusive lock held for the capture's whole life. */
    static final String OWNER = ".owner";

    public static Written write(Job job) throws IOException {
        if (Files.exists(job.out())) throw new IOException("will not overwrite " + job.out());
        Path parent = job.out().toAbsolutePath().getParent();
        Files.createDirectories(parent);
        reapCorpses(parent);
        Path folder = Files.createTempDirectory(parent, ".capture-");
        try (java.nio.channels.FileChannel owner = claim(folder)) {
            // the members live in their own folder, so the owner marker is never packed
            Path payload = Files.createDirectories(folder.resolve("bundle"));
            List<String> lines = new ArrayList<>();
            Path logDir = Files.createDirectories(payload.resolve("log"));
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
                if (job.readSoFar()) {
                    cut.put("readSoFar", true);
                    lines.add("read so far: the log was still growing under Follow, so the bundle holds the "
                            + r.sourceRecords() + " records read before the capture, not what was written after them");
                }
                lines.add("excerpt: records " + r.first() + ".." + r.last() + " of " + r.sourceRecords()
                        + (job.readSoFar() ? " read" : "")
                        + ", re-read and matched record by record; walks and reports are re-based onto it");
            }
            if (job.graph() != null) {
                Path graphDir = Files.createDirectories(payload.resolve("graph"));
                Files.copy(job.graph(), graphDir.resolve(job.graph().getFileName().toString()));
            }
            // the settings as flushed, under a name the profile loader reads the same way the original was read
            Path settings = Files.createDirectories(folder.resolve(".settings/.analyser")).resolve(job.settingsName());
            Files.write(settings, job.settingsBytes());
            Path profileDir = Files.createDirectories(payload.resolve("profile"));
            BundleProfile.Export x = BundleProfile.export(settings, profileDir.resolve("project.fluxtion-settings"), rebase);
            deleteTree(folder.resolve(".settings"));
            for (String l : x.leftOut()) lines.add("left out: " + l);
            for (String d : x.dangling()) lines.add("dangling: " + d);
            for (String r : x.redacted()) lines.add("redacted: " + r);
            if (job.notes() != null && !job.notes().isBlank()) {
                Path notes = Files.createDirectories(payload.resolve("notes")).resolve("NOTES.md");
                Files.writeString(notes, job.notes().endsWith("\n") ? job.notes() : job.notes() + "\n", StandardCharsets.UTF_8);
            }
            String identity = EvidenceBundle.pack(payload, job.out(), job.createdAt(), job.version(), cut);
            return new Written(identity, List.copyOf(lines));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(job.out());
            throw e;
        } finally {
            deleteTree(folder);
        }
    }

    /**
     * Remove a bundle a refused capture wrote, and reap any working folder a KILLED capture left beside it. The refused
     * capture's own folder is already gone: {@link #write} deletes it in a {@code finally}, before the result that led
     * to this refusal was ever reported.
     */
    public static void delete(Path out) throws IOException {
        Files.deleteIfExists(out);
        Path parent = out.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) return;
        reapCorpses(parent);
    }

    /** Create the owner marker, name this host in it, and lock it for as long as the returned channel is open. */
    static java.nio.channels.FileChannel claim(Path folder) throws IOException {
        var ch = java.nio.channels.FileChannel.open(folder.resolve(OWNER),
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        try {
            ch.lock();
            ch.write(java.nio.ByteBuffer.wrap(HOST.getBytes(StandardCharsets.UTF_8)));
            ch.force(true);
            return ch;
        } catch (IOException | RuntimeException e) {
            ch.close();
            throw e;
        }
    }

    /**
     * Reap the working folders of captures that are provably dead: a {@code .capture-*} folder whose marker names this
     * host and whose lock can be taken. Anything else is left: never delete on doubt.
     */
    static void reapCorpses(Path parent) throws IOException {
        if (HOST.isEmpty()) return;                                    // this host cannot be named: nothing is provable
        List<Path> candidates;
        try (var list = Files.list(parent)) {
            candidates = list.filter(p -> p.getFileName().toString().startsWith(".capture-") && Files.isDirectory(p)).toList();
        }
        for (Path p : candidates) {
            Path marker = p.resolve(OWNER);
            String host;
            try {
                host = Files.readString(marker, StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                continue;                                              // unmarked or unreadable: not provably a capture's
            }
            if (!HOST.equals(host)) continue;                          // another machine's: its lock means nothing here
            boolean ownerDead;
            try (var ch = java.nio.channels.FileChannel.open(marker, java.nio.file.StandardOpenOption.WRITE)) {
                var lock = ch.tryLock();
                ownerDead = lock != null;                              // null: held by another live process
                if (lock != null) lock.release();
            } catch (java.nio.channels.OverlappingFileLockException live) {
                ownerDead = false;                                     // held by a live capture in this JVM
            } catch (IOException cannotTell) {
                ownerDead = false;                                     // locking unsupported here: cannot tell
            }
            if (!ownerDead) continue;
            deleteTree(p);
        }
    }

    /** This host's name as the marker records it, or "" when it cannot be established (then nothing is reaped). */
    static final String HOST = hostName();

    private static String hostName() {
        try {
            String h = java.net.InetAddress.getLocalHost().getHostName();
            return h == null ? "" : h.trim();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
