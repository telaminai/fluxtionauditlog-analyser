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
 *
 * <p>A second fixture, {@code entitlement-investigation-plans}, is an export of the same producer after plans and the
 * interpret allowance (compiler design 03): credit of two kinds, an interpretation recorded as {@code interpret:<id>},
 * an operator's {@code CAPABILITY_SET}, and the two new refusals, {@code CAPABILITY_EXCEEDED} and
 * {@code INTERPRET_ALLOWANCE_EXHAUSTED}. The producer's own export test does not yet write that scenario; it was
 * exported from the producer's code unchanged, by a scenario run beside it (provision; one compile and one interpret
 * credit; {@code set-capability maxInputs=10}; a 120-node compile; two interpretations; a 12-node compile). Since the
 * inputs limit (compiler design 03 §3, 2026-10-27) replaced the event-type limit, the scenario's operator limit is
 * {@code maxInputs}; the fixture was regenerated from the producer's {@code InvestigationExportTest}.
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
    private static final LogStore PLANS = new HeapLogStore(resource("entitlement-investigation-plans.yaml"));

    private static Optional<LogRecord> first(String eventType) {
        return first(LOG, eventType, null);
    }

    /** The first record of {@code eventType} in {@code log} whose row also contains {@code alsoContains}, if given. */
    private static Optional<LogRecord> first(LogStore log, String eventType, String alsoContains) {
        for (int i = 0; i < log.size(); i++) {
            String row = log.record(i).eventToString();
            if (row != null && row.contains("type=" + eventType + ",") && (alsoContains == null || row.contains(alsoContains)))
                return Optional.of(log.record(i));
        }
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
        assertEquals(10, PLANS.size(), "the plans export holds ten committed rows");
        for (LogStore log : java.util.List.of(LOG, PLANS)) {
            long previous = Long.MIN_VALUE;
            for (int i = 0; i < log.size(); i++) {
                LogRecord r = log.record(i);
                assertEquals("EntitlementEvent", r.event());
                assertTrue(r.logTime() >= previous, "time order at record " + i);
                previous = r.logTime();
                assertTrue(AuditTrace.tracesEveryInvocation(r), "every handler invocation is traced, record " + i);
            }
        }
    }

    /**
     * The projection replays each committed row AT ITS OWN RECORDED TIME, and every committed row is there. A producer
     * that stamped the replay's wall clock, or skipped a row, would still parse — so the times and sequence numbers
     * are checked against the row each record carries, not only for order.
     */
    @Test
    void eachRecordIsStampedWithItsRowsOwnTime_andNoCommittedRowIsMissing() {
        for (LogStore log : java.util.List.of(LOG, PLANS)) {
            for (int i = 0; i < log.size(); i++) {
                LogRecord r = log.record(i);
                String row = r.eventToString();
                assertEquals(i + 1, Long.parseLong(field(row, "sequence")), "committed rows 1..n, none skipped: record " + i);
                long at = Long.parseLong(field(row, "atMillis"));
                assertEquals(at, r.logTime(), "logTime is the row's own time, record " + i);
                assertEquals(at, r.eventTime(), "eventTime is the row's own time, record " + i);
            }
        }
    }

    private static String field(String row, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\[ ]" + name + "=([^,\\]]*)").matcher(row);
        assertTrue(m.find(), name + " missing from " + row);
        return m.group(1);
    }

    @Test
    void theGraphItRanOnPairsWithTheLog() {
        assertPairs(LOG, "entitlement-investigation.graphml", Set.of("accounts", "ledger", "requests"));
        assertPairs(PLANS, "entitlement-investigation-plans.graphml", Set.of("accounts", "ledger", "requests", "capabilities"));
    }

    private static void assertPairs(LogStore log, String graphml, Set<String> expected) {
        ProcessorTopology graph = GraphMlParser.parse(resource(graphml));
        Set<String> logged = new LinkedHashSet<>();
        for (int i = 0; i < log.size(); i++) for (NodeLog n : log.record(i).nodeLogs()) logged.add(n.instanceId());
        assertTrue(logged.containsAll(expected), logged.toString());
        GraphPairing pairing = GraphPairing.of(GraphPairing.declaredNodeIds(graph), logged);
        assertTrue(pairing.applies(), pairing.reason());
        assertEquals(pairing.logged(), pairing.matched(), "every logging node is declared by the graph");
    }

    /** Every row since credit kinds says which kind it moved; a compile row is {@code compile}, before and after plans. */
    @Test
    void everyCreditRowNamesItsKind() {
        for (int i = 0; i < LOG.size(); i++) {
            String row = LOG.record(i).eventToString();
            if (!row.contains("type=ACCOUNT_PROVISIONED,"))
                assertEquals("compile", field(row, "creditKind"), "record " + i + " of the compile-only export");
        }
        assertEquals("compile", value(node(first("GENERATION_SETTLED").orElseThrow(), "ledger"), "kind"),
                "the ledger names the kind too");
    }

    /**
     * Design 03 §1: an interpretation is the same transaction as a generation, against its own credit. Its request id
     * is {@code interpret:<id>}, its row and ledger say {@code interpret}, and it draws on the interpret bucket only.
     */
    @Test
    void anInterpretationIsReservedAndSettledAgainstItsOwnCredit() {
        LogRecord granted = first(PLANS, "ALLOWANCE_GRANTED", "creditKind=interpret").orElseThrow();
        assertEquals("interpret", value(node(granted, "ledger"), "kind"));
        LogRecord reserved = first(PLANS, "GENERATION_RESERVED", "creditKind=interpret").orElseThrow();
        assertEquals("interpret:model-0001", field(reserved.eventToString(), "requestId"));
        assertEquals("interpret:model-0001", value(node(reserved, "requests"), "requestId"));
        assertEquals("interpret", value(node(reserved, "requests"), "kind"));
        LogRecord settled = first(PLANS, "GENERATION_SETTLED", "creditKind=interpret").orElseThrow();
        NodeLog ledger = node(settled, "ledger");
        assertEquals("interpret", value(ledger, "kind"));
        assertEquals("1", value(ledger, "consumed"), "one interpret unit charged");
        assertEquals("generation_succeeded", value(node(settled, "requests"), "reason"));
        LogRecord compile = first(PLANS, "GENERATION_SETTLED", "creditKind=compile").orElseThrow();
        assertEquals("compile", value(node(compile, "ledger"), "kind"));
        assertEquals("0", value(node(compile, "ledger"), "available"), "the compile bucket ran down on its own");
    }

    /** Design 03 §2: an operator's per-account capability is a row of its own, and says what was set and by whom. */
    @Test
    void anOperatorsCapabilityIsItsOwnRow() {
        LogRecord set = first(PLANS, "CAPABILITY_SET", null).orElseThrow();
        assertEquals("maxInputs=10", field(set.eventToString(), "detail"));
        assertEquals("operator:ops:contract", field(set.eventToString(), "reasonCode"));
        NodeLog caps = node(set, "capabilities");
        assertEquals("maxInputs", value(caps, "capability"));
        assertEquals("10", value(caps, "value"));
    }

    /**
     * Design 03 §3: a request beyond the plan is refused before any work and charged nothing; the row names the
     * limit. An exhausted interpret allowance is a refusal of its own kind, with its own code.
     */
    @Test
    void aCapabilityRefusalNamesTheLimit_andAnExhaustedInterpretAllowanceHasItsOwnCode() {
        LogRecord over = first(PLANS, "GENERATION_REFUSED", "failureCode=CAPABILITY_EXCEEDED").orElseThrow();
        assertEquals("capability=maxNodes;limit=20;requested=120;plan=free", field(over.eventToString(), "detail"));
        assertEquals("capability_exceeded", field(over.eventToString(), "reasonCode"));
        assertEquals("0", field(over.eventToString(), "units"), "nothing charged");
        assertEquals("CAPABILITY_EXCEEDED", value(node(over, "ledger"), "refused"));
        assertEquals("capability_exceeded", value(node(over, "requests"), "reason"));

        LogRecord exhausted = first(PLANS, "GENERATION_REFUSED", "failureCode=INTERPRET_ALLOWANCE_EXHAUSTED").orElseThrow();
        assertEquals("interpret", field(exhausted.eventToString(), "creditKind"));
        assertEquals("interpret:model-0002", field(exhausted.eventToString(), "requestId"));
        assertEquals("INTERPRET_ALLOWANCE_EXHAUSTED", value(node(exhausted, "ledger"), "refused"));
        assertEquals("exhausted", value(node(exhausted, "requests"), "reason"));
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
        for (String name : java.util.List.of("entitlement-investigation", "entitlement-investigation-plans")) {
            String yaml = resource(name + ".yaml");
            String graphml = resource(name + ".graphml");
            assertFalse(yaml.contains("acct_"), "pseudonymous: internal account ids never leave the service");
            assertTrue(yaml.contains("account-1"));
            assertFalse(graphml.contains("fluxtion.entitlement"), "the producer's private package is a placeholder here");
        }
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
            "UNKNOWN_BUCKET", "ALREADY_REMOVED", "NOTHING_TO_REMOVE",
            // the capability names a CAPABILITY_EXCEEDED refusal or a CAPABILITY_SET row may carry (the producer's
            // PlanProfile names); this fixture refuses only maxNodes and sets only maxInputs
            "maxExportedInterfaces", "targets", "wasm", "assurance",
            // an operator limit recorded before maxInputs replaced it (2026-10-27): its rows still carry this name and
            // the producer replays them as maxInputs
            "maxEventTypes",
            // refusal details for the two input limits, in the producer's Refusal.detail() form; their shape is pinned
            // against the fixture's own refusal by detailExamplesHaveTheShapeTheProducerWrites
            "capability=maxInputs;limit=6;requested=7;plan=free",
            "capability=maxExportedInterfaces;limit=1;requested=2;plan=free");

    /** The refusal details the guide names but the fixture does not hold are in the shape the fixture's refusal has. */
    @Test
    void detailExamplesHaveTheShapeTheProducerWrites() {
        LogRecord over = first(PLANS, "GENERATION_REFUSED", "failureCode=CAPABILITY_EXCEEDED").orElseThrow();
        String shape = field(over.eventToString(), "detail").replaceAll("=[^;]+", "=_");
        assertEquals("capability=_;limit=_;requested=_;plan=_", shape);
        int examples = 0;
        for (String code : PRODUCER_CODES_NOT_IN_FIXTURE) {
            if (!code.startsWith("capability=")) continue;
            examples++;
            assertEquals(shape, code.replaceAll("=[^;]+", "=_"), code);
            String capability = code.substring("capability=".length(), code.indexOf(';'));
            assertTrue(PRODUCER_CODES_NOT_IN_FIXTURE.contains(capability) || resource("entitlement-investigation-plans.yaml").contains(capability), capability);
        }
        assertEquals(2, examples, "maxInputs and maxExportedInterfaces");
    }

    /** Every key, status and reason the guide's *Investigate a compiler charge* names is one the producer writes. */
    @Test
    void theGuideNamesOnlyKeysAndCodesTheProducerWrites() throws IOException {
        String guide = Files.readString(Path.of("docs/site/getting-started.md"));
        int start = guide.indexOf("## Investigate a compiler charge");
        assertTrue(start >= 0, "the guide section exists");
        int end = guide.indexOf("\n## ", start + 3);
        String section = guide.substring(start, end < 0 ? guide.length() : end);
        String yaml = resource("entitlement-investigation.yaml") + resource("entitlement-investigation-plans.yaml");
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
