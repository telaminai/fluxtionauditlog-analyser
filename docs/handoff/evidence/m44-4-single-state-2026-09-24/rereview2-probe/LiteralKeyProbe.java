package telamin.fluxtion.audit.analyser.analyser.ui;
import java.util.*;
import java.nio.file.*;
import javax.swing.SwingUtilities;
import telamin.fluxtion.audit.analyser.analyser.graph.*;
import telamin.fluxtion.audit.analyser.analyser.parse.*;
import telamin.fluxtion.audit.analyser.analyser.filter.*;
/** Compare the production adapter before/after N2; literal keys must not become formulas. */
public class LiteralKeyProbe {
 public static void main(String[] args)throws Exception{SwingUtilities.invokeAndWait(()->{try{
  var s=new HeapLogStore("eventLogRecord:\n  event: Tick\n  logTime: 1000\n  nodeLogs:\n    - rootNode: {v: 100, v+1: 7}\n---\n");
  System.out.println("literal extraction="+SeriesExtractor.extract(s,new FilterState(),GraphKey.fromDisplay("rootNode.v+1")).y(0));
  System.out.println("quoted verb="+SeriesScan.scan(s,Map.of("expr","`rootNode.v+1`")));
  System.out.println("unquoted formula="+SeriesScan.scan(s,Map.of("expr","rootNode.v+1")));
  var of=Arrays.stream(ReportSeriesPicture.class.getDeclaredMethods()).filter(m->m.getName().equals("of")).findFirst().orElseThrow();
  Object r=of.getParameterCount()==5?of.invoke(null,s,new FilterState(),Map.of("key","rootNode.v+1"),1200,600):of.invoke(null,s,Map.of("key","rootNode.v+1"),1200,600);
  var image=r.getClass().getDeclaredMethod("image");var caption=r.getClass().getDeclaredMethod("caption");
  System.out.println("report="+caption.invoke(r));
  javax.imageio.ImageIO.write((java.awt.image.BufferedImage)image.invoke(r),"png",Path.of(args[0]).toFile());
 }catch(Exception e){throw new RuntimeException(e);}});System.exit(0);}
}
