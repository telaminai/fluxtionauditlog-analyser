package telamin.fluxtion.audit.analyser.analyser.ui;

import com.formdev.flatlaf.FlatLightLaf;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateClient;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Generated documentation capture of the real live-catalogue template chooser. */
public final class TemplatePickerDocCapture {
    private TemplatePickerDocCapture() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("need the documentation asset directory");
        Path assets = Path.of(args[0]);
        Files.createDirectories(assets);
        var selection = TemplateClient.playground().catalogue("documentation capture");
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> TemplateProjectDialog.chooseTemplate(null, selection));
        Window chooser = awaitWindow("New project from template");
        capture(chooser, assets.resolve("template-picker.png"));
        SwingUtilities.invokeAndWait(chooser::dispose);
    }

    private static Window awaitWindow(String title) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < end) {
            for (Window window : Window.getWindows()) {
                if (window.isDisplayable() && window.isShowing()
                        && window instanceof javax.swing.JDialog dialog && title.equals(dialog.getTitle())) {
                    return window;
                }
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("did not show window: " + title);
    }

    private static void capture(Window window, Path target) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            window.toFront();
            window.requestFocus();
        });
        Thread.sleep(500);
        Rectangle bounds = window.getBounds();
        GraphicsConfiguration graphics = window.getGraphicsConfiguration();
        BufferedImage image = new Robot(graphics.getDevice()).createScreenCapture(bounds);
        if (!ImageIO.write(image, "png", target.toFile())) throw new IllegalStateException("could not write " + target);
    }
}
