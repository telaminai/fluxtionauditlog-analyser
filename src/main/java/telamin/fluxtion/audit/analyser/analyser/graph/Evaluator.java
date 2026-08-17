package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A per-scan, possibly-stateful evaluator for an {@link Expr} — a compiled <b>mirror</b> of the AST
 * (spec-expr-conditionals-windows W0). One mirror node is built per tree <b>position</b>, so
 * structurally-equal subtrees ({@code delta(x) + delta(x)}, M28.3) each own their own state. State is
 * deliberately never keyed by AST node: {@code Expr} nodes are records with deep value equality, so a
 * map would hand equal subtrees one shared cell (advancing twice per record), and an identity map
 * only holds while the parser never interns equal subtrees — an invariant nothing enforces.
 *
 * <p>Create <b>one per scan</b> and evaluate rows in order — a fresh scan gets a fresh evaluator, so
 * rolling windows reset by construction, never by a call someone can forget. This is also why
 * {@code Expr} has no per-record eval convenience: a throwaway evaluator per row would silently reset
 * every window, a bug whose only symptom is wrong numbers.
 *
 * <p>Semantics are unchanged from the pre-W0 engine: missing ref → NaN, div-by-zero → NaN, any NaN
 * propagates ("no data point"), comparisons yield 1.0/0.0 with NaN staying unknown, and every
 * {@code if()} argument is evaluated eagerly so windows see a deterministic sample stream.
 */
public final class Evaluator {

    private interface Node {
        double eval(EvalContext ctx);
    }

    private final Node root;
    private int stateSlots;

    Evaluator(Expr e) {
        this.root = compile(e);
    }

    public double eval(EvalContext ctx) {
        return root.eval(ctx);
    }

    /** Convenience when the caller holds the pieces loose. */
    public double eval(long logTime, Map<GraphKey, Double> values) {
        return root.eval(new EvalContext(logTime, values));
    }

    /** 0 is a checkable proof that this expression carries no window state (spec M28 W0). */
    public int stateSlotCount() {
        return stateSlots;
    }

    private Node compile(Expr e) {
        return switch (e) {
            case Expr.Num n -> ctx -> n.value();
            case Expr.Ref r -> ctx -> {
                Double d = ctx.values().get(r.key());
                return d == null ? Double.NaN : d;
            };
            case Expr.Neg g -> {
                Node a = compile(g.e());
                yield ctx -> -a.eval(ctx);
            }
            case Expr.Bin b -> {
                Node l = compile(b.left());
                Node r = compile(b.right());
                yield switch (b.op()) {
                    case '+' -> ctx -> l.eval(ctx) + r.eval(ctx);
                    case '-' -> ctx -> l.eval(ctx) - r.eval(ctx);
                    case '*' -> ctx -> l.eval(ctx) * r.eval(ctx);
                    case '/' -> ctx -> {
                        double d = r.eval(ctx);
                        return d == 0.0 ? Double.NaN : l.eval(ctx) / d;   // div-by-zero → no point (not ±Inf)
                    };
                    default -> ctx -> Double.NaN;
                };
            }
            case Expr.Cmp c -> {
                Node l = compile(c.left());
                Node r = compile(c.right());
                String op = c.op();
                yield ctx -> {
                    double a = l.eval(ctx), b = r.eval(ctx);
                    if (Double.isNaN(a) || Double.isNaN(b)) return Double.NaN;   // unknown stays unknown
                    boolean holds = switch (op) {
                        case ">"  -> a > b;
                        case "<"  -> a < b;
                        case ">=" -> a >= b;
                        case "<=" -> a <= b;
                        case "==" -> a == b;
                        case "!=" -> a != b;
                        default   -> false;
                    };
                    return holds ? 1.0 : 0.0;
                };
            }
            case Expr.Call c -> compileCall(c);
        };
    }

