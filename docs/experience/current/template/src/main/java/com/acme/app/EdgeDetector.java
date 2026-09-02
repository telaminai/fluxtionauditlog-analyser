package com.acme.app;

import java.util.HashMap;
import java.util.Map;

/**
 * "Did this just become true?" — the state every EDGE rule needs, written once.
 *
 * <p>An EDGE rule fires on the event that first makes a condition true, and again only after the
 * condition has gone false and true once more. That requires remembering the previous answer per key,
 * and hand-rolling it per rule is the single largest repeated cost in this kind of engine: an author
 * building fourteen EDGE rules wrote fourteen copies of it and ran out of room.
 *
 * <p>Use one instance per rule, inside the node that decides:
 *
 * <pre>
 * public class ReleaseDecider extends EventLogNode {
 *     private final transient EdgeDetector released = new EdgeDetector();
 *
 *     &#64;OnTrigger public boolean check() {
 *         boolean fired = false;
 *         for (String orderId : orders.openOrderIds()) {
 *             if (released.roseFor(orderId, isReleasable(orderId))) {
 *                 decisions.add("RELEASE", orderId);   // collect, do not overwrite
 *                 fired = true;
 *             }
 *         }
 *         return fired;
 *     }
 * }
 * </pre>
 *
 * <p>Two things to note in that loop. It walks <b>every</b> key, because one event can make several
 * orders releasable at once and each is a separate decision. And it <b>adds</b> to a collection rather
 * than assigning to a field — a node that holds one decision per cycle silently drops the rest.
 */
public class EdgeDetector {

    private final Map<String, Boolean> previous = new HashMap<>();

    /** True on the cycle where {@code nowTrue} first becomes true for this key, and not while it stays true. */
    public boolean roseFor(String key, boolean nowTrue) {
        boolean was = previous.getOrDefault(key, false);
        previous.put(key, nowTrue);
        return nowTrue && !was;
    }

    /** For rules with a single subject rather than one per key. */
    public boolean rose(boolean nowTrue) {
        return roseFor("", nowTrue);
    }

    /** True on the cycle where a numeric quantity crosses below a bound from at-or-above it. */
    public boolean fellBelow(String key, long value, long bound) {
        return roseFor(key, value < bound);
    }

    /** Forget a key — use when its subject is terminal, so a later revival cannot re-fire on stale state. */
    public void forget(String key) {
        previous.remove(key);
    }
}
