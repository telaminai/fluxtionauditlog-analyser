package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.LegacyKeyFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAPID-OFF (2026-11-07): the legacy RapidAPI key route to the hosted generator is closed, so the analyser offers no
 * way to manage a Fluxtion API key — no AI-menu item, no dialog, no key store, no start-page card — and the one thing
 * it still does about a leftover key file is notice that it EXISTS, never what it holds.
 *
 * <p>The live menu is compared with {@link MenuInventory} by {@code MenuLayoutFrameTest} under CI's display; this is
 * the headless half, read from source so it runs everywhere.
 */
class LegacyKeyRouteRetiredTest {

    private static final Path MAIN = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser");
    private static final List<String> RETIRED_LABELS = List.of("Fluxtion API key", "Fluxtion key");

    /** Every menu item or start-page chip in {@code source} whose label names the retired key; empty when none does. */
    static List<String> keyItems(String source) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("new\\s+JMenuItem\\s*\\(\\s*\"([^\"]*)\"|chip\\s*\\(\\s*\"([^\"]*)\"").matcher(source);
        while (m.find()) {
            String label = m.group(1) != null ? m.group(1) : m.group(2);
            if (RETIRED_LABELS.stream().anyMatch(label::contains)) out.add(label);
        }
        return out;
    }

    /** Witness: put {@code new JMenuItem("Fluxtion API key…")} back into buildAiMenu — this fails. */
    @Test
    @DisplayName("the AI menu and the start page offer no Fluxtion API key item")
    void noMenuItemOrCardOffersTheKey() throws Exception {
        assertEquals(List.of(), keyItems(Files.readString(MAIN.resolve("ui/MainFrame.java"))));
        assertEquals(List.of(), keyItems(Files.readString(MAIN.resolve("ui/StartPanel.java"))));
        List<String> ai = MenuInventory.MENUS.get("AI");
        assertTrue(ai.contains("Compiler account…"), "sign-in is the route: " + ai);
        assertTrue(ai.stream().noneMatch(i -> RETIRED_LABELS.stream().anyMatch(i::contains)), ai.toString());
    }

    /**
     * Review B1: AI ▸ Compiler account… is the ACCOUNT view; it does not sign builds in (its credential is in the OS
     * store, the Maven plugin reads ~/.fluxtion/credentials). The start-page chip must not claim it regenerates.
     * Witness: put "Sign in through your browser to regenerate a processor" back as the chip's tooltip — this fails.
     */
    @Test
    void theCompilerAccountChipDoesNotClaimToSignBuildsIn() throws Exception {
        String start = Files.readString(MAIN.resolve("ui/StartPanel.java"));
        int chip = start.indexOf("chip(\"Compiler account\"");
        assertTrue(chip > 0, "the Compiler account chip must exist, or this rule checks nothing");
        String call = start.substring(chip, start.indexOf("actions::openCompilerAccount", chip));
        assertFalse(call.toLowerCase().contains("to regenerate"), call);
        assertTrue(call.contains("Builds sign in separately") && call.contains("CompilerSignIn.LOGIN"), call);
    }

    @Test
    void witnessTheScanFindsTheRetiredItem() {
        assertEquals(List.of("Fluxtion API key…"),
                keyItems("JMenuItem fluxtionKey = new JMenuItem(\"Fluxtion API key…\");"));
        assertEquals(List.of("Fluxtion key — not found"), keyItems("x = chip(\"Fluxtion key — not found\", \"\", \"\", go);"));
        assertEquals(List.of(), keyItems("new JMenuItem(\"Compiler account…\")"));
    }

    /** Witness: restore FluxtionKeyStore.java or FluxtionKeyDialog.java — this fails. */
    @Test
    @DisplayName("the key store and its dialog are gone, and settings hold no Fluxtion key")
    void theKeyStoreAndDialogAreGone() {
        assertFalse(Files.exists(MAIN.resolve("config/FluxtionKeyStore.java")));
        assertFalse(Files.exists(MAIN.resolve("ui/FluxtionKeyDialog.java")));
        assertTrue(java.util.Arrays.stream(AppConfig.class.getFields())
                        .noneMatch(f -> f.getName().toLowerCase().contains("fluxtionkey")),
                "AppConfig must not hold a Fluxtion build key (its apiKey is the LLM key)");
    }

    /**
     * What {@code LegacyKeyFile} may do with the file: ONLY ask whether it exists. An allow-list, so a read API this list
     * never thought of (a channel, a mapped buffer, a fully qualified java.io class) is refused too (review M5).
     */
    static List<String> reads(String source) {
        List<String> out = new ArrayList<>();
        Matcher files = Pattern.compile("\\bFiles\\s*\\.\\s*(\\w+)").matcher(source);
        while (files.find())
            if (!List.of("exists", "isRegularFile").contains(files.group(1))) out.add("Files." + files.group(1));
        Matcher imports = Pattern.compile("(?m)^import\\s+([\\w.]+)\\s*;").matcher(source);
        while (imports.find())
            if (!List.of("java.nio.file.Files", "java.nio.file.Path").contains(imports.group(1)))
                out.add("import " + imports.group(1));
        for (String banned : List.of("java.io.", "java.nio.channels", "Channel", "RandomAccessFile", "InputStream",
                "Reader", "Scanner", "Properties", "MappedByteBuffer", "new "))
            if (source.contains(banned)) out.add(banned.strip());
        return out;
    }

    /** Witness: read the file in {@code LegacyKeyFile.presentUnder} (Files.readString) — this fails. */
    @Test
    @DisplayName("the leftover key file is checked for existence only — never opened, read or shown")
    void theLegacyFileIsExistenceOnly(@TempDir Path home) throws Exception {
        assertEquals(List.of(), reads(Files.readString(MAIN.resolve("config/LegacyKeyFile.java"))));
        // the review's bypasses, and their kin: each is refused
        assertEquals(List.of("Files.readString"), reads("Files.readString(file)"));
        assertEquals(List.of("Files.newByteChannel", "Channel"), reads("Files.newByteChannel(f).read(buf)"));
        assertEquals(List.of("Files.lines"), reads("Files.lines(f).count()"));
        assertEquals(List.of("Files.readAllBytes"), reads("Files.readAllBytes(f)"));
        assertEquals(List.of("Files.newInputStream", "InputStream"), reads("Files.newInputStream(f)"));
        assertEquals(List.of("Files.newBufferedReader", "Reader"), reads("Files.newBufferedReader(f)"));
        assertEquals(List.of("Files.size"), reads("Files.size(f)"));
        assertTrue(reads("java.nio.channels.FileChannel.open(f).read(b)").containsAll(List.of("java.nio.channels", "Channel")));
        assertTrue(reads("new java.io.RandomAccessFile(f.toFile(), \"r\")").containsAll(List.of("java.io.", "RandomAccessFile", "new")));
        assertTrue(reads("new java.io.FileInputStream(f.toFile())").contains("InputStream"));
        assertTrue(reads("import java.io.File;\n").contains("import java.io.File"));
        assertFalse(LegacyKeyFile.presentUnder(home));
        Path dir = Files.createDirectories(home.resolve(".fluxtion"));
        Files.writeString(dir.resolve("fluxtion.apiKeyFile"), "a value this test never reads back");
        assertTrue(LegacyKeyFile.presentUnder(home));
        assertFalse(LegacyKeyFile.presentUnder(null));
        // the fact is posted with the start-up facts, through the session (rule 9) — never a status write of its own
        String frame = Files.readString(MAIN.resolve("ui/MainFrame.java"));
        int at = frame.indexOf("LegacyKeyFile.presentUnder(");
        assertTrue(at > 0 && frame.indexOf("LegacyKeyFile.presentUnder(", at + 1) < 0, "one observation site");
        assertTrue(frame.lastIndexOf("AiSetupStartupObserved(", at) > frame.lastIndexOf("private void postStartupFacts()", at),
                "observed inside postStartupFacts, as part of the start-up fact");
    }
}
