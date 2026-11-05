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
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
        AtomicBoolean attached = new AtomicBoolean(true);
        CountDownLatch exited = new CountDownLatch(1);
        Thread watchdog = McpBridge.exitWhenOrphaned(attached::get, Duration.ofMillis(10), exited::countDown);
        assertTrue(watchdog.isDaemon(), "the watchdog never keeps the process alive itself");
        assertFalse(exited.await(100, TimeUnit.MILLISECONDS), "an attached bridge keeps running");
        attached.set(false);
        assertTrue(exited.await(2, TimeUnit.SECONDS), "a gone parent ends the bridge");
    }

    /**
     * The attachment rule the watchdog polls. Wrong-result witness: test only {@code isAlive} — the zombie case (an
     * exited, unreaped parent still reads alive, but its child is already re-parented) then stays attached.
     */
    @Test
    void theBridgeIsAttachedOnlyWhileItsOriginalParentIsStillItsParent() {
        assertFalse(McpBridge.attached(1, true, Optional.of(1L)), "launcher died before Java sampled its parent");
        assertTrue(McpBridge.attached(100, true, Optional.of(100L)), "the launching client, alive and still the parent");
        assertFalse(McpBridge.attached(100, true, Optional.of(1L)), "an unreaped (zombie) parent: re-parented to init");
        assertFalse(McpBridge.attached(100, true, Optional.of(200L)), "re-parented to anything else");
        assertFalse(McpBridge.attached(100, true, Optional.empty()), "no parent any more");
        assertFalse(McpBridge.attached(100, false, Optional.of(100L)),
                "the original handle is gone: a reused pid with another start time is not the launcher");
    }

    /** Enter the real bridge only after the short-lived launcher is gone, with stdin still held open. */
    public static class OrphanBeforeLaunch {
        public static void main(String[] args) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (ProcessHandle.current().parent().map(p -> p.pid() != 1).orElse(false)) {
                if (System.nanoTime() > deadline) throw new IllegalStateException("launcher did not exit");
                Thread.sleep(10);
            }
            McpBridge.launch(new String[0]);
        }
    }

    @Test
    void aLauncherThatDiesBeforeJavaStartsCannotLeaveAnOrphanBridge() throws Exception {
        Path pidFile = dir.resolve("child.pid");
        Path errors = dir.resolve("child.err");
        Path fifo = dir.resolve("stdin.fifo");
        Process mkfifo = new ProcessBuilder("mkfifo", fifo.toString()).start();
        assertTrue(mkfifo.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, mkfifo.exitValue());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c",
                "exec 3<\"$1\"; shift; \"$@\" <&3 3<&- & echo $! > \"$0\"; exit 0", pidFile.toString(), fifo.toString(),
                java, "-Djava.awt.headless=true", "-Duser.home=" + dir, "-cp",
                System.getProperty("java.class.path"), OrphanBeforeLaunch.class.getName());
        for (String variable : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"))
            pb.environment().remove(variable);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(errors.toFile());
        Process launcher = pb.start();
        ProcessHandle child = null;
        // Process.getOutputStream() is closed by Java when its process exits. Own the FIFO writer separately,
        // so the launching shell's exit cannot deliver EOF to the bridge and hide a broken orphan watchdog.
        try (OutputStream heldInput = Files.newOutputStream(fifo)) {
            assertTrue(launcher.waitFor(5, TimeUnit.SECONDS), "the shell launcher exits immediately");
            long pid = Long.parseLong(Files.readString(pidFile).trim());
            child = ProcessHandle.of(pid).orElse(null);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
            while (child != null && child.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
            assertTrue(child == null || !child.isAlive(), "bridge exits even though the test still holds its stdin open");
            assertTrue(Files.readString(errors).contains("the launching process has gone; stopping"),
                    "the watchdog, not EOF or a launch failure, stopped the bridge");
        } finally {
            launcher.getOutputStream().close();
            if (child != null && child.isAlive()) child.destroyForcibly();
            launcher.destroyForcibly();
        }
    }

    /**
     * The grace restarts for each request taken after EOF: three calls piped in, stdin closed at once, each answered
     * by an analyser that takes 500 ms, all answered under a 1200 ms grace. One shared budget from EOF (1500 ms of
     * calls against 1200 ms) would drop the third. Wrong-result witness: drop the per-request deadline reset; the third
     * reply is missing.
     */
    @Test
    void everyRequestReceivedBeforeEndOfInputIsAnsweredEvenWhenTheAnalyserIsSlow() throws Exception {
        HttpServer slow = answering(Duration.ofMillis(500));
        try {
            RestEndpointFile file = new RestEndpointFile(dir.resolve("slow-endpoint"));
            file.write("http://127.0.0.1:" + slow.getAddress().getPort(), "t");
            McpBridge bridge = new McpBridge(file);
            String in = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n"
                    + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
                    + CALL.replace("\"id\":7", "\"id\":2") + CALL.replace("\"id\":7", "\"id\":3")
                    + CALL.replace("\"id\":7", "\"id\":4");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bridge.run(new java.io.ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)), out, Duration.ofMillis(1200));
            List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
            assertEquals(4, lines.size(), "initialize and all three calls are answered: " + lines);
            for (int id = 2; id <= 4; id++) {
                String reply = lines.get(id - 1);
                assertTrue(reply.contains("\"id\":" + id) && reply.contains("\"isError\":false"), reply);
            }
        } finally {
            slow.stop(0);
        }
    }

    /** An "analyser" that answers every {@code /action} with {@code {"ok":true}} after {@code delay}. */
    private static HttpServer answering(Duration delay) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/action", exchange -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "slow-analyser");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return server;
    }

    /**
     * The whole process, through the real entry point: after a completed call (so the HTTP client's threads exist), no
     * thread the bridge leaves behind may keep the JVM up once stdin closes.
     */
    @Test
    void theMcpProcessExitsWhenItsStdinCloses() throws Exception {
        HttpServer analyser = answering(Duration.ZERO);
        Path endpoint = dir.resolve("hosted-endpoint");
        new RestEndpointFile(endpoint).write("http://127.0.0.1:" + analyser.getAddress().getPort(), "t");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-Djava.awt.headless=true", "-Duser.home=" + dir,
                "-cp", System.getProperty("java.class.path"), "telamin.fluxtion.audit.analyser.Main",
                "--mcp", McpBridge.ENDPOINT_FILE_FLAG, endpoint.toString());
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().remove("JDK_JAVA_OPTIONS");
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        try {
            process.getOutputStream().write(CALL.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            java.io.BufferedReader replies = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            String reply = CompletableFuture.supplyAsync(() -> {
                try {
                    return replies.readLine();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).get(30, TimeUnit.SECONDS);
            assertTrue(reply != null && reply.contains("\"id\":7"), "the call was answered: " + reply);
            process.getOutputStream().close();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "the --mcp process exited after its stdin closed");
            assertEquals(0, process.exitValue());
        } finally {
            process.destroyForcibly();
            analyser.stop(0);
        }
    }
}
