package telamin.fluxtion.audit.analyser.analyser.export;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Exports the currently-filtered records (spec §13). CSV is the index columns (one row per record);
 * YAML is the raw record text, so it re-loads into the analyser. Both honour the active filter.
 */
public final class RecordExporter {

    private static final DateTimeFormatter UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final String[] HEADERS = {
            "eventTimeUtc", "logTimeUtc", "endTimeUtc", "event", "callback", "eventToString", "thread", "nodeLogs"
    };

    private RecordExporter() {
    }

    public static String toCsv(LogStore store, FilterState filter) {
        LogIndex idx = store.index();
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append('\n');
        for (int row = 0; row < idx.size(); row++) {
            if (!filter.test(idx, row)) continue;
            append(sb, utc(idx.eventTime(row))); sb.append(',');
            append(sb, utc(idx.logTime(row))); sb.append(',');
            append(sb, utc(idx.endTime(row))); sb.append(',');
            append(sb, nz(idx.event(row))); sb.append(',');
            append(sb, nz(idx.callback(row))); sb.append(',');
            append(sb, nz(idx.eventToString(row))); sb.append(',');
            append(sb, nz(idx.thread(row))); sb.append(',');
            append(sb, Integer.toString(idx.nodeLogsCount(row)));
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String toYaml(LogStore store, FilterState filter) {
        LogIndex idx = store.index();
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < idx.size(); row++) {
            if (!filter.test(idx, row)) continue;
            sb.append("---\n").append(store.rawText(row)).append('\n');
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
            sb.append('"').append(value.replace("\"", "\"\"")).append('"');
        } else {
            sb.append(value);
        }
    }

    private static String utc(Long millis) {
        return millis == null ? "" : UTC.format(Instant.ofEpochMilli(millis));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
