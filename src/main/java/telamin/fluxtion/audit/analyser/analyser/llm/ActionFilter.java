package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The population an {@code aggregate} runs over (spec-assistant-actions §4.1.1). {@code dimensions} and
 * the time bounds are <b>index-resident</b> (O(index)); {@code text} matches nodeLogs and forces a
 * <b>raw byte scan</b> via the supplied {@code rawText} lookup. Immutable; mirrors {@code FilterState}
 * semantics so an aggregate agrees with what the UI filter would show.
 */
public record ActionFilter(List<String> dimensions, Long from, Long to, String text) {

    public boolean isRawScan() {
        return text != null && !text.isBlank();
    }

    /** Does row {@code i} of the snapshot pass this filter? {@code rawText} may be null when no text filter. */
    public boolean matches(LogIndex.Snapshot snap, int i, IntFunction<String> rawText) {
        if (from != null || to != null) {
            Long t = snap.logTime(i);
            if (t == null) return false;                 // a time-bounded query excludes untimed rows
            if (from != null && t < from) return false;
            if (to != null && t > to) return false;
        }
        if (dimensions != null && !dimensions.isEmpty() && !dimensions.contains(snap.dimension(i))) {
            return false;
        }
        if (isRawScan()) {
            if (rawText == null) return false;           // text requested but no source → matches nothing
            String raw = rawText.apply(i);
            if (raw == null || !raw.toLowerCase().contains(text.toLowerCase())) return false;
        }
        return true;
    }

    /** Echo back into the result so a count is never reported without its population. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("dimensions", dimensions == null ? List.of() : dimensions);
        m.put("from", from);
        m.put("to", to);
        m.put("text", text);
        return m;
    }

    /** Parse from the {@code filter} object of an action's params (any field may be absent). */
    @SuppressWarnings("unchecked")
    public static ActionFilter from(Object filterObj) {
        if (!(filterObj instanceof Map<?, ?> m)) return new ActionFilter(List.of(), null, null, null);
        List<String> dims = new ArrayList<>();
        Object d = m.get("dimensions");
        if (d instanceof List<?> list) {
            for (Object o : list) if (o != null) dims.add(o.toString());
        }
        return new ActionFilter(dims, asLong(m.get("from")), asLong(m.get("to")), asText(m.get("text")));
    }

    static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    static String asText(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isBlank() ? null : s;
    }
}
