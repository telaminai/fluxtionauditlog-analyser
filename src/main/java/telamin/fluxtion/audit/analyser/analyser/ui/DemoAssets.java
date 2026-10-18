package telamin.fluxtion.audit.analyser.analyser.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The demo set that ships in the jar, materialised on disk so the analyser can open it (M36).
 *
 * <p><b>Why on disk.</b> Every verb the start page's actions use takes a <i>path</i> — {@code open
 * {log}}, {@code open {graphml}}, {@code source_root {add}}. A classpath resource is not a path, so
 * the set is extracted once, to a stable directory under the profile, and reused. Extracting is also
 * what makes the demo behave exactly like a real investigation: the same open, the same store, the
 * same everything. A special in-memory path would be a demo of something the product does not do.
 *
 * <p><b>Why these files.</b> Each one exists to make a specific claim on the start page TRUE. The
 * first draft of M36 was going to bundle a single log; there is no graph in this repo that matches
 * it, so coverage, step-through, dispatch order and source navigation would all have been buttons
 * that did nothing — the failure the page exists to avoid.
 *
 * <ul>
 *   <li><b>the walkthrough log</b> + <b>the graph</b> — 5 of the graph's 20 nodes log, so the
 *       topology renders, cycles step, and <b>coverage has a real answer</b> rather than a vacuous
 *       100%;</li>
 *   <li><b>the traced log</b> — the only one where "did not run" is <i>proof</i>: it records every
 *       invocation, so absence means something. Without it the strongest claim on the page could
 *       only be described, never shown;</li>
 *   <li><b>the series log</b> — 726 records, because ten points is not a chart;</li>
 *   <li><b>the source</b> — so clicking a node in the topology opens the code, instead of being a
 *       picture you cannot go into.</li>
 * </ul>
 */
public final class DemoAssets {

    /** Every file that ships, relative to {@code /demo} on the classpath. */
    private static final List<String> FILES = List.of(
            "demo-quote-audit.yaml",
            "demo-quote-audit-traced.yaml",
            "demo-quote-series.yaml",
            "demo-quote-processor.graphml",
            // M74 P2 (r8 F13): the DEMO journey "Find the first recorded breach" — its log and its walk + chart, exactly as
            // the site bundle carries them, so the start page's S1 plays it from the jar (JourneyCatalogueTest pins it)
            "journey/demo-quote-series.yaml",
            "journey/first-breach.fluxtion-settings",
            "com/acme/demo/api/QuoteControl.java",
            "com/acme/demo/builder/DemoQuoteProcessorBuilder.java",
            "com/acme/demo/builder/DemoQuoteTracedProcessorBuilder.java",
            "com/acme/demo/event/Events.java",
            "com/acme/demo/generated/DemoQuoteProcessor.java",
            "com/acme/demo/generated/DemoQuoteTracedProcessor.java",
            "com/acme/demo/node/Nodes.java");

    private DemoAssets() {
    }

    /** Where the set lives once unpacked: {@code ~/.fluxtion-analyser/demo}. */
    public static Path root() {
        return Path.of(System.getProperty("user.home"), ".fluxtion-analyser", "demo");
    }

    public static Path log() {
        return root().resolve("demo-quote-audit.yaml");
    }

    /** The TRACED log — the only one where an absent node is proof rather than silence. */
    public static Path tracedLog() {
        return root().resolve("demo-quote-audit-traced.yaml");
    }

    /** 726 records, for the chart. */
    public static Path seriesLog() {
        return root().resolve("demo-quote-series.yaml");
    }

    /** The DEMO journey's log — the site bundle's bytes, which its walk is bound to (r8 F13). */
    public static Path journeyLog() {
        return root().resolve("journey").resolve("demo-quote-series.yaml");
    }

    /** The DEMO journey's walk and chart, as a profile fragment. */
    public static Path journeyProfile() {
        return root().resolve("journey").resolve("first-breach.fluxtion-settings");
    }

    public static Path graphml() {
        return root().resolve("demo-quote-processor.graphml");
    }

    /** The source root a node resolves through. */
    public static Path sourceRoot() {
        return root();
    }

