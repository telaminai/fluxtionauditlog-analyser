import java.nio.file.*;
import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.topology.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
class SpringCoverageProbe {
 public static void main(String[] args) throws Exception {
  Path project=Path.of(args[0]);
  var graph=GraphMlParser.parse(Files.readString(project.resolve("MyProcessor.graphml")));
  var store=new HeapLogStore(Files.readString(project.resolve("run-c/audit.yaml")));
  var authored=Scaffolding.authoredNodes(graph);
  var result=CoverageService.assess(store,false,null,new CoverageService.Input(graph,authored,null));
  Set<String> logged=new LinkedHashSet<>();
  for(int i=0;i<store.size();i++)for(var n:store.record(i).nodeLogs())logged.add(n.instanceId());
  System.out.println("graph contains checked="+graph.contains("checked"));
  System.out.println("checked class="+graph.node("checked").className());
  System.out.println("authored contains checked="+authored.contains("checked"));
  System.out.println("coverage="+result.echo());
  System.out.println("current pairing="+GraphPairing.of(authored,logged));
  Set<String> all=new LinkedHashSet<>();for(var n:graph.nodes())all.add(n.id());
  System.out.println("whole graph pairing="+GraphPairing.of(all,logged));
  if(graph.contains("checked") && result.echo().containsKey("loggedButNotInTopology"))
    throw new AssertionError("A declared sink was reported absent from its graph");
 }
}
