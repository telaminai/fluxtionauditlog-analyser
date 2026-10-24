package telamin.fluxtion.audit.analyser.analyser.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.AccountException;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.DeviceStart;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Outcome;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.account.StandInCompilerService.BEARER;
import static telamin.fluxtion.audit.analyser.analyser.account.StandInCompilerService.DEVICE_CODE;

/** CA-1: the device sign-in, usage read and revoke against a loopback stand-in, on a fake clock. */
class CompilerAccountClientTest {

    private StandInCompilerService service;
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final List<Long> sleeps = new ArrayList<>();
    private CompilerAccountClient client;

    @BeforeEach
    void setUp() throws Exception {
        service = new StandInCompilerService();
        client = new CompilerAccountClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                d -> {
                    sleeps.add(d.toSeconds());
                    clock.addAndGet(d.toNanos());
                }, clock::get);
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    private DeviceStart start() throws AccountException {
        return client.start(service.base());
    }

    @Test
    void pendingThenSlowDownThenApproved_honoursTheIntervalAndLengthensItOnSlowDown() throws Exception {
        service.token("pending", "slow_down", "approved");
        DeviceStart s = start();
        assertEquals(StandInCompilerService.USER_CODE, s.userCode());
        assertTrue(s.browserUri().endsWith("user_code=" + StandInCompilerService.USER_CODE));

        Outcome o = client.await(s, () -> false);

        CompilerCredential c = assertInstanceOf(CompilerAccountClient.Approved.class, o).credential();
        assertEquals("cred01", c.credentialId());
        assertEquals("acct-demo", c.accountId());
        assertEquals(service.base(), c.service());
        assertEquals(BEARER, c.bearer());
        assertEquals(List.of(2L, 2L, 7L), sleeps, "the interval before every poll; slow_down adds five seconds");
        assertEquals(3, service.count("/auth/device/token"));
        assertTrue(service.requests().stream().filter(r -> r.path().equals("/auth/device/token"))
                .allMatch(r -> r.body().contains(DEVICE_CODE)), "each poll presents the device code");
        assertFalse(o.toString().contains(BEARER), "an outcome's text never carries the credential: " + o);
        assertFalse(s.toString().contains(DEVICE_CODE), "a start's text never carries the device code: " + s);
    }

    @Test
    void deniedInTheBrowser_isDenied() throws Exception {
        service.token("pending", "access_denied");
        assertInstanceOf(CompilerAccountClient.Denied.class, client.await(start(), () -> false));
    }

    @Test
    void theServiceSayingExpired_isExpired() throws Exception {
        service.token("expired_token");
        assertInstanceOf(CompilerAccountClient.Expired.class, client.await(start(), () -> false));
    }

    @Test
    void noPollIsSentOnceTheCodeHasExpired() throws Exception {
        service.expiresIn = 5;
        service.interval = 2;
        service.token("pending");
        // bounded: a client that ignored expiry would poll a pending service for ever
        CompilerAccountClient bounded = new CompilerAccountClient(HttpClient.newHttpClient(), d -> {
            clock.addAndGet(d.toNanos());
            if (service.count("/auth/device/token") > 10) throw new AssertionError("still polling long after the code expired");
        }, clock::get);
        Outcome o = bounded.await(bounded.start(service.base()), () -> false);
        assertInstanceOf(CompilerAccountClient.Expired.class, o);
        // polls at t=2 and t=4; the third sleep reaches t=6, past the five-second expiry, so it is not sent
        assertEquals(2, service.count("/auth/device/token"), "no poll after expiry");
    }

    @Test
    void aRateLimitedOrUnavailablePollWaitsLongerAndPollsAgain_ratherThanEndingTheSignIn() throws Exception {
        service.token("pending", "rate_limited", "unavailable", "approved");
        Outcome o = client.await(start(), () -> false);
        assertInstanceOf(CompilerAccountClient.Approved.class, o);
        assertEquals(4, service.count("/auth/device/token"));
        assertTrue(sleeps.get(2) > sleeps.get(1) && sleeps.get(3) > sleeps.get(2), "each transient answer lengthens the wait: " + sleeps);
    }

