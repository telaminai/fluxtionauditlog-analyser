import java.nio.file.*;import java.io.*;import java.util.*;
import com.acme.demo.generated.DemoQuoteRecordedProcessor;
import com.acme.demo.replay.*;import com.acme.demo.event.Events;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.time.ClockStrategy;
public class LiveCapture {
 public static void main(String[] args)throws Exception {
  var p=new DemoQuoteRecordedProcessor();p.init();long[] tick={1767258000000L};
  p.onEvent(ClockStrategy.registerClockEvent(()->tick[0]+=10));
  p.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
  StringBuilder audit=new StringBuilder();p.setAuditLogProcessor(r->audit.append("---\n").append(r).append('\n'));
  audit.setLength(0);var w=(ReplayCapture)p.getAuditorById(ReplayCapture.NAME);var text=new StringWriter();w.setTarget(text);
  var entries=ReplayReader.read(Files.readString(Path.of(args[0])),w.getHandled());
  for(var e:entries){w.expect(e.event());p.onEvent(e.event());}
  var external=new Events.RiskBreachEvent("DEMO-external",8);w.expect(external);p.onEvent(external);
  var captured=ReplayReader.read(text.toString(),w.getHandled());
  System.out.println("external inputs=8; captured="+captured.size()+"; audit="+audit.toString().lines().filter(l->l.equals("---")).count());
  System.out.println("captured RiskBreachEvent="+captured.stream().filter(e->e.event() instanceof Events.RiskBreachEvent).count());
  if(captured.size()!=8 || captured.stream().filter(e->e.event() instanceof Events.RiskBreachEvent).count()!=1)throw new AssertionError("only external inputs, including external breach, recorded");
  var docs=audit.toString().split("(?m)^---$");int pos=0;
  for(var e:captured){String name=e.event().getClass().getSimpleName();boolean found=false;
   while(pos<docs.length){String d=docs[pos++];if(d.contains("event: "+name+"\n") && d.contains("eventTime: "+e.time()+"\n")){found=true;break;}}
   if(!found)throw new AssertionError("receipt instant differs from audit");
  }
  System.out.println("all 8 receipt instants match audit; graph-raised event excluded");
  Files.writeString(Path.of(args[1]),audit.toString());
 }
}
