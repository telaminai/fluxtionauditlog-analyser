package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.Component;
import java.awt.event.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import telamin.fluxtion.audit.analyser.analyser.source.*;
import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.await;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.opened;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.walk;

/** Own probes, DEMO-only; built released jar plus the released test helpers, never a substitute presenter. */
public class JavaWalkProbeTest {
    static final String FQN="com.acme.DemoNode", TARGET="source:java:"+FQN+":line:3";
    static final String SOURCE="package com.acme;\npublic class DemoNode {\n    double spread() { return 0.004; }\n"+"    // DEMO context\n".repeat(80)+"}\n";
    static Path tmp(String label)throws Exception{return Files.createTempDirectory("DEMO-issue84-"+label+"-");}
    static SourceService service(Frame f){return (SourceService)field(f.frame,"sourceService");}
    static SpotlightOverlay overlay(Frame f){return (SpotlightOverlay)field(f.frame,"spotlight");}
    static JTabbedPane tabs(Frame f){return (JTabbedPane)field(f.frame,"sideTabs");}
    static void set(Object object,String name,Object value)throws Exception{var p=object.getClass().getDeclaredField(name);p.setAccessible(true);p.set(object,value);}
    static void call(Frame f,String verb,Map<String,Object> p)throws Exception{onEdt(()->{var reply=render(f.ex,verb,p);assertEquals(true,reply.get("ok"),reply.toString());});}
    static Map<String,Object> step(String target){return Map.of("view",Map.of("tab","source"),"targets",List.of(Map.of("target",target,"caption","DEMO the spread is 0.004")));}
    static void save(Frame f,List<Map<String,Object>> steps)throws Exception{call(f,"walk",Map.of("name","DEMO source walk","steps",steps));}
    static void play(Frame f)throws Exception{call(f,"walk",Map.of("name","DEMO source walk","play",true));}
    static void snapshot(Frame f,String label)throws Exception{onEdt(()->System.out.println(label+": phase="+walk(f).phase()+" showing="+walk(f).showing()+" step="+walk(f).step()+" reason="+walk(f).reason()+" walkOwn="+field(f.frame,"walkOwnSpotlight")+" lit="+overlay(f).lit().stream().map(SpotlightOverlay.Lit::target).toList()));}
    static void capture(Frame f,String name)throws Exception{
        onEdt(()->{boolean lower=name.equals("walk-changed-source")||name.equals("walk-after-view-change");int y=lower?630:0;var image=new java.awt.image.BufferedImage(f.frame.getWidth(),f.frame.getHeight()-y,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.translate(0,-y);f.frame.printAll(g);g.dispose();try{javax.imageio.ImageIO.write(image,"png",Path.of(System.getProperty("issue84.captureDir"),name+".png").toFile());}catch(Exception e){throw new AssertionError(e);}});
    }
    static Path source(Path tmp)throws Exception{Path p=tmp.resolve("src/com/acme/DemoNode.java");Files.createDirectories(p.getParent());Files.writeString(p,SOURCE);return p;}
    static void roots(Frame f,Path root)throws Exception{onEdt(()->{AppConfig c=(AppConfig)field(f.frame,"config");c.sourceRoots.clear();c.sourceRoots.add(root.toString());service(f).configure(c.sourceRoots,null);});}
    static Object archives(Frame f,Path tmp,boolean prime)throws Exception{
        Path repo=Files.createDirectories(tmp.resolve("repo"));
        JavaSourceSpotlightFrameTest.jar(repo.resolve("DEMO-sources.jar"),Map.of("com/acme/DemoNode.java",SOURCE));
        onEdt(()->service(f).configure(List.of(),null,List.of(repo.toString()),true));
        // Cached availability succeeds; fresh asynchronous preparation then reaches the real discovery monitor.
        if(prime)assertTrue(service(f).sourceForFqn(FQN).isPresent());
        Object a=field(service(f),"maven");if(prime)set(a,"jars",null);
        return a;
    }
    static boolean blocked(boolean edt)throws Exception{
        for(int attempt=0;attempt<300;attempt++){
            for(var e:Thread.getAllStackTraces().entrySet())
                if(e.getKey().getState()==Thread.State.BLOCKED && (edt==e.getKey().getName().startsWith("AWT-EventQueue"))
                    && java.util.Arrays.stream(e.getValue()).anyMatch(s->s.getMethodName().equals("jarList")))return true;
            Thread.sleep(10);
        }
        return false;
    }
    static void waitFinished(Frame f)throws Exception{await("source terminal result",()->!((Boolean)field(f.frame,"walkOwnSpotlight")));onEdt(()->{});onEdt(()->{});}

    @Test void availabilityMustNotReadOnTheEventThread()throws Exception{
        Path tmp=tmp("availability");try(var f=opened(tmp)){
            Object a=archives(f,tmp,false);save(f,List.of(step(TARGET)));
            CountDownLatch sentinel=new CountDownLatch(1);boolean free;
            synchronized(a){
                play(f);assertTrue(blocked(true),"real EDT source read must be reached");
                SwingUtilities.invokeLater(sentinel::countDown);free=sentinel.await(200,TimeUnit.MILLISECONDS);
                System.out.println("availability: EDT sentinel ran while archive lookup held="+free);
            }
            assertTrue(sentinel.await(5,TimeUnit.SECONDS));waitFinished(f);
            assertTrue(free,"javaWalkAvailabilityMustNotBlockTheEDT");
        }
    }
    @Test void unreadSourceMustStayPreparing()throws Exception{
        Path tmp=tmp("phase");try(var f=opened(tmp)){
            Object a=archives(f,tmp,true);save(f,List.of(step(TARGET)));String[] phase={null};
            synchronized(a){play(f);assertTrue(blocked(false));onEdt(()->phase[0]=walk(f).phase());snapshot(f,"read still held");capture(f,"walk-held-read");}
            waitFinished(f);snapshot(f,"read returned");
            assertEquals("PREPARING",phase[0],"aJavaWalkMustNotClaimSHOWNBeforeItHasReadOrLitTheSource");
        }
    }
    @Test void anUnavailableLineReportsNotShown()throws Exception{
        Path tmp=tmp("bad-line");source(tmp);try(var f=opened(tmp)){
            roots(f,tmp.resolve("src"));save(f,List.of(step("source:java:"+FQN+":line:99999")));play(f);
            await("line refusal",()->"NOT_SHOWN".equals(walk(f).phase()));snapshot(f,"unavailable line");
            assertFalse(overlay(f).isLit());assertTrue(walk(f).reason().contains("outside"));
        }
    }
    @Test void aNeverReturningPreparationExpiresAndReleasesOwnership()throws Exception{
        Path tmp=tmp("deadline");try(var f=opened(tmp)){
            Object a=archives(f,tmp,true);onEdt(()->{try{set(f.frame,"javaSourcePreparationTimeout",Duration.ofMillis(200));}catch(Exception e){throw new AssertionError(e);}});
            save(f,List.of(step(TARGET)));
            synchronized(a){play(f);assertTrue(blocked(false));await("deadline",()->"NOT_SHOWN".equals(walk(f).phase()));snapshot(f,"deadline while held");assertEquals(false,field(f.frame,"walkOwnSpotlight"));assertTrue(walk(f).reason().contains("deadline expired"));}
            Thread.sleep(100);onEdt(()->assertFalse(overlay(f).isLit(),"late completion cannot light after expiry"));
        }
    }
    @Test void aViewChangeDuringReadMustNotUndoThePersonsChoice()throws Exception{
        Path tmp=tmp("view");try(var f=opened(tmp)){
            Object a=archives(f,tmp,true);save(f,List.of(step(TARGET)));
            synchronized(a){play(f);assertTrue(blocked(false));onEdt(()->{Action previous=tabs(f).getActionMap().get("navigatePrevious");assertNotNull(previous,"native keyboard tab action exists");previous.actionPerformed(new ActionEvent(tabs(f),ActionEvent.ACTION_PERFORMED,"DEMO keyboard Previous tab"));assertEquals(field(f.frame,"summaryPanel"),tabs(f).getSelectedComponent(),"native keyboard action selected Summary");});snapshot(f,"person selected Summary");}
            waitFinished(f);snapshot(f,"late source after Summary");capture(f,"walk-after-view-change");
            assertEquals(field(f.frame,"summaryPanel"),tabs(f).getSelectedComponent(),"lateWalkReadMustNotReplaceThePersonsNewView");
        }
    }
    @Test void aClosedSourceTabCannotBeClaimedShown()throws Exception{
        Path tmp=tmp("closed-source");try(var f=opened(tmp)){
            Object a=archives(f,tmp,true);save(f,List.of(step(TARGET)));
            synchronized(a){play(f);assertTrue(blocked(false));onEdt(()->tabs(f).remove((Component)field(f.frame,"sourcePanel")));}
            waitFinished(f);snapshot(f,"source tab removed during read");assertEquals("NOT_SHOWN",walk(f).phase());assertFalse(overlay(f).isLit());
        }
    }
    @Test void projectSwitchCannotResurrectALateStep()throws Exception{
        Path tmp=tmp("project");try(var f=opened(tmp)){
            Object a=archives(f,tmp,true);save(f,List.of(step(TARGET)));Path profile=ProjectProfile.pathFor(tmp.resolve("recipient"));ProjectProfile.save(profile,new AppConfig(),new SettingsShare());
            synchronized(a){play(f);assertTrue(blocked(false));call(f,"open",Map.of("project",profile.toString()));}
            waitFinished(f);snapshot(f,"late result after project switch");assertFalse(walk(f).showing());assertFalse(overlay(f).isLit());
        }
    }
    @Test void changedRootsRefuseTheLateRead()throws Exception{
        Path tmp=tmp("roots");try(var f=opened(tmp)){
            Object a=archives(f,tmp,true);save(f,List.of(step(TARGET)));
            synchronized(a){play(f);assertTrue(blocked(false));call(f,"source_root",Map.of("add",List.of(Files.createDirectories(tmp.resolve("other")).toString())));}
            waitFinished(f);snapshot(f,"late result after root change");assertEquals("NOT_SHOWN",walk(f).phase());assertFalse(overlay(f).isLit());assertTrue(walk(f).reason().contains("superseded"));
        }
    }
    @Test void backAndNextDoNotLightTheSupersededRead()throws Exception{
        Path tmp=tmp("navigation");try(var f=opened(tmp)){
            Object a=archives(f,tmp,true);save(f,List.of(step(TARGET),Map.of("view",Map.of("tab","summary"),"targets",List.of(Map.of("target","status","caption","DEMO second step")))));
            synchronized(a){play(f);assertTrue(blocked(false));onEdt(()->((SessionDriver)field(f.frame,"session")).post(new SessionEvents.WalkNavigated(1)));await("second step lit",()->walk(f).step()==1&&overlay(f).lit().stream().anyMatch(l->l.target().equals("status")));snapshot(f,"Next while first read held");}
            Thread.sleep(250);onEdt(()->{});snapshot(f,"old read returned after Next");assertEquals(1,walk(f).step());assertTrue(overlay(f).lit().stream().noneMatch(l->l.target().equals(TARGET)));
            onEdt(()->((SessionDriver)field(f.frame,"session")).post(new SessionEvents.WalkNavigated(-1)));await("Back re-prepared source",()->overlay(f).lit().stream().anyMatch(l->l.target().equals(TARGET)));assertEquals(0,walk(f).step());
        }
    }
    @Test void aMovedJavaLineMustNotBeMarkedCurrentAgainstTheSavedWalk()throws Exception{
        Path tmp=tmp("revision");Path p=source(tmp);try(var f=opened(tmp)){
            roots(f,tmp.resolve("src"));save(f,List.of(step(TARGET)));
            Files.writeString(p,SOURCE.replace("return 0.004","return 999"));play(f);await("edited source shown",()->overlay(f).isLit());
            snapshot(f,"changed Java source");onEdt(()->System.out.println("changed source target verdict="+walk(f).targets().getFirst()+" caption="+overlay(f).lit().getFirst().caption()));capture(f,"walk-changed-source");
            assertNotEquals("CURRENT",walk(f).targets().getFirst().state(),"savedJavaLineMustNotClaimCurrentAfterItsTextChanges");
        }
    }
    @Test void anUnreadableResolvedClassReportsFailureInsteadOfPreparingForever()throws Exception {
        Path tmp=tmp("encoding");Path p=source(tmp);Files.write(p,new byte[]{(byte)0xe9,10});
        AtomicReference<Throwable> seen=new AtomicReference<>();AtomicReference<Thread.UncaughtExceptionHandler> previous=new AtomicReference<>();
        try(var f=opened(tmp)) {
            roots(f,tmp.resolve("src"));onEdt(()->{previous.set(Thread.currentThread().getUncaughtExceptionHandler());Thread.currentThread().setUncaughtExceptionHandler((t,e)->seen.set(e));});
            try{save(f,List.of(step(TARGET)));play(f);await("availability read exception",()->seen.get()!=null);snapshot(f,"resolved file cannot be decoded");System.out.println("availability exception="+seen.get());assertEquals("NOT_SHOWN",walk(f).phase(),"sourceAvailabilityFailureMustSettleHonestlyInsteadOfLeavingPREPARING");}
            finally{onEdt(()->Thread.currentThread().setUncaughtExceptionHandler(previous.get()));}
        }
    }
    @Test void backDuringTheOriginalReadMustNotBlockTheEventThread()throws Exception {
        Path tmp=tmp("back-held");try(var f=opened(tmp)) {
            Object a=archives(f,tmp,true);save(f,List.of(step(TARGET),Map.of("view",Map.of("tab","summary"),"targets",List.of(Map.of("target","status","caption","DEMO second step")))));
            CountDownLatch sentinel=new CountDownLatch(1);boolean free;
            synchronized(a){
                play(f);assertTrue(blocked(false));onEdt(()->((SessionDriver)field(f.frame,"session")).post(new SessionEvents.WalkNavigated(1)));await("Next shown while first read held",()->walk(f).step()==1&&overlay(f).isLit());
                onEdt(()->((SessionDriver)field(f.frame,"session")).post(new SessionEvents.WalkNavigated(-1)));
                boolean blocked=false;for(int i=0;i<300&&!blocked;i++){blocked=Thread.getAllStackTraces().entrySet().stream().anyMatch(e->e.getKey().getName().startsWith("AWT-EventQueue")&&e.getKey().getState()==Thread.State.BLOCKED&&java.util.Arrays.stream(e.getValue()).anyMatch(st->st.getMethodName().equals("sourceForFqn")));if(!blocked)Thread.sleep(10);}
                assertTrue(blocked,"Back reached the real availability read");SwingUtilities.invokeLater(sentinel::countDown);free=sentinel.await(200,TimeUnit.MILLISECONDS);System.out.println("Back during read: EDT sentinel ran="+free);
            }
            assertTrue(sentinel.await(5,TimeUnit.SECONDS));await("Back eventually lit after releasing the read",()->overlay(f).lit().stream().anyMatch(l->l.target().equals(TARGET)));
            assertTrue(free,"BackDuringAReadMustNotBlockTheEDTOnTheOriginalLookup");
        }
    }

}
