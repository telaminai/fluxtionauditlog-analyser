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

/** Pins the externally-verified M42.3 Codex CLI vector without touching anyone's real Codex configuration. */
class CodexMcpClientTest {

    @TempDir
    Path dir;

    @Test
    void checkAndAddUseTheCurrentCodexCliArgumentVectors() {
        List<List<String>> started = new ArrayList<>();
        CodexMcpClient client = client(started, new FinishedProcess(0, "{}"), new FinishedProcess(0, "added"));
        McpLaunchCommand bridge = McpLaunchCommand.of(List.of("/Applications/Java 21/bin/java", "-jar", "/tmp/a b.jar"));

        assertTrue(client.registration().present());
        assertTrue(client.add(bridge).successful());

        assertEquals(List.of("/tools/codex", "mcp", "get", "fluxtion-analyser", "--json"), started.get(0));
        assertEquals(List.of("/tools/codex", "mcp", "add", "fluxtion-analyser", "--",
                "/Applications/Java 21/bin/java", "-jar", "/tmp/a b.jar", "--mcp"), started.get(1));
    }

    @Test
    void replaceRemovesOnlyTheNamedServerBeforeAddingTheExactBridgeVector() {
        List<List<String>> started = new ArrayList<>();
        CodexMcpClient client = client(started, new FinishedProcess(0, "removed"), new FinishedProcess(0, "added"));
        McpLaunchCommand bridge = McpLaunchCommand.of(List.of("/opt/analyser"));

        assertTrue(client.replace(bridge).successful());

        assertEquals(List.of("/tools/codex", "mcp", "remove", "fluxtion-analyser"), started.get(0));
        assertEquals(List.of("/tools/codex", "mcp", "add", "fluxtion-analyser", "--", "/opt/analyser", "--mcp"),
                started.get(1));
    }

    @Test
    void missingNamedRegistrationIsNotMistakenForACliFailure() {
        List<List<String>> started = new ArrayList<>();
        CodexMcpClient client = client(started, new FinishedProcess(1, "No MCP server named fluxtion-analyser was found"));

        CodexMcpClient.Registration registration = client.registration();

        assertEquals(CodexMcpClient.RegistrationStatus.ABSENT, registration.status());
        assertEquals(1, started.size());
    }

    @Test
    void failuresKeepCliOutputOutOfTheResultAndRedactAccidentalSecrets() {
        CodexMcpClient client = client(new ArrayList<>(), new FinishedProcess(1, "api_key=do-not-display"));

        CodexMcpClient.Result result = client.add(McpLaunchCommand.of(List.of("/opt/analyser")));

        assertEquals(CodexMcpClient.Status.FAILED, result.status());
        assertFalse(result.detail().contains("do-not-display"));
        assertEquals("api_key=…", CodexMcpClient.redactForDisplay("api_key=do-not-display"));
    }

    @Test
    void detectionFindsOnlyAnExecutableNamedCodex(@TempDir Path home) throws Exception {
        Path bin = home.resolve("bin");
        Files.createDirectories(bin);
        Path codex = bin.resolve("codex");
        Files.writeString(codex, "#!/bin/sh\nexit 0\n");
        assertTrue(codex.toFile().setExecutable(true));

        assertEquals(codex.toAbsolutePath(), CodexMcpClient.findExecutable(bin.toString(), ":", false).orElseThrow());
        assertTrue(CodexMcpClient.findExecutable(home.resolve("none").toString(), ":", false).isEmpty());
    }

    @Test
    void copyFallbackQuotesPathsButProcessCommandsRemainSeparateArguments() {
        McpLaunchCommand bridge = McpLaunchCommand.of(List.of("/Applications/Java 21/bin/java", "-jar", "/tmp/a b.jar"));

        assertEquals("codex mcp add fluxtion-analyser -- '/Applications/Java 21/bin/java' -jar '/tmp/a b.jar' --mcp",
                CodexMcpClient.shellDisplay(CodexMcpClient.addCommandForCopy(bridge)));
    }

    private static CodexMcpClient client(List<List<String>> started, Process... processes) {
        ArrayDeque<Process> queue = new ArrayDeque<>(List.of(processes));
        return new CodexMcpClient(Path.of("/tools/codex"), command -> {
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
