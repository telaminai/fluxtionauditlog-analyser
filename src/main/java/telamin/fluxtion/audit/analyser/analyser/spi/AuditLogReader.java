package telamin.fluxtion.audit.analyser.analyser.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

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

    /** Capability flags — checked by the core, degraded loudly, never assumed (D-P4). */
    record Capabilities(boolean follow, boolean byteAnchors, boolean randomAccess) {
    }
}
