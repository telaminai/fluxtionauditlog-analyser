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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAPID-OFF (2026-11-07): the legacy RapidAPI key route to the hosted generator is closed. Generation goes only through
 * compiler sign-in — AI ▸ Compiler account…, {@code mvn fluxtion:login}, {@code FLUXTION_API_TOKEN} in CI — so no
 * CURRENT user-facing page or skill may still send a reader to the route or to its key file.
 *
 * <p>The sweep covers what a person or an agent is told today: the docs site, the canonical skills, the skills and
 * vendored pages the analyser writes into a project, and the current experience-bundle skills. Historical records
 * (handoff, evidence, archive, completed, experience runs) are evidence and stay as they were, so they are outside
 * these roots by construction, and {@link #HISTORICAL} refuses one that ever moves inside.
 */
class LegacyKeyRouteDocsSweepTest {

    /** The terms, case-insensitive: the retired route's name and its key file's name. */
    static final List<String> TERMS = List.of("rapidapi", "apikeyfile");

    static final List<Path> ROOTS = List.of(Path.of("docs/site"), Path.of("docs/skills"),
            Path.of("src/main/resources/starter-skills"), Path.of("docs/experience/current/skills"));

    static final List<String> HISTORICAL = List.of("handoff", "evidence", "archive", "completed", "runs");

    /** {@code path: term} for every text that names a retired term; empty when none does. */
    static List<String> hits(Map<String, String> texts) {
        List<String> out = new ArrayList<>();
        texts.forEach((path, text) -> {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String term : TERMS) if (lower.contains(term)) out.add(path + ": " + term);
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
                    if (!(name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".json")
                            || name.endsWith(".py") || name.endsWith(".sh") || name.endsWith(".yml"))) continue;
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

    /** Witness: put "Generation through RapidAPI" back into spring-authoring-getting-started.md — this fails. */
    @Test
    @DisplayName("no current page or skill names the retired key route or its key file")
    void noCurrentPageOrSkillNamesTheRetiredRoute() throws IOException {
        Map<String, String> texts = read();
        assertTrue(texts.size() > 50, "the sweep must read the published pages, not silently match nothing: "
                + texts.size());
        assertEquals(List.of(), hits(texts),
                "the key route is closed: point to AI ▸ Compiler account…, mvn fluxtion:login or FLUXTION_API_TOKEN");
    }

    @Test
    void witnessTheSweepFindsBothTermsInAnyCase() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("a.md", "Generation through RapidAPI also needs a key.");
        texts.put("b.md", "Put the key in ~/.fluxtion/fluxtion.apiKeyFile.");
        texts.put("c.md", "Sign in with AI ▸ Compiler account… or mvn fluxtion:login.");
        assertEquals(List.of("a.md: rapidapi", "b.md: apikeyfile"), hits(texts));
    }
}
