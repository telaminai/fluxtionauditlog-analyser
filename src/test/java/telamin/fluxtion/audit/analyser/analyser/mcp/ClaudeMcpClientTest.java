package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the live M42.4 Claude Code user-scope argument vectors without changing a real Claude configuration. */
class ClaudeMcpClientTest {

    @TempDir
    Path dir;

    @Test
    void checkAndAddUseTheCurrentUserScopedStdioArgumentVectors() {
        List<List<String>> started = new ArrayList<>();
        ClaudeMcpClient client = client(started, new FinishedProcess(0, "fluxtion-analyser"), new FinishedProcess(0, "added"));
        McpLaunchCommand bridge = McpLaunchCommand.of(List.of("/Applications/Java 21/bin/java", "-jar", "/tmp/a b.jar"));

        assertTrue(client.registration().present());
        assertTrue(client.add(bridge).successful());

        assertEquals(List.of("/tools/claude", "mcp", "get", "fluxtion-analyser"), started.get(0));
        assertEquals(List.of("/tools/claude", "mcp", "add", "--scope", "user", "--transport", "stdio",
                "fluxtion-analyser", "--", "/Applications/Java 21/bin/java", "-jar", "/tmp/a b.jar", "--mcp"),
                started.get(1));
    }

    @Test
    void replaceAndProjectCopyKeepTheScopeExplicit() {
        List<List<String>> started = new ArrayList<>();
        ClaudeMcpClient client = client(started, new FinishedProcess(0, "removed"), new FinishedProcess(0, "added"));
        McpLaunchCommand bridge = McpLaunchCommand.of(List.of("/opt/analyser"));

        assertTrue(client.replace(bridge).successful());

        assertEquals(List.of("/tools/claude", "mcp", "remove", "--scope", "user", "fluxtion-analyser"), started.get(0));
        assertEquals(List.of("claude", "mcp", "add", "--scope", "project", "--transport", "stdio",
                        "fluxtion-analyser", "--", "/opt/analyser", "--mcp"),
                ClaudeMcpClient.projectCommandForCopy(bridge));
    }

    @Test
    void missingNamedRegistrationAndSecretOutputStaySafe() {
        ClaudeMcpClient missing = client(new ArrayList<>(), new FinishedProcess(1, "MCP server fluxtion-analyser not found"));
        assertEquals(ClaudeMcpClient.RegistrationStatus.ABSENT, missing.registration().status());

        ClaudeMcpClient failing = client(new ArrayList<>(), new FinishedProcess(1, "token=do-not-display"));
        ClaudeMcpClient.Result result = failing.add(McpLaunchCommand.of(List.of("/opt/analyser")));
        assertFalse(result.detail().contains("do-not-display"));
        assertEquals("token=…", ClaudeMcpClient.redactForDisplay("token=do-not-display"));
    }

    @Test
    void detectionFindsOnlyAnExecutableNamedClaude(@TempDir Path home) throws Exception {
        Path bin = home.resolve("bin");
        Files.createDirectories(bin);
        Path claude = bin.resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        assertTrue(claude.toFile().setExecutable(true));

        assertEquals(claude.toAbsolutePath(), ClaudeMcpClient.findExecutable(bin.toString(), ":", false).orElseThrow());
        assertTrue(ClaudeMcpClient.findExecutable(home.resolve("none").toString(), ":", false).isEmpty());
    }

    private static ClaudeMcpClient client(List<List<String>> started, Process... processes) {
        ArrayDeque<Process> queue = new ArrayDeque<>(List.of(processes));
        return new ClaudeMcpClient(Path.of("/tools/claude"), command -> {
            started.add(List.copyOf(command));
            return queue.removeFirst();
        }, Duration.ofSeconds(1));
    }

    private static final class FinishedProcess extends Process {
        private final int exit;
        private final InputStream output;

        private FinishedProcess(int exit, String output) {
            this.exit = exit;
            this.output = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
        }

        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return output; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return exit; }
        @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
        @Override public int exitValue() { return exit; }
        @Override public void destroy() { }
        @Override public Process destroyForcibly() { return this; }
        @Override public boolean isAlive() { return false; }
    }
}
