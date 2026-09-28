package telamin.fluxtion.audit.analyser.analyser.ui;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
public class RunawayNativeProbeAfter {
 static MainFrame frame; static ActionExecutor ex; static JTable table; static Robot robot;
 static <T>T edt(Callable<T> c)throws Exception{var r=new AtomicReference<T>();var error=new AtomicReference<Throwable>();SwingUtilities.invokeAndWait(()->{try{r.set(c.call());}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new RuntimeException(error.get());return r.get();}
 static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void action(String name,Map<String,Object> args)throws Exception{edt(()->{var r=ex.render(name,new java.util.LinkedHashMap<>(args));if(!r.ok())throw new IllegalStateException(name+": "+r.toMap());return null;});}
 static void log(String s){System.out.println(Instant.now()+" "+s);System.out.flush();}
 static boolean scrolling()throws Exception{Class<?> c=Class.forName("javax.swing.Autoscroller");Field f=c.getDeclaredField("component"),t=c.getDeclaredField("timer");f.setAccessible(true);t.setAccessible(true);Object timer=t.get(null);return f.get(null)==table && timer instanceof javax.swing.Timer st && st.isRunning();}
 static String sample()throws Exception{return edt(()->"selected="+table.getSelectedRowCount()+" lead="+table.getSelectionModel().getLeadSelectionIndex()+" adjusting="+table.getSelectionModel().getValueIsAdjusting()+" autoscroller="+scrolling()+" view="+table.getVisibleRect());}
 static Point point(boolean above)throws Exception{return edt(()->{Rectangle v=table.getVisibleRect();Point p=new Point(v.x+Math.min(180,v.width/2),above?v.y-8:v.y+v.height/2);SwingUtilities.convertPointToScreen(p,table);return p;});}
 public static void main(String[] args)throws Exception{
  Point original=MouseInfo.getPointerInfo().getLocation();
  Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue(){
   public AWTEvent getNextEvent()throws InterruptedException{
    AWTEvent e=super.getNextEvent();
    if(e instanceof MouseEvent m && (m.getID()==MouseEvent.MOUSE_PRESSED || m.getID()==MouseEvent.MOUSE_RELEASED))
     log("QUEUE_BEFORE_FILTER id="+m.getID()+" button="+m.getButton()+" when="+m.getWhen()+" source="+m.getSource().getClass().getName());
    return e;
   }
  });
  Toolkit.getDefaultToolkit().addAWTEventListener(e->{if(e instanceof WindowEvent w)log("WINDOW_EVENT id="+w.getID()+" class="+w.getWindow().getClass().getName()+" title="+(w.getWindow() instanceof Dialog d?d.getTitle():"DEMO main"));},AWTEvent.WINDOW_EVENT_MASK|AWTEvent.WINDOW_FOCUS_EVENT_MASK);
  try{
   edt(()->{ThemeManager.apply("dark");frame=new MainFrame();frame.setSize(1200,800);frame.setLocation(80,70);frame.setVisible(true);frame.toFront();ex=(ActionExecutor)field(frame,"actionExecutor");table=((LogTablePanel)field(frame,"tablePanel")).table();return null;});
   action("open",Map.of("log",Path.of("src/main/resources/demo/demo-quote-series.yaml").toAbsolutePath().toString(),"graphml",Path.of("src/main/resources/demo/demo-quote-processor.graphml").toAbsolutePath().toString()));
   long deadline=System.currentTimeMillis()+20000;
   while(!edt(()-> table.getRowCount()>0 && !(Boolean)field(frame,"loadInFlight")) && System.currentTimeMillis()<deadline)Thread.sleep(50);
   log("READY rows="+edt(()->table.getRowCount())+" showing="+edt(()->frame.isShowing()));
   action("walk",Map.of("name","DEMO_mouse", "steps",List.of(Map.of("view",Map.of("tab","summary","record",50),"targets",List.of(Map.of("target","records:row:50","caption","DEMO record"))))));
   robot=new Robot();robot.setAutoDelay(25);robot.waitForIdle();
   int failures=0, valid=0, inconclusive=0;
   for(String scenario:List.of(args.length==0?new String[]{"outside-frame","secondary","menu-open","settings-dialog"}:args[0].split(","))){
    for(int trial=1;trial<=3;trial++){
     robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);robot.waitForIdle();
     edt(()->{MenuSelectionManager.defaultManager().clearSelectedPath();return null;});
     action("walk",Map.of("end",true));action("goto",Map.of("recordIndex",50));
     edt(()->{frame.toFront();table.scrollRectToVisible(table.getCellRect(Math.min(65,table.getRowCount()-1),0,true));return null;});robot.waitForIdle();
     Point start=point(false),edge=point(true);
     log("BEGIN "+scenario+" trial="+trial+" "+sample());
     robot.mouseMove(start.x,start.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
     for(int n=1;n<=12;n++)robot.mouseMove(start.x,(int)(start.y+(edge.y-start.y)*(n/12.0)));
     Thread.sleep(180);log("HELD "+scenario+" "+sample());
     if(!edt(()->scrolling())){inconclusive++;robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);log("INCONCLUSIVE: gesture did not start the table autoscroller");continue;}
     valid++;
     if(scenario.equals("overlay")||scenario.equals("secondary")) action("spotlight",Map.of("target","records","caption","DEMO mouse trace"));
     if(scenario.equals("walk-step")) action("walk",Map.of("name","DEMO_mouse","play",true));
     if(scenario.equals("goto")) action("goto",Map.of("recordIndex",10));
     if(scenario.equals("secondary")){robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);}
     if(scenario.equals("outside-frame"))robot.mouseMove(65,edge.y);
     if(scenario.equals("menu-open"))edt(()->{frame.getJMenuBar().getMenu(0).setPopupMenuVisible(true);return null;});
     if(scenario.equals("settings-dialog")){
      SwingUtilities.invokeLater(()->{for(int m=0;m<frame.getJMenuBar().getMenuCount();m++)for(Component c:frame.getJMenuBar().getMenu(m).getMenuComponents())if(c instanceof JMenuItem item && item.getText().equals("Settings…"))item.doClick();});
      long until=System.currentTimeMillis()+3000;
      while(!edt(()->java.util.Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof Dialog && w.isShowing())) && System.currentTimeMillis()<until)Thread.sleep(25);
      log("DIALOG showing="+edt(()->java.util.Arrays.stream(Window.getWindows()).filter(w->w instanceof Dialog && w.isShowing()).map(w->((Dialog)w).getTitle()).toList()));
     }
     Thread.sleep(160);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
     log("RELEASE_SENT "+scenario+" "+sample());
     Thread.sleep(250);String first=sample();Thread.sleep(800);String last=sample();
     boolean running=edt(()->scrolling());
     log("RESULT "+scenario+" trial="+trial+" running="+running+" first={"+first+"} last={"+last+"}");
     if(running){failures++;log("RUNAWAY observed; stopping before another gesture changes it");Thread.sleep(1000);break;}
     edt(()->{for(Window w:Window.getWindows())if(w instanceof Dialog)w.dispose();frame.toFront();return null;});
     long focusDeadline=System.currentTimeMillis()+3000;
     while(!edt(()->frame.isFocused()) && System.currentTimeMillis()<focusDeadline)Thread.sleep(25);
    }
    if(failures>0)break;
   }
   log("DONE runawayCases="+failures+" validGestures="+valid+" inconclusive="+inconclusive);
  }finally{
   if(robot!=null){robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);robot.mouseMove(original.x,original.y);}
   edt(()->{for(Window w:Window.getWindows())w.dispose();return null;});
  }
  System.exit(0);
 }
}
