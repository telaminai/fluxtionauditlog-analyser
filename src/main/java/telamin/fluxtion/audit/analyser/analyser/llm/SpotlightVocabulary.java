package telamin.fluxtion.audit.analyser.analyser.llm;

/**
 * M64 — the spotlight's target vocabulary as the verb schema prints it.
 *
 * <p>The vocabulary is OWNED by {@code ui.SpotlightTarget}; this is its text form, held here because
 * {@link VerbSchemas} lives in a package the UI depends on and not the other way round.
 * {@code SpotlightTargetTest} asserts the two are identical, so the words an agent is given can never be a
 * different vocabulary from the one the parser accepts.
 */
public final class SpotlightVocabulary {

    private SpotlightVocabulary() {
    }

    public static final String TEXT =
            "tab:<summary|source|graph|topology|reports|assistant> · records · records:row:<recordIndex> · "
                    + "detail · detail:node:<instanceId> · topology · topology:node:<instanceId> · coverage · "
                    + "graph · graph:note:<n> · graph:series:<label> · project · "
                    + "project:<log|graph|processors|roots> · toolbar:<open|flag|explain|follow> · status";
}
