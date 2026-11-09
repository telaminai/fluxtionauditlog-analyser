package telamin.fluxtion.audit.analyser.analyser.account;

import org.junit.jupiter.api.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import static org.junit.jupiter.api.Assertions.*;

/** Review witnesses: external effects and saved state, not merely helper method return values. */
class SignInReviewRegressionTest {
    String previousOrigin;
    @BeforeEach void trustSyntheticProvider() {
        previousOrigin=System.getProperty("fluxtion.login.verificationOrigin");
        System.setProperty("fluxtion.login.verificationOrigin","https://compiler.example.com");
    }
    @AfterEach void restoreOrigin() {
        if(previousOrigin==null)System.clearProperty("fluxtion.login.verificationOrigin");
        else System.setProperty("fluxtion.login.verificationOrigin",previousOrigin);
    }
    CompilerAccountClient client(HttpClient http) {
        return new CompilerAccountClient(http,d->{},System::nanoTime);
    }
    @Test void bothVerificationAddressesAreRejectedBeforeMcpExposesOrPollsThem() throws Exception {
        for(boolean completeOnly:List.of(false,true)) try(StandInCompilerService server=new StandInCompilerService()) {
            if(completeOnly)server.verificationUriComplete="https://untrusted.example/device?user_code=DEMO";
            else server.verificationUri="https://untrusted.example/device";
            CompilerSignInAttemptsTest.Store store=new CompilerSignInAttemptsTest.Store();
            try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client(HttpClient.newHttpClient()),store,()->false)) {
                Map<String,Object> result=attempts.start(server.base(),false);
                assertEquals("service_error",result.get("event"));
                assertFalse(result.containsKey("verificationUri"));
                assertFalse(result.containsKey("userCode"));
                assertEquals(0,server.count("/auth/device/token"));
                assertNull(store.value);
            }
        }
    }
    @Test void switchWithoutConfirmationNeverContactsNewIssuer() throws Exception {
        try(StandInCompilerService previous=new StandInCompilerService(); StandInCompilerService next=new StandInCompilerService()) {
            CompilerSignInAttemptsTest.Store store=new CompilerSignInAttemptsTest.Store();
            CompilerCredential saved=new CompilerCredential(previous.base(),"cred01","acct-demo",StandInCompilerService.BEARER);
            store.value=saved;
            try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client(HttpClient.newHttpClient()),store,()->false)) {
                assertEquals("usage_error",attempts.start(next.base(),false).get("event"));
                assertTrue(next.requests().isEmpty()); assertTrue(previous.requests().isEmpty());
                assertEquals(saved,store.value);
            }
        }
    }
    @Test void productionHasNoImplicitVerificationOrigin() throws Exception {
        assertNull(VerificationOrigin.trusted("https://compiler.fluxtion-playground.dev",null));
        assertThrows(IOException.class,()->VerificationOrigin.check("https://unconfigured.authkit.app/device",null));
    }
    @Test void equivalentOriginsUseOneIdentityWithoutChangingTransportHost() {
        assertEquals("https://compiler-staging.fluxtion-playground.dev",ServiceAddress.require("HTTPS://COMPILER-STAGING.FLUXTION-PLAYGROUND.DEV:443/"));
        assertEquals("http://localhost:12345",ServiceAddress.require("HTTP://LOCALHOST:12345/"));
    }
    @Test void localhostSpellingDoesNotReplaceExistingMachineSignIn() throws Exception {
        try(StandInCompilerService server=new StandInCompilerService()) {
            CompilerSignInAttemptsTest.Store store=new CompilerSignInAttemptsTest.Store();
            store.value=new CompilerCredential(server.base().replace("127.0.0.1","localhost"),"cred01","acct-demo",StandInCompilerService.BEARER);
            try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client(HttpClient.newHttpClient()),store,()->false)) {
                assertEquals("signed_in",attempts.start(server.base(),false).get("event"));
                assertEquals(0,server.count("/auth/device/authorize"));
                assertEquals(0,server.count("/v1/credentials/cred01"));
                assertTrue(store.value.service().contains("localhost"),"transport host remains the saved issuer's host");
            }
        }
    }
    @Test void storageFailureNamesStorageAndGivesMavenRecoveryWithoutNetwork() throws Exception {
        try(StandInCompilerService server=new StandInCompilerService()) {
            CredentialStore broken=new CredentialStore() {
                public String description(){return "synthetic unavailable store";}
                public Optional<CompilerCredential> load() throws IOException {throw new IOException("synthetic read failure");}
                public void save(CompilerCredential value) {fail("no save");}
                public void delete(){fail("no delete");}
            };
            try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client(HttpClient.newHttpClient()),broken,()->false)) {
                Map<String,Object> result=attempts.start(server.base(),false);
                assertEquals("service_error",result.get("event"));
                assertTrue(result.get("message").toString().contains("credential store"));
                assertEquals("maven_login",result.get("recovery"));
                assertTrue(server.requests().isEmpty());
            }
        }
    }
    @Test void existingSharedSignInDoesNotDependOnOpeningLegacyKeyring(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        try(StandInCompilerService server=new StandInCompilerService()) {
            CredentialStore unavailableLegacy=new CredentialStore() {
                public String description(){return "unavailable legacy fixture";}
                public Optional<CompilerCredential> load() throws IOException {throw new IOException("legacy store unavailable");}
                public void save(CompilerCredential value){fail("legacy remains untouched");}
                public void delete(){fail("legacy remains untouched");}
            };
            SharedCredentialStore store=new SharedCredentialStore(new SharedCredentialFile(temp.resolve("credentials")),unavailableLegacy);
            store.save(new CompilerCredential(server.base(),"cred01","acct-demo",StandInCompilerService.BEARER));
            try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client(HttpClient.newHttpClient()),store,()->false)) {
                assertEquals("signed_in",attempts.start(server.base(),false).get("event"));
                assertEquals(0,server.count("/auth/device/authorize"));
            }
        }
    }
    @Test void legacyOnlyAccountOffersHeadlessMavenRecovery(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        try(StandInCompilerService server=new StandInCompilerService()) {
            CompilerSignInAttemptsTest.Store legacy=new CompilerSignInAttemptsTest.Store();
            legacy.value=new CompilerCredential(server.base(),"cred01","acct-demo",StandInCompilerService.BEARER);
            SharedCredentialStore store=new SharedCredentialStore(new SharedCredentialFile(temp.resolve("credentials")),legacy);
            try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client(HttpClient.newHttpClient()),store,()->false)) {
                Map<String,Object> result=attempts.start(server.base(),false);
                assertEquals("usage_error",result.get("event")); assertEquals("maven_login",result.get("recovery"));
                assertTrue(result.get("message").toString().contains("Maven"));
                assertTrue(store.load().isEmpty()); assertNotNull(legacy.value); assertTrue(server.requests().isEmpty());
            }
        }
    }
    @Test void canonicalIdentityStillDeletesTheExactLegacySnapshot(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        SharedCredentialFile file=new SharedCredentialFile(temp.resolve("credentials"));
        file.write(new SharedCredentialFile.Stored("synthetic","cred01","acct-demo","HTTPS://COMPILER.EXAMPLE.COM:443/"));
        SharedCredentialStore store=new SharedCredentialStore(file,new CompilerSignInAttemptsTest.Store());
        CompilerCredential observed=store.load().orElseThrow();
        assertEquals("https://compiler.example.com",observed.service());
        assertTrue(store.delete(observed),"normalization must preserve compare-before-delete for older saved spelling");
        assertTrue(store.load().isEmpty());
        file.write(new SharedCredentialFile.Stored("synthetic","cred01","acct-demo","HTTPS://COMPILER.EXAMPLE.COM:443/"));
        observed=store.load().orElseThrow();
        CompilerCredential replacement=new CompilerCredential("https://compiler.example.com","cred02","acct-demo","synthetic-next");
        assertTrue(store.replace(observed,replacement));
        assertFalse(store.delete(observed)); assertEquals(replacement,store.load().orElseThrow());
    }
    @Test void cancelledAfterSuccessfulVerificationNeverCommitsEvenWhenTransportIgnoresInterrupt() throws Exception {
        try(StandInCompilerService server=new StandInCompilerService()) {
            server.token("approved");
            CountDownLatch verified=new CountDownLatch(1), release=new CountDownLatch(1), finished=new CountDownLatch(1);
            HttpClient http=new HeldVerificationClient(HttpClient.newHttpClient(),verified,release,finished);
            CompilerSignInAttemptsTest.Store store=new CompilerSignInAttemptsTest.Store();
            try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client(http),store,()->false)) {
                String id=(String)attempts.start(server.base(),false).get("attemptId");
                assertTrue(verified.await(5,TimeUnit.SECONDS));
                assertEquals("cancelled",attempts.cancel(id).get("event"));
                release.countDown(); assertTrue(finished.await(5,TimeUnit.SECONDS));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(server.count("/v1/credentials/cred01")==0 && System.nanoTime()<deadline)Thread.sleep(10);
                assertNull(store.value,"the commit-boundary cancellation check must retain the empty store");
                assertEquals("cancelled",attempts.status(id).get("event"));
                assertEquals(1,server.count("/v1/credentials/cred01"),"the approved but unsaved device is revoked");
            } finally {release.countDown();}
        }
    }
    /** The HTTP answer is already received; cancellation cannot be rescued by HttpClient throwing first. */
    static final class HeldVerificationClient extends HttpClient {
        final HttpClient delegate; final CountDownLatch verified,release,finished;
        HeldVerificationClient(HttpClient delegate,CountDownLatch verified,CountDownLatch release,CountDownLatch finished){this.delegate=delegate;this.verified=verified;this.release=release;this.finished=finished;}
        @Override public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler) throws IOException,InterruptedException {
            HttpResponse<T> response=delegate.send(request,handler);
            if(request.uri().getPath().equals("/v1/me")) {
                verified.countDown(); boolean done=false; long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(!done && System.nanoTime()<end)try{done=release.await(100,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
                if(!done)throw new IOException("synthetic verification hold timed out");
                Thread.interrupted(); finished.countDown();
            }
            return response;
        }
        @Override public Optional<CookieHandler> cookieHandler(){return delegate.cookieHandler();}
        @Override public Optional<Duration> connectTimeout(){return delegate.connectTimeout();}
        @Override public Redirect followRedirects(){return delegate.followRedirects();}
        @Override public Optional<ProxySelector> proxy(){return delegate.proxy();}
        @Override public SSLContext sslContext(){return delegate.sslContext();}
        @Override public SSLParameters sslParameters(){return delegate.sslParameters();}
        @Override public Optional<Authenticator> authenticator(){return delegate.authenticator();}
        @Override public Version version(){return delegate.version();}
        @Override public Optional<Executor> executor(){return delegate.executor();}
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){return delegate.sendAsync(r,h);}
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){return delegate.sendAsync(r,h,p);}
    }
}
