package telamin.fluxtion.audit.analyser.analyser.ui;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import telamin.fluxtion.audit.analyser.analyser.config.FocusSpec;
import telamin.fluxtion.audit.analyser.analyser.topology.FocusStack;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.filter.*;
import telamin.fluxtion.audit.analyser.analyser.graph.*;
/** Constructed, bounded N1/N2 review cases against the packaged production classes. */
public class FocusedProbe {
 static int assertions;
 static void check(boolean value,String label) {if(!value)throw new AssertionError(label); assertions++;System.out.println("PASS "+label);}
 static Object field(Object o,String n) {try{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
 static List<FocusStack.Context> contexts(TopologyPanel p){return ((FocusStack)field(p,"focusStack")).contextsOldestFirst();}
 static String rec(int t,String nodes){return "eventLogRecord:\n  event: Tick\n  logTime: "+t+"\n  nodeLogs:\n"+nodes+"---\n";}
 static class H {
  TopologyPanel p=new TopologyPanel(); ArrayList<FocusSpec> saved=new ArrayList<>(); AtomicInteger notifications=new AtomicInteger(); ActionExecutor ex;
  H(){p.load(Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml"));
   p.bindNamedFocuses(()->saved,notifications::incrementAndGet);var s=new HeapLogStore(rec(1000,"    - rootNode: {v: 1}\n"));var f=new FilterState();var tabs=new GraphTabs();tabs.bind(s,f);var table=new LogTablePanel();table.setModel(new LogTableModel(s));ex=new ActionExecutor(()->s,()->f,tabs,table,(r,n,fl,k)->{});ex.bind(p,null);
   p.selectNode("rootNode");p.setFocus(true);
  }
  void refused(Map<String,Object> call,String label){var before=new LinkedHashMap<>(p.cursorState());var stack=contexts(p);var defs=List.copyOf(saved);int n=notifications.get();var result=ex.render("topology",call);
   check(!result.ok(),label+" refused");check(before.equals(p.cursorState()),label+" cursor unchanged");check(stack.equals(contexts(p)),label+" contexts/labels unchanged");check(defs.equals(saved)&&n==notifications.get(),label+" saved definitions/notifications unchanged");System.out.println("REFUSAL "+result.toMap());}
 }
 public static void main(String[] a)throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var h=new H();h.refused(Map.of("showAll",true,"saveFocusAs","review-focus","source",true,"callout",true),"original showAll/save plus later visibility fields");
   check(field(h.p,"embeddedSource")==null,"refused call did not initialise source pane");
   h.refused(Map.of("showAll",true,"pop",1,"saveFocusAs","review-focus"),"showAll plus pop");
   h.saved.add(new FocusSpec("partial","",List.of("rootNode","missing-DEMO")));
   h.refused(Map.of("select","riskCheck","focus","partial","saveFocusAs","copy","orientation","diagonal"),"named partial focus plus select with invalid later field");
   var named=h.ex.render("topology",Map.of("select","riskCheck","focus","partial","saveFocusAs","copy"));
   check(named.ok(),"named partial focus plus select saves");check(h.saved.stream().anyMatch(f->f.name().equals("copy")&&f.nodeIds().equals(List.of("rootNode"))),"named recall wins in apply order and saves resolved ids");
   check(!h.p.lastRecallNote().isBlank(),"partial recall disclosed");
   var r=new H();var routes=r.ex.render("topology",Map.of("showAll",true,"select","rootNode","routeBound",false,"scope","routes","focus",true,"saveFocusAs","routes"));
   check(routes.ok(),"routes plus routeBound plus focus saves");check(r.notifications.get()==1,"trial does not save or notify");
   r.p.selectNode("rootNode");r.p.setFocus(true);var trial=r.p.trialCopy();
   check(contexts(r.p).size()==2,"two-level copy fixture");check(contexts(r.p).equals(contexts(trial)),"trial preserves context ids/order/labels");
   check(!((javax.swing.Timer)field(trial,"autoplay")).isRunning(),"trial autoplay is stopped");
   var before=contexts(r.p);trial.showAll();check(before.equals(contexts(r.p)),"trial mutation leaves original stack unchanged");
   var alternating=new HeapLogStore(rec(1000,"    - nodeA: {x: 1}\n")+rec(2000,"    - nodeB: {y: 2}\n")+rec(3000,"    - nodeA: {x: 4}\n"));
   var three=new HeapLogStore(rec(1000,"    - rootNode: {v: 1}\n")+rec(2000,"    - rootNode: {v: 3}\n")+rec(3000,"    - rootNode: {v: 2}\n"));
   for(var call:List.<Map<String,Object>>of(Map.of("expr","nodeA.x + nodeB.y","resolve","STRICT"),Map.of("expr","rootNode.v","filter",Map.of("from",2000,"to",2000)))){
    var store=call.containsKey("resolve")?alternating:three;var verb=SeriesScan.scan(store,call);var picture=ReportSeriesPicture.of(store,call,700,300);int expected=call.containsKey("resolve")?0:1;
    check(((Number)verb.get("points")).intValue()==expected,"verb counterexample count "+expected);check(picture.problem()==null&&picture.caption().contains(" · "+expected+" point"),"report counterexample count "+expected);System.out.println("SERIES "+verb+"\nCAPTION "+picture.caption());
   }
  });
  System.out.println("TOTAL "+assertions+" assertions");System.exit(0);
 }
}
