package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The ONE place the analyser builds an operating-system process for its local agent (spec §4.1; a one-home rule in
 * {@code OneDispatchModelTest} pins {@code ProcessBuilder} to this class within the {@code localagent} package).
 *
 * <p><b>Hygiene (§4.7).</b> stdout and stderr are drained continuously, each on its own daemon thread, so a chatty CLI
 * can never fill a pipe and stall. stderr is kept as a bounded tail and redacted before anyone reads it.
 */
public final class OsAgentLauncher implements AgentProcess.Launcher {

    /** How much of stderr is kept: the tail, for the "process exited" line. */
    static final int STDERR_TAIL = 8 * 1024;


    @Override
    public AgentProcess start(List<String> command, Map<String, String> extraEnvironment, Path workingDir,
                              Consumer<String> stdoutLine) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.environment().putAll(extraEnvironment);
        Process process = pb.start();
        OsProcess p = new OsProcess(process);
        Thread out = new Thread(() -> readLines(process.getInputStream(), stdoutLine), "analyser-local-agent-stdout");
        out.setDaemon(true);
        out.start();
        Thread err = new Thread(() -> p.drainStderr(process.getErrorStream()), "analyser-local-agent-stderr");
        err.setDaemon(true);
        err.start();
        return p;
    }

    /** Review S6: one stdout line is at most this long; a longer one is dropped whole, never buffered without bound. */
    static final int MAX_LINE = 16 * 1024 * 1024;

    static void readLines(InputStream in, Consumer<String> sink) {
        readLines(in, sink, MAX_LINE);
    }

    static void readLines(InputStream in, Consumer<String> sink, int maxLine) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder line = new StringBuilder();
            boolean overlong = false;
            char[] buf = new char[8192];
            for (int n; (n = r.read(buf)) >= 0; ) {
                for (int i = 0; i < n; i++) {
                    char c = buf[i];
                    if (c == '\n') {
                        if (!overlong && !line.toString().isBlank()) sink.accept(line.toString().replace("\r", ""));
                        else if (overlong) sink.accept("{\"type\":\"analyser_overlong_line\"}");
                        line.setLength(0);
                        overlong = false;
                    } else if (!overlong) {
                        line.append(c);
                        if (line.length() > maxLine) {
                            overlong = true;
                            line.setLength(0);
                        }
                    }
                }
            }
            if (!overlong && !line.toString().isBlank()) sink.accept(line.toString());
        } catch (IOException ignored) {
            // the process ended; its exit is reported by onExit
        }
    }

    /** Redact secret-shaped values with the shared rule (review S4). */
    public static String redact(String text) {
        return telamin.fluxtion.audit.analyser.analyser.core.Redaction.all(text);
    }

    static final class OsProcess implements AgentProcess {
        private final Process process;
        private final BufferedWriter stdin;
        private final StringBuilder tail = new StringBuilder();

        OsProcess(Process process) {
            this.process = process;
            this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }

        void drainStderr(InputStream in) {
            byte[] buf = new byte[4096];
            try {
                for (int n; (n = in.read(buf)) >= 0; ) {
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    synchronized (tail) {
                        tail.append(chunk);
                        if (tail.length() > STDERR_TAIL) tail.delete(0, tail.length() - STDERR_TAIL);
                    }
                }
            } catch (IOException ignored) {
                // the process ended
            }
        }

        @Override
        public synchronized void writeLine(String line) throws IOException {
            stdin.write(line);
            stdin.write('\n');
            stdin.flush();
        }

        @Override
        public long pid() {
            return process.pid();
        }

        @Override
        public CompletableFuture<Integer> onExit() {
            return process.onExit().thenApply(Process::exitValue);
        }

        @Override
        public String stderrTail() {
            synchronized (tail) {
                return redact(tail.toString());
            }
        }

        @Override
        public void terminateTree(Duration grace) {
            ProcessTree.terminate(process.toHandle(), grace);
        }
    }
}
