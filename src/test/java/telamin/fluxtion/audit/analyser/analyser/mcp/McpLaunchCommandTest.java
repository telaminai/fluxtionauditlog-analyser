package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpLaunchCommandTest {

    @TempDir
    Path dir;

    @Test
    void addsTheBridgeFlagToAnExactArgumentVector() {
        McpLaunchCommand command = McpLaunchCommand.of(List.of("/opt/Fluxtion Analyser/analyser", "--quiet"));
        assertEquals(List.of("/opt/Fluxtion Analyser/analyser", "--quiet", "--mcp"), command.command());
        assertEquals(List.of("/opt/Fluxtion Analyser/analyser", "--quiet"), command.launcher());
    }

    @Test
    void rejectsACommandThatAlreadyContainsTheBridgeFlag() {
        assertThrows(IllegalArgumentException.class,
                () -> McpLaunchCommand.of(List.of("analyser", "--mcp")));
    }

    @Test
    void findsTheDocumentedAbsoluteJbangLauncher() throws Exception {
        Path launcher = dir.resolve(".jbang/bin/analyser");
        Files.createDirectories(launcher.getParent());
        Files.createFile(launcher);

        McpLaunchCommand command = McpLaunchCommand.installedJbang(dir).orElseThrow();
        assertEquals(List.of(launcher.toAbsolutePath().toString(), "--mcp"), command.command());
    }

    @Test
    void selectedFatjarKeepsJavaAndJarAsSeparateArguments() {
        McpLaunchCommand command = McpLaunchCommand.jar(
                Path.of("/Applications/Java 21/bin/java"), Path.of("/tmp/Fluxtion Analyser.jar"));
        assertEquals(List.of("/Applications/Java 21/bin/java", "-jar", "/tmp/Fluxtion Analyser.jar", "--mcp"),
                command.command());
    }

    @Test
    void acceptsOnlyOneDirectlyLaunchedJarAsTheSafeRunningJarFallback() throws Exception {
        Path java = dir.resolve("java");
        Path jar = dir.resolve("Fluxtion Analyser.jar");
        Files.createFile(java);
        Files.createFile(jar);

        McpLaunchCommand command = McpLaunchCommand.fromRunningJar(java,
                new String[]{"-Duser.home=/tmp/isolated-analyser", "-Xmx1g", "-jar", jar.toString(), "--rest"},
                jar.toString(), ":", "/ignored-fallback").orElseThrow();
        assertEquals(List.of(java.toAbsolutePath().toString(), "-Duser.home=/tmp/isolated-analyser", "-Xmx1g",
                        "-jar", jar.toAbsolutePath().toString(), "--mcp"),
                command.command());
        assertTrue(McpLaunchCommand.fromRunningJar(java, new String[0],
                        jar + ":" + dir.resolve("other.jar"), ":", "/tmp/isolated-analyser").isEmpty(),
                "a multi-entry classpath is not a launcher we can safely reconstruct");
    }

    @Test
    void argumentRestrictedProcessMetadataStillCarriesTheCurrentUserHome() throws Exception {
        Path java = dir.resolve("java");
        Path jar = dir.resolve("analyser.jar");
        Files.createFile(java);
        Files.createFile(jar);

        McpLaunchCommand command = McpLaunchCommand.fromRunningJar(java, new String[0], jar.toString(), ":",
                dir.resolve("isolated-home").toString()).orElseThrow();
        assertEquals(List.of(java.toAbsolutePath().toString(), "-Duser.home=" + dir.resolve("isolated-home").toAbsolutePath(),
                "-jar", jar.toAbsolutePath().toString(), "--mcp"), command.command());
    }

    @Test
    void refusesDebugOrInstrumentationOptionsForTheDirectJarFallback() throws Exception {
        Path java = dir.resolve("java");
        Path jar = dir.resolve("analyser.jar");
        Files.createFile(java);
        Files.createFile(jar);

        for (String unsafe : List.of("-agentlib:jdwp=transport=dt_socket,address=5005,suspend=y",
                "-agentpath:/tmp/instrument.dylib", "-javaagent:/tmp/instrument.jar", "-Xdebug",
                "-Xrunjdwp:transport=dt_socket,address=5005", "-XX:+UseDebugger")) {
            assertTrue(McpLaunchCommand.fromRunningJar(java,
                            new String[]{"-Duser.home=/tmp/isolated-analyser", unsafe, "-jar", jar.toString()},
                            jar.toString(), ":", "/ignored-fallback").isEmpty(),
                    "a client must not inherit " + unsafe);
        }
    }

    @Test
    void refusesAnyJvmOptionCarryingSomethingCredentialShaped() throws Exception {
        // The vector is written into the AI client's OWN config file (claude mcp add / codex) and
        // rendered for copying by GenericMcpConfiguration, so a secret on this app's command line would
        // leave the machine by that route. D-C6 already refuses credential shapes where reports are
        // published; this is the same rule at an entrance that had been missed.
        Path java = dir.resolve("java");
        Path jar = dir.resolve("analyser.jar");
        Files.createFile(java);
        Files.createFile(jar);

        for (String secret : List.of("-Dhttp.proxyPassword=hunter2", "-Dapi_key=abcd1234",
                "-Dmy.token=zzz", "-Dauthorization=Bearer xyz", "-Daws.key=AKIAIOSFODNN7EXAMPLE")) {
            assertTrue(McpLaunchCommand.fromRunningJar(java,
                            new String[]{"-Duser.home=/tmp/isolated-analyser", secret, "-jar", jar.toString()},
                            jar.toString(), ":", "/ignored-fallback").isEmpty(),
                    "a client config must not inherit " + secret);
        }
    }

    @Test
    void anOrdinaryLaunchIsStillAccepted_theGateMustNotRefuseEverything() throws Exception {
        // the counter-test: a refusal that fires on normal options would silently remove the fallback
        Path java = dir.resolve("java");
        Path jar = dir.resolve("analyser.jar");
        Files.createFile(java);
        Files.createFile(jar);

        assertTrue(McpLaunchCommand.fromRunningJar(java,
                        new String[]{"-Duser.home=/tmp/isolated-analyser", "-Xmx2g", "-Dfile.encoding=UTF-8",
                                "-jar", jar.toString()},
                        jar.toString(), ":", "/ignored-fallback").isPresent(),
                "ordinary JVM options must still produce a bridge command");
    }
}
