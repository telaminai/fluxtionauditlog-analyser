package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P1b, OD-74.12 (§10 P1b ★ trust, ★ dialog): full-posture folder trust is separate from the default posture's and
 * bound to the content of {@code .claude/settings.json}, {@code .claude/settings.local.json} and {@code .mcp.json};
 * the dialog names what will run from those files, by name only, and says when a file cannot be read.
 */
class FullFolderTrustTest {

    @TempDir
    Path dir;

    private void plant() throws Exception {
        Files.createDirectories(dir.resolve(".claude"));
        Files.writeString(dir.resolve(".claude/settings.json"), "{\"hooks\":{\"SessionStart\":[{\"hooks\":[{\"type\":"
                + "\"command\",\"command\":\"echo secret-command\"}]}],\"PreToolUse\":[{\"matcher\":\"*\",\"hooks\":[{\"type\":"
                + "\"command\",\"command\":\"a\"},{\"type\":\"command\",\"command\":\"b\"}]}]}}");
        Files.writeString(dir.resolve(".mcp.json"), "{\"mcpServers\":{\"db-tools\":{\"command\":\"npx\"},\"search\":{}}}");
    }

    /**
     * ★ Witness: key the record on the path alone ({@code confirms} = any record starting with the path) — the "changed
     * file asks again" assertions fail.
     */
    @Test
    @DisplayName("★ trust is bound to content: a changed settings.json, settings.local.json or .mcp.json asks again; an unchanged one does not")
    void trustIsBoundToContent() throws Exception {
        plant();
        String real = dir.toRealPath().toString();
        String d0 = FullFolderTrust.digest(dir);
        List<String> records = FullFolderTrust.recorded(List.of(), real, d0);
        assertTrue(FullFolderTrust.confirms(records, real, FullFolderTrust.digest(dir)), "unchanged: confirmed");

        assertFalse(FullFolderTrust.confirms(List.of(real), real, d0),
                "a DEFAULT-posture record (the bare path) never confirms the full posture");

        for (String file : FullFolderTrust.FILES) {
            Path f = dir.resolve(file);
            String before = Files.exists(f) ? Files.readString(f) : null;
            Files.createDirectories(f.getParent());
            Files.writeString(f, "{\"hooks\":{\"SessionStart\":[{\"hooks\":[{\"type\":\"command\",\"command\":\"new\"}]}]}}");
            assertFalse(FullFolderTrust.confirms(records, real, FullFolderTrust.digest(dir)),
                    "a changed or added " + file + " (a git pull that adds a hook) asks again");
            if (before == null) Files.delete(f);
            else Files.writeString(f, before);
            assertTrue(FullFolderTrust.confirms(records, real, FullFolderTrust.digest(dir)), "restored: confirmed again");
        }
        Files.delete(dir.resolve(".mcp.json"));
        assertFalse(FullFolderTrust.confirms(records, real, FullFolderTrust.digest(dir)), "a removed file is a change too");

        // re-confirming replaces the folder's old record, never piles them up
        List<String> again = FullFolderTrust.recorded(records, real, FullFolderTrust.digest(dir));
        assertEquals(1, again.size());
    }

    @Test
    @DisplayName("the scan names hook events with their counts and the .mcp.json server names, never a command")
    void theScanNamesWhatRuns() throws Exception {
        plant();
        var scan = FullFolderTrust.scan(dir);
        String text = FullFolderTrust.dialogText(dir, scan);
        assertTrue(text.contains("Hooks in this folder: SessionStart (1), PreToolUse (2) — from .claude/settings.json"), text);
        assertTrue(text.contains("MCP servers in this folder: db-tools, search — from .mcp.json"), text);
        assertTrue(text.contains("This is asked again if these files change"), text);
        assertFalse(text.contains("secret-command") || text.contains("npx"), "the commands are not shown: " + text);
        assertFalse(scan.nothingRuns());
    }

    @Test
    @DisplayName("nothing that runs, and a file over 256 KB or not JSON, are said — never hidden")
    void unreadableFilesAreSaid() throws Exception {
        assertTrue(FullFolderTrust.dialogText(dir, FullFolderTrust.scan(dir)).contains("Nothing in this folder"));
        Files.createDirectories(dir.resolve(".claude"));
        Files.writeString(dir.resolve(".claude/settings.json"), "{ not json");
        Files.writeString(dir.resolve(".mcp.json"), "{\"mcpServers\":{\"x\":{}}," + " ".repeat(FullFolderTrust.MAX_BYTES) + "}");
        var scan = FullFolderTrust.scan(dir);
        String text = FullFolderTrust.dialogText(dir, scan);
        assertTrue(text.contains(".claude/settings.json: could not be read (not JSON)"), text);
        assertTrue(text.contains(".mcp.json: could not be read (over 256 KB)"), text);
        assertTrue(scan.anyUnreadable());
        assertNotNull(scan.digest(), "the digest still covers them: Start stays possible after the person's Yes");
    }

    @Test
    @DisplayName("a planted name's control and format characters are made visible")
    void namesAreMadePlain() {
        assertEquals("a\\u202Eb\\u000Ac", FullFolderTrust.plain("a‮b\nc"));
        assertTrue(FullFolderTrust.plain("x".repeat(200)).endsWith("…"));
    }
}
