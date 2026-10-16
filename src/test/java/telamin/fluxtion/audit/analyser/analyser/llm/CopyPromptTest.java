package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SF-1 — Copy prompt put the local REST token on the clipboard whenever REST was on, and its dialog said only that
 * nothing was sent. Now the composition is one pure function: the token (and the action manifest, which is useless
 * without it) is copied only when the person ticks the box, and the dialog's parts list is the function's own record
 * of what it wrote.
 *
 * <p>Witness: {@link #theShippedCompositionCopiedTheToken()} — the composition as it shipped (manifest appended
 * whenever a REST URL existed) carries the token, so the assertions below discriminate; and making
 * {@code copyPrompt} append the manifest unconditionally turns {@link #noTokenUnlessTheBoxIsTicked()} red.
 */
class CopyPromptTest {

    private static final String URL = "http://127.0.0.1:4711";
    private static final String TOKEN = "tok-DEMO-0123456789";
    private static final String EP = "com.acme.marketmaker.strategy.DemoMarketMakerStrategy";

    private static List<LogRecord> twoRecords() {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        return List.of(store.record(0), store.record(1));
    }

    @Test
    @DisplayName("SF-1: with REST on and a selection, no token and no manifest unless the box is ticked")
    void noTokenUnlessTheBoxIsTicked() {
        var copied = PromptBuilder.copyPrompt(twoRecords(), EP, null, null, "DEMO means the demo venue",
                "why is the quote stale?", URL, TOKEN, 20, false);
        assertFalse(copied.text().contains(TOKEN), "the token is not copied by default");
        assertFalse(copied.text().contains("X-Analyser-Token"), "nor the header it would be sent in");
        assertFalse(copied.text().contains(URL), "nor the manifest, which is useless without the token");
        assertTrue(copied.tokenOffered(), "REST is on, so the dialog offers the box");
        assertFalse(copied.carriesToken());
        assertEquals(List.of("the analyser's standing instructions", "the glossary", "the event processor's class name",
                "the raw text of 2 records", "your question"), copied.parts());

        var ticked = PromptBuilder.copyPrompt(twoRecords(), EP, null, null, "DEMO means the demo venue",
                "why is the quote stale?", URL, TOKEN, 20, true);
        assertTrue(ticked.text().contains("X-Analyser-Token: " + TOKEN), "ticked: the manifest and its token");
        assertTrue(ticked.carriesToken());
        assertEquals(PromptBuilder.TOKEN_PART, ticked.parts().get(ticked.parts().size() - 1),
                "and the parts list says so");
        assertTrue(ticked.text().startsWith(copied.text()), "the box adds the manifest and changes nothing else");
    }

    @Test
    @DisplayName("SF-1: the parts list names exactly what was copied")
    void thePartsListMatchesTheText() throws Exception {
        var copied = PromptBuilder.copyPrompt(twoRecords(), EP, null, null, "DEMO glossary", "q", URL, TOKEN, 20, false);
        assertTrue(copied.text().contains("<<<GLOSSARY"), "the glossary part is in the text");
        assertTrue(copied.text().contains("EventProcessor: " + EP));
        for (LogRecord r : twoRecords()) assertTrue(copied.text().contains(r.rawText()), "each record's raw text");
        assertEquals("the analyser's standing instructions, the glossary, the event processor's class name, the raw "
                + "text of 2 records and your question", copied.partsSentence());

        // no glossary, no processor: neither is claimed
        var bare = PromptBuilder.copyPrompt(twoRecords().subList(0, 1), null, null, null, null, "  ", null, null, 20, true);
        assertEquals(List.of("the analyser's standing instructions", "the raw text of 1 record",
                "the default question (\"Explain this record.\")"), bare.parts());
        assertFalse(bare.tokenOffered(), "REST off: no box, and ticking it cannot add a token that does not exist");
        assertFalse(bare.carriesToken());
        assertTrue(bare.text().endsWith(PromptBuilder.DEFAULT_QUESTION));

        // a local file adds its framing and the selection's byte offsets
        Path log = Files.createTempFile("sf1", ".yml");
        try {
            var file = new LogFileInfo(log.toString(), log.toString(), 10, 2, null, null);
            var framed = PromptBuilder.copyPrompt(twoRecords(), null, null, file, null, "q", null, null, 20, false);
            assertTrue(framed.parts().contains("the log file's path, size and framing"), framed.parts().toString());
            assertTrue(framed.parts().contains("the byte offsets of the selected records"), framed.parts().toString());
            assertTrue(framed.text().contains("Full audit log: " + log));
        } finally {
            Files.deleteIfExists(log);
        }

        // nothing selected: only the instructions and the question
        var none = PromptBuilder.copyPrompt(List.of(), EP, null, null, "DEMO", "q", URL, TOKEN, 20, false);
        assertEquals(List.of("the analyser's standing instructions", "your question"), none.parts());
    }

    @Test
    @DisplayName("SF-1 witness: the shipped composition — manifest appended whenever REST was on — copied the token")
    void theShippedCompositionCopiedTheToken() {
        String context = PromptBuilder.recordContext(twoRecords(), EP, null, null, "DEMO");
        String shipped = PromptBuilder.fullPrompt(context, "q") + "\n\n"
                + PromptBuilder.restActionManifest(URL, TOKEN, 20);
        assertTrue(shipped.contains(TOKEN), "the check above can tell the difference");
    }

    @Test
    @DisplayName("SF-1: the frame composes Copy prompt only through copyPrompt, and offers the token behind the box")
    void theFrameOnlyRendersTheComposition() throws Exception {
        String frame = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertFalse(frame.contains("restActionManifest("),
                "MainFrame must not append the REST manifest itself — that was the unconditional token leak");
        assertTrue(frame.contains("PromptBuilder.copyPrompt("), "the dialog renders the pure composition");
        assertTrue(frame.contains("new JCheckBox(COPY_PROMPT_TOKEN_BOX, false)"), "the box starts unticked");
    }
}
