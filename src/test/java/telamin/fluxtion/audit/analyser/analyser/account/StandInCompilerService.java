package telamin.fluxtion.audit.analyser.analyser.account;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A loopback stand-in for the compiler service's four endpoints (CA-1 tests). Token answers are scripted as a queue
 * of {@code status|body} pairs; the last one repeats. Every request is recorded with its method, path, Authorization
 * header and body, so a test can assert what was — and was not — sent. The values are placeholders, not real codes.
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

    public StandInCompilerService() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Queue token-endpoint answers: "pending", "slow_down", "access_denied", "expired_token" or "approved". */
    public synchronized StandInCompilerService token(String... answers) {
        for (String a : answers) {
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
                        + "\"verification_uri\":\"https://compiler.example.com/device\","
                        + "\"verification_uri_complete\":\"https://compiler.example.com/device?user_code=" + USER_CODE + "\","
                        + "\"expires_in\":" + expiresIn + ",\"interval\":" + interval + "}";
            }
            case "/auth/device/token" -> {
                String[] a;
                synchronized (this) {
                    a = tokenAnswers.size() > 1 ? tokenAnswers.poll() : tokenAnswers.peek();
                }
                if (a == null) a = new String[]{"400", "{\"error\":\"authorization_pending\"}"};
                status = Integer.parseInt(a[0]);
                out = a[1];
            }
            case "/v1/usage" -> {
                boolean ok = ("Bearer " + BEARER).equals(ex.getRequestHeaders().getFirst("Authorization"));
                status = ok ? usageStatus : 401;
                out = status == 200 ? usageBody : "{\"error\":\"unauthorized\"}";
            }
            default -> {
                if (path.startsWith("/v1/credentials/") && ex.getRequestMethod().equals("DELETE")) {
                    status = revokeStatus;
                    out = "";
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

    @Override
    public void close() {
        server.stop(0);
    }
}
