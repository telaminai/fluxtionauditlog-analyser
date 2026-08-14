package telamin.fluxtion.audit.analyser.analyser.parse;

/**
 * A raw, unparsed record slice: its character offset + length within the source and the exact text
 * (header comment + {@code eventLogRecord} block), verbatim.
 */
public record RawRecord(long offset, int length, String text) {
}
