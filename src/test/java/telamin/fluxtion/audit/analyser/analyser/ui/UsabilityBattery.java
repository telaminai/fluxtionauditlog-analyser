package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The usability scorecard's runner: {@code tools/usability/run.py} starts it against the built jar, under an isolated
 * {@code user.home}, from the repository root (the recorded fixtures are read from there).
 *
 * <pre>
 *   UsabilityBattery &lt;out-dir&gt; &lt;label&gt; [work-dir]
 * </pre>
 * writes {@code usability-<label>.json} (one result per task and per key state) and {@code usability-<label>.md} (the
 * table, with the enforced budgets and the targets beside the measures). Every path a string carries is redacted:
 * no machine path reaches the record.
 */
public final class UsabilityBattery {

    private UsabilityBattery() {
    }

    /** Everything one run measured. */
    record Results(List<UsabilityTasks.StateMeasure> states, List<Usability.Run> runs) {
    }

    static Results runAll(Path work) throws Exception {
        List<UsabilityTasks.StateMeasure> states = new ArrayList<>();
        states.add(UsabilityTasks.startPage(work.resolve("S1")));
        states.add(UsabilityTasks.assistantReady(work.resolve("S2")));
        states.add(UsabilityTasks.recordsWithList(work.resolve("S3")));
        List<Usability.Run> runs = new ArrayList<>();
        for (UsabilityTasks.Task t : UsabilityTasks.tasks()) runs.add(UsabilityTasks.run(t, work.resolve(t.id())));
        return new Results(states, runs);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: UsabilityBattery <out-dir> <label> [work-dir]");
            System.exit(2);
        }
        Path out = Files.createDirectories(Path.of(args[0]));
        String label = args[1];
        Path work = args.length > 2 ? Path.of(args[2]) : Files.createTempDirectory("usability");
        int code = 0;
        try {
            Results r = runAll(work);
            Files.writeString(out.resolve("usability-" + label + ".json"), redact(json(r, label)) + "\n");
            Files.writeString(out.resolve("usability-" + label + ".md"), redact(markdown(r, label)));
            for (Usability.Run run : r.runs()) if (!run.success) code = 1;
            System.out.println(redact(markdown(r, label)));
        } catch (Throwable t) {
            t.printStackTrace();
            code = 2;
        } finally {
            System.exit(code);       // the frames' timers and the REST server would keep the JVM alive
        }
    }

    // ---- the record -------------------------------------------------------------------------------------------------------

    static String json(Results r, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scorecard", "usability");
        m.put("label", label);
        m.put("window", UsabilityTasks.WIDTH + "x" + UsabilityTasks.HEIGHT);
        List<Object> states = new ArrayList<>();
        for (var s : r.states()) states.add(s.toMap());
        m.put("states", states);
        List<Object> tasks = new ArrayList<>();
        for (var t : r.runs()) tasks.add(t.toMap());
        m.put("tasks", tasks);
        return pretty(Json.write(m));
    }

    static String markdown(Results r, String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Usability scorecard: ").append(label).append("\n\n");
        sb.append("Window ").append(UsabilityTasks.WIDTH).append("×").append(UsabilityTasks.HEIGHT)
                .append(", docked, Light theme; stub `claude`, recorded Claude Code streams; isolated `user.home`.\n\n");
        sb.append("## Tasks\n\n");
        sb.append("| Task | Success | Acts (click/key/drag/scroll) | Hesitations | Dead ends | Words before first act | Clipped | Acts budget | Target |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Usability.Run t : r.runs()) {
            sb.append("| ").append(t.id).append(" ").append(t.name).append(" | ").append(t.success ? "yes" : "**no**")
                    .append(" | ").append(t.totalActs()).append(" (")
                    .append(t.acts.get(Usability.ActKind.CLICK)).append('/').append(t.acts.get(Usability.ActKind.KEY)).append('/')
                    .append(t.acts.get(Usability.ActKind.DRAG)).append('/').append(t.acts.get(Usability.ActKind.SCROLL)).append(")")
                    .append(" | ").append(t.hesitations()).append(" | ").append(t.deadEnds())
                    .append(" | ").append(t.wordsBeforeFirstAct).append(" | ").append(t.clippedSeen.size())
                    .append(" | ≤ ").append(UsabilityBudgets.ACTS.getOrDefault(t.id, -1))
                    .append(" | ").append(UsabilityBudgets.ACT_TARGETS.getOrDefault(t.id, "—")).append(" |\n");
        }
        sb.append("\n## Key states\n\n");
        sb.append("| State | Region | Chrome words | Controls | Clipped | Whole-window words | Words budget | Clipped budget | Target |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|\n");
        for (var s : r.states()) {
            sb.append("| ").append(s.id()).append(" ").append(s.name()).append(" | ").append(s.region()).append(" | ")
                    .append(s.words()).append(" | ").append(s.controls()).append(" | ").append(s.clipped().size())
                    .append(" | ").append(s.windowWords()).append(" | ≤ ").append(UsabilityBudgets.WORDS.getOrDefault(s.id(), -1))
                    .append(" | ≤ ").append(UsabilityBudgets.CLIPPED.getOrDefault(s.id(), -1))
                    .append(" | ").append(UsabilityBudgets.WORD_TARGETS.getOrDefault(s.id(), "—")).append(" |\n");
        }
        sb.append("\n## Steps\n\n");
        for (Usability.Run t : r.runs()) {
            sb.append("**").append(t.id).append(" ").append(t.name).append("** — key words: ").append(String.join(", ", t.keyWords));
            if (t.failure != null) sb.append(" — failed: ").append(t.failure);
            sb.append("\n\n| # | Act | What | Visible label | Hesitation | Dead end |\n|---|---|---|---|---|---|\n");
            for (var st : t.steps) {
                sb.append("| ").append(st.n()).append(" | ").append(st.kind() == null ? "—" : st.kind().name().toLowerCase())
                        .append(" | ").append(st.what()).append(" | ").append(cell(st.label())).append(" | ")
                        .append(st.reasons().isEmpty() ? "—" : String.join(", ", st.reasons().stream()
                                .map(x -> x.name().toLowerCase().replace('_', ' ')).toList()))
                        .append(" | ").append(st.deadEnd() ? "yes" : "—").append(" |\n");
            }
            if (!t.clippedSeen.isEmpty()) {
                sb.append("\nClipped on the way:\n\n");
                for (String c : t.clippedSeen) sb.append("- ").append(cell(c)).append('\n');
            }
            sb.append('\n');
        }
        for (var s : r.states()) {
            if (s.clipped().isEmpty()) continue;
            sb.append("Clipped in ").append(s.id()).append(":\n\n");
            for (String c : s.clipped()) sb.append("- ").append(cell(c)).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    /** Machine paths out of every string: the repository, the homes, the JDK, the temp directory, any home directory. */
    static String redact(String text) {
        String t = text;
        for (String p : new String[]{Path.of("").toAbsolutePath().toString(), System.getProperty("java.home"),
                System.getProperty("java.io.tmpdir"), System.getProperty("user.home")}) {
            if (p != null && p.length() > 4) t = t.replace(p.endsWith("/") ? p.substring(0, p.length() - 1) : p, "<path>");
        }
        t = t.replaceAll("/(Users|home)/[^/\\s\"'<>]+", "~");
        return t;
    }

    /** Two-space JSON from the compact writer's output. */
    static String pretty(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean str = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (str) {
                sb.append(c);
                if (c == '\\') { sb.append(json.charAt(++i)); continue; }
                if (c == '"') str = false;
                continue;
            }
            switch (c) {
                case '"' -> { str = true; sb.append(c); }
                case '{', '[' -> {
                    char close = c == '{' ? '}' : ']';
                    if (i + 1 < json.length() && json.charAt(i + 1) == close) { sb.append(c).append(close); i++; break; }
                    sb.append(c).append('\n').append("  ".repeat(++indent));
                }
                case '}', ']' -> sb.append('\n').append("  ".repeat(--indent)).append(c);
                case ',' -> sb.append(c).append('\n').append("  ".repeat(indent));
                case ':' -> sb.append(": ");
                default -> { if (!Character.isWhitespace(c)) sb.append(c); }
            }
        }
        return sb.toString();
    }
}
