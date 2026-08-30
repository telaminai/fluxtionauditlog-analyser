package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.ReferenceSet;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The template-download half of D-AX1d.
 *
 * <p>The playground's own audit of all fourteen catalogue templates found that exactly ONE ships agent
 * instructions; the other thirteen arrive with no {@code CLAUDE.md}, no {@code AGENTS.md} and no skills.
 * Two of them are tagged for onboarding and are offered side by side in our picker, so a person can choose
 * the bare one without knowing it is bare. This is the analyser narrowing that gap with the thing it
 * already has; their catalogue field discloses it, which is the complementary half.
 *
 * <p>Swing is not unit-tested (rule 4). What is tested is the outcome the dialog's checkbox drives.
 */
class TemplateReferenceGuideTest {

    @Test
    void aBareTemplateGETSTheGuide(@TempDir Path project) throws Exception {
        if (ReferenceSet.agreed().isEmpty()) return;
        Files.writeString(project.resolve("pom.xml"), "<project/>");   // a template that ships no bootstrap

        assertEquals(ReferenceSet.Result.WROTE,
                ReferenceSet.create(project, NewProjectDiscovery.detectKind(project)));
        assertTrue(Files.readString(project.resolve(ReferenceSet.FILE_NAME))
                .contains("fluxtion-playground.dev"));
    }

    @Test
    void aTemplateThatSHIPSItsOwnKeepsIt(@TempDir Path project) throws Exception {
        // the analyser-bundle case: it arrives with a CLAUDE.md carrying project-specific content and a
        // reference block of its own. Overwriting it would replace something better with something generic.
        String theirs = "# audit-analyser-bundle — agent guide\nrun ./run-server.sh\n";
        Files.writeString(project.resolve(ReferenceSet.FILE_NAME), theirs);

        assertEquals(ReferenceSet.Result.ALREADY_EXISTS,
                ReferenceSet.create(project, NewProjectDiscovery.detectKind(project)));
        assertEquals(theirs, Files.readString(project.resolve(ReferenceSet.FILE_NAME)),
                "the template's own guide must survive byte-for-byte");
    }

    @Test
    void aSpringTemplateGetsTheSpringLinkAndAPlainOneDoesNot(@TempDir Path root) throws Exception {
        if (ReferenceSet.agreedFor("spring").size() <= ReferenceSet.agreed().size()) return;
        String springUrl = ReferenceSet.all().stream()
                .filter(r -> r.agreed() && "spring".equals(r.appliesTo())).findFirst().orElseThrow().url();

        Path spring = root.resolve("spring");
        Files.createDirectories(spring.resolve("src/main/fluxtion/designer"));
        Files.writeString(spring.resolve("src/main/fluxtion/designer/application-context.xml"), "<beans/>");
        ReferenceSet.create(spring, NewProjectDiscovery.detectKind(spring));
        assertTrue(Files.readString(spring.resolve(ReferenceSet.FILE_NAME)).contains(springUrl));

        Path plain = root.resolve("plain");
        Files.createDirectories(plain);
        ReferenceSet.create(plain, NewProjectDiscovery.detectKind(plain));
        assertFalse(Files.readString(plain.resolve(ReferenceSet.FILE_NAME)).contains(springUrl),
                "a non-Spring template must not be charged for a Spring link");
    }

    @Test
    void theOfferIsCARRIEDbyTheChoice() {
        // the dialog's checkbox is the only thing that can turn this on; a Choice that did not ask for it
        // must not be able to write. Swing cannot be driven here, so the carrier is asserted instead.
        TemplateProjectDialog.Choice off =
                new TemplateProjectDialog.Choice(null, Path.of("x"), false);
        TemplateProjectDialog.Choice on =
                new TemplateProjectDialog.Choice(null, Path.of("x"), true);
        assertFalse(off.referenceGuide());
        assertTrue(on.referenceGuide());
    }
}
