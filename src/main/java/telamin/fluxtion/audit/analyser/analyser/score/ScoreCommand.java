package telamin.fluxtion.audit.analyser.analyser.score;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.RecordParser;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.spi.YamlAuditReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless comparison of two audit logs on business outcomes.
 *
 * <pre>score &lt;expected.log&gt; &lt;actual.log&gt; [maxDifferencesShown] [natural|tagged]</pre>
 *
 * <p>The dialect is DECLARED, never inferred — see {@link ExpectationScorer}. It defaults to
 * {@code natural}, which is the published record format.
 *
 * <p>Reads through the shipped {@link AuditLogReader} and {@link RecordParser} rather than a
 * bespoke parser — the reason being that every hand-rolled comparison in this project's experiment
 * history was wrong, and the reader is the component with a format conformance suite behind it.
 *
 * <p>Exit codes: {@code 0} pass · {@code 1} differences found · {@code 2} the comparison could not
 * be trusted (a guard fired, or a record was malformed under the declared dialect) · {@code 3} usage
 * or I/O error. These are distinct because "I could not compare these" and "these differ" call for
 * different actions, and an uncaught exception previously collapsed the first into the second.
 */
public final class ScoreCommand {

    private ScoreCommand() {}

    public static List<LogRecord> read(Path source) throws Exception {
        AuditLogReader reader = new YamlAuditReader();
        List<LogRecord> out = new ArrayList<>();
        long[] offset = {0};
        reader.read(source, text -> out.add(RecordParser.parse(text, offset[0]++)));
        return out;
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (ExpectationScorer.MalformedRecordException malformed) {
            // A record that cannot be reduced under the declared dialect is an UNTRUSTWORTHY
            // comparison, not a failing one — the same class as the ExpectationScorer guards.
            System.err.println("UNTRUSTWORTHY — " + malformed.getMessage());
            System.exit(2);
        } catch (Exception io) {
            // Documented as exit 3. Previously these propagated and the JVM exited 1, which is
            // indistinguishable from "the comparison found differences".
            System.err.println("error: " + io);
            System.exit(3);
        }
    }

    static void run(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: score <expected.log> <actual.log> [maxDifferencesShown] [natural|tagged]");
            System.exit(3);
        }
        int show;
        ExpectationScorer.Dialect dialect;
        try {
            show = args.length > 2 ? Integer.parseInt(args[2]) : 10;
            dialect = args.length > 3
                    ? ExpectationScorer.Dialect.valueOf(args[3].toUpperCase())
                    : ExpectationScorer.Dialect.NATURAL;
        } catch (IllegalArgumentException bad) {          // usage error, not a comparison failure
            System.err.println("bad argument: " + bad.getMessage());
            System.exit(3);
            return;
        }
        ExpectationScorer scorer = new ExpectationScorer(dialect);
        var expected = scorer.snapshots(read(Path.of(args[0])));
        var actual = scorer.snapshots(read(Path.of(args[1])));

        System.out.printf("  expected: %d scored events, %d figures%n",
                expected.size(), ExpectationScorer.figuresIn(expected).size());
        System.out.printf("  actual  : %d scored events, %d figures%n",
                actual.size(), ExpectationScorer.figuresIn(actual).size());

        var result = scorer.score(expected, actual);
        System.out.println("  " + result.summary());
        result.differences().stream().limit(show).forEach(d -> System.out.println("      " + d));
        if (result.differences().size() > show) {
            System.out.printf("      … and %d more%n", result.differences().size() - show);
        }
        System.exit(!result.trustworthy() ? 2 : result.pass() ? 0 : 1);
    }
}
