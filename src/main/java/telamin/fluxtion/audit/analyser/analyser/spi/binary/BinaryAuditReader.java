package telamin.fluxtion.audit.analyser.analyser.spi.binary;

import com.telamin.fluxtion.runtime.audit.BinaryLogFile;
import com.telamin.fluxtion.runtime.audit.BinaryLogReader;
import com.telamin.fluxtion.runtime.audit.BinaryRecordDecoder;
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
        RecordTextRenderer renderer = new RecordTextRenderer(recordText);
        BinaryLogReader.read(source, renderer);
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
        private int pending;

        RecordTextRenderer(Consumer<String> sink) {
            this.sink = sink;
        }

        @Override
        public boolean onRecord(int eventTypeId, String type,
                                long eventTimeIn, long logTimeIn, long endTimeIn, int entryCount) {
            flush();
            eventTime = eventTimeIn;
            logTime = logTimeIn;
            endTime = endTimeIn;
            // The wire format stores Class.getName(); the analyser's text format carries the SIMPLE
            // name ("event: LifecycleEvent"), and every downstream feature matches on that. Rendering
            // the FQN here would make the same event look like a different one depending on which
            // format the log arrived in.
            eventType = simpleName(type);
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
                currentNode.append("    - ").append(node).append(": {");
            } else {
                currentNode.append(',');
            }
            if (keyId == 0) {
                // A trace entry: the node ran and logged no property. keyId 0 is "no key", not an id
                // that failed to resolve.
                currentNode.append(" invoked: true");
            } else {
                currentNode.append(' ').append(key).append(": ")
                        .append(BinaryRecordDecoder.renderValue(tag, rawBits));
            }
            if (--pending == 0) {
                flush();
            }
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
                    .append("  event: ").append(eventType).append('\n')
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
