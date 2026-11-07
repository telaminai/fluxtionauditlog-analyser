package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAPID-OFF (2026-11-07): the legacy RapidAPI key route to the hosted generator is closed. Builds sign in with the
 * Maven plugin's login ({@link CompilerSignIn#LOGIN}), or {@code FLUXTION_API_TOKEN} and {@code FLUXTION_COMPILER_URL}
 * in CI — so no CURRENT user-facing page or skill may still send a reader to the key route, and every sign-in
 * instruction must be one that works on plugin 1.3.3.
 *
 * <p>Roots: the docs site, the canonical skills, the skills and vendored pages the analyser writes into a project, the
 * current experience-bundle skills — and the string literals of the analyser's own source (the template picker's words
 * are the reason: review M4). Historical records are evidence and stay outside these roots by construction.
 *
 * <p>The terms are matched on a NORMALISED line (lower case; spaces, dots, underscores, hyphens, asterisks and back-ticks
 * removed), so "Rapid API", "-Dfluxtion.apiKey", "FLUXTION_API_KEY" and "Fluxtion API key file" are all caught (review M5).
 * The in-app assistant's own LLM "API key" is a different key and is not a term. The one allowed mention is the
 * sentence that says the route is closed ({@link #ALLOWED}).
 */
class LegacyKeyRouteDocsSweepTest {

    /** Normalised terms: the retired route, its key file, its key property/env/name. */
    static final List<String> TERMS = List.of("rapidapi", "apikeyfile", "fluxtionapikey");
    /** "build key" / "regeneration key" as words (not "build keyless"). */
    static final Pattern KEY_WORDS = Pattern.compile("\\b(build|regeneration)[\\s_-]*keys?\\b");
    /** Stripped before matching: the sentence that announces the closure. */
    static final List<String> ALLOWED = List.of("fluxtion api key route");

    static final List<Path> ROOTS = List.of(Path.of("docs/site"), Path.of("docs/skills"),
            Path.of("src/main/resources/starter-skills"), Path.of("docs/experience/current/skills"));
    static final Path SOURCE = Path.of("src/main/java");
    /** The one source file that must name the legacy file, to check that it exists. */
    static final Path NAMES_THE_FILE = SOURCE.resolve("telamin/fluxtion/audit/analyser/analyser/config/LegacyKeyFile.java");

    static final List<String> HISTORICAL = List.of("handoff", "evidence", "archive", "completed", "runs");
    static final List<String> TEXT = List.of(".md", ".txt", ".json", ".py", ".sh", ".yml", ".yaml", ".html");

    /** {@code path:line: term} for every line that names a retired term; empty when none does. */
    static List<String> hits(Map<String, String> texts) {
        List<String> out = new ArrayList<>();
        texts.forEach((path, text) -> {
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                for (String allowed : ALLOWED) line = line.replace(allowed, "");
                String norm = line.replaceAll("[\\s._*`-]", "");
                for (String term : TERMS) if (norm.contains(term)) out.add(path + ":" + (i + 1) + ": " + term);
                Matcher m = KEY_WORDS.matcher(line);
                if (m.find()) out.add(path + ":" + (i + 1) + ": " + m.group().replaceAll("[\\s_-]+", " "));
            }
        });
        return out;
    }

    /**
     * Review M2: plugin 1.3.3's login fails without a compiler URL, and a CI token fails without
     * {@code FLUXTION_COMPILER_URL}. A paragraph that gives either must give its URL.
     */
    static List<String> incompleteSignIn(Map<String, String> texts) {
        List<String> out = new ArrayList<>();
        texts.forEach((path, text) -> {
            for (String paragraph : text.split("\n\\s*\n")) {
                if (paragraph.contains("fluxtion:login") && !paragraph.contains("fluxtion.compilerUrl"))
                    out.add(path + ": fluxtion:login without -Dfluxtion.compilerUrl");
                if (paragraph.contains("FLUXTION_API_TOKEN") && !paragraph.contains("FLUXTION_COMPILER_URL"))
                    out.add(path + ": FLUXTION_API_TOKEN without FLUXTION_COMPILER_URL");
            }
        });
        return out;
    }

    /**
     * Review B1: AI ▸ Compiler account… does not sign builds in (its credential is in the OS store; the Maven plugin
     * reads ~/.fluxtion/credentials). Phrases that say it does, normalised as {@link #hits} normalises.
     */
    static final List<String> IN_APP_SIGNS_BUILDS = List.of("signinwithaicompileraccount", "useaicompileraccount",
            "compileraccount…signsin", "compileraccountsignsin", "compileraccount…(alsocompileraccountonthestartpage)signs");

    static List<String> inAppClaims(Map<String, String> texts) {
        List<String> out = new ArrayList<>();
        texts.forEach((path, text) -> {
            String norm = text.toLowerCase(Locale.ROOT).replaceAll("[\\s._*`▸-]", "");
            for (String claim : IN_APP_SIGNS_BUILDS) if (norm.contains(claim)) out.add(path + ": " + claim);
        });
        return out;
    }

    static Map<String, String> read() throws IOException {
        Map<String, String> texts = new LinkedHashMap<>();
        for (Path root : ROOTS) {
            assertTrue(Files.isDirectory(root), root + " must exist, or this sweep checks nothing");
            try (Stream<Path> all = Files.walk(root)) {
                for (Path p : all.filter(Files::isRegularFile).sorted().toList()) {
                    String name = p.getFileName().toString();
                    if (TEXT.stream().noneMatch(name::endsWith)) continue;
                    for (Path part : p) {
                        assertTrue(!HISTORICAL.contains(part.toString()),
                                p + " is a historical record inside a current root: move it out, do not exempt it");
                    }
                    texts.put(p.toString(), Files.readString(p));
                }
            }
        }
        return texts;
    }

    /** The string literals of the analyser's own source, one per line, keyed by file. */
    static Map<String, String> sourceLiterals() throws IOException {
        Map<String, String> texts = new LinkedHashMap<>();
        Pattern literal = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
        try (Stream<Path> all = Files.walk(SOURCE)) {
            for (Path p : all.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                if (p.equals(NAMES_THE_FILE)) continue;
                StringBuilder lits = new StringBuilder();
                Matcher m = literal.matcher(Files.readString(p));
                while (m.find()) lits.append(m.group(1)).append('\n');
                texts.put(p.toString(), lits.toString());
            }
        }
        return texts;
    }

    /** Witness: put "Generation through RapidAPI" back into spring-authoring-getting-started.md — this fails. */
    @Test
    @DisplayName("no current page, skill or source string names the retired key route or its key file")
    void noCurrentPageOrSkillNamesTheRetiredRoute() throws IOException {
        Map<String, String> texts = read();
        assertTrue(texts.size() > 50, "the sweep must read the published pages, not silently match nothing: "
                + texts.size());
        List<String> found = new ArrayList<>(hits(texts));
        found.addAll(hits(sourceLiterals()));
        assertEquals(List.of(), found, "the key route is closed: point to the build sign-in (" + CompilerSignIn.LOGIN
                + ") or, in CI, " + CompilerSignIn.CI);
    }

    /** Witness: drop -Dfluxtion.compilerUrl from a login in tutorial-playground.md — this fails. */
    @Test
    @DisplayName("every build sign-in instruction carries its compiler URL")
    void everySignInInstructionWorks() throws IOException {
        Map<String, String> texts = read();
        texts.putAll(sourceLiterals());
        assertEquals(List.of(), incompleteSignIn(texts));
    }

    /** Witness: put "sign in with AI ▸ Compiler account…" back into getting-started.md — this fails. */
    @Test
    @DisplayName("nothing says the in-app compiler account signs builds in")
    void nothingSaysTheInAppAccountSignsBuildsIn() throws IOException {
        Map<String, String> texts = read();
        texts.putAll(sourceLiterals());
        assertEquals(List.of(), inAppClaims(texts));
    }

    @Test
    void witnessTheSweepFindsEverySpelling() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("a.md", "Generation through RapidAPI also needs a key.\nOld docs: the Rapid API route.");
        texts.put("b.md", "Put the key in ~/.fluxtion/fluxtion.apiKeyFile.\nThe Fluxtion API key file.");
        texts.put("c.md", "Pass -Dfluxtion.apiKey=... or FLUXTION_API_KEY.\nBuild key: required at build");
        texts.put("d.md", "The old Fluxtion API key route is closed. Build keyless. Your LLM API key stays local.");
        assertEquals(List.of("a.md:1: rapidapi", "a.md:2: rapidapi", "b.md:1: apikeyfile", "b.md:1: fluxtionapikey", "b.md:2: apikeyfile",
                "b.md:2: fluxtionapikey", "c.md:1: fluxtionapikey", "c.md:2: build key"), hits(texts));
    }

    @Test
    void witnessTheSignInChecksFindTheReviewsDefects() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("a.md", "Sign in with `mvn fluxtion:login`; in CI, set `FLUXTION_API_TOKEN`.");
        texts.put("b.md", "Run " + CompilerSignIn.LOGIN + "; in CI, set " + CompilerSignIn.CI + ".");
        texts.put("c.md", "To regenerate a processor, sign in with AI ▸ Compiler account…");
        texts.put("d.md", "In the analyser, **AI ▸ Compiler account…** signs in the same way.");
        assertEquals(List.of("a.md: fluxtion:login without -Dfluxtion.compilerUrl",
                "a.md: FLUXTION_API_TOKEN without FLUXTION_COMPILER_URL"), incompleteSignIn(texts));
        assertEquals(List.of("c.md: signinwithaicompileraccount", "d.md: compileraccount…signsin"), inAppClaims(texts));
    }
}
