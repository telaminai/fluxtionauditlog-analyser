package telamin.fluxtion.audit.analyser.analyser.net;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The well-known file that publishes the app's <b>live</b> REST endpoint, so a statically-configured
 * client can find a per-run port and token (spec-assistant-actions-mcp §3, M13.1).
 *
 * <p>REST binds an ephemeral port and mints a fresh token every launch — neither can be hard-coded in
 * an MCP client config. So while the transport is up the app writes:
 *
 * <pre>{@code
 * ~/.fluxtion-analyser/rest-endpoint          (mode 600)
 * {"url":"http://127.0.0.1:53411","token":"…","pid":12345,"startedAt":"2026-08-15T09:31:04Z"}
 * }</pre>
 *
 * and deletes it on a clean stop/exit. <b>Crash safety:</b> a killed app leaves the file behind, so a
 * reader must call {@link Endpoint#alive()} (a {@link ProcessHandle} liveness check — the portable
 * {@code kill -0}) before trusting it, and report "analyser not running" rather than letting the caller
 * hit connection-refused.
 *
 * <p>The path is <b>explicit</b>, not baked in: {@link #wellKnown()} is what the app uses, while tests
 * (and any future multi-instance selector) bind an instance to their own path. That matters because the
 * REST server is started for real inside unit tests — publishing to the well-known path from there would
 * clobber the endpoint of a developer's running analyser on every {@code mvn test}.
 */
public final class RestEndpointFile {

    /** {@code rw-------} — the file carries the per-run token. */
    private static final String PERMS = "rw-------";

    private final Path path;

    public RestEndpointFile(Path path) {
        this.path = path;
    }

    /** The path the app publishes to: {@code ~/.fluxtion-analyser/rest-endpoint}. */
    public static RestEndpointFile wellKnown() {
        return new RestEndpointFile(Path.of(System.getProperty("user.home"), ".fluxtion-analyser", "rest-endpoint"));
    }

    public Path path() {
        return path;
    }

    /**
     * Publish {@code url} + {@code token} for this process. A private sibling is written completely
     * before it atomically replaces the public path, so readers see either the previous complete
     * endpoint or the new one — never an empty/partial token file.
     */
    public void write(String url, String token) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url);
        m.put("token", token);
        m.put("pid", ProcessHandle.current().pid());
        m.put("startedAt", Instant.now().toString());

        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path pending;
        try {
            pending = Files.createTempFile(parent, path.getFileName() + ".", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(PERMS)));
        } catch (UnsupportedOperationException e) {
            pending = Files.createTempFile(parent, path.getFileName() + ".", ".tmp");
        }
        try {
            Files.writeString(pending, Json.write(m), StandardCharsets.UTF_8);
            try {
                Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }

    /** The published endpoint, or {@code null} if the file is absent or unreadable/malformed. */
    public Endpoint read() {
        try {
            if (!Files.exists(path)) return null;
            if (!(Json.parse(Files.readString(path, StandardCharsets.UTF_8)) instanceof Map<?, ?> m)) return null;
            Object url = m.get("url");
            Object token = m.get("token");
            if (url == null || token == null) return null;
            long pid = m.get("pid") instanceof Number n ? n.longValue() : -1;
            Object startedAt = m.get("startedAt");
            return new Endpoint(url.toString(), token.toString(), pid,
                    startedAt == null ? null : startedAt.toString());
        } catch (IOException | RuntimeException e) {
            return null;   // a half-written or corrupt file reads as "no endpoint", never throws at a caller
        }
    }

    /** Remove the published endpoint. Best-effort: a failure here must never break shutdown. */
    public void delete() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignore) {
            // best-effort; a stale file is handled by the reader's liveness check
        }
    }

    /**
     * Remove the published endpoint <b>only if this process published it</b>. Used on app exit as a
     * backstop to the stop-path delete: it clears a file this process stranded, without stomping the
     * endpoint of a second analyser that published after us (the multi-instance last-writer-wins case
     * left open in spec §12).
     */
    public void deleteIfOwnedByThisProcess() {
        Endpoint e = read();
        if (e != null && e.pid() == ProcessHandle.current().pid()) delete();
    }

    /** One published endpoint. {@link #alive()} guards against a file stranded by a crash. */
    public record Endpoint(String url, String token, long pid, String startedAt) {

        /** True if the publishing process is still running — the portable {@code kill -0}. */
        public boolean alive() {
            return pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }
    }
}
