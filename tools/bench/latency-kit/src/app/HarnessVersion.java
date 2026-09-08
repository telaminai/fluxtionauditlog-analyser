package app;

/**
 * The harness's own version, printed on every RESULT line.
 *
 * <p>Round 63 measured the same 30-node graph at 3.41 ns in one session and 9.7 ns in another. Neither
 * number was wrong and no flag had changed — <b>the harness had</b>. The newer one constructed the
 * processor in {@code main} and passed it into the loop method, so it escaped and its nodes could no
 * longer be scalar-replaced: 2.26 ns against 9.79, a 4.3× difference invisible in every recorded input.
 *
 * <p>The harness is therefore an input like any other, and it is versioned like one. Bump
 * {@link #VERSION} whenever a change could move a number — construction site, loop shape, what is timed,
 * what is asserted — and say why in {@link #CHANGES}. A result carrying a version can be compared; one
 * that does not, cannot.
 */
public final class HarnessVersion {

    /** Bump on ANY change that could move a measurement. */
    public static final String VERSION = "4";

    /** What each version changed, newest first. */
    public static final String[] CHANGES = {
            "4 - adds record=noop, a LogRecord that writes nothing, so audit DISPATCH can be separated "
                    + "from record BUILDING. Adds one reachable LogRecord subclass, which is why every "
                    + "arm must be re-measured under h4 rather than compared to an h3 number",
            "3 - processor constructed inside the loop method and never escaping (the documented "
                    + "runtime shape); worth 4.3x on native, nothing on JIT",
            "2 - refuses to report unless it can prove what it measured: -D placement fatal, resolved "
                    + "clock cross-checked, sink records asserted non-zero, graph checksum compared",
            "1 - original; -D after the main class silently ignored, arms not interleaved",
    };

    private HarnessVersion() {
    }

    public static String tag() {
        return "h" + VERSION;
    }
}
