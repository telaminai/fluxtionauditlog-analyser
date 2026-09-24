package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.List;
import java.util.Map;

/** Test contract shared by the live-menu and published-path checks; never used to build the UI. */
final class MenuInventory {
    static final String SEPARATOR = "<separator>";
    static final List<String> RESOURCE_MENUS = List.of("Project", "Sources", "Audit log");
    static final Map<String, List<String>> MENUS = Map.of(
            "Project", List.of("Open project…", "Open recent project", "New project from template…", "New project…",
                    "Save project as…", "Close project", SEPARATOR, "Close log and topology", SEPARATOR,
                    "Run analysis", SEPARATOR, "Settings…", "Export settings…", "Import settings…", SEPARATOR, "Exit"),
            "Sources", List.of("Source roots…", "Event processor…", "Maven repos…", SEPARATOR, "Open GraphML…",
                    "Open recent GraphML", "Open design…", "Open producer diagnostics…", "Find GraphML in source roots…",
                    "Close graph"),
            "Audit log", List.of("Open log…", "Open recent audit log", "Open log from S3…", "Add series from CSV…",
                    "Close log", "Follow (tail)", SEPARATOR, "Export records (CSV)…", "Export records (YAML)…"),
            "Records", List.of("Flag / unflag selected  (F)", "Show flagged only", "Clear all flags",
                    "Write a finding for this record…", "Export finding to PDF…", SEPARATOR, "Copy selected as YAML",
                    "Diff selected two records", "Explain selected with LLM", SEPARATOR,
                    "Export records (CSV)…", "Export records (YAML)…"),
            "AI", List.of("Connect an AI client…", "Local MCP / REST enabled", SEPARATOR, "Fluxtion API key…",
                    SEPARATOR, "Runbooks…", "Domain glossary…", SEPARATOR, "Posture", "Place mode-selector record…",
                    "Clear mode-selector record", SEPARATOR, "Report exchange directory…", "Show exchange directory",
                    SEPARATOR, "Working with AI (docs)"),
            "Theme", List.of("Light", "Dark", "IntelliJ", "Darcula"),
            "Help", List.of("Start page", SEPARATOR, "User guide", "Release notes", "About"));

    // Independently transcribed from cfe4c925's File menu, before this relocation. Pin the old 26
    // rather than deriving this list from MENUS: removing an item from both UI and inventory must fail.
    static final List<String> BASE_FILE = List.of("Open log…", "Open log from S3…", "Add series from CSV…",
            "Open GraphML…", "Open design…", "Open producer diagnostics…", "Find GraphML in source roots…",
            "Close log", "Close graph", "Reset (close log + graph)", "Open recent audit log", "Open recent GraphML",
            "Open project…", "Open recent project", "New project from template…", "New project…", "Save project as…",
            "Close project", "Run analysis", "Follow (tail)", "Export records (CSV)…", "Export records (YAML)…",
            "Settings…", "Export settings…", "Import settings…", "Exit");
    static final List<String> SHORTCUTS = List.of("Source roots…", "Event processor…", "Maven repos…");
    static List<String> labels(String menu) {
        return MENUS.get(menu).stream().filter(s -> !SEPARATOR.equals(s)).toList();
    }
    private MenuInventory() { }
}
