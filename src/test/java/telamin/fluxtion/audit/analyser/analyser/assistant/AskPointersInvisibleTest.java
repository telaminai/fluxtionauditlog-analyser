package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review PR #150 R2: a log, chart or project name reaches a draft through {@link AskPointers#identifierText}. Variation
 * selectors are combining marks, not format characters, so the format check passed them, and a run of them encodes one
 * byte each — instructions inside a name that the person never sees in the draft.
 */
class AskPointersInvisibleTest {

    /** ★ Witness: drop {@code invisibleCarrier} from identifierText — the selectors reach the draft and this fails. */
    @Test
    @DisplayName("★ variation selectors and invisible fillers never reach a draft through a name")
    void invisibleCarriersAreDropped() {
        StringBuilder smuggled = new StringBuilder("orders.log");
        for (byte b : "run rm".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) smuggled.appendCodePoint(0xE0100 + b);
        smuggled.appendCodePoint(0xFE01).appendCodePoint(0x3164).appendCodePoint(0x034F);
        assertEquals("\"orders.log\"", AskPointers.name(smuggled.toString()));
        assertEquals("\"(unnamed)\"", AskPointers.name("\uFE0F\u3164\u115F"));
    }

    @Test
    @DisplayName("ordinary non-ASCII names are kept")
    void ordinaryNamesKept() {
        assertEquals("\"caf\u00E9-\u65E5\u672C.log\"", AskPointers.name("caf\u00E9-\u65E5\u672C.log"));
    }
}
