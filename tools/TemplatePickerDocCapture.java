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
        // WHERE the two things a reader clicks are, in the captured image's own coordinates (the capture is of the
        // window's bounds, so window-relative IS image-relative). Printed for tools/capture-docs.py, which draws
        // the tutorial's "click here" marks with tools/AnnotateShot.java. Measured from the live components, so a
        // changed layout moves the marks with it instead of leaving them pointing at where a row used to be.
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JList<?> list = find(chooser, javax.swing.JList.class, c -> true);
            javax.swing.JButton use = find(chooser, javax.swing.JButton.class, b -> "Use this template".equals(b.getText()));
            if (list != null && list.getSelectedIndex() >= 0) {
                print("MARK row", SwingUtilities.convertRectangle(list, list.getCellBounds(list.getSelectedIndex(),
                        list.getSelectedIndex()).intersection(list.getVisibleRect()), chooser), renderedText(list));
            }
            if (use != null) print("MARK use", SwingUtilities.convertRectangle(use.getParent(), use.getBounds(), chooser), use.getText());
        });
        SwingUtilities.invokeAndWait(chooser::dispose);
    }

    /** What the selected row SHOWS — the renderer's text, not the model entry's toString() (a record dump). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String renderedText(javax.swing.JList list) {
        int i = list.getSelectedIndex();
        java.awt.Component cell = list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(i), i, true, false);
        return cell instanceof javax.swing.JLabel label ? label.getText() : String.valueOf(list.getSelectedValue());
    }

    private static void print(String what, Rectangle r, String label) {
        System.out.println(what + " " + r.x + "," + r.y + "," + r.width + "," + r.height + " " + label);
    }

    @SuppressWarnings("unchecked")
    private static <T extends java.awt.Component> T find(java.awt.Container root, Class<T> type, java.util.function.Predicate<T> wanted) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c) && wanted.test((T) c)) return (T) c;
            if (c instanceof java.awt.Container inner) {
                T hit = find(inner, type, wanted);
                if (hit != null) return hit;
            }
        }
        return null;
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
