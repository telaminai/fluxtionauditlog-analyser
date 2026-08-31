package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.ArrayList;
import java.util.List;

/**
 * A stand-in for {@code MainFrame} / {@code ProjectSession}: it performs effects against an in-memory
 * world and reports what happened.
 *
 * <p><b>It records what it was ASKED and what it DID separately</b>, because that is the distinction
 * the replay tests exist to check. A test asserting only on {@link #performed} would prove the
 * processor emitted a request; asserting on {@link #logClosed} proves the close happened. The whole of
 * M44 §0 is the gap between those two lists.
 *
 * <p>It contains no policy, deliberately. Every {@code perform} branch does what it is told, including
 * when what it is told looks wrong — an adapter that second-guessed a decision would hide exactly the
 * defects these tests are looking for.
 */
final class FakeSessionAdapter implements SessionDriver.Adapter {

    /** Profiles that exist, by path. Anything else fails to load. */
    private final List<String> loadable = new ArrayList<>();

    final List<SessionEffects> performed = new ArrayList<>();

    boolean logClosed;
    boolean graphClosed;
    boolean settingsRestored;
    String appliedProfile;
    String lastStatus;
    String lastWarning;

    /** Set to have the very next load throw rather than return a failure result. */
    boolean loadThrows;

    FakeSessionAdapter withProfile(String path) {
        loadable.add(path);
        return this;
    }

    @Override
    public SessionEvents.Result perform(SessionEffects effect) throws Exception {
        performed.add(effect);
        return switch (effect) {
            case SessionEffects.LoadProfileEffect e -> {
                if (loadThrows) {
                    loadThrows = false;
                    throw new java.io.IOException("disk went away");
                }
                boolean ok = loadable.contains(e.profilePath());
                yield new SessionEvents.ProfileLoaded(e.opId(), e.profilePath(), ok,
                        ok ? nameOf(e.profilePath()) : null, 0,
                        ok ? null : "no such profile: " + e.profilePath());
            }
            case SessionEffects.CreateProfileEffect e -> {
                loadable.add(e.profilePath());
                yield new SessionEvents.ProfileLoaded(e.opId(), e.profilePath(), true,
                        nameOf(e.profilePath()), 0, null);
            }
            case SessionEffects.ApplyProfileEffect e -> {
                appliedProfile = e.profilePath();
                yield new SessionEvents.ProfileApplied(e.opId(), e.profilePath(), e.name());
            }
            case SessionEffects.RestoreSettingsEffect e -> {
                settingsRestored = true;
                appliedProfile = null;
                yield new SessionEvents.SettingsRestored(e.opId());
            }
            case SessionEffects.CloseLogEffect e -> {
                logClosed = true;
                yield new SessionEvents.LogClosed(e.opId());
            }
            case SessionEffects.CloseGraphEffect e -> {
                graphClosed = true;
                yield new SessionEvents.GraphClosed(e.opId());
            }
            case SessionEffects.ShowStatusEffect e -> {
                lastStatus = e.text();
                yield new SessionEvents.StatusShown(e.opId(), "showStatus");
            }
            case SessionEffects.ShowWarningEffect e -> {
                lastWarning = e.text();
                yield new SessionEvents.StatusShown(e.opId(), "showWarning");
            }
        };
    }

    /** How many effects of this type were performed — the count, not merely "at least one". */
    long countOf(Class<? extends SessionEffects> type) {
        return performed.stream().filter(type::isInstance).count();
    }

    void forget() {
        performed.clear();
    }

    private static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);
        return file.endsWith(".properties") ? file.substring(0, file.length() - 11) : file;
    }
}
