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

public class DisplayProbe {
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
  Path project=Files.createDirectories(dir.resolve("demo")), profile=ProjectProfile.pathFor(project), log=project.resolve("input.yaml");StringBuilder data=new StringBuilder();for(int i=1;i<=8;i++)data.append("---\neventLogRecord:\n  logTime: "+(i*1000)+"\n  event: Tick\n  nodeLogs:\n    - node: { value: "+i+", other: "+(i*10)+", third: "+(i*100)+"}\n");data.append("---\n");Files.writeString(log,data);
  Files.writeString(project.resolve("series.csv"),"t,v\n1000,1\n2000,2\n");
  AppConfig cfg=new AppConfig();cfg.reports.add(new ReportSpec("report-one","First report title","","",null,null,List.of(ReportSpec.SectionSpec.narrative("FIRST REPORT DISTINCT BODY"))));cfg.reports.add(new ReportSpec("report-two","Second report title","","",null,null,List.of(ReportSpec.SectionSpec.narrative("SECOND REPORT DISTINCT BODY"))));cfg.savedGraphs.add(graph("Alpha chart",null,false));cfg.savedGraphs.add(graph("Beta chart",null,false));ProjectProfile.save(profile,cfg,new SettingsShare());
  try{
   createFrame();action("open",Map.of("project",profile.toString()));action("open",Map.of("log",log.toString()));loaded();
   openRow("Second report title");screenshot("00-click-debug");System.out.println("DETAIL="+detail());check(detail().contains("SECOND REPORT DISTINCT BODY")&&!detail().contains("FIRST REPORT DISTINCT BODY"),"check1 second row selects second NAME and body");screenshot("01-second-report");
   openRow("First report title");check(detail().contains("FIRST REPORT DISTINCT BODY")&&!detail().contains("SECOND REPORT DISTINCT BODY"),"check2 first row selects first NAME and body");screenshot("02-first-report");
   GraphTabs gt=(GraphTabs)field(frame,"graphTabs");AppConfig live=(AppConfig)field(frame,"config");
   // Construct the explicitly requested saved-but-not-open state: detach its view only, retain profile definition.
   edt(()->{GraphPanel p=gt.graphNamed("Beta chart");p.unbind();((JTabbedPane)field(gt,"tabs")).remove(p);return null;});
   var before=List.copyOf(live.savedGraphs);openRow("Beta chart");GraphPanel beta=edt(()->gt.graphNamed("Beta chart"));check(beta!=null,"check3 closed saved chart materialized");
   edt(()->{check(((JTabbedPane)field(gt,"tabs")).getSelectedComponent()==beta,"check3 chart selected");check(beta.seriesSpecs().equals(before.get(1).series()),"check3 series retained");check(beta.notes().explanation().equals("saved explanation")&&beta.notes().notes().size()==1,"check3 notes retained");check(beta.pinnedFrom().equals(1000L)&&beta.pinnedTo().equals(7000L),"check3 pin retained");check(gt.specs().stream().filter(g->g.name().equals("Beta chart")).findFirst().orElseThrow().rightAxis().equals(List.of("node.other")),"check3 right axis retained");check(live.savedGraphs.equals(before),"check3 reveal does not rewrite saved definitions");return null;});screenshot("03-open-saved-chart");
   edt(()->{beta.addSpecs(List.of("node\u0001third"));beta.setCaption("later edit survives");beta.pin(2000L,5000L);return null;});openRow("Alpha chart");openRow("Beta chart");edt(()->{check(gt.graphNamed("Beta chart")==beta,"check4 same panel instance");check(beta.seriesSpecs().size()==3&&beta.caption().equals("later edit survives")&&beta.pinnedFrom().equals(2000L)&&beta.pinnedTo().equals(5000L),"check4 later edits survive repeated Open");return null;});screenshot("04-existing-edited-chart");
   JComboBox<?> style=(JComboBox<?>)field(beta,"styleCombo");click(style);var popup=(javax.swing.plaf.basic.ComboPopup)edt(()->style.getAccessibleContext().getAccessibleChild(0));JList<?> choices=popup.getList();Rectangle cell=edt(()->choices.getCellBounds(1,1));Point menu=edt(choices::getLocationOnScreen);robot.mouseMove(menu.x+cell.x+cell.width/2,menu.y+cell.y+cell.height/2);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);pause();check(edt(beta::styleName).equals("line"),"check5 real combo selects Line");screenshot("04b-ui-line-selected");edt(()->{invoke(frame,"flushProject");return null;});System.out.println("OBSERVATION check5 UI dropdown persisted line="+Files.readString(profile).contains(".style=line"));
   action("open",Map.of("close","project"));action("open",Map.of("project",profile.toString()));action("open",Map.of("log",log.toString()));loaded();openRow("Beta chart");System.out.println("OBSERVATION check5 UI Line after close/reopen="+edt(()->((GraphTabs)field(frame,"graphTabs")).graphNamed("Beta chart").styleName()));screenshot("05-ui-line-reopened");
   edt(()->{((GraphTabs)field(frame,"graphTabs")).graphNamed("Beta chart").setStyleByName("line");invoke(frame,"flushProject");return null;});check(Files.readString(profile).contains(".style=line"),"control programmatic setter persisted style key=line");action("open",Map.of("close","project"));action("open",Map.of("project",profile.toString()));action("open",Map.of("log",log.toString()));loaded();openRow("Beta chart");check(edt(()->((GraphTabs)field(frame,"graphTabs")).graphNamed("Beta chart").styleName()).equals("line"),"control programmatic Line survives actual project close/reopen");screenshot("05b-programmatic-line-reopened");
   AppConfig legacy=new AppConfig();Path old=project.resolve(".analyser/project.legacy.fluxtion-settings");legacy.savedGraphs.add(graph("Legacy chart",null,false));ProjectProfile.save(old,legacy,new SettingsShare());action("open",Map.of("close","project"));action("open",Map.of("project",old.toString()));action("open",Map.of("log",log.toString()));loaded();openRow("Legacy chart");check(edt(()->((GraphTabs)field(frame,"graphTabs")).graphNamed("Legacy chart").styleName()).equals("step"),"check5 legacy missing style reopens as Stairs");screenshot("06-legacy-stairs");
   // Style-copy attack: use actual profile load then actual UI renderer.
   AppConfig external=new AppConfig();external.savedGraphs.add(graph("External line","line",true));Path externalFile=project.resolve(".analyser/project.external.fluxtion-settings");ProjectProfile.save(externalFile,external,new SettingsShare());action("open",Map.of("close","project"));action("open",Map.of("project",externalFile.toString()));action("open",Map.of("log",log.toString()));loaded();openRow("External line");System.out.println("ATTACK external input profile style=line, rendered style="+edt(()->((GraphTabs)field(frame,"graphTabs")).graphNamed("External line").styleName()));screenshot("07-external-line-reopened");
  }finally{if(frame!=null)edt(()->{frame.dispose();return null;});}
  System.exit(0);
 }
}
