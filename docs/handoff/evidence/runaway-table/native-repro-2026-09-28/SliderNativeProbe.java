package telamin.fluxtion.audit.analyser.analyser.ui;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
public class SliderNativeProbe extends RunawayNativeProbe {
 static TimeRangeSlider slider;
 static Point at(Component c,int x,int y)throws Exception{return edt(()->{Point p=new Point(x,y);SwingUtilities.convertPointToScreen(p,c);return p;});}
 static void move(Point a,Point b,int count){for(int n=1;n<=count;n++)robot.mouseMove(a.x+(b.x-a.x)*n/count,a.y+(b.y-a.y)*n/count);}
 static String both()throws Exception{return sample()+edt(()->" sliderMode="+field(slider,"dragMode")+" edgeTimer="+((javax.swing.Timer)field(slider,"edgeScroll")).isRunning()+" rows="+table.getRowCount()+" window="+field(slider,"lo")+":"+field(slider,"hi"));}
 public static void main(String[] args)throws Exception{
  Point original=MouseInfo.getPointerInfo().getLocation();
  try{
   edt(()->{ThemeManager.apply("dark");frame=new MainFrame();frame.setSize(1200,800);frame.setLocation(80,70);frame.setVisible(true);frame.toFront();ex=(ActionExecutor)field(frame,"actionExecutor");table=((LogTablePanel)field(frame,"tablePanel")).table();slider=(TimeRangeSlider)field(frame,"timeSlider");return null;});
   action("open",Map.of("log",Path.of("src/main/resources/demo/demo-quote-series.yaml").toAbsolutePath().toString(),"graphml",Path.of("src/main/resources/demo/demo-quote-processor.graphml").toAbsolutePath().toString()));
   long deadline=System.currentTimeMillis()+20000;
   while(!edt(()->table.getRowCount()>0 && !(Boolean)field(frame,"loadInFlight")) && System.currentTimeMillis()<deadline)Thread.sleep(50);
   action("walk",Map.of("name","DEMO_time", "steps",List.of(Map.of("view",Map.of("tab","summary","record",50),"targets",List.of(Map.of("target","records:row:50","caption","DEMO record"))))));
   robot=new Robot();robot.setAutoDelay(20);robot.waitForIdle();int stuck=0;
   for(String scenario:List.of("slider-then-table","walk-then-slider","walk-during-slider","table-to-slider","filter-during-table","edge-pan")){
    for(int trial=1;trial<=3;trial++){
     robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);robot.waitForIdle();
     action("walk",Map.of("end",true));action("filter",Map.of("clear",true));
     edt(()->{slider.setWindowMillis(0);frame.toFront();return null;});
     action("goto",Map.of("recordIndex",50));robot.waitForIdle();
     if(scenario.equals("walk-then-slider")){action("walk",Map.of("name","DEMO_time","play",true));Thread.sleep(250);}
     if(scenario.equals("edge-pan"))edt(()->{long span=(long)field(slider,"absMax")-(long)field(slider,"absMin");slider.setWindowMillis(span/4);return null;});
     Point start=at(slider,12,34),end=at(slider,Math.max(25,slider.getWidth()/3),34);
     boolean tableStart=scenario.equals("table-to-slider")||scenario.equals("filter-during-table");
     if(tableStart){start=point(false);end=at(slider,slider.getWidth()/2,34);}
     if(scenario.equals("edge-pan"))end=at(slider,0,34);
     log("BEGIN "+scenario+" trial="+trial+" "+both());
     robot.mouseMove(start.x,start.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);move(start,end,12);
     if(scenario.equals("walk-during-slider"))action("walk",Map.of("name","DEMO_time","play",true));
     if(scenario.equals("filter-during-table"))action("filter",Map.of("from",(long)edt(()->field(slider,"absMin"))+30000L));
     Thread.sleep(scenario.equals("edge-pan")?100:250);log("HELD "+scenario+" "+both());
     robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();log("RELEASE_SENT "+scenario+" "+both());
     Thread.sleep(150);String first=both();Thread.sleep(700);String last=both();
     boolean running=edt(()->scrolling()||((javax.swing.Timer)field(slider,"edgeScroll")).isRunning());
     log("RESULT "+scenario+" trial="+trial+" running="+running+" first={"+first+"} last={"+last+"}");
     if(running){stuck++;break;}
     if(scenario.contains("slider")&&!tableStart){
      Point a=point(false),b=point(true);robot.mouseMove(a.x,a.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);move(a,b,8);Thread.sleep(120);log("FOLLOWUP_HELD "+scenario+" "+both());robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();Thread.sleep(300);log("FOLLOWUP_RELEASE "+scenario+" "+both());
      if(edt(()->scrolling())){stuck++;break;}
     }
    }
    if(stuck>0)break;
   }
   log("DONE slider runawayCases="+stuck);
  }finally{if(robot!=null){robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.mouseMove(original.x,original.y);}edt(()->{for(Window w:Window.getWindows())w.dispose();return null;});}
  System.exit(0);
 }
}
