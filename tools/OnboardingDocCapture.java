package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.OsAgentLauncher;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
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
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/**
 * Capture the quickstart against the built jar, with an isolated user.home. Arguments: output directory [--live].
 * Without --live, use DEMO Claude checks and never start a session. With --live, use the existing Claude login,
 * send one neutral question and capture the real reply. The live launcher adds --strict-mcp-config and
 * --setting-sources project to keep personal servers/settings out of the capture; Full tools remain available.
 * Compile against the built jar, then add this class to a COPY of that jar (the bridge needs one jar classpath).
 * Run that copy with this main class, -Djava.awt.headless=false and a fresh -Duser.home, from a neutral directory.
 * Read every visible string before publishing. Images are unmodified Swing component captures; no stream is saved.
 */
public final class OnboardingDocCapture {
    private static MainFrame frame;
    private static Path output;
    private static final String QUESTION = "Help me build a temperature alert application. Ask me one question about the alert rule first.";

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

    private static LocalAgentState agent() {
        return ((SessionDriver) field(frame, "session")).snapshot().localAgent();
    }

    private static void await(BooleanSupplier ready) throws Exception {
        await(ready, 20);
    }

    private static void await(BooleanSupplier ready, int seconds) throws Exception {
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
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
        if (args.length < 1 || args.length > 2 || args.length == 2 && !"--live".equals(args[1]))
            throw new IllegalArgumentException("Expected an image output directory and optional --live");
        boolean live = args.length == 2;
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        Files.createDirectories(home);
        if (!home.equals(home.toRealPath()) || Files.isSameFile(home, Path.of(System.getenv("HOME")))
                || Files.exists(home.resolve(".fluxtion-analyser"))) {
            throw new IllegalArgumentException("Use a fresh, isolated, non-symlinked user.home");
        }
        output = Files.createDirectories(Path.of(args[0]));
        if (!live) {
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
        }
        int exit = 1;
        try {
            edt(() -> {
                ThemeManager.apply(new ConfigStore().load().theme);
                frame = new MainFrame();
                if (!live) frame.setupEnvironmentForTest = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
                frame.localAgentLauncher = (command, extra, removed, dir, out) -> {
                    if (!live) throw new AssertionError("A DEMO capture must not start Claude");
                    if (!command.contains("acceptEdits") || command.contains("--tools"))
                        throw new AssertionError("The live capture must exercise Full Claude Code");
                    var isolated = new ArrayList<>(command);
                    isolated.addAll(java.util.List.of("--strict-mcp-config", "--setting-sources", "project"));
                    return new OsAgentLauncher().start(isolated, extra, removed, dir, out);
                };
                frame.fullProfileConfirmModal = false;
                frame.setSize(1100, 760);
                frame.setVisible(true);
            });
            await(() -> setup().firstRun());
            StartPanel start = (StartPanel) field(frame, "startPanel");
            if (!live) shot(start.aiSetupBlock(), "start-with-claude.png");
            edt(() -> start.aiSetupBlock().proceedButton().doClick());
            await(() -> setup().signedIn().verdict() == AiSetupState.SignedIn.SIGNED_IN);
            if (!live) shot(frame.aiSetupWindow, "start-with-claude-setup.png");
            edt(() -> frame.aiSetupWindow.primaryButton().doClick());
            LocalAgentPanel panel = (LocalAgentPanel) field(frame, "localAgentPanel");
            JTextArea draft = (JTextArea) field(panel, "draft");
            await(() -> !setup().open() && draft.isShowing());
            edt(() -> {
                if (((SessionDriver) field(frame, "session")).snapshot().profiles().profile() != AssistantProfile.FULL
                        || agent().admittedGeneration() != 0)
                    throw new AssertionError("Setup must configure Full without starting Claude");
                draft.setText(QUESTION);
                draft.setCaretPosition(0);
            });
            if (live) {
                edt(() -> panel.sendButton().doClick());
                await(() -> agent().admittedGeneration() != 0, 60);
                await(() -> agent().openTicket() == 0 && agent().entries().stream()
                        .anyMatch(e -> LocalAgentState.ANSWER.equals(e.kind())), 90);
                edt(() -> {
                    if (((AppConfig) field(frame, "config")).assistantActionsRest || agent().personServers() != 0)
                        throw new AssertionError("The capture must leave external access off and personal servers out");
                });
                shot((AssistantPanel) field(frame, "assistantPanel"), "start-with-claude-reply.png");
                System.out.println("Full-mode bridge admitted; live answer completed; external access off; personal servers 0.");
            } else {
                System.out.println("Captured welcome and consent with DEMO checks; no Claude session or message started.");
            }
            exit = 0;
        } catch (Throwable failure) {
            failure.printStackTrace();
        } finally {
            edt(() -> { for (Window window : Window.getWindows()) window.dispose(); });
            System.exit(exit);
        }
    }
}
