package com.acme.demo.replay;

import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.audit.Auditor;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.time.Clock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Set;

/**
 * A replay writer, compiled into the processor as an auditor (spec-evidence-bundle-replay §3.2).
 *
 * <ul>
 *   <li><b>What:</b> only the event the caller names with {@link #expect} just before it calls {@code onEvent}.
 *   Anything else the processor hears during that call was raised by the graph itself, whatever its type, and is
 *   never recorded: replaying it would raise it twice.</li>
 *   <li><b>When:</b> {@code clock.getProcessTime()}, the instant the cycle ran at, which the audit log's
 *   {@code eventTime} also shows. Not a fresh reading.</li>
 *   <li><b>How:</b> the event types the processor handles, known when it is built ({@link #handles}). Each is
 *   checked encodable then, so a type this writer cannot write fails the build rather than a cycle in
 *   production. Records are written by their components, in Fluxtion's ReplayRecord YAML shape, with no
 *   dependency beyond the runtime.</li>
 * </ul>
 */
public class ReplayCapture implements Auditor {
    public static final String NAME = "replayCapture";
    @Inject
    private final Clock clock;
    private Set<Class<?>> handled = new HashSet<>();
    private transient Writer target;
    private transient Object expected;

    public ReplayCapture(Clock clock) { this.clock = clock; }

    public ReplayCapture() { this(null); }

    public Set<Class<?>> getHandled() { return handled; }

    public void setHandled(Set<Class<?>> handled) { this.handled.clear(); this.handled.addAll(handled); }

    /** Build time: the processor's handled event types, each checked encodable now. */
    public ReplayCapture handles(Set<Class<?>> types) {
        types.forEach(ReplayCapture::checkEncodable);
        setHandled(types);
        return this;
    }

    public void setTarget(Writer target) { this.target = target; }

    /** The consumption point names the input it is about to dispatch; only that object is recorded. */
    public void expect(Object input) { expected = input; }

    @Override
    public void nodeRegistered(Object node, String nodeName) { }

    @Override
    public void eventReceived(Event event) { eventReceived((Object) event); }

    @Override
    public void eventReceived(Object event) {
        if (event != expected || target == null) return;
        expected = null;
        if (!handled.contains(event.getClass())) return;
        try {
            target.append("---\n!!com.telamin.fluxtion.runtime.event.ReplayRecord\nevent: !!")
                    .append(event.getClass().getName()).append(' ').append(encode(event))
                    .append("\nwallClockTime: ").append(Long.toString(clock.getProcessTime())).append('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Exactly the component types {@link ReplayReader} decodes: anything else fails the build, not a cycle. */
    static final Set<Class<?>> ENCODABLE = Set.of(String.class, char.class, Character.class, boolean.class, Boolean.class,
            byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class,
            float.class, Float.class, double.class, Double.class);

    static void checkEncodable(Class<?> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException("ReplayCapture cannot write " + type.getName() + ": not a record");
        }
        for (RecordComponent c : type.getRecordComponents()) {
            if (!ENCODABLE.contains(c.getType())) {
                throw new IllegalArgumentException(
                        "ReplayCapture cannot write " + type.getName() + "." + c.getName() + ": " + c.getType().getName());
            }
        }
    }

    static String encode(Object event) {
        StringBuilder sb = new StringBuilder("{");
        RecordComponent[] parts = event.getClass().getRecordComponents();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(", ");
            Object v;
            try {
                v = parts[i].getAccessor().invoke(event);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            sb.append(parts[i].getName()).append(": ");
            if (v == null) sb.append("null");                              // never quoted: a quoted "null" is the text
            else if (v instanceof String || v instanceof Character) quote(sb, v.toString());
            else sb.append(v);
        }
        return sb.append('}').toString();
    }

    /**
     * A string as one quoted token on one line: backslash, quote, and every character that could end a line or a
     * record ({@code \n}, {@code \r}, the other controls, U+0085, U+2028, U+2029) escaped, so a record is always
     * exactly one line and {@code ---} inside a string can never split one.
     */
    static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20 || ch == 0x7f || ch == 0x85 || ch == 0x2028 || ch == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }
}
