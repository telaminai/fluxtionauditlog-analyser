package telamin.fluxtion.audit.analyser.analyser.account;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A loopback stand-in for the compiler service's endpoints the analyser calls (CA-1 tests): device authorize and token,
 * {@code /v1/usage}, {@code /v1/me}, the credential list and credential revoke. Token answers are scripted as a queue
 * of {@code status|body} pairs; the last one repeats. Every request is recorded with its method, path, Authorization
 * header and body, so a test can assert what was — and was not — sent; the last {@code device_name} a token poll sent is
 * kept in {@link #deviceName}. A revoke the stand-in answers 200/204 marks that credential revoked in the list. The
 * values are placeholders, not real codes.
 */
public final class StandInCompilerService implements AutoCloseable {

    public static final String DEVICE_CODE = "devcode-PLACEHOLDER-must-never-be-shown";
    public static final String USER_CODE = "WDJB-MJHT";
    public static final String BEARER = "fxk_cred01.secret-PLACEHOLDER-must-never-be-shown";

    public record Request(String method, String path, String authorization, String body) { }

    private final HttpServer server;
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private final Deque<String[]> tokenAnswers = new ArrayDeque<>();
    public volatile int expiresIn = 600;
    public volatile int interval = 2;
    public volatile int usageStatus = 200;
    public volatile String usageBody = "{\"accountId\":\"acct-demo\",\"plan\":\"free\",\"active\":true,\"available\":120,"
            + "\"reserved\":0,\"consumed\":30,\"released\":0,\"generations\":3,\"failures\":0,\"currentPeriod\":\"2026-10\","
            + "\"nextReset\":null,\"buckets\":[],\"policyVersion\":\"p1\",\"as_of_sequence\":42}";
    public volatile int revokeStatus = 204;
    public volatile String meBody = "{\"userId\":\"user-demo\",\"selectedAccount\":\"acct-demo\",\"accounts\":["
            + "{\"accountId\":\"acct-other\",\"personal\":false,\"role\":\"member\"},"
            + "{\"accountId\":\"acct-demo\",\"personal\":true,\"role\":\"administrator\"}]}";
    /** The device_name the last token poll carried; null when none did. */
    public volatile String deviceName;
    /** The account's credentials by id: {kind, name, createdAt, revokedAt}; cred01 is the one sign-in issues. */
    public final Map<String, Object[]> credentials = Collections.synchronizedMap(new LinkedHashMap<>());

    {
        credentials.put("cred01", new Object[]{"DEVICE", "Fluxtion analyser on demo-host", 1_790_000_000_000L, null});
        credentials.put("cred02", new Object[]{"DEVICE", "Fluxtion analyser on other-host", 1_780_000_000_000L, null});
        credentials.put("cred03", new Object[]{"DEVICE", "device", 1_770_000_000_000L, 1_775_000_000_000L});
    }
    public volatile String verificationUri = "https://compiler.example.com/device";
    /** When set, the usage read answers 302 to this path on the same stand-in, which records whether it was followed. */
    public volatile String usageRedirect;

    public StandInCompilerService() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Queue token-endpoint answers: "pending", "slow_down", "access_denied", "expired_token", "approved", "rate_limited" (429) or "unavailable" (503). */
    public synchronized StandInCompilerService token(String... answers) {
        for (String a : answers) {
            if (a.equals("rate_limited") || a.equals("unavailable")) {
                tokenAnswers.add(a.equals("rate_limited") ? new String[]{"429", "{\"error\":\"RATE_LIMITED\"}"}
                        : new String[]{"503", "{\"error\":\"ENTITLEMENT_UNAVAILABLE\"}"});
                continue;
            }
            tokenAnswers.add(a.equals("approved")
                    ? new String[]{"200", "{\"access_token\":\"" + BEARER + "\",\"token_type\":\"Bearer\","
                            + "\"credential_id\":\"cred01\",\"account_id\":\"acct-demo\"}"}
                    : new String[]{"400", "{\"error\":\"" + (a.equals("pending") ? "authorization_pending" : a) + "\"}"});
        }
        return this;
    }

    public List<Request> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    public long count(String path) {
        return requests().stream().filter(r -> r.path().equals(path)).count();
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = ex.getRequestURI().getPath();
        requests.add(new Request(ex.getRequestMethod(), path, ex.getRequestHeaders().getFirst("Authorization"), body));
        int status;
        String out;
        switch (path) {
            case "/auth/device/authorize" -> {
                status = 200;
                out = "{\"device_code\":\"" + DEVICE_CODE + "\",\"user_code\":\"" + USER_CODE + "\","
                        + "\"verification_uri\":\"" + verificationUri + "\","
                        + "\"verification_uri_complete\":\"https://compiler.example.com/device?user_code=" + USER_CODE + "\","
                        + "\"expires_in\":" + expiresIn + ",\"interval\":" + interval + "}";
            }
            case "/auth/device/token" -> {
                if (Json.parse(body) instanceof Map<?, ?> m && m.get("device_name") instanceof String n) deviceName = n;
                String[] a;
                synchronized (this) {
                    a = tokenAnswers.size() > 1 ? tokenAnswers.poll() : tokenAnswers.peek();
                }
                if (a == null) a = new String[]{"400", "{\"error\":\"authorization_pending\"}"};
                status = Integer.parseInt(a[0]);
                out = a[1];
            }
            case "/v1/usage" -> {
                if (usageRedirect != null) {
                    ex.getResponseHeaders().add("Location", base() + usageRedirect);
                    ex.sendResponseHeaders(302, -1);
                    ex.close();
                    return;
                }
                boolean ok = ("Bearer " + BEARER).equals(ex.getRequestHeaders().getFirst("Authorization"));
                status = ok ? usageStatus : 401;
                out = status == 200 ? usageBody : "{\"error\":\"unauthorized\"}";
            }
            case "/v1/me", "/v1/credentials" -> {
                boolean ok = ("Bearer " + BEARER).equals(ex.getRequestHeaders().getFirst("Authorization"));
                status = ok ? 200 : 401;
                out = !ok ? "{\"error\":\"unauthorized\"}" : path.equals("/v1/me") ? meBody : credentialList();
            }
            default -> {
                if (path.startsWith("/v1/credentials/") && ex.getRequestMethod().equals("DELETE")) {
                    status = revokeStatus;
                    out = "";
                    Object[] c = credentials.get(path.substring("/v1/credentials/".length()));
                    if (c != null && (status == 200 || status == 204)) c[3] = System.currentTimeMillis();
                } else {
                    status = 404;
                    out = "{}";
                }
            }
        }
        byte[] bytes = out.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(bytes);
        }
    }

    private String credentialList() {
        List<Object> list = new ArrayList<>();
        synchronized (credentials) {
            credentials.forEach((id, c) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("credentialId", id);
                m.put("kind", c[0]);
                m.put("name", c[1]);
                m.put("scopes", List.of("compile"));
                m.put("createdAt", c[2]);
                m.put("revokedAt", c[3]);
                list.add(m);
            });
        }
        return Json.write(Map.of("credentials", list));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
