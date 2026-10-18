package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import javax.swing.SwingUtilities;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * M77-4 (spec-ai-setup-wizard.md §5.1, T20): the Set up AI documentation captures — the start page's first-run block and
 * the wizard's screens (Screen 1 with its <i>Where data goes</i> picture, Claude inside with both checks passed, Your own
 * AI client with the picture beside its access switch) — in the REAL frame, on a FRESH isolated home with no log and no
 * {@code assistant.rest}, so the first run is a real first run.
 *
 * <p>Run by {@code capture-docs.py --setup-ai} from a jar copied under the isolated capture root (review R1-27: the
 * registration command shows the launcher's path, so it must be a neutral one), with a stub {@code claude} that answers
 * {@code --version} and {@code auth status} and runs nothing else. Every image is taken by the harness, by window id
 * ({@link DocShot}); every shot refuses a symlinked {@code user.home} first.
 */
public final class AiSetupDocCapture {

    private AiSetupDocCapture() {
    }

    public static void main(String[] args) throws Exception {
        DocShot.requireRealHome();
        ThemeManager.apply(new telamin.fluxtion.audit.analyser.analyser.config.ConfigStore().load().theme);
        MainFrame[] made = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame f = new MainFrame();
            f.setBounds(60, 28, 1200, 800);
            f.setVisible(true);
            made[0] = f;
        });
        MainFrame f = made[0];
        try {
            // 1. the first run: the start page's AI block, inline — a state of the page, not a window (OD-77.1)
            await(f, AiSetupState::firstRun, "the first-run block");
            Thread.sleep(1200);
            DocShot.window(f, "setup-ai-first-run.png");

            // 2. Screen 1, from the AI line's door (as AI ▸ Set up AI… opens it on a fresh machine)
            post(f, new SessionEvents.AiSetupRequested(AiSetupState.Origin.MENU));
            await(f, s -> s.open() && s.step() == AiSetupState.Step.CHOICE, "Screen 1");
            post(f, new SessionEvents.AiSetupChoiceMade(AiSetupState.Choice.INSIDE));
            Thread.sleep(600);
            DocShot.window(window(f), "setup-ai-choice.png");

            // 3. Claude inside: both read-only checks, answered by the stub
            post(f, new SessionEvents.AiSetupContinue(AiSetupState.Step.CHOICE));
            await(f, s -> s.step() == AiSetupState.Step.INSIDE
                    && s.installed().verdict() == AiSetupState.Installed.FOUND
                    && s.signedIn().verdict() != AiSetupState.SignedIn.RUNNING
                    && s.signedIn().verdict() != AiSetupState.SignedIn.UNCHECKED, "the checks");
            Thread.sleep(600);
            DocShot.window(window(f), "setup-ai-inside.png");

            // 4. Your own AI client: the four parts, the picture beside the access switch
            post(f, new SessionEvents.AiSetupBack(AiSetupState.Step.INSIDE));
            await(f, s -> s.step() == AiSetupState.Step.CHOICE, "back to Screen 1");
            post(f, new SessionEvents.AiSetupChoiceMade(AiSetupState.Choice.CLIENT));
            post(f, new SessionEvents.AiSetupContinue(AiSetupState.Step.CHOICE));
            await(f, s -> s.step() == AiSetupState.Step.CLIENT, "Screen 2b");
            Thread.sleep(600);
            DocShot.window(window(f), "setup-ai-client.png");

            post(f, new SessionEvents.AiSetupCancelled("doc capture"));
        } finally {
            SwingUtilities.invokeAndWait(f::dispose);
        }
        System.exit(0);
    }

    private static SessionDriver session(MainFrame f) {
        try {
            var field = MainFrame.class.getDeclaredField("session");
            field.setAccessible(true);
            return (SessionDriver) field.get(f);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void post(MainFrame f, Object fact) throws Exception {
        SwingUtilities.invokeAndWait(() -> session(f).post(fact));
    }

    private static AiSetupWindow window(MainFrame f) {
        if (f.aiSetupWindow == null) throw new IllegalStateException("no Set up AI window");
        return f.aiSetupWindow;
    }

    private static void await(MainFrame f, Predicate<AiSetupState> done, String what) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        AtomicReference<AiSetupState> s = new AtomicReference<>();
        do {
            SwingUtilities.invokeAndWait(() -> s.set(session(f) == null ? null : session(f).snapshot().aiSetup()));
            if (s.get() != null && done.test(s.get())) return;
            Thread.sleep(50);
        } while (System.nanoTime() < until);
        throw new IllegalStateException(what + " never showed: " + s.get());
    }
}
