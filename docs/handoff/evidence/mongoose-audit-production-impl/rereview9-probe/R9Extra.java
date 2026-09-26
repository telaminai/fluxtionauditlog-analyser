import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.topology.PerNodeLevelChanges;
/** Constructed review cases; no runtime/provider generation. Extends the preserved R8 probe helpers. */
public class R9Extra extends R8Review {
    public static void main(String[] args) {
        String warn=ctl(1,"null","WARN","riskMonitor","null");
        String emptyMarker=MARKER.replace("streamEndRecords: 1", "streamEndRecords: 0");
        for (int count:new int[]{2,3}) {
            HeapLogStore s=new HeapLogStore(warn+MARKER+emptyMarker.repeat(count-1)+row(2,"null"));
            System.out.println("== adjacentMarkers="+count+", boundaries="+s.runBoundaries());
            System.out.println("   "+PerNodeLevelChanges.of(s).annotationFor("riskMonitor",new int[]{1}));
        }
        String separate=warn+MARKER+row(2,"null")+MARKER+row(3,"null")+MARKER+row(4,"null")
            +ctl(5,"null","INFO","riskMonitor","null");
        show("three separated markers; only record 2 is in the annotation's window",separate,1,2,3);
        show("three separated markers; view only beyond the second",separate,2,3);
        show("only in-window record sits between markers; earlier record is also in view",separate,0,1);
        show("second marker before closer, all records in view",warn+row(2,"null")+MARKER+row(3,"null")+MARKER
            +row(4,"null")+ctl(5,"null","INFO","riskMonitor","null"),1,2,3);
        show("second marker inside closed window; view only before it",warn+row(2,"null")+MARKER+row(3,"null")+MARKER
            +row(4,"null")+unread(5,"null"),2);
        for(String kind:new String[]{"standard","eventTime","commentBlank","streamEnd","timeAfterPayload","untimed"}) {
            String close=ctl(3,"null","INFO","riskMonitor","null");
            close=switch(kind) {
                case "eventTime" -> close.replace("  logTime: 3", "  eventTime: -1\n  logTime: 3");
                case "commentBlank" -> close.replace("  logTime: 3", "# header comment\n\n  logTime: 3");
                case "streamEnd" -> "eventLogRecord:\n  logTime: 3\n  groupingId: null\n  streamEnd: normal\n  event: EventLogControlEvent\n---\n";
                case "timeAfterPayload" -> close.replace("  logTime: 3\n", "").replace("  nodeLogs:","  logTime: 3\n  nodeLogs:");
                case "untimed" -> close.replace("  logTime: 3\n", "");
                default -> close;
            };
            show("null record, header "+kind,"riskMonitor",new NullingStore(warn+row(2,"null")+close+row(4,"null"),Set.of(2)),1,3);
        }
        show("spanning, absent grouping, unreadable closer",ctl(1,ABSENT,"WARN","null","null")+row(2,ABSENT)
            +MARKER+row(3,ABSENT)+unread(4,ABSENT)+row(5,ABSENT),1,2,4);
        show("spanning, non-applicable closer",ctl(1,"alpha","WARN","riskMonitor","alpha")+row(2,"alpha")
            +MARKER+row(3,"alpha")+ctl(4,"alpha","INFO","riskMonitor","beta")+row(5,"alpha"),1,2,4);
    }
}
