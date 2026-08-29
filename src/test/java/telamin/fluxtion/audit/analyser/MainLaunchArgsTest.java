package telamin.fluxtion.audit.analyser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M19.9 — the launch decisions the end-to-end bench should not be the first place to catch. */
class MainLaunchArgsTest {

    @Test
    void restIsStrippedAndRememberedWhereverItAppears() {
        Main.DesktopArgs parsed = Main.parseDesktopArgs(new String[]{"audit.yaml", "--rest"});
        assertTrue(parsed.rest());
        assertEquals(List.of("audit.yaml"), parsed.remaining());
    }

    @Test
    void anUnknownFlagRemainsForTheExistingLoudRejection() {
        Main.DesktopArgs parsed = Main.parseDesktopArgs(new String[]{"--rest", "--typo"});
        assertTrue(parsed.rest());
        assertEquals(List.of("--typo"), parsed.remaining());
        assertTrue(Main.looksLikeFlag(parsed.remaining().get(0)));
    }

    @Test
    void aLogPathFallsThroughUnchanged() {
        Main.DesktopArgs parsed = Main.parseDesktopArgs(new String[]{"logs/demo-audit.yaml"});
        assertFalse(parsed.rest());
        assertEquals(List.of("logs/demo-audit.yaml"), parsed.remaining());
        assertFalse(Main.looksLikeFlag(parsed.remaining().get(0)));
    }

    @Test
    void nullAndEmptyAreTheSameFreshDesktopLaunch() {
        assertEquals(new Main.DesktopArgs(false, List.of()), Main.parseDesktopArgs(null));
        assertEquals(new Main.DesktopArgs(false, List.of()), Main.parseDesktopArgs(new String[0]));
    }
}
