package telamin.fluxtion.audit.analyser.analyser.template;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTPS-only client for the playground's catalogue, template defaults and existing scaffold endpoint. */
public final class TemplateClient {

    public static final URI PLAYGROUND = URI.create("https://fluxtion-playground.dev");
    static final long MAX_CATALOGUE_BYTES = 2L * 1024 * 1024;
    static final long MAX_TEMPLATE_BYTES = 2L * 1024 * 1024;
    static final long MAX_ZIP_BYTES = 64L * 1024 * 1024;

    @FunctionalInterface
    interface Fetcher {
        Reply get(URI uri, long maxBytes) throws IOException, InterruptedException;
    }

    record Reply(int status, String contentType, byte[] body) {
        Reply {
            contentType = contentType == null ? "" : contentType;
            body = body == null ? new byte[0] : body.clone();
        }
    }

    public record Defaults(String artifact, String group, String basePackage) {
        public Defaults {
            artifact = required(artifact, "artifact");
            group = required(group, "group");
            basePackage = required(basePackage, "base package");
        }
    }

    public record Download(TemplateCatalogue.Entry template, String artifact, String group,
                           String basePackage) {
        public Download {
            if (template == null) throw new IllegalArgumentException("select a template");
            artifact = validateArtifact(artifact);
            group = validatePackage(group, "group");
            basePackage = validatePackage(basePackage, "base package");
        }
    }

    /** One error type for the background UI boundary; its message is safe to show directly. */
    public static final class Failure extends RuntimeException {
        public Failure(String message) { super(message); }
        public Failure(String message, Throwable cause) { super(message, cause); }
    }

    private final URI origin;
    private final Fetcher fetcher;

    /**
     * D-AX10 — an origin override with a <b>loopback-only HTTP exception</b>, for running the analyser
     * against a playground served locally while experimenting on the template and its documentation.
     *
     * <p>The heading previously said "loopback-only origin override", which is backwards: any HTTPS
     * origin is accepted, and it is <em>plain HTTP</em> that is confined to loopback. Recorded twice by
     * review before it was fixed.
     *
     * <p>Deliberately a <b>JVM system property and not a setting</b>. A persisted origin is a
     * supply-chain surface that outlives the experiment that created it: it would sit in a profile,
     * travel with a shared project, and silently redirect where starter code is fetched from. The same
     * reasoning as {@code spec-onboarding-example.md} D-R4 for the skills source. This is unreachable
     * from the Settings UI and is never written to a project profile.
     *
     * <p>Only {@code http} to a loopback host is unlocked. Every other origin keeps the HTTPS rule
     * unchanged, so the hole cannot be widened by a document, a config file or a redirect.
     */
    public static final String ORIGIN_PROPERTY = "fluxtion.analyser.playgroundOrigin";

