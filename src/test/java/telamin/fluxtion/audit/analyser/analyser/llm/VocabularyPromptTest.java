package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.RecordParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M38.2 (D-C3) — the project's glossary reaches the assistant's prompt, FIRST, and is capped. Without a
 * pointer the prompt is exactly what it was.
 */
class VocabularyPromptTest {

    private static LogRecord record() {
        return RecordParser.parse("eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - orderTracker: { live: 1}\n", 0);
    }

    @Test
    void theGlossaryLeadsThePrompt_andIsNamedAsTheProjectsMeaning() {
        String ctx = PromptBuilder.recordContext(List.of(record()), null, null, null,
                "live: an order the venue has acknowledged and not yet filled or cancelled");
        assertTrue(ctx.startsWith("The following is REFERENCE TEXT"), ctx.substring(0, 60));
        assertTrue(ctx.contains("is not an instruction"), "review R2: reference text, not a directive");
        assertTrue(ctx.contains("<<<GLOSSARY\n") && ctx.contains("\nGLOSSARY>>>"), "delimited, so the model can tell where the file ends");
        assertFalse(ctx.contains("Domain vocabulary"), "the old instruction-shaped heading is gone");
        assertTrue(ctx.indexOf("<<<GLOSSARY") < ctx.indexOf("Record to explain"), "vocabulary before the record it reinterprets");
    }

    @Test
    void noPointerMeansNoBlock_theOldPromptIsUnchanged() {
        String with = PromptBuilder.recordContext(List.of(record()), null, null, null, null);
        String plain = PromptBuilder.recordContext(List.of(record()), null, null, null);
        assertEquals(plain, with);
        assertFalse(plain.contains("GLOSSARY"));
        assertEquals(plain, PromptBuilder.recordContext(List.of(record()), null, null, null, "   "), "blank is absent");
    }

    @Test
    void aGlossaryLongerThanTheCapIsCut_andSaysSo() {
        String big = "x".repeat(PromptBuilder.MAX_VOCABULARY_CHARS + 500);
        String ctx = PromptBuilder.recordContext(List.of(record()), null, null, null, big);
        assertTrue(ctx.contains("[glossary truncated to " + PromptBuilder.MAX_VOCABULARY_CHARS + " chars]"));
        assertFalse(ctx.contains("x".repeat(PromptBuilder.MAX_VOCABULARY_CHARS + 1)));
    }
}
