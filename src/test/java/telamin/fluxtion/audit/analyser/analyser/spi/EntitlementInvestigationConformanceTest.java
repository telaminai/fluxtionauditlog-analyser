package telamin.fluxtion.audit.analyser.analyser.spi;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compiler-entitlement investigation opens here unchanged (managed-auth spec, test 14, consumer half).
 *
 * <p>The hosted compiler's entitlement service exports an account's investigation as a diagnostic projection of its
 * committed rows: the rows replayed through a fresh copy of its generated entitlement graph with audit on, each at its
 * own recorded time, plus that graph's GraphML. The fixture is one such export (account and attempt ids
 * pseudonymous; package names replaced by placeholders). This test pins that the analyser reads it as an ordinary
 * audit log: every record, in time order, traced, paired with its graph, and showing a charge, its reason and the
 * transitions around it. If the producer's format drifts, this fails rather than the investigation silently reading
 * as something else.
 */
class EntitlementInvestigationConformanceTest {

    private static String resource(String name) {
        try (InputStream in = EntitlementInvestigationConformanceTest.class
                .getResourceAsStream("/entitlement-investigation/" + name)) {
            if (in == null) throw new IllegalStateException("fixture missing: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final LogStore LOG = new HeapLogStore(resource("entitlement-investigation.yaml"));

    private static Optional<LogRecord> first(String eventType) {
        for (int i = 0; i < LOG.size(); i++)
            if (LOG.record(i).eventToString() != null && LOG.record(i).eventToString().contains("type=" + eventType + ","))
                return Optional.of(LOG.record(i));
        return Optional.empty();
    }

    private static NodeLog node(LogRecord r, String id) {
        return r.nodeLogs().stream().filter(n -> n.instanceId().equals(id) && n.last("account") != null
                        || n.instanceId().equals(id) && n.last("requestId") != null)
                .findFirst().orElseThrow(() -> new AssertionError(id + " logged nothing in " + r.eventToString()));
    }

    private static String value(NodeLog n, String key) {
        return n.last(key) == null ? null : n.last(key).rawValue();
    }

    @Test
    void everyCommittedRowIsOneRecord_inTimeOrder_andTraced() {
        assertEquals(11, LOG.size(), "the export holds eleven committed rows");
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < LOG.size(); i++) {
            LogRecord r = LOG.record(i);
            assertEquals("EntitlementEvent", r.event());
            assertTrue(r.logTime() >= previous, "time order at record " + i);
            previous = r.logTime();
            assertTrue(AuditTrace.tracesEveryInvocation(r), "every handler invocation is traced, record " + i);
        }
    }

    /**
     * The projection replays each committed row AT ITS OWN RECORDED TIME, and every committed row is there. A producer
     * that stamped the replay's wall clock, or skipped a row, would still parse — so the times and sequence numbers
     * are checked against the row each record carries, not only for order.
     */
    @Test
    void eachRecordIsStampedWithItsRowsOwnTime_andNoCommittedRowIsMissing() {
        for (int i = 0; i < LOG.size(); i++) {
            LogRecord r = LOG.record(i);
            String row = r.eventToString();
            assertEquals(i + 1, Long.parseLong(field(row, "sequence")), "committed rows 1..n, none skipped: record " + i);
            long at = Long.parseLong(field(row, "atMillis"));
            assertEquals(at, r.logTime(), "logTime is the row's own time, record " + i);
            assertEquals(at, r.eventTime(), "eventTime is the row's own time, record " + i);
        }
    }

    private static String field(String row, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\[ ]" + name + "=([^,\\]]*)").matcher(row);
        assertTrue(m.find(), name + " missing from " + row);
        return m.group(1);
    }

    @Test
    void theGraphItRanOnPairsWithTheLog() {
        ProcessorTopology graph = GraphMlParser.parse(resource("entitlement-investigation.graphml"));
        Set<String> logged = new LinkedHashSet<>();
        for (int i = 0; i < LOG.size(); i++) for (NodeLog n : LOG.record(i).nodeLogs()) logged.add(n.instanceId());
        assertTrue(logged.containsAll(Set.of("accounts", "ledger", "requests")), logged.toString());
        GraphPairing pairing = GraphPairing.of(GraphPairing.declaredNodeIds(graph), logged);
        assertTrue(pairing.applies(), pairing.reason());
        assertEquals(pairing.logged(), pairing.matched(), "every logging node is declared by the graph");
    }

    @Test
    void aChargeShowsItsReasonAndTheBalanceAfterIt() {
        LogRecord settled = first("GENERATION_SETTLED").orElseThrow();
        NodeLog ledger = node(settled, "ledger");
        assertEquals("1", value(ledger, "consumed"), "one unit charged");
        assertEquals("free", value(ledger, "bucket"), "against the bucket it reserved from");
        NodeLog request = node(settled, "requests");
        assertEquals("SETTLED", value(request, "status"));
        assertEquals("generation_succeeded", value(request, "reason"), "the rule that charged it");
        assertTrue(value(request, "result").startsWith("sha256:"), "the durable result it settled with");

        LogRecord reserved = first("GENERATION_RESERVED").orElseThrow();
        assertEquals("within_allowance", value(node(reserved, "requests"), "reason"),
                "the reservation before it, and why it was admitted");
    }

    @Test
    void aFailureIsReleased_andARefusalSaysWhy() {
        LogRecord released = first("GENERATION_RELEASED").orElseThrow();
        assertEquals("1", value(node(released, "ledger"), "released"), "the unit came back");
        assertEquals("GENERATION_ERROR", value(node(released, "requests"), "failure"));
        assertEquals("not_charged", value(node(released, "requests"), "reason"));

        LogRecord refused = first("GENERATION_REFUSED").orElseThrow();
        assertEquals("CREDIT_ALLOWANCE_EXHAUSTED", value(node(refused, "ledger"), "refused"));
    }

    /** An operator's removal is its own row: what was removed, what was asked, who and why, and the bucket after it. */
    @Test
    void aRemovalShowsWhatWasRemoved_WhatWasAsked_AndTheBucketAfterIt() {
        LogRecord removal = first("ALLOWANCE_REMOVED").orElseThrow();
        NodeLog ledger = node(removal, "ledger");
        assertEquals("support", value(ledger, "bucket"));
        assertEquals("3", value(ledger, "removed"), "more than was left removes what was left");
        assertEquals("0", value(ledger, "available"), "nothing left in the bucket after it");
        assertEquals("requested:5", field(removal.eventToString(), "detail"), "what the operator asked for");
        assertEquals("operator:ops:mistaken_grant", field(removal.eventToString(), "reasonCode"), "who and why");
    }

    @Test
    void theExportCarriesNoInternalAccountIdsOrPrivatePackageNames() {
        String yaml = resource("entitlement-investigation.yaml");
        String graphml = resource("entitlement-investigation.graphml");
        assertFalse(yaml.contains("acct_"), "pseudonymous: internal account ids never leave the service");
        assertTrue(yaml.contains("account-1"));
        assertFalse(graphml.contains("fluxtion.entitlement"), "the producer's private package is a placeholder here");
    }

    /**
     * Codes the producer's decider emits that this fixture does not exercise. Named here so the guide may name them;
     * each was read from the compiler's entitlement graph (its decider and request registry), not inferred. Adding a
     * code to the guide that is neither in the fixture nor here fails the test below.
     */
    private static final Set<String> PRODUCER_CODES_NOT_IN_FIXTURE = Set.of(
            "EXPIRED", "retention_elapsed", "account_inactive", "no_account", "request_changed_under_same_id",
            // an operator's removal, refused (UNKNOWN_BUCKET) or a no-op: answered to the operator, never written as a
            // row, so no export can contain them
            "UNKNOWN_BUCKET", "ALREADY_REMOVED", "NOTHING_TO_REMOVE");

    /** Every key, status and reason the guide's *Investigate a compiler charge* names is one the producer writes. */
    @Test
    void theGuideNamesOnlyKeysAndCodesTheProducerWrites() throws IOException {
        String guide = Files.readString(Path.of("docs/site/getting-started.md"));
        int start = guide.indexOf("## Investigate a compiler charge");
        assertTrue(start >= 0, "the guide section exists");
        int end = guide.indexOf("\n## ", start + 3);
        String section = guide.substring(start, end < 0 ? guide.length() : end);
        String yaml = resource("entitlement-investigation.yaml");
        Matcher m = Pattern.compile("`([^`]+)`").matcher(section);
        Set<String> unknown = new LinkedHashSet<>();
        int checked = 0;
        while (m.find()) {
            String token = m.group(1);
            if (token.startsWith("investigation-") || token.startsWith(".")
                    || token.matches("[A-Za-z0-9_-]+\\.(graphml|yaml|zip)")) continue; // file names, not record content
            for (String part : token.split(":\\s*|\\.")) {
                if (part.isBlank()) continue;
                checked++;
                if (!Pattern.compile("\\b" + Pattern.quote(part) + "\\b").matcher(yaml).find()
                        && !PRODUCER_CODES_NOT_IN_FIXTURE.contains(part)) unknown.add(part);
            }
        }
        assertTrue(checked > 10, "the section's keys were found: " + checked);
        assertTrue(unknown.isEmpty(), "the guide names what the producer never writes: " + unknown);
    }
}
