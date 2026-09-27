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

    public static IdentityBannerView of(long generation, String verdict, String reason) {
        return new IdentityBannerView(generation, verdict, reason, warns(verdict));
    }

    /** The fields that differ from {@code previous} — what the audit records about a render. */
    public Map<String, Object> changedFrom(IdentityBannerView previous) {
        Map<String, Object> changed = new LinkedHashMap<>();
        if (previous == null || previous.generation != generation) changed.put("generation", generation);
        if (previous == null || !Objects.equals(previous.verdict, verdict)) changed.put("verdict", verdict);
        if (previous == null || !Objects.equals(previous.reason, reason)) changed.put("reason", reason);
        if (previous == null || previous.shown != shown) changed.put("shown", shown);
        return changed;
    }
}
