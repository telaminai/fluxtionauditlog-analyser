package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrustedProjectScriptsTest {
    @TempDir Path root;

    private List<String> trust() { return List.of(TrustedProjectScripts.record(TrustedProjectScripts.scan(root))); }

    @Test
    void onlyDeclaredProgramsAndTheWrapperAreCandidates() throws Exception {
        Files.writeString(root.resolve("mvnw"), "DEMO wrapper");
        Files.writeString(root.resolve("deploy.sh"), "DEMO undeclared");
        assertEquals(List.of("./mvnw"), TrustedProjectScripts.candidates(root));
        assertFalse(TrustedProjectScripts.permits(trust(), trust(), root, "./deploy.sh"), "★ B3: another program asks");
        assertFalse(TrustedProjectScripts.permits(trust(), trust(), root, "./mvnw test; echo pwned"), "★ B3: no second command");
        assertTrue(TrustedProjectScripts.permits(trust(), trust(), root, "./mvnw clean install -q"));
        assertFalse(TrustedProjectScripts.permits(List.of(), trust(), root, "./mvnw test"), "★ B2: no captured grant asks");
        assertFalse(TrustedProjectScripts.permits(trust(), List.of(), root, "./mvnw test"), "revocation is immediate");
    }

    @Test
    void everyShellInjectionFormRetainsOrdinaryAsking() throws Exception {
        Files.writeString(root.resolve("mvnw"), "DEMO wrapper");
        for (String command : List.of("./mvnw test; echo pwned", "./mvnw test && echo pwned", "./mvnw test || echo pwned",
                "./mvnw test | sh", "./mvnw `echo pwned`", "./mvnw $(echo pwned)", "./mvnw <(echo pwned)",
                "./mvnw test > DEMO", "./mvnw < DEMO", "DEMO=1 ./mvnw test", "./mvnw test\necho pwned",
                "./mvnw test &", "sh ./mvnw test", "./mvnw ${DEMO}", "./mvnw *", "./mvnw # ignored",
                "./mvnw \"$(echo pwned)\"", "./mvnw test\\\necho pwned", "\u2003./mvnw test", "./mvnw test\u2003",
                "\f./mvnw test")) {
            assertTrue(LocalAgentPosture.buildScriptInvocation(command, root).isEmpty(), command);
            assertFalse(TrustedProjectScripts.permits(trust(), trust(), root, command), command);
        }
    }

    @Test
    void changingOneScriptDoesNotBorrowAnUnchangedSiblingsGrant() throws Exception {
        Files.writeString(root.resolve("mvnw"), "DEMO wrapper A");
        Files.writeString(root.resolve("run.sh"), "DEMO run A");
        var old = trust();
        var scan = TrustedProjectScripts.scan(root);
        Files.writeString(root.resolve("mvnw"), "DEMO wrapper B");
        assertFalse(TrustedProjectScripts.permits(old, old, root, "./mvnw test"), "★ B4: byte change asks immediately");
        assertTrue(TrustedProjectScripts.permits(old, old, root, "./run.sh"), "bindings are per program, not borrowed");
        var now = TrustedProjectScripts.scan(root);
        assertEquals(TrustedProjectScripts.CHANGED, TrustedProjectScripts.changeNote(old, now.realRoot(), now.scripts(), now.digest()));
        assertFalse(TrustedProjectScripts.confirms(old, scan.realRoot(), now.scripts(), now.digest()));
        assertFalse(TrustedProjectScripts.permits(old, trust(), root, "./mvnw test"), "re-grant cannot widen the capture");
        Files.writeString(root.resolve("check-fluxtion-key.sh"), "DEMO new script");
        assertFalse(TrustedProjectScripts.permits(old, trust(), root, "./check-fluxtion-key.sh"), "new program asks");
    }

    @Test
    void anOutOfProjectSymlinkNeverGetsItsApparentNamesTrust(@TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("DEMO-wrapper"), "DEMO wrapper");
        Files.createSymbolicLink(root.resolve("mvnw"), outside.resolve("DEMO-wrapper"));
        assertTrue(LocalAgentPosture.buildScriptInvocation("./mvnw test", root).isEmpty(), "★ B5: real parent must be the project");
        assertTrue(TrustedProjectScripts.candidates(root).isEmpty());
        assertFalse(TrustedProjectScripts.permits(trust(), trust(), root, "./mvnw test"));
    }

    @Test
    void oversizedOrUnreadableProgramsNeverReceiveAPartialDigest() throws Exception {
        Files.write(root.resolve("mvnw"), new byte[TrustedProjectScripts.MAX_BYTES + 1]);
        assertTrue(TrustedProjectScripts.candidates(root).isEmpty());
        Files.delete(root.resolve("mvnw"));
        Files.createDirectory(root.resolve("mvnw"));
        assertTrue(TrustedProjectScripts.candidates(root).isEmpty());
        assertFalse(TrustedProjectScripts.permits(List.of("{malformed"), List.of("{}"), root, "./mvnw test"));
    }

    @Test
    void replacingOneProjectsRecordPreservesOthersAndUntickingRevokes() throws Exception {
        Files.writeString(root.resolve("mvnw"), "DEMO wrapper");
        var scan = TrustedProjectScripts.scan(root);
        String own = TrustedProjectScripts.record(scan);
        assertEquals(List.of("DEMO unrelated record", own), TrustedProjectScripts.recorded(List.of("DEMO unrelated record"), scan.realRoot(), own));
        assertEquals(List.of("DEMO unrelated record"), TrustedProjectScripts.recorded(List.of("DEMO unrelated record", own), scan.realRoot(), ""));
        assertTrue(TrustedProjectScripts.confirms(List.of(own), scan.realRoot(), scan.scripts(), scan.digest()));
    }
}
