package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A local agent CLI the analyser can host in its Assistant tab (spec-claude-code-provider.md §4.1): how to find it, the
 * command line of its posture, how to read and write its stream, and how a typed line classifies. One implementation,
 * {@link ClaudeCodeProvider}. Another CLI (Codex) would be a second, with its own protocol probe first.
 *
 * <p>Pure: nothing here starts a process or touches Swing. {@code LocalAgentAdapter} performs; the session decides.
 */
public interface LocalAgentProvider {

    /** A short id for the record and the header, e.g. {@code claude-code}. */
    String id();

    /** Where the CLI is, or why it is not found, naming every place searched. */
    ClaudeResolver.Resolution resolve(Map<String, String> environment, Path userHome);

    /**
     * Everything one spawn's command line depends on.
     *
     * @param executable      the resolved CLI
     * @param sessionId       {@code --session-id} for a new conversation, {@code --resume} when {@code resume}
     * @param model           the Claude Code model setting (its own, never {@code llmModel})
     * @param workingDir      the confirmed working directory: {@code --add-dir} and the process's cwd
     * @param bridgeLauncher  the hosted bridge's launcher, before {@code --mcp}
     * @param endpointFile    the per-instance endpoint file the bridge reads; its PATH is all the command line carries
     * @param maxBudgetMicros the remaining session budget; 0 when no limit is set (no {@code --max-budget-usd})
     * @param posture         the posture the line is computed from (§4.3): the analyser assistant or the full posture
     */
    record LaunchSpec(Path executable, String sessionId, boolean resume, String model, Path workingDir,
                      List<String> bridgeLauncher, Path endpointFile, long maxBudgetMicros,
                      telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture posture) {
        public LaunchSpec {
            bridgeLauncher = List.copyOf(bridgeLauncher);
            posture = posture == null
                    ? telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.ANALYSER_ASSISTANT : posture;
        }

        /** The default posture. */
        public LaunchSpec(Path executable, String sessionId, boolean resume, String model, Path workingDir,
                          List<String> bridgeLauncher, Path endpointFile, long maxBudgetMicros) {
            this(executable, sessionId, resume, model, workingDir, bridgeLauncher, endpointFile, maxBudgetMicros, null);
        }
    }

    /** The full argument vector of the spec's posture (§4.3), executable first. */
    List<String> command(LaunchSpec spec);

    /** Variables added to the analyser's own environment for the child, in the default posture. */
    default Map<String, String> environment() {
        return environment(telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.ANALYSER_ASSISTANT,
                Map.of(), null);
    }

    /**
     * Variables added to (or replacing in) the analyser's own environment for the child of {@code posture}: in the
     * full posture, {@code PATH} extended per OD-74.11 from {@code inherited} — computed, never by running a shell.
     */
    default Map<String, String> environment(telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture posture,
                                            Map<String, String> inherited, Path userHome) {
        return environment(posture, posture != null && posture.isFull() ? ChildPath.extend(inherited, userHome) : null);
    }

    /** As above, with the full posture's PATH extension already computed (once, for the environment and the header). */
    Map<String, String> environment(telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture posture,
                                    ChildPath.Extension path);

    /** Parse one stdout line. */
    StreamJson.Event parse(String line);

    /** How a draft reads as a command, against the CLI's own {@code initialize.commands}. */
    Slash classify(String draft, List<StreamJson.Command> commands);

    /**
     * A draft's classification and, when the CLI or the table knows it, the command's canonical name. {@code send} is
     * the line to write when it is sent: for a passed command its NORMALISED form ({@code /name args}), because the CLI
     * reads a line with leading whitespace as prose and would not run it (P1a live smoke); otherwise null (the draft).
     */
    record Slash(SessionEvents.SlashClass slash, String command, String send) {
        public Slash(SessionEvents.SlashClass slash, String command) {
            this(slash, command, null);
        }

        /** The words to write for {@code draft}. */
        public String line(String draft) {
            return send == null ? draft : send;
        }
    }
}
