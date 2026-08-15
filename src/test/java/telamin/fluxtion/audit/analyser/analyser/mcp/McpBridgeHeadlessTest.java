package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enforces the headless constraint (spec-assistant-actions-mcp §9) mechanically rather than by review: an
 * MCP client launches the bridge in an arbitrary environment — no display, no window server — where
 * initializing AWT can fail oddly, especially on macOS.
 *
 * <p>Reading the compiled bytecode is the honest check. A test that merely <em>ran</em> the bridge would
 * pass even if a Swing reference existed on a branch it happened not to take; the constant pool shows the
 * dependency whether or not it is reached.
 */
class McpBridgeHeadlessTest {

    private String bytecodeOf(Class<?> type) throws IOException {
        String resource = type.getSimpleName() + ".class";
        URL url = type.getResource(resource);
        assertNotNull(url, "compiled class not found: " + resource);
        try (InputStream in = url.openStream()) {
            // latin-1 maps every byte to a char, so constant-pool strings survive intact
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private void assertNoUiDependency(Class<?> type) throws IOException {
        String bytes = bytecodeOf(type);
        assertFalse(bytes.contains("javax/swing"), type.getSimpleName() + " must not reference Swing");
        assertFalse(bytes.contains("java/awt"), type.getSimpleName() + " must not reference AWT");
        assertFalse(bytes.contains("com/formdev/flatlaf"), type.getSimpleName() + " must not reference FlatLaf");
    }

    @Test
    void theBridgeTouchesNoSwingOrAwtClass() throws IOException {
        assertNoUiDependency(McpBridge.class);
    }

    @Test
    void theToolAdapterTouchesNoSwingOrAwtClass() throws IOException {
        assertNoUiDependency(McpTools.class);
    }

    @Test
    void mainSetsTheHeadlessPropertyBeforeAnythingElse() {
        // guards the property name itself; Main routes --mcp here before any UI bootstrap
        String previous = System.getProperty("java.awt.headless");
        java.io.InputStream stdin = System.in;
        try {
            System.clearProperty("java.awt.headless");
            System.setIn(new java.io.ByteArrayInputStream(new byte[0]));   // immediate EOF → main returns
            McpBridge.main(new String[]{"--mcp"});
            assertEquals("true", System.getProperty("java.awt.headless"));
        } finally {
            System.setIn(stdin);
            if (previous == null) System.clearProperty("java.awt.headless");
            else System.setProperty("java.awt.headless", previous);
        }
    }
}
