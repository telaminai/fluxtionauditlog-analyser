package telamin.fluxtion.audit.analyser.analyser.session;
import org.junit.jupiter.api.Test;
import java.util.List;
import telamin.fluxtion.audit.analyser.analyser.design.*;
import static org.junit.jupiter.api.Assertions.*;
/**
 * The #198 review's reproduction of F2, kept as the reviewer wrote it: an accepted profile reload must request fresh
 * Design discovery rather than keep the candidates found under the old source roots. It drives the generated dispatcher.
 */
class ReviewPr198ReloadDiscoveryTest {
 @Test void acceptedReloadMustRefreshDesignDiscovery() {
  var adapter=new FakeSessionAdapter().withProfile("/work/DEMO/profile");
  var driver=LocalAgentSessionTest.driver(adapter);
  driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(),"/work/DEMO/profile",TransitionKind.EXPLICIT_SWITCH,"test"));
  long before=driver.processor().designSession.discoveryGeneration();
  driver.post(new DesignEvents.DiscoveryCompleted(new DesignDiscovery("spring",List.of(new DesignDiscovery.FileRef("/work/DEMO/old/design.xml","definition")),"source roots"),before));
  driver.post(new SessionEvents.ProjectProfileObserved("DEMO-key","disk-new","disk-old",false));
  driver.post(new SessionEvents.ProjectProfileReloaded(0,"DEMO-key",true,"reloaded","socket"));
  assertTrue(driver.processor().designSession.discoveryGeneration()>before,
    "an accepted profile reload must request fresh discovery rather than retain old-root candidates");
 }
}