    private Node compileCall(Expr.Call c) {
        List<Node> args = new ArrayList<>(c.args().size());
        for (Expr a : c.args()) args.add(compile(a));
        return switch (c.fn()) {
            case "abs" -> ctx -> Math.abs(args.get(0).eval(ctx));
            case "min" -> ctx -> {
                double m = Double.POSITIVE_INFINITY;
                for (Node a : args) m = Math.min(m, a.eval(ctx));
                return m;
            };
            case "max" -> ctx -> {
                double m = Double.NEGATIVE_INFINITY;
                for (Node a : args) m = Math.max(m, a.eval(ctx));
                return m;
            };
            // EVERY argument below is evaluated eagerly — including if()'s untaken branch. Rolling
            // windows (M28.3) must see a deterministic sample stream regardless of which branch wins:
            // a lazily-skipped window would go cold and emit NaN for its full length after every
            // branch switch. Do not "optimise" this to short-circuit.
            case "if" -> ctx -> {
                double cond = args.get(0).eval(ctx);
                double then = args.get(1).eval(ctx);
                double els = args.size() > 2 ? args.get(2).eval(ctx) : Double.NaN;   // 2-arg: else = no point
                return Double.isNaN(cond) ? Double.NaN : (cond != 0.0 ? then : els);
            };
            case "and" -> ctx -> {
                double r = 1.0;
                for (Node a : args) {
                    double x = a.eval(ctx);
                    if (Double.isNaN(x)) r = Double.NaN;               // NaN poisons, but keep evaluating
                    else if (x == 0.0 && !Double.isNaN(r)) r = 0.0;
                }
                return r;
            };
            case "or" -> ctx -> {
                double r = 0.0;
                for (Node a : args) {
                    double x = a.eval(ctx);
                    if (Double.isNaN(x)) r = Double.NaN;
                    else if (x != 0.0 && !Double.isNaN(r)) r = 1.0;
                }
                return r;
            };
            case "not" -> ctx -> {
                double x = args.get(0).eval(ctx);
                return Double.isNaN(x) ? Double.NaN : (x == 0.0 ? 1.0 : 0.0);
            };
            case "lag", "delta", "mean", "sum", "rollingMin", "rollingMax" -> compileWindow(c, args.get(0));
            default -> ctx -> Double.NaN;
        };
    }

    // ---- rolling windows (M28.3) -------------------------------------------------------------------
    //
    // D-W2 (never fabricate, never erase): a non-finite sample contributes nothing and the window is
    // UNCHANGED; an under-filled window yields NaN → no point. Within that rule the functions split by
    // what they are anchored to:
    //   - AGGREGATES (mean/sum/rollingMin/rollingMax) describe the WINDOW, which exists independently
    //     of the current record — so on a non-contributing record a full window still answers. This is
    //     what makes mean(if(c, x), 10) a continuous "mean of the last 10 breaching samples".
    //   - lag/delta are anchored to the CURRENT sample ("N samples ago" needs a now) — no finite sample
    //     this record, no answer this record.

    private Node compileWindow(Expr.Call c, Node arg) {
        stateSlots++;   // each POSITION gets its own state — delta(x)+delta(x) is two slots, not one
        int n = c.fn().equals("delta") ? 1 : (int) ((Expr.Num) c.args().get(1)).value();
        return switch (c.fn()) {
            case "lag" -> new Node() {
                private final double[] ring = new double[n];
                private long count;
                @Override public double eval(EvalContext ctx) {
                    double x = arg.eval(ctx);
                    if (!Double.isFinite(x)) return Double.NaN;
                    int i = (int) (count % n);
                    double out = count >= n ? ring[i] : Double.NaN;   // the value n accepted samples ago
                    ring[i] = x;
                    count++;
                    return out;
                }
            };
            case "delta" -> new Node() {
                private double prev = Double.NaN;
                @Override public double eval(EvalContext ctx) {
                    double x = arg.eval(ctx);
                    if (!Double.isFinite(x)) return Double.NaN;   // prev is kept — a gap widens the step
                    double out = x - prev;                        // NaN until the second accepted sample
                    prev = x;
                    return out;
                }
            };
            case "mean", "sum" -> new Node() {
                private final double[] ring = new double[n];
                private long count;
                private double runningSum;
                private final boolean mean = c.fn().equals("mean");
                @Override public double eval(EvalContext ctx) {
                    double x = arg.eval(ctx);
                    if (Double.isFinite(x)) {
                        int i = (int) (count % n);
                        if (count >= n) runningSum -= ring[i];
                        ring[i] = x;
                        runningSum += x;
                        count++;
                    }
                    if (count < n) return Double.NaN;             // under-filled → no point
                    return mean ? runningSum / n : runningSum;
                }
            };
            case "rollingMin", "rollingMax" -> new Node() {
                // monotonic deque of {sampleIndex, value} — O(1) amortised at any window size
                private final java.util.ArrayDeque<double[]> deque = new java.util.ArrayDeque<>();
                private long count;
                private final boolean min = c.fn().equals("rollingMin");
                @Override public double eval(EvalContext ctx) {
                    double x = arg.eval(ctx);
                    if (Double.isFinite(x)) {
                        long idx = count++;
                        while (!deque.isEmpty()
                                && (min ? deque.peekLast()[1] >= x : deque.peekLast()[1] <= x)) {
                            deque.pollLast();
                        }
                        deque.addLast(new double[]{idx, x});
                        while (deque.peekFirst()[0] <= idx - n) deque.pollFirst();
                    }
                    if (count < n) return Double.NaN;
                    return deque.peekFirst()[1];
                }
            };
            default -> ctx -> Double.NaN;
        };
    }
}
