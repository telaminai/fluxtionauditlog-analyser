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
        assertEquals(ReferenceSet.Result.ALREADY_EXISTS, ReferenceSet.create(project),
                "must refuse rather than write, and say why");
        assertEquals(mine, Files.readString(claude), "the author's file must be byte-identical afterwards");
    }

    @Test
    void nothingIsWrittenWhileTheSetIsUNSIGNED(@TempDir Path project) throws Exception {
        // today every entry is 'proposed'. The feature must not ship guidance nobody approved — and when
        // the owner signs entries off, this test starts exercising the create path instead.
        if (!ReferenceSet.agreed().isEmpty()) return;
        assertEquals(ReferenceSet.Outcome.NOTHING_AGREED, ReferenceSet.offer(project));
        assertEquals(ReferenceSet.Result.NOTHING_AGREED, ReferenceSet.create(project));
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
    void theRenderedBlockCarriesItsMACHINEreadableEnd() {
        // contract v4: the bench bounds its restated-rule scan on this marker and fails closed without
        // it, so a renderer that stops emitting it silently disables a check rather than failing one.
        if (ReferenceSet.agreed().isEmpty()) return;
        String md = ReferenceSet.markdown(null);
        assertTrue(md.contains(ReferenceSet.BLOCK_END), "the block must mark where it ends");
        for (var r : ReferenceSet.agreedFor(null)) {
            assertTrue(md.indexOf(r.url()) < md.indexOf(ReferenceSet.BLOCK_END),
                    r.id() + " must sit ABOVE the boundary, or the scan will read it as project prose");
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
    void createRefusesAFileThatAPPEAREDafterTheOfferSaidItCould(@TempDir Path project) throws Exception {
        // review F6: an earlier version of this test called Files.writeString(CREATE_NEW) directly inside
        // assertThrows. It proved the JDK primitive and would still have passed if create() were changed to
        // truncate the author's file. This one drives ReferenceSet.create().
        if (ReferenceSet.agreed().isEmpty()) return;   // create path unreachable until sign-off

        Path claude = project.resolve(ReferenceSet.FILE_NAME);
        assertEquals(ReferenceSet.Outcome.CAN_CREATE, ReferenceSet.offer(project),
                "the offer must green-light first, or this is not the race being tested");

        // the gap: someone writes between offer and create
        String theirs = "# written between offer and create\n";
        Files.writeString(claude, theirs);

        assertEquals(ReferenceSet.Result.ALREADY_EXISTS, ReferenceSet.create(project),
                "create() must decline a file that appeared after the offer, and name the real reason");
        assertEquals(theirs, Files.readString(claude), "and their bytes must survive it");
    }

    @Test
    void appliesToSELECTSratherThanAnnotates() {
        // review N1: a Spring-only link rendered into every project charges every reader for an
        // irrelevant fourth link, and an always-in-context file is a tax on every turn.
        var spring = ReferenceSet.all().stream()
                .filter(r -> r.agreed() && "spring".equals(r.appliesTo())).findFirst();
        if (spring.isEmpty()) return;
        assertFalse(ReferenceSet.markdown(null).contains(spring.get().url()),
                "a spring-only resource must not appear in a project that is not spring-authored");
        assertTrue(ReferenceSet.markdown("spring").contains(spring.get().url()),
                "and it must appear when it does apply");
        assertTrue(ReferenceSet.agreedFor("spring").size() > ReferenceSet.agreedFor(null).size(),
                "selection must actually widen the set for a matching kind");
    }

    @Test
    void theLastRESORTguardThrowsRatherThanTruncating(@TempDir Path project) throws Exception {
        // review F6: the previous version of this called Files.writeString directly and so proved the JDK
        // rather than this class. writeNew IS the production write path, split out so the CREATE_NEW guard
        // — defence in depth for the window between create()'s own check and the write — is reachable.
        Path claude = project.resolve(ReferenceSet.FILE_NAME);
        String theirs = "# theirs\n";
        Files.writeString(claude, theirs);

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> ReferenceSet.writeNew(claude, "ours"),
                "the write path must refuse an existing file; if this ever truncates, an author loses work");
        assertEquals(theirs, Files.readString(claude));
    }

    @Test
    void createIsTheONLYwriterAndItWritesTheRenderedSet(@TempDir Path project) throws Exception {
        // the positive half, so the refusal tests above cannot pass by create() being inert
        if (ReferenceSet.agreed().isEmpty()) return;
        assertEquals(ReferenceSet.Result.WROTE, ReferenceSet.create(project), "create must report that it wrote");
        String written = Files.readString(project.resolve(ReferenceSet.FILE_NAME));
        assertEquals(ReferenceSet.markdown(), written, "the file must be exactly the rendered set");
        for (var r : ReferenceSet.agreed()) {
            assertTrue(written.contains(r.url()), r.id() + " missing from the written file");
        }
        assertEquals(ReferenceSet.Result.ALREADY_EXISTS, ReferenceSet.create(project),
                "a second call must refuse — the file now exists");
    }
}
