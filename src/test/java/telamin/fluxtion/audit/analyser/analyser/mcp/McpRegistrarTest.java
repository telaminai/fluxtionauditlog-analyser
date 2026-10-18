package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * M77-3 (spec-ai-setup-wizard.md §9.3, S5, S10; ★ T10, T22, T24) — the registrar with a STUB client: a shell script in
 * an isolated home's {@code ~/.local/bin} that records its working folder and arguments and touches nothing else. No
 * real {@code claude} or {@code codex} is ever run: every test first asserts that the resolved executable IS the stub.
 */
class McpRegistrarTest {

    /** A GUI-shaped PATH (a Finder-launched app's): the client is not on it. */
    static final Map<String, String> GUI_ENV = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");

    static Path stub(Path home, String name, Path record) throws Exception {
        Path exe = Files.createDirectories(home.resolve(".local/bin")).resolve(name);
        Files.writeString(exe, """
                #!/bin/sh
                printf '%s|%s\\n' "$PWD" "$*" >> '""" + record + """
                '
                if [ "$2" = "get" ]; then echo "No MCP server found with name: fluxtion-analyser"; exit 1; fi
                exit 0
                """);
        Files.setPosixFilePermissions(exe, PosixFilePermissions.fromString("rwx------"));
        return exe.toAbsolutePath().normalize();
    }

    static McpLaunchCommand launcher(Path home) {
        return McpLaunchCommand.of(List.of(home.resolve(".jbang/bin/analyser").toString()));
    }

    /**
     * ★ T24 (review R1-9). With a GUI-shaped PATH and claude in {@code ~/.local/bin}, the registrar finds it and builds a
     * register command with it. Witness: resolve with {@code ClaudeMcpClient.findExecutable} over the inherited PATH (what
     * {@code detect()} does) — nothing is found and no command is built.
     */
    @Test
    @DisplayName("★ T24: a GUI-shaped PATH with claude in ~/.local/bin — found, and a register command is built from it")
    void theOneResolverFindsTheClient(@TempDir Path tmp) throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"), "a POSIX stub");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = stub(home, "claude", tmp.resolve("record"));
        Path codex = stub(home, "codex", tmp.resolve("record"));
        McpRegistrar r = new McpRegistrar(GUI_ENV, home, Optional.of(launcher(home)), () -> tmp.resolve("scratch"));
        assertEquals(Optional.of(claude), r.executable(false), "★ found beyond the GUI PATH");
        assertEquals(Optional.of(codex), r.executable(true));
        List<List<String>> add = r.vectors(false, false);
        assertEquals(1, add.size());
        assertEquals(claude.toString(), add.get(0).get(0), "★ the command is built from the resolver's claude");
        assertEquals(List.of("mcp", "add", "--scope", "user", "--transport", "stdio", "fluxtion-analyser", "--"),
                add.get(0).subList(1, 9), "M42.4: user scope, the exact vector");
        assertEquals(2, r.vectors(false, true).size(), "a replace is the remove, then the add");
        assertTrue(new McpRegistrar(GUI_ENV, home, Optional.empty(), () -> tmp).vectors(false, false).isEmpty(),
                "no supported launcher: nothing to run");
    }

    /**
     * ★ T10 (S5) and ★ T22 (S10). The vectors run are exactly the vectors shown, and each process starts in a fresh EMPTY
     * folder under the scratch area — never the project root or this process's working directory. A vector that is not
     * the client's own is refused, nothing started. Witness: drop {@code .directory(folder)} from the starter — the
     * recorded folder is this process's working directory and the T22 assertion fails.
     */
    @Test
    @DisplayName("★ T10/T22: the vector run is the vector shown, in a fresh empty scratch folder; a foreign vector runs nothing")
    void theShownVectorRunsInAnEmptyFolder(@TempDir Path tmp) throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"), "a POSIX stub");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path record = tmp.resolve("record");
        Path claude = stub(home, "claude", record);
        Path scratch = tmp.resolve("scratch");
        McpRegistrar r = new McpRegistrar(GUI_ENV, home, Optional.of(launcher(home)), () -> scratch);
        assertEquals(Optional.of(claude), r.executable(false), "only ever the stub");
        List<List<String>> shown = r.vectors(false, false);
        assertTrue(r.run(false, shown));
        List<String> lines = Files.readAllLines(record);
        assertEquals(1, lines.size());
        String[] cut = lines.get(0).split("\\|", 2);
        Path folder = Path.of(cut[0]).toRealPath();
        assertEquals(String.join(" ", shown.get(0).subList(1, shown.get(0).size())), cut[1], "★ T10: exactly the shown vector");
        assertTrue(folder.startsWith(scratch.toRealPath()), "★ T22: under the scratch area: " + folder);
        assertNotEquals(Path.of(System.getProperty("user.dir")).toRealPath(), folder, "★ T22: never this process's cwd");
        try (var in = Files.list(folder)) {
            assertEquals(0, in.count(), "★ T22: the folder holds nothing (no .claude/settings.json to read)");
        }

        assertEquals(McpRegistrar.Found.ABSENT, r.check(false), "mcp get, in its own empty folder");
        assertEquals(2, Files.readAllLines(record).size());
        assertNotEquals(folder.toString(), Path.of(Files.readAllLines(record).get(1).split("\\|")[0]).toRealPath()
                .toString(), "a fresh folder per process");

        List<List<String>> foreign = List.of(List.of("/bin/echo", "mcp", "add"));
        assertFalse(r.run(false, foreign), "★ S5: a vector that is not the client's own runs nothing");
        assertEquals(2, Files.readAllLines(record).size());
    }
}
