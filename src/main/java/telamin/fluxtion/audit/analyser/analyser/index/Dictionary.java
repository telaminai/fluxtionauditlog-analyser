package telamin.fluxtion.audit.analyser.analyser.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** String interner: maps repeated strings (dimensions, loggers, threads) to compact int ids. */
public final class Dictionary {

    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> values = new ArrayList<>();

    /** Interns {@code s} (null treated as empty) and returns its id. */
    public int intern(String s) {
        String key = (s == null) ? "" : s;
        Integer id = ids.get(key);
        if (id != null) return id;
        int nid = values.size();
        ids.put(key, nid);
        values.add(key);
        return nid;
    }

    public String get(int id) {
        return (id >= 0 && id < values.size()) ? values.get(id) : null;
    }

    public int size() {
        return values.size();
    }

    /**
     * A defensive copy of the id→string values, for {@link LogIndex#snapshot()}. Must be called while
     * holding the index lock (all {@link #intern} calls go through the synchronized index), so the copy
     * is consistent and an off-lock reader never touches the live, resizable backing list.
     */
    String[] copyValues() {
        return values.toArray(new String[0]);
    }
}