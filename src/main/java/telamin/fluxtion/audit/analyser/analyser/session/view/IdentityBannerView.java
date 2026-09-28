package telamin.fluxtion.audit.analyser.analyser.session.view;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * View-model spike, second element: WHAT the file-identity banner states — never how any surface words it.
 *
 * <p>Three surfaces draw this today (the table, the charts, the record detail) and each composes its own sentence.
 * They agreed about the WORDS by accident and about the POLICY by dependency: {@code GraphTabs} and
 * {@code DetailPanel} each began by calling {@code LogTablePanel.identityBannerText(...)} and showing nothing when it
 * returned null — two surfaces asking a third whether to draw. {@link #shown} is that decision, made once, by the
 * session.
 *
 * @param generation the log generation this verdict is about
 * @param verdict    the session's file-identity verdict, e.g. {@code VERIFIED}, {@code UNVERIFIED}, {@code REPLACEMENT}
 * @param reason     why, when the session has one, else null
 * @param shown      whether a banner belongs on screen at all — the policy, decided here and not by a surface
 */
public record IdentityBannerView(long generation, String verdict, String reason, boolean shown) {

    /**
     * The verdicts that mean "what you are looking at was not re-read from the file". Kept here rather than in a
     * panel because it is the one place three surfaces used to reach into each other for.
     */
    public static boolean warns(String verdict) {
        return "UNVERIFIED".equals(verdict) || "REPLACEMENT".equals(verdict);
    }

    /**
     * readable-surfaces step 2: the verdict for a log whose store never looks at its file. Without it, "no verdict"
     * meant both "looked, and saw no change" and "never looked", and only a reader holding the store could tell which.
     * It never warns: the screen speaks only of a change, and there is none to speak of.
     */
    public static final String NOT_ASSESSED = "NOT_ASSESSED";

    /**
     * The view of what the session knows about the file. {@code assessed} is whether the store looks at its file at all.
     * There is deliberately no overload without it, because a default would answer "assessed" for a caller that did
     * not say.
     */
    public static IdentityBannerView of(long generation, String verdict, String reason, boolean assessed) {
        if (verdict == null && !assessed) return new IdentityBannerView(generation, NOT_ASSESSED, null, false);
        return new IdentityBannerView(generation, verdict, reason, warns(verdict));
    }

    /** The fields that differ from {@code previous} — what the audit records about a render. */
    public Map<String, Object> changedFrom(IdentityBannerView previous) {
        Map<String, Object> changed = new LinkedHashMap<>();
        Map<String, Object> now = fields();
        Map<String, Object> before = previous == null ? Map.of() : previous.fields();
        now.forEach((k, v) -> {
            if (previous == null || !Objects.equals(v, before.get(k))) changed.put(k, v);
        });
        return changed;
    }

    /**
     * Every field by name — the order is the record's. Nulls are kept, so a cleared field shows as a change.
     *
     * <p>Two readers: the audit's field diff, and {@code context}'s {@code surfaces} section. Both must describe
     * the same view with the same names, or an agent reading the log and an agent reading the context would be
     * comparing different vocabularies for one statement.
     */
    public Map<String, Object> fields() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("generation", generation);
        f.put("verdict", verdict);
        f.put("reason", reason);
        f.put("shown", shown);
        return f;
    }
}
