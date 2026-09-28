package com.acme.demo.replay;

import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.audit.Auditor;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.lifecycle.Lifecycle;
import com.telamin.fluxtion.runtime.time.Clock;
import com.telamin.fluxtion.runtime.time.ClockStrategy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The analyser's replay writer (spec R-D8), compiled into the processor as an auditor. It records EVERY event the
 * processor hears, graph-raised ones included, stamped with the instant the cycle ran at ({@code getProcessTime()},
 * not a fresh read). In observe mode it reports each event to the replay runner instead of writing it. No
 * dependencies beyond the runtime: records are written by their components, in Fluxtion's ReplayRecord YAML shape.
 */
public class ReplayCapture implements Auditor {
    public static final String NAME = "replayCapture";
    @Inject
    private final Clock clock;
    /** Every event type the processor handles, known when it is built; nothing else is written or read. */
    private Set<Class<?>> handled = new HashSet<>();
    /** The handled types the graph raises on itself; declared, because a method body is not visible statically. */
    private Set<Class<?>> raised = new HashSet<>();
    private transient Writer target;
    /** Set by the consumption point just before it calls onEvent: the one object that is this cycle's INPUT. */
    private transient Object expected;
    private transient boolean identityMode;
    private transient Consumer<Object> observer;

    public ReplayCapture(Clock clock) { this.clock = clock; }

    public ReplayCapture() { this(null); }

    public Set<Class<?>> getHandled() { return handled; }

    public void setHandled(Set<Class<?>> handled) { this.handled.clear(); this.handled.addAll(handled); }

    public Set<Class<?>> getRaised() { return raised; }

    public void setRaised(Set<Class<?>> raised) { this.raised.clear(); this.raised.addAll(raised); }

    /**
     * Build time: the processor's handled types, each checked encodable NOW, so an event the writer cannot write
     * fails the build by name rather than the first cycle in production.
     */
    public ReplayCapture handles(Set<Class<?>> types) {
        types.forEach(ReplayCapture::checkEncodable);
        setHandled(types);
        return this;
    }

    public ReplayCapture raisedByGraph(Class<?>... types) {
        for (Class<?> t : types) {
            if (!handled.contains(t)) throw new IllegalArgumentException(t.getName() + " is raised but not handled");
        }
        setRaised(new HashSet<>(Arrays.asList(types)));
        return this;
    }

    static void checkEncodable(Class<?> type) {
        if (!type.isRecord()) throw new IllegalArgumentException("ReplayCapture cannot write " + type.getName() + ": not a record");
        for (RecordComponent c : type.getRecordComponents()) {
            Class<?> t = c.getType();
            if (!(t == String.class || t.isPrimitive() || Number.class.isAssignableFrom(t) || t == Boolean.class)) {
                throw new IllegalArgumentException("ReplayCapture cannot write " + type.getName() + "." + c.getName() + ": " + t.getName());
            }
        }
    }

    public void setTarget(Writer target) { this.target = target; }

    /**
     * Identity mode: the caller names each input before dispatching it, and only that object is recorded. Anything
     * else the processor hears during the call was raised by the graph, whatever its type.
     */
    public void expect(Object input) { identityMode = true; expected = input; }

    /** Observe mode: report each event heard, write nothing. */
    public void setObserver(Consumer<Object> observer) { this.observer = observer; }

    @Override
    public void nodeRegistered(Object node, String nodeName) { }

    @Override
    public void eventReceived(Event event) { eventReceived((Object) event); }

    @Override
    public void eventReceived(Object event) {
        if (trace != null && !isFramework(event)) trace.add("eventReceived " + event.getClass().getSimpleName());
        if (isFramework(event) || !handled.contains(event.getClass())) return;
        if (observer != null) { observer.accept(event); return; }
        if (identityMode) {
            if (event != expected) return;          // graph-raised: same type or not, never recorded
            expected = null;
        }
        if (target == null) return;
        try {
            // a YAML comment marks a graph-raised record, so Fluxtion's own parser still reads the file
            target.append(raised.contains(event.getClass()) ? "---\n# raised\n" : "---\n")
                    .append("!!com.telamin.fluxtion.runtime.event.ReplayRecord\nevent: !!")
                    .append(event.getClass().getName()).append(' ').append(encode(event))
                    .append("\nwallClockTime: ").append(Long.toString(clock.getProcessTime())).append('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Probe: the order of receipt and completion callbacks, to see whether a raised event arrives INSIDE its input's cycle. */
    public static transient java.util.List<String> trace;

    @Override
    public void processingComplete() { if (trace != null) trace.add("processingComplete"); }

    static boolean isFramework(Object event) {
        return event instanceof Lifecycle.LifecycleEvent || event instanceof EventLogControlEvent
                || event instanceof ClockStrategy.ClockStrategyEvent;
    }

    static String encode(Object event) {
        if (!event.getClass().isRecord()) {
            throw new IllegalArgumentException("ReplayCapture writes records only: " + event.getClass().getName());
        }
        StringBuilder sb = new StringBuilder("{");
        RecordComponent[] parts = event.getClass().getRecordComponents();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(", ");
            Object v;
            try { v = parts[i].getAccessor().invoke(event); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            sb.append(parts[i].getName()).append(": ");
            if (v instanceof String s) sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else throw new IllegalArgumentException("unsupported component " + parts[i].getName() + ": " + parts[i].getType());
        }
        return sb.append('}').toString();
    }
}
