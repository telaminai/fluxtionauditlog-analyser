package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.JPanel;
import javax.swing.JWindow;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * A lightweight custom splash shown at startup (pure Swing {@link JWindow}, no {@code -splash} image
 * file needed). Displays the generated banner, then closes once the main window is up.
 */
public final class SplashScreen extends JWindow {

    private static final int W = 520, H = 200;

    public SplashScreen() {
        BufferedImage banner = AppImages.splash(W, H,
                "Fluxtion Audit Log Analyser", "Loading…");
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(banner, 0, 0, null);
            }
        };
        panel.setPreferredSize(new Dimension(W, H));
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
    }

    public void showSplash() {
        setVisible(true);
    }

    public void close() {
        setVisible(false);
        dispose();
    }
}
