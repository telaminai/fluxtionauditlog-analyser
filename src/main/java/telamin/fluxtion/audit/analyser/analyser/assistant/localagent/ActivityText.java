package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.session.FactWords;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;

import java.util.Map;
import java.util.function.LongFunction;

/** Adapter-side bounded words and pure substitution of the node's step template. No activity decision lives here. */
public final class ActivityText {
    private ActivityText() { }
    private static final java.util.regex.Pattern ABSOLUTE = java.util.regex.Pattern.compile(
            "(?<![\\w.…])(?:~/|/|[A-Za-z]:[\\\\/]|" + java.util.regex.Pattern.quote("\\\\")
                    + ")[^\\s'\"`,;:()<>\\[\\]{}]*");

    /** Only a name or the first command line; never the Read body, tool output or raw record content. */
    public static String words(String name, String input) {
        Object parsed;
        try { parsed = Json.parse(input == null ? "{}" : input); }
        catch (RuntimeException | StackOverflowError e) { return ""; }
        if (!(parsed instanceof Map<?, ?> fields)) return "";
        String key = "Read".equals(name) ? "file_path" : "Bash".equals(name) ? "command" : null;
        if (key == null || !(fields.get(key) instanceof String value)) return "";
        value = value.lines().findFirst().orElse("");
        if ("Read".equals(name)) {
            String normalized = value.replace('\\', '/');
            int slash = normalized.lastIndexOf('/');
            value = slash < 0 ? normalized : normalized.substring(slash + 1);
        } else {
            var paths = ABSOLUTE.matcher(FactWords.withoutPaths(value));
            StringBuilder redacted = new StringBuilder();
            while (paths.find()) paths.appendReplacement(redacted, java.util.regex.Matcher.quoteReplacement(FactWords.shown(paths.group())));
            paths.appendTail(redacted);
            value = redacted.toString();
        }
        value = value.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").replaceAll("(?U)\\s+", " ").strip();
        if (value.length() > 40) value = value.substring(0, 39) + "…";
        String[] words = value.split("\\s+");
        if (words.length > 8) value = String.join(" ", java.util.Arrays.copyOf(words, 8)) + "…";
        return value;
    }

    /** Both the panel and context use this same substitution, against the same immutable Guide. */
    public static String step(LocalAgentState.Guide guide, LongFunction<String> words) {
        String value = guide.stepWordsRef() == 0 ? "" : words.apply(guide.stepWordsRef());
        if (value == null || LocalAgentTranscript.TRIMMED.equals(value)) value = "…";
        if (value.endsWith("…") && guide.currentStep().contains("{step}…")) value = value.substring(0, value.length() - 1);
        return guide.currentStep().replace("{step}", value);
    }

    public static Map<String, Object> context(LocalAgentState state, LongFunction<String> words) {
        var guide = state.extra().guide();
        return Map.of("phase", state.phase(), "currentStep", state.busy() ? step(guide, words) : "",
                "secondsSinceLastEvent", state.busy() ? guide.secondsSinceLastEvent() : 0,
                "approvalPending", guide.approvalPending());
    }
}
