package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.core.Redaction;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FolderVerdict;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M74 review M4/S10, S4, S6: the folder check, the shared redaction and the stdout line cap — pure, headless. */
class LocalAgentFoldersTest {

    @TempDir
    Path tmp;

    /** ★ Witness: return OK from {@code LocalAgentFolders.check} for a folder containing the home — the home assertions fail. */
    @Test
    @DisplayName("★ M4: the home folder, a folder containing it and the two settings folders expose credentials; a project is fine")
    void foldersThatExposeCredentials() throws Exception {
        Path home = Files.createDirectories(tmp.resolve("home/person"));
        Files.createDirectories(home.resolve(".claude/projects"));
        Files.createDirectories(home.resolve(".fluxtion-analyser/local-agent"));
        Path project = Files.createDirectories(home.resolve("work/proj"));
        assertEquals(FolderVerdict.EXPOSES_SECRETS, LocalAgentFolders.check(home, home), "the home folder");
        assertEquals(FolderVerdict.EXPOSES_SECRETS, LocalAgentFolders.check(home.getParent(), home), "a folder containing it");
        assertEquals(FolderVerdict.EXPOSES_SECRETS, LocalAgentFolders.check(tmp, home));
        assertEquals(FolderVerdict.EXPOSES_SECRETS, LocalAgentFolders.check(home.resolve(".claude"), home));
        assertEquals(FolderVerdict.EXPOSES_SECRETS, LocalAgentFolders.check(home.resolve(".claude/projects"), home));
        assertEquals(FolderVerdict.EXPOSES_SECRETS, LocalAgentFolders.check(home.resolve(".fluxtion-analyser/local-agent"), home));
        assertEquals(FolderVerdict.OK, LocalAgentFolders.check(project, home));
        Path link = Files.createSymbolicLink(tmp.resolve("alias"), home);
        assertEquals(FolderVerdict.EXPOSES_SECRETS, LocalAgentFolders.check(link, home), "a link to the home folder");
        assertEquals(FolderVerdict.UNAVAILABLE, LocalAgentFolders.check(null, home), "S10: no folder is refused");
    }

    /** ★ Witness: drop the 64-hex rule from {@code Redaction.all} — the bare credential survives. */
    @Test
    @DisplayName("★ S4: one redaction — key=value shapes, the hosted header, bare 64-hex credentials and known exact values")
    void redaction() {
        String cred = "0123456789abcdef".repeat(4);
        String out = Redaction.all("token=abc123 X-Analyser-Hosted: zzz9 " + cred + " sk-ant-ABCDEFGH12 my-rest-token!",
                "my-rest-token");
        for (String secret : List.of("abc123", "zzz9", cred, "ABCDEFGH12", "my-rest-token")) {
            assertFalse(out.contains(secret), secret + " in " + out);
        }
        assertEquals("token=…", Redaction.keyValues("token=abc123"), "the registration clients' shape, unchanged");
    }

    /** ★ Witness: ignore {@code maxLine} in {@code readLines} — the overlong line is delivered. */
    @Test
    @DisplayName("★ S6: a stdout line over the cap is dropped whole (reported as an unknown kind), the next line still arrives")
    void overlongLinesAreDropped() {
        String input = "{\"type\":\"a\"}\n" + "x".repeat(100) + "\n{\"type\":\"b\"}\n";
        List<String> got = new ArrayList<>();
        OsAgentLauncher.readLines(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), got::add, 50);
        assertEquals(List.of("{\"type\":\"a\"}", "{\"type\":\"analyser_overlong_line\"}", "{\"type\":\"b\"}"), got);
        assertInstanceOf(StreamJson.Unknown.class, StreamJson.parse(got.get(1)), "counted in the diagnostics");
    }
}