    /**
     * Unpack if needed and return the root. Re-extracts a file that is missing or empty — a
     * half-written directory from an interrupted first run must heal rather than leave one action
     * broken, because the whole promise of these actions is that they work on first contact.
     *
     * @throws UncheckedIOException if the set cannot be written; the caller shows why rather than
     *                              offering a button that fails
     */
    public static Path install() {
        Path root = root();
        try {
            for (String name : FILES) {
                Path out = root.resolve(name);
                if (Files.isRegularFile(out) && Files.size(out) > 0) continue;
                Files.createDirectories(out.getParent());
                try (InputStream in = DemoAssets.class.getResourceAsStream("/demo/" + name)) {
                    if (in == null) {
                        throw new IOException("the demo set is incomplete in this build: " + name);
                    }
                    Files.write(out, in.readAllBytes());
                }
            }
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("could not unpack the demo set to " + root, e);
        }
    }

    /** The sample's event processor: its generated source ships in the set, under {@link #sourceRoot()}. */
    public static final String PROCESSOR = "com.acme.demo.generated.DemoQuoteProcessor";
    /** The traced log's processor, also shipped, so the traced log resolves to its own source. */
    public static final String TRACED_PROCESSOR = "com.acme.demo.generated.DemoQuoteTracedProcessor";
    /**
     * The placeholder {@code AppConfig}'s default once named (owner hands-on test, 2026-10-18): a class that does not
     * exist in the demo set. A sample profile written before then holds it, and the sample's source and
     * {@code source {bean}} could never resolve.
     */
    static final String LEGACY_PLACEHOLDER = "com.acme.marketmaker.strategy.DemoMarketMakerStrategy";

    /**
     * The sample project's profile under {@code root}: written when absent, and HEALED when it still names the
     * placeholder processor (or none), or when its source roots do not reach the shipped processor's source — only the
     * processor fields and the demo root change, so a saved DEMO tour or report survives. The root is written
     * project-relative ({@code sourceRoot.0=.}), so a moved home still resolves it.
     *
     * @return the profile's path
     */
    public static Path ensureSampleProfile(Path root) throws IOException {
        Path profile = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(root);
        var share = new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare();
        var sample = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        if (Files.isRegularFile(profile)) {
            var loaded = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.load(profile, sample, share);
            if (!loaded.loaded() || !needsProcessorHeal(sample) && !needsSourceRootHeal(sample, root)) return profile;
        }
        sampleSourceRoot(sample, root);
        sampleProcessors(sample);
        telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile, sample, share);
        return profile;
    }

    /** The profile's own source roots do not find the shipped processor's source. */
    static boolean needsSourceRootHeal(telamin.fluxtion.audit.analyser.analyser.config.AppConfig c, Path root) {
        return new telamin.fluxtion.audit.analyser.analyser.source.SourceRootResolver(c.sourceRoots).find(PROCESSOR).isEmpty();
    }

    /** The demo folder (where {@code com/} sits) as a source root, added once. */
    static void sampleSourceRoot(telamin.fluxtion.audit.analyser.analyser.config.AppConfig c, Path root) {
        Path demo = root.toAbsolutePath().normalize();
        boolean present = c.sourceRoots.stream().anyMatch(r -> {
            try { return Path.of(r).toAbsolutePath().normalize().equals(demo); }
            catch (RuntimeException notAPath) { return false; }
        });
        if (!present) c.sourceRoots.add(demo.toString());
    }

    /** The profile names the placeholder, or no processor at all. */
    static boolean needsProcessorHeal(telamin.fluxtion.audit.analyser.analyser.config.AppConfig c) {
        String selected = c.selectedEventProcessor == null ? "" : c.selectedEventProcessor.trim();
        return selected.isEmpty() || selected.equals(LEGACY_PLACEHOLDER) || c.eventProcessorFqns.contains(LEGACY_PLACEHOLDER);
    }

    /** The sample's two shipped processors, the walkthrough's selected. */
    static void sampleProcessors(telamin.fluxtion.audit.analyser.analyser.config.AppConfig c) {
        c.eventProcessorFqns.remove(LEGACY_PLACEHOLDER);
        for (String fqn : List.of(PROCESSOR, TRACED_PROCESSOR)) {
            if (!c.eventProcessorFqns.contains(fqn)) c.eventProcessorFqns.add(fqn);
        }
        c.selectedEventProcessor = PROCESSOR;
    }

    /** The files that ship — exposed so a test can assert the jar carries every one. */
    public static List<String> files() {
        return FILES;
    }
}
