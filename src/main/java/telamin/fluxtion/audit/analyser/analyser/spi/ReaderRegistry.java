package telamin.fluxtion.audit.analyser.analyser.spi;

import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStores;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers and holds {@link AuditLogReader}s: the built-in YAML reader plus any the user explicitly
 * installed as jars in the plugins directory (D-P3 — <b>loading a jar is arbitrary code execution</b>;
 * the install action is the trust boundary, and the FAQ says so in those words).
 *
 * <p>Each plugin jar gets its OWN {@link PluginClassLoader} with the delegation rule that makes
 * acceptance 5 achievable (review P1): <b>parent-first for the SPI package and the JDK, child-first
 * for everything else</b>. Pure isolation would break the hand-off — a plugin loading its own copy of
 * the SPI types gives the core objects whose classes are not the core's classes — while parent-first
 * everywhere would let one plugin's dependency tree fight another's.
 *
 * <p>Store construction is routed HERE, not on the SPI (D-P1 keeps the SPI records-only): the
 * built-in text reader gets the existing size-thresholded Heap/Mapped stores with real byte anchors
 * and follow; every other reader gets a generic {@link SpiLogStore} over its record stream.
 */
public final class ReaderRegistry {

    private final List<AuditLogReader> readers = new ArrayList<>();
    private final List<String> loadNotes = new ArrayList<>();

    public ReaderRegistry() {
        readers.add(new YamlAuditReader());
    }

    /** Load every {@code *.jar} in {@code pluginsDir} (explicitly-installed jars only; no network). */
    public void loadPlugins(Path pluginsDir) {
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) return;
        try (var stream = Files.list(pluginsDir)) {
            for (Path jar : stream.sorted().toList()) {
                if (!jar.getFileName().toString().endsWith(".jar")) continue;
                try {
                    PluginClassLoader loader = new PluginClassLoader(jar, getClass().getClassLoader());
                    int before = readers.size();
                    for (AuditLogReader r : ServiceLoader.load(AuditLogReader.class, loader)) {
                        try {
                            register(r);
                        } catch (IllegalArgumentException e) {
                            loadNotes.add(jar.getFileName() + ": " + e.getMessage());
                        }
                    }
                    if (readers.size() == before) {
                        loadNotes.add(jar.getFileName() + ": no AuditLogReader services found");
                    }
                } catch (Throwable t) {
                    loadNotes.add(jar.getFileName() + ": failed to load — " + t.getMessage());
                }
            }
        } catch (IOException e) {
            loadNotes.add("could not list " + pluginsDir + ": " + e.getMessage());
        }
    }

    /**
     * Register a reader directly. The plugin loader uses this after its timeBase check; tests use it
     * to stand in a reader without building a jar. A null timeBase is refused here too — one rule.
     */
    public void register(AuditLogReader reader) {
        if (reader.timeBase() == null) {
            throw new IllegalArgumentException("reader '" + reader.formatId()
                    + "' declares no timeBase — the declaration is mandatory (M31 X2)");
        }
        readers.add(reader);
    }

    /** All readers, built-in first. */
    public List<AuditLogReader> readers() {
        return List.copyOf(readers);
    }

    /** What happened during plugin loading — surfaced in Settings ▸ Plugins, never swallowed. */
    public List<String> loadNotes() {
        return List.copyOf(loadNotes);
    }

    /** The reader claiming {@code source}: an explicit {@code formatId} wins; otherwise first canOpen. */
    public AuditLogReader readerFor(Path source, String formatId) {
        if (formatId != null && !formatId.isBlank()) {
            for (AuditLogReader r : readers) {
                if (r.formatId().equals(formatId.trim())) return r;
            }
            return null;
        }
        for (AuditLogReader r : readers) {
            if (r.canOpen(source)) return r;
        }
        return null;
    }

    /** Human list for refusals: {@code yaml (Fluxtion audit log (YAML)), parquet (…)}. */
    public String describeReaders() {
        StringBuilder sb = new StringBuilder();
        for (AuditLogReader r : readers) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.formatId());
        }
        return sb.toString();
    }

    /**
     * Open {@code source} with {@code reader}. The built-in text reader keeps its optimised stores
     * (mmap past the threshold, real byte anchors, follow); everything else goes through the generic
     * record-stream store.
     */
    public LogStore open(AuditLogReader reader, Path source, int thresholdMb) throws IOException {
        if (reader instanceof YamlAuditReader) {
            return LogStores.open(source, thresholdMb);
        }
        return SpiLogStore.open(reader, source);
    }

    /**
     * Parent-first for the SPI package and the JDK; child-first for everything else (review P1).
     * One plugin's Chronicle must not fight another's — and the SPI types must be the CORE's classes
     * or every hand-off dies with {@code ClassCastException}.
     */
    static final class PluginClassLoader extends URLClassLoader {
        private static final String SPI_PACKAGE = "telamin.fluxtion.audit.analyser.analyser.spi";

        PluginClassLoader(Path jar, ClassLoader parent) throws IOException {
            super(new URL[]{jar.toUri().toURL()}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith(SPI_PACKAGE)) {
                    return super.loadClass(name, resolve);   // parent-first: shared, typed hand-off
                }
                try {
                    Class<?> c = findClass(name);             // child-first: the plugin's own tree wins
                    if (resolve) resolveClass(c);
                    return c;
                } catch (ClassNotFoundException e) {
                    return super.loadClass(name, resolve);    // not in the jar → fall back to parent
                }
            }
        }
    }
}
