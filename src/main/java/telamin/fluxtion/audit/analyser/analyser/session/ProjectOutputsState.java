package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/** M81 D: immutable node-owned offers; a surface renders, never discovers or opens from an observation. */
public record ProjectOutputsState(long watch, long projectGeneration, boolean watching, List<Offer> offers) {
    public static final ProjectOutputsState NONE = new ProjectOutputsState(0, 0, false, List.of());
    public ProjectOutputsState { offers = List.copyOf(offers); }
    public enum Kind { GRAPHML, LOG }
    /** Metadata identity, not a claim that a file is whole or a lock against a subsequent rewrite. */
    public record Revision(long size, long modifiedNanos, String fileKeyDigest) { }
    /** Canonical path held by reference; only its digest and redacted display name enter the node. */
    public record File(Kind kind, long pathRef, String pathKey, String shown, Revision revision) { }
    public record Offer(long id, File file, File graph) {
        public String text() {
            return file.kind() == Kind.GRAPHML ? "The graph was regenerated — open it?"
                    : graph == null ? "An audit log changed — open it?"
                    : "A new audit log was written — open it with the graph?";
        }
    }
    public List<String> pendingKinds() {
        return offers.stream().map(o -> o.file().kind().name().toLowerCase(java.util.Locale.ROOT)).distinct().toList();
    }
}
