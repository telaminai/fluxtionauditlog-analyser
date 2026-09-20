import java.nio.file.*;
import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.score.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.diff.DiffBuilder;

public class ComparisonScopeProbe {
  public static void main(String[] args) throws Exception {
    var expected=ScoreCommand.read(Path.of(args[0]));
    String raw=Files.readString(Path.of(args[0]));
    // Deliberately alter only a price-event identity; default scorer excludes this event class.
    String changed=raw.replace("event: PriceUpdate\n", "event: ChangedPriceUpdate\n");
    if (changed.equals(raw)) throw new IllegalStateException("mutation did not match");
    var actualStore=new HeapLogStore(changed);
    var actual=new ArrayList<telamin.fluxtion.audit.analyser.analyser.model.LogRecord>();
    for(int i=0;i<actualStore.size();i++) actual.add(actualStore.record(i));
    var scorer=new ExpectationScorer();
    var e=scorer.snapshots(expected);
    var a=scorer.snapshots(actual);
    System.out.println("source records="+expected.size()+", default scored events="+e.size());
    System.out.println("All PriceUpdate event names changed: "+scorer.score(e,a).summary());
    String header="eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n";
    var left=RecordParser.parse(header+"    - demo: {value: 1}\n    - demo: {value: 2}\n",0);
    var right=RecordParser.parse(header+"    - demo: {value: 2}\n",0);
    System.out.println("Repeated writes [1,2] vs [2], record diff differences="+DiffBuilder.diff(left,right).stream().filter(DiffBuilder.DiffRow::isDifference).count());
    System.out.println("These are documented comparison scopes, not a whole-run equivalence verdict.");
  }
}
