package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.List;

/**
 * A named topology focus (M27.3): a saved filter context — the node ids it admits, and the rationale
 * that explains why the view exists (shown in the picker, the AV.2 provenance rule applied to views).
 * Project-tier, persisted with the saved graphs (same M15 GRAPHS category, so PROJECT_SCOPED stays at
 * five categories — folded deliberately, not drifted).
 */
public record FocusSpec(String name, String rationale, List<String> nodeIds) {

    public FocusSpec {
        name = name == null ? "" : name;
        rationale = rationale == null ? "" : rationale;
        nodeIds = List.copyOf(nodeIds == null ? List.of() : nodeIds);
    }
}