    @Test
    void transientAnswersAreBounded() throws Exception {
        service.expiresIn = 100_000;
        service.token("rate_limited");
        Outcome o = client.await(start(), () -> false);
        assertInstanceOf(CompilerAccountClient.Failed.class, o);
        assertEquals(CompilerAccountClient.MAX_TRANSIENT_POLLS + 1, service.count("/auth/device/token"));
    }

    @Test
    void anyLoopbackAddressMayBePlainHttp_andNoOtherHost() {
        assertNull(ServiceAddress.refusal("http://127.0.0.2:8080"));
        assertNull(ServiceAddress.refusal("http://localhost:8080"));
        assertNull(ServiceAddress.refusal("http://[::1]:8080"));
        assertNotNull(ServiceAddress.refusal("http://127.0.0.1.example.com"));
        assertNotNull(ServiceAddress.refusal("http://10.0.0.5:8080"));
    }

    @Test
    void cancellingStopsPolling() throws Exception {
        service.token("pending");
        AtomicBoolean cancelled = new AtomicBoolean();
        CompilerAccountClient counting = new CompilerAccountClient(HttpClient.newHttpClient(), d -> {
            clock.addAndGet(d.toNanos());
            if (service.count("/auth/device/token") == 1) cancelled.set(true);   // the person presses Cancel
            if (service.count("/auth/device/token") > 10) throw new AssertionError("still polling after cancel");
        }, clock::get);
        Outcome o = counting.await(counting.start(service.base()), cancelled::get);
        assertInstanceOf(CompilerAccountClient.Cancelled.class, o);
        assertEquals(1, service.count("/auth/device/token"), "nothing is polled after a cancel");
    }

    @Test
    void anInterruptIsACancellation() throws Exception {
        service.token("pending");
        CompilerAccountClient interrupted = new CompilerAccountClient(HttpClient.newHttpClient(), d -> {
            throw new InterruptedException();
        }, clock::get);
        assertInstanceOf(CompilerAccountClient.Cancelled.class, interrupted.await(interrupted.start(service.base()), () -> false));
        assertTrue(Thread.interrupted(), "the interrupt is preserved for the caller");
        assertEquals(0, service.count("/auth/device/token"));
    }

    @Test
    void usageIsRead_andANullNextResetStaysNull() throws Exception {
        service.token("approved");
        CompilerCredential c = ((CompilerAccountClient.Approved) client.await(start(), () -> false)).credential();
        CompilerAccountClient.Usage u = client.usage(c);
        assertEquals("free", u.plan());
        assertEquals(120, u.available());
        assertNull(u.nextReset());
        assertTrue(u.active());

        service.usageBody = service.usageBody.replace("\"nextReset\":null", "\"nextReset\":\"2026-11-01T00:00:00Z\"");
        assertEquals("2026-11-01T00:00:00Z", client.usage(c).nextReset());
        StandInCompilerService.Request read = service.requests().get(service.requests().size() - 1);
        assertEquals("GET", read.method());
        assertEquals("Bearer " + BEARER, read.authorization());
    }

    @Test
    void aRevokedCredentialReadsAsUnauthorized() throws Exception {
        service.token("approved");
        CompilerCredential c = ((CompilerAccountClient.Approved) client.await(start(), () -> false)).credential();
        service.usageStatus = 401;
        AccountException e = assertThrows(CompilerAccountClient.Unauthorized.class, () -> client.usage(c));
        assertFalse(e.getMessage().contains(BEARER));
    }

    @Test
    void revokeDeletesThisDevicesCredential_andOnlyA404CountsAsAlreadyDone() throws Exception {
        service.token("approved");
        CompilerCredential c = ((CompilerAccountClient.Approved) client.await(start(), () -> false)).credential();
        assertTrue(client.revoke(c));
        StandInCompilerService.Request r = service.requests().get(service.requests().size() - 1);
        assertEquals("DELETE", r.method());
        assertEquals("/v1/credentials/cred01", r.path());
        assertEquals("Bearer " + BEARER, r.authorization());

        service.revokeStatus = 404;
        assertFalse(client.revoke(c), "the service no longer has it: the caller still disconnects");
        service.revokeStatus = 401;
        AccountException e = assertThrows(AccountException.class, () -> client.revoke(c),
                "a 401 may be a credential the request never delivered: it stays connected");
        assertTrue(e.getMessage().contains("Disconnect"), e.getMessage());
        service.revokeStatus = 500;
        assertThrows(AccountException.class, () -> client.revoke(c), "any other answer leaves it connected");
    }

