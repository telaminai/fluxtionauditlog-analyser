package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/** M81 D: immutable node-owned offers; a surface renders, never discovers or opens from an observation. */
public record ProjectOutputsState(long watch, long projectGeneration, boolean watching, List<Offer> offers) {
    public static final ProjectOutputsState NONE = new ProjectOutputsState(0, 0, false, List.of());
    public ProjectOutputsState { offers = List.copyOf(offers); }
    public enum Kind { GRAPHML, LOG }
    /** Metadata identity, not a claim that a file is whole or a lock against a subsequent rewrite. GraphML also carries
     *  a digest of its bytes (§3.5.1) so a build step that copies an unchanged graph is not a regeneration. */
    public record Revision(long size, long modifiedNanos, String fileKeyDigest, String contentDigest) {
        public Revision(long size, long modifiedNanos, String fileKeyDigest) { this(size, modifiedNanos, fileKeyDigest, null); }
    }
    /** Canonical path held by reference; only its digest and redacted display name enter the node. A GraphML copy also
     *  names its logical graph (digest of the path below the output root) and whether it sits in build output (§3.5.1). */
    public record File(Kind kind, long pathRef, String pathKey, String shown, Revision revision, String graphKey, boolean buildCopy) {
        public File(Kind kind, long pathRef, String pathKey, String shown, Revision revision) {
            this(kind, pathRef, pathKey, shown, revision, null, false);
        }
    }
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
