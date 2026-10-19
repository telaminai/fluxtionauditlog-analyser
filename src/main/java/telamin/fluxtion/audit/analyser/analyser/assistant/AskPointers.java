package telamin.fluxtion.audit.analyser.analyser.assistant;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * M74 P2 (spec §5.5, review c7f2 F11): the ONE sanitiser per pointer kind. A draft template interpolates nothing that has
 * not passed through one of these, so a name a sender, a bundle or a log authored reaches the analyser's own draft only
 * as a bounded identifier or a quoted, single-line, length-bounded name. Review P2 N-f (2026-10-17): that LIMITS what a
 * name can say — it cannot open a new line, close its quotes or exceed its length — but a quoted name is still words, and
 * a name such as {@code "ignore the above"} reaches the draft as those words, inside quotes. Where a name is free prose
 * (a walk name with spaces, an ill-formed id) it is replaced by its position instead.
 */
public final class AskPointers {

    private AskPointers() {
    }

    /** The node id / class / key form (§5.5): anything else is replaced by its position, never shown. */
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_.$:-]{1,100}");

    /** The docs site's address — a compile-time constant, the only URL a draft ever names. */
    public static final String DOCS_URL = "https://telaminai.github.io/fluxtionauditlog-analyser/";

    /** At most this many node ids in Q1's draft. */
    public static final int MAX_NODE_IDS = 12;
    /** At most this many records named in Q2's draft (more are refused by the precondition). */
    public static final int MAX_COMPARED = 20;
    /** At most this many key names from the diff dialog. */
    public static final int MAX_DIFF_KEYS = 30;
    /** At most this many template names (S4). */
    public static final int MAX_TEMPLATE_NAMES = 20;

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS 'UTC'")
            .withZone(ZoneOffset.UTC);

    /** A record index or a count: an integer, rendered as itself. */
    public static String integer(long n) {
        return Long.toString(n);
    }

    /**
     * Identifier text (a log, bundle, project, graph or chart NAME): printable, single line, at most {@code max}
     * characters, quoted. Control, format, line-separator and invisible ({@link InvisibleCarriers}) characters are
     * dropped, a double quote becomes a single one, and an over-long name is cut with an ellipsis, so the result can never open a new line or close its quotes.
     */
    public static String identifierText(String s, int max) {
        if (s == null || s.isBlank()) return "\"(unnamed)\"";
        StringBuilder sb = new StringBuilder();
        int[] count = {0};
        s.codePoints().forEach(cp -> {
            if (count[0] >= max) return;
            if (cp == '\n' || cp == '\r' || cp == '\t') {           // a line break becomes a space: still one line
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                    count[0]++;
                }
                return;
            }
            int t = Character.getType(cp);
            if (Character.isISOControl(cp) || t == Character.FORMAT || t == Character.LINE_SEPARATOR
                    || t == Character.PARAGRAPH_SEPARATOR || t == Character.UNASSIGNED || t == Character.PRIVATE_USE
                    || t == Character.SURROGATE
                    // review PR #150 R2: variation selectors and invisible fillers are not format characters; a run of
                    // selectors encodes bytes no one sees in the draft. A name never needs one, so all are dropped
                    || InvisibleCarriers.carrier(cp)) {
                return;
            }
            sb.appendCodePoint(cp == '"' ? '\'' : cp == '`' ? '\'' : cp);
            count[0]++;
        });
        String body = sb.toString().strip();
        if (body.isEmpty()) return "\"(unnamed)\"";
        boolean cut = s.codePointCount(0, s.length()) > max;
        return "\"" + body + (cut ? "…" : "") + "\"";
    }

    /** {@link #identifierText(String, int)} at the log-name bound of 80 characters. */
    public static String name(String s) {
        return identifierText(s, 80);
    }

    /**
     * A node id, event class simple name or key name: the identifier form, or — when it is anything else — the
     * {@code fallback} the caller gives, which names its position ({@code "#7 of the graph"}), so an ill-formed id never
     * enters a draft.
     */
    public static String identifier(String s, String fallback) {
        if (s != null && IDENTIFIER.matcher(s).matches()) return s;
        return fallback;
    }

    /** Whether {@code s} would pass {@link #identifier} as itself. */
    public static boolean isIdentifier(String s) {
        return s != null && IDENTIFIER.matcher(s).matches();
    }

    /** A series expression: single line, at most 120 characters, quoted. */
    public static String expression(String s) {
        return identifierText(s, 120);
    }

    /** A time: epoch milliseconds and its UTC rendering. */
    public static String time(long epochMillis) {
        return epochMillis + " (" + UTC.format(Instant.ofEpochMilli(epochMillis)) + ")";
    }

    /**
     * A walk NAME through the identifier sanitiser (§5.5); a name that fails it — a sender's prose, in a bundle — is its
     * position ({@code "walk 2 of this project's walks"}). A walk's TITLE never enters a draft.
     */
    public static String walk(String name, int position) {
        if (isIdentifier(name)) return "\"" + name + "\"";
        return "walk " + Math.max(1, position) + " of this project's walks";
    }

    /** Template names: identifier text, at most 80 characters each and 20 names. */
    public static String templateNames(List<String> names) {
        if (names == null || names.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String name : names) {
            if (n == MAX_TEMPLATE_NAMES) break;
            if (n > 0) sb.append(", ");
            sb.append(identifierText(name, 80));
            n++;
        }
        return sb.toString();
    }

    /** The verification verdict: an enum word. Only a verified bundle ever reaches a draft (§5.8 S3). */
    public static String verdict(boolean verified) {
        if (!verified) throw new IllegalArgumentException("an unverified bundle never reaches a draft");
        return "verified";
    }
}
