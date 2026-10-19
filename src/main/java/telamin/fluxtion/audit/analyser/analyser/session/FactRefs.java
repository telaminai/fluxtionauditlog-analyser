package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M74 (spec §4.1; scorecard R6): where the words and places a FACT names by reference live — Claude's proposal words (a
 * summary, an item's reason, path or class name) and the folders a person chose (the set-up folder, an example's target,
 * its unpacked root). The frame owns one; facts carry the reference; the node keeps it; the frame resolves it when it
 * performs an effect or renders. Memory only, bounded, never written anywhere — and unlike the conversation's transcript
 * store it is not trimmed by a long conversation, so a reference a node still holds keeps resolving.
 */
public final class FactRefs {

    /** At most this many references are kept; the oldest go first. */
    public static final int MAX = 4_096;

    private final AtomicLong next = new AtomicLong(1);
    private final Map<Long, String> held = new LinkedHashMap<>();

    /** Keep {@code words}; 0 for none (null). */
    public synchronized long put(String words) {
        if (words == null) return 0;
        long id = next.getAndIncrement();
        held.put(id, words);
        while (held.size() > MAX) held.remove(held.keySet().iterator().next());
        return id;
    }

    /** What {@code ref} names, or null for 0 or a reference no longer held. */
    public synchronized String get(long ref) {
        return ref == 0 ? null : held.get(ref);
    }
}
