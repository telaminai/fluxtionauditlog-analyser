package telamin.fluxtion.audit.analyser.analyser.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a log from S3 by streaming it through the local {@code aws} CLI
 * ({@code aws s3 cp s3://bucket/key - --quiet}). This reuses the user's existing AWS auth (profiles /
 * SSO / MFA) and adds no runtime dependency; it just requires the AWS CLI on the PATH.
 */
public final class S3Source {

    private S3Source() {
    }

    public static boolean isS3(String location) {
        return location != null && location.startsWith("s3://");
    }

    /** The CLI command to copy the object to {@code dest} (package-visible for testing). */
    static List<String> buildCommand(String uri, Path dest, String profile, String region) {
        List<String> cmd = new ArrayList<>(List.of("aws", "s3", "cp", uri, dest.toString(), "--quiet"));
        if (profile != null && !profile.isBlank()) {
            cmd.add("--profile");
            cmd.add(profile);
        }
        if (region != null && !region.isBlank()) {
            cmd.add("--region");
            cmd.add(region);
        }
        return cmd;
    }

    /**
     * Downloads the object to a local temp file (streamed to disk, never fully into heap — so large
     * objects don't OOM), and returns the path. Caller opens it via {@code LogStores.open} so the
     * heap-vs-mmap choice applies to S3 too. The temp file is deleted on exit.
     */
    public static Path fetchToFile(String uri, String profile, String region) throws IOException, InterruptedException {
        Path dest = Files.createTempFile("fluxtion-s3-", ".log");
        dest.toFile().deleteOnExit();
        ProcessBuilder pb = new ProcessBuilder(buildCommand(uri, dest, profile, region));
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new IOException("Could not run the 'aws' CLI (is it installed and on your PATH?): " + e.getMessage(), e);
        }
        StringBuilder err = new StringBuilder();
        Thread errDrain = new Thread(() -> {
            try {
                err.append(new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignore) {
                // process ended
            }
        }, "s3-stderr");
        errDrain.setDaemon(true);
        errDrain.start();
        p.getInputStream().readAllBytes();   // drain stdout (empty for file copy)
        int code = p.waitFor();
        errDrain.join(2000);
        if (code != 0) {
            Files.deleteIfExists(dest);
            throw new IOException("aws s3 cp failed (exit " + code + "): " + err.toString().trim());
        }
        return dest;
    }
}
