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

    /**
     * ★ Review finding 5. Witness: the P1b scan, which read only {@code hooks} and {@code mcpServers} — the plugin,
     * command and unknown-key assertions fail and the dialog says "Nothing … runs".
     */
    @Test
    @DisplayName("★ plugins, marketplaces and command-valued keys are named; a key the analyser does not interpret may run other commands")
    void everythingThatMayRunIsNamed() throws Exception {
        Files.createDirectories(dir.resolve(".claude"));
        Files.writeString(dir.resolve(".claude/settings.json"), "{\"enabledPlugins\":{\"formatter@team\":true,"
                + "\"off@team\":false},\"extraKnownMarketplaces\":{\"team\":{}},\"apiKeyHelper\":\"./key.sh\","
                + "\"statusLine\":{\"type\":\"command\",\"command\":\"x\"},\"env\":{\"NODE_OPTIONS\":\"--require x\"},"
                + "\"permissions\":{\"allow\":[]}}");
        var scan = FullFolderTrust.scan(dir);
        String text = FullFolderTrust.dialogText(dir, scan);
        assertTrue(text.contains("Plugins enabled in this folder: formatter@team — from .claude/settings.json"), text);
        assertFalse(text.contains("off@team"), "a plugin set to false is not enabled");
        assertTrue(text.contains("Plugin marketplaces added by this folder: team"), text);
        assertTrue(text.contains("Commands this folder's settings run: apiKeyHelper, statusLine"), text);
        assertTrue(text.contains("Settings the analyser does not interpret: env — from .claude/settings.json; they may run "
                + "other commands"), text);
        assertFalse(text.contains("permissions"), "a key known to run nothing is not listed");
        assertFalse(text.contains("Nothing in this folder"), text);
        assertFalse(text.contains("./key.sh") || text.contains("NODE_OPTIONS"), "values are never shown");
        assertFalse(scan.nothingRuns());

        Files.writeString(dir.resolve(".claude/settings.json"), "{\"permissions\":{\"allow\":[]},\"model\":\"x\"}");
        assertTrue(FullFolderTrust.dialogText(dir, FullFolderTrust.scan(dir)).contains("Nothing in this folder"),
                "only keys known to run nothing: nothing runs");
        Files.writeString(dir.resolve(".mcp.json"), "{\"mcpServers\":{},\"somethingNew\":1}");
        assertTrue(FullFolderTrust.dialogText(dir, FullFolderTrust.scan(dir))
                .contains("does not interpret: somethingNew — from .mcp.json"));
    }

    /** ★ Review finding 13. Witness: the digest's {@code !isRegularFile → absent} — the directory reads as absent. */
    @Test
    @DisplayName("★ a .mcp.json that is not a regular file is unreadable in the scan and in the digest alike; the digest is bounded")
    void nonRegularAndLargeFiles() throws Exception {
        String absent = FullFolderTrust.digest(dir);
        Files.createDirectories(dir.resolve(".mcp.json"));
        var scan = FullFolderTrust.scan(dir);
        assertEquals(FullFolderTrust.State.UNREADABLE, scan.files().get(2).state());
        assertNotEquals(absent, scan.digest(), "a directory named .mcp.json is not 'absent'");
        assertFalse(scan.allAbsent());
        assertFalse(scan.nothingRuns(), "an unreadable file is never 'nothing runs'");
        Files.delete(dir.resolve(".mcp.json"));

        Path big = dir.resolve(".mcp.json");
        Files.writeString(big, "{}" + " ".repeat(FullFolderTrust.MAX_BYTES * 2));
        String d1 = FullFolderTrust.digest(dir);
        Files.writeString(big, "{}" + " ".repeat(FullFolderTrust.MAX_BYTES * 2 + 1));
        assertNotEquals(d1, FullFolderTrust.digest(dir), "a change past the read bound still changes the digest (size)");
        assertTrue(FullFolderTrust.plain("a\u2028b\u2029c").contains("\\u2028"), "line separators made visible");
    }
}
