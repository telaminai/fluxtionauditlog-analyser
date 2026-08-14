package telamin.fluxtion.audit.analyser.analyser.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SourceServiceTest {

    private static final String PKG = "com.acme.marketmaker.strategy";

    private static Path writeEp(Path root, String name, String body) throws IOException {
        Path dir = root.resolve(PKG.replace('.', '/'));
        Files.createDirectories(dir);
        Path f = dir.resolve(name + ".java");
        Files.writeString(f, "package " + PKG + ";\n"
                + "import com.acme.node.MarketDataBookNode;\n"
                + "public class " + name + " {\n" + body + "\n}\n");
        return f;
    }

    private SourceRootResolver rootWithTwoProcessors(Path root) throws IOException {
        writeEp(root, "DemoMarketMakerStrategy",
                "  private final transient MarketDataBookNode hedgeRateSource = null;\n"
                        + "  private final transient MarketDataBookNode bidMakerOrder = null;");
        writeEp(root, "OtherStrategy",
                "  private final transient MarketDataBookNode unrelatedNode = null;");
        return new SourceRootResolver(List.of(root.toString()));
    }

    @Test
    void discoversProcessorsInPackage(@TempDir Path root) throws IOException {
        SourceRootResolver r = rootWithTwoProcessors(root);
        List<String> found = SourceService.discover(r, PKG);
        assertTrue(found.contains(PKG + ".DemoMarketMakerStrategy"));
        assertTrue(found.contains(PKG + ".OtherStrategy"));
        assertEquals(2, found.size());
    }

    @Test
    void infersProcessorByInstanceIdCoverage(@TempDir Path root) throws IOException {
        SourceRootResolver r = rootWithTwoProcessors(root);
        List<String> candidates = SourceService.discover(r, PKG);
        String inferred = SourceService.infer(r, candidates,
                Set.of("hedgeRateSource", "bidMakerOrder"), PKG + ".OtherStrategy");
        assertEquals(PKG + ".DemoMarketMakerStrategy", inferred, "best-covering processor wins");
    }

    @Test
    void inferenceFallsBackWhenNothingMatches(@TempDir Path root) throws IOException {
        SourceRootResolver r = rootWithTwoProcessors(root);
        List<String> candidates = SourceService.discover(r, PKG);
        String fallback = PKG + ".DemoMarketMakerStrategy";
        assertEquals(fallback, SourceService.infer(r, candidates, Set.of("nothingMatches"), fallback));
    }

    @Test
    void resolvesInstanceToSourceForSelectedProcessor(@TempDir Path root) throws IOException {
        SourceRootResolver r = rootWithTwoProcessors(root);
        SourceService svc = new SourceService();
        svc.configure(List.of(root.toString()), PKG + ".DemoMarketMakerStrategy");
        assertTrue(svc.selectedModel().isPresent());
        assertEquals("com.acme.node.MarketDataBookNode", svc.fqnForInstance("hedgeRateSource"));
        assertTrue(svc.sourceForFqn(PKG + ".DemoMarketMakerStrategy").isPresent());
        assertTrue(svc.sourceForFqn("com.acme.does.NotExist").isEmpty());
    }
}
