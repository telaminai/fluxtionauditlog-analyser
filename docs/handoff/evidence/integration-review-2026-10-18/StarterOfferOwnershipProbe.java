package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.FactRefs;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/**
 * Review-only boundary probe, deliberately outside the maintained test tree while the cancellation policy is open.
 * Inject an already-issued graph-offer effect; in its answer callback an external client opens project B before the
 * old answer returns. This exercises the real adapter and graph loader, NOT the directory watch or a live model.
 * Intended assertion: an offer issued for A cannot install A's graph in B without the session deciding ownership.
 */
public final class StarterOfferOwnershipProbe {
    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "starter-offer-probe-");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path a = Files.createDirectories(tmp.resolve("DEMO-A/.analyser")).resolve("project.fluxtion-settings");
        Path b = Files.createDirectories(tmp.resolve("DEMO-B/.analyser")).resolve("project.fluxtion-settings");
        Files.writeString(a, "share.version=1\n");
        Files.writeString(b, "share.version=1\n");
        Path graph = tmp.resolve("DEMO-A/first-build.graphml");
        Files.copy(Path.of("src/test/resources/topology/demo-quote-processor.graphml"), graph);
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        MainFrame[] made = new MainFrame[1];
        AtomicReference<Throwable> problem = new AtomicReference<>();
        try {
            onEdt(() -> made[0] = new MainFrame());
            MainFrame frame = made[0];
            ActionExecutor executor = (ActionExecutor) field(frame, "actionExecutor");
            onEdt(() -> render(executor, "open", Map.of("project", a.toString())));
            onEdt(() -> {
                frame.starterGraphOffer = shown -> {
                    try {
                        render(executor, "open", Map.of("project", b.toString()));
                    } catch (Exception error) { problem.set(error); }
                    return true;
                };
                try {
                    FactRefs refs = (FactRefs) field(frame, "factRefs");
                    var method = MainFrame.class.getDeclaredMethod("offerStarterGraph", SessionEffects.OfferStarterGraphEffect.class);
                    method.setAccessible(true);
                    method.invoke(frame, new SessionEffects.OfferStarterGraphEffect(0, 41,
                            refs.put(graph.toString()), "DEMO-A/first-build.graphml"));
                } catch (Exception error) { throw new AssertionError(error); }
            });
            // Drain the queued answer, whose graph load is synchronous on the EDT.
            onEdt(() -> { });
            assertNull(problem.get(), "the external project switch itself must succeed");
            onEdt(() -> {
                SessionDriver driver = (SessionDriver) field(frame, "session");
                assertTrue(driver.processor().activeProject.isAt(b.toString()), "DEMO-B is now active");
                assertFalse(driver.snapshot().graphOpen(),
                        "a superseded starter offer must not open DEMO-A's graph in DEMO-B");
            });
        } finally {
            if (made[0] != null) onEdt(made[0]::dispose);
            System.setProperty("user.home", previousHome);
        }
    }
}
