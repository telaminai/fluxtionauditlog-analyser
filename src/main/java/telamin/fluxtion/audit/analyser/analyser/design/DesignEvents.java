package telamin.fluxtion.audit.analyser.analyser.design;

/** Completed adapter facts; no filesystem or Swing operations run inside the session graph. */
public final class DesignEvents {
    private DesignEvents() { }
    public record ReadRequested(String kind) { }
    public record ReadCompleted(String file, DesignDocument document, String error, boolean sessionOpen, long generation) {
        @Override public String toString() { return "DesignRead{generation=" + generation + ", revision=" + (document == null ? "unavailable" : document.revision()) + ", opening=" + sessionOpen + "}"; }
    }
    public record ResultReadCompleted(ProducerResult result, String error, long generation) {
        @Override public String toString() { return "ProducerResultRead{generation=" + generation + ", stage=" + (result == null ? "unavailable" : result.stage()) + "}"; }
    }
    public record Cleared(String reason) { }
}
