package telamin.fluxtion.audit.analyser.analyser.account;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * CA-1: the analyser's whole conversation with the compiler service — sign in with a device code, read usage, revoke
 * its own device. Nothing else: no grants, no credential creation beyond the sign-in the person approves in their
 * browser, no account changes. The analyser reads the service; it never operates it.
 *
 * <p>The device code and the bearer never leave this package: {@link DeviceStart} keeps the code package-private,
 * {@link CompilerCredential} the bearer, and no message this class produces quotes either. Nothing here logs.
 */
public final class CompilerAccountClient {

    /** Sleeps between polls; injected so tests run without waiting and can see every interval. */
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    /** A failure the person can act on; its message never contains a code or a credential. */
    public static class AccountException extends Exception {
        public AccountException(String message) {
            super(message);
        }
    }

    /** The service no longer accepts this device's credential (revoked or unknown). */
    public static final class Unauthorized extends AccountException {
        public Unauthorized() {
            super("The service no longer accepts this device's credential (revoked or unknown). Disconnect, then connect again.");
        }
    }

    /** What the person needs to approve the sign-in. The device code itself is package-private and never shown. */
    public static final class DeviceStart {
        private final String service;
        private final String deviceCode;
        private final String userCode;
        private final String verificationUri;
        private final String verificationUriComplete;
        private final long expiresInSeconds;
        private final long intervalSeconds;

        DeviceStart(String service, String deviceCode, String userCode, String verificationUri,
                    String verificationUriComplete, long expiresInSeconds, long intervalSeconds) {
            this.service = service;
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.verificationUriComplete = verificationUriComplete;
            this.expiresInSeconds = expiresInSeconds;
            this.intervalSeconds = intervalSeconds;
        }

        public String service() { return service; }
        String deviceCode() { return deviceCode; }
        public String userCode() { return userCode; }
        public String verificationUri() { return verificationUri; }
        /** The page with the code filled in, when the service offers one; otherwise {@link #verificationUri()}. */
        public String browserUri() { return verificationUriComplete == null ? verificationUri : verificationUriComplete; }
        public long expiresInSeconds() { return expiresInSeconds; }
        public long intervalSeconds() { return intervalSeconds; }

        @Override public String toString() {
            return "DeviceStart[service=" + service + ", userCode=" + userCode + ", deviceCode=<redacted>]";
        }
    }

    /** How a sign-in ended. */
    public sealed interface Outcome permits Approved, Denied, Expired, Cancelled, Failed { }
    public record Approved(CompilerCredential credential) implements Outcome { }
    public record Denied() implements Outcome { }
    public record Expired() implements Outcome { }
    public record Cancelled() implements Outcome { }
    public record Failed(String reason) implements Outcome { }

    /** Read-only usage, as the service reports it. {@code nextReset} is null when the plan has none. */
    public record Usage(String accountId, String plan, boolean active, long available, String nextReset) { }

    /** RFC 8628 §3.5: a slow_down adds at least five seconds to every later interval. */
    static final long SLOW_DOWN_SECONDS = 5;
    /** Rate-limited or unavailable answers tolerated in a row while polling (each lengthens the interval). */
    static final int MAX_TRANSIENT_POLLS = 5;
    static final long DEFAULT_INTERVAL_SECONDS = 5;
    private static final int MAX_BODY = 64 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http;
    private final Sleeper sleeper;
    private final LongSupplier nanoClock;

    public CompilerAccountClient(HttpClient http, Sleeper sleeper, LongSupplier nanoClock) {
        this.http = http;
        this.sleeper = sleeper;
        this.nanoClock = nanoClock;
    }

    /** The real client: redirects are never followed, so a bearer is only ever presented to the issuing service. */
    public static CompilerAccountClient standard() {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(TIMEOUT).build();
        return new CompilerAccountClient(http, d -> Thread.sleep(d.toMillis()), System::nanoTime);
    }

