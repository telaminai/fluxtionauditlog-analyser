package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpBridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Onboarding trial arm C (2026-11-09): connected assistants asked to build a Fluxtion app built from stale Maven Central
 * artifacts, because nothing the Analyser said named the starter. The MCP server instructions gain
 * {@link NewProjectRoute#INSTRUCTIONS}, and only that; {@code context.project} gains {@code newProject} while no
 * project is open.
 *
 * <p>Witness: edit any other word of the instructions, or any schema — {@link #onlyTheInstructionsMoved} or
 * {@link #removingTheSectionGivesTheEarlierPinBack} fails.
 */
class NewProjectApiDeltaTest {

    /** The instructions' pin before this change (FirstMoveApiDeltaTest's "now"). Literal, never derived. */
    static final String INSTRUCTIONS_BEFORE = "6a241aab08422f281cd37c5d487238d96ef97c9ac09bfda0d59653eb0c527bb3";

    /** Remove only {@link NewProjectRoute#INSTRUCTIONS}, recursively over any surface shape, so delta chains compose. */
    @SuppressWarnings("unchecked")
    static <T> T withoutNewProject(T value) {
        if (value instanceof String text) return (T) text.replace(NewProjectRoute.INSTRUCTIONS, "");
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((key, entry) -> out.put(key, withoutNewProject(entry)));
            return (T) out;
        }
        if (value instanceof List<?> list) return (T) list.stream().map(NewProjectApiDeltaTest::withoutNewProject).toList();
        return value;
    }

    @Test
    @DisplayName("★ the instructions gain only the new-project section: removed, they hash to the earlier pin")
    void removingTheSectionGivesTheEarlierPinBack() throws Exception {
        assertEquals(INSTRUCTIONS_BEFORE, WorkModeKeepsTheApiTest.sha(withoutNewProject(McpBridge.INSTRUCTIONS)));
        assertEquals(WorkModeKeepsTheApiTest.PINNED.get("McpBridge.INSTRUCTIONS"),
                WorkModeKeepsTheApiTest.sha(McpBridge.INSTRUCTIONS), "the pin now");
    }

    @Test
    void onlyTheInstructionsMoved() throws Exception {
        for (var surface : WorkModeKeepsTheApiTest.surfaces().entrySet()) {
            if (surface.getKey().equals("McpBridge.INSTRUCTIONS")) continue;
            assertEquals(surface.getValue(), withoutNewProject(surface.getValue()), surface.getKey() + " carries no new-project section");
        }
    }

    @Test
    void theSectionNamesTheStarterTheTrapsAndTheSignIn() {
        String s = NewProjectRoute.INSTRUCTIONS;
        assertTrue(McpBridge.INSTRUCTIONS.contains(s));
        assertTrue(s.contains("https://fluxtion-playground.dev/start/scaffold?template=java-authoring"), "the starter");
        assertTrue(s.contains("asked to build or create an app or processor"), "when it applies");
        int download = s.indexOf("download"), read = s.indexOf("read its CLAUDE.md"), build = s.indexOf("then build");
        assertTrue(download >= 0 && download < read && read < build, "download, read CLAUDE.md, build — in that order");
        assertTrue(s.contains("Maven Central does not work"));
        assertTrue(s.contains("com.telamin.fluxtion 1.0.9 predates compiler sign-in"), "the stale release");
        assertTrue(s.contains("com.fluxtion 9.x is the retired earlier product"), "the legacy product");
        assertTrue(s.contains("Mongoose"), "what the starter deploys to");
        assertTrue(s.contains("new, blank or empty project") && s.contains("is the blank Java project"), "blank maps to the starter");
        assertTrue(s.contains("FLX-1022") && s.contains("FLX-1023"), "the signed-out stops");
        assertTrue(s.contains("fluxtion-maven-plugin:1.3.5:login prints a link and a code"), "the pinned login and what it prints");
        assertTrue(s.endsWith(NewProjectRoute.SIGN_IN_FACTS), "the sign-in facts close it");
        assertTrue(NewProjectRoute.SIGN_IN_FACTS.contains("-DskipFluxtion leaves no processor to run"));
        // facts in the third person: no orders to the client (benchmark 2026-11-09 set those aside as injections)
        for (String order : List.of("not a blocker", "relay", "to avoid it", "say so", "Never ", "do not "))
            assertFalse(s.contains(order), "no order: " + order);
        assertFalse(s.toLowerCase(java.util.Locale.ROOT).contains(" key"), "compiler sign-in, never a key");
        assertTrue(s.split("\\s+").length <= 115, "short: " + s.split("\\s+").length + " words");
    }

    @Test
    void theContextPointerIsCompactAndAddedOnlyWhereNoProjectIsOpen() throws Exception {
        String p = NewProjectRoute.CONTEXT_POINTER;
        assertTrue(p.contains(NewProjectRoute.STARTER_URL) && p.contains("CLAUDE.md") && p.contains("compiler sign-in"));
        assertTrue(p.contains("blank Java project"), "blank maps to the starter");
        assertTrue(p.split("\\s+").length <= 30, "a pointer, not a second copy: " + p.split("\\s+").length + " words");
        Map<String, Object> project = new LinkedHashMap<>(Map.of("active", false));
        NewProjectRoute.addTo(project);
        assertEquals(p, project.get("newProject"));
        // the frame's context assembly adds it in the no-project branch, right after that branch's note (frame tests
        // need a display, so the placement is read from the source, as AssistantVocabularyTest reads McpBridge)
        String frame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertEquals(1, frame.split("NewProjectRoute.addTo\\(proj\\)", -1).length - 1, "added in one place");
        assertTrue(frame.contains("proj.put(\"note\", \"your own settings — no project is open\");\n"
                + "                telamin.fluxtion.audit.analyser.analyser.llm.NewProjectRoute.addTo(proj);"),
                "in the no-project branch");
    }
}
