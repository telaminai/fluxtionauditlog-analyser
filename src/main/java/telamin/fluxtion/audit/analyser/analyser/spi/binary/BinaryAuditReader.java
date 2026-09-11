package telamin.fluxtion.audit.analyser.analyser.spi.binary;

import com.telamin.fluxtion.runtime.audit.BinaryLogFile;
import com.telamin.fluxtion.runtime.audit.BinaryLogReader;
import com.telamin.fluxtion.runtime.audit.BinaryRecordDecoder;
import telamin.fluxtion.audit.analyser.analyser.parse.NodeLogTokenizer;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Opens a {@code FLXA} binary audit log in the analyser (M52.5).
 *
 * <p>A binary log could be read at the command line and not in the UI, which is why {@code TEXT}
 * remained the default record format even though the binary one is faster to write. This closes that:
 * frames are decoded by the runtime's {@link BinaryLogReader} and rendered as the record text the rest
 * of the analyser already understands, so filters, series, coverage and reports work unchanged.
 *
 * <p><b>Why this calls the runtime's reader rather than carrying its own.</b> The analyser first
 * shipped a second decoder written from the format specification, on the reasoning that two
 * implementations agreeing is evidence the specification is right. The owner's call reversed it, and
 * the reversal was correct: this is a BRANCH, so it can depend on the snapshot that contains the
 * reader, and two implementations only prove a format when something forces them to agree. Nothing
 * did — the duplicate's tests hand-encoded the format from the same reading of the same document, so a
 * misreading would have been repeated in both halves and passed. One tested implementation beats two
 * that agree by construction.
 *
 * <p>The debt this takes on is stated rather than hidden: the analyser now depends on a SNAPSHOT for a
 * shipping feature. Releasing it needs a runtime release carrying {@link BinaryLogReader}.
 *
 * <p><b>What the binary format cannot supply is omitted rather than invented.</b> {@code groupingId}
 * and {@code thread} are not in the wire format, so they do not appear; emitting {@code groupingId:
 * null} would assert the log said something it did not.
 *
 * <p><b>Text that would be syntax is quoted.</b> The record text this constructs is parsed by the
 * analyser's tokenizer, which splits on top-level commas and {@code ": "} and types {@code null},
 * booleans and numbers. A logged String is typed on the wire (tag CHARSEQ/OBJECT) and can spell
 * anything, so written bare it can BE syntax: a review showed {@code "ok, price: 42.0"} reading as a
 * second entry with a numeric figure the producer never published, and a value carrying a newline
 * rewriting the record's {@code eventType}. Every string the tokenizer would mis-split or mistype is
 * therefore written in the quoted form of format-spec §3a, which the tokenizer decodes losslessly and
 * marks as a string. Keys and instance ids get the same treatment, on a stricter identifier rule. The
 * grammar is THIS READER's declaration ({@link #textEncoding()}), applied by the parser to every record
 * it delivers and never read from the text - a round-6 review showed a legacy value containing a
 * declaration-shaped line being promoted into a control field. A text log is read with the legacy
 * grammar, exactly as it always was. Every wire tag crosses this boundary the same way: numbers and
 * booleans bare, everything else - a char included - as text that is quoted when it has to be.
 *
 * <p><b>The unit is decided at the header, before any record.</b> This reader presents every file as
 * epoch milliseconds ({@link #timeBase()}). A file whose header says otherwise is refused in
 * {@code onHeader}, so no record is delivered in the wrong unit; an earlier version checked the unit
 * after the runtime's reader returned, by which time every record had already been handed on. A file
 * whose header says nothing is refused too: the analyser does not assume a unit, the user declares
 * one into the file with the runtime's {@code AuditLogTool --declare-unit}, and the declaration then
 * travels with the evidence.
 */
public final class BinaryAuditReader implements AuditLogReader {

    @Override
    public String formatId() {
        return "fluxtion-binary";
    }

    @Override
    public String displayName() {
        return "Fluxtion binary audit log (FLXA)";
    }

    @Override
    public boolean canOpen(Path source) {
        // The magic is four bytes at offset 0, so there is no guessing from an extension. A truncated
        // file still opens: truncation is the normal end state of the crash that made someone open it.
        try {
            byte[] head = new byte[BinaryLogFile.MAGIC.length];
            try (var in = Files.newInputStream(source)) {
                int read = 0;
                while (read < head.length) {
                    int n = in.read(head, read, head.length - read);
                    if (n < 0) return false;
                    read += n;
                }
            }
            for (int i = 0; i < head.length; i++) {
                if (head[i] != BinaryLogFile.MAGIC[i]) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public TimeBase timeBase() {
        return TimeBase.wallClockMillisUtc();
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, true);
    }

    @Override
    public void read(Path source, Consumer<String> recordText) throws IOException {
        read(source, recordText, ignored -> { });
    }

    /**
     * The runtime's reader returns what it could not use - bytes at the end that formed no whole
     * frame, and ids the file never defined. An earlier version discarded that result, so a cut log
     * opened as a whole one and a file whose every String value was undefined looked complete. Both
     * are now stated beside the evidence, never as a record.
     */
    @Override
    public void read(Path source, Consumer<String> recordText, Consumer<String> sourceDiagnostic)
            throws IOException {
        RecordTextRenderer renderer = new RecordTextRenderer(recordText);
        BinaryLogReader.Result result;
        try {
            result = BinaryLogReader.read(source, renderer);
        } catch (UnreadableUnit refused) {
            throw new IOException(refused.getMessage(), refused);
        }
        if (result.truncatedBytes > 0) {
            sourceDiagnostic.accept("the last " + result.truncatedBytes + " bytes of " + source.getFileName()
                    + " did not form a whole record and were not read - a process that stopped mid-write, "
                    + "or a damaged tail. Every record before them is here; the one they belong to is not.");
        }
        if (result.unresolvedIds > 0) {
            sourceDiagnostic.accept(result.unresolvedIds + " reference" + (result.unresolvedIds == 1 ? "" : "s")
                    + " in " + source.getFileName() + " to names the file never defined, shown as #id - "
                    + "an event type, node, key or String value. A rolled file whose dictionary is in an "
                    + "earlier file, or damage. Those values are unknown, not empty.");
        }
    }

    /** The text this reader constructs uses the quoted-scalar grammar (format specification §3a). */
    @Override
    public TextEncoding textEncoding() {
        return TextEncoding.QUOTED_SCALARS;
    }

    /**
     * The unit policy, applied to the header before a record is delivered. This reader DECLARES every
     * file wallClockMillisUtc ({@link #timeBase()}), so:
     * <ul>
     *   <li>{@code EPOCH_MILLIS} is read.</li>
     *   <li>{@code UNSPECIFIED} (0) is refused. An earlier version read it as milliseconds on the claim
     *       that every Java-written file predating the field was milliseconds; a review produced one
     *       that was not, from the pre-release runtime with {@code nanoEpochClock()} installed, and the
     *       C++ runtime of the same era wrote nanoseconds under a zero header as a matter of course.
     *       The analyser cannot know, so it does not assume: the user states the unit with the
     *       runtime's {@code AuditLogTool --declare-unit millis|nanos}, which writes it into a copy's
     *       header, and the declaration then travels with the evidence instead of living in a comment.
     *       No file written by a released runtime carries a zero header; the format shipped with the
     *       field.</li>
     *   <li>{@code EPOCH_NANOS} is refused: presenting it as milliseconds places every record a million
     *       times too far in the future.</li>
     *   <li>Any other code is refused: the format does not define it, so nothing is known about the
     *       unit, and a reader that guesses is worse than one that stops.</li>
     * </ul>
     *
     * @throws UnreadableUnit for a file this reader cannot present truthfully
     */
    static void checkUnit(int timeUnit) {
        switch (timeUnit) {
            case BinaryLogFile.TIME_UNIT_EPOCH_MILLIS:
                return;
            case BinaryLogFile.TIME_UNIT_UNSPECIFIED:
                throw new UnreadableUnit("this audit log's header does not state its time unit (code 0: "
                        + "written before the unit field existed). The analyser presents binary logs as "
                        + "epoch milliseconds and will not assume a file is in them - a pre-release runtime "
                        + "could write nanoseconds under this header. Declare the unit into a copy with "
                        + "the runtime's audit tool: AuditLogTool <file> --declare-unit millis|nanos "
                        + "--out <copy>, then open the copy.");
            case BinaryLogFile.TIME_UNIT_EPOCH_NANOS:
                throw new UnreadableUnit("this audit log declares epoch NANOSECOND timestamps, and this "
                        + "reader presents every binary log as epoch milliseconds. Reading it would place "
                        + "every record a million times too far in the future. Write it with a millisecond "
                        + "clock, or convert it before opening.");
            default:
                throw new UnreadableUnit("this audit log's header carries time unit code " + timeUnit
                        + ", which the format does not define (0 unspecified, 1 epoch milliseconds, "
                        + "2 epoch nanoseconds). Nothing is known about its timestamps, so it is not read.");
        }
    }

    /** Thrown from the header callback so the runtime's reader stops before delivering a record. */
    static final class UnreadableUnit extends RuntimeException {
        UnreadableUnit(String message) {
            super(message);
        }
    }

    /** Turns the reader's callbacks into one YAML record document per audit record. */
    private static final class RecordTextRenderer implements BinaryLogReader.Visitor {
        private final Consumer<String> sink;
        private final StringBuilder out = new StringBuilder(512);
        private final List<String> nodeLines = new ArrayList<>();
        private final StringBuilder currentNode = new StringBuilder();
        private String openNode;
        private long eventTime;
        private long logTime;
        private long endTime;
        private String eventType;
        private String eventTypeFqn;
        private int pending;

        RecordTextRenderer(Consumer<String> sink) {
            this.sink = sink;
        }

        /** id -> name, kept so a String/Object VALUE (stored as a dictionary id) can be rendered. */
        private final java.util.List<String> namesById = new java.util.ArrayList<>();

        @Override
        public void onHeader(int formatVersion, int timeUnit) {
            checkUnit(timeUnit);
        }

        @Override
        public void onDictionaryEntry(int id, String name) {
            while (namesById.size() <= id) {
                namesById.add(null);
            }
            namesById.set(id, name);
        }

        private String nameById(int id) {
            return id >= 0 && id < namesById.size() ? namesById.get(id) : null;
        }

        @Override
        public boolean onRecord(int eventTypeId, String type,
                                long eventTimeIn, long logTimeIn, long endTimeIn, int entryCount) {
            flush();
            eventTime = eventTimeIn;
            logTime = logTimeIn;
            endTime = endTimeIn;
            // TWO fields. `event:` carries the SIMPLE name, which is what the text format has always
            // written and what every feature that matches literally expects. `eventType:` carries the
            // fully-qualified name the wire deliberately records - the identity. Reducing to the simple
            // name alone made com.a.Tick and com.b.Tick the same event, and the scorer's G9 guard -
            // which compares identity and exists to catch exactly that - reported PASS, because the
            // information was gone before it could look.
            eventType = simpleName(type);
            eventTypeFqn = type;
            pending = entryCount;
            nodeLines.clear();
            currentNode.setLength(0);
            openNode = null;
            // A record with no entries still happened, and the analyser should see that it did.
            if (entryCount == 0) {
                flush();
            }
            return true;
        }

        @Override
        public void onEntry(int nodeId, String node, int keyId, String key, int tag, long rawBits) {
            // Consecutive entries for one node become one nodeLogs line, which is how the text format
            // groups them; the writer emits a node's entries together.
            if (openNode == null || !openNode.equals(node)) {
                closeNode();
                openNode = node;
                currentNode.append("    - ").append(name(node)).append(": {");
            } else {
                currentNode.append(',');
            }
            if (keyId == 0) {
                // A trace entry: the node ran and logged no property. keyId 0 is "no key", not an id
                // that failed to resolve.
                currentNode.append(" invoked: true");
            } else {
                // The dictionary-resolving overload. The id-free one has no dictionary and rendered
                // every String and Object value as its raw tag/id pair - "#tag5:4" - and a logged null
                // as "#tag5:0", the spelling that means an UNRESOLVED id everywhere else.
                currentNode.append(' ').append(name(key)).append(": ")
                        .append(value(tag, rawBits));
            }
            if (--pending == 0) {
                flush();
            }
        }

        private static final int TAG_CHARSEQ = 5, TAG_OBJECT = 6;

        /**
         * A value as the tokenizer will read it back. Primitives render as the decoder spells them;
         * they cannot be syntax. A String or Object value is the dictionary text verbatim, and is quoted
         * whenever written bare it would split, nest, end the line, or read as null, a boolean or a
         * number - the wire says it is a string, and the text must say so too. A null stays the bare
         * {@code null} literal, which is how the tokenizer spells an absent value; the string "null"
         * is quoted, so the two never meet.
         */
        private String value(int tag, long rawBits) {
            switch (tag) {
                case BinaryRecordDecoder.TAG_DOUBLE:
                case BinaryRecordDecoder.TAG_LONG:
                case BinaryRecordDecoder.TAG_INT:
                case BinaryRecordDecoder.TAG_BOOL:
                    // The only tags whose rendering is a number or a boolean literal: they cannot be
                    // syntax and the tokenizer types them as the wire did.
                    return BinaryRecordDecoder.renderValue(tag, rawBits, this::nameById);
                case BinaryRecordDecoder.TAG_CHARSEQ:
                case BinaryRecordDecoder.TAG_OBJECT:
                    if (rawBits == 0 || nameById((int) rawBits) == null) {
                        // A logged null is the bare literal the tokenizer reads as absent; an
                        // unresolved id renders as the decoder's diagnostic, quoted below.
                        return quoted(BinaryRecordDecoder.renderValue(tag, rawBits, this::nameById), rawBits == 0);
                    }
                    return quoted(nameById((int) rawBits), false);
                default:
                    // EVERY OTHER TAG IS TEXT. A char in particular: a review logged '\'' and '{' and
                    // '"' and each swallowed the entry after it, and '7' became a figure. A character
                    // is textual content - it never types as a number, a flag or null - so it takes the
                    // same road as a String. An unknown tag's "#tagN:bits" diagnostic goes the same way.
                    return quoted(BinaryRecordDecoder.renderValue(tag, rawBits, this::nameById), false);
            }
        }

        /** Quoted when the bare spelling would split, nest, end the line, strip, or type. */
        private static String quoted(String text, boolean bareNullLiteral) {
            if (bareNullLiteral) {
                return text;
            }
            return NodeLogTokenizer.needsQuoting(text) ? NodeLogTokenizer.quote(text) : text;
        }

        /** An instance id or key: a plain identifier as is, anything else quoted. */
        private static String name(String s) {
            if (s == null) {
                return "null";
            }
            return NodeLogTokenizer.needsQuotingAsName(s) ? NodeLogTokenizer.quote(s) : s;
        }

        /**
         * A top-level scalar such as {@code event:} is parsed by the record parser, which has no
         * quoted form, on its own line. A class name cannot hold a line break, so one in the dictionary
         * is a corrupt or hostile file; it is made visible rather than allowed to end the line early
         * and start a scalar the file never wrote.
         */
        private static String oneLine(String s) {
            if (s == null || (s.indexOf('\n') < 0 && s.indexOf('\r') < 0)) {
                return s;
            }
            return s.replace("\r", "\\r").replace("\n", "\\n");
        }

        private static String simpleName(String className) {
            if (className == null) {
                return null;
            }
            int cut = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
            return cut < 0 ? className : className.substring(cut + 1);
        }

        private void closeNode() {
            if (openNode != null) {
                currentNode.append('}');
                nodeLines.add(currentNode.toString());
                currentNode.setLength(0);
                openNode = null;
            }
        }

        private void flush() {
            if (eventType == null) {
                return;
            }
            closeNode();
            out.setLength(0);
            out.append("---\n")
                    .append("eventLogRecord:\n")
                    .append("  eventTime: ").append(eventTime).append('\n')
                    .append("  logTime: ").append(logTime).append('\n')
                    .append("  event: ").append(oneLine(eventType)).append('\n')
                    .append("  eventType: ").append(oneLine(eventTypeFqn)).append('\n')
                    .append("  nodeLogs:\n");
            for (String line : nodeLines) {
                out.append(line).append('\n');
            }
            out.append("  endTime: ").append(endTime).append('\n');
            sink.accept(out.toString());
            eventType = null;
            nodeLines.clear();
        }
    }
}
