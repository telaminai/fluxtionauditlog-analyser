package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.design.SourceText;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.Frame;

/**
 * ★ Owner, 2026-10-18 (the owner's session ran Read, find and grep on the demo folder to "show the code", each one asked):
 * {@code source {…, text}} returns the lines as data through the verb itself, on a real analyser — for a file inside an
 * authorised source root only, the same check {@code source} already makes; refused outside the roots and through a link
 * that leads out; and without {@code text} the reply is byte-identical to the one before.
 *
 * <p>Witnesses (run 2026-10-18): drop {@code echo.put("text", …)} in {@code MainFrame.source} — the in-root row fails;
 * accept a readable candidate outside the roots in {@code DesignFiles.resolve} — the outside row fails; accept a link by
 * its own path rather than its real path and open it following links ({@code DesignFiles} checks both) — the link row
 * fails and the linked file's words leave; put a {@code text} key on every reply — the
 * byte-identical row fails.
 */
class SourceTextFrameTest {

    @Test
    @DisplayName("★ source {text}: the lines as data in-root; refused outside the roots and through a link out; no text, no change")
    void sourceReturnsTheLinesInsideTheRootsOnly(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path src = JavaSourceSpotlightFrameTest.source(tmp);           // tmp/src/com/acme/Node.java, 185 lines
        Path outside = Files.writeString(Files.createDirectories(tmp.resolve("elsewhere")).resolve("Secret.java"),
                "class Secret { String key = \"DEMO\"; }\n");
        Path link = Files.createSymbolicLink(src.resolveSibling("Linked.java"), outside);
        try (var f = new Frame(tmp)) {
            JavaSourceSpotlightFrameTest.show(f, tmp.resolve("src"));

            ActionResult plain = f.ex.render("source", Map.of("fqn", "com.acme.Node"));
            assertTrue(plain.ok(), plain.toMap().toString());
            ActionResult withText = f.ex.render("source", Map.of("fqn", "com.acme.Node", "text", Map.of("from", 1, "to", 3)));
            assertTrue(withText.ok(), withText.toMap().toString());

            // in-root: the lines, as data
            @SuppressWarnings("unchecked")
            Map<String, Object> text = (Map<String, Object>) withText.payload().get("text");
            assertNotNull(text, "the lines came back: " + withText.toMap());
            assertEquals("package com.acme;\npublic class Node {\n    public int value = 42;", text.get("content"));
            assertEquals(SourceText.DATA_NOTE, text.get("note"));

            // without text: byte-identical to the reply before — the same reply, and today's shape exactly
            Map<String, Object> stripped = new LinkedHashMap<>(withText.toMap());
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) stripped.get(withText.payloadKey()));
            payload.remove("text");
            stripped.put(withText.payloadKey(), payload);
            assertEquals(Json.write(plain.toMap()), Json.write(stripped), "text adds one key and changes nothing else");
            assertEquals(List.of("file", "mode", "line", "relationship"), List.copyOf(plain.payload().keySet()),
                    "a Java glance's reply, as it was");
            assertEquals(src.toRealPath().toString(), plain.payload().get("file"));

            // outside the roots: refused, nothing returned
            ActionResult out = f.ex.render("source", Map.of("file", outside.toString(), "text", Map.of("from", 1)));
            assertFalse(out.ok());
            assertTrue(String.valueOf(out.toMap().get("error")).contains("outside authorised roots"), out.toMap().toString());
            assertFalse(Json.write(out.toMap()).contains("DEMO"), "nothing of the file leaves");

            // a link inside the root that leads out: refused the same way
            ActionResult viaLink = f.ex.render("source", Map.of("file", link.toString(), "text", Map.of("from", 1)));
            assertFalse(viaLink.ok(), viaLink.toMap().toString());
            assertFalse(Json.write(viaLink.toMap()).contains("DEMO"), "nothing of the linked file leaves");
        }
    }
}
