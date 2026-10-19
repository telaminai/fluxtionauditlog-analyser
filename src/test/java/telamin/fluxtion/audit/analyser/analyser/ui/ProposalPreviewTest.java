package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b §5.6 step 5 and step 6 — the analyser's own read-only check of each proposed item (review S4), and its
 * re-check at apply (review S3). Real files in a temp folder, real symbolic links.
 */
class ProposalPreviewTest {

    @TempDir
    Path tmp;

    private Path setup() throws Exception {
        Path setup = Files.createDirectories(tmp.resolve("setup"));
        Files.createDirectories(setup.resolve("src/main/java/com/acme"));
        Files.writeString(setup.resolve("src/main/java/com/acme/Demo.java"), "package com.acme;\nclass Demo {}\n");
        Files.writeString(setup.resolve("notes.md"), "# notes\n");
        Files.writeString(setup.resolve("run.yaml"), "eventLogRecord: {}\n");
        Path outside = Files.createDirectories(tmp.resolve("elsewhere"));
        Files.writeString(outside.resolve("secret.md"), "# not yours\n");
        Files.createSymbolicLink(setup.resolve("link-out"), outside);
        return setup;
    }

    private static Map<String, ProposalPreview.Checked> check(Path setup, ProposalPreview.Item... items) {
        return ProposalPreview.check(setup, List.of(items), fqn -> fqn.equals("com.acme.Demo")).stream()
                .collect(Collectors.toMap(ProposalPreview.Checked::itemId, c -> c));
    }

    /**
     * ★ Review S4. Witness: {@code Path real = at;} instead of {@code real(at)} in {@code ProposalPreview.one} — the
     * symbolic link out of the folder is judged by its name (inside) and the "link out" row is not OUTSIDE.
     */
    @Test
    @DisplayName("★ preview: a link out, ../x, an absolute path outside, inside-but-missing, and the wrong type")
    void theAnalysersOwnVerdicts() throws Exception {
        Path setup = setup();
        Path outsideFile = tmp.resolve("elsewhere/secret.md").toAbsolutePath();
        var c = check(setup,
                new ProposalPreview.Item("linkOut", "runbook", "link-out/secret.md", null),
                new ProposalPreview.Item("dotdot", "runbook", "../elsewhere/secret.md", null),
                new ProposalPreview.Item("dotdotMissing", "runbook", "../x", null),
                new ProposalPreview.Item("absolute", "runbook", outsideFile.toString(), null),
                new ProposalPreview.Item("missing", "graphml", "graphs/none.graphml", null),
                new ProposalPreview.Item("wrongType", "sourceRoot", "notes.md", null),
                new ProposalPreview.Item("root", "sourceRoot", "src/main/java", null),
                new ProposalPreview.Item("runbook", "runbook", "notes.md", null),
                new ProposalPreview.Item("processor", "processor", "src/main/java/com/acme/Demo.java", null));
        assertEquals("OUTSIDE", c.get("linkOut").verdict(), "a link out of the folder is outside, whatever its name");
        assertTrue(c.get("linkOut").outside());
        assertEquals("OUTSIDE", c.get("dotdot").verdict());
        assertEquals("NOT_FOUND", c.get("dotdotMissing").verdict());
        assertTrue(c.get("dotdotMissing").outside(), "a missing path that names a place outside is outside");
        assertEquals("OUTSIDE", c.get("absolute").verdict());
        assertEquals("NOT_FOUND", c.get("missing").verdict());
        assertFalse(c.get("missing").outside());
        assertEquals("NOT_FOUND", c.get("missing").refusal());
        assertEquals("NOT_A_DIRECTORY", c.get("wrongType").verdict());
        assertEquals("WRONG_TYPE", c.get("wrongType").refusal());
        assertEquals("DIRECTORY (1 .java files)", c.get("root").verdict());
        assertEquals("MARKDOWN", c.get("runbook").verdict());
        assertEquals("RESOLVES (pairing UNKNOWN)", c.get("processor").verdict());
    }

    /**
     * ★ Review S3: a link swapped in between preview and apply. Witness: {@code atApply} returning
     * {@code base.resolve(itemPath).normalize()} (the old apply) — the swapped link is applied, outside the folder.
     */
    @Test
    @DisplayName("★ apply re-checks: a directory swapped for a link out after the preview is refused; the real path is kept")
    void applyReChecksTheRealPath() throws Exception {
        Path setup = setup();
        Path lib = Files.createDirectories(setup.resolve("lib"));
        assertEquals("DIRECTORY (0 .java files)", check(setup, new ProposalPreview.Item("lib", "sourceRoot", "lib", null))
                .get("lib").verdict(), "the preview saw a folder inside");
        var before = ProposalPreview.atApply(setup, "lib");
        assertNull(before.refusal());
        assertEquals(lib.toRealPath(), before.real(), "the real path is what is applied");

        Files.delete(lib);
        Files.createSymbolicLink(lib, tmp.resolve("elsewhere"));       // swapped after the preview
        var after = ProposalPreview.atApply(setup, "lib");
        assertNull(after.real());
        assertTrue(after.refusal().startsWith("OUTSIDE"), after.refusal());
        assertTrue(ProposalPreview.atApply(setup, tmp.resolve("elsewhere").toString()).refusal().startsWith("OUTSIDE"));
        assertTrue(ProposalPreview.atApply(setup, "../elsewhere").refusal().startsWith("OUTSIDE"));
        assertEquals("not found", ProposalPreview.atApply(setup, "gone").refusal());
    }

    /** ★ Review S3, the static half: the frame's apply goes through {@code atApply}, not a lexical resolve. */
    @Test
    @DisplayName("★ the frame's apply resolves an item through ProposalPreview.atApply")
    void theFramesApplyUsesAtApply() throws Exception {
        String src = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        int at = src.indexOf("private String[] applyProposalItemNow(");
        assertTrue(at > 0);
        String body = src.substring(at, src.indexOf("/** CheckProposalOutcomeEffect", at));
        assertTrue(body.contains("ProposalPreview.atApply(base, itemPath)"), "apply re-checks the real path");
        assertFalse(body.contains(".resolve(itemPath).normalize()"), "never a lexical resolve at apply");
    }

    /** Review N-d: the package line is read from the first 64 KB, never the whole file. */
    @Test
    @DisplayName("fqnOf reads a bounded head")
    void fqnOfIsBounded() throws Exception {
        Path big = tmp.resolve("Big.java");
        String pad = "// " + "x".repeat(ProposalPreview.FQN_READ_LIMIT) + "\n";
        Files.writeString(big, pad + "package com.acme.late;\nclass Big {}\n");
        assertEquals("Big", ProposalPreview.fqnOf(big), "a package line past the bound is not read");
        Path small = tmp.resolve("Small.java");
        Files.writeString(small, "package com.acme;\nclass Small {}\n");
        assertEquals("com.acme.Small", ProposalPreview.fqnOf(small));
    }
}
