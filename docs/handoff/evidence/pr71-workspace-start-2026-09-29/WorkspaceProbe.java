package telamin.fluxtion.audit.analyser.analyser.ui;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import telamin.fluxtion.audit.analyser.bundle.EvidenceBundle;
public class WorkspaceProbe {
 static MainFrame f; static Path out; static String mode;
 interface Work<T>{T run() throws Exception;}
 static <T>T edt(Work<T>w)throws Exception { if(SwingUtilities.isEventDispatchThread())return w.run(); FutureTask<T> t=new FutureTask<>(()->w.run());SwingUtilities.invokeAndWait(t);return t.get(); }
 static Object field(Object x,String n)throws Exception{Class<?>c=x.getClass();while(c!=null){try{Field v=c.getDeclaredField(n);v.setAccessible(true);return v.get(x);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new Exception(n);}
 static Object call(String n,Class<?>[] types,Object... a)throws Exception{Method m=MainFrame.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(f,a);}
 static java.util.List<Component> all(Container c){var r=new ArrayList<Component>();for(Component x:c.getComponents()){r.add(x);if(x instanceof Container v)r.addAll(all(v));}return r;}
 static String name(Component c){return c.getAccessibleContext()==null?"":String.valueOf(c.getAccessibleContext().getAccessibleName());}
 static JButton button(String s)throws Exception{return edt(()->all(f).stream().filter(c->c instanceof JButton&&name(c).equals(s)).map(c->(JButton)c).findFirst().orElseThrow());}
 static void waitFor(Work<Boolean> p)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!edt(p)){if(System.nanoTime()>end)throw new AssertionError("condition timed out");Thread.sleep(25);}}
 static void shot(String n)throws Exception { for(int i=0;i<4;i++){new Robot().waitForIdle();edt(()->{f.validate();return null;});} edt(()->{f.validate();BufferedImage b=new BufferedImage(f.getWidth(),f.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=b.createGraphics();f.paintAll(g);g.dispose();javax.imageio.ImageIO.write(b,"png",out.resolve(n+".png").toFile());return null;});}
 static void visibleButtons(String label)throws Exception{System.out.println(label+": "+edt(()->all(f).stream().filter(c->c instanceof JButton&&c.isShowing()).map(WorkspaceProbe::name).toList()));}
 static void project(Path p)throws Exception{edt(()->call("requestProject",new Class[]{Path.class,TransitionKind.class,String.class},p,TransitionKind.EXPLICIT_SWITCH,"review"));}
 static Path newProject(String n)throws Exception {Path p=ProjectProfile.pathFor(Files.createDirectories(out.resolve(n)));ProjectProfile.save(p,new AppConfig(),new SettingsShare());return p;}
 static SessionDriver session()throws Exception{return(SessionDriver)field(f,"session");}
 public static void main(String[]args)throws Exception{out=Path.of(args[0]);mode=args[1];Files.createDirectories(out);System.setProperty("user.home",Files.createDirectories(out.resolve("home-"+mode+"-"+System.nanoTime())).toString());try{
  edt(()->{com.formdev.flatlaf.FlatLightLaf.setup(); f=new MainFrame();f.setSize(1200,800);f.setLocation(30,30);f.setVisible(true);f.toFront();return null;});
  if(mode.equals("visual")){
   shot("start-light");visibleButtons("first-run");
   edt(()->{com.formdev.flatlaf.FlatDarkLaf.setup();SwingUtilities.updateComponentTreeUI(f);return null;});shot("start-dark");
   edt(()->{f.setSize(700,650);return null;});shot("start-narrow-settled"); System.out.println("narrow-card-bounds="+edt(()->all(f).stream().filter(c->c instanceof JButton&&name(c).equals("Investigate an incident")).map(c->c.getBounds()+" parent="+c.getParent().getBounds()+" visible="+((JComponent)c).getVisibleRect()).toList()));
   edt(()->{com.formdev.flatlaf.FlatLightLaf.setup();SwingUtilities.updateComponentTreeUI(f);f.setSize(1200,800);return null;});
   Path p=newProject("DEMO-existing");project(p);edt(()->call("showStartPage",new Class[]{}));shot("returning-no-log");visibleButtons("returning-no-log");
   System.out.println("menuShowing="+edt(()->f.getJMenuBar().isShowing()));
  } else if(mode.equals("tour")){
   edt(()->{button("Take a guided tour").doClick();return null;});
   for(int step=0;step<4;step++){final int wanted=step;waitFor(()->session().snapshot().walkPlayback().step()==wanted&&"SHOWN".equals(session().snapshot().walkPlayback().phase()));
    System.out.println("tour "+step+" "+edt(()->session().snapshot().walkPlayback()));shot("tour-"+step);
    if(step<3)edt(()->{SpotlightOverlay ov=(SpotlightOverlay)field(f,"spotlight");Method m=SpotlightOverlay.class.getDeclaredMethod("stripControlAt",Point.class);m.setAccessible(true);Point found=null;for(int y=0;y<ov.getHeight()&&found==null;y+=4)for(int x=0;x<ov.getWidth();x+=4)if(m.invoke(ov,new Point(x,y))==SpotlightOverlay.StripControl.NEXT){found=new Point(x,y);break;}if(found==null)throw new AssertionError("next absent");ov.dispatchEvent(new MouseEvent(ov,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,found.x,found.y,1,false,MouseEvent.BUTTON1));return null;});
   }
  } else if(mode.equals("bundle-race")){
   Path payload=Files.createDirectories(out.resolve("payload"));Path pr=payload.resolve("profile/project.fluxtion-settings");ProjectProfile.save(pr,new AppConfig(),new SettingsShare());Files.createDirectories(payload.resolve("log"));Files.copy(Path.of("src/test/resources/topology/demo-quote-audit.yaml"),payload.resolve("log/demo.yaml"),StandardCopyOption.REPLACE_EXISTING);
   Path bundle=out.resolve("DEMO.fexp");EvidenceBundle.pack(payload,bundle,java.time.Instant.parse("2026-01-01T00:00:00Z"),"DEMO");Path newer=newProject("DEMO-newer");
   javax.swing.Timer dismiss=new javax.swing.Timer(30,e->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.getTitle().equals("Experiment verified"))d.dispose();});edt(()->{dismiss.start();return null;});
   // Hold the EDT until the worker has queued its success callback. Then process a newer user request,
   // before returning to the event queue. The bundle source and verification remain unmodified.
   edt(()->{call("loadExperiment",new Class[]{Path.class},bundle);long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);Path copies=Path.of(System.getProperty("user.home"),".fluxtion-analyser/bundles");while(System.nanoTime()<limit){if(Files.exists(copies)){try(var paths=Files.walk(copies)){if(paths.anyMatch(q->q.endsWith("project.fluxtion-settings")))break;}}Thread.sleep(10);}call("requestProject",new Class[]{Path.class,TransitionKind.class,String.class},newer,TransitionKind.EXPLICIT_SWITCH,"review-newer");System.out.println("newer request active="+((ProjectSession)field(f,"project")).activeFile().getFileName());return null;});
   waitFor(()->field(f,"store")!=null);System.out.println("newer project survived="+edt(()->newer.equals(((ProjectSession)field(f,"project")).activeFile())));System.out.println("active is bundle="+edt(()->((ProjectSession)field(f,"project")).activeFile().toString().contains("bundles")));edt(()->{dismiss.stop();return null;});
  } else if(mode.equals("keyboard")){
   Robot robot=new Robot();robot.setAutoDelay(40);JButton start=button("Take a guided tour");edt(()->{f.toFront();f.requestFocus();start.requestFocusInWindow();return null;});
   try{waitFor(()->start.isFocusOwner());}catch(AssertionError e){System.out.println("UNVERIFIED keyboard: could not acquire native focus");System.exit(0);}
   System.out.println("keyboard initial="+edt(()->name(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner())));
   robot.keyPress(KeyEvent.VK_TAB);robot.keyRelease(KeyEvent.VK_TAB);robot.waitForIdle();System.out.println("keyboard Tab="+edt(()->name(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner())));
   robot.keyPress(KeyEvent.VK_SHIFT);robot.keyPress(KeyEvent.VK_TAB);robot.keyRelease(KeyEvent.VK_TAB);robot.keyRelease(KeyEvent.VK_SHIFT);robot.keyPress(KeyEvent.VK_SPACE);robot.keyRelease(KeyEvent.VK_SPACE);robot.waitForIdle();waitFor(()->session().snapshot().walkPlayback().showing());System.out.println("keyboard Space started tour="+edt(()->session().snapshot().walkPlayback().showing()));
   waitFor(()->"SHOWN".equals(session().snapshot().walkPlayback().phase()));robot.keyPress(KeyEvent.VK_RIGHT);robot.keyRelease(KeyEvent.VK_RIGHT);robot.waitForIdle();waitFor(()->session().snapshot().walkPlayback().step()==1);System.out.println("keyboard Right step="+edt(()->session().snapshot().walkPlayback().step()));
  } else if(mode.equals("native-drop")){
   Path log=Path.of("src/test/resources/topology/demo-quote-audit.yaml").toAbsolutePath();
   var source=edt(()->{JFrame sf=new JFrame("DEMO file drag source"); JLabel l=new JLabel("Drag DEMO audit file",SwingConstants.CENTER);l.setTransferHandler(new TransferHandler(){public int getSourceActions(JComponent c){return COPY;} protected void exportDone(JComponent c,java.awt.datatransfer.Transferable t,int action){System.out.println("native exportDone action="+action);} protected java.awt.datatransfer.Transferable createTransferable(JComponent c){System.out.println("native drag started");return new java.awt.datatransfer.Transferable(){public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors(){return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.javaFileListFlavor};}public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor d){return d.equals(java.awt.datatransfer.DataFlavor.javaFileListFlavor);}public Object getTransferData(java.awt.datatransfer.DataFlavor d){return java.util.List.of(log.toFile());}};}});l.addMouseMotionListener(new MouseMotionAdapter(){public void mouseDragged(MouseEvent e){l.getTransferHandler().exportAsDrag(l,e,TransferHandler.COPY);}});sf.add(l);sf.setBounds(1230,40,240,160);sf.setVisible(true);return l;});
   Robot robot=new Robot();robot.setAutoDelay(15);robot.waitForIdle();
   for(String target:new String[]{"body","body","card"}){
    Point from=edt(()->{Point q=source.getLocationOnScreen();q.translate(100,70);return q;});
    Point to=edt(()->{Component c=target.equals("body")?all(f).stream().filter(x->x instanceof JTextArea a&&a.getText().startsWith("Open evidence,")).findFirst().orElseThrow():button("Open audit log");Point q=c.getLocationOnScreen();q.translate(30,10);System.out.println(target+" transfer="+((JComponent)c).getTransferHandler()+" dropTarget="+c.getDropTarget());return q;});
    robot.mouseMove(from.x,from.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);for(int i=1;i<=40;i++)robot.mouseMove(from.x+(to.x-from.x)*i/40,from.y+(to.y-from.y)*i/40);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<end&&edt(()->field(f,"store")==null))Thread.sleep(20);
    System.out.println("native "+target+" logOpened="+edt(()->field(f,"store")!=null));if(edt(()->field(f,"store")!=null))break;
   }
  } else if(mode.equals("mixed-drop")){
   Path b=Files.writeString(out.resolve("DEMO-invalid.fexp"),"invalid");Path log=Path.of("src/test/resources/topology/demo-quote-audit.yaml").toAbsolutePath();System.out.println("mixed accepted="+edt(()->call("openDroppedFiles",new Class[]{java.util.List.class},java.util.List.of(b.toFile(),log.toFile()))));System.out.println("mixed message="+edt(()->((JLabel)field(f,"status")).getText()));System.out.println("mixed message showing="+edt(()->((JLabel)field(f,"status")).isShowing()));
  } else if(mode.equals("xml")){
   Path xml=Files.writeString(out.resolve("DEMO-design.xml"),"<beans><bean id='DEMO'/></beans>");
   System.out.println("xml accepted="+edt(()->call("openDroppedFiles",new Class[]{java.util.List.class},java.util.List.of(xml.toFile()))));
   System.out.println("status="+edt(()->((JLabel)field(f,"status")).getText()));System.out.println("status showing="+edt(()->((JLabel)field(f,"status")).isShowing()));long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<end && edt(()->session().processor().designSession.path()==null))Thread.sleep(25);System.out.println("XML final path="+edt(()->session().processor().designSession.path()));System.out.println("XML final status="+edt(()->((JLabel)field(f,"status")).getText()));shot("xml-settled");
  }
 }finally{if(f!=null)edt(()->{for(Window w:Window.getWindows())w.dispose();return null;});}System.exit(0);}
}
