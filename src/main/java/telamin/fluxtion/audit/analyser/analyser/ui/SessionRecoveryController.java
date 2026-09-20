package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.node.SessionRecovery;
import telamin.fluxtion.audit.analyser.analyser.session.resume.*;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** File-I/O adapter for the session graph. Serial I/O keeps outgoing saves ahead of subsequent offers. */
final class SessionRecoveryController {
    record Capture(Path profile, List<SessionResumeStore.Input> inputs, Map<String,Object> view) {
        Capture { inputs = List.copyOf(inputs); view = Collections.unmodifiableMap(new LinkedHashMap<>(view)); }
    }
    interface Host {
        SessionDriver driver();
        Capture capture();
        void render();
        void apply(long generation, SessionRecovery.Plan plan, Consumer<ResumeEvents.Outcome> completion);
        void failed(String message);
    }
    private final SessionResumeStore files;
    private final Host host;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "analyser-session-recovery"); t.setDaemon(true); return t;
    });
    private boolean closing;
    SessionRecoveryController(SessionResumeStore files, Host host) { this.files = files; this.host = host; }

    void capture() {
        Capture capture = host.capture();
        if (capture.inputs().isEmpty()) return; // an unopened offer is not overwritten by an empty landing
        io.execute(() -> save(capture));
    }
    private void save(Capture c) {
        try { files.save(files.capture(SessionResumeStore.key(c.profile()), c.inputs(), c.view())); }
        catch (Exception e) { later(() -> host.failed("Could not save session recovery: " + e.getMessage())); }
    }
    void activate(Path profile, String activationError) {
        host.driver().submit(new ResumeEvents.Activated(profile == null ? null : profile.toString()));
        long generation = state().generation();
        host.render();
        io.execute(() -> {
            String key = null, error = activationError;
            SessionResumeStore.Snapshot snapshot = null;
            if (error == null) {
                try { key = SessionResumeStore.key(profile); snapshot = files.load(key).orElse(null); }
                catch (Exception e) { error = "Session recovery unavailable: " + e.getMessage(); }
            }
            var fact = new ResumeEvents.OfferLoaded(generation,key,snapshot,error);
            later(() -> { if (!closing) { host.driver().submit(fact); host.render(); } });
        });
    }
    void dismiss(long generation) {
        host.driver().submit(new ResumeEvents.Requested(generation, false)); host.render();
    }
    void restore(long generation) {
        if (closing) return;
        boolean offered = Boolean.TRUE.equals(state().echo().get("available"));
        host.driver().submit(new ResumeEvents.Requested(generation, true));
        host.render();
        if (!offered || state().generation() != generation || !state().verifying()) return;
        var snapshot = state().candidate();
        io.execute(() -> {
            ResumeEvents.Checked result;
            try { result = new ResumeEvents.Checked(generation,files.check(snapshot),null); }
            catch (Exception e) { result = new ResumeEvents.Checked(generation,List.of(),"Recovery check failed: " + e.getMessage()); }
            final var fact = result;
            later(() -> {
                if (closing) return;
                host.driver().submit(fact); host.render();
                var plan = state().plan();
                if (state().generation() != generation || plan == null || plan.available().isEmpty()) return;
                host.apply(generation, plan, outcome -> later(() -> {
                    if (!closing) { host.driver().submit(new ResumeEvents.Finished(generation,outcome)); host.render(); }
                }));
            });
        });
    }
    /** Finish all queued outgoing writes before application shutdown stops its workers. */
    void closeThen(Runnable finish) {
        if (closing) return;
        closing = true;
        try { capture(); }
        catch (RuntimeException e) { host.failed("Session capture failed: " + e.getMessage()); }
        finally {
            io.execute(() -> later(finish));
            io.shutdown();
        }
    }
    private SessionRecovery state() { return host.driver().processor().sessionRecovery; }
    private static void later(Runnable r) { SwingUtilities.invokeLater(r); }
}
