package telamin.fluxtion.audit.analyser.analyser.net;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder;
import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every published list of verbs must be <b>derived</b> from {@link VerbSchemas}, never written out.
 *
 * <p>This test exists because both of them were written out, and both went stale. {@code /manifest}
 * hardcoded six verbs and kept advertising six while seven more shipped — so the manifest contradicted
 * itself, its {@code verbs} field naming a third of what its {@code schemas} field described. The
 * copy-prompt handed to every external agent named five.
 *
 * <p>{@code VerbSchemasTest} and {@code McpToolsTest} already pin the schema set and the MCP tool set.
 * Nothing pinned the two places that tell a <em>foreign</em> agent what it may call, which is why those
 * were the two that rotted. A hardcoded list next to the thing it duplicates is a promise to drift.
 */
class ManifestVerbContractTest {

    /** Guards against a future edit reintroducing a literal list beside the derived one. */
    @Test
    void theRestManifestDoesNotHardcodeItsVerbList() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/net/ActionServer.java"));
        int at = src.indexOf("m.put(\"verbs\"");
        assertTrue(at >= 0, "the manifest no longer publishes a 'verbs' field — if that was deliberate, "
                + "update this test; if not, a foreign agent has lost its verb list");
        String stanza = src.substring(at, Math.min(src.length(), at + 400));
        assertTrue(stanza.contains("VerbSchemas.all()"),
                "the manifest's verb list must be derived from VerbSchemas, not written out — "
                        + "a literal list here went stale for seven verbs across three milestones");
        assertFalse(stanza.contains("List.of(\""),
                "found a literal verb list in the manifest stanza; derive it instead");
    }

    /**
     * The value-level half of the cross-transport contract. The MCP side ({@code McpToolsTest.
     * everyToolsInputSchemaIsExactlyItsVerbSchemaMinusTheLiftedDescription}) proves each tool wraps the
     * exact {@code VerbSchemas} schema for its verb; this proves REST publishes those same schemas
     * <b>verbatim</b> as its {@code schemas} field. Together they mean the two transports cannot advertise
     * a different schema for the same verb — both are the one source, not two copies that agree today.
     */
    @Test
    void theRestManifestPublishesTheVerbSchemasSchemasVerbatim() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/net/ActionServer.java"));
        int at = src.indexOf("m.put(\"schemas\"");
        assertTrue(at >= 0, "the manifest no longer publishes a 'schemas' field — if that was deliberate, "
                + "update this test; a foreign agent reads each verb's parameters from here");
        String stanza = src.substring(at, Math.min(src.length(), at + 200));
        assertTrue(stanza.contains("VerbSchemas.all()"),
                "the manifest's 'schemas' must BE VerbSchemas.all(), not a rebuilt or transformed copy — "
                        + "anything else forks the REST schema from the MCP one the moment either changes");
        assertFalse(stanza.contains("Map.of(") || stanza.contains("new LinkedHashMap"),
                "found the manifest assembling its own schema map; publish VerbSchemas.all() directly");
    }

    /**
     * The copy-prompt is the only verb list an agent working from a pasted brief ever sees, so a stale
     * one silently caps what that session believes it can do.
     */
    @Test
    void theCopyPromptAdvertisesEveryShippedVerb() {
        String manifest = PromptBuilder.restActionManifest("http://127.0.0.1:1234", "tok", 20);
        for (String verb : VerbSchemas.all().keySet()) {
            assertTrue(manifest.contains(verb),
                    "verb '" + verb + "' ships but the REST copy-prompt never mentions it — an agent "
                            + "given this brief cannot know it exists");
        }
    }

    /**
     * The MkDocs page a human configures their client from. Not a duplicate of {@code McpToolsTest},
     * which checks the tool set the app publishes; this checks the set the documentation claims, and the
     * two disagreed on {@code analyser_coverage} for a full release.
     */
    @Test
    void theAssistantGuideNamesEveryMcpTool() throws Exception {
        String doc = Files.readString(Path.of("docs/site/user-guide/assistant.md"));
        for (String verb : VerbSchemas.all().keySet()) {
            assertTrue(doc.contains("analyser_" + verb),
                    "tool 'analyser_" + verb + "' ships but docs/site/user-guide/assistant.md does not "
                            + "name it — document a verb in the same change that adds it");
        }
    }
}
