package telamin.fluxtion.audit.analyser.analyser.assistant;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M74 P2 (spec §5.8 "A dropped prompt pack", owner request 2026-10-17): a Markdown file the person drops on the start page
 * to replace its numbered list, for this session only. PURE: bytes in, a pack or ONE named refusal out. The grammar is
 * strict — anything it does not name is refused, never guessed:
 *
 * <pre>
 *   pack        := title-line [description-line] entry-line{1..9}      (blank lines may separate any two lines)
 *   title-line  := "# " title                                           (title: 1–60 characters)
 *   description := one line that is not an entry                         (1–140 characters)
 *   entry-line  := n ". **" label "** — " text                           (n = 1, 2, … in order; label 1–60; text 1–500)
 *                | n ". **" label "** — example: " url [" sha256:" hex64] (P2b: a downloadable example, ExampleSource)
 * </pre>
 *
 * <p>An <b>example</b> entry (owner request 2026-10-17) names an archive of a PINNED commit on an allowed host
 * ({@link ExampleSource}); the URL may be written bare or in angle brackets. It is the only place a pack may hold an
 * address, and that line must be exactly this shape. Nothing is fetched when the pack is dropped or the entry chosen.
 *
 * The file is at most {@value #MAX_BYTES} bytes of UTF-8 (a byte-order mark is a hidden character, refused). No HTML, no images, no links, no
 * {@code {placeholders}}, no control, format or bidirectional characters, and no other content: each is refused with its
 * line. Entries are words placed in the person's draft, exactly as a built-in prompt's; nothing runs them.
 */
public final class PromptPack {

    private PromptPack() {
    }

    public static final int MAX_BYTES = 16 * 1024;
    public static final int MAX_ENTRIES = 9;
    public static final int MAX_TITLE = 60;
    public static final int MAX_DESCRIPTION = 140;
    public static final int MAX_LABEL = 60;
    public static final int MAX_TEXT = 500;

    /** One entry: a label and its prompt text, or (P2b) a label and the example it downloads (then {@code text} is null). */
    public record Entry(String label, String text, ExampleSource example) {
        public Entry(String label, String text) {
            this(label, text, null);
        }

        public boolean isExample() {
            return example != null;
        }
    }

    /** A valid pack. */
    public record Pack(String title, String description, List<Entry> entries) {
        public Pack {
            entries = List.copyOf(entries);
            description = description == null ? "" : description;
        }

        @Override
        public String toString() {             // never the words, should one ever be printed
            return "PromptPack[" + entries.size() + " entries]";
        }
    }

    /** Why a file was refused: its line (0 for the file as a whole) and one cause. */
    public enum Cause {
        TOO_LARGE("the file is over 16 KB"),
        NOT_UTF8("the file is not UTF-8 text"),
        NO_TITLE("the first line must be a title: \"# Title\""),
        TITLE_TOO_LONG("the title is over 60 characters"),
        DESCRIPTION_TOO_LONG("the description is over 140 characters"),
        NOT_AN_ENTRY("this line is not an entry: \"1. **Label** — prompt text\""),
        OUT_OF_ORDER("the entries must be numbered 1, 2, 3 … in order"),
        MISSING_LABEL("an entry needs a bold label: \"1. **Label** — prompt text\""),
        LABEL_TOO_LONG("the label is over 60 characters"),
        TEXT_TOO_LONG("the prompt text is over 500 characters"),
        EMPTY_TEXT("the entry has no prompt text"),
        TOO_MANY("a pack has at most 9 entries"),
        NO_ENTRIES("a pack needs at least one entry"),
        HTML("HTML is not allowed"),
        IMAGE("images are not allowed"),
        LINK("links are not allowed"),
        PLACEHOLDER("placeholders such as {log} are not allowed"),
        HIDDEN_CHARACTER("control, format and bidirectional characters are not allowed"),
        EXTRA_CONTENT("nothing may follow the entries"),
        EXAMPLE_MALFORMED("an example entry is \"N. **Label** — example: https://… [sha256:<64 hex>]\" and nothing else"),
        EXAMPLE_NOT_HTTPS("an example is downloaded only over https"),
        EXAMPLE_HOST("an example's host must be one of " + String.join(", ", ExampleSource.ALLOWED_HOSTS)),
        EXAMPLE_NOT_ARCHIVE("an example must be a commit archive (…/archive/<commit>.zip), with no user, port, query or fragment"),
        EXAMPLE_MOVING_REF("an example must pin a 40-character commit, not a branch or tag"),
        EXAMPLE_DIGEST("an example's sha256 must be 64 hexadecimal characters");

        public final String words;

        Cause(String words) {
            this.words = words;
        }
    }

    public record Refusal(int line, Cause cause) {
        /** "line 7 — the label is over 60 characters" (or the cause alone for the whole file). */
        public String words() {
            return line <= 0 ? cause.words : "line " + line + " — " + cause.words;
        }
    }

    /** A pack, or why not — exactly one is non-null. */
    public record Result(Pack pack, Refusal refusal) {
        public boolean ok() {
            return pack != null;
        }
    }

    private static final Pattern ENTRY = Pattern.compile("^(\\d{1,2})\\. (.*)$");
    private static final Pattern BOLD_LABEL = Pattern.compile("^\\*\\*([^*]+)\\*\\* — (.*)$");
    private static final Pattern HTML_TAG = Pattern.compile("<\\s*[/!?A-Za-z]");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z_][A-Za-z0-9_]*\\}");
    private static final Pattern LINK = Pattern.compile("\\]\\(|https?://|\\bwww\\.|<https?:", Pattern.CASE_INSENSITIVE);
    /** P2b: an entry line whose text begins "example:" — exempt from the link and HTML checks, and held to EXAMPLE. */
    private static final Pattern EXAMPLE_LINE = Pattern.compile("^\\d{1,2}\\. \\*\\*[^*]+\\*\\* — example:.*$");
    private static final Pattern EXAMPLE = Pattern.compile("^example: (?:<(\\S+)>|(\\S+))(?: sha256:(\\S+))?$");

    public static Result parse(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_BYTES) return refuse(0, Cause.TOO_LARGE);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return refuse(0, Cause.NOT_UTF8);
        }
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        // hidden characters, HTML, images, links and placeholders are refused wherever they are, first by line
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            int line = i + 1;
            for (int k = 0; k < l.length(); ) {
                int cp = l.codePointAt(k);
                if (cp != '\t' && hiddenCharacter(cp)) return refuse(line, Cause.HIDDEN_CHARACTER);
                k += Character.charCount(cp);
            }
            if (l.contains("![")) return refuse(line, Cause.IMAGE);
            if (EXAMPLE_LINE.matcher(l).matches()) continue;      // held to the example grammar below, nothing else
            if (HTML_TAG.matcher(l).find()) return refuse(line, Cause.HTML);
            if (LINK.matcher(l).find()) return refuse(line, Cause.LINK);
            if (PLACEHOLDER.matcher(l).find()) return refuse(line, Cause.PLACEHOLDER);
        }
        int i = skipBlank(lines, 0);
        if (i >= lines.length || !lines[i].startsWith("# ") || lines[i].substring(2).isBlank()) {
            return refuse(i + 1, Cause.NO_TITLE);
        }
        String title = lines[i].substring(2).strip();
        if (length(title) > MAX_TITLE) return refuse(i + 1, Cause.TITLE_TOO_LONG);
        i = skipBlank(lines, i + 1);
        String description = null;
        if (i < lines.length && !ENTRY.matcher(lines[i]).matches()) {
            description = lines[i].strip();
            if (length(description) > MAX_DESCRIPTION) return refuse(i + 1, Cause.DESCRIPTION_TOO_LONG);
            i = skipBlank(lines, i + 1);
        }
        List<Entry> entries = new ArrayList<>();
        while (i < lines.length) {
            String l = lines[i];
            if (l.isBlank()) {
                i++;
                continue;
            }
            Matcher m = ENTRY.matcher(l);
            if (!m.matches()) {
                return refuse(i + 1, entries.isEmpty() ? Cause.NOT_AN_ENTRY : Cause.EXTRA_CONTENT);
            }
            int n = Integer.parseInt(m.group(1));
            if (entries.size() == MAX_ENTRIES) return refuse(i + 1, Cause.TOO_MANY);
            if (n != entries.size() + 1) return refuse(i + 1, Cause.OUT_OF_ORDER);
            Matcher b = BOLD_LABEL.matcher(m.group(2));
            if (!b.matches()) return refuse(i + 1, Cause.MISSING_LABEL);
            String label = b.group(1).strip();
            String prompt = b.group(2).strip();
            if (label.isEmpty()) return refuse(i + 1, Cause.MISSING_LABEL);
            if (length(label) > MAX_LABEL) return refuse(i + 1, Cause.LABEL_TOO_LONG);
            if (prompt.isEmpty()) return refuse(i + 1, Cause.EMPTY_TEXT);
            if (length(prompt) > MAX_TEXT) return refuse(i + 1, Cause.TEXT_TOO_LONG);
            if (prompt.startsWith("example:")) {
                Matcher x = EXAMPLE.matcher(prompt);
                if (!x.matches()) return refuse(i + 1, Cause.EXAMPLE_MALFORMED);
                ExampleSource.Parsed parsed = ExampleSource.parse(x.group(1) != null ? x.group(1) : x.group(2), x.group(3));
                if (parsed.refusal() != null) return refuse(i + 1, switch (parsed.refusal()) {
                    case NOT_HTTPS -> Cause.EXAMPLE_NOT_HTTPS;
                    case HOST_NOT_ALLOWED -> Cause.EXAMPLE_HOST;
                    case USER_INFO_OR_PORT, QUERY_OR_FRAGMENT, NOT_AN_ARCHIVE -> Cause.EXAMPLE_NOT_ARCHIVE;
                    case MOVING_REF -> Cause.EXAMPLE_MOVING_REF;
                    case BAD_DIGEST -> Cause.EXAMPLE_DIGEST;
                });
                entries.add(new Entry(label, null, parsed.source()));
                i++;
                continue;
            }
            entries.add(new Entry(label, prompt));
            i++;
        }
        if (entries.isEmpty()) return refuse(lines.length, Cause.NO_ENTRIES);
        return new Result(new Pack(title, description, entries), null);
    }

    private static boolean hiddenCharacter(int cp) {
        if (cp < 0x20 || cp == 0x7F || (cp >= 0x80 && cp <= 0x9F)) return true;
        if ((cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069) || cp == 0x200E || cp == 0x200F) return true;
        int t = Character.getType(cp);
        return t == Character.FORMAT || t == Character.LINE_SEPARATOR || t == Character.PARAGRAPH_SEPARATOR
                || t == Character.PRIVATE_USE || t == Character.UNASSIGNED;
    }

    private static int skipBlank(String[] lines, int from) {
        int i = from;
        while (i < lines.length && lines[i].isBlank()) i++;
        return i;
    }

    private static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    private static Result refuse(int line, Cause cause) {
        return new Result(null, new Refusal(line, cause));
    }
}
