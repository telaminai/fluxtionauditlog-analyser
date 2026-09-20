import java.util.*;
import telamin.fluxtion.audit.analyser.analyser.ui.ChartPanel;
import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget;
import telamin.fluxtion.audit.analyser.analyser.graph.*;
public class JourneyChartProbe {
 static double field(ChartPanel c,String n) throws Exception {var f=ChartPanel.class.getDeclaredField(n); f.setAccessible(true);return f.getDouble(c);}
 public static void main(String[] args) throws Exception {
  javax.swing.SwingUtilities.invokeAndWait(()-> {try {
   var c=new ChartPanel(); var a=new Series("small");a.add(100,10);a.add(200,20);
   var b=new Series("large");b.add(100,1000000);b.add(200,1250000);
   c.setSeries(List.of(a,b));c.setAxes(new AxisAssignment(List.of("large")));
   System.out.println("after axes: left="+field(c,"vy0")+".."+field(c,"vy1")+" right="+field(c,"ry0")+".."+field(c,"ry1"));
   c.setViewWindow(100L,200L);
   System.out.println("after window: left="+field(c,"vy0")+".."+field(c,"vy1")+" right="+field(c,"ry0")+".."+field(c,"ry1"));
   if(field(c,"vy1")<1000000)throw new AssertionError("reported scale contamination did not reproduce");
   System.out.println("colon target="+SpotlightTarget.parse("graph:Hedging: exposure and working:note:1"));
  }catch(Exception e){throw new RuntimeException(e);}});
 }
}
