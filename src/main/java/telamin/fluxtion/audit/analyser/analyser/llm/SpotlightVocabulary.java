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
     * M80 — the largest line SPAN a {@code line:<from>-<to>} may light at once (owner choice): wide enough for a
     * real excerpt, narrow enough that "light a range" never becomes a back door to "light nearly the whole file".
     */
    public static final int MAX_LINE_SPAN = 200;

    /**
     * WHEN to point — the general guidance, said once and printed at every entrance an assistant arrives
     * through: the in-app assistant's action manifest, the copy-prompt REST manifest, and the MCP bridge's
     * server instructions. Until M64.6 this lived only in the verb's own description and in one skill, so an
     * assistant diagnosing a real log had the verb and no reason to reach for it.
     */
    /** The caption-length line every entrance carries (owner, 2026-10-17). */
    public static final String CAPTION_LENGTH = "Keep a caption under about 80 characters — a callout shows three short "
            + "lines until the person points at it — and put the detail in your answer.";

    public static final String GUIDANCE =
            "POINT BEFORE YOU EXPLAIN. When your answer is about something the person can see in the analyser — "
                    + "the node that never logged, the record where a value crossed, a note on a chart — light it "
                    + "with `spotlight` first, then say your sentence. One thing: {target, caption}. A relation "
                    + "between things: {targets: [{target, caption}, …]} — up to " + MAX_LIT + " at once, "
                    + "numbered on screen, so use the numbers in your sentence (\"1 feeds 2; 2 never logged\"); "
                    + "a call REPLACES what is lit unless it says {add: true}, so a step-by-step walk sends one "
                    + "call with all its targets, or adds. "
                    + "Light AFTER filter / goto / graph / topology: a verb that changes the view puts a spotlight "
                    + "out, as does any click. A callout is YOUR words and shows WHERE to look; it is not evidence "
                    + "and is never saved — a finding worth keeping is a flag, a chart note or a report. Point when "
                    + "the person would otherwise have to hunt for it, not for every sentence. "
                    // owner, 2026-10-17: a callout shows three lines of about 40 characters until it is pointed at
                    + CAPTION_LENGTH;

    public static final String TEXT =
            "tab:<summary|source|graph|topology|reports|assistant> · source:design · source:design:bean:<id>|source:design:node:<id> · source:design:line:<n|from-to> · source:java:<fqn> · source:java:<fqn>:line:<n|from-to> · records · records:row:<recordIndex> · "
                    + "detail · detail:node:<instanceId> · topology · topology:node:<instanceId> · topology:verdict · "
                    + "graph[:<name>] · graph[:<name>]:note:<n> · graph[:<name>]:series:<label> · project · "
                    + "project:<log|graph|processors|roots> · toolbar:<open|flag|explain|follow> · menu:<Menu> · "
                    + "menu:<Menu>:<item> · status";
}
