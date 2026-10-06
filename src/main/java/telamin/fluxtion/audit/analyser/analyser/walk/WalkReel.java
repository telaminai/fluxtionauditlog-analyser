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

    /**
     * Said INSTEAD of {@link #OPEN_IT_YOURSELF} when a bundle is in force but the frames show a log it does not
     * carry. The invitation would otherwise be false in the one way that survives checking: the recipient opens a
     * genuine bundle, verification passes, and the records they get are not the records in the pictures.
     */
    public static final String BUNDLE_IS_NOT_THESE_RECORDS =
            "This bundle is NOT the evidence for the frames above. It supplied the project — its charts, walks and "
            + "reports — but the log in these pictures is not one of its records, so opening it will not reproduce "
            + "these steps. Verification of the bundle will succeed and still tell you nothing about them.";

    /**
     * Write {@code bytes} to {@code out}, or leave nothing at {@code out} at all.
     *
     * <p>HTML has no integrity check. A reel written straight to its destination and interrupted part-way — a full
     * volume, an I/O error, an unmounted home — renders in a browser as a reel that simply stops, and what it stops
     * before is the finish page: the one that names the evidence, or says there is none. A truncated reel is an
     * undisclosed claim, so it must never exist under the name of a finished one. Written beside the target and
     * moved into place; the partial is removed if the write fails.</p>
     *
     * @throws java.io.IOException if the reel could not be written; {@code out} is then untouched
     */
    public static void write(java.nio.file.Path out, byte[] bytes) throws java.io.IOException {
        java.nio.file.Path part = out.resolveSibling(out.getFileName() + ".part");
        try {
            if (out.getParent() != null) java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.write(part, bytes);
            try {
                java.nio.file.Files.move(part, out, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException acrossStores) {
                java.nio.file.Files.move(part, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (java.io.IOException failed) {
            try {
                java.nio.file.Files.deleteIfExists(part);
            } catch (java.io.IOException ignored) {
                // could not remove the partial; it is named .part and was never presented as a reel
            }
            throw failed;
        }
    }

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
     * @param logRelation empty when the frames show a log this bundle carries; otherwise WHY the bundle is not the
     *                 evidence for them. A bundle's provenance is about the PROJECT, not whichever log is on screen
     *                 (see {@code BundleProvenanceTest#theClaimIsAboutTheProjectNotTheLogOnScreen}), so a reel can
     *                 be recorded with a bundle in force while showing a log the bundle never contained. Naming the
     *                 bundle then is an affirmative false claim that the recipient's own --verify would confirm,
     *                 because the bundle is genuine. The page says which it is describing.
     */
    public record Evidence(String identity, String fileName, List<String> limits, String notes, String logRelation) {
        public Evidence {
            identity = identity == null ? "" : identity.trim();
            fileName = fileName == null ? "" : fileName.trim();
            limits = List.copyOf(limits == null ? List.of() : limits);
            notes = notes == null ? "" : notes.trim();
            logRelation = logRelation == null ? "" : logRelation.trim();
        }

        /** The frames' log is one this bundle carries, so the invitation to replay them against it is true. */
        public boolean coversTheseFrames() {
            return logRelation.isEmpty();
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

    /**
     * Said on the title page, before anything else a sender might act on (#113). The page's TEXT goes through the
     * evidence bundle's path redaction; the frames are pixels and cannot, so the page says which is which.
     */
    public static final String FRAMES_NOT_REDACTED =
            "Machine paths in this page's text are replaced with "
            + telamin.fluxtion.audit.analyser.bundle.BundleProfile.REDACTED
            + ". The frame images are screenshots of the analyser's window and are NOT redacted: they may show "
            + "local file paths.";

    /**
     * The page, and every machine path its text had removed.
     *
     * @param redacted one line per distinct removal, {@code "<where>: <path>"} — what the sender is told was taken
     *                 out (a field the page writes twice, such as a caption that is also the image's alt text, is
     *                 named once)
     */
    public record Rendered(String html, List<String> redacted) {
        public Rendered {
            redacted = List.copyOf(new java.util.LinkedHashSet<>(redacted));
        }

        public byte[] bytes() {
            return html.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * The whole reel as one HTML document: no scripts, no network, every frame embedded, and no machine path in its
     * text.
     *
     * <p><b>A reel is sent, so its text leaves the machine (#113).</b> Every string written into the page — the
     * title-page fields, each step's caption, callouts, reason and dialogue, and the finish page — goes through
     * {@link telamin.fluxtion.audit.analyser.bundle.BundleProfile#redactProse}, the one rule an evidence bundle uses.
     * There is no other way onto the page: {@link Page#text} is the only writer of words.</p>
     *
     * @throws java.io.IOException when a path's ending is ambiguous and the bundle rule refuses to guess where it
     *                             stops; no page is produced, exactly as no bundle would be
     */
    public static Rendered render(Reel reel) throws java.io.IOException {
        Page p = new Page();
        try {
            p.raw("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                    .raw("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                    .raw("<title>").text("walk name", reel.walkName()).raw(" — spotlight walk</title>\n")
                    .raw("<style>\n").raw(CSS).raw("</style>\n</head>\n<body>\n");
            titlePage(p, reel);
            for (Frame f : reel.frames()) framePage(p, reel, f);
            finishPage(p, reel);
            p.raw("</body>\n</html>\n");
        } catch (java.io.UncheckedIOException refused) {
            throw refused.getCause();
        }
        return new Rendered(p.out.toString(), p.redacted);
    }

    /** {@link #render}'s page, for a caller that only wants the HTML; a refusal is unchecked here. */
    public static String html(Reel reel) {
        try {
            return render(reel).html();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The bytes written to disk. */
    public static byte[] bytes(Reel reel) throws java.io.IOException {
        return render(reel).bytes();
    }

    /**
     * The page being written. Markup goes in through {@link #raw}; WORDS go in through {@link #text}, which redacts
     * then escapes, and nothing else does both. A new field on the page cannot reach it unredacted without
     * appending through {@code raw}, which is for constants and markup.
     */
    private static final class Page {
        final StringBuilder out = new StringBuilder(64 * 1024);
        final List<String> redacted = new java.util.ArrayList<>();

        Page raw(String markup) {
            out.append(markup);
            return this;
        }

        Page text(String where, String words) {
            String clean;
            try {
                clean = telamin.fluxtion.audit.analyser.bundle.BundleProfile.redactProse(where, words, redacted);
            } catch (java.io.IOException ambiguous) {
                throw new java.io.UncheckedIOException(ambiguous);
            }
            out.append(esc(clean));
            return this;
        }

        Page number(int n) {
            out.append(n);
            return this;
        }
    }

    private static void titlePage(Page p, Reel reel) {
        p.raw("<section class=\"page title\">\n");
        p.raw("<p class=\"kicker\">Spotlight walk</p>\n");
        p.raw("<h1>").text("walk name", reel.walkName()).raw("</h1>\n");
        if (!reel.purpose().isEmpty()) {
            p.raw("<p class=\"purpose\">").text("purpose", reel.purpose()).raw("</p>\n");
        } else {
            p.raw("<p class=\"purpose muted\">This walk records no one-line purpose.</p>\n");
        }
        p.raw("<h2>What you are looking at</h2>\n<dl>\n");
        if (reel.log().known()) {
            row(p, "Audit log", reel.log().name());
            row(p, "Records", String.format("%,d", reel.log().records()));
            row(p, "Time span", reel.log().span().isEmpty() ? "no timestamps" : reel.log().span());
        } else {
            row(p, "Audit log", "none was open when these frames were recorded");
        }
        row(p, "Event processor", reel.log().processor().isEmpty()
                ? "not declared for this session" : reel.log().processor());
        row(p, "Steps", reel.frames().size() + (reel.frames().size() == 1 ? " step" : " steps"));
        if (!reel.author().isEmpty()) row(p, "Walk", reel.author());
        if (!reel.walkSaved().isEmpty()) row(p, "Walk saved", reel.walkSaved());
        row(p, "Reel recorded", reel.recordedAt());
        if (!reel.dialogueLabel().isEmpty()) row(p, "Dialogue", reel.dialogueLabel());
        p.raw("</dl>\n");
        p.raw("<p class=\"note\">Every frame below is the analyser's own window, painted as it stood at that "
              + "step. The evidence behind them is named at the end of this page.</p>\n");
        p.raw("<p class=\"note warn unredacted\">").text("title page", FRAMES_NOT_REDACTED).raw("</p>\n");
        p.raw("</section>\n");
    }

    private static void framePage(Page p, Reel reel, Frame f) {
        String step = "step " + f.number();
        p.raw("<section class=\"page step\">\n");
        p.raw("<p class=\"kicker\">Step ").number(f.number()).raw(" of ").number(reel.frames().size());
        String state = f.stateLabel();
        if (!state.isEmpty()) p.raw(" <span class=\"warn\">· ").text(step + " state", state).raw("</span>");
        p.raw("</p>\n");
        if (!f.caption().isEmpty()) p.raw("<h2>").text(step + " caption", f.caption()).raw("</h2>\n");
        if (f.png().length > 0) {
            p.raw("<img alt=\"Step ").number(f.number()).raw(": ")
                    .text(step + " caption", f.caption().isEmpty() ? "the analyser at this step" : f.caption())
                    .raw("\" width=\"").number(f.width()).raw("\" height=\"").number(f.height())
                    .raw("\" src=\"data:image/png;base64,")
                    .raw(Base64.getEncoder().encodeToString(f.png())).raw("\">\n");
        }
        if (!f.targets().isEmpty()) {
            p.raw("<ol class=\"targets\">\n");
            int n = 1;
            for (String c : f.targets()) p.raw("<li>").text(step + " callout " + n++, c).raw("</li>\n");
            p.raw("</ol>\n");
        }
        if (!f.reason().isEmpty()) {
            p.raw("<p class=\"warn\">").text(step + " reason", f.reason()).raw("</p>\n");
        }
        if (!f.dialogue().isEmpty()) {
            p.raw("<div class=\"dialogue\">\n");
            if (!reel.dialogueLabel().isEmpty()) {
                p.raw("<p class=\"kicker\">").text("dialogue label", reel.dialogueLabel()).raw("</p>\n");
            }
            int n = 1;
            for (WalkSpec.Turn t : f.dialogue()) {
                p.raw("<p class=\"turn ").raw("assistant".equals(t.role()) ? "assistant" : "user")
                        .raw("\"><span class=\"role\">").text(step + " turn " + n + " role", t.role()).raw("</span> ")
                        .text(step + " turn " + n++, t.text()).raw("</p>\n");
            }
            p.raw("</div>\n");
        }
        p.raw("</section>\n");
    }

    private static void finishPage(Page p, Reel reel) {
        p.raw("<section class=\"page finish\">\n");
        if (reel.fromBundle()) {
            Evidence e = reel.evidence();
            p.raw("<h2>The evidence behind this reel</h2>\n<dl>\n");
            if (!e.fileName().isEmpty()) {
                // relative, by file name: a reel travels beside its bundle, and an absolute path would be both
                // unusable on the recipient's machine and a disclosure nobody asked for
                p.raw("<dt>Bundle</dt><dd><a href=\"").text("bundle file name", e.fileName()).raw("\"><code>")
                        .text("bundle file name", e.fileName()).raw("</code></a></dd>\n");
            }
            p.raw("<dt>Identity</dt><dd><code class=\"identity\">").text("bundle identity", e.identity())
                    .raw("</code></dd>\n</dl>\n");
            // the invitation is only true when this bundle carries the records in the pictures; when it does not,
            // the page says so INSTEAD, never both
            if (e.coversTheseFrames()) {
                p.raw("<p class=\"invite\">").text("finish page", OPEN_IT_YOURSELF).raw("</p>\n");
            } else {
                p.raw("<p class=\"warn notinbundle\">").text("finish page", BUNDLE_IS_NOT_THESE_RECORDS)
                        .raw("</p>\n<p class=\"muted\">").text("log relation", e.logRelation()).raw("</p>\n");
            }
            if (!e.limits().isEmpty()) {
                p.raw("<h3>What this bundle does and does not evidence</h3>\n<ul class=\"limits\">\n");
                for (String l : e.limits()) p.raw("<li>").text("bundle limits", l).raw("</li>\n");
                p.raw("</ul>\n");
            }
            if (!e.notes().isEmpty()) {
                p.raw("<h3>From the sender</h3>\n<blockquote>").text("sender's note", e.notes())
                        .raw("</blockquote>\n<p class=\"muted\">").text("finish page", SENDER_WORDS).raw("</p>\n");
            }
        } else {
            p.raw("<h2>No evidence bundle</h2>\n<p class=\"warn nobundle\">").text("finish page", NO_BUNDLE)
                    .raw("</p>\n");
        }
        p.raw("<p class=\"muted\">Recorded by the Fluxtion Audit Log Analyser.</p>\n");
        p.raw("</section>\n");
    }

    private static void row(Page p, String term, String value) {
        p.raw("<dt>").text("title page", term).raw("</dt><dd>").text(term.toLowerCase(java.util.Locale.ROOT), value)
                .raw("</dd>\n");
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
