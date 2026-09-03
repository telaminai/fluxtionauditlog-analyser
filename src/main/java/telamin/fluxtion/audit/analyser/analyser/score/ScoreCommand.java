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
 * <pre>score &lt;expected.log&gt; &lt;actual.log&gt;</pre>
 *
 * <p>Reads through the shipped {@link AuditLogReader} and {@link RecordParser} rather than a
 * bespoke parser — the reason being that every hand-rolled comparison in this project's experiment
 * history was wrong, and the reader is the component with a format conformance suite behind it.
 *
 * <p>Exit codes: {@code 0} pass · {@code 1} differences found · {@code 2} the comparison could not
 * be trusted (see {@link ExpectationScorer} guards) · {@code 3} usage or I/O error.
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

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: score <expected.log> <actual.log> [maxDifferencesShown]");
            System.exit(3);
        }
        int show = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        ExpectationScorer scorer = new ExpectationScorer();
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
