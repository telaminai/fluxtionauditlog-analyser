package telamin.fluxtion.audit.analyser.analyser.assistant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * M74 P2b: the ONE place an example archive is fetched — an effect's performer, never a decision. HTTPS only, an
 * allowed host only, no redirect followed unless it stays on an allowed host and names the same pinned commit
 * ({@link ExampleSource#mayRedirectTo}), at most {@value #MAX_REDIRECTS} of them, and at most the cap in bytes. It
 * computes the archive's SHA-256 so the session can verify it before anything is unpacked. No git, no clone.
 */
public final class ExampleArchiveClient {

    public static final int MAX_REDIRECTS = 3;

    /** One HTTP GET with no redirect followed: the status, a Location header, and the body up to the cap. */
    @FunctionalInterface
    public interface Transport {
        Reply get(URI uri, long maxBytes) throws IOException, InterruptedException;
    }

    public record Reply(int status, String location, byte[] body) {
    }

    /** The archive and its digest, or why not. */
    public record Fetched(byte[] bytes, String sha256, String failure) {
        public boolean ok() {
            return failure == null;
        }
    }

    private final Transport transport;

    public ExampleArchiveClient(Transport transport) {
        this.transport = transport;
    }

    /** The real transport: java.net.http, redirects NEVER followed by the client itself. */
    public static ExampleArchiveClient https() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new ExampleArchiveClient((uri, max) -> {
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET()
                    .header("User-Agent", "fluxtion-auditlog-analyser").build();
            HttpResponse<InputStream> r = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            String location = r.headers().firstValue("Location").orElse(null);
            try (InputStream in = r.body()) {
                if (r.statusCode() / 100 != 2) return new Reply(r.statusCode(), location, new byte[0]);
                return new Reply(r.statusCode(), location, readCapped(in, max));
            }
        });
    }

    public Fetched fetch(ExampleSource source, long maxBytes) {
        if (source == null) return new Fetched(null, null, "no archive is named");
        URI at = URI.create(source.url());
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (!"https".equalsIgnoreCase(at.getScheme())
                        || !ExampleSource.ALLOWED_HOSTS.contains(String.valueOf(at.getHost()).toLowerCase(java.util.Locale.ROOT))) {
                    return new Fetched(null, null, "refused to contact " + at.getScheme() + "://" + at.getHost());
                }
                Reply r = transport.get(at, maxBytes);
                if (r.status() / 100 == 3) {
                    if (r.location() == null) return new Fetched(null, null, "a redirect named no address");
                    URI next = at.resolve(r.location());
                    if (!source.mayRedirectTo(next)) {
                        return new Fetched(null, null, "a redirect left the allowed hosts or the pinned commit ("
                                + next.getHost() + ") and was not followed");
                    }
                    at = next;
                    continue;
                }
                if (r.status() / 100 != 2) return new Fetched(null, null, "the server answered " + r.status());
                if (r.body() == null || r.body().length == 0) return new Fetched(null, null, "the archive is empty");
                return new Fetched(r.body(), sha256(r.body()), null);
            }
            return new Fetched(null, null, "more than " + MAX_REDIRECTS + " redirects");
        } catch (TooLarge e) {
            return new Fetched(null, null, "the archive is over " + (maxBytes / (1024 * 1024)) + " MB");
        } catch (IOException e) {
            return new Fetched(null, null, e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Fetched(null, null, "the download was interrupted");
        }
    }

    /** Lower-case hex SHA-256. */
    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Thrown by {@link #readCapped} past the cap. */
    public static final class TooLarge extends IOException {
        public TooLarge() {
            super("over the size cap");
        }
    }

    public static byte[] readCapped(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > max) throw new TooLarge();
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
