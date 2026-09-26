package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-26 fresh-look virgin run: every model opened a downloaded project's design by path, was refused, and then
 * authorised the WHOLE project directory with source_root, because the refusal named source_root and never the
 * project. The refusal now names open {project} when the file belongs to a project that is not open.
 */
class DesignFilesRefusalHintTest {
    @TempDir Path tmp;
    Path project, design;

    @BeforeEach void layout() throws Exception {
        project = Files.createDirectories(tmp.resolve("bundle"));
        Files.createDirectories(project.resolve(".analyser"));
        Files.writeString(project.resolve(ProjectProfile.CANONICAL_RELATIVE), "sourceRoot.count=0\n");
        Path designer = Files.createDirectories(project.resolve("src/main/fluxtion/designer"));
        design = Files.writeString(designer.resolve("application-context.xml"), "<beans/>\n");
    }

    private static String refusal(DesignFiles files, String requested) {
        return assertThrows(IOException.class, () -> files.resolve(requested)).getMessage();
    }

    @Test void aDesignInsideAProjectThatIsNotOpenNamesOpenProjectBeforeSourceRoot() throws Exception {
        String why = refusal(new DesignFiles(List.of(), null), design.toString());
        String dir = project.toRealPath().toString();
        assertTrue(why.startsWith("file outside authorised roots: " + design), why);
        assertTrue(why.contains("it is inside the project " + dir + " — open {project: \"" + dir + "\"}"),
                "the refusal names the project to open: " + why);
        assertTrue(why.indexOf("open {project") < why.indexOf("source_root"), "the project comes first: " + why);
        assertTrue(why.contains("to authorise its parent only, call source_root"), "the narrow alternative stays: " + why);
    }

    @Test void theOpenProjectIsNeverSuggestedAgain() throws Exception {
        String why = refusal(new DesignFiles(List.of(), project.toRealPath()), design.toString());
        assertFalse(why.contains("open {project"), "suggesting the project already open is noise: " + why);
        assertTrue(why.contains("call source_root"), why);
    }

    /** PR #35 review: a project opened through a directory alias was suggested again (lexical vs canonical paths). */
    @Test void theOpenProjectReachedThroughAnAliasIsNotSuggestedAgain() throws Exception {
        Path alias = Files.createSymbolicLink(tmp.resolve("bundle-alias"), project);
        assertNotEquals(alias.toAbsolutePath().normalize(), project.toRealPath(), "control: the alias is lexically different");
        String why = refusal(new DesignFiles(List.of(), alias), design.toString());
        assertFalse(why.contains("open {project"), "the project open through an alias is not suggested again: " + why);
        assertTrue(why.contains("call source_root"), "the narrow alternative stays: " + why);
    }

    @Test void aFileInNoProjectKeepsOnlyTheSourceRootHint() throws Exception {
        Path loose = Files.writeString(tmp.resolve("loose.xml"), "<beans/>\n");
        String why = refusal(new DesignFiles(List.of(), null), loose.toString());
        assertFalse(why.contains("open {project"), why);
        assertTrue(why.contains("call source_root"), why);
    }

    @Test void aRelativePathWithNothingToResolveAgainstSaysSoAndAsksForAnAbsolutePath() {
        String why = refusal(new DesignFiles(List.of(), null), "src/main/fluxtion/designer/application-context.xml");
        assertTrue(why.startsWith("file unavailable or outside authorised roots: src/main/fluxtion/designer"), why);
        assertTrue(why.contains("no project is open and no source roots are set"), why);
        assertTrue(why.contains("Pass an absolute path, or open the project first with open {project: <dir>}"), why);
    }

    @Test void aRelativePathNamesWhereItWasLookedUp() throws Exception {
        Path root = Files.createDirectories(tmp.resolve("elsewhere"));
        String why = refusal(new DesignFiles(List.of(root.toString()), null), "missing.xml");
        assertTrue(why.contains("it was looked up under the source roots [" + root.toAbsolutePath().normalize() + "]"), why);
    }

    @Test void theEnclosingProjectIsTheNearestAncestorWithACanonicalProfile() throws Exception {
        assertEquals(Optional.of(project.toAbsolutePath().normalize()), ProjectProfile.enclosingProject(design));
        Path inner = Files.createDirectories(project.resolve("modules/inner"));
        Files.createDirectories(inner.resolve(".analyser"));
        Files.writeString(inner.resolve(ProjectProfile.CANONICAL_RELATIVE), "");
        Path deep = Files.writeString(Files.createDirectories(inner.resolve("src")).resolve("a.xml"), "<beans/>");
        assertEquals(Optional.of(inner.toAbsolutePath().normalize()), ProjectProfile.enclosingProject(deep), "nearest wins");
        assertEquals(Optional.empty(), ProjectProfile.enclosingProject(tmp.resolve("x.xml")));
    }
}
