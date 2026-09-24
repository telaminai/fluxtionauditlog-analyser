package telamin.fluxtion.audit.analyser.analyser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.1 re-review R3: the "different build" conclusion must not reappear on any surface a person or an
 * assistant reads — Java string literals, the in-app help and the published docs.
 *
 * <p>Written because the conclusion was removed from three surfaces and then found alive on five more: two a
 * reviewer named, and one in the topology guide nobody had named. A per-surface test only guards the surfaces
 * somebody remembered. This one guards the repository.
 *
 * <p><b>Comments are exempt, by construction:</b> they are stripped before string literals are read, because the
 * history of why the phrase was removed is legitimately written down in them. <b>{@code release-notes.md} is
 * exempt</b> because it republishes the changelog, which quotes the removed messages in order to say they are gone.
 *
 * <p>Deliberately self-checking: if a scanner ever finds no literals or no documents it fails, so it cannot pass by
 * scanning nothing.
 */
class UserVisibleWordingGuardTest {

    static final Pattern CONCLUSION = Pattern.compile(
            "different build|version mismatch|version problem|probably from a different|different system or build",
            Pattern.CASE_INSENSITIVE);

    @Test
    void noJavaStringLiteralDrawsTheBuildConclusion() throws IOException {
        List<String> hits = new ArrayList<>();
        int[] literals = {0};
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = stripComments(Files.readString(f));
                Matcher m = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"").matcher(code);
                while (m.find()) {
                    literals[0]++;
                    if (CONCLUSION.matcher(m.group()).find()) hits.add(f + ": " + m.group());
                }
            }
        }
        assertTrue(literals[0] > 1000, "the scanner must actually read the product's strings: " + literals[0]);
        assertEquals(List.of(), hits, "a user-visible string concludes which build an artefact came from");
    }

    @Test
    void noHelpPageOrPublishedDocDrawsTheBuildConclusion() throws IOException {
        List<Path> docs = new ArrayList<>();
        docs.add(Path.of("src/main/resources/help/help.html"));
        try (Stream<Path> site = Files.walk(Path.of("docs/site"))) {
            site.filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equals("release-notes.md"))
                    .forEach(docs::add);
        }
        assertTrue(docs.size() > 10, "the guard must actually cover the docs site: " + docs.size());
        List<String> hits = new ArrayList<>();
        for (Path d : docs) {
            List<String> lines = Files.readAllLines(d);
            for (int i = 0; i < lines.size(); i++) {
                if (CONCLUSION.matcher(lines.get(i)).find()) hits.add(d + ":" + (i + 1) + ": " + lines.get(i).trim());
            }
        }
        assertEquals(List.of(), hits, "a help page or published doc tells the reader to draw the build conclusion");
    }

    @Test
    void theGuardDetectsTheConclusionItForbids() {
        // the wrong-result witness: the exact incident sentence must be caught, so a pattern that silently
        // stopped matching could not pass this suite
        assertTrue(CONCLUSION.matcher("the graphml is probably from a different build").find());
        assertTrue(CONCLUSION.matcher("Treat a mismatch as a version problem.").find());
        assertTrue(CONCLUSION.matcher("(different build?)").find());
        String kept = stripComments("int a = 1; // \"a different build\"\n/* \"version mismatch\" */ String s = \"ok\";");
        assertFalse(CONCLUSION.matcher(kept).find(), "comments are stripped: " + kept);
        assertTrue(kept.contains("\"ok\""), "strings survive stripping: " + kept);
    }

    /** Removes line and block comments while leaving string and character literals intact. */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c && src.charAt(j) != '\n') j += src.charAt(j) == '\\' ? 2 : 1;
                out.append(src, i, Math.min(n, j + 1));
                i = j + 1;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
