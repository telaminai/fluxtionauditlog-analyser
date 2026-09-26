package telamin.fluxtion.audit.analyser.analyser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.1: the "different build" conclusion must not reappear on any surface a person or an assistant reads.
 *
 * <p>Written because the conclusion was removed from three surfaces and then found alive on five more. A per-surface
 * test only guards the surfaces somebody remembered; this one guards the repository.
 *
 * <p><b>What it covers</b> (round 3, N3 — each coverage has a planted witness in
 * {@link #theGuardDetectsTheConclusionItForbids}):
 * <ul>
 *   <li>every Java string literal under {@code src/main/java}, <b>including text blocks</b>, and <b>adjacent literals
 *       joined by {@code +} read as one string</b> — the original incident sentence was itself split across two;</li>
 *   <li>the in-app help, the in-app assistant's own instructions ({@code llm/system-prompt.md}), the skills served
 *       to assistants ({@code docs/skills}) and every page of the docs site;</li>
 *   <li>the <b>{@code [Unreleased]}</b> section of {@code CHANGELOG.md}, which ships in the jar as the in-app release
 *       notes (O-a).</li>
 * </ul>
 *
 * <p><b>It matches normalised text</b> (round 4, Q6). A document is read a paragraph at a time with its lines joined,
 * a text block with its lines joined, and in both Markdown emphasis, HTML tags and entities are removed and whitespace
 * — including the non-breaking space — collapses to one space. The docs are hard-wrapped and use Markdown and HTML
 * freely, and the help page's original sentence was itself inside an {@code <i>} tag, so each of those shapes passed
 * the guard until then.
 *
 * <p><b>What it does not cover, stated rather than implied:</b>
 * <ul>
 *   <li><b>synonyms</b> — it matches a phrase list, and any phrase list can be written around;</li>
 *   <li>a phrase split across two <b>paragraphs</b>, or across a Markdown list item's continuation that is not
 *       indented as part of it;</li>
 *   <li>strings assembled other than by literals directly joined with {@code +} — a variable, {@code String.format},
 *       a {@code StringBuilder}, or a comment between the two literals;</li>
 *   <li><b>released</b> {@code CHANGELOG.md} sections: dated history is left as it was written, and two entries there
 *       (1.8.0 and 1.1.0) still quote the old conclusion as the product said it then;</li>
 *   <li>comments, which are stripped first, because the history of why the phrase was removed is written in them.</li>
 * </ul>
 *
 * <p>Deliberately self-checking: if a scanner finds no strings or no documents it fails, so it cannot pass by
 * scanning nothing.
 */
class UserVisibleWordingGuardTest {

    static final Pattern CONCLUSION = Pattern.compile(
            "different build|version mismatch|version problem|probably from a different|different system or build",
            Pattern.CASE_INSENSITIVE);

    @Test
    void noJavaStringLiteralDrawsTheBuildConclusion() throws IOException {
        List<String> hits = new ArrayList<>();
        int strings = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String s : javaStrings(Files.readString(f))) {
                    strings++;
                    if (CONCLUSION.matcher(normalise(s)).find()) hits.add(f + ": " + s);
                }
            }
        }
        assertTrue(strings > 1000, "the scanner must actually read the product's strings: " + strings);
        assertEquals(List.of(), hits, "a user-visible string concludes which build an artefact came from");
    }

    @Test
    void noHelpPageOrPublishedDocDrawsTheBuildConclusion() throws IOException {
        Map<String, List<String>> docs = documents();
        assertTrue(docs.size() > 15, "the guard must actually cover the documents: " + docs.keySet());
        assertTrue(docs.containsKey("src/main/resources/llm/system-prompt.md"), "the assistant's own instructions");
        assertTrue(docs.keySet().stream().anyMatch(k -> k.startsWith("docs/skills/")), "the served skills");
        assertTrue(docs.containsKey("CHANGELOG.md [Unreleased]"), "the unreleased release notes");
        List<String> hits = new ArrayList<>();
        docs.forEach((name, lines) -> {
            for (String paragraph : paragraphs(lines)) {
                if (CONCLUSION.matcher(paragraph).find()) hits.add(name + ": " + paragraph);
            }
        });
        assertEquals(List.of(), hits, "a document a person or an assistant reads draws the build conclusion");
    }

    @Test
    void theGuardDetectsTheConclusionItForbids() {
        // the wrong-result witnesses: each coverage above must catch a planted conclusion, so a scanner that silently
        // stopped reading a surface could not pass this suite
        assertTrue(CONCLUSION.matcher("the graphml is probably from a different build").find());
        assertTrue(CONCLUSION.matcher("Treat a mismatch as a version problem.").find());

        String textBlock = "class X { String s = \"\"\"\n        the graphml is probably from a different build\n        \"\"\"; }";
        assertTrue(anyMatch(javaStrings(textBlock)), "a text block is read as a string: " + javaStrings(textBlock));

        String split = "class X { String s = \"the graphml is from a different \"\n        + \"build, which makes it suspect\"; }";
        assertFalse(CONCLUSION.matcher("the graphml is from a different ").find(), "neither half matches alone");
        assertFalse(CONCLUSION.matcher("build, which makes it suspect").find(), "neither half matches alone");
        assertTrue(anyMatch(javaStrings(split)), "adjacent literals are read as one string: " + javaStrings(split));

        String commented = "int a = 1; // \"a different build\"\n/* \"version mismatch\" */ String s = \"ok\";";
        assertFalse(anyMatch(javaStrings(commented)), "comments are not strings: " + javaStrings(commented));
        assertEquals(List.of("ok"), javaStrings(commented));

        String twoArguments = "f(\"a different \", \"build\");";
        assertFalse(anyMatch(javaStrings(twoArguments)), "literals NOT joined by + stay separate");

        List<String> prompt = List.of("You are the analyser's assistant.",
                "If the graph does not match, the graphml is probably from a different build.");
        assertTrue(prompt.stream().anyMatch(l -> CONCLUSION.matcher(l).find()),
                "a planted line in the assistant prompt would be caught by the document scan");

        String changelog = "# Changelog\n\n## [Unreleased]\n\n- Treat a mismatch as a version problem.\n\n"
                + "## [1.1.0] - 2026-08-01\n\n- treat that as a version mismatch\n";
        assertEquals(List.of("", "- Treat a mismatch as a version problem.", ""), unreleased(changelog),
                "only the unreleased section is read");
        assertTrue(unreleased(changelog).stream().anyMatch(l -> CONCLUSION.matcher(l).find()));
    }

    private static boolean anyMatch(List<String> strings) {
        return strings.stream().anyMatch(s -> CONCLUSION.matcher(normalise(s)).find());
    }

    private static boolean docMatches(String text) {
        return paragraphs(text.lines().toList()).stream().anyMatch(p -> CONCLUSION.matcher(p).find());
    }

    @Test
    void theGuardMatchesTheSixShapesRoundFourFound() {
        // round 4, Q6: each shape passed the guard until text was normalised; each must now be caught
        assertTrue(docMatches("Intro.\n\nThe graph is from a different\nbuild than the log.\n"), "wrapped across a line break");
        assertTrue(docMatches("The graph is from a *different* build."), "Markdown emphasis");
        assertTrue(docMatches("The graph is from a <i>different</i> build."), "an HTML tag");
        assertTrue(docMatches("The graph is from a different  build."), "two spaces");
        assertTrue(docMatches("The graph is from a different\u00a0build."), "a non-breaking space");
        assertTrue(docMatches("The graph is from a different&nbsp;build."), "an HTML non-breaking-space entity");
        String textBlock = "class X { String s = \"\"\"\n        the graph is from a different\n        build, which makes it suspect\n        \"\"\"; }";
        assertTrue(anyMatch(javaStrings(textBlock)), "a text block wrapping the phrase: " + javaStrings(textBlock));
        // and the limit stated above is real: two paragraphs are not joined
        assertFalse(docMatches("The graph is from a different\n\nbuild than the log."), "paragraphs stay separate");
    }

    /** Collapse formatting so the phrase is found however the text is laid out (round 4, Q6). */
    static String normalise(String text) {
        String s = text.replace('\u00a0', ' ')
                .replaceAll("&(nbsp|#160|#x[aA]0);", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[*_`~]+", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** A document's paragraphs — blank-line separated — each joined into one line and normalised. */
    static List<String> paragraphs(List<String> lines) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.isBlank()) {
                if (current.length() > 0) out.add(normalise(current.toString()));
                current.setLength(0);
            } else {
                current.append(line).append(' ');
            }
        }
        if (current.length() > 0) out.add(normalise(current.toString()));
        return out;
    }

    /** Every document a person or an assistant reads, as lines, keyed by where it came from. */
    static Map<String, List<String>> documents() throws IOException {
        Map<String, List<String>> docs = new LinkedHashMap<>();
        for (String f : List.of("src/main/resources/help/help.html", "src/main/resources/llm/system-prompt.md")) {
            docs.put(f, Files.readAllLines(Path.of(f)));
        }
        for (String root : List.of("docs/site", "docs/skills")) {
            try (Stream<Path> tree = Files.walk(Path.of(root))) {
                for (Path p : tree.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".json")).sorted().toList()) {
                    docs.put(p.toString(), Files.readAllLines(p));
                }
            }
        }
        docs.put("CHANGELOG.md [Unreleased]", unreleased(Files.readString(Path.of("CHANGELOG.md"))));
        return docs;
    }

    /** The lines of the {@code [Unreleased]} section, up to the first released heading. */
    static List<String> unreleased(String changelog) {
        List<String> lines = changelog.lines().toList();
        List<String> out = new ArrayList<>();
        boolean in = false;
        for (String line : lines) {
            if (line.startsWith("## [")) {
                if (in) break;
                in = line.startsWith("## [Unreleased]");
                continue;
            }
            if (in) out.add(line);
        }
        return out;
    }

    private record Token(String text, int start, int end) { }

    /**
     * The string expressions in a Java source: ordinary literals and text blocks, with comments skipped, and adjacent
     * literals separated only by whitespace and {@code +} joined into one string.
     */
    static List<String> javaStrings(String src) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (src.startsWith("\"\"\"", i)) {
                int j = i + 3;
                StringBuilder body = new StringBuilder();
                while (j < n && !(src.startsWith("\"\"\"", j) && src.charAt(j - 1) != '\\')) body.append(src.charAt(j++));
                int end = Math.min(n, j + 3);
                tokens.add(new Token(body.toString(), i, end));
                i = end;
            } else if (c == '"') {
                int j = i + 1;
                StringBuilder body = new StringBuilder();
                while (j < n && src.charAt(j) != '"' && src.charAt(j) != '\n') {
                    if (src.charAt(j) == '\\' && j + 1 < n) { body.append(src.charAt(j + 1)); j += 2; }
                    else body.append(src.charAt(j++));
                }
                int end = Math.min(n, j + 1);
                tokens.add(new Token(body.toString(), i, end));
                i = end;
            } else if (c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != '\'' && src.charAt(j) != '\n') j += src.charAt(j) == '\\' ? 2 : 1;
                i = Math.min(n, j + 1);
            } else {
                i++;
            }
        }
        List<String> out = new ArrayList<>();
        StringBuilder current = null;
        int currentEnd = -1;
        for (Token t : tokens) {
            String gap = current == null ? "" : src.substring(currentEnd, t.start());
            if (current != null && gap.contains("+") && gap.chars().allMatch(ch -> ch == '+' || Character.isWhitespace(ch))) {
                current.append(t.text());
            } else {
                if (current != null) out.add(current.toString());
                current = new StringBuilder(t.text());
            }
            currentEnd = t.end();
        }
        if (current != null) out.add(current.toString());
        return out;
    }
}
