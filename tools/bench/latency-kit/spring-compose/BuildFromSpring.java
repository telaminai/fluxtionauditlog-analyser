import com.telamin.fluxtion.builder.extern.spring.FluxtionSpring;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.util.ClassUtils;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The integrator's build.
 *
 * <p>Point Fluxtion at the supplier jars and the wiring file. Nothing here knows the graph: it hands
 * over a classloader, an XML path, and the ONE decision that is genuinely the integrator's — which
 * performance profile the deployment wants.
 *
 * <p>That last part matters. Built without a profile the generated processor carries dirty-flag guards,
 * because the framework default keeps conditional propagation. Applying LOWEST_LATENCY is what produces
 * the guard-free straight-line dispatch the benchmarks measure — and it is a property of the
 * deployment, not of any supplier's code.
 */
public class BuildFromSpring {
    public static void main(String[] a) throws Exception {
        Path jarDir = Path.of(a[0]);
        URL[] jars;
        try (var s = Files.list(jarDir)) {
            jars = s.filter(f -> f.toString().endsWith(".jar"))
                    .map(f -> { try { return f.toUri().toURL(); } catch (Exception e) { throw new RuntimeException(e); } })
                    .toArray(URL[]::new);
        }
        for (URL u : jars) { System.out.println("supplier jar: " + Path.of(u.toURI()).getFileName()); }

        ClassLoader vendorLoader = new URLClassLoader(jars, BuildFromSpring.class.getClassLoader());
        ClassUtils.overrideThreadContextClassLoader(vendorLoader);
        Thread.currentThread().setContextClassLoader(vendorLoader);
        System.setProperty("fluxtion.sourceGeneratorId", "local");

        var context = new FileSystemXmlApplicationContext(new String[]{Path.of(a[1]).toUri().toString()},
                false);
        context.setClassLoader(vendorLoader);
        context.refresh();

        FluxtionSpring.compileAot(context,
                (EventProcessorConfig cfg) ->
                        cfg.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY),
                compiler -> {
                    compiler.setClassName("SpringQuotingCore");
                    compiler.setPackageName("app.spring");
                    compiler.setOutputDirectory(a[2]);
                    compiler.setResourcesOutputDirectory(a[2]);
                    compiler.setWriteSourceToFile(true);
                    compiler.setCompileSource(false);
                    compiler.setFormatSource(false);
                });
        System.out.println("generated OK");
    }
}
