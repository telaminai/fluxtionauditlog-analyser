package telamin.fluxtion.audit.analyser.analyser.account;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * CA-1: the analyser's whole conversation with the compiler service — sign in with a device code, read the account,
 * its usage and its devices, and revoke a device the person picks. Nothing else: no grants, no credential creation
 * beyond the sign-in the person approves in their browser, no account changes. Revoking one of the person's own
 * credentials is the only change it makes, and only when they press for it.
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

    /**
     * Read-only usage, as the service reports it. {@code nextReset} is null when the plan has none (a one-time
     * allowance). The top-level figures are the <b>compile</b> balance (the only kind before the service had two):
     * {@code available} is what remains, {@code consumed} what was used, {@code reserved} what generations still in
     * progress hold. {@code interpret} is the interpret balance, and {@code capabilities} the plan's profile; either is
     * null when the service did not report it (a service older than plans), and nothing is guessed in its place.
     */
    public record Usage(String accountId, String plan, boolean active, long available, long consumed, long reserved,
                        long generations, long failures, String nextReset, Balance interpret, Capabilities capabilities) {

        /** A service that reports compile credit only, and no plan profile. */
        public Usage(String accountId, String plan, boolean active, long available, long consumed, long reserved,
                     long generations, long failures, String nextReset) {
            this(accountId, plan, active, available, consumed, reserved, generations, failures, nextReset, null, null);
        }

        /** The compile balance: the top-level figures, with {@code generations} as its completed requests. */
        public Balance compile() {
            return new Balance(available, consumed, reserved, generations, failures);
        }

        /** The plan's name: the profile's own when the service reported one, else the account's plan field. */
        public String planName() {
            return capabilities != null && capabilities.plan() != null ? capabilities.plan() : plan;
        }
    }

    /**
     * One kind of credit: what remains, what was used, what requests in progress hold, how many requests completed
     * (settled) and how many failed. Compile and interpret credit are separate; one never spends the other.
     */
    public record Balance(long available, long consumed, long reserved, long completed, long failures) { }

    /**
     * What the plan allows, as the service's {@code capabilities} object states it. A field the service did not state
     * is null (never a default the analyser made up). {@code overrides} are an operator's per-account values, by
     * capability name; empty when none.
     */
    public record Capabilities(String plan, Long maxNodes, Long maxEventTypes, List<String> targets, Boolean wasm,
                               String assurance, Long compilePerMinute, Long interpretPerMinute,
                               Map<String, String> overrides) { }

    /**
     * Who the credential belongs to: the selected account, whether it is a personal one, and the person's role in it.
     * {@code personal} and {@code role} are null when the service's answer did not list the selected account.
     */
    public record Me(String selectedAccount, Boolean personal, String role) {
        /** "Personal" or "Organisation", from the service's {@code personal} flag; "Unknown" when it was not given. */
        public String type() {
            return personal == null ? "Unknown" : personal ? "Personal" : "Organisation";
        }
    }

    /** One of the account's credentials that the service still accepts. {@code createdAt} is epoch milliseconds. */
    public record Device(String credentialId, String kind, String name, long createdAt) { }

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
    private final Supplier<String> hostName;

    public CompilerAccountClient(HttpClient http, Sleeper sleeper, LongSupplier nanoClock) {
        this(http, sleeper, nanoClock, CompilerAccountClient::localHostName);
    }

    CompilerAccountClient(HttpClient http, Sleeper sleeper, LongSupplier nanoClock, Supplier<String> hostName) {
        this.http = http;
        this.sleeper = sleeper;
        this.nanoClock = nanoClock;
        this.hostName = hostName;
    }

    /** Characters the service keeps in a device name ({@code [A-Za-z0-9 ._:-]{1,64}} after trimming). */
    static final int DEVICE_NAME_MAX = 64;

    /**
     * The name this device asks to be listed under: "Fluxtion analyser on &lt;host&gt;", the host reduced to the
     * characters the service keeps and the whole cut to 64, so a person with several machines can tell which to revoke.
     */
    static String deviceName(String host) {
        String h = host == null ? "" : host.replaceAll("[^A-Za-z0-9 ._:-]", "").strip();
        String name = h.isEmpty() ? "Fluxtion analyser" : "Fluxtion analyser on " + h;
        return name.length() > DEVICE_NAME_MAX ? name.substring(0, DEVICE_NAME_MAX).strip() : name;
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
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
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("device_code", start.deviceCode);
        token.put("device_name", deviceName(hostName.get()));
        String tokenBody = Json.write(token);
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
                !(body.get("active") instanceof Boolean b) || b, n.longValue(), number(body, "consumed", 0),
                number(body, "reserved", 0), number(body, "generations", 0), number(body, "failures", 0), reset,
                interpret(body), capabilities(body));
    }

    /** {@code kinds.interpret}, when the service reports it with at least what remains; otherwise null. */
    static Balance interpret(Map<?, ?> body) {
        if (!(body.get("kinds") instanceof Map<?, ?> kinds) || !(kinds.get("interpret") instanceof Map<?, ?> k)
                || !(k.get("available") instanceof Number available)) return null;
        return new Balance(available.longValue(), number(k, "consumed", 0), number(k, "reserved", 0),
                number(k, "settled", 0), number(k, "failures", 0));
    }

    /** The {@code capabilities} object, field by field; null when the service sent none. */
    static Capabilities capabilities(Map<?, ?> body) {
        if (!(body.get("capabilities") instanceof Map<?, ?> caps)) return null;
        List<String> targets = null;
        if (caps.get("targets") instanceof List<?> list) {
            targets = new ArrayList<>();
            for (Object t : list) if (t instanceof String s && !s.isBlank()) targets.add(s);
            targets = List.copyOf(targets);
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        if (caps.get("overrides") instanceof Map<?, ?> o)
            o.forEach((k, v) -> { if (k instanceof String key && v != null) overrides.put(key, String.valueOf(v)); });
        return new Capabilities(string(caps, "plan"), optional(caps, "maxNodes"), optional(caps, "maxEventTypes"),
                targets, caps.get("wasm") instanceof Boolean w ? w : null, string(caps, "assurance"),
                optional(caps, "compilePerMinute"), optional(caps, "interpretPerMinute"),
                java.util.Collections.unmodifiableMap(overrides));
    }

    /** Read who the credential belongs to: the selected account, its type and the person's role. Reads only. */
    public Me me(CompilerCredential credential) throws AccountException {
        Map<?, ?> body = object(get(credential, "/v1/me", "account"));
        String selected = string(body, "selectedAccount");
        if (selected == null) selected = credential.accountId();
        if (body.get("accounts") instanceof List<?> accounts) {
            for (Object a : accounts) {
                if (a instanceof Map<?, ?> m && selected.equals(m.get("accountId")))
                    return new Me(selected, m.get("personal") instanceof Boolean p ? p : null, string(m, "role"));
            }
        }
        return new Me(selected, null, null);
    }

    /** The account's credentials the service still accepts (a revoked one is left out), in the service's order. */
    public List<Device> devices(CompilerCredential credential) throws AccountException {
        Map<?, ?> body = object(get(credential, "/v1/credentials", "the device list"));
        if (!(body.get("credentials") instanceof List<?> all)) throw new AccountException("The service's device list was not understood");
        List<Device> out = new ArrayList<>();
        for (Object o : all) {
            if (!(o instanceof Map<?, ?> m) || m.get("revokedAt") != null) continue;
            String id = string(m, "credentialId");
            if (id == null || !CompilerCredential.IDENTIFIER.matcher(id).matches()) continue;   // never a path we would build
            String kind = string(m, "kind");
            String name = string(m, "name");
            out.add(new Device(id, kind == null ? "" : kind, name == null ? "" : name, number(m, "createdAt", 0)));
        }
        return out;
    }

    private String get(CompilerCredential credential, String path, String what) throws AccountException {
        Response r = send(HttpRequest.newBuilder(URI.create(credential.service() + path))
                .header("Authorization", "Bearer " + credential.bearer()).GET());
        if (r.status == 401) throw new Unauthorized();
        if (r.status != 200) throw new AccountException("The service could not report " + what + " (HTTP " + r.status + ")");
        return r.body;
    }

    /**
     * Revoke this device's credential at the service. True when the service revoked it now; false when the service no
     * longer has it (404) — either way the caller then disconnects. Any other answer leaves it connected, 401 included:
     * a 401 cannot tell a credential already revoked from one the request never delivered (an address in front of the
     * service that drops the Authorization header), and deleting a live credential here would leave it working there.
     */
    public boolean revoke(CompilerCredential credential) throws AccountException {
        return revoke(credential, credential.credentialId());
    }

    /**
     * Revoke another of the account's devices, presenting this device's credential; the same answers as
     * {@link #revoke(CompilerCredential)}. The id must have the identifier shape, so it can only ever name one credential.
     */
    public boolean revoke(CompilerCredential credential, String credentialId) throws AccountException {
        if (credentialId == null || !CompilerCredential.IDENTIFIER.matcher(credentialId).matches())
            throw new AccountException("That device's id cannot be revoked from here");
        Response r = send(HttpRequest.newBuilder(URI.create(credential.service() + "/v1/credentials/" + credentialId))
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

    private static Long optional(Map<?, ?> body, String key) {
        return body.get(key) instanceof Number n ? n.longValue() : null;
    }
}
