package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One running local-agent process, as {@code LocalAgentAdapter} sees it: a line-oriented stdin, an exit, and a tree
 * kill. The real one is {@link OsAgentLauncher}'s; tests replay recorded fixtures through a fake, so no live CLI runs
 * in the suite.
 */
public interface AgentProcess {

    /** Write one line (a JSON message) to stdin, flushed. */
    void writeLine(String line) throws IOException;

    /** The process id, or -1 for a fake. */
    long pid();

    /** Completes with the exit code when the process has exited. */
    CompletableFuture<Integer> onExit();

    /** The last lines of stderr, bounded and redacted; "" when there were none. */
    String stderrTail();

    /** Stop the whole tree: snapshot descendants, SIGTERM, wait up to {@code grace}, kill the survivors (§4.7). */
    void terminateTree(Duration grace);

    /** Starts processes. The ONE place the analyser builds a process for its local agent is {@link OsAgentLauncher}. */
    interface Launcher {
        /**
         * @param stdoutLine called on a reader thread for every stdout line, in order
         */
        AgentProcess start(List<String> command, Map<String, String> extraEnvironment, Path workingDir,
                           Consumer<String> stdoutLine) throws IOException;
    }
}
