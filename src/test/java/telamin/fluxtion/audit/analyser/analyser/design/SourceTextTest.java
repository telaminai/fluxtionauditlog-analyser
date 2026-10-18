package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner, 2026-10-18: {@code source {…, text}} returns the requested lines as data, read-only, from a file {@code source}
 * resolved under the authorised roots — capped at {@value SourceText#MAX_LINES} lines and 32 KB with the cut stated, a
 * NUL-holding file refused as not text, a non-UTF-8 one refused in words. The frame-level checks (in-root text, refusal
 * outside the roots and through a link, the reply without text unchanged) are {@code SourceTextFrameTest}.
 *
 * <p>Witnesses (run 2026-10-18): drop the {@code MAX_LINES} test in {@code SourceText.slice} — the line cap fails; drop
 * the byte test — the 32 KB cap fails; drop the NUL check — the binary file's lines come back; drop the
 * {@code CharacterCodingException} wrap in {@code DesignWorkspace.source} — the decoder's "Input length = 1" comes back.
 */
class SourceTextTest {

    @TempDir Path root;
    DesignWorkspace workspace;

    @BeforeEach
    void setup() {
        SessionDriver driver = new SessionDriver(effect -> { throw new AssertionError("no effects"); });
        DesignFiles files = new DesignFiles(List.of(root.toString()), root);
        workspace = new DesignWorkspace(() -> files, () -> driver.processor().designSession, driver::submit);
    }

    static String numbered(int lines) {
        return IntStream.rangeClosed(1, lines).mapToObj(i -> "line " + i).collect(Collectors.joining("\n")) + "\n";
    }

    static SourceText.Request request(Map<String, Object> text) {
        var parsed = SourceText.parse(text);
        assertTrue(parsed.ok(), parsed.error());
        return parsed.request();
    }

    @Test
    @DisplayName("a range and a window come back exactly, as data; the window defaults to the line source chose")
    void rangesAndWindows() throws Exception {
        Path java = Files.writeString(Files.createDirectories(root.resolve("a")).resolve("Node.java"), "package a;\nclass Node {\n  void onEvent() {\n  }\n}\n");
        var view = workspace.source(Map.of("file", java.toString()), true);
        var range = SourceText.slice(view.text(), request(Map.of("from", 2, "to", 3)), view.line());
        assertEquals("class Node {\n  void onEvent() {", range.echo().get("content"));
        assertEquals(2, range.echo().get("from"));
        assertEquals(3, range.echo().get("to"));
        assertEquals(5, range.echo().get("fileLines"));
        assertEquals(SourceText.DATA_NOTE, range.echo().get("note"), "said to be data, never instructions");
        assertNull(range.echo().get("truncated"), "nothing was cut");

        var method = workspace.source(Map.of("fqn", "a.Node", "method", "onEvent"), true);
        var window = SourceText.slice(method.text(), request(Map.of("context", 1)), method.line());
        assertEquals("class Node {\n  void onEvent() {\n  }", window.echo().get("content"), "around the method's line");
        var whole = SourceText.slice(view.text(), request(Map.of("from", 1)), view.line());
        assertEquals(5, whole.echo().get("to"), "from alone: to the end, within the cap");
        assertEquals("text 'from' 9 is past the end: the file has 5 lines",
                SourceText.slice(view.text(), request(Map.of("from", 9)), 1).error());
    }

    @Test
    @DisplayName("★ the cap: at most 200 lines and 32 KB per call, the cut stated with where to continue")
    void theCapIsEnforcedAndStated() {
        String text = numbered(500);
        var lines = SourceText.slice(text, request(Map.of("from", 1, "to", 500)), 1);
        assertEquals(1, lines.echo().get("from"));
        assertEquals(200, lines.echo().get("to"), "200 lines at most");
        assertEquals(200, String.valueOf(lines.echo().get("content")).split("\n").length);
        assertTrue(String.valueOf(lines.echo().get("truncated")).contains("cut at 200 lines"), lines.echo().toString());
        assertTrue(String.valueOf(lines.echo().get("truncated")).contains("continue with text: {from: 201}"),
                lines.echo().toString());

        String wide = IntStream.rangeClosed(1, 100).mapToObj(i -> "x".repeat(1000)).collect(Collectors.joining("\n"));
        var bytes = SourceText.slice(wide, request(Map.of("from", 1, "to", 100)), 1);
        assertTrue(String.valueOf(bytes.echo().get("content")).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= SourceText.MAX_BYTES, "32 KB at most");
        assertEquals(32, bytes.echo().get("to"), "32 whole lines of 1000 bytes and their newlines fit");
        assertTrue(String.valueOf(bytes.echo().get("truncated")).contains("cut at 32 KB"), bytes.echo().toString());

        String oneLine = "é".repeat(40_000);                       // 80,000 bytes on one line
        var cut = SourceText.slice(oneLine, request(Map.of("from", 1)), 1);
        String content = String.valueOf(cut.echo().get("content"));
        assertTrue(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= SourceText.MAX_BYTES);
        assertTrue(content.chars().allMatch(c -> c == 'é'), "cut on a character, never inside one");
        assertTrue(String.valueOf(cut.echo().get("truncated")).contains("line 1 is longer and was cut"));
    }

    @Test
    @DisplayName("★ a binary file is refused as not text; a file that is not UTF-8 is refused in words, not the decoder's")
    void binaryAndEncoding() throws Exception {
        Path binary = Files.write(root.resolve("Blob.java"), new byte[]{'c', 'l', 0, 0, 1, 2, 'x', '\n'});
        var view = workspace.source(Map.of("file", binary.toString()), true);
        var refused = SourceText.slice(view.text(), request(Map.of("from", 1)), 1);
        assertFalse(refused.ok());
        assertTrue(refused.error().contains("not text"), refused.error());

        Path latin1 = Files.write(root.resolve("Latin.java"), "class Café {}\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        String why = assertThrows(IOException.class, () -> workspace.source(Map.of("file", latin1.toString()), true))
                .getMessage();
        assertTrue(why.contains("not UTF-8 text"), why);
        assertThrows(java.nio.charset.CharacterCodingException.class,
                () -> workspace.source(Map.of("file", latin1.toString())), "without text the refusal is as before");
    }

    @Test
    @DisplayName("the text parameter is checked before anything is read")
    void malformedRequestsAreRefused() {
        assertFalse(SourceText.parse("1-3").ok());
        assertTrue(SourceText.parse(Map.of("from", 1, "around", 3)).error().contains("not both"));
        assertTrue(SourceText.parse(Map.of("to", 3)).error().contains("needs 'from'"));
        assertTrue(SourceText.parse(Map.of("from", 0)).error().contains("1 or more"));
        assertTrue(SourceText.parse(Map.of("from", 5, "to", 2)).error().contains("before"));
        assertTrue(SourceText.parse(Map.of("lines", 5)).error().contains("not 'lines'"));
        assertTrue(SourceText.parse(Map.of("context", -1)).error().contains("0 or more"));
        assertEquals(SourceText.DEFAULT_CONTEXT, SourceText.parse(Map.of()).request().context(), "an empty object: a window");
    }
}
