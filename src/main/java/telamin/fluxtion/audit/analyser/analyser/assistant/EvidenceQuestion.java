package telamin.fluxtion.audit.analyser.analyser.assistant;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robot re-walk #3 (2026-10-18): whether a start-page question READS as being about evidence — a log, its records, what
 * happened and why — so that asking it with no log open would get a general answer. A word check on the box's words,
 * made where the words are (the frame); only its verdict travels, in {@code StartPagePromptSent}. A question about the
 * tool itself ("What can this tool do?", "How do I install it?") does not read so; when unsure it reads as not, because
 * the hint it brings is an interruption.
 */
public final class EvidenceQuestion {

    private EvidenceQuestion() {
    }

    /** Words that only a question about evidence uses, matched whole, any case. */
    static final Set<String> WORDS = Set.of(
            "happened", "happen", "happens", "happening", "why", "cause", "caused", "causes", "logged",
            "record", "records", "event", "events", "node", "nodes", "error", "errors", "fail", "failed", "fails",
            "failure", "failing", "breach", "breached", "breaches", "exception", "exceptions", "trace", "spike", "alert",
            "warning", "warnings", "crossed", "dropped", "missing", "wrong", "unusual", "anomaly", "incident", "cycle",
            "dispatch", "dispatched", "here", "latency", "timeline", "evidence", "triggered", "value", "values");

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]+");
    /**
     * "Log" alone is the product's own name ("Audit Log Analyser", in the built-in "What can this tool do?") and a
     * general question's ("what does it do with my logs?"); a log the question points AT is evidence.
     */
    private static final Pattern THIS_LOG = Pattern.compile("\\b(this|the|that) (audit )?log\\b");
    private static final Pattern PRODUCT = Pattern.compile("audit log analy[sz]er");

    /** Whether {@code question} reads as being about evidence (blank: no). */
    public static boolean reads(String question) {
        if (question == null || question.isBlank()) return false;
        String q = PRODUCT.matcher(question.toLowerCase(Locale.ROOT)).replaceAll(" ");
        if (THIS_LOG.matcher(q).find()) return true;
        Matcher m = WORD.matcher(q);
        while (m.find()) if (WORDS.contains(m.group())) return true;
        return false;
    }
}
