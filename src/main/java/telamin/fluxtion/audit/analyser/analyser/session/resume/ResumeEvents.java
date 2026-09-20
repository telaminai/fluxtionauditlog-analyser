package telamin.fluxtion.audit.analyser.analyser.session.resume;

import java.util.List;

/** Filesystem facts enter the graph only after the adapter has finished reading them. */
public final class ResumeEvents {
    private ResumeEvents() { }
    public record Activated(String profile) { }
    public record OfferLoaded(long generation, String key, SessionResumeStore.Snapshot snapshot, String error) { }
    public record Requested(boolean accept) { public Requested() { this(true); } }
    public record Checked(long generation, List<SessionResumeStore.Check> checks, String error) { }
    public record Finished(long generation, String outcome) { }
}
