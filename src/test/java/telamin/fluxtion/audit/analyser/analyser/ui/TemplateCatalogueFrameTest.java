package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogue;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The actual modal picker: every catalogue entry and its declared requirements remain reachable. */
class TemplateCatalogueFrameTest {
    @Test void allEntriesAndDisclosuresAppearInTheRealPicker() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        String fixture = System.getProperty("journey.catalogue");
        String json = fixture == null ? """
                {"catalogue":1,"templates":[
                  {"name":"Embedded","description":"Embedded processor","file":"embedded.starter.json","mode":"aot"},
                  {"name":"Hosted","description":"Hosted processor","file":"hosted.starter.json","tags":["onboarding"],"agentBootstrap":["PROJECT.md"],"keyNeed":"build"},
                  {"name":"Bare","description":"Without bootstrap","file":"bare.starter.json","agentBootstrap":[],"keyNeed":"none"}
                ]}
                """ : Files.readString(Path.of(fixture));
        var selection = TemplateCatalogue.parse(json, "journey-test").forPicker();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<TemplateCatalogue.Entry> chosen = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            javax.swing.Timer probe = new javax.swing.Timer(200, event -> {
                JDialog dialog = Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog && w.isShowing())
                        .map(w -> (JDialog)w).filter(d -> "New project from template".equals(d.getTitle())).findFirst().orElse(null);
                if (dialog == null) {
                    if (System.nanoTime() > deadline) {
                        failure.set(new AssertionError("Template picker did not appear"));
                        ((javax.swing.Timer)event.getSource()).stop();
                        for (Window window : Window.getWindows()) if (window instanceof JDialog) window.dispose();
                    }
                    return;
                }
                ((javax.swing.Timer)event.getSource()).stop();
                try {
                    JList<?> list = find(dialog,JList.class);
                    JTextArea description = find(dialog,JTextArea.class);
                    assertNotNull(list); assertNotNull(description);
                    assertEquals(selection.entries().size(), list.getModel().getSize());
                    for (int i=0; i<selection.entries().size(); i++) {
                        list.setSelectedIndex(i);
                        assertEquals(selection.entries().get(i).disclosure(),description.getText());
                    }
                    int recommended = 0;
                    for (int i=0; i<selection.entries().size(); i++) if(selection.entries().get(i).recommended()) { recommended=i; break; }
                    list.setSelectedIndex(recommended);
                    String output = System.getProperty("journey.evidence");
                    if (output != null) {
                        Path dir = Files.createDirectories(Path.of(output));
                        var image = new java.awt.image.BufferedImage(dialog.getWidth(),dialog.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
                        var g=image.createGraphics(); try { dialog.paint(g); } finally {g.dispose();}
                        javax.imageio.ImageIO.write(image,"png",dir.resolve("template-catalogue.png").toFile());
                        Files.writeString(dir.resolve("catalogue.json"),json);
                    }
                } catch(Throwable t) { failure.set(t); }
                finally { dialog.dispose(); }
            });
            probe.start();
            try { chosen.set(TemplateProjectDialog.chooseTemplate(null,selection)); }
            finally { probe.stop(); }
        });
        if(failure.get()!=null) throw new AssertionError(failure.get());
        assertNull(chosen.get(), "Closing the dialog must not start a download");
    }
    private static <T extends Component> T find(Container parent,Class<T> type) {
        for(Component child:parent.getComponents()) {
            if(type.isInstance(child)) return type.cast(child);
            if(child instanceof Container container) {T found=find(container,type);if(found!=null)return found;}
        }
        return null;
    }
}
