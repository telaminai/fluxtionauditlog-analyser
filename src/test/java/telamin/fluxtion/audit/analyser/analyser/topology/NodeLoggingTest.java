package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M40.2b — can this node log at all?
 *
 * <p>The premise recorded in the tracker ("a node that does not extend EventLogNode is silent by
 * construction") is WRONG, and these tests exist mostly to keep it wrong-proof. Read from the runtime
 * jar (rule 6): the contract is the {@code EventLogSource} interface, {@code EventLogNode} is a
 * convenience base, and nine further framework classes reach it transitively. The shipped demo
 * contains the counterexample — {@code RiskMonitor extends SingleNamedNode} and logs happily.
 */
class NodeLoggingTest {

    /** The real demo sources, which ship in the jar — the same file the resolver would hand back. */
    private static final SourceResolver DEMO = fqn -> {
        Path p = Path.of("src/main/resources/demo", fqn.replace('.', '/') + ".java");
        try {
            return Files.exists(p) ? Optional.of(Files.readString(p)) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    };

    private static SourceResolver src(String text) {
        return fqn -> Optional.of(text);
    }

    // ---- the demo, which is the whole reason this slice is safe -----------------------------------

    @Test
    void theDemosSilentNodeIsProvenSilent() {
        // SpreadCalculator declares no supertype at all and never mentions auditLog — it is the one
        // genuinely uncovered id in the demo's 0.833, and it cannot write audit output by construction
        assertEquals(NodeLogging.Capability.SILENT_BY_CONSTRUCTION,
                NodeLogging.of("com.acme.demo.node.Nodes$SpreadCalculator", DEMO));
    }

    @Test
    void aNodeThatLogsViaAFrameworkBaseIsNotMistakenForSilent() {
        // THE false-exclusion case, and the reason the tracker's premise had to be read rather than
        // trusted: RiskMonitor extends SingleNamedNode, NOT EventLogNode, and calls auditLog.info(…).
        // Excluding it would drop a node that demonstrably logs — improving the score by hiding a
        // real observation, which is the error direction nobody notices from the output.
        assertEquals(NodeLogging.Capability.CAN_LOG,
                NodeLogging.of("com.acme.demo.node.Nodes$RiskMonitor", DEMO));
    }

    @Test
    void theDirectBaseIsRecognisedToo() {
        assertEquals(NodeLogging.Capability.CAN_LOG,
                NodeLogging.of("com.acme.demo.node.Nodes$PriceListener", DEMO));
    }

    @Test
    void anExportServiceOnTheSameClassDoesNotConfuseTheHeader() {
        // QuotePublisher extends EventLogNode implements @ExportService QuoteControl — an annotated
        // interface in the clause, which a naive token scan could trip over
        assertEquals(NodeLogging.Capability.CAN_LOG,
                NodeLogging.of("com.acme.demo.node.Nodes$QuotePublisher", DEMO));
    }

    // ---- the safe direction: unknown STAYS COUNTED ------------------------------------------------

    @Test
    void noSourceIsUnknown_neverSilent() {
        assertEquals(NodeLogging.Capability.UNKNOWN,
                NodeLogging.of("com.acme.Missing", fqn -> Optional.empty()));
        assertEquals(NodeLogging.Capability.UNKNOWN, NodeLogging.of("com.acme.X", null));
        assertEquals(NodeLogging.Capability.UNKNOWN, NodeLogging.of(null, DEMO));
    }

    @Test
    void anUnrecognisedSupertypeIsUnknown_becauseItMayLogFurtherUp() {
        assertEquals(NodeLogging.Capability.UNKNOWN,
                NodeLogging.of("com.acme.A", src("package com.acme; class A extends Mystery { }")));
    }

    @Test
    void aClassWeCannotFindInItsOwnFileIsUnknown() {
        assertEquals(NodeLogging.Capability.UNKNOWN,
                NodeLogging.of("com.acme.Outer$Gone", src("package com.acme; class Outer { }")));
    }

    @Test
    void aResolverThatThrowsIsNotTreatedAsEvidence() {
        assertEquals(NodeLogging.Capability.UNKNOWN, NodeLogging.of("com.acme.A", fqn -> {
            throw new IllegalStateException("source root vanished");
        }));
    }

    @Test
    void aContradictoryFileIsUnknownRatherThanAWrongExclusion() {
        // no supertype, yet it mentions auditLog: our reading of the file must be wrong, so refuse to
        // exclude. Preferring UNKNOWN here costs a slightly worse ratio; preferring SILENT would hide
        // a node that logs.
        assertEquals(NodeLogging.Capability.UNKNOWN,
                NodeLogging.of("com.acme.A", src("package com.acme; class A { void f(){ auditLog.info(\"x\", 1); } }")));
    }

    /**
     * Review 2026-08-27 (the second session probed the gate): a logger held under ANY field name, or handed in,
     * must not be excluded. The first cut looked for the literal `auditLog` and dropped this class — a false
     * exclusion, the direction that flatters the score.
     */
    @Test
    void aLoggerUnderAnotherFieldNameIsUnknown_neverSilent() {
        assertEquals(NodeLogging.Capability.UNKNOWN, NodeLogging.of("com.acme.A", src(
                "package com.acme; import com.telamin.fluxtion.runtime.audit.EventLogger; class A { private EventLogger log; void f(){ log.info(\"x\", 1); } }")));
        assertEquals(NodeLogging.Capability.UNKNOWN, NodeLogging.of("com.acme.A", src(
                "package com.acme; class A { void f(Object o){ ((EventLogSource) o).setLogger(null); } }")),
                "naming the contract anywhere in the body is enough to stay counted");
    }

    @Test
    void aPlainClassWithNoSupertypeAndNoLoggingIsSilent() {
        assertEquals(NodeLogging.Capability.SILENT_BY_CONSTRUCTION,
                NodeLogging.of("com.acme.A", src("package com.acme; class A { int f(){ return 1; } }")));
    }

    @Test
    void everyKnownBaseWasMeasuredFromTheRuntimeJar_notInvented() {
        // if this list is ever edited by hand, the edit should be justified the same way it was built:
        // walk fluxtion-runtime and keep what reaches EventLogNode / implements EventLogSource
        for (String base : NodeLogging.AUDIT_CAPABLE) {
            assertEquals(NodeLogging.Capability.CAN_LOG,
                    NodeLogging.of("com.acme.A", src("package com.acme; class A extends " + base + " { }")),
                    base + " is in the measured list but is not recognised in an extends clause");
        }
    }

    @Test
    void aNestedClassIsFoundThroughItsOUTER_notByTheFirstNameMatchInTheFile() {
        // review N2: a file holding a top-level `A` AND a nested `B.A` used to resolve to whichever came
        // first in the text. Reading the wrong declaration can return SILENT for a node that logs, so the
        // lookup now walks the nesting chain. The top-level A here logs; the nested one does not.
        String file = "package p;\n"
                + "class A extends EventLogNode { void f(){ auditLog.info(\"x\", 1); } }\n"
                + "class B { static class A { int n; int f(){ return n; } } }\n";
        assertEquals(NodeLogging.Capability.SILENT_BY_CONSTRUCTION,
                NodeLogging.of("p.B$A", src(file)), "the NESTED A declares no supertype");
        assertEquals(NodeLogging.Capability.CAN_LOG,
                NodeLogging.of("p.A", src(file)), "the TOP-LEVEL A extends EventLogNode");
    }

    @Test
    void aNestedClassWhoseOuterCannotBePlacedIsUnknown() {
        assertEquals(NodeLogging.Capability.UNKNOWN,
                NodeLogging.of("p.Gone$Inner", src("package p; class Other { class Inner { } }")));
    }

    @Test
    void theBodyCheckedIsTheNESTEDonesBody_notTheWholeFile() {
        // the outer mentions EventLogger; the inner does not. Scoping the body check to the whole file
        // would return UNKNOWN for the inner and silently stop excluding anything nested in a logging class.
        String file = "package p; class Outer extends EventLogNode {"
                + " void f(){ EventLogger e = auditLog; }"
                + " static class Plain { int n; } }";
        assertEquals(NodeLogging.Capability.SILENT_BY_CONSTRUCTION, NodeLogging.of("p.Outer$Plain", src(file)));
    }
}
