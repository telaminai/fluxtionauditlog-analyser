package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A tiny, hermetic arithmetic expression over node-log references (spec-graph-artifacts §B; conditionals
 * per spec-expr-conditionals-windows M28.1) — the engine behind derived graph series
 * (`askMakerOrder.price − bidMakerOrder.price`). Deliberately <b>not</b> a scripting engine: references,
 * numeric literals, {@code + − × ÷}, comparisons ({@code > < >= <= == !=} → {@code 1.0}/{@code 0.0}),
 * parentheses and a small function set ({@code abs}/{@code min}/{@code max}/{@code if}/{@code and}/
 * {@code or}/{@code not}) only. No {@code eval}, no dependency — matching the project's
 * bespoke-{@code Json} ethos — so a formula is safe to run, serializable, and portable (it can travel to a
 * Grafana transform, M11).
 *
 * <p>References resolve to a {@link GraphKey} at parse time against the set of known keys; an unknown key
 * is a parse error that <b>names the ref</b> (and suggests the nearest known key) so the author — usually
 * an LLM — can fix it in one round. At eval time a missing ref, or division by zero, yields {@code NaN}
 * (the derived point is then omitted — the existing "NaN is no data point" semantics).
 */
public sealed interface Expr permits Expr.Num, Expr.Ref, Expr.Neg, Expr.Bin, Expr.Cmp, Expr.Call {

    /** Evaluate; missing ref → {@code NaN}; any {@code NaN} operand propagates. */
    double eval(Map<GraphKey, Double> refValues);

    /** The {@link GraphKey}s this expression references (so the extractor knows what to resolve). */
    default Set<GraphKey> refs() {
        Set<GraphKey> out = new LinkedHashSet<>();
        collectRefs(out);
        return out;
    }

    void collectRefs(Set<GraphKey> out);

    // ---- AST ------------------------------------------------------------------------------------

    record Num(double value) implements Expr {
        @Override public double eval(Map<GraphKey, Double> v) { return value; }
        @Override public void collectRefs(Set<GraphKey> out) { }
    }

    record Ref(GraphKey key) implements Expr {
        @Override public double eval(Map<GraphKey, Double> v) {
            Double d = v.get(key);
            return d == null ? Double.NaN : d;
        }
        @Override public void collectRefs(Set<GraphKey> out) { out.add(key); }
    }

    record Neg(Expr e) implements Expr {
        @Override public double eval(Map<GraphKey, Double> v) { return -e.eval(v); }
        @Override public void collectRefs(Set<GraphKey> out) { e.collectRefs(out); }
    }

