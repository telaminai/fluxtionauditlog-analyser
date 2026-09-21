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
                assertTrue(first.ok(),first.toMap().toString());
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
