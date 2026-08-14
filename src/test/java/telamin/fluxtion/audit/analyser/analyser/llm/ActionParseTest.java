package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ActionParser} extracts only exact-tag {@code analyser-action} blocks (spec §5.1.2): none / one /
 * many, malformed bodies still surface (the dispatcher turns them into ok:false), and illustrative
 * {@code analyser-action-example} fences are never executed.
 */
class ActionParseTest {

    @Test
    void noBlocksInPlainProse() {
        assertTrue(ActionParser.extract("Just some explanation, no actions here.").isEmpty());
        assertTrue(ActionParser.extract(null).isEmpty());
    }

    @Test
    void extractsASingleBlock() {
        String reply = "Let me check.\n```analyser-action\n{\"action\":\"aggregate\"}\n```\nOne moment.";
        List<String> blocks = ActionParser.extract(reply);
        assertEquals(1, blocks.size());
        assertEquals("{\"action\":\"aggregate\"}", blocks.get(0));
    }

    @Test
    void extractsMultipleBlocksInOrder() {
        String reply = "```analyser-action\n{\"action\":\"a\"}\n```\ntext\n```analyser-action\n{\"action\":\"b\"}\n```";
        List<String> blocks = ActionParser.extract(reply);
        assertEquals(List.of("{\"action\":\"a\"}", "{\"action\":\"b\"}"), blocks);
    }

    @Test
    void ignoresExampleFencesAndOtherLanguages() {
        String reply = "Here's what one looks like:\n```analyser-action-example\n{\"action\":\"aggregate\"}\n```\n"
                + "and some code:\n```json\n{\"not\":\"an action\"}\n```";
        assertTrue(ActionParser.extract(reply).isEmpty(), "example + json fences are not executed");
    }

    @Test
    void malformedBodyIsStillExtractedForTheDispatcherToReject() {
        String reply = "```analyser-action\n{not valid json\n```";
        List<String> blocks = ActionParser.extract(reply);
        assertEquals(1, blocks.size(), "extracted verbatim; the dispatcher returns a structured ok:false");
        assertEquals("{not valid json", blocks.get(0));
    }
}