    record Bin(char op, Expr left, Expr right) implements Expr {
        @Override public double eval(Map<GraphKey, Double> v) {
            double a = left.eval(v), b = right.eval(v);
            return switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                case '/' -> b == 0.0 ? Double.NaN : a / b;   // div-by-zero → no point (not ±Inf)
                default -> Double.NaN;
            };
        }
        @Override public void collectRefs(Set<GraphKey> out) { left.collectRefs(out); right.collectRefs(out); }
    }

    /**
     * A comparison — {@code 1.0} when it holds, {@code 0.0} when it does not, NaN when either side is
     * NaN (unknown stays unknown; {@code if} then yields NaN and the point is omitted).
     */
    record Cmp(String op, Expr left, Expr right) implements Expr {
        @Override public double eval(Map<GraphKey, Double> v) {
            double a = left.eval(v), b = right.eval(v);
            if (Double.isNaN(a) || Double.isNaN(b)) return Double.NaN;
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
        }
        @Override public void collectRefs(Set<GraphKey> out) { left.collectRefs(out); right.collectRefs(out); }
    }

    record Call(String fn, List<Expr> args) implements Expr {
        @Override public double eval(Map<GraphKey, Double> v) {
            // EVERY argument is evaluated eagerly — including if()'s untaken branch. No side effects
            // exist today, and rolling windows (M28.2+) must see a deterministic sample stream
            // regardless of which branch wins: a lazily-skipped window would go cold and emit NaN
            // for its full length after every branch switch. Do not "optimise" this to short-circuit.
            return switch (fn) {
                case "abs" -> Math.abs(args.get(0).eval(v));
                case "if" -> {
                    double c = args.get(0).eval(v);
                    double then = args.get(1).eval(v);
                    double els = args.size() > 2 ? args.get(2).eval(v) : Double.NaN;   // 2-arg: else = no point
                    yield Double.isNaN(c) ? Double.NaN : (c != 0.0 ? then : els);
                }
                case "and" -> {
                    double r = 1.0;
                    for (Expr a : args) {
                        double x = a.eval(v);
                        if (Double.isNaN(x)) r = Double.NaN;             // NaN poisons, but keep evaluating
                        else if (x == 0.0 && !Double.isNaN(r)) r = 0.0;
                    }
                    yield r;
                }
                case "or" -> {
                    double r = 0.0;
                    for (Expr a : args) {
                        double x = a.eval(v);
                        if (Double.isNaN(x)) r = Double.NaN;
                        else if (x != 0.0 && !Double.isNaN(r)) r = 1.0;
                    }
                    yield r;
                }
                case "not" -> {
                    double x = args.get(0).eval(v);
                    yield Double.isNaN(x) ? Double.NaN : (x == 0.0 ? 1.0 : 0.0);
                }
                case "min" -> {
                    double m = Double.POSITIVE_INFINITY;
                    for (Expr a : args) m = Math.min(m, a.eval(v));
                    yield m;
                }
                case "max" -> {
                    double m = Double.NEGATIVE_INFINITY;
                    for (Expr a : args) m = Math.max(m, a.eval(v));
                    yield m;
                }
                default -> Double.NaN;
            };
        }
        @Override public void collectRefs(Set<GraphKey> out) { for (Expr a : args) a.collectRefs(out); }
    }

    // ---- parsing --------------------------------------------------------------------------------

    Set<String> FUNCTIONS = Set.of("abs", "min", "max", "if", "and", "or", "not");

    /** Parse {@code source}; references resolve against {@code known}. Throws {@link IllegalArgumentException}
     *  (message names the failing ref / points at the offending token) so the caller can surface ok:false. */
    static Expr parse(String source, Set<GraphKey> known) {
        return new Parser(source, known).parseTop();
    }

    /**
     * Parse without a known-key set — references become {@link GraphKey}s by splitting {@code instanceId.key}
     * (only <b>syntax</b> errors throw; a ref that doesn't exist in the log simply yields no points at eval).
     * Used for extraction and the UI, where the log is scanned in full anyway; the {@code known}-set overload
     * is for the assistant path, where an unknown ref should be reported back with a suggestion.
     */
    static Expr parse(String source) {
        return new Parser(source, null).parseTop();
    }

    /** Recursive-descent parser (two precedence levels); tolerant of ASCII and Unicode operators. */
    final class Parser {
        private final String src;
        private final Set<GraphKey> known;
        private final List<Tok> toks;
        private int pos;

        Parser(String source, Set<GraphKey> known) {
            this.src = source == null ? "" : source;
            this.known = known;   // null → resolve refs by splitting instanceId.key (no validation)
            this.toks = lex(this.src);
        }

        Expr parseTop() {
            Expr e = parseComparison();
            Tok t = peek();
            if (t.type != T.END) throw err("unexpected '" + t.text + "' at position " + t.at);
            return e;
        }

        private static final Set<String> CMP_OPS = Set.of(">", "<", ">=", "<=", "==", "!=");

        /** One comparison, lowest precedence, deliberately NON-chaining: {@code a < b < c} compares a
         *  boolean to a value and never means what it reads as — combine with {@code and(...)}. */
        private Expr parseComparison() {
            Expr e = parseAdditive();
            if (peek().type == T.OP && CMP_OPS.contains(peek().text)) {
                String op = next().text;
                e = new Cmp(op, e, parseAdditive());
                if (peek().type == T.OP && CMP_OPS.contains(peek().text)) {
                    throw err("chained comparisons are not supported — write and(a " + op + " b, ...) instead");
                }
            }
            return e;
        }

        private Expr parseAdditive() {
            Expr e = parseMultiplicative();
            while (peek().type == T.OP && (peek().text.equals("+") || peek().text.equals("-"))) {
                char op = next().text.charAt(0);
                e = new Bin(op, e, parseMultiplicative());
            }
            return e;
        }

        private Expr parseMultiplicative() {
            Expr e = parseUnary();
            while (peek().type == T.OP && (peek().text.equals("*") || peek().text.equals("/"))) {
                char op = next().text.charAt(0);
                e = new Bin(op, e, parseUnary());
            }
            return e;
        }

        private Expr parseUnary() {
            if (peek().type == T.OP && peek().text.equals("-")) { next(); return new Neg(parseUnary()); }
            if (peek().type == T.OP && peek().text.equals("+")) { next(); return parseUnary(); }
            return parsePrimary();
        }

        private Expr parsePrimary() {
            Tok t = peek();
            switch (t.type) {
                case NUMBER -> { next(); return new Num(Double.parseDouble(t.text)); }
                case LPAREN -> {
                    next();
                    Expr e = parseComparison();
                    expect(T.RPAREN, ")");
                    return e;
                }
                case IDENT -> {
                    if (FUNCTIONS.contains(t.text) && peekAt(1).type == T.LPAREN) return parseCall();
                    next();
                    return resolveRef(t);
                }
                case REF -> { next(); return resolveRef(t); }   // backtick-quoted ref
                case END -> throw err("unexpected end of expression");
                default -> throw err("unexpected '" + t.text + "' at position " + t.at);
            }
        }

        private Expr parseCall() {
            Tok name = next();                 // the function IDENT
            expect(T.LPAREN, "(");
            List<Expr> args = new ArrayList<>();
            if (peek().type != T.RPAREN) {
                args.add(parseComparison());
                while (peek().type == T.COMMA) { next(); args.add(parseComparison()); }
            }
            expect(T.RPAREN, ")");
            int n = args.size();
            if (name.text.equals("abs") && n != 1) throw err("abs() takes exactly 1 argument, got " + n);
            if ((name.text.equals("min") || name.text.equals("max")) && n < 1) {
                throw err(name.text + "() takes at least 1 argument");
            }
            if (name.text.equals("if") && n != 2 && n != 3) {
                throw err("if() takes (condition, then) or (condition, then, else), got " + n + " argument(s)");
            }
            if ((name.text.equals("and") || name.text.equals("or")) && n < 2) {
                throw err(name.text + "() takes at least 2 arguments, got " + n);
            }
            if (name.text.equals("not") && n != 1) throw err("not() takes exactly 1 argument, got " + n);
            return new Call(name.text, args);
        }

        private Expr resolveRef(Tok t) {
            if (known == null) {   // no validation — build the key by splitting instanceId.key
                GraphKey k = GraphKey.fromDisplay(t.text);
                if (k == null) throw err("'" + t.text + "' is not a key reference (expected instanceId.key)");
                return new Ref(k);
            }
            for (GraphKey k : known) if (k.display().equals(t.text)) return new Ref(k);
            String hint = nearest(t.text);
            throw new IllegalArgumentException("unknown key '" + t.text + "' in expression \"" + src + "\""
                    + (hint == null ? "" : " (did you mean '" + hint + "'?)"));
        }

        /** Nearest known key by edit distance, if close enough to be a plausible typo. */
        private String nearest(String ref) {
            String best = null;
            int bestD = Integer.MAX_VALUE;
            for (GraphKey k : known) {
                int d = editDistance(ref, k.display());
                if (d < bestD) { bestD = d; best = k.display(); }
            }
            return best != null && bestD <= Math.max(2, ref.length() / 4) ? best : null;
        }

        private Tok peek() { return toks.get(pos); }
        private Tok peekAt(int ahead) { return toks.get(Math.min(pos + ahead, toks.size() - 1)); }
        private Tok next() { return toks.get(pos++); }

        private void expect(T type, String what) {
            Tok t = peek();
            if (t.type != type) {
                throw err("expected '" + what + "' but found '" + (t.type == T.END ? "end" : t.text)
                        + "' at position " + t.at);
            }
            next();
        }

        private IllegalArgumentException err(String msg) {
            return new IllegalArgumentException(msg + " in expression \"" + src + "\"");
        }
    }

    // ---- lexer ----------------------------------------------------------------------------------

    enum T { NUMBER, IDENT, REF, OP, LPAREN, RPAREN, COMMA, END }

    record Tok(T type, String text, int at) { }

    private static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                out.add(new Tok(T.NUMBER, s.substring(i, j), i));
                i = j;
            } else if (isIdentStart(c)) {
                int j = i;
                // maximal dotted-identifier run: ident ('.' ident)*
                while (true) {
                    while (j < n && isIdentPart(s.charAt(j))) j++;
                    if (j + 1 < n && s.charAt(j) == '.' && isIdentStart(s.charAt(j + 1))) j++;   // consume the dot
                    else break;
                }
                out.add(new Tok(T.IDENT, s.substring(i, j), i));
                i = j;
            } else if (c == '`') {                         // backtick-quoted ref (escape hatch for odd keys)
                int j = s.indexOf('`', i + 1);
                if (j < 0) throw new IllegalArgumentException("unterminated `ref` at position " + i + " in \"" + s + "\"");
                out.add(new Tok(T.REF, s.substring(i + 1, j), i));
                i = j + 1;
            } else if (c == '>' || c == '<') {
                boolean eq = i + 1 < n && s.charAt(i + 1) == '=';
                out.add(new Tok(T.OP, eq ? c + "=" : String.valueOf(c), i));
                i += eq ? 2 : 1;
            } else if (c == '≥') { out.add(new Tok(T.OP, ">=", i)); i++; }
            else if (c == '≤') { out.add(new Tok(T.OP, "<=", i)); i++; }
            else if (c == '≠') { out.add(new Tok(T.OP, "!=", i)); i++; }
            else if (c == '=' || c == '!') {
                if (i + 1 < n && s.charAt(i + 1) == '=') { out.add(new Tok(T.OP, c + "=", i)); i += 2; }
                else throw new IllegalArgumentException("single '" + c + "' is not an operator — did you "
                        + "mean '" + c + "='? at position " + i + " in \"" + s + "\"");
            } else {
                char op = normalizeOp(c);
                switch (op) {
                    case '+', '-', '*', '/' -> out.add(new Tok(T.OP, String.valueOf(op), i));
                    case '(' -> out.add(new Tok(T.LPAREN, "(", i));
                    case ')' -> out.add(new Tok(T.RPAREN, ")", i));
                    case ',' -> out.add(new Tok(T.COMMA, ",", i));
                    default -> throw new IllegalArgumentException(
                            "unexpected character '" + c + "' at position " + i + " in \"" + s + "\"");
                }
                i++;
            }
        }
        out.add(new Tok(T.END, "", n));
        return out;
    }

    /** Map Unicode math operators to their ASCII form — an LLM will emit −(U+2212), ×, ÷ (spec-graph-artifacts §B.1). */
    private static char normalizeOp(char c) {
        return switch (c) {
            case '−' -> '-';   // minus sign
            case '×', '∗' -> '*';   // × , ∗
            case '÷' -> '/';   // ÷
            default -> c;
        };
    }

    private static boolean isIdentStart(char c) { return Character.isLetter(c) || c == '_'; }
    private static boolean isIdentPart(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
