package telamin.fluxtion.audit.analyser.analyser.model;

/** Parse outcome for a record. {@link #PARSE_ERROR} records still retain their raw text. */
public enum EventKind {
    OK,
    PARSE_ERROR
}