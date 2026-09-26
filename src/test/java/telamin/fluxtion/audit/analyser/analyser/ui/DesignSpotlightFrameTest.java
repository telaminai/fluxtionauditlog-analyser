package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

class DesignSpotlightFrameTest {
    @Test void sourceViewportRefusesHiddenLinesAndAddReportsDepartures(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path xml=tmp.resolve("design.xml");
        var text=new StringBuilder("<beans>\n");
        for (int i=2;i<=150;i++) text.append("  <bean id=\"node").append(i).append("\" class=\"com.acme.Node\"/>\n");
        text.append("</beans>\n");Files.writeString(xml,text);
        try(var f=new Frame(tmp)) {
            onEdt(() -> {
                ((AppConfig)field(f.frame,"config")).sourceRoots.add(tmp.toString());
                f.frame.setSize(1300,850);f.frame.setVisible(true);f.frame.validate();
            });
            assertTrue(f.ex.render("open",Map.of("design",xml.toString())).ok());
            onEdt(() -> {
                var refused=f.ex.render("spotlight",Map.of("targets",List.of("source:design:line:3","source:design:line:140")));
                assertFalse(refused.ok(),"a set spanning beyond the source viewport must refuse");
                assertTrue(refused.toMap().toString().contains("cannot be on screen at the same time"),refused.toMap().toString());
                assertFalse(((SpotlightOverlay)field(f.frame,"spotlight")).isLit());
                var first=f.ex.render("spotlight",Map.of("targets",List.of("source:design:bean:node3","status")));
                assertTrue(first.ok(), () -> first.toMap() + " | " + designGeometry(f, 3));
                checkEcho(f,first.payload());
                checkBounds(f);
                var overlay=(SpotlightOverlay)field(f.frame,"spotlight");
                int status=overlay.lit().stream().filter(l->l.target().equals("status")).findFirst().orElseThrow().n();
                var added=f.ex.render("spotlight",Map.of("target","source:design:line:140","add",true));
                assertTrue(added.ok(),added.toMap().toString());
                checkEcho(f,added.payload());
                assertEquals(List.of("source:design:bean:node3"),added.payload().get("wentOut"),"offscreen old target must be accounted for");
                assertEquals(status,overlay.lit().stream().filter(l->l.target().equals("status")).findFirst().orElseThrow().n());
                checkBounds(f);
            });
            // Drain queued render/scroll/remeasure callbacks; accepted geometry must remain true.
            onEdt(() -> {}); onEdt(() -> {checkBounds(f);assertEquals(2,((SpotlightOverlay)field(f.frame,"spotlight")).lit().size());});
            Path saved=Path.of("docs/handoff/evidence/spring-authoring-feedback-2026-09-19-round2/src/main/fluxtion/designer/application-context.xml").toAbsolutePath();
            onEdt(() -> ((AppConfig)field(f.frame,"config")).sourceRoots.add(saved.getParent().toString()));
            assertTrue(f.ex.render("open",Map.of("design",saved.toString())).ok());
            for(int[] size:List.of(new int[]{1680,950},new int[]{1802,1085})) {
                onEdt(() -> {
                    f.frame.setSize(size[0],size[1]);f.frame.validate();
                    // The participant's pair of adjacent beans, from their exact preserved XML.
                    var result=f.ex.render("spotlight",Map.of("targets",List.of("source:design:bean:positionNode","source:design:bean:markToMarketNode")));
                    assertTrue(result.ok(),result.toMap().toString());checkBounds(f);checkEcho(f,result.payload());
                    var all=f.ex.render("spotlight",Map.of("targets",List.of("source:design:bean:positionNode","source:design:bean:markToMarketNode","source:design:line:31","source:design:line:38","source:design:line:45")));
                    if(all.ok()) {checkBounds(f);checkEcho(f,all.payload());}
                    else assertTrue(all.toMap().toString().contains("cannot be on screen at the same time"),all.toMap().toString());
                });
                onEdt(() -> {});onEdt(() -> checkBounds(f));
            }
        }
    }
    @Test void viewportMovementExtinguishesPartiallyVisibleDesignBand(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path xml=Files.writeString(tmp.resolve("design.xml"),"<beans>\n"+"  <!-- context -->\n".repeat(150)+"</beans>\n");
        try(var f=new Frame(tmp)) {
            onEdt(()->{((AppConfig)field(f.frame,"config")).sourceRoots.add(tmp.toString());f.frame.setSize(1300,850);f.frame.setVisible(true);});
            assertTrue(f.ex.render("open",Map.of("design",xml.toString())).ok());
            onEdt(()->{
                assertTrue(f.ex.render("spotlight",Map.of("target","source:design:line:30")).ok());
            });
            onEdt(()->{});onEdt(()->{});
            onEdt(()->{
                var panel=(SourcePanel)field(f.frame,"sourcePanel");var design=(DesignSourcePanel)panel.designComponent();
                var viewport=(JViewport)design.text.getParent();
                try {
                    int offset=design.text.getDocument().getDefaultRootElement().getElement(29).getStartOffset();
                    var rect=design.text.modelToView2D(offset).getBounds();
                    viewport.setViewPosition(new java.awt.Point(0,rect.y+2));
                    assertTrue(design.lineBounds(30).isEmpty(),"released design policy refuses partial visibility");
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            onEdt(()->{});onEdt(()->assertFalse(((SpotlightOverlay)field(f.frame,"spotlight")).isLit(),"design viewport hook must extinguish the partial band"));
        }
    }
    private static String designGeometry(Frame f, int line) {
        SourcePanel source = (SourcePanel) field(f.frame, "sourcePanel");
        DesignSourcePanel design = (DesignSourcePanel) source.designComponent();
        try {
            int offset = telamin.fluxtion.audit.analyser.analyser.design.DesignDocument.offset(design.text.getText(), line, 1);
            return "file=" + design.file() + " visible=" + design.text.getVisibleRect()
                    + " size=" + design.text.getSize() + " preferred=" + design.text.getPreferredSize()
                    + " showing=" + design.text.isShowing() + " valid=" + design.isValid()
                    + " line=" + design.text.modelToView2D(offset) + " bounds=" + design.lineBounds(line);
        } catch (Exception error) { return error.toString(); }
    }

    /**
     * PR #35 review: the default-window test caught an uncapped status note on macOS but not on Linux, because how far
     * the note overflowed depended on the temporary path's length and the platform's font metrics. Here the note
     * overflows by construction — 200 lines — and the XML must keep usable height with the target line on screen.
     * Measured before any spotlight: a design reveal re-renders the note, which would put the short one back.
     */
    @Test void anOverflowingStatusNoteLeavesTheXmlItsHeight(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path xml=Files.writeString(tmp.resolve("application-context.xml"),"<beans>\n"
                +"  <bean id=\"rootNode\" class=\"com.example.RootNode\"/>\n"
                +"  <bean id=\"riskCheck\" class=\"com.example.RiskCheck\"/>\n"
                +"  <bean id=\"csvRejections\" class=\"com.example.CsvRejections\"/>\n</beans>\n");
        var defaults=new AppConfig();
        try(var f=new Frame(tmp)) {
            onEdt(() -> {
                ((AppConfig)field(f.frame,"config")).sourceRoots.add(tmp.toString());
                f.frame.setSize(defaults.windowW,defaults.windowH);f.frame.setVisible(true);f.frame.validate();
            });
            assertTrue(f.ex.render("open",Map.of("design",xml.toString())).ok());
            assertTrue(f.ex.render("spotlight",Map.of("target","tab:source")).ok(),"control: the Source tab is on screen");
            onEdt(() -> {
                var source=(SourcePanel)field(f.frame,"sourcePanel");
                source.designNote(String.join("\n",java.util.Collections.nCopies(200,"an overflowing status note line")));
                f.frame.validate();
                var design=(DesignSourcePanel)source.designComponent();
                assertTrue(design.text.isShowing(),"control: the design text is in the showing tree");
                design.revealLine(4);
                f.frame.validate();
                int panel=design.getHeight(), xmlHeight=design.text.getVisibleRect().height;
                assertTrue(xmlHeight>=panel/2,"an overflowing status note must leave the XML its height: xml "+xmlHeight
                        +" of "+panel+" px");
                assertTrue(design.lineBounds(4).isPresent(),"the target line stays on screen under an overflowing note");
            });
        }
    }

    /**
     * 2026-09-26 fresh-look run: at the default window size the design panel is ~212 px wide and the fixed 210 px
     * bean list left the XML ~2 px, so every bean and line target was refused as "session design is unavailable"
     * while context listed the bean. Measured here at the window size a first start gets.
     */
    @Test void atTheDefaultWindowSizeTheXmlIsReadableAndABeanCanBeLit(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path xml=Files.writeString(tmp.resolve("application-context.xml"),"<beans>\n"
                +"  <bean id=\"rootNode\" class=\"com.example.RootNode\"/>\n"
                +"  <bean id=\"riskCheck\" class=\"com.example.RiskCheck\"/>\n"
                +"  <bean id=\"csvRejections\" class=\"com.example.CsvRejections\"/>\n"
                +"  <bean id=\"perfMon\" class=\"com.example.PerfMon\"/>\n</beans>\n");
        var defaults=new AppConfig();
        try(var f=new Frame(tmp)) {
            onEdt(() -> {
                ((AppConfig)field(f.frame,"config")).sourceRoots.add(tmp.toString());
                f.frame.setSize(defaults.windowW,defaults.windowH);f.frame.setVisible(true);f.frame.validate();
            });
            assertTrue(f.ex.render("open",Map.of("design",xml.toString())).ok());
            onEdt(() -> {
                var result=f.ex.render("spotlight",Map.of("target","source:design:bean:csvRejections"));
                assertTrue(result.ok(),"a declared bean lights at the default window size: "+result.error()+" | "+designGeometry(f,4));
            });
            onEdt(() -> {
                var design=(DesignSourcePanel)((SourcePanel)field(f.frame,"sourcePanel")).designComponent();
                int panel=design.getWidth(), xmlWidth=design.text.getVisibleRect().width;
                assertTrue(xmlWidth>=panel*0.6,"the XML keeps most of the design panel: xml "+xmlWidth+" of "+panel);
                checkBounds(f);
                var missing=f.ex.render("spotlight",Map.of("target","source:design:bean:nope"));
                assertFalse(missing.ok());
                assertTrue(String.valueOf(missing.error()).contains("no bean 'nope' in "),"a missing bean says so: "+missing.error());
            });
            // Owner report: two spotlights on neighbouring lines each drew an edge through the other's line of code.
            onEdt(() -> {
                var both=f.ex.render("spotlight",Map.of("targets",List.of("source:design:bean:riskCheck","source:design:bean:csvRejections")));
                assertTrue(both.ok(),"neighbouring beans light together: "+both.error());
            });
            onEdt(() -> {
                var overlay=(SpotlightOverlay)field(f.frame,"spotlight");
                var design=(DesignSourcePanel)((SourcePanel)field(f.frame,"sourcePanel")).designComponent();
                java.awt.Rectangle up=overlay.cutOutOf("source:design:bean:riskCheck"), low=overlay.cutOutOf("source:design:bean:csvRejections");
                var line3=SwingUtilities.convertRectangle(design,design.lineBounds(3).orElseThrow(),overlay);
                var line4=SwingUtilities.convertRectangle(design,design.lineBounds(4).orElseThrow(),overlay);
                assertEquals(up.y+up.height,low.y,"one separator between neighbouring lines: "+up+" / "+low);
                assertFalse(low.y>line3.y && low.y<line3.y+line3.height,"the lower outline's top stays off line 3: "+low+" / "+line3);
                int bottom=up.y+up.height;
                assertFalse(bottom>line4.y && bottom<line4.y+line4.height,"the upper outline's bottom stays off line 4: "+up+" / "+line4);
                checkBounds(f);
            });
        }
    }

    private static void checkEcho(Frame f, Map<String,Object> echo) {
        var overlay=(SpotlightOverlay)field(f.frame,"spotlight");
        for(Object item:(List<?>)echo.get("lit")) {
            var entry=(Map<?,?>)item;var b=(Map<?,?>)entry.get("bounds");
            var actual=SwingUtilities.convertRectangle(overlay,overlay.cutOutOf(entry.get("target").toString()),f.frame.getContentPane());
            assertEquals(Map.of("x",actual.x,"y",actual.y,"width",actual.width,"height",actual.height),b,"echo must use painted cutout coordinates");
        }
    }
    private static void checkBounds(Frame f) {
        var overlay=(SpotlightOverlay)field(f.frame,"spotlight");
        var source=(SourcePanel)field(f.frame,"sourcePanel");
        var design=(DesignSourcePanel)source.designComponent();
        var viewport=SwingUtilities.convertRectangle(design.text,design.text.getVisibleRect(),overlay);
        var visible=design.text.getVisibleRect();
        int offset=design.text.viewToModel2D(new java.awt.Point(visible.x,visible.y+1));
        int top=telamin.fluxtion.audit.analyser.analyser.design.DesignDocument.lineAt(design.text.getText(),offset);
        if(top>1) assertTrue(design.lineBounds(top-1).isEmpty(),"a line above the viewport must not be measurable in its header");
        for(var lit:overlay.lit()) {
            assertTrue(lit.bounds().width>0 && lit.bounds().height>0);
            if(lit.target().startsWith("source:")) assertTrue(viewport.contains(lit.bounds()),"source highlight must stay inside its own viewport: "+lit.bounds()+" / "+viewport);
            var cut=SwingUtilities.convertRectangle(overlay,overlay.cutOutOf(lit.target()),f.frame.getContentPane());
            assertTrue(cut.width>0 && cut.height>0 && cut.x>=0 && cut.y>=0,"positive screenshot bounds: "+cut);
        }
    }
}
