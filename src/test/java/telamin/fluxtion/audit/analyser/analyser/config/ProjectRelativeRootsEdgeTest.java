package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M35.10/.11 review — the two path shapes the branch's own tests do not reach, locked in because
 * both are silent when wrong: a root OUTSIDE the project (must not be relativised into ../..) and a
 * root that IS the project root (written "."). Neither throws if it regresses; the profile simply
 * points somewhere that does not exist, which is the failure M35.10 was raised to fix.
 */
class ProjectRelativeRootsEdgeTest {

    @Test
    void aRootOUTSIDEtheProjectStillRoundTrips(@TempDir Path tmp) throws Exception {
        Path proj = Files.createDirectories(tmp.resolve("proj"));
        Path outside = Files.createDirectories(tmp.resolve("elsewhere/lib/src"));
        AppConfig c = new AppConfig();
        c.sourceRoots.add(proj.resolve("src/main/java").toString());
        c.sourceRoots.add(outside.toString());                 // NOT under the project

        Path file = ProjectProfile.pathFor(proj);
        Files.createDirectories(file.getParent());
        ProjectProfile.save(file, c, new SettingsShare());
        String text = Files.readString(file);

        AppConfig back = new AppConfig();
        ProjectProfile.load(file, back, new SettingsShare());
        assertEquals(2, back.sourceRoots.size(), "both roots survive: " + text);
        assertTrue(back.sourceRoots.get(0).endsWith("src/main/java"), back.sourceRoots.toString());
        assertEquals(outside.toString(), Path.of(back.sourceRoots.get(1)).normalize().toString(),
                "a root outside the project must come back as itself, not relativised into ../..");
    }

    @Test
    void aRootThatIS_theProjectRootRoundTrips(@TempDir Path tmp) throws Exception {
        Path proj = Files.createDirectories(tmp.resolve("proj"));
        AppConfig c = new AppConfig();
        c.sourceRoots.add(proj.toString());                    // exactly the root -> written as "."
        Path file = ProjectProfile.pathFor(proj);
        Files.createDirectories(file.getParent());
        ProjectProfile.save(file, c, new SettingsShare());
        AppConfig back = new AppConfig();
        ProjectProfile.load(file, back, new SettingsShare());
        assertEquals(proj.toRealPath().toString(),
                Path.of(back.sourceRoots.get(0)).toRealPath().toString(),
                "\".\" must resolve back to the project root: " + Files.readString(file));
    }

}
