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

    /** How many spotlights may be lit together. Bounded, because six callouts is already a crowded window. */
    public static final int MAX_LIT = 6;

    /** One caption is one short line: the sentence belongs in the tutor's chat, where it is clearly theirs. */
    public static final int MAX_CAPTION = 160;

    /**
     * WHEN to point — the general guidance, said once and printed at every entrance an assistant arrives
     * through: the in-app assistant's action manifest, the copy-prompt REST manifest, and the MCP bridge's
     * server instructions. Until M64.6 this lived only in the verb's own description and in one skill, so an
     * assistant diagnosing a real log had the verb and no reason to reach for it.
     */
    public static final String GUIDANCE =
            "POINT BEFORE YOU EXPLAIN. When your answer is about something the person can see in the analyser — "
                    + "the node that never logged, the record where a value crossed, a note on a chart — light it "
                    + "with `spotlight` first, then say your sentence. One thing: {target, caption}. A relation "
                    + "between things: {targets: [{target, caption}, …]} — up to " + MAX_LIT + " at once, "
                    + "numbered on screen, so use the numbers in your sentence (\"1 feeds 2; 2 never logged\"). "
                    + "Light AFTER filter / goto / graph / topology: a verb that changes the view puts a spotlight "
                    + "out, as does any click. A callout is YOUR words and shows WHERE to look; it is not evidence "
                    + "and is never saved — a finding worth keeping is a flag, a chart note or a report. Point when "
                    + "the person would otherwise have to hunt for it, not for every sentence.";

    public static final String TEXT =
            "tab:<summary|source|graph|topology|reports|assistant> · records · records:row:<recordIndex> · "
                    + "detail · detail:node:<instanceId> · topology · topology:node:<instanceId> · coverage · "
                    + "graph · graph:note:<n> · graph:series:<label> · project · "
                    + "project:<log|graph|processors|roots> · toolbar:<open|flag|explain|follow> · status";
}