    @Test
    void theAnalyserOnlyEverCallsTheFourDocumentedEndpoints() throws Exception {
        service.token("pending", "approved");
        CompilerCredential c = ((CompilerAccountClient.Approved) client.await(start(), () -> false)).credential();
        client.usage(c);
        client.revoke(c);
        for (StandInCompilerService.Request r : service.requests()) {
            assertTrue(List.of("POST /auth/device/authorize", "POST /auth/device/token", "GET /v1/usage",
                    "DELETE /v1/credentials/cred01").contains(r.method() + " " + r.path()), "reads, never operates: " + r);
        }
    }

    @Test
    void aBearerIsNeverSentInClearToAnotherHost_andNothingIsSent() {
        AccountException e = assertThrows(AccountException.class, () -> client.start("http://compiler.example.com"));
        assertTrue(e.getMessage().contains("https://"));
        assertNotNull(ServiceAddress.refusal("https://user@compiler.example.com"));
        assertNull(ServiceAddress.refusal("https://compiler.example.com/"));
        assertEquals("https://compiler.example.com", ServiceAddress.require("https://compiler.example.com/"));
        assertEquals(0, service.requests().size());
    }

    @Test
    void theRealClientNeverFollowsARedirect_soTheBearerOnlyReachesTheIssuingService() throws Exception {
        service.token("approved");
        CompilerCredential c = ((CompilerAccountClient.Approved) client.await(start(), () -> false)).credential();
        service.usageRedirect = "/elsewhere";
        AccountException e = assertThrows(AccountException.class, () -> CompilerAccountClient.standard().usage(c));
        assertTrue(e.getMessage().contains("HTTP 302"), e.getMessage());
        assertEquals(0, service.count("/elsewhere"), "the redirect was followed, carrying the bearer");
    }

    @Test
    void aSignInPageServedInClearFromAnotherHostIsRefused_andNothingIsOpened() {
        assertTrue(ServiceAddress.browsable("https://compiler.example.com/device?user_code=WDJB-MJHT"));
        assertTrue(ServiceAddress.browsable("http://127.0.0.1:8080/device"));
        assertFalse(ServiceAddress.browsable("http://compiler.example.com/device"), "a sign-in page in clear");
        assertFalse(ServiceAddress.browsable("https://user@compiler.example.com/device"));
        assertFalse(ServiceAddress.browsable("file:///etc/passwd"));
        service.verificationUri = "http://compiler.example.com/device";
        AccountException e = assertThrows(AccountException.class, this::start);
        assertTrue(e.getMessage().contains("not a web address") || e.getMessage().contains("sign-in page"), e.getMessage());
    }

    @Test
    void aCredentialIdThatIsADotSegmentIsRefused_soRevokeCannotNameAnotherPath() {
        for (String id : List.of(".", "..", "-x", "a/b"))
            assertThrows(IllegalArgumentException.class, () -> new CompilerCredential(service.base(), id, "acct-demo", BEARER), id);
        assertEquals("cred.01", new CompilerCredential(service.base(), "cred.01", "acct-demo", BEARER).credentialId());
    }

    @Test
    void aFailureMessageNeverQuotesWhatTheServiceSent() throws Exception {
        DeviceStart s = start();
        // a misbehaving service echoing the device code and a credential in an unexpected error answer
        service.token("weird_" + DEVICE_CODE.replaceAll("[^A-Za-z0-9_]", "") + "_" + "x".repeat(80));
        Outcome o = client.await(s, () -> false);
        String reason = assertInstanceOf(CompilerAccountClient.Failed.class, o).reason();
        assertFalse(reason.contains(DEVICE_CODE.replaceAll("[^A-Za-z0-9_]", "")), reason);
        assertTrue(reason.length() < 120, reason);
    }

    @Test
    void sleepsAreWholeIntervals_neverBusyPolling() throws Exception {
        service.interval = 0;   // a service asking for no interval still gets at least one second
        service.token("pending", "approved");
        client.await(start(), () -> false);
        assertTrue(sleeps.stream().allMatch(s -> s >= 1), "" + sleeps);
        assertEquals(Duration.ofSeconds(5).toSeconds(), CompilerAccountClient.SLOW_DOWN_SECONDS);
    }
}
