package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Capture the quickstart against the built jar, with an isolated user.home and DEMO Claude checks.
 * Compile with the built jar on the classpath; run with jar + compiled tools on the classpath,
 * -Djava.awt.headless=false and a fresh -Duser.home. The sole argument is the image output directory.
 * No Claude session is launched and Send is never pressed. Images are unmodified Swing component captures.
 */
public final class OnboardingDocCapture {
    private static MainFrame frame;
    private static Path output;
    private static final String QUESTION = "Help me build a temperature alert application. Ask me what it should do first.";

    private static Object field(Object instance, String name) {
        try {
            Field f = instance.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(instance);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }

    private static AiSetupState setup() {
        return ((SessionDriver) field(frame, "session")).snapshot().aiSetup();
    }

    private static void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        do {
            boolean[] yes = {false};
            edt(() -> yes[0] = ready.getAsBoolean());
            if (yes[0]) return;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new IllegalStateException("The expected onboarding screen did not appear");
    }

    private static void shot(Component component, String name) throws Exception {
        Thread.sleep(250); // Let layout and queued document listeners settle before painting.
        edt(() -> {
            Component view = component instanceof RootPaneContainer root ? root.getRootPane() : component;
            BufferedImage pic = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = pic.createGraphics();
            JRootPane root = SwingUtilities.getRootPane(view);
            Rectangle bounds = SwingUtilities.convertRectangle(view,
                    new Rectangle(0, 0, view.getWidth(), view.getHeight()), root);
            graphics.translate(-bounds.x, -bounds.y);
            root.printAll(graphics); // Preserve the real parent background for transparent panels.
            graphics.dispose();
            try { ImageIO.write(pic, "png", output.resolve(name).toFile()); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected an image output directory");
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        Files.createDirectories(home);
        if (!home.equals(home.toRealPath()) || Files.isSameFile(home, Path.of(System.getenv("HOME")))
                || Files.exists(home.resolve(".fluxtion-analyser"))) {
            throw new IllegalArgumentException("Use a fresh, isolated, non-symlinked user.home");
        }
        output = Files.createDirectories(Path.of(args[0]));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, """
                #!/bin/sh
                if [ "$1" = "--version" ]; then echo '2.1.282 (Claude Code)'; exit 0; fi
                if [ "$1" = "auth" ] && [ "$2" = "status" ]; then
                  echo '{"loggedIn":true,"authMethod":"claude.ai","apiProvider":"firstParty","email":"demo@example.invalid","orgId":"DEMO","orgName":"DEMO","subscriptionType":"max"}'
                  exit 0
                fi
                exit 1
                """);
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        int exit = 1;
        try {
            edt(() -> {
                ThemeManager.apply(new ConfigStore().load().theme);
                frame = new MainFrame();
                frame.setupEnvironmentForTest = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
                frame.localAgentLauncher = (command, extra, removed, dir, out) -> {
                    throw new AssertionError("A documentation capture must not start Claude");
                };
                frame.fullProfileConfirmModal = false;
                frame.setSize(1100, 760);
                frame.setVisible(true);
            });
            await(() -> setup().firstRun());
            StartPanel start = (StartPanel) field(frame, "startPanel");
            shot(start.aiSetupBlock(), "start-with-claude.png");
            edt(() -> start.aiSetupBlock().proceedButton().doClick());
            await(() -> setup().signedIn().verdict() == AiSetupState.SignedIn.SIGNED_IN);
            shot(frame.aiSetupWindow, "start-with-claude-setup.png");
            edt(() -> frame.aiSetupWindow.primaryButton().doClick());
            await(() -> frame.fullProfileAsked != null && frame.fullProfileAsked.dialog().isShowing());
            shot(frame.fullProfileAsked.dialog(), "start-with-claude-consent.png");
            edt(() -> frame.fullProfileAsked.riskyButton().doClick());
            await(() -> setup().step() == AiSetupState.Step.SUMMARY);
            edt(() -> frame.aiSetupWindow.setSize(640, 300));
            shot(frame.aiSetupWindow, "start-with-claude-ready.png");
            edt(() -> frame.aiSetupWindow.suggestedButton().doClick());
            await(() -> !setup().open() && start.prompt().box().isShowing());
            edt(() -> start.prompt().box().setText(QUESTION));
            await(() -> start.prompt().actionButton().isEnabled());
            shot(start.prompt().actionButton().getParent().getParent(), "start-with-claude-describe.png");
            edt(() -> start.prompt().actionButton().doClick());
            LocalAgentPanel panel = (LocalAgentPanel) field(frame, "localAgentPanel");
            JTextArea draft = (JTextArea) field(panel, "draft");
            await(() -> draft.isShowing() && draft.getText().contains(QUESTION));
            edt(() -> draft.setCaretPosition(0));
            shot(((AssistantPanel) field(frame, "assistantPanel")).composerRow(), "start-with-claude-send.png");
            System.out.println("Captured six onboarding screens; no Claude session or message was started.");
            exit = 0;
        } catch (Throwable failure) {
            failure.printStackTrace();
        } finally {
            edt(() -> { for (Window window : Window.getWindows()) window.dispose(); });
            System.exit(exit);
        }
    }
}
