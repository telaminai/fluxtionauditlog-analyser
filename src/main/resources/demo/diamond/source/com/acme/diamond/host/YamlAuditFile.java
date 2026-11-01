package com.acme.diamond.host;

import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.audit.LogRecordListener;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

/**
 * Writes each audit record as one YAML document, with the {@code ---} separator the record itself
 * does not carry. A named class rather than a lambda, so its {@code toString} is stable from run to run.
 */
final class YamlAuditFile implements LogRecordListener {
    private final Writer out;
    private int records;

    YamlAuditFile(Writer out) {
        this.out = out;
    }

    @Override
    public void processLogRecord(LogRecord r) {
        try {
            out.write("---\n");
            out.write(r.toString());
            out.write("\n");
            records++;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The format's stream-end marker (format 1.1, section 1a), with the separator that makes it a claim. */
    void close() throws IOException {
        out.write("---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: " + records + "\n---\n");
        out.flush();
    }

    int records() {
        return records;
    }

    @Override
    public String toString() {
        return "YamlAuditFile";
    }
}
