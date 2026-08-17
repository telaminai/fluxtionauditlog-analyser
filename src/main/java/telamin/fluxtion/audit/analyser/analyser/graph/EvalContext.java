package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.Map;

/**
 * One record's view for expression evaluation (spec-expr-conditionals-windows W0): the record's log
 * time and the resolved reference values. {@code logTime} is a primitive because every call site
 * skips logTime-null rows before evaluating — a record with no time can anchor no window.
 */
public record EvalContext(long logTime, Map<GraphKey, Double> values) {
}
