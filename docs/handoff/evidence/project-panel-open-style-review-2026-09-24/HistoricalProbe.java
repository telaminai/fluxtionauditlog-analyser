import telamin.fluxtion.audit.analyser.analyser.ui.*;
import java.util.*;
import java.awt.*;
import javax.swing.*;
import java.lang.reflect.*;
public class HistoricalProbe {
 static java.util.List<Component> all(Component c){var r=new ArrayList<Component>();r.add(c);if(c instanceof Container x)for(Component y:x.getComponents())r.addAll(all(y));return r;}
 public static void main(String[] args)throws Exception {SwingUtilities.invokeAndWait(()->{
 var nav=(ProjectPanel.Navigator)Proxy.newProxyInstance(ProjectPanel.class.getClassLoader(),new Class[]{ProjectPanel.Navigator.class},(p,m,a)->null);
 ProjectPanel panel=new ProjectPanel(nav);ProjectModel model=ProjectModel.from(Map.of("savedGraphs",java.util.List.of(Map.of("name","Saved chart","open",false,"input","2 series"))));panel.render(model);
 var row=model.sections().stream().flatMap(s->s.rows().stream()).filter(r->r.primary().equals("Saved chart")).findFirst().orElseThrow();
 for(var c:all(panel))if(c instanceof JLabel l&&l.getText().equals("Saved chart")){long n=all(l.getParent()).stream().filter(b->b instanceof JButton j&&j.getText().equals("Open")).count();System.out.println("Target="+row.target()+", path="+row.path()+", row Open buttons="+n);if(row.target()!=ProjectModel.Target.NONE||n!=0)throw new AssertionError("Unexpected old rendering");}
 });}
}
