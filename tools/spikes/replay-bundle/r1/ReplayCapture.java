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
    /** The types that arrive from OUTSIDE the processor. Any other event it hears was raised by the graph itself. */
    private Set<Class<?>> inputs = new HashSet<>();
    private transient Writer target;
    private transient Consumer<Object> observer;

    public ReplayCapture(Clock clock) { this.clock = clock; }

    public ReplayCapture() { this(null); }

    public Set<Class<?>> getInputs() { return inputs; }

    public void setInputs(Set<Class<?>> inputs) { this.inputs.clear(); this.inputs.addAll(inputs); }

    public ReplayCapture inputs(Class<?>... types) { setInputs(new HashSet<>(Arrays.asList(types))); return this; }

    public void setTarget(Writer target) { this.target = target; }

    /** Observe mode: report each event heard, write nothing. */
    public void setObserver(Consumer<Object> observer) { this.observer = observer; }

    @Override
    public void nodeRegistered(Object node, String nodeName) { }

    @Override
    public void eventReceived(Event event) { eventReceived((Object) event); }

    @Override
    public void eventReceived(Object event) {
        if (isFramework(event)) return;
        if (observer != null) { observer.accept(event); return; }
        if (target == null) return;
        try {
            // a YAML comment marks a graph-raised record, so Fluxtion's own parser still reads the file
            target.append(inputs.contains(event.getClass()) ? "---\n" : "---\n# raised\n")
                    .append("!!com.telamin.fluxtion.runtime.event.ReplayRecord\nevent: !!")
                    .append(event.getClass().getName()).append(' ').append(encode(event))
                    .append("\nwallClockTime: ").append(Long.toString(clock.getProcessTime())).append('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

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
