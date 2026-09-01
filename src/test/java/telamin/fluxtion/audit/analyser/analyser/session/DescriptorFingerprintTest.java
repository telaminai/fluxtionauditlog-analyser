package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M45.6a — the downstream canary, aimed at the artefact that can actually move.
 *
 * <h2>Why this replaces the canary that was offered</h2>
 * The canary promised upstream was {@code SessionGraphShapeTest} re-pinned after a builder bump. That
 * test was run against released 1.0.65 and <b>passed unchanged</b>, and the reason is structural rather
 * than lucky: it asserts graph SHAPE — which ids exist, which edges run which way — and at the product
 * default of {@code fluxtion.graphml.metadata=OFF} the emitted GraphML carries no {@code fluxtion.*}
 * vocabulary at all. Two keys before the bump, two after. The only things that moved at OFF were
 * {@code edgedefault} and the document ORDER of the nodes, and a shape test correctly reads neither.
 *
 * <p><b>So the canary was insensitive by construction at the mode everyone actually builds in.</b> It
 * could only have fired had this repository regenerated in PARALLEL, which is the very default the two
 * trackers were deadlocked over.
 *
 * <h2>What this watches instead</h2>
 * The descriptor fingerprint, because it is now the <b>only</b> artefact that proves the wire envelope
 * crossed the gateway. GraphML and client-side diagnostics appear identically in a fallback run — a
 * fallback build still emits its keys and still raises FLX-1008 — so neither is evidence. The digest is
 * computed client-side, travels as envelope metadata, and is stamped server-side into
 * {@code DescriptorSupport.Meta}. The field it rides on is {@code transient}, so it cannot travel in the
 * payload under any serialisation format, and the generating server holds no code that could recompute
 * it. A null here means the envelope did not cross.
 *
 * <p><b>Nothing pinned this before.</b> The 1.0.65 regeneration added a fingerprint to the committed
 * processor where there had been {@code null}, and all 1272 tests stayed green. A silent reversion —
 * a server rollback, a gateway that stops preserving the content type — would be equally invisible.
 */
class DescriptorFingerprintTest {

    private static final Path PROCESSOR = Path.of(
            "src/main/java/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.java");
    private static final Path PARALLEL = Path.of(
            "src/test/resources/topology/vocabulary/session-processor-parallel.graphml");
    private static final Path AGGREGATED = Path.of(
            "src/test/resources/topology/vocabulary/session-processor-aggregated.graphml");

    /** The third constructor argument of {@code DescriptorSupport.Meta}, across the generator's wrapping. */
    private static String descriptorFingerprint() throws IOException {
        String src = Files.readString(PROCESSOR);
        Matcher m = Pattern.compile(
                "new DescriptorSupport\\.Meta\\(\\s*([^,]+),\\s*([^,]+),\\s*([^,]+),", Pattern.DOTALL)
                .matcher(src);
        assertTrue(m.find(), "the generated processor must declare a DescriptorSupport.Meta");
        String third = m.group(3).trim();
        return "null".equals(third) ? null : third.replace("\"", "");
    }

    private static String graphFingerprint(Path graphml) throws IOException {
        Matcher m = Pattern.compile("fluxtion\\.sourceFingerprint\">([a-f0-9]+)<")
                .matcher(Files.readString(graphml));
        return m.find() ? m.group(1) : null;
    }

    @Test
    @DisplayName("the committed processor carries a descriptor fingerprint — the envelope crossed")
    void theEnvelopeReachedTheGeneratingServer() throws IOException {
        String stamped = descriptorFingerprint();
        assertNotNull(stamped,
                "a null descriptor fingerprint means the remote build fell back to a plain call and the "
                        + "wire envelope did not cross. Before builder 1.0.65 was deployed this WAS null "
                        + "and nothing here noticed; that is the reversion this test exists to catch.");
        assertEquals(64, stamped.length(), "a model digest is a 64-char hex sha-256: " + stamped);
        assertTrue(stamped.matches("[a-f0-9]{64}"), stamped);
    }

    @Test
    @DisplayName("the server's stamp equals the digest the CLIENT computed for the same model")
    void clientAndServerAgreeOnTheModel() throws IOException {
        String stamped = descriptorFingerprint();
        String declared = graphFingerprint(PARALLEL);
        assertNotNull(declared, "the PARALLEL fixture declares fluxtion.sourceFingerprint");

        // The GraphML value is computed client-side; the descriptor value is stamped server-side out of
        // envelope metadata. Equality says the digest that crossed the wire describes the model that was
        // actually sent — the two could only diverge if something rewrote it in transit.
        assertEquals(declared, stamped,
                "client-computed GraphML digest and server-stamped descriptor digest must describe the "
                        + "same model");
    }

    @Test
    @DisplayName("the digest is model-scoped, so it does not move with the emission mode")
    void theDigestDoesNotFollowTheFile() throws IOException {
        assertEquals(graphFingerprint(PARALLEL), graphFingerprint(AGGREGATED),
                "PARALLEL and AGGREGATED are two renderings of ONE model. A digest that moved between "
                        + "them would report a difference that does not exist — which is the class of "
                        + "defect measured in this repo when the emitter's byte order was unstable at "
                        + "builder 1.0.64.");
    }
}
