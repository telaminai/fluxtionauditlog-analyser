package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A bridge whose client has gone must not outlive it. Old {@code --mcp} processes were found still running days after
 * their sessions ended. A client shuts a stdio server down by closing its stdin, so EOF must end the bridge promptly —
 * also while it is blocked on a call to an analyser that does not answer, which would otherwise hold it for the 60 s
 * call timeout — and the {@code --mcp} process must exit when whoever launched it dies.
 *
 * <p>Wrong-result witness: restore the old loop (read stdin on the calling thread); the blocked-call tests then wait
 * the full call timeout and fail their bound.
 */
class McpBridgeExitTest {

    private static final String CALL = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"analyser_context\",\"arguments\":{}}}\n";

    @TempDir
    Path dir;

    /** An "analyser" that accepts every connection and never answers: an HTTP call to it blocks. */
    private ServerSocket silent;
    private final List<Socket> held = new CopyOnWriteArrayList<>();
    private final CountDownLatch connected = new CountDownLatch(1);

    @BeforeEach
    void startSilentAnalyser() throws IOException {
        silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(() -> {
            try {
                while (true) {
                    held.add(silent.accept());
                    connected.countDown();
                }
            } catch (IOException closed) {
                // test over
            }
        }, "silent-analyser");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopSilentAnalyser() throws IOException {
        silent.close();
        for (Socket s : held) s.close();
    }

    private RestEndpointFile silentEndpoint() throws IOException {
        RestEndpointFile file = new RestEndpointFile(dir.resolve("rest-endpoint"));
        file.write("http://127.0.0.1:" + silent.getLocalPort(), "t");
        return file;
    }

    @Test
    void stdinClosingWhileACallIsBlockedEndsTheLoopPromptly() throws Exception {
        McpBridge bridge = new McpBridge(silentEndpoint());
        PipedOutputStream client = new PipedOutputStream();
        PipedInputStream stdin = new PipedInputStream(client);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        CompletableFuture<Void> loop = CompletableFuture.runAsync(() -> {
            try {
                bridge.run(stdin, stdout, Duration.ofMillis(200));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        client.write(CALL.getBytes(StandardCharsets.UTF_8));
        client.flush();
        assertTrue(connected.await(10, TimeUnit.SECONDS), "the call reached the analyser and is now blocked on it");
        assertFalse(loop.isDone(), "the call is still in flight");

        long closedAt = System.nanoTime();
        client.close();                                       // the client goes away
        loop.get(5, TimeUnit.SECONDS);                        // well inside the 60 s call timeout
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closedAt);
        assertTrue(tookMs < 5_000, "the loop returned " + tookMs + " ms after stdin closed");
        assertEquals("", stdout.toString(StandardCharsets.UTF_8), "nobody is left to answer");
    }

    @Test
    void stdinClosingWhileIdleEndsTheLoopAtOnce() throws Exception {
        McpBridge bridge = new McpBridge(silentEndpoint());
        PipedOutputStream client = new PipedOutputStream();
        PipedInputStream stdin = new PipedInputStream(client);
        CompletableFuture<Void> loop = CompletableFuture.runAsync(() -> {
            try {
                bridge.run(stdin, OutputStream.nullOutputStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(100);
        assertFalse(loop.isDone(), "an open stdin keeps the bridge alive");
        client.close();
        loop.get(2, TimeUnit.SECONDS);
    }

    @Test
    void theLoopLeavesItsThreadUninterrupted() throws Exception {
        McpBridge bridge = new McpBridge(silentEndpoint());
        AtomicBoolean interruptedAfter = new AtomicBoolean(true);
        CompletableFuture.runAsync(() -> {
            try {
                bridge.run(new java.io.ByteArrayInputStream(CALL.getBytes(StandardCharsets.UTF_8)),
                        OutputStream.nullOutputStream(), Duration.ofMillis(100));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            interruptedAfter.set(Thread.currentThread().isInterrupted());
        }).get(5, TimeUnit.SECONDS);
        assertFalse(interruptedAfter.get(), "the grace interrupt must not leak to the caller's thread");
    }

    @Test
    void theWatchdogExitsWhenTheLaunchingProcessDies() throws Exception {
        AtomicBoolean parentAlive = new AtomicBoolean(true);
        CountDownLatch exited = new CountDownLatch(1);
        Thread watchdog = McpBridge.exitWhenOrphaned(parentAlive::get, Duration.ofMillis(10), exited::countDown);
        assertTrue(watchdog.isDaemon(), "the watchdog never keeps the process alive itself");
        assertFalse(exited.await(100, TimeUnit.MILLISECONDS), "a live parent keeps the bridge running");
        parentAlive.set(false);
        assertTrue(exited.await(2, TimeUnit.SECONDS), "a dead parent ends the bridge");
    }

    /** The whole process, through the real entry point: no thread the bridge leaves behind may keep the JVM up. */
    @Test
    void theMcpProcessExitsWhenItsStdinClosesMidCall() throws Exception {
        Path endpoint = dir.resolve("hosted-endpoint");
        silentEndpoint();
        new RestEndpointFile(endpoint).write("http://127.0.0.1:" + silent.getLocalPort(), "t");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-Djava.awt.headless=true", "-Duser.home=" + dir,
                "-cp", System.getProperty("java.class.path"), "telamin.fluxtion.audit.analyser.Main",
                "--mcp", McpBridge.ENDPOINT_FILE_FLAG, endpoint.toString());
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        try {
            process.getOutputStream().write(CALL.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            assertTrue(connected.await(30, TimeUnit.SECONDS), "the bridge's call reached the silent analyser");
            assertTrue(process.isAlive(), "blocked on the call");
            process.getOutputStream().close();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "the --mcp process exited after its stdin closed");
            assertEquals(0, process.exitValue());
        } finally {
            process.destroyForcibly();
        }
    }
}
