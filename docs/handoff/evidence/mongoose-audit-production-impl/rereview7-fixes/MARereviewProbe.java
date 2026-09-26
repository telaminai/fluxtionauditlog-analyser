import java.nio.file.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.reflect.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.topology.*;
import com.telamin.fluxtion.runtime.audit.*;
public class MARereviewProbe {
 static class Node implements EventLogSource {EventLogger logger; public void setLogger(EventLogger x){logger=x;}}
 static final Set<Integer> validPrefixes=new HashSet<>();
 static int key(byte[] b,int start,int end){int key=1;for(int i=start;i<end;i++)key=(key<<8)|(b[i]&255);return key;}
 static final String END="eventLogRecord:\n streamEnd: normal\n streamEndRecords: 1\n---\n";
 static String row(int t,String group){return "eventLogRecord:\n logTime: "+t+"\n"+(group==null?"":" groupingId: "+group+"\n")+" event: Tick\n nodeLogs:\n  - priceListener: { seen: true}\n---\n";}
 static String control(int t,String grouping,String source,String addressed,String level){return "eventLogRecord:\n logTime: "+t+"\n"+(grouping==null?"":" groupingId: "+grouping+"\n")+" event: EventLogControlEvent\n eventToString: "+new EventLogControlEvent(source,addressed,EventLogControlEvent.LogLevel.valueOf(level))+"\n---\n";}
 static void annotation(String name,String text,int selected)throws Exception{var s=new HeapLogStore(text);System.out.println(name+" => "+PerNodeLevelChanges.of(s).annotationFor("riskMonitor",new int[]{selected}));
  var topology=GraphMlParser.parse(Files.readString(Path.of("src/test/resources/topology/demo-quote-processor-noaudit.graphml")));
  var filter=new telamin.fluxtion.audit.analyser.analyser.filter.FilterState();filter.setTimeRange(s.record(selected).logTime(),s.record(selected).logTime());
  var result=CoverageService.assess(s,true,filter,new CoverageService.Input(topology,Scaffolding.authoredNodes(topology),null));
  System.out.println("  CoverageService selected="+result.echo().get("recordsScanned")+" ratio="+result.echo().get("ratio")+" annotations="+result.echo().get("levelAnnotations"));
 }
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("ma-rereview-input-");
  Path p=dir.resolve("bad.yaml");Files.writeString(p,row(1,null)+END);var live=HeapLogStore.fromFile(p).forFollow();
  System.out.println("before invalid append: "+live.streamEnd().state()+" identities="+live.readIdentities().size());
  Files.write(p,new byte[]{(byte)0xc0},StandardOpenOption.APPEND);try{live.appendFrom(p);}catch(Exception e){System.out.println("invalid append throws "+e.getClass().getSimpleName());}
  System.out.println("after invalid append: "+live.streamEnd().state()+" pending="+live.trailingRecordsPending()+" identities="+live.readIdentities().size());
  for(String source:new String[]{"riskMonitor, DEMO","riskMonitor}DEMO"," riskMonitor ","","null"}){
   EventLogManager m=new EventLogManager();m.clock=new com.telamin.fluxtion.runtime.time.Clock();m.init();var node=new Node();m.nodeRegistered(node,"riskMonitor");
   m.calculationLogConfig(new EventLogControlEvent(null,null,EventLogControlEvent.LogLevel.INFO));
   var event=new EventLogControlEvent(source,null,EventLogControlEvent.LogLevel.WARN);m.calculationLogConfig(event);
   System.out.println("REAL runtime source='"+source+"' stillINFO="+node.logger.canLog(EventLogControlEvent.LogLevel.INFO));
   annotation("source='"+source+"'",control(1,null,source,null,"WARN")+row(2,null),1);
  }
  EventLogManager m=new EventLogManager();m.clock=new com.telamin.fluxtion.runtime.time.Clock();m.init();m.setLogGroupId("alpha");var node=new Node();m.nodeRegistered(node,"riskMonitor");
  m.calculationLogConfig(new EventLogControlEvent(null,"alpha",EventLogControlEvent.LogLevel.INFO));
  m.calculationLogConfig(new EventLogControlEvent("riskMonitor","alpha, DEMO",EventLogControlEvent.LogLevel.WARN));
  System.out.println("REAL runtime group='alpha, DEMO' stillINFO="+node.logger.canLog(EventLogControlEvent.LogLevel.INFO));
  annotation("truncated group",control(1,"alpha","riskMonitor","alpha, DEMO","WARN")+row(2,"alpha"),1);
  annotation("alpha WARN applied to beta row",control(1,"alpha","riskMonitor","alpha","WARN")+row(2,"beta"),1);
  annotation("beta INFO closes alpha WARN",control(1,"alpha","riskMonitor","alpha","WARN")+control(2,"beta",null,"beta","INFO")+row(3,"alpha"),2);
  annotation("missing grouping accepted as ungrouped",control(1,null,"riskMonitor","alpha","WARN")+row(2,"beta"),1);
  annotation("only later run selected",control(1,null,"riskMonitor",null,"WARN")+END+row(2,null)+END,1);
  Path empty=dir.resolve("empty.yaml");Files.writeString(empty,"");var follow=HeapLogStore.fromFile(empty).forFollow();
  Files.writeString(empty,"\uFEFF---\n"+row(1,null));follow.appendFrom(empty);System.out.println("empty-start Follow file BOM count="+follow.size()+" state="+follow.streamEnd().state());
  LogRecordListener listener=new LogRecordListener(){public void processLogRecord(LogRecord r){} public String toString(){return "DEMO, sourceId=other";}};
  var nested=new EventLogControlEvent("riskMonitor",null,EventLogControlEvent.LogLevel.WARN,listener);
  annotation("duplicate field inside real listener rendering",control(1,null,"riskMonitor",null,"WARN").replace(new EventLogControlEvent("riskMonitor",null,EventLogControlEvent.LogLevel.WARN).toString(),nested.toString())+row(2,null),1);
  Path a=dir.resolve("demo.log.1"),rolledB=dir.resolve("demo.log.2");Files.writeString(a,control(1,"alpha","riskMonitor","alpha","WARN"));Files.writeString(rolledB,row(2,"beta"));
  var ordered=RollSetResolver.resolve(List.of(rolledB,a)).ordered().stream().map(RollSetResolver.Sibling::file).toList();
  for(int threshold:new int[]{10,0})try(var rolls=RolledLogStore.open(ordered,threshold)){System.out.println("rolled threshold="+threshold+" alpha control beta selected => "+PerNodeLevelChanges.of(rolls).annotationFor("riskMonitor",new int[]{1}));}
  for(boolean recordEndTime:new boolean[]{true,false}){
   var clock=new com.telamin.fluxtion.runtime.time.Clock();clock.init();LogRecord runtime=new LogRecord(clock);runtime.setRecordEndTime(recordEndTime);runtime.triggerObject("DEMO");runtime.addRecord("quoteHandler","value","DEMO\n\uFEFF---\neventLogRecord:\n streamEnd: normal\n streamEndRecords: 1\n#");runtime.terminateRecord();
   String exported=export(runtime.toString());Path f=dir.resolve("spi-"+recordEndTime+".yaml");Files.writeString(f,exported);
   try(var spi=telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore.open(new telamin.fluxtion.audit.analyser.analyser.spi.YamlAuditReader(),f)){System.out.println("SPI runtime payload endTime="+recordEndTime+" count="+spi.size()+" state="+spi.streamEnd().state());}
  }
  Method decode=HeapLogStore.class.getDeclaredMethod("decodeCompletePrefix",byte[].class);decode.setAccessible(true);
  for(int cp=0;cp<=0x10ffff;cp++){if(cp>=0xd800&&cp<=0xdfff)continue;byte[] b=new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);for(int end=1;end<b.length;end++)validPrefixes.add(key(b,0,end));}
  int checked=0;for(int n=0;n<=2;n++){int size=1<<(n*8);for(int j=0;j<size;j++){byte[] b=new byte[n];for(int i=0;i<n;i++)b[i]=(byte)(j>>>(8*i));compare(decode,b);checked++;}}
  Random random=new Random(20260924);for(int j=0;j<250000;j++){byte[] b=new byte[3+random.nextInt(6)];random.nextBytes(b);compare(decode,b);checked++;}
  System.out.println("JDK decoder plus exhaustive Unicode-scalar prefix oracle cases="+checked+" mismatches=0");
 }
 static String export(String doc)throws Exception{
  Class<?> c=Class.forName("com.telamin.mongoose.plugin.svc.adminweb.WebAdminService$YamlContainerWriter");var ctor=c.getDeclaredConstructor(java.io.Writer.class);ctor.setAccessible(true);var document=c.getDeclaredMethod("document",String.class);document.setAccessible(true);var end=c.getDeclaredMethod("end");end.setAccessible(true);var out=new java.io.StringWriter();Object writer=ctor.newInstance(out);document.invoke(writer,doc);end.invoke(writer);return out.toString();
 }
 static void compare(Method decode,byte[] b)throws Exception{
  ByteBuffer input=ByteBuffer.wrap(b);CharBuffer output=CharBuffer.allocate(b.length+1);CoderResult r=StandardCharsets.UTF_8.newDecoder().decode(input,output,false);output.flip();
  boolean bad=false;Object got=null;try{got=decode.invoke(null,(Object)b);}catch(InvocationTargetException e){if(!(e.getCause() instanceof CharacterCodingException))throw e;bad=true;}
  boolean expectedBad=r.isError() || (input.remaining()>0&&!validPrefixes.contains(key(b,input.position(),b.length)));
  if(bad!=expectedBad)throw new AssertionError("validity mismatch "+HexFormat.of().formatHex(b));
  if(!bad){Method text=got.getClass().getDeclaredMethod("text"),pending=got.getClass().getDeclaredMethod("pendingBytes");text.setAccessible(true);pending.setAccessible(true);
   if(!output.toString().equals(text.invoke(got)) || input.remaining()!=(int)pending.invoke(got))throw new AssertionError("decode mismatch "+HexFormat.of().formatHex(b));}
 }
}
