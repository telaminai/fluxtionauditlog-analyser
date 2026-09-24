package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Published menu paths are checked headlessly against the same contract as the real Swing menu. */
class MenuDocumentationTest {
    private static final Pattern START = Pattern.compile(
            "\\b(Project|Sources|Audit log|Records)[\\s*`\"']*[▸→][\\s*`\"']*");
    private static final Pattern END = Pattern.compile("[▸→<`*\"',;.!?]|…");
    record MenuPath(int line, String menu, String item) { }

    static List<MenuPath> paths(String source) {
        // Preserve line breaks for diagnostics, including a path wrapped inside one Markdown span.
        String text = source.replace("&rarr;", "→").replace("&#8594;", "→")
                .replace("&#x2192;", "→").replace("&hellip;", "…").replace("&#8230;", "…")
                .replace("&nbsp;", " ").replace("&gt;", ">")
                .replaceAll("</?(?:b|i|em|strong|code)>", "`");
        var found = new ArrayList<MenuPath>();
        var start = START.matcher(text);
        while (start.find()) {
            int from = start.end();
            var end = END.matcher(text);
            int to = end.find(from) ? end.start() : text.length();
            String item = normal(text.substring(from, to));
            int line = 1 + (int)text.substring(0, start.start()).chars().filter(c -> c == '\n').count();
            found.add(new MenuPath(line, start.group(1), item));
        }
        return found;
    }

    private static String normal(String label) {
        return label.replace("…", "").replaceAll("\\s+", " ").trim();
    }
    static List<String> problems(String file, String text) {
        return paths(text).stream().filter(p -> MenuInventory.labels(p.menu()).stream()
                        .noneMatch(item -> normal(item).equals(p.item())))
                .map(p -> file + ":" + p.line() + ": no menu item " + p.menu() + " → " + p.item()).toList();
    }

    @Test void documentedPathsNameExistingItems() throws Exception {
        var files = new ArrayList<>(List.of(Path.of("README.md"), Path.of("src/main/resources/help/help.html")));
        try (var tree = Files.walk(Path.of("docs/site"))) {
            files.addAll(tree.filter(p -> p.toString().endsWith(".md")).sorted().toList());
        }
        var errors = new ArrayList<String>();
        int count = 0;
        for (Path file : files) {
            String text = Files.readString(file);
            count += paths(text).size();
            errors.addAll(problems(file.toString(), text));
        }
        assertTrue(count >= 60, "the extractor must inspect the published paths, not silently match nothing: " + count);
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    @Test void extractsHtmlMarkdownAndReportsLocations() {
        String text = """
                <b>Audit log → Open log from S3…</b>
                **Sources ▸ Open
                GraphML…**
                <i>Sources &rarr; Source roots&hellip;</i>
                Project ▸ "Settings…"
                **Project ▸ Run analysis ▸ *name***
                **Audit log ▸ Open from S3…**
                """;
        assertEquals(6, paths(text).size());
        assertEquals(new MenuPath(2, "Sources", "Open GraphML"), paths(text).get(1));
        assertEquals(List.of("example.md:7: no menu item Audit log → Open from S3"), problems("example.md", text));
        // Do not accept an existing label as a prefix of a different, nonexistent one.
        assertEquals(List.of("example.md:1: no menu item Project → Settings that do not exist"),
                problems("example.md", "**Project ▸ Settings that do not exist**"));
    }
}
