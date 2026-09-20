import java.nio.file.*;
import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.filter.*;
import telamin.fluxtion.audit.analyser.analyser.graph.*;
import telamin.fluxtion.audit.analyser.analyser.config.*;
public class MarkerProbe {
 public static void main(String[] args)throws Exception {
  for(String file:args){
   var store=HeapLogStore.fromFile(Path.of(file)); var filter=new FilterState();
   var pnl=SeriesExtractor.extract(store,filter,GraphKey.fromDisplay("markToMarketNode.totalPnl"));
   var events=new TreeMap<String,Integer>();var times=new TreeMap<Long,Integer>(); int buys=0,sells=0;
   for(int i=0;i<store.size();i++){
    var r=store.record(i);events.merge(r.event(),1,Integer::sum);if(r.logTime()!=null)times.merge(r.logTime(),1,Integer::sum);
    var q=SeriesExtractor.lastMatching(r.nodeLogs(),GraphKey.fromDisplay("positionNode.quantity"));
    if(q!=null&&q.graphValue().isPresent()){double v=q.graphValue().getAsDouble();if(v>0)buys++;if(v<0)sells++;}
   }
   System.out.println("FILE="+file+" records="+store.size()+" events="+events+" observedBuys="+buys+" observedSells="+sells+" distinctTimes="+times.size()+" maxSameTime="+Collections.max(times.values()));
   for(var condition:List.of("positionNode.quantity > 0","positionNode.quantity < 0","rootNode.price")){
    var marker=MarkerExtractor.extract(store,filter,new GraphSpec.MarkerSpec(condition,"circle",condition,condition.equals("rootNode.price")?"axis":"series:markToMarketNode.totalPnl",null),label->pnl);
    System.out.println("WHEN="+condition+" count="+marker.points().size()+" rows="+marker.points().stream().map(p->p.recordIndex()+":"+store.record(p.recordIndex()).event()).toList());
   }
   var msft=SeriesExtractor.extract(store,filter,GraphKey.fromDisplay("positionNode.position_MSFT"));
   if(msft.size()>0)System.out.println("MSFT last="+msft.x(msft.size()-1)+" value="+msft.y(msft.size()-1)+" logEnd="+times.lastKey());
  }
 }
}
