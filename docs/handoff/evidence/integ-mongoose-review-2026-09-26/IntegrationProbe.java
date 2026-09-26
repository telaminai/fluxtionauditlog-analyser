import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.topology.*;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

/** Constructed integration inputs. Run from the checkout root; no client or external service. */
public class IntegrationProbe {
    static final String RECORD = "eventLogRecord:\n  logTime: 1\n  groupingId: null\n  event: Tick\n  nodeLogs:\n    - quoteHandler: { v: 1}\n---\n";
    static final String MARKER = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";
    static String control(int t,String level) {
        return "eventLogRecord:\n  logTime: "+t+"\n  groupingId: null\n  event: EventLogControlEvent\n  eventToString: EventLogConfig{level="+level+", logRecordProcessor=null, sourceId=riskMonitor, groupId=null}\n  nodeLogs:\n---\n";
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("integration-probe-");
        String pending="eventLogRecord:\n  logTime: 1\neventLogRecord:\n  logTime: 2\n\u2003---\n  tail: x\n";
        Path p=Files.writeString(root.resolve("pending.yaml"),pending);
        HeapLogStore s=HeapLogStore.fromFile(p).forFollow();s.appendFrom(p);
        var d=ProducerDiagnostics.of(s.index(),s::rawText,s.sourceDiagnostics(),s.completenessDiagnostics(),s.completenessIsNote(),s.pendingFrameText());
        System.out.println("pending indexed="+s.size()+" full candidates="+FramingScan.of(pending,10000).candidates()+" adapterText="+s.pendingFrameText().replace("\n","\\n")+" findings="+d.findings());
        Path bad=Files.writeString(root.resolve("bad.yaml"),RECORD+MARKER);
        HeapLogStore b=HeapLogStore.fromFile(bad).forFollow();b.appendFrom(bad);
        byte[] bytes=Files.readAllBytes(bad);bytes[10]=(byte)0xff;Files.write(bad,bytes);
        try { b.appendFrom(bad); } catch(Exception e) { System.out.println("same-length decode error="+e.getClass().getSimpleName()); }
        System.out.println("same-length state="+b.streamEnd().state()+" digests="+b.readIdentities().size()+" identity="+b.followIdentity()+" damage="+b.sourceDiagnostics());
        HeapLogStore inner=new HeapLogStore(control(0,"WARN")+RECORD+control(2,"INFO"));
        List<Integer> reads=new ArrayList<>();
        LogStore bounded=new LogStore(){
            public int size(){return inner.size();} public LogIndex index(){return inner.index();}
            void check(int r){reads.add(r);if(r>=2)throw new AssertionError("outside captured bound: "+r);}
            public LogRecord record(int r){check(r);return inner.record(r);} public String rawText(int r){check(r);return inner.rawText(r);}
            public List<Integer> runBoundaries(){return List.of(2,3);}
            public void close(){}
        };
        var topology=GraphMlParser.parse(Files.readString(Path.of("src/test/resources/topology/demo-quote-processor-noaudit.graphml")));
        var result=CoverageService.assess(bounded,false,null,new CoverageService.Input(topology,Scaffolding.authoredNodes(topology),null),2);
        System.out.println("coverage bound="+result.echo().get("logRecords")+" maxRead="+Collections.max(reads)+" annotations="+result.echo().get("levelAnnotations"));
    }
}
