package telamin.fluxtion.audit.analyser.analyser.walk;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * A recorded spotlight walk, as one self-contained HTML page you can send to somebody who does not have the
 * analyser (issue #82).
 *
 * <p><b>A reel is a claim; the finish page is what makes it checkable.</b> Frames are pixels, and pixels can be
 * cropped, staged or simply wrong — a viewer cannot tell. So every reel ends by naming the evidence behind it:
 * the bundle's {@code sha256:} identity, its file name, the limits the bundle itself states, and the sentence
 * that answers "how do I know?" — open it yourself. A walk that was NOT captured from a bundle says so plainly
 * on the same page, rather than leaving the question open: an unanswered question reads as a claim.
 *
 * <p>That is also why the page, not an animation, is the primary output. An identity is worth nothing baked into
 * a frame as pixels; it has to be selectable text beside a real link.
 *
 * <p>This class is pure: records in, one HTML string out. It never reads the session, a file or the clock, so the
 * page a test asserts on is exactly the page a person receives.
 */
public final class WalkReel {

    private WalkReel() {
    }

    /** Said on the finish page when the walk was not captured from an evidence bundle. Never omitted, never softened. */
    public static final String NO_BUNDLE =
            "This reel was not captured from an evidence bundle. There is no bundle to open and no identity to "
            + "check: these frames are a picture of one session of the analyser, and nothing here lets you verify "
            + "them against the log they claim to show. To send evidence somebody can check, capture the "
            + "investigation as a .fexp bundle first and record the reel from that.";

    /** How the sender's own note is labelled: their words are never allowed to read as a fact about the evidence. */
    public static final String SENDER_WORDS = "The sender's words, not a fact about the evidence.";

    /** The invitation the finish page makes when there IS a bundle — the honest answer to "how do I know?". */
    public static final String OPEN_IT_YOURSELF =
            "Open this bundle in the Fluxtion Audit Log Analyser to replay these steps yourself against the real log.";

    /** What is being shown: the log the frames came from, named the way the window names it — by file, never by path. */
    public record Log(String name, int records, String span, String processor) {
        public Log {
            name = name == null || name.isBlank() ? "" : name.trim();
            span = span == null ? "" : span.trim();
            processor = processor == null ? "" : processor.trim();
        }

        public boolean known() {
            return !name.isEmpty();
        }
    }

    /**
     * The bundle this walk was captured from, or null when there was none.
     *
     * @param identity the bundle's {@code sha256:…} — the fact verification produced
     * @param fileName the {@code .fexp}'s own file name, never the path it happened to sit at
     * @param limits   what the bundle itself states it does and does not evidence, one per line
     * @param notes    what the SENDER wrote. Their words, not a fact about the evidence, and labelled as such.
     */
    public record Evidence(String identity, String fileName, List<String> limits, String notes) {
        public Evidence {
            identity = identity == null ? "" : identity.trim();
            fileName = fileName == null ? "" : fileName.trim();
            limits = List.copyOf(limits == null ? List.of() : limits);
            notes = notes == null ? "" : notes.trim();
        }
    }

    /**
     * One captured step.
     *
     * @param state  the phase the session had settled on: {@code SHOWN}, {@code PARTLY_SHOWN} or {@code NOT_SHOWN}.
     *               It is carried onto the page rather than hidden — a step the analyser could not fully show is
     *               part of what the recipient is owed.
     * @param dialogue the turns this step revealed, in order; empty when the walk carries no dialogue
     */
    public record Frame(int number, String caption, List<String> targets, String state, String reason,
                        int width, int height, List<WalkSpec.Turn> dialogue, byte[] png) {
        public Frame {
            caption = caption == null ? "" : caption.trim();
            targets = List.copyOf(targets == null ? List.of() : targets);
            state = state == null ? "" : state.trim();
            reason = reason == null ? "" : reason.trim();
            dialogue = List.copyOf(dialogue == null ? List.of() : dialogue);
            png = png == null ? new byte[0] : png.clone();
        }

        @Override
        public byte[] png() {
            return png.clone();
        }

        /** How the page names the state; "" when there is nothing a reader needs told. */
        public String stateLabel() {
            return switch (state) {
                case "SHOWN" -> "";
                case "PARTLY_SHOWN" -> "Partly shown";
                case "NOT_SHOWN" -> "Not shown";
                default -> state;
            };
        }
    }

    /**
     * The whole reel.
     *
     * @param purpose      the walk's one-line purpose, or "" — never invented when the walk did not state one
     * @param walkSaved    when the walk itself was saved
     * @param recordedAt   when these frames were captured
     * @param dialogueLabel how the walk's dialogue is labelled ({@code Simulated conversation}, …), or ""
     */
    public record Reel(String walkName, String purpose, String author, String walkSaved, String recordedAt,
                       String dialogueLabel, Log log, Evidence evidence, List<Frame> frames) {
        public Reel {
            walkName = walkName == null ? "" : walkName.trim();
            purpose = purpose == null ? "" : purpose.trim();
            author = author == null ? "" : author.trim();
            walkSaved = walkSaved == null ? "" : walkSaved.trim();
            recordedAt = recordedAt == null ? "" : recordedAt.trim();
            dialogueLabel = dialogueLabel == null ? "" : dialogueLabel.trim();
            frames = List.copyOf(frames == null ? List.of() : frames);
        }

        public boolean fromBundle() {
            return evidence != null && !evidence.identity().isEmpty();
        }
    }

    // ---- the page ---------------------------------------------------------------------------------------------

    /** The whole reel as one HTML document: no scripts, no network, every frame embedded. */
    public static String html(Reel reel) {
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(esc(reel.walkName())).append(" — spotlight walk</title>\n")
                .append("<style>\n").append(CSS).append("</style>\n</head>\n<body>\n");
        titlePage(out, reel);
        for (Frame f : reel.frames()) framePage(out, reel, f);
        finishPage(out, reel);
        out.append("</body>\n</html>\n");
        return out.toString();
    }

    /** The bytes written to disk. */
    public static byte[] bytes(Reel reel) {
        return html(reel).getBytes(StandardCharsets.UTF_8);
    }

    private static void titlePage(StringBuilder out, Reel reel) {
        out.append("<section class=\"page title\">\n");
        out.append("<p class=\"kicker\">Spotlight walk</p>\n");
        out.append("<h1>").append(esc(reel.walkName())).append("</h1>\n");
        if (!reel.purpose().isEmpty()) {
            out.append("<p class=\"purpose\">").append(esc(reel.purpose())).append("</p>\n");
        } else {
            out.append("<p class=\"purpose muted\">This walk records no one-line purpose.</p>\n");
        }
        out.append("<h2>What you are looking at</h2>\n<dl>\n");
        if (reel.log().known()) {
            row(out, "Audit log", reel.log().name());
            row(out, "Records", String.format("%,d", reel.log().records()));
            row(out, "Time span", reel.log().span().isEmpty() ? "no timestamps" : reel.log().span());
        } else {
            row(out, "Audit log", "none was open when these frames were recorded");
        }
        row(out, "Event processor", reel.log().processor().isEmpty()
                ? "not declared for this session" : reel.log().processor());
        row(out, "Steps", reel.frames().size() + (reel.frames().size() == 1 ? " step" : " steps"));
        if (!reel.author().isEmpty()) row(out, "Walk", reel.author());
        if (!reel.walkSaved().isEmpty()) row(out, "Walk saved", reel.walkSaved());
        row(out, "Reel recorded", reel.recordedAt());
        if (!reel.dialogueLabel().isEmpty()) row(out, "Dialogue", reel.dialogueLabel());
        out.append("</dl>\n");
        out.append("<p class=\"note\">Every frame below is the analyser's own window, painted as it stood at that "
                   + "step. The evidence behind them is named at the end of this page.</p>\n");
        out.append("</section>\n");
    }

    private static void framePage(StringBuilder out, Reel reel, Frame f) {
        out.append("<section class=\"page step\">\n");
        out.append("<p class=\"kicker\">Step ").append(f.number()).append(" of ").append(reel.frames().size());
        String state = f.stateLabel();
        if (!state.isEmpty()) out.append(" <span class=\"warn\">· ").append(esc(state)).append("</span>");
        out.append("</p>\n");
        if (!f.caption().isEmpty()) out.append("<h2>").append(esc(f.caption())).append("</h2>\n");
        if (f.png().length > 0) {
            out.append("<img alt=\"Step ").append(f.number()).append(": ")
                    .append(esc(f.caption().isEmpty() ? "the analyser at this step" : f.caption()))
                    .append("\" width=\"").append(f.width()).append("\" height=\"").append(f.height())
                    .append("\" src=\"data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(f.png())).append("\">\n");
        }
        if (!f.targets().isEmpty()) {
            out.append("<ol class=\"targets\">\n");
            for (String c : f.targets()) out.append("<li>").append(esc(c)).append("</li>\n");
            out.append("</ol>\n");
        }
        if (!f.reason().isEmpty()) {
            out.append("<p class=\"warn\">").append(esc(f.reason())).append("</p>\n");
        }
        if (!f.dialogue().isEmpty()) {
            out.append("<div class=\"dialogue\">\n");
            if (!reel.dialogueLabel().isEmpty()) {
                out.append("<p class=\"kicker\">").append(esc(reel.dialogueLabel())).append("</p>\n");
            }
            for (WalkSpec.Turn t : f.dialogue()) {
                out.append("<p class=\"turn ").append("assistant".equals(t.role()) ? "assistant" : "user")
                        .append("\"><span class=\"role\">").append(esc(t.role())).append("</span> ")
                        .append(esc(t.text())).append("</p>\n");
            }
            out.append("</div>\n");
        }
        out.append("</section>\n");
    }

    private static void finishPage(StringBuilder out, Reel reel) {
        out.append("<section class=\"page finish\">\n");
        if (reel.fromBundle()) {
            Evidence e = reel.evidence();
            out.append("<h2>The evidence behind this reel</h2>\n<dl>\n");
            if (!e.fileName().isEmpty()) {
                // relative, by file name: a reel travels beside its bundle, and an absolute path would be both
                // unusable on the recipient's machine and a disclosure nobody asked for
                out.append("<dt>Bundle</dt><dd><a href=\"").append(escAttr(e.fileName())).append("\"><code>")
                        .append(esc(e.fileName())).append("</code></a></dd>\n");
            }
            out.append("<dt>Identity</dt><dd><code class=\"identity\">").append(esc(e.identity()))
                    .append("</code></dd>\n</dl>\n");
            out.append("<p class=\"invite\">").append(esc(OPEN_IT_YOURSELF)).append("</p>\n");
            if (!e.limits().isEmpty()) {
                out.append("<h3>What this bundle does and does not evidence</h3>\n<ul class=\"limits\">\n");
                for (String l : e.limits()) out.append("<li>").append(esc(l)).append("</li>\n");
                out.append("</ul>\n");
            }
            if (!e.notes().isEmpty()) {
                out.append("<h3>From the sender</h3>\n<blockquote>").append(esc(e.notes()))
                        .append("</blockquote>\n<p class=\"muted\">").append(esc(SENDER_WORDS)).append("</p>\n");
            }
        } else {
            out.append("<h2>No evidence bundle</h2>\n<p class=\"warn nobundle\">").append(esc(NO_BUNDLE))
                    .append("</p>\n");
        }
        out.append("<p class=\"muted\">Recorded by the Fluxtion Audit Log Analyser.</p>\n");
        out.append("</section>\n");
    }

    private static void row(StringBuilder out, String term, String value) {
        out.append("<dt>").append(esc(term)).append("</dt><dd>").append(esc(value)).append("</dd>\n");
    }

    /** Escape for element text. {@code '} is escaped too, so the same function is safe in a quoted attribute. */
    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    private static String escAttr(String s) {
        return esc(s);
    }

    private static final String CSS = """
            :root { color-scheme: light dark;
              --bg:#ffffff; --fg:#181a1c; --muted:#5d6570; --rule:#d9dde2; --card:#f6f7f9; --warn:#8a4b00;
              --accent:#1a4f7a; }
            @media (prefers-color-scheme: dark) {
              :root { --bg:#14171a; --fg:#e7eaee; --muted:#9aa3ad; --rule:#2b3137; --card:#1c2126; --warn:#e0a35a;
                --accent:#7fb6e0; }
            }
            html { background: var(--bg); }
            body { margin:0; background:var(--bg); color:var(--fg); font:16px/1.55 -apple-system, BlinkMacSystemFont,
              "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
            .page { max-width:1000px; margin:0 auto; padding:40px 16px; border-bottom:1px solid var(--rule); }
            .page:last-child { border-bottom:none; }
            h1 { font-size:2rem; margin:.2em 0 .3em; }
            h2 { font-size:1.2rem; margin:1.4em 0 .5em; }
            h3 { font-size:1rem; margin:1.4em 0 .4em; }
            .kicker { text-transform:uppercase; letter-spacing:.08em; font-size:.78rem; color:var(--muted);
              margin:0 0 .3em; }
            .purpose { font-size:1.12rem; margin:0 0 1.2em; }
            .muted { color:var(--muted); }
            .warn { color:var(--warn); }
            .note { color:var(--muted); font-size:.92rem; }
            dl { display:grid; grid-template-columns:max-content 1fr; gap:.3em 1.2em; margin:0; }
            dt { color:var(--muted); }
            dd { margin:0; }
            img { display:block; max-width:100%; height:auto; margin:.6em 0 1em;
              border:1px solid var(--rule); border-radius:6px; }
            ol.targets { margin:.2em 0 1em; padding-left:1.4em; }
            ol.targets li { margin:.2em 0; }
            .dialogue { background:var(--card); border:1px solid var(--rule); border-radius:8px;
              padding:.9em 1.1em; margin:1em 0; }
            .turn { margin:.45em 0; }
            .turn .role { display:inline-block; min-width:5.5em; color:var(--muted); font-size:.82rem;
              text-transform:uppercase; letter-spacing:.06em; }
            .finish { background:var(--card); }
            code { font:13px/1.5 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; word-break:break-all; }
            .identity { font-size:14px; }
            .invite { font-size:1.05rem; }
            a { color:var(--accent); }
            blockquote { margin:.4em 0; padding-left:1em; border-left:3px solid var(--rule); }
            ul.limits { margin:.2em 0 1em; padding-left:1.3em; }
            .nobundle { max-width:62ch; }
            @media print { .page { break-after:page; border-bottom:none; } }
            """;
}
