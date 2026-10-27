package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** A1.9: the real Main twice, not a frame fixture that bypasses startup. No model or real CLI configuration. */
class M79FirstMinuteFrameTest {
    @Test void firstMinuteInputsAndProcessorNeverBecomeMachineSettings(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        probe(tmp, Files.createDirectories(tmp.resolve("home")), stub(tmp),
                Files.createTempDirectory(Path.of("target"), "m79-config-").toAbsolutePath(), "first-minute-config");
    }
    @Test void firstLaunchPlaysThePinnedDiamondAndRelaunchOffersNeitherPlaybackNorRecovery(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path bin = stub(tmp);
        Path evidence = Files.createTempDirectory(Path.of("target"), "m79-frames-").toAbsolutePath();
        probe(tmp, home, bin, evidence, "first");
        probe(tmp, home, bin, evidence, "returning");
    }

    @Test void aSubstitutedStepThreeRecordWithholdsTheWholeClaimOnTheRealOverlay(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        probe(tmp, Files.createDirectories(tmp.resolve("home")), stub(tmp),
                Files.createTempDirectory(Path.of("target"), "m79-substituted-").toAbsolutePath(), "substituted");
    }

    @Test void addingAFindingMakesTheFirstMinuteAnOrdinaryRecoverableSession(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home")), bin = stub(tmp);
        Path evidence = Files.createTempDirectory(Path.of("target"), "m79-personal-").toAbsolutePath();
        probe(tmp, home, bin, evidence, "personal");
        probe(tmp, home, bin, evidence, "personal-returning");
    }

    @Test void aFailedExtractionShowsOneReasonAndNeverStartsAHiddenWalkOrDialog(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        probe(tmp, Files.createDirectories(tmp.resolve("home")), stub(tmp),
                Files.createTempDirectory(Path.of("target"), "m79-failed-").toAbsolutePath(), "failed");
    }

    @Test void closeLogAndTopologyAfterTheFirstMinuteShowsTheStartPage(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        probe(tmp, Files.createDirectories(tmp.resolve("home")), stub(tmp),
                Files.createTempDirectory(Path.of("target"), "m79-close-all-").toAbsolutePath(), "close-all");
    }

    @Test void aLiveSpotlightBesideTheShippedStripIsNotLabelledBundled(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        probe(tmp, Files.createDirectories(tmp.resolve("home")), stub(tmp),
                Files.createTempDirectory(Path.of("target"), "m79-live-").toAbsolutePath(), "live-spotlight");
    }

    private static Path stub(Path tmp) throws Exception {
        Path bin = Files.createDirectories(tmp.resolve("stub-bin"));
        Path claude = Files.writeString(bin.resolve("claude"),
                "#!/bin/sh\nif [ \"$1\" = \"--version\" ]; then printf '2.1.282 (DEMO stub)\\n'; exit 0; fi\nexit 91\n");
        assertTrue(claude.toFile().setExecutable(true, true));
        return bin;
    }

    private static void probe(Path tmp, Path home, Path bin, Path evidence, String mode) throws Exception {
        Path output = tmp.resolve(mode + ".log");
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=false", "-Duser.home=" + home,
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                FirstMinuteAppProbe.class.getName(), mode, evidence.toString());
        String display = System.getenv("DISPLAY");
        String displayAuthority = System.getenv("XAUTHORITY");
        builder.environment().clear();
        builder.environment().put("HOME", home.toString());
        builder.environment().put("CLAUDE_CONFIG_DIR", Files.createDirectories(home.resolve("stub-claude")).toString());
        builder.environment().put("PATH", bin + ":/usr/bin:/bin");
        builder.environment().put("LANG", "en_US.UTF-8");
        if (display != null) builder.environment().put("DISPLAY", display);
        if (displayAuthority != null) builder.environment().put("XAUTHORITY", displayAuthority);
        builder.redirectErrorStream(true).redirectOutput(output.toFile());
        Process child = builder.start();
        try {
            assertTrue(child.waitFor(75, TimeUnit.SECONDS), "the isolated real-Main probe finishes: " + Files.readString(output));
            assertEquals(0, child.exitValue(), "real-Main " + mode + ": " + Files.readString(output));
        } finally {
            child.descendants().forEach(ProcessHandle::destroyForcibly);
            if (child.isAlive()) child.destroyForcibly();
        }
    }
}