    public static TemplateClient playground() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new TemplateClient(configuredOrigin(), (uri, maxBytes) -> fetch(http, uri, maxBytes));
    }

    /** @return the origin in force: the pinned playground, or a loopback override when one is set. */
    public static URI configuredOrigin() {
        String raw = System.getProperty(ORIGIN_PROPERTY);
        if (raw == null || raw.isBlank()) return PLAYGROUND;
        try {
            return validateOrigin(URI.create(raw.strip()));
        } catch (IllegalArgumentException e) {
            throw new Failure("-D" + ORIGIN_PROPERTY + "=" + raw.strip() + " is not usable: "
                    + e.getMessage(), e);
        }
    }

    /** True when an override is in force, so a surface can SAY so rather than quietly fetching elsewhere. */
    public static boolean originOverridden() {
        String raw = System.getProperty(ORIGIN_PROPERTY);
        return raw != null && !raw.isBlank();
    }

    /** The origin this client is actually using. */
    public URI origin() {
        return origin;
    }

    TemplateClient(URI origin, Fetcher fetcher) {
        this.origin = validateOrigin(origin);
        this.fetcher = fetcher;
    }

    public TemplateCatalogue.Selection catalogue(String analyserVersion) {
        Reply reply = get(path("/starter-templates/index.json"), MAX_CATALOGUE_BYTES, "template catalogue");
        requireStatus(reply, "template catalogue");
        try {
            return TemplateCatalogue.parse(new String(reply.body(), StandardCharsets.UTF_8), analyserVersion)
                    .onboarding();
        } catch (IllegalArgumentException e) {
            throw new Failure(e.getMessage(), e);
        }
    }

    /** Read identity defaults from the catalogue-owned template file; never duplicate them in Java. */
    @SuppressWarnings("unchecked")
    public Defaults defaults(TemplateCatalogue.Entry entry) {
        if (entry == null) throw new Failure("select a template");
        Reply reply = get(path("/starter-templates/" + entry.file()), MAX_TEMPLATE_BYTES,
                "template " + entry.name());
        requireStatus(reply, "template " + entry.name());
        try {
            Object parsed = Json.parse(new String(reply.body(), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> map)) throw new IllegalArgumentException("template must be an object");
            return new Defaults(string(map, "artifact"), string(map, "group"), string(map, "basePackage"));
        } catch (RuntimeException e) {
            throw new Failure("invalid defaults for " + entry.name() + ": " + e.getMessage(), e);
        }
    }

    public byte[] download(Download request) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("template", request.template().file());
        query.put("artifact", request.artifact());
        query.put("group", request.group());
        query.put("basePackage", request.basePackage());
        StringBuilder encoded = new StringBuilder("/start/scaffold?");
        query.forEach((key, value) -> {
            if (encoded.charAt(encoded.length() - 1) != '?') encoded.append('&');
            encoded.append(url(key)).append('=').append(url(value));
        });
        Reply reply = get(path(encoded.toString()), MAX_ZIP_BYTES, "starter download");
        requireStatus(reply, "starter download");
        byte[] body = reply.body();
        if (body.length < 4 || body[0] != 'P' || body[1] != 'K') {
            throw new Failure("starter download did not return a zip archive");
        }
        return body.clone();
    }

    public URI manualUrl() {
        return path("/start/templates");
    }

    private Reply get(URI uri, long maxBytes, String label) {
        requirePinned(uri);
        try {
            return fetcher.get(uri, maxBytes);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Failure(label + " was interrupted", e);
        } catch (IOException e) {
            throw new Failure(label + " is unreachable. Download manually from " + manualUrl()
                    + " (" + e.getMessage() + ")", e);
        }
    }

    private void requireStatus(Reply reply, String label) {
        if (reply.status() == 200) return;
        String detail = new String(reply.body(), StandardCharsets.UTF_8).strip();
        if (detail.length() > 240) detail = detail.substring(0, 240) + "…";
        throw new Failure(label + " returned HTTP " + reply.status()
                + (detail.isEmpty() ? "" : ": " + detail)
                + ". Manual route: " + manualUrl());
    }

    private URI path(String pathAndQuery) {
        return origin.resolve(pathAndQuery);
    }

    private void requirePinned(URI uri) {
        if (!sameOrigin(origin, uri)) {
            throw new Failure("refusing template URL outside the configured playground origin: " + uri);
        }
    }

    private static URI validateOrigin(URI origin) {
        if (origin == null || origin.getHost() == null || origin.getScheme() == null
                || origin.getRawQuery() != null || origin.getRawFragment() != null
                || origin.getRawUserInfo() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty() && !origin.getPath().equals("/"))) {
            throw new IllegalArgumentException("playground origin must be a bare HTTPS origin");
        }
        boolean https = "https".equalsIgnoreCase(origin.getScheme());
        boolean loopbackHttp = "http".equalsIgnoreCase(origin.getScheme()) && isLoopback(origin.getHost());
        if (!https && !loopbackHttp) {
            throw new IllegalArgumentException("playground origin must be an HTTPS origin — plain http is "
                    + "accepted only for loopback (127.0.0.1, [::1], localhost)");
        }
        String base = origin.toString();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return URI.create(base + "/");
    }

    /**
     * Exactly the three loopback spellings, matched literally. Deliberately not "starts with 127." and
     * not a DNS lookup: a resolvable name is attacker-influenceable, and the point of this predicate is
     * that it cannot be argued into accepting a remote host.
     */
    private static boolean isLoopback(String host) {
        String h = host.toLowerCase(java.util.Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        return h.equals("127.0.0.1") || h.equals("::1") || h.equals("localhost");
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static Reply fetch(HttpClient http, URI uri, long maxBytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/zip")
                .GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        long declared = response.headers().firstValueAsLong("content-length").orElse(-1);
        if (declared > maxBytes) {
            response.body().close();
            throw new IOException("response exceeds " + maxBytes + " bytes");
        }
        byte[] body;
        try (InputStream input = response.body()) {
            body = readBounded(input, maxBytes);
        }
        return new Reply(response.statusCode(),
                response.headers().firstValue("content-type").orElse(""), body);
    }

    static byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > maxBytes) throw new IOException("response exceeds " + maxBytes + " bytes");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private static String validateArtifact(String value) {
        String out = required(value, "artifact");
        if (!out.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("artifact must contain only letters, digits, '.', '_' or '-'");
        }
        return out;
    }

    private static String validatePackage(String value, String field) {
        String out = required(value, field);
        if (!out.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            throw new IllegalArgumentException(field + " must be a Java package name");
        }
        return out;
    }

    private static String required(String value, String field) {
        String out = value == null ? "" : value.strip();
        if (out.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return out;
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
