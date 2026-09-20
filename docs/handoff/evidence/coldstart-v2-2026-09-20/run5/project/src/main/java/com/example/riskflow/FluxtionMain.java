package com.example.riskflow;

import com.example.riskflow.builder.RiskGraphBuilder;
import com.example.riskflow.node.ExposureCalculator;
import com.example.riskflow.node.PositionBook;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.runtime.EventProcessor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs the risk graph over a CSV feed.
 *
 * <p>The dispatcher is generated and compiled in-process at start-up ({@code Fluxtion.compile}),
 * and the generated source is left in {@code target/generated-sources/fluxtion} to read.
 */
public final class FluxtionMain {

    private static final double SYMBOL_LIMIT = 2_000_000;
    private static final double TOTAL_LIMIT = 3_000_000;

    public static void main(String[] args) throws Exception {
        Path feed = Path.of(args.length > 0 ? args[0] : "data/market-events.csv");
        Path auditFile = Path.of("data/audit.log");
        List<Object> events = EventFileReader.read(feed);

        RiskGraphBuilder graph = new RiskGraphBuilder(SYMBOL_LIMIT, TOTAL_LIMIT);
        EventProcessor<?> flow = Fluxtion.compile(graph::buildGraph, graph::configureGeneration);

        Files.createDirectories(auditFile.getParent());
        try (PrintWriter auditWriter = new PrintWriter(Files.newBufferedWriter(auditFile))) {
            // Both must be attached BEFORE init(): init() itself dispatches an audited lifecycle event.
            flow.setAuditLogProcessor(record -> auditWriter.println(record.asCharSequence()));
            flow.addSink(RiskGraphBuilder.ALERT_SINK, alert -> System.out.println("   ALERT  " + alert));
            flow.init();

            System.out.printf("riskflow — %d events from %s%n", events.size(), feed);
            System.out.printf("limits: per-symbol %,.0f  total %,.0f%n%n", SYMBOL_LIMIT, TOTAL_LIMIT);

            ExposureCalculator exposure = flow.getNodeById(RiskGraphBuilder.EXPOSURE_CALCULATOR);
            for (Object event : events) {
                System.out.println("-> " + event);
                flow.onEvent(event);
                System.out.printf("   exposure %s total=%,.2f%n", format(exposure), exposure.getTotalExposure());
            }
            flow.tearDown();
            auditWriter.flush();

            printSummary(flow, exposure, auditFile);
        }
    }

    private static void printSummary(EventProcessor<?> flow, ExposureCalculator exposure, Path auditFile)
            throws NoSuchFieldException, IOException {
        PositionBook positions = flow.getNodeById(RiskGraphBuilder.POSITION_BOOK);
        System.out.println("\nfinal book");
        positions.positions().forEach((symbol, position) ->
                System.out.printf("   %-5s position=%,7d exposure=%,14.2f%n",
                        symbol, position, exposure.exposureFor(symbol)));
        System.out.printf("   %-5s %19s %,14.2f%n", "TOTAL", "", exposure.getTotalExposure());
        System.out.printf("%naudit records: %d in %s%ngenerated dispatcher: %s%n",
                Files.readAllLines(auditFile).size(), auditFile,
                "target/generated-sources/fluxtion/com/example/riskflow/generated/RiskDispatcher.java");
    }

    private static String format(ExposureCalculator exposure) {
        StringBuilder sb = new StringBuilder();
        exposure.exposures().forEach((symbol, value) -> sb.append("%s=%,.2f ".formatted(symbol, value)));
        return sb.toString().trim();
    }
}
