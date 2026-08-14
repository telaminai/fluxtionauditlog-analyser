package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    @Test
    void systemPromptLoadsBundledResource() {
        String sys = PromptBuilder.systemPrompt();
        assertTrue(sys.contains("Fluxtion"), "bundled system prompt loaded");
        assertTrue(sys.contains("nodeLogs"));
    }

    @Test
    void recordContextIncludesEventProcessorAndRawRecord() {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        LogRecord r0 = store.record(0);
        String ctx = PromptBuilder.recordContext(List.of(r0),
                "com.acme.marketmaker.strategy.DemoMarketMakerStrategy", null);
        assertTrue(ctx.contains("EventProcessor: com.acme.marketmaker.strategy.DemoMarketMakerStrategy"));
        assertTrue(ctx.contains("StartComplete"), "raw record text is embedded");
        assertTrue(ctx.contains("positionNode"), "nodeLogs are embedded");
    }

    @Test
    void includesNodeTypesAndEventProcessorSourceWhenResolvable(@TempDir Path root) throws IOException {
        // an EventProcessor whose field 'positionNode' has a known type (as in the sample's nodeLogs)
        String fqn = "com.acme.marketmaker.strategy.DemoMarketMakerStrategy";
        Path dir = root.resolve("com/acme/marketmaker/strategy");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("DemoMarketMakerStrategy.java"),
                "package com.acme.marketmaker.strategy;\n"
                        + "import com.acme.node.PositionNode;\n"
                        + "public class DemoMarketMakerStrategy {\n"
                        + "  private final PositionNode positionNode = new PositionNode();\n}\n");
        SourceService svc = new SourceService();
        svc.configure(List.of(root.toString()), fqn);

        LogRecord r0 = new HeapLogStore(Samples.sample()).record(0);   // StartComplete → nodeLog positionNode
        String ctx = PromptBuilder.recordContext(List.of(r0), fqn, svc);

        assertTrue(ctx.contains("Node types"), "node-type mapping section present");
        assertTrue(ctx.contains("positionNode -> com.acme.node.PositionNode"), "instanceId resolved to its field type");
        assertTrue(ctx.contains("--- source: " + fqn), "the EventProcessor source is included");
        assertTrue(ctx.contains("Source roots"), "source roots listed for agentic exploration");
    }

    @Test
    void seedsFileAccessWithPathShapeFramingAndByteAnchors() {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        LogRecord r0 = store.record(0);
        LogRecord r1 = store.record(1);
        LogFileInfo file = new LogFileInfo("/logs/demo.yml", "/logs/demo.yml", 2_147_483_648L,
                store.size(), store.minLogTime(), store.maxLogTime());

        String ctx = PromptBuilder.recordContext(List.of(r0, r1), "EP", null, file);

        assertTrue(ctx.contains("Full audit log: /logs/demo.yml"), "log path seeded");
        assertTrue(ctx.contains("2.0 GB"), "human-readable size");
        assertTrue(ctx.contains(store.size() + " records") || ctx.contains(String.format("%,d", store.size()) + " records"),
                "record count seeded");
        assertTrue(ctx.contains("separated by lines of exactly `---`"), "framing described");
        assertTrue(ctx.contains("read or grep this file"), "invites bidirectional file access");
        assertTrue(ctx.contains("Selected record anchors"), "per-record anchors present");
        assertTrue(ctx.contains("byte " + String.format("%,d", r1.fileOffset())), "second record's byte offset seeded");
    }

    @Test
    void showsRemoteOriginForS3ButPointsAtTheLocalTempFile() {
        HeapLogStore store = new HeapLogStore(Samples.sample());
        LogFileInfo file = new LogFileInfo("s3://bucket/demo.yml", "/tmp/demo123.yml", 1024L,
                store.size(), store.minLogTime(), store.maxLogTime());
        String ctx = PromptBuilder.recordContext(List.of(store.record(0)), null, null, file);
        assertTrue(ctx.contains("Full audit log: /tmp/demo123.yml"), "grep target is the local temp file");
        assertTrue(ctx.contains("Opened from: s3://bucket/demo.yml"), "records the S3 origin");
    }

    @Test
    void noFileBlockWhenInfoAbsent() {
        String ctx = PromptBuilder.recordContext(List.of(new HeapLogStore(Samples.sample()).record(0)), "EP", null);
        assertFalse(ctx.contains("Full audit log:"), "no file-access block without file info (graceful degradation)");
    }

    @Test
    void fullPromptCombinesSystemContextAndQuestion() {
        String prompt = PromptBuilder.fullPrompt("CTX-BODY", "why is hedgeQuantity NaN?");
        assertTrue(prompt.contains("===== CONTEXT =====\nCTX-BODY"));
        assertTrue(prompt.contains("===== QUESTION =====\nwhy is hedgeQuantity NaN?"));
        assertTrue(prompt.contains("Fluxtion"), "system prompt prepended");
    }
}
