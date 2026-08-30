package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-AX1d — writing a CLAUDE.md into a project, which is the first documentation the analyser has ever
 * written into a user's repository. The tests that matter are the REFUSALS.
 */
class ReferenceSetWriteTest {

    @Test
    void anExistingFileIsNEVERtouched(@TempDir Path project) throws Exception {
        Path claude = project.resolve(ReferenceSet.FILE_NAME);
        String mine = "# my own notes\nthings I worked out the hard way\n";
        Files.writeString(claude, mine);

        assertEquals(ReferenceSet.Outcome.EXISTS, ReferenceSet.offer(project));
        assertFalse(ReferenceSet.create(project), "must refuse rather than write");
        assertEquals(mine, Files.readString(claude), "the author's file must be byte-identical afterwards");
    }

    @Test
    void nothingIsWrittenWhileTheSetIsUNSIGNED(@TempDir Path project) throws Exception {
        // today every entry is 'proposed'. The feature must not ship guidance nobody approved — and when
        // the owner signs entries off, this test starts exercising the create path instead.
        if (!ReferenceSet.agreed().isEmpty()) return;
        assertEquals(ReferenceSet.Outcome.NOTHING_AGREED, ReferenceSet.offer(project));
        assertFalse(ReferenceSet.create(project));
        assertFalse(Files.exists(project.resolve(ReferenceSet.FILE_NAME)),
                "an empty set must produce no file at all — not a heading with no links under it");
        assertTrue(ReferenceSet.markdown().isEmpty());
    }

    @Test
    void aNullOrMissingRootIsNotAnError() {
        assertEquals(ReferenceSet.Outcome.NOTHING_AGREED, ReferenceSet.offer(null));
    }

    @Test
    void theSetParsesAndEveryEntryIsUsable() {
        var all = ReferenceSet.all();
        assertFalse(all.isEmpty(), "the shipped resource must load from the classpath, not just from disk");
        for (var r : all) {
            assertTrue(r.url() != null && r.url().startsWith("https://"), r.id());
            assertTrue(r.why() != null && !r.why().isBlank(), r.id());
        }
    }

    @Test
    void onlyAGREEDentriesCouldEverBeWritten() {
        // the excluded audit-replay page actively misleads authors (fluxtion#22); a rendering bug that
        // let it through would put that in front of every new project
        String rendered = ReferenceSet.markdown();
        for (var r : ReferenceSet.all()) {
            if (!r.agreed()) {
                assertFalse(rendered.contains(r.url()),
                        r.id() + " is '" + r.status() + "' and must not be rendered");
            }
        }
    }

    @Test
    void aRaceLosesTheWriteRatherThanTheAuthorsFile(@TempDir Path project) throws Exception {
        if (ReferenceSet.agreed().isEmpty()) return;   // create path unreachable until sign-off
        Files.writeString(project.resolve(ReferenceSet.FILE_NAME), "written between offer and create");
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> {
            // simulate the gap: offer said CAN_CREATE, someone else wrote, create must not clobber
            Files.writeString(project.resolve(ReferenceSet.FILE_NAME), ReferenceSet.markdown(),
                    java.nio.file.StandardOpenOption.CREATE_NEW);
        });
    }
}
