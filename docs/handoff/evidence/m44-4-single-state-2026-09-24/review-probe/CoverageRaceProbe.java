import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.ui.*;
import telamin.fluxtion.audit.analyser.analyser.filter.*;
import telamin.fluxtion.audit.analyser.analyser.llm.*;
/** Deterministic scheduling of a log arrival between store capture and snapshot capture. */
@SuppressWarnings("unchecked")
public class CoverageRaceProbe {
    static String record(String node) { return "eventLogRecord:\n  event: Tick\n  logTime: 1\n  nodeLogs:\n    - "+node+": {v: 1}\n---\n"; }
    static void open(SessionDriver d,String path,int total) {
        long id=d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(id,path,null,"DECLARED",false));
        d.submit(new SessionEvents.LogOpened(id,path,"DECLARED",Set.of("rootNode"),total,total,"TRACE"));
    }
    public static void main(String[] args) throws Exception {
        var captured=new CountDownLatch(1); var release=new CountDownLatch(1);
        var old=new HeapLogStore(record("foreignOldLog")); var fresh=new HeapLogStore(record("rootNode")+record("rootNode"));
        var current=new AtomicReference<LogStore>(old); var driver=new AtomicReference<SessionDriver>();
        var executor=new AtomicReference<ActionExecutor>(); var reply=new AtomicReference<ActionResult>();
        SwingUtilities.invokeAndWait(()->{
            var d=new SessionDriver(e->e instanceof SessionEffects.OpenLogEffect ? new SessionEvents.Pending(e.opId(),"openLog") : new SessionEvents.StatusShown(e.opId(),"shown")); driver.set(d);
            open(d,"old.log",1);
            var p=new TopologyPanel(); p.load(Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml"));
            d.post(new SessionEvents.GraphOpened("demo.graphml","OPENED",Set.of("rootNode"),List.of("EventLogManager")));
            var filter=new FilterState(); var tabs=new GraphTabs(); tabs.bind(old,filter);
            var ex=new ActionExecutor(()->{
                var got=current.get(); captured.countDown();
                try {if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("probe timeout");}catch(InterruptedException e){throw new RuntimeException(e);}return got;
            },()->filter,tabs,new LogTablePanel(),(r,n,f,k)->{});
            AppControl app=(AppControl)Proxy.newProxyInstance(AppControl.class.getClassLoader(),new Class[]{AppControl.class},(o,m,a)->{
                if(m.getName().equals("qualifyPublishedPairing")) {
                    Map<String,Object> echo=(Map<String,Object>)a[0];
                    d.post(new SessionEvents.MembershipCompared((Long)echo.get(ActionExecutor.PAIR_LOG_GENERATION),(Long)echo.get(ActionExecutor.PAIR_GRAPH_REVISION),echo));
                    return d.processor().pairingQualifier.lastSaid();
                }
                if(m.isDefault())return InvocationHandler.invokeDefault(o,m,a);
                return null;
            });
            ex.bind(p,app); ex.bindSessionSnapshot(d::snapshot); executor.set(ex);
        });
        Thread scan=new Thread(()->reply.set(executor.get().render("coverage",Map.of()))); scan.start();
        if(!captured.await(10,TimeUnit.SECONDS))throw new IllegalStateException("capture never reached");
        SwingUtilities.invokeAndWait(()->{current.set(fresh);open(driver.get(),"new.log",2);});
        release.countDown(); scan.join(10000); if(scan.isAlive())throw new IllegalStateException("scan did not finish");
        System.out.println("coverage.ok="+reply.get().ok()+", result="+reply.get().payload().get("loggedButNotInTopology"));
        var s=driver.get().snapshot();
        System.out.println("current.total="+s.total()+", current.generation="+s.logGeneration()+", qualification="+s.qualifications().toMap(s.total(),s.filterKey()));
        System.exit(0);
    }
}
