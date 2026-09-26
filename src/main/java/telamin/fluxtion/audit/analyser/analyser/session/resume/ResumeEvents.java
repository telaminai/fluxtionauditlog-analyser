package telamin.fluxtion.audit.analyser.analyser.session.resume;

import java.util.List;

/** Filesystem facts enter the graph only after the adapter has finished reading them. */
public final class ResumeEvents {
    private ResumeEvents() { }
    public record Activated(String profile) { }
    /** {@code profileIdentity}: the active profile's file identity, or null when no project is active. */
    public record OfferLoaded(long generation, String key, SessionResumeStore.Snapshot snapshot, String error,
                              String profileIdentity) {
        public OfferLoaded(long generation, String key, SessionResumeStore.Snapshot snapshot, String error) {
            this(generation, key, snapshot, error, null);
        }
    }
    public record Requested(long generation, boolean accept) { }
    public record Checked(long generation, List<SessionResumeStore.Check> checks, String error, long operationId) {
        public Checked(long generation, List<SessionResumeStore.Check> checks, String error) {
            this(generation, checks, error, Long.MIN_VALUE);
        }
    }
    public record Outcome(String message, boolean superseded) {
        public static Outcome done(String message) { return new Outcome(message, false); }
        public static Outcome superseded(String message) { return new Outcome(message, true); }
    }
    public record Finished(long generation, Outcome outcome) {
        public Finished(long generation, String message) { this(generation, Outcome.done(message)); }
    }
}
