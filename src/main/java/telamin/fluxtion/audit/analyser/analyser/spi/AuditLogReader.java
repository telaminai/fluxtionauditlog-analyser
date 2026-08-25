package telamin.fluxtion.audit.analyser.analyser.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

/**
 * The log-source plugin SPI (spec-log-source-plugins M31.1, D-P1): a reader identifies itself, says
 * whether a source is its kind, and streams <b>canonical record text</b> in <b>container order</b>.
 * The CORE builds the index, the store, the filter columns and everything above them — a reader that
 * produces records correctly gets every analyser feature for free, including ones that ship after it.
 *
 * <p><b>D-P2 — canonical text.</b> Each record arrives as the standard {@code eventLogRecord} YAML
 * shape (for a text container, the original bytes; for parquet/DB, a rendering the reader produces).
 * Half the analyser's surfaces are text-shaped — the detail viewer, the free-text filter, {@code read},
 * report quoting — and the rendering is what keeps them all working over any container.
 *
 * <p><b>Mandatory time base</b> (review X2): a parquet reader KNOWS its epoch unit; requiring the
 * declaration costs a plugin author nothing and means plugin sources arrive better described than the
 * native YAML log. This is a compatibility surface — additive evolution only once published.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} from jars the user explicitly
 * installs. Loading a jar is <b>arbitrary code execution</b>; the trust boundary is the user's install
 * action, and the FAQ says so in those words (D-P3).
 */
public interface AuditLogReader {

    /** Stable machine id ({@code "yaml"}, {@code "parquet"}) — used by {@code open {format}}. */
    String formatId();

    /** Human name for Settings ▸ Plugins and open-dialog filters. */
    String displayName();

    /** Whether {@code source} looks like this reader's container. May inspect the file head. */
    boolean canOpen(Path source);

    /** The declared clock domain of records this reader produces. Never null — declared, not sniffed. */
    TimeBase timeBase();

    /** What the container supports; features degrade LOUDLY where it says no (D-P4). */
    Capabilities capabilities();

    /**
     * Stream every record's canonical text, in container order. The core parses, indexes and stores;
     * the reader never sees Swing, the index, or another plugin.
     */
    void read(Path source, Consumer<String> recordText) throws IOException;

    /** {@code epoch}: millis | micros | nanos · {@code zone}: IANA · {@code source}: wallClock | monotonic | injected. */
    record TimeBase(String epoch, String zone, String source) {
        public static TimeBase wallClockMillisUtc() {
            return new TimeBase("millis", "UTC", "wallClock");
        }
    }

    /**
     * The graph this source declares, when it has one (M34.1, spec-source-adapters §B).
     *
     * <p><b>Default: empty.</b> This is a published surface — a reader written against 1.5.0 must
     * keep compiling, the same discipline {@link Capabilities} follows.
     *
     * <p><b>Provenance rides the RETURNED graph, not the reader</b> (spec review F4). Availability is
     * per SOURCE, not per adapter: one estate has a workflow-registry export where another has none,
     * so a fixed {@code graphSupport()} on the reader would be wrong for half its users. Returning
     * empty is the honest answer for a source that cannot say, and it is a different statement from
     * returning an INFERRED graph.
     *
     * <p>The graph is handed over in the CORE's vocabulary (D-A4) — {@link ProcessorTopology.Node}
     * and {@link ProcessorTopology.Edge} — because a richer engine-specific model would force every
     * consumer above to know which engine it was looking at, which is the coupling this SPI exists
     * to remove. A reader maps its concepts onto the existing kinds or declines a kind.
     */
    default Optional<SourceGraph> graph(Path source) throws IOException {
        return Optional.empty();
    }

    /**
     * A graph and where it came from.
     *
     * @param provenance DECLARED when the source states its own structure (a compiler emitted it, a
     *                   registry holds it); INFERRED when it was reconstructed from what was
     *                   observed. The distinction is not decoration: <b>coverage is
     *                   "declared minus observed"</b>, so subtracting from an inferred set always
     *                   reports 100% and the feature that found the POC's 54 dead nodes silently
     *                   becomes a tautology. D-A2: the view says which.
     */
    record SourceGraph(List<ProcessorTopology.Node> nodes, List<ProcessorTopology.Edge> edges,
                       Provenance provenance) {

        public SourceGraph {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            if (provenance == null) {
                throw new IllegalArgumentException(
                        "a SourceGraph must say whether it is DECLARED or INFERRED — an unmarked "
                                + "graph is the one thing coverage cannot safely consume (D-A2)");
            }
        }
    }

    enum Provenance {
        /** The source states its own structure — safe to subtract observed from. */
        DECLARED,
        /** Reconstructed from what ran. Coverage against this is a tautology and must say so. */
        INFERRED
    }

    /**
     * Whether the source can say what ran BEFORE what, within one cycle (M34 D-A1a).
     *
     * <p>This is not metadata about a run — on a concurrent engine it is the difference between
     * evidence and fabrication. A Fluxtion cycle's {@code nodeLogs} order is DERIVED by the AOT
     * compiler and consumed as meaning: step-through reads it back, the topology paints dispatch
     * badges from it, route escalation and the M21 classification depend on it. A source whose
     * components run concurrently has no such order to report, so anything it emits is arrival
     * order — and the M34.0 spike found the presentation identical either way, with nothing on
     * screen distinguishing a compiler-derived order from an invented one.
     */
    enum Ordering {
        /** Within a cycle, position IS dispatch order. Safe to read as causality. */
        TOTAL,
        /** The source could not supply an order. Position is arrival, and consumers must say so. */
        PARTIAL
    }

    /** Capability flags — checked by the core, degraded loudly, never assumed (D-P4). */
    record Capabilities(boolean follow, boolean byteAnchors, boolean randomAccess,
                        Ordering ordering) {

        public Capabilities {
            ordering = ordering == null ? Ordering.TOTAL : ordering;
        }

        /**
         * The pre-M34 shape, kept so readers published against 1.5.0 keep compiling — this is a
         * published compatibility surface and evolves additively only (see the class javadoc).
         * Defaulting to {@link Ordering#TOTAL} is correct for every container that existed when
         * this constructor was the only one: a byte stream of records is totally ordered.
         */
        public Capabilities(boolean follow, boolean byteAnchors, boolean randomAccess) {
            this(follow, byteAnchors, randomAccess, Ordering.TOTAL);
        }
    }
}
