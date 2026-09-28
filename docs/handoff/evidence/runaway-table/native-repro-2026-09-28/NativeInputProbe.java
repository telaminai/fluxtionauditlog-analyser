import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicInteger;
public class NativeInputProbe {
 public static void main(String[] args) throws Exception {
  AtomicInteger pressed=new AtomicInteger(),released=new AtomicInteger(),clicked=new AtomicInteger();
  JFrame[] f={null}; JButton[] b={null}; Point[] p={null};
  Point old=MouseInfo.getPointerInfo().getLocation();
  try {
   SwingUtilities.invokeAndWait(()->{
    f[0]=new JFrame("DEMO native mouse delivery probe");b[0]=new JButton("DEMO input target");
    b[0].addMouseListener(new MouseAdapter(){
     public void mousePressed(MouseEvent e){pressed.incrementAndGet();System.out.println("PRESSED received");}
     public void mouseReleased(MouseEvent e){released.incrementAndGet();System.out.println("RELEASED received");}
    });
    b[0].addActionListener(e->{clicked.incrementAndGet();System.out.println("ACTION received");});
    f[0].add(b[0]);f[0].setSize(400,200);f[0].setLocationRelativeTo(null);f[0].setAlwaysOnTop(true);f[0].setVisible(true);f[0].toFront();
   });
   Robot r=new Robot();r.setAutoDelay(100);r.waitForIdle();Thread.sleep(500);
   SwingUtilities.invokeAndWait(()->{p[0]=b[0].getLocationOnScreen();p[0].translate(b[0].getWidth()/2,b[0].getHeight()/2);System.out.println("showing="+f[0].isShowing()+" active="+f[0].isActive());});
   r.mouseMove(p[0].x,p[0].y);r.mousePress(InputEvent.BUTTON1_DOWN_MASK);r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);r.waitForIdle();Thread.sleep(500);
   System.out.println("NATIVE counts pressed="+pressed+" released="+released+" action="+clicked);
   r.mouseMove(old.x,old.y);
  }finally{SwingUtilities.invokeAndWait(()->{if(f[0]!=null)f[0].dispose();});}
  System.exit(0);
 }
}
