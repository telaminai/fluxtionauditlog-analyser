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
 * <p>Witness: making {@code copyPrompt} append the manifest whenever REST is on — the shipped behaviour — turns
 * {@link #noTokenUnlessTheBoxIsTicked()} and {@link #thePartsListMatchesTheText()} red (run against this commit).
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

    /**
     * Review of 7f825492: after the cut at 60,000 characters the parts list still named the source that had been cut
     * away, and the cut note sat inside the list. Now a part that starts past the cut is not named, the one the cut
     * goes through is marked "(cut)", and the cut is a sentence of its own.
     */
    @Test
    @DisplayName("SF-1: a context cut at 60,000 characters names only what was copied, and says so separately")
    void aCutContextNamesOnlyWhatWasCopied(@org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        // oversized records: the sample log, many times over, all selected
        String sample = Samples.sample();
        StringBuilder log = new StringBuilder();
        while (log.length() < 150_000) log.append(sample).append(sample.endsWith("\n") ? "" : "\n");
        HeapLogStore store = new HeapLogStore(log.toString());
        List<LogRecord> all = new java.util.ArrayList<>();
        for (int i = 0; i < store.size(); i++) all.add(store.record(i));
        // and a source that would follow them
        Path dir = Files.createDirectories(root.resolve("com/acme/marketmaker/strategy"));
        Files.writeString(dir.resolve("DemoMarketMakerStrategy.java"), "package com.acme.marketmaker.strategy;\n"
                + "public class DemoMarketMakerStrategy { }\n");
        var source = new telamin.fluxtion.audit.analyser.analyser.source.SourceService();
        source.configure(List.of(root.toString()), EP);
        // the same selection small enough to fit names the source — so its absence below is the cut, not the setup
        var whole = PromptBuilder.copyPrompt(all.subList(0, 1), EP, source, null, null, "q", null, null, 20, false);
        assertTrue(whole.parts().stream().anyMatch(p -> p.startsWith("source code from") || p.startsWith("the source roots")),
                whole.parts().toString());
        assertFalse(whole.contextCut());

        var cut = PromptBuilder.copyPrompt(all, EP, source, null, null, "q", null, null, 20, false);
        assertTrue(cut.contextCut());
        assertEquals(List.of("the analyser's standing instructions", "the event processor's class name",
                "the raw text of " + all.size() + " records (cut)", "your question"), cut.parts(),
                "the source started past the cut, so it is not named; the records are marked as cut");
        assertFalse(cut.text().contains("--- source: " + EP), "and indeed the source is not in the text");
        assertFalse(cut.partsSentence().contains("characters"), "the cut is not one of the parts");
        assertTrue(cut.sentence().contains("The record context was cut at 60,000 characters"), cut.sentence());
    }

    @Test
    @DisplayName("SF-1: the frame composes Copy prompt only through copyPrompt and CopyPromptDialog")
    void theFrameOnlyRendersTheComposition() throws Exception {
        String frame = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertFalse(frame.contains("restActionManifest("),
                "MainFrame must not append the REST manifest itself — that was the unconditional token leak");
        assertTrue(frame.contains("PromptBuilder.copyPrompt("), "the dialog renders the pure composition");
        assertTrue(frame.contains("CopyPromptDialog.body("), "through the dialog body CopyPromptDialogTest checks");
    }
}
