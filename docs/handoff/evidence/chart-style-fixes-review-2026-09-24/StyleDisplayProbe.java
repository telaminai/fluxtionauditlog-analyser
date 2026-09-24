import telamin.fluxtion.audit.analyser.analyser.ui.*;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import telamin.fluxtion.audit.analyser.analyser.report.*;
import telamin.fluxtion.audit.analyser.analyser.llm.*;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;
import java.lang.reflect.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

public class StyleDisplayProbe {
 static MainFrame frame; static Robot robot; static Path dir; static ActionExecutor ex;
 static <T>T edt(Callable<T> call)throws Exception { var v=new AtomicReference<T>();var e=new AtomicReference<Throwable>();SwingUtilities.invokeAndWait(()->{try{v.set(call.call());}catch(Throwable t){e.set(t);}});if(e.get()!=null)throw new RuntimeException(e.get());return v.get(); }
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object invoke(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);return m.invoke(o);}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);System.out.println("PASS "+s);}
 static void pause()throws Exception{robot.waitForIdle();Thread.sleep(450);robot.waitForIdle();}
 static List<Component> all(Component c){List<Component> r=new ArrayList<>();r.add(c);if(c instanceof Container t)for(Component x:t.getComponents())r.addAll(all(x));return r;}
 static JButton button(String label)throws Exception{return edt(()->{var p=(ProjectPanel)field(frame,"projectPanel");for(Component c:all(p))if(c instanceof JLabel l && label.equals(l.getText()))for(Component b:all(l.getParent()))if(b instanceof JButton j&&j.getText().equals("Open"))return j;throw new AssertionError("No Open for "+label);});}
 static void click(Component c)throws Exception{pause();edt(()->{if(c instanceof JComponent jc){var v=(JViewport)SwingUtilities.getAncestorOfClass(JViewport.class,c);if(v!=null){Point q=SwingUtilities.convertPoint(c,0,0,v.getView());v.setViewPosition(new Point(0,Math.max(0,q.y-v.getHeight()/2)));}else jc.scrollRectToVisible(new Rectangle(0,0,c.getWidth(),c.getHeight()));}return null;});pause();Point p=edt(()->c.getLocationOnScreen());Dimension d=edt(c::getSize);System.out.println("CLICK "+(c instanceof JButton j?j.getToolTipText():"Style dropdown")+" at "+p);if(c instanceof JButton j)edt(()->{j.addActionListener(e->System.out.println("BUTTON FIRED "+j.getToolTipText()));return null;});robot.mouseMove(p.x+d.width/2,p.y+d.height/2);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);pause();}
 static void openRow(String label)throws Exception{click(button(label));}
 static ActionResult action(String verb,Map<String,Object> params)throws Exception{ActionResult r=ex.render(verb,params);check(r.ok(),"setup action "+verb+" "+params.keySet()+" => "+r.error());return r;}
 static void loaded()throws Exception{for(int i=0;i<400;i++){if(edt(()->field(frame,"store"))!=null){pause();return;}Thread.sleep(50);}throw new AssertionError("load timed out");}
 static String detail()throws Exception{return edt(()->{var p=(ReportsPanel)field(frame,"reportsPanel");StringBuilder s=new StringBuilder();for(Component c:all((Component)field(p,"detail")))if(c instanceof JTextComponent t)s.append(t.getText()).append('\n');else if(c instanceof JLabel l)s.append(l.getText()).append('\n');return s.toString();});}
 static void screenshot(String name)throws Exception{pause();Rectangle bounds=edt(frame::getBounds);ImageIO.write(robot.createScreenCapture(bounds),"png",dir.resolve(name+".png").toFile());}
 static GraphSpec graph(String name,String style, boolean ext){return new GraphSpec(name,List.of("node\u0001value","node\u0001other"),List.of(),1000L,7000L,"saved caption","saved explanation",List.of(new GraphSpec.NoteSpec(2000,"saved note",null)),List.of("node.other"),List.of(),List.of(),ext?List.of(new GraphSpec.ExternalSpec("series.csv","external","t","epochMillis",null,"v",0)):List.of(),List.of(),style);}
 static void createFrame()throws Exception{frame=edt(()->{var f=new MainFrame();Rectangle s=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();f.setBounds(s.x+10,s.y+10,Math.min(1450,s.width-20),Math.min(1000,s.height-20));f.setAlwaysOnTop(true);f.setVisible(true);f.toFront();Desktop.getDesktop().requestForeground(true);return f;});ex=(ActionExecutor)field(frame,"actionExecutor");pause();}
 public static void main(String[] args)throws Exception{
  dir=Path.of(args[0]);Files.createDirectories(dir);System.setProperty("user.home",Files.createDirectories(dir.resolve("home")).toString());robot=new Robot();robot.setAutoDelay(80);
  Path project=Files.createDirectories(dir.resolve("demo")), profile=ProjectProfile.pathFor(project), log=project.resolve("input.yaml");
  StringBuilder data=new StringBuilder();for(int i=1;i<=8;i++)data.append("---\neventLogRecord:\n  logTime: "+(i*1000)+"\n  event: Tick\n  nodeLogs:\n    - node: { value: "+i+", other: "+(i*10)+"}\n");data.append("---\n");Files.writeString(log,data);
  AppConfig cfg=new AppConfig();cfg.savedGraphs.add(graph("Alpha chart","step",false));cfg.savedGraphs.add(graph("Beta chart","step",false));ProjectProfile.save(profile,cfg,new SettingsShare());
  try{
   ThemeManager.apply("Light"); createFrame();action("open",Map.of("project",profile.toString()));action("open",Map.of("log",log.toString()));loaded();openRow("Beta chart");
   GraphTabs gt=(GraphTabs)field(frame,"graphTabs");GraphPanel beta=edt(()->gt.graphNamed("Beta chart"));
   JComboBox<?> style=(JComboBox<?>)field(beta,"styleCombo");click(style);var popup=(javax.swing.plaf.basic.ComboPopup)edt(()->style.getAccessibleContext().getAccessibleChild(0));JList<?> choices=popup.getList();Rectangle cell=edt(()->choices.getCellBounds(1,1));Point menu=edt(choices::getLocationOnScreen);robot.mouseMove(menu.x+cell.x+cell.width/2,menu.y+cell.y+cell.height/2);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);pause();
   check(edt(beta::styleName).equals("line"),"real mouse selection chooses Line");screenshot("01-line-selected");
   // Do not flush or make another edit: wait for the production project's autosave timer.
   boolean saved=false;for(int i=0;i<100;i++){if(Files.readString(profile).contains("graph.1.style=line")){saved=true;break;}Thread.sleep(50);}check(saved,"dropdown alone autosaves Line to project file");
   action("open",Map.of("close","project"));check(edt(()->field(frame,"store"))==null,"project close closes log");
   action("open",Map.of("project",profile.toString()));action("open",Map.of("log",log.toString()));loaded();openRow("Beta chart");
   check(edt(()->gt.graphNamed("Beta chart").styleName()).equals("line"),"mouse-selected Line survives actual project close/reopen and explicit log open");screenshot("02-line-reopened");
   // The real post-dialog merge seam: no file-chooser automation, but the same preview/apply/refresh.
   AppConfig live=(AppConfig)field(frame,"config");SettingsShare share=new SettingsShare();AppConfig incoming=new AppConfig();incoming.savedGraphs.add(graph("Beta chart","points",false).withOpen(false));
   edt(()->{invoke(frame,"syncOpenGraphsIntoConfig");var plan=share.preview(share.export(incoming,Set.of(SettingsShare.Category.GRAPHS)),live,project);share.apply(plan,Set.of(SettingsShare.Category.GRAPHS),live);var g=live.savedGraphs.stream().filter(x->x.name().equals("Beta chart")).findFirst().orElseThrow();check(g.style().equals("points")&&!g.open(),"import plan applies Points/closed before UI refresh");invoke(frame,"applyImportedConfig");g=live.savedGraphs.stream().filter(x->x.name().equals("Beta chart")).findFirst().orElseThrow();System.out.println("OBSERVATION after actual import refresh: style="+g.style()+", open="+g.open());check(g.style().equals("line")&&g.open(),"REPRODUCED import refresh overwrites imported Points/closed with old Line/open tab");return null;});screenshot("03-import-overwritten");
  }finally{if(frame!=null)edt(()->{frame.dispose();return null;});}
  System.exit(0);
 }
}
