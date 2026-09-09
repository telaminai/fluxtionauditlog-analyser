package telamin.fluxtion.audit.analyser.analyser.spi.binary;

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
 * the reader decodes frames and hands the rest of the analyser exactly the record text it already
 * understands, so every downstream feature — filters, series, coverage, reports — works unchanged.
 *
 * <p><b>What the binary format cannot supply is omitted rather than invented.</b> {@code groupingId}
 * and {@code thread} are not in the wire format, so they do not appear; a reader that emitted
 * {@code groupingId: null} would be asserting the log said something it did not.
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
        // The magic is four bytes at offset 0 - there is no need to guess from an extension, and a
        // truncated file still opens, because truncation is a normal end state for an audit log.
        try {
            byte[] head = new byte[BinaryLogDecoder.MAGIC.length];
            try (var in = Files.newInputStream(source)) {
                int read = 0;
                while (read < head.length) {
                    int n = in.read(head, read, head.length - read);
                    if (n < 0) return false;
                    read += n;
                }
            }
            for (int i = 0; i < head.length; i++) {
                if (head[i] != BinaryLogDecoder.MAGIC[i]) return false;
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
        StringBuilder out = new StringBuilder(512);
        List<String> nodeLines = new ArrayList<>();
        StringBuilder currentNode = new StringBuilder();
        String[] openNode = {null};

        BinaryLogDecoder.Visitor visitor = new BinaryLogDecoder.Visitor() {
            long eventTime, logTime, endTime;
            String eventType;

            @Override
            public void recordStart(long eventTimeIn, long logTimeIn, long endTimeIn, String type) {
                eventTime = eventTimeIn;
                logTime = logTimeIn;
                endTime = endTimeIn;
                eventType = type;
                nodeLines.clear();
                currentNode.setLength(0);
                openNode[0] = null;
            }

            @Override
            public void entry(String node, String key, int tag, long rawBits) {
                // Consecutive entries for one node become one nodeLogs line, which is how the text
                // format groups them. The writer emits a node's entries together.
                if (openNode[0] == null || !openNode[0].equals(node)) {
                    closeNode();
                    openNode[0] = node;
                    currentNode.append("    - ").append(node).append(": {");
                } else {
                    currentNode.append(',');
                }
                if (key == null) {
                    // A trace entry: the node ran, and logged no property.
                    currentNode.append(" invoked: true");
                } else {
                    currentNode.append(' ').append(key).append(": ")
                            .append(BinaryLogDecoder.renderValue(tag, rawBits));
                }
            }

            @Override
            public void recordEnd() {
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
                recordText.accept(out.toString());
            }

            private void closeNode() {
                if (openNode[0] != null) {
                    currentNode.append('}');
                    nodeLines.add(currentNode.toString());
                    currentNode.setLength(0);
                    openNode[0] = null;
                }
            }
        };

        BinaryLogDecoder.read(source, visitor);
    }
}
