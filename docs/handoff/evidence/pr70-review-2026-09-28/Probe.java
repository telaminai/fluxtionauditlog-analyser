import java.nio.file.*;import java.util.*;import telamin.fluxtion.audit.analyser.bundle.*;import telamin.fluxtion.audit.analyser.analyser.parse.*;
public class Probe {
 public record DEMO(int n) {}
 public static void main(String[] args) throws Exception {
  String duplicated="---\n!!com.telamin.fluxtion.runtime.event.ReplayRecord\nevent: !!Probe$DEMO {n: 1}\nwallClockTime: 1\nevent: !!Probe$DEMO {n: 2}\nwallClockTime: 2\n";
  var read=ReplayBundle.read(duplicated,Map.of(DEMO.class.getName(),DEMO.class));System.out.println("two replay entries without delimiter => entries="+read.size()+" first="+read.get(0)[0]);
  try(var store=LogStores.open(Path.of(args[0]),256)) {System.out.println("changed input values => "+ReplayPairing.observe(Path.of(args[1]),store.index(),store.size()));}
 }
}