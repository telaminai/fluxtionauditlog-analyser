package telamin.fluxtion.audit.analyser.analyser.design;

/** Completed adapter facts; no filesystem or Swing operations run inside the session graph. */
public final class DesignEvents {
    private DesignEvents() { }
    public record ReadRequested(String kind) { }
    /** A background Follow observation; presentation is requested only after the session accepts this read. */
    public record FollowReadCompleted(ReadCompleted read) { }
    public record ReadCompleted(String file, DesignDocument document, String error, boolean sessionOpen, long generation,
                                BuilderDocument builder) {
        public ReadCompleted(String file, DesignDocument document, String error, boolean sessionOpen, long generation) {
            this(file, document, error, sessionOpen, generation, null);
        }
        @Override public String toString() { return "DesignRead{generation=" + generation + ", revision=" + (document == null ? "unavailable" : document.revision()) + ", opening=" + sessionOpen + "}"; }
    }
    public record ResultReadCompleted(ProducerResult result, String error, long generation) {
        @Override public String toString() { return "ProducerResultRead{generation=" + generation + ", stage=" + (result == null ? "unavailable" : result.stage()) + "}"; }
    }
    public record Cleared(String reason) { }
    public record DiscoveryRequested() { }
    public record DiscoveryCompleted(DesignDiscovery discovery, long generation) {
        @Override public String toString() { return "DesignDiscoveryCompleted{generation=" + generation
                + ", kind=" + discovery.kind() + ", files=" + discovery.files().size() + "}"; }
    }
}
