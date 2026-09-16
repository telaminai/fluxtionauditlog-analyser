package telamin.fluxtion.audit.analyser.analyser.model;

import java.util.List;

/** Entries and their source positions, produced together by the tokenizer. */
public record NodeLogData(List<NodeLog> nodes, List<KeySpan> keySpans) {
    public NodeLogData {
        nodes = List.copyOf(nodes);
        keySpans = List.copyOf(keySpans);
    }

    /** A complete named key in the record's raw text, with an exclusive end. */
    public record KeySpan(int start, int end, String instanceId, String key) { }
}