    /** Ask the service for a device code. */
    public DeviceStart start(String serviceAddress) throws AccountException {
        String base = require(serviceAddress);
        Response r = send(HttpRequest.newBuilder(URI.create(base + "/auth/device/authorize"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")));
        if (r.status != 200) throw new AccountException("The service refused to start a sign-in (HTTP " + r.status + ")");
        Map<?, ?> body = object(r.body);
        String deviceCode = string(body, "device_code");
        String userCode = string(body, "user_code");
        String uri = string(body, "verification_uri");
        String complete = body.get("verification_uri_complete") instanceof String s && !s.isBlank() ? s : null;
        if (deviceCode == null || userCode == null || uri == null)
            throw new AccountException("The service's sign-in answer was incomplete");
        if (!ServiceAddress.browsable(uri) || (complete != null && !ServiceAddress.browsable(complete)))
            throw new AccountException("The service gave a sign-in page that is not a web address");
        if (!userCode.matches("[A-Za-z0-9 -]{1,32}")) throw new AccountException("The service gave an unusable user code");
        long expires = number(body, "expires_in", 0);
        if (expires <= 0) throw new AccountException("The service's sign-in answer had no expiry");
        long interval = Math.max(1, number(body, "interval", DEFAULT_INTERVAL_SECONDS));
        return new DeviceStart(base, deviceCode, userCode, uri, complete, expires, interval);
    }

    /**
     * Poll until the person approves or denies, the code expires, or {@code cancelled} answers true. The interval the
     * service asked for is honoured before every poll; a slow_down lengthens it for the rest of the sign-in; no poll is
     * sent once the code has expired or the sign-in was cancelled. An interrupt is a cancellation.
     */
    public Outcome await(DeviceStart start, BooleanSupplier cancelled) {
        long deadline = nanoClock.getAsLong() + Duration.ofSeconds(start.expiresInSeconds).toNanos();
        long interval = start.intervalSeconds;
        String tokenBody = Json.write(Map.of("device_code", start.deviceCode));
        int transientInARow = 0;
        while (true) {
            if (cancelled.getAsBoolean()) return new Cancelled();
            try {
                sleeper.sleep(Duration.ofSeconds(interval));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new Cancelled();
            }
            if (cancelled.getAsBoolean()) return new Cancelled();   // pressed while waiting: no further poll
            if (nanoClock.getAsLong() - deadline >= 0) return new Expired();
            Response r;
            try {
                r = send(HttpRequest.newBuilder(URI.create(start.service + "/auth/device/token"))
                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(tokenBody)));
            } catch (AccountException e) {
                return cancelled.getAsBoolean() ? new Cancelled() : new Failed(e.getMessage());
            }
            if (r.status == 200) {
                Map<?, ?> body;
                try {
                    body = object(r.body);
                    String type = string(body, "token_type");
                    if (type != null && !type.toLowerCase(Locale.ROOT).equals("bearer"))
                        return new Failed("The service issued a credential of an unexpected kind");
                    return new Approved(new CompilerCredential(start.service, string(body, "credential_id"),
                            string(body, "account_id"), string(body, "access_token")));
                } catch (AccountException | IllegalArgumentException e) {
                    return new Failed(e.getMessage());
                }
            }
            // the service limits /auth per client (429) and answers 5xx while its store or provider recovers: wait
            // longer and poll again, a bounded number of times in a row, rather than ending a sign-in the person
            // has already approved
            if (r.status == 429 || r.status == 500 || r.status == 502 || r.status == 503 || r.status == 504) {
                if (++transientInARow > MAX_TRANSIENT_POLLS)
                    return new Failed("The service answered the sign-in poll with HTTP " + r.status + " repeatedly");
                interval += SLOW_DOWN_SECONDS;
                continue;
            }
            transientInARow = 0;
            String error;
            try {
                error = r.status == 400 ? string(object(r.body), "error") : null;
            } catch (AccountException e) {
                error = null;
            }
            if (error == null) return new Failed("The service answered the sign-in poll with HTTP " + r.status);
            switch (error) {
                case "authorization_pending" -> { }
                case "slow_down" -> interval += SLOW_DOWN_SECONDS;
                case "access_denied" -> { return new Denied(); }
                case "expired_token" -> { return new Expired(); }
                // an unknown answer is not quoted: a misbehaving service could echo the device code in it
                default -> { return new Failed("The service ended the sign-in with an answer the analyser does not know"); }
            }
        }
    }

    /** Read the account's plan and credits. Reads only. */
    public Usage usage(CompilerCredential credential) throws AccountException {
        Response r = send(HttpRequest.newBuilder(URI.create(credential.service() + "/v1/usage"))
                .header("Authorization", "Bearer " + credential.bearer()).GET());
        if (r.status == 401) throw new Unauthorized();
        if (r.status != 200) throw new AccountException("The service could not report usage (HTTP " + r.status + ")");
        Map<?, ?> body = object(r.body);
        String plan = string(body, "plan");
        Object available = body.get("available");
        if (plan == null || !(available instanceof Number n)) throw new AccountException("The service's usage answer was incomplete");
        String account = string(body, "accountId");
        String reset = body.get("nextReset") instanceof String s && !s.isBlank() ? s : null;
        return new Usage(account == null ? credential.accountId() : account, plan,
                !(body.get("active") instanceof Boolean b) || b, n.longValue(), reset);
    }

    /**
     * Revoke this device's credential at the service. True when the service revoked it now; false when the service no
     * longer has it (404) — either way the caller then disconnects. Any other answer leaves it connected, 401 included:
     * a 401 cannot tell a credential already revoked from one the request never delivered (an address in front of the
     * service that drops the Authorization header), and deleting a live credential here would leave it working there.
     */
    public boolean revoke(CompilerCredential credential) throws AccountException {
        Response r = send(HttpRequest.newBuilder(URI.create(credential.service() + "/v1/credentials/" + credential.credentialId()))
                .header("Authorization", "Bearer " + credential.bearer()).DELETE());
        if (r.status == 204 || r.status == 200) return true;
        if (r.status == 404) return false;
        if (r.status == 401)
            throw new AccountException("The service did not accept this device's credential (HTTP 401). If it was"
                    + " already revoked, use Disconnect; otherwise check that the address is the compiler service itself.");
        throw new AccountException("The service did not revoke this device (HTTP " + r.status + ")");
    }

    // ---- plumbing -----------------------------------------------------------------------------------------------

    private record Response(int status, String body) { }

    private static String require(String address) throws AccountException {
        String refusal = ServiceAddress.refusal(address);
        if (refusal != null) throw new AccountException(refusal);
        return ServiceAddress.require(address);
    }

    private Response send(HttpRequest.Builder request) throws AccountException {
        try {
            HttpResponse<InputStream> r = http.send(request.timeout(TIMEOUT).header("Accept", "application/json").build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = r.body()) {
                return new Response(r.statusCode(), new String(in.readNBytes(MAX_BODY), StandardCharsets.UTF_8));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AccountException("Interrupted");
        } catch (IOException e) {
            throw new AccountException("Could not reach the compiler service");
        }
    }

    private static Map<?, ?> object(String body) throws AccountException {
        try {
            if (Json.parse(body) instanceof Map<?, ?> m) return m;
        } catch (RuntimeException ignored) {
            // fall through: the message must not quote a body that may hold a credential
        }
        throw new AccountException("The service's answer was not understood");
    }

    private static String string(Map<?, ?> body, String key) {
        return body.get(key) instanceof String s && !s.isBlank() ? s : null;
    }

    private static long number(Map<?, ?> body, String key, long fallback) {
        return body.get(key) instanceof Number n ? n.longValue() : fallback;
    }
}
