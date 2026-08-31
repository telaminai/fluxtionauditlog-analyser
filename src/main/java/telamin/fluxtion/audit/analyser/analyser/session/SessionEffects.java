package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * What the processor <b>asks be done</b>. Never what was done — that is a result in
 * {@link SessionEvents}.
 *
 * <p>The processor emits these into {@code EffectQueue}; the driver drains them <em>after</em>
 * {@code onEvent} returns and performs them outside Fluxtion dispatch. <b>If an adapter ever decides
 * whether to perform one, the decision has leaked back out</b> (M44 §7).
 *
 * <p>Every effect is answered — by its typed success result or by
 * {@link SessionEvents.EffectFailed}. An effect with no answer is a hole in the audit record, so
 * {@code SessionDriver} treats one as a programming error rather than letting it pass.
 */
public sealed interface SessionEffects {

    /** The operation this effect belongs to; the answering result must carry the same id. */
    long opId();

    /** Read the profile file at this path and report {@link SessionEvents.ProfileLoaded}. */
    record LoadProfileEffect(long opId, String profilePath) implements SessionEffects {
    }

    /**
     * Start a new, empty project at this path and make it the loaded one.
     *
     * <p>Distinct from {@link LoadProfileEffect} rather than a flag on it, because it is a different
     * act with a different failure: loading a profile that is not there is an error, and creating one
     * where a project already exists would be destruction. An adapter told to "load, and create if
     * missing" would have to decide which — and adapters do not decide.
     */
    record CreateProfileEffect(long opId, String profilePath) implements SessionEffects {
    }

    /** Put the loaded profile's settings genuinely in force, then report. */
    record ApplyProfileEffect(long opId, String profilePath, String name) implements SessionEffects {
    }

    /** Revert to the user's own pre-project settings, then report. */
    record RestoreSettingsEffect(long opId) implements SessionEffects {
    }

    /** Close the open log because this transition is a session boundary. */
    record CloseLogEffect(long opId) implements SessionEffects {
    }

    /** Close the open topology graph, for the same reason. */
    record CloseGraphEffect(long opId) implements SessionEffects {
    }

    /** Say something in the status line. Infallible by construction, but still answered. */
    record ShowStatusEffect(long opId, String text) implements SessionEffects {
    }

    /** Warn — the louder surface, for a transition that did not do what was asked. */
    record ShowWarningEffect(long opId, String text) implements SessionEffects {
    }
}
