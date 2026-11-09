package telamin.fluxtion.audit.analyser.analyser.account;
import org.junit.jupiter.api.*;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class SignInCancellationRegressionTest {
 String old;
 @BeforeEach void set(){old=System.getProperty("fluxtion.login.verificationOrigin");System.setProperty("fluxtion.login.verificationOrigin","https://compiler.example.com");}
 @AfterEach void unset(){if(old==null)System.clearProperty("fluxtion.login.verificationOrigin");else System.setProperty("fluxtion.login.verificationOrigin",old);}
 @Test void cancelDuringVerificationRevokesUnsavedCredential() throws Exception {
  try(StandInCompilerService server=new StandInCompilerService()){
   server.token("approved");CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
   server.beforeMe=()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}};
   CompilerSignInAttemptsTest.Store store=new CompilerSignInAttemptsTest.Store();
   CompilerAccountClient client=new CompilerAccountClient(HttpClient.newHttpClient(),d->{},System::nanoTime);
   try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(client,store,()->false)){
    String id=(String)attempts.start(server.base(),false).get("attemptId");assertTrue(entered.await(5,TimeUnit.SECONDS));
    assertEquals("cancelled",attempts.cancel(id).get("event"));release.countDown();
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
    while(server.count("/v1/credentials/cred01")==0 && System.nanoTime()<deadline)Thread.sleep(10);
    assertNull(store.value);assertEquals(1,server.count("/v1/credentials/cred01"));
    assertNotNull(server.credentials.get("cred01")[3]);
   }finally{release.countDown();}
  }
 }
 @Test void completedAttemptsDoNotConsumePendingCapacity() throws Exception {
  try(StandInCompilerService server=new StandInCompilerService()){
   CompilerSignInAttemptsTest.Store store=new CompilerSignInAttemptsTest.Store();
   store.value=new CompilerCredential(server.base(),"cred01","acct-demo",StandInCompilerService.BEARER);
   try(CompilerSignInAttempts attempts=new CompilerSignInAttempts(new CompilerAccountClient(HttpClient.newHttpClient(),d->{},System::nanoTime),store,()->false)){
    for(int i=0;i<16;i++) assertEquals("signed_in",attempts.start(server.base(),false).get("event"));
    Map<String,Object> result=attempts.start(server.base(),false);
    assertEquals("signed_in",result.get("event"));
   }
  }
 }
}
