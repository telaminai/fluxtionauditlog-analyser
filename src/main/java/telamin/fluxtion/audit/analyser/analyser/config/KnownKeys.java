package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * M38.7 — the key FAMILIES this version of the analyser writes, so a writer can tell "a key I own and
 * chose not to write" from "a key a newer version wrote that I do not understand".
 *
 * <p>The hazard (found live, 2026-08-27): both writers rebuilt their file from scratch, so an OLDER
 * analyser that opened a profile written by a NEWER one silently dropped every key it did not know on its
 * next save — for a team on mixed versions, a committed profile losing its M38 facts with no diff anyone
 * asked for. Dropping a key you do not understand is a silent repair; the format spec already asks readers
 * to ignore-never-reject, and a writer owes the same courtesy: <b>carry over what you do not understand,
 * rewrite only what you own.</b>
 *
 * <p>A family is the key up to its first dot ({@code sourceRoot.2} → {@code sourceRoot}; {@code theme} →
 * {@code theme}). Families this version owns are rewritten wholesale — which is what lets a list shrink:
 * a stale {@code sourceRoot.2} is NOT preserved, because the writer owns {@code sourceRoot} and wrote the
 * list it meant. Everything else is preserved byte-for-byte. The failure direction is safe: a family this
 * list forgets is carried over unchanged, never lost.
 */
public final class KnownKeys {

    private KnownKeys() {
    }

    /** What {@code SettingsShare.export} can write — the profile and the share file. */
    public static final Set<String> PROFILE_FAMILIES = Set.of(
            "share", "sourceRoot", "mavenRepo", "mavenRepoSearch", "eventProcessorFqn", "selectedEventProcessor",
            "graph", "focus", "report", "hiddenColumn", "assistant", "llmProvider", "llmModel", "llmBaseUrl",
            "runbook", "vocabulary", "environment", "analysis", "destination", "workspaceRoot");

    /** What {@code ConfigStore.save} can write — the own-settings file: the profile families plus the machine tier. */
    public static final Set<String> CONFIG_FAMILIES;

    static {
        Set<String> all = new java.util.HashSet<>(PROFILE_FAMILIES);
        all.addAll(Set.of("activeProjectPath", "apiKey", "awsProfile", "awsRegion", "eventFilterCollapsed", "graphmlFile",
                "lastRunVersion", "logFile", "memoryThresholdMb", "projectPanelCollapsed", "recentFile", "recentGraphml",
                "recentProject", "searchHistory", "theme", "mcp", "topologyOrientation", "topologyPanX", "topologyPanY",
                "topologySpacing", "topologySyncSource", "topologyTextSize", "topologyZoom", "westDivider", "westWidth",
                "windowH", "windowW", "windowX", "windowY"));
        CONFIG_FAMILIES = Set.copyOf(all);
    }

    public static String family(String key) {
        if (key == null) return "";
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    /** The entries of {@code previous} whose family is not in {@code known} — what a writer must carry over. */
    public static Map<String, String> unknown(Properties previous, Set<String> known) {
        Map<String, String> out = new LinkedHashMap<>();
        if (previous == null) return out;
        for (String k : new java.util.TreeSet<>(previous.stringPropertyNames())) {
            if (!known.contains(family(k))) out.put(k, previous.getProperty(k));
        }
        return out;
    }

    /** Copy every unknown-family entry of {@code previous} into {@code fresh} unless the writer already set it. */
    public static int preserve(Properties fresh, Properties previous, Set<String> known) {
        int n = 0;
        for (var e : unknown(previous, known).entrySet()) {
            if (fresh.getProperty(e.getKey()) == null) {
                fresh.setProperty(e.getKey(), e.getValue());
                n++;
            }
        }
        return n;
    }
}
