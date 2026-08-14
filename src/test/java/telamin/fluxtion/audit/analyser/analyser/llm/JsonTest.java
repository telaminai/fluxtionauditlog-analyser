package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void writesObjectsArraysAndEscapes() {
        String json = Json.write(Map.of("a", 1.0));
        assertTrue(json.contains("\"a\""));
        String s = Json.write(Map.of("msg", "line1\nline2 \"q\""));
        assertTrue(s.contains("line1\\nline2 \\\"q\\\""), s);
    }

    @Test
    void roundTripsNestedStructures() {
        String src = "{\"choices\":[{\"message\":{\"content\":\"hello\"}}],\"n\":42,\"ok\":true,\"x\":null}";
        Object root = Json.parse(src);
        assertEquals("hello", Json.at(root, "choices", 0, "message", "content"));
        assertEquals(42.0, (Double) Json.at(root, "n"));
        assertEquals(Boolean.TRUE, Json.at(root, "ok"));
        assertNull(Json.at(root, "x"));
        assertNull(Json.at(root, "missing", "deep"), "missing hops return null, not throw");
    }

    @Test
    void parsesAnthropicShapeContentBlocks() {
        String src = "{\"content\":[{\"type\":\"text\",\"text\":\"part1\"},{\"type\":\"text\",\"text\":\"-part2\"}]}";
        Object content = Json.at(Json.parse(src), "content");
        assertInstanceOf(List.class, content);
        assertEquals(2, ((List<?>) content).size());
        assertEquals("part1", Json.at(content, 0, "text"));
    }

    @Test
    void handlesEscapedUnicodeAndSlashes() {
        Object v = Json.parse("{\"k\":\"a\\u0041b\\/c\"}");
        assertEquals("aAb/c", Json.at(v, "k"));
    }
}
