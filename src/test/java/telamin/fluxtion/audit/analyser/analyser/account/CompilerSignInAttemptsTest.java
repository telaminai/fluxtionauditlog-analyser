package telamin.fluxtion.audit.analyser.analyser.account;

import org.junit.jupiter.api.*;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class CompilerSignInAttemptsTest {
    StandInCompilerService service; String old;
    static class Store implements CredentialStore {
        CompilerCredential value;
        public String description(){return "synthetic memory";}
        public synchronized Optional<CompilerCredential> load(){return Optional.ofNullable(value);}
        public synchronized void save(CompilerCredential c){value=c;}
        public synchronized void delete(){value=null;}
    }
    Store store; CompilerAccountClient client;
    @BeforeEach void setup() throws Exception {
        old=System.getProperty("fluxtion.login.verificationOrigin");
        System.setProperty("fluxtion.login.verificationOrigin","https://compiler.example.com");
        service=new StandInCompilerService(); store=new Store(); AtomicLong now=new AtomicLong();
        client=new CompilerAccountClient(HttpClient.newHttpClient(), d->{Thread.sleep(10);now.addAndGet(d.toNanos());},now::get);
    }
    @AfterEach void cleanup(){service.close();if(old==null)System.clearProperty("fluxtion.login.verificationOrigin");else System.setProperty("fluxtion.login.verificationOrigin",old);}
    Map<String,Object> terminal(CompilerSignInAttempts attempts,String id) throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        Map<String,Object> r;
        do{r=attempts.status(id);if(!"pending".equals(r.get("event")))return r;Thread.sleep(10);}while(System.nanoTime()<until);
        fail("attempt did not finish");return r;
    }
    @Test void approvalSavesVerifiedIdentityAndDoesNotCertifyExternalBuild() throws Exception {
        service.token("approved");
        try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client,store,()->false)) {
            Map<String,Object> first=attempts.start(service.base(),false);
            assertEquals("pending",first.get("event"));assertTrue(first.containsKey("userCode"));
            Map<String,Object> end=terminal(attempts,(String)first.get("attemptId"));
            assertEquals("signed_in",end.get("event"));assertEquals("acct-demo",end.get("accountId"));
            assertEquals("not-observed",end.get("effectiveBuildIdentity"));assertNotNull(store.value);
            assertFalse(end.toString().contains(StandInCompilerService.BEARER));
            assertFalse(first.toString().contains(StandInCompilerService.DEVICE_CODE));
            assertEquals("signed_in",attempts.start(service.base(),false).get("event"));
        }
    }
    @Test void ownershipCancellationAndRestartNeverAdoptAnotherAttempt() throws Exception {
        service.token("pending");
        try(CompilerSignInAttempts a=new CompilerSignInAttempts(client,store,()->false);
            CompilerSignInAttempts b=new CompilerSignInAttempts(client,store,()->false)) {
            String id=(String)a.start(service.base(),false).get("attemptId");
            assertEquals("unknown",b.status(id).get("event"));assertEquals("unknown",b.cancel(id).get("event"));
            assertEquals("cancelled",a.cancel(id).get("event")); assertNull(store.value);
            assertEquals("cancelled",a.status(id).get("event"));
        }
    }
    @Test void denialAndMissingServerIdentityNeverSave() throws Exception {
        for(boolean missing:List.of(false,true)) {
            if(missing){service.close();service=new StandInCompilerService();}
            service.token(missing?"approved":"access_denied"); if(missing)service.meBody="{}";
            try(CompilerSignInAttempts a=new CompilerSignInAttempts(client,store,()->false)) {
                String id=(String)a.start(service.base(),false).get("attemptId");
                assertEquals(missing?"service_error":"denied",terminal(a,id).get("event")); assertNull(store.value);
            }
        }
    }
    @Test void environmentOverrideMakesNoRequest() {
        try(CompilerSignInAttempts a=new CompilerSignInAttempts(client,store,()->true)) {
            Map<String,Object> result = a.start(service.base(),false);
            assertEquals("usage_error",result.get("event")); assertNull(result.get("recovery")); assertTrue(service.requests().isEmpty());
        }
    }
}
