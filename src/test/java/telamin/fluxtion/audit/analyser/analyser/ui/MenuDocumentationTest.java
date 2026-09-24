package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import javax.tools.SimpleJavaFileObject;
import javax.tools.JavaFileObject;
import java.net.URI;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import static org.junit.jupiter.api.Assertions.*;

/** Published menu paths are checked headlessly against the same contract as the real Swing menu. */
class MenuDocumentationTest {
    // Settings ▸ ... denotes a dialog tab, not a top-level menu. Working with AI is a docs
    // navigation section, not the AI menu. Only the first item of a menu path is checked;
    // descendants can be dynamic or controls in the dialog opened by that item.
    private static final Pattern START = Pattern.compile(
            "\\b(?<!Working with )(Project|Sources|Audit log|Records|AI|Theme|Help)[\\s*`\"']*[▸→][\\s*`\"']*");
    private static final Pattern END = Pattern.compile("[▸→<`*\"',;.!?]|…|\\s+(?:sets it|or open)\\b");
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
            var nextPath = START.matcher(text);
            if (nextPath.find(from)) to = Math.min(to, nextPath.start());
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

    /** Parse, do not grep Java comments. Constant concatenations and text blocks are supported.
     * Computed strings and menu paths assembled across method calls are intentionally not evaluated. */
    static List<String> javaProblems(String file, String source) throws Exception {
        var input = new SimpleJavaFileObject(URI.create("string:///MenuSource.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignored) { return source; }
        };
        var errors = new ArrayList<String>();
        try (var manager = ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null)) {
            var diagnostics = new javax.tools.DiagnosticCollector<JavaFileObject>();
            var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, manager, diagnostics,
                    List.of("-proc:none"), null, List.of(input));
            var trees = Trees.instance(task);
            for (var unit : task.parse()) new TreeScanner<Void, Void>() {
                String constant(Tree tree) {
                    if (tree instanceof LiteralTree l && l.getValue() instanceof String v) return v;
                    if (tree instanceof ParenthesizedTree parens) return constant(parens.getExpression());
                    if (tree instanceof BinaryTree b && b.getKind() == Tree.Kind.PLUS) {
                        String a = constant(b.getLeftOperand()), c = constant(b.getRightOperand());
                        if (a != null && c != null) return a + c;
                    }
                    return null;
                }
                void check(Tree tree, String value) {
                    int start = (int) trees.getSourcePositions().getStartPosition(unit, tree);
                    int end = (int) trees.getSourcePositions().getEndPosition(unit, tree);
                    String spelling = source.substring(start, end);
                    var at = START.matcher(spelling);
                    for (MenuPath path : paths(value)) {
                        int offset = at.find() ? start + at.start() : start;
                        if (MenuInventory.labels(path.menu()).stream().anyMatch(x -> normal(x).equals(path.item()))) continue;
                        // Locate the menu token in the original spelling, so escaped newlines in a
                        // Java string cannot invent physical source lines in the failure message.
                        long line = unit.getLineMap().getLineNumber(offset);
                        errors.add(file + ":" + line + ": no menu item " + path.menu() + " → " + path.item());
                    }
                }
                @Override public Void visitBinary(BinaryTree tree, Void unused) {
                    String text = constant(tree);
                    if (text != null) { check(tree, text); return null; }
                    return super.visitBinary(tree, unused);
                }
                @Override public Void visitLiteral(LiteralTree tree, Void unused) {
                    if (tree.getValue() instanceof String text) check(tree, text);
                    return null;
                }
            }.scan(unit, null);
            assertTrue(diagnostics.getDiagnostics().stream().noneMatch(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR),
                    () -> file + ": Java parse errors: " + diagnostics.getDiagnostics());
        }
        return errors;
    }

    @Test void documentedPathsNameExistingItems() throws Exception {
        var files = new ArrayList<>(List.of(Path.of("README.md"), Path.of("src/main/resources/help/help.html"),
                Path.of("src/main/resources/llm/system-prompt.md")));
        for (String directory : List.of("docs/site", "docs/skills")) {
            try (var tree = Files.walk(Path.of(directory))) {
                files.addAll(tree.filter(p -> p.toString().endsWith(".md")).sorted().toList());
            }
        }
        var errors = new ArrayList<String>();
        int count = 0;
        for (Path file : files) {
            String text = Files.readString(file);
            count += paths(text).size();
            errors.addAll(problems(file.toString(), text));
        }
        try (var tree = Files.walk(Path.of("src/main/java"))) {
            for (Path file : tree.filter(p -> p.toString().endsWith(".java")).sorted().toList())
                errors.addAll(javaProblems(file.toString(), Files.readString(file)));
        }
        assertTrue(count >= 80, "the extractor must inspect the published paths, not silently match nothing: " + count);
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    @Test void extractsHtmlMarkdownAndReportsLocations() throws Exception {
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
        assertEquals(List.of(), problems("tabs.md", "Settings ▸ Assistant; Working with AI ▸ Runbooks"));
        assertEquals(List.of("ai.md:2: no menu item AI → Nonexistent item"),
                problems("ai.md", "Heading\n**AI ▸ Nonexistent item…**"));
        assertEquals(List.of("Demo.java:3: no menu item Audit log → Open"), javaProblems("Demo.java", """
                class Demo {
                  // AI ▸ Not an item: comments are not menu instructions in a string.
                  String text = "Audit log ▸ Open";
                  String good = "Records ▸ Show " + "flagged only";
                }
                """));
        String block = "class Demo { String text = \"\"\"\nHelp ▸ About\nAI ▸ Missing…\n\"\"\"; }";
        assertEquals(List.of("Block.java:3: no menu item AI → Missing"), javaProblems("Block.java", block));
        // Do not accept an existing label as a prefix of a different, nonexistent one.
        assertEquals(List.of("example.md:1: no menu item Project → Settings that do not exist"),
                problems("example.md", "**Project ▸ Settings that do not exist**"));
    }
}
