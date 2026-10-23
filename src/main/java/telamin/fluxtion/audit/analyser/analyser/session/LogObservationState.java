package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M79-3 (spec-first-minute §8.1, rule 9): the open log's metadata observation, as {@code logObservation} last received
 * it — published in the {@link SessionSnapshot}, so {@code context}, the Project panel and the evidence chip read ONE
 * value instead of each stating the file afresh.
 *
 * @param generation     the log generation the observation is about; a surface ignores one about another generation
 * @param state          {@code unchanged-metadata}, {@code changed-on-disk} or {@code unknown}
 * @param basis          the analyser's words for what was compared
 * @param freshnessRef   the frame's reference to the full observation (its members carry paths, so no fact does)
 * @param readReported   whether the log's reader reports a read identity at all
 * @param readIdentified whether every byte read was identified by its SHA-256
 */
public record LogObservationState(long generation, String state, String basis, long freshnessRef,
                                  boolean readReported, boolean readIdentified) {

    /** Nothing observed: no log, or none yet for the log in force. */
    public static final LogObservationState NONE = new LogObservationState(-1, null, null, 0, false, false);

    /** Whether this observation is about {@code logGeneration}. */
    public boolean about(long logGeneration) {
        return state != null && generation == logGeneration;
    }
}
