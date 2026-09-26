import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.ui.*;
import telamin.fluxtion.audit.analyser.analyser.filter.*;

/** Constructed review cases, run against the unmodified packaged subject. No client session. */
public class ReviewProbe {
    static final String RECORD = "eventLogRecord:\n  event: Tick\n  logTime: 1\n  nodeLogs:\n    - rootNode: {v: 1}\n---\n";
    static SessionDriver pair() {
        SessionDriver d = new SessionDriver(e -> {
            if (e instanceof SessionEffects.OpenLogEffect) return new SessionEvents.Pending(e.opId(), "openLog");
            return new SessionEvents.StatusShown(e.opId(), "shown");
        });
        long id=d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(id,"demo.log",null,"DECLARED",false));
        d.submit(new SessionEvents.LogOpened(id,"demo.log","DECLARED",Set.of("rootNode"),1,1,"TRACE"));
        d.post(new SessionEvents.GraphOpened("demo.graphml","OPENED",Set.of("rootNode"),List.of("EventLogManager")));
        d.post(new SessionEvents.MembershipCompared(d.snapshot().logGeneration(),d.snapshot().graphRevision(),
            Map.of("scope","whole log","recordsScanned",1,"logRecords",1,
                "loggedButNotInTopology",List.of("foreign"),
                "membership",Map.of("scope","whole log","established",true,"loggedIds",2,"declaredOfLogged",1))));
        return d;
    }
    public static void main(String[] args) throws Exception {
        var d=pair(); var snap=d.snapshot();
        System.out.println("snapshot.before="+snap.qualifications().isEmpty());
        snap.qualifications().clear();
        System.out.println("snapshot.after="+d.snapshot().qualifications().isEmpty()+", nodeStillQualified="+!d.processor().pairingQualifier.qualifications().isEmpty());
        var delivered=new ArrayList<String>(); var once=new AtomicBoolean();
        var d2=pair();
        d2.onSnapshot(s->{if(once.compareAndSet(false,true))d2.post(new SessionEvents.ViewFilterChanged("second"));});
        d2.onSnapshot(s->delivered.add(s.filterKey()));
        d2.post(new SessionEvents.ViewFilterChanged("first"));
        System.out.println("listener.order="+delivered+", final="+d2.snapshot().filterKey());
        SwingUtilities.invokeAndWait(()->{
            var panel=new TopologyPanel();
            panel.load(Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml"));
            var store=new HeapLogStore(RECORD); var filter=new FilterState(); var tabs=new GraphTabs(); tabs.bind(store,filter);
            var table=new LogTablePanel(); table.setModel(new LogTableModel(store));
            var ex=new ActionExecutor(()->store,()->filter,tabs,table,(r,n,f,k)->{}); ex.bind(panel,null);
            System.out.println("topology.before="+panel.cursorState().get("selected"));
            var reply=ex.render("topology",Map.of("select","rootNode","saveFocusAs","demo"));
            System.out.println("topology.reply="+reply.toMap()+", selectedAfter="+panel.cursorState().get("selected"));
        });
        Path dir=Files.createTempDirectory("m44-probe-"); Path a=dir.resolve("a.yaml"),b=dir.resolve("b.yaml");
        Files.writeString(a,RECORD); Files.writeString(b,RECORD);
        try(var mapped=new MappedLogStore(a); var rolled=RolledLogStore.open(List.of(a,b),0)) {
            FileTime before=Files.getLastModifiedTime(a);
            Files.writeString(a,RECORD.replace("v: 1","v: 9")); Files.setLastModifiedTime(a,FileTime.fromMillis(before.toMillis()+2000));
            System.out.println("mapped.changed="+mapped.readThroughIdentity()+", rolled.changed="+rolled.readThroughIdentity());
            System.out.println("rolled.rawChanged="+rolled.rawText(0).contains("v: 9"));
        }
        // A BOM at the first physical line is valid on the existing reader. It must not hide a later header.
        String collapse="\ufeff"+RECORD.replace("---\n","")+RECORD.replace("---\n","");
        System.out.println("framing.bomCollapse="+FramingScan.of(collapse,100000).candidates());
        System.out.println("framing.plainCollapse="+FramingScan.of(collapse.substring(1),100000).candidates());
        var collapsedStore=new HeapLogStore(collapse);
        System.out.println("framing.bomFindings="+ProducerDiagnostics.of(collapsedStore.index(),collapsedStore::rawText).findings());
        String large="eventLogRecord:\n  message: "+"x".repeat((1<<20)+10)+"\n";
        var empty=new HeapLogStore("");
        System.out.println("framing.pendingLarge="+ProducerDiagnostics.of(empty.index(),empty::rawText,List.of(),List.of(),false,large).findings());
        var largeStore=new HeapLogStore(large);
        System.out.println("framing.staticLarge="+ProducerDiagnostics.of(largeStore.index(),largeStore::rawText).findings());
        Files.delete(a); Files.delete(b); Files.delete(dir);
        String address=SpotlightTarget.graphAddress("saved\"chart");
        System.out.println("savedQuote.address="+address+", parses="+SpotlightTarget.parse(address).ok());
        System.exit(0);
    }
}
