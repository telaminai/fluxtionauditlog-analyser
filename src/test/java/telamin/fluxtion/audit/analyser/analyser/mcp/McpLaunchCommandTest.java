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
}
