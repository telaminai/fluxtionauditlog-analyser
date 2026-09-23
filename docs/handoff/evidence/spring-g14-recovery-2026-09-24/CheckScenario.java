import com.example.myapp.generated.MyProcessor;
import com.example.myapp.event.PriceUpdate;
import com.example.myapp.event.Checked;
import com.example.myapp.service.Commands;
import java.util.ArrayList;
import java.util.List;
public class CheckScenario {
  static final int[] counts={0,1,1,1,1,0,1};
  static final double[] prices={0,10,10,10,10,0,40};
  static final boolean[] paused={false,false,false,true,true,false,false};
  static final int[] sizes={0,1,1,1,1,1,2};
  static void state(MyProcessor p,List<Checked> out,int n){
    var c=p.child;
    String actual=c.getProcessedCount()+","+c.getLastProcessedPrice()+","+c.isPaused()+","+out.size();
    if(c.getProcessedCount()!=counts[n] || c.getLastProcessedPrice()!=prices[n] || c.isPaused()!=paused[n] || out.size()!=sizes[n])
      throw new AssertionError("step "+n+": "+actual);
    System.out.println("STATE "+n+" "+actual);
  }
  public static void main(String[] ignored){
    var p=new MyProcessor();var out=new ArrayList<Checked>();
    p.setAuditLogProcessor(record -> {});p.init();p.addSink("checked",(Checked c)->out.add(c));
    state(p,out,0);p.onEvent(new PriceUpdate("DEMO",10,7));state(p,out,1);
    p.onEvent(new PriceUpdate("DEMO",20,9));state(p,out,2);
    p.publishSignal("pause");state(p,out,3);
    p.onEvent(new PriceUpdate("DEMO",30,7));state(p,out,4);
    p.getExportedService(Commands.class).reset();state(p,out,5);
    p.onEvent(new PriceUpdate("DEMO",40,7));state(p,out,6);
    if(!out.equals(List.of(new Checked(10,1),new Checked(40,1))))throw new AssertionError(out);
    System.out.println("PASS outputs="+out);
  }
}
