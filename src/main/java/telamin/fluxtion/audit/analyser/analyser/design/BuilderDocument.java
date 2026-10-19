package telamin.fluxtion.audit.analyser.analyser.design;

import java.util.List;

/** Java definition text. M80 PR1 has no index; the bounded anchor scanner arrives in PR2. */
public record BuilderDocument(String file, String text, String revision, List<NodeAnchor> nodes) {
    public record NodeAnchor(String name, int line, boolean anchored, String kind) { }
    public BuilderDocument { nodes = List.copyOf(nodes); }
    public static BuilderDocument parse(String file, String text) {
        return new BuilderDocument(file, text, DesignFiles.sha256(text), List.of());
    }
}
