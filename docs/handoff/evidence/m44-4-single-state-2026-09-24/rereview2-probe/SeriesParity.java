import java.util.*;
import java.nio.file.*;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
public class SeriesParity {
 public static void main(String[] args)throws Exception{
  var s=HeapLogStore.fromFile(Path.of("src/test/resources/topology/demo-quote-series.yaml"));int n=0;
  for(String expr:List.of("quotePublisher.spread","priceListener.mid - quotePublisher.spread"))
   for(String resolve:List.of("DEFAULT","STRICT","locf")){
    var p=new LinkedHashMap<String,Object>();p.put("expr",expr);if(!resolve.equals("DEFAULT"))p.put("resolve",resolve);
    System.out.println(++n+" "+SeriesScan.scan(s,p));
    p.put("filter",Map.of("from",1767258004000L,"to",1767258050000L));
    System.out.println(++n+" "+SeriesScan.scan(s,p));
   }
  for(var extra:List.<Map<String,Object>>of(Map.of("crossings",Map.of("above",2)),Map.of("buckets","1s"),Map.of("limit",2))){
   var p=new LinkedHashMap<String,Object>(extra);p.put("expr","quotePublisher.spread");System.out.println(++n+" "+SeriesScan.scan(s,p));
  }
 }
}
