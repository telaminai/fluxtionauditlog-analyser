package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every docs-site URL the application opens resolves to a page the site builds, or to a redirect the site builds,
 * and every anchor it names is a heading on the page it lands on (docs restructure, 2026-10-17).
 *
 * <p>Headless and static (CLAUDE.md rule 8): it reads {@code src/main}, {@code docs/site} and {@code mkdocs.yml}
 * rather than running MkDocs, so a page moved without a redirect — or a heading renamed under an anchor the app opens —
 * fails here, before a person presses the button and lands on a 404.
 */
class DocsUrlResolutionTest {
    static final Path SITE = Path.of("docs/site");
    static final Path MKDOCS = Path.of("mkdocs.yml");
    static final String BASE = "https://telaminai.github.io/fluxtionauditlog-analyser/";

    private static final List<Pattern> CALLS = List.of(
            Pattern.compile("openDocsPage\\(\\s*\"([^\"]*)\"\\s*\\)"),
            Pattern.compile("openUserGuide\\(\\s*\"([^\"]*)\"\\s*\\)"),
            Pattern.compile(Pattern.quote(BASE) + "([^\"\\s<>)']*)"));

    /** Docs-relative targets ("" is the site root) the application can open, with where each was found. */
    static Map<String, String> targetsInMain() throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            for (Path f : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().matches(".*\\.(java|properties|html|md|txt|json)$")).toList()) {
                String text = Files.readString(f);
                for (Pattern p : CALLS) {
                    Matcher m = p.matcher(text);
                    while (m.find()) found.putIfAbsent(m.group(1), f.toString());
                }
            }
        }
        return found;
    }

    /** {@code redirect_maps} of the redirects plugin in mkdocs.yml: source page → target page. */
    static Map<String, String> redirects() throws IOException {
        Map<String, String> maps = new LinkedHashMap<>();
        boolean in = false;
        int indent = -1;
        for (String line : Files.readAllLines(MKDOCS)) {
            if (line.trim().equals("redirect_maps:")) { in = true; indent = line.indexOf('r'); continue; }
            if (!in) continue;
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            int lead = line.length() - line.stripLeading().length();
            if (lead <= indent) break;
            Matcher m = Pattern.compile("^\\s*['\"]?([^'\":]+?)['\"]?\\s*:\\s*['\"]?([^'\"#]+?)['\"]?\\s*(#.*)?$").matcher(line);
            if (m.matches()) maps.put(m.group(1).trim(), m.group(2).trim());
        }
        return maps;
    }

    /** The source page a docs-relative URL path ("", "a/", "a/b/") is built from, if the site has one. */
    static Path page(String path) {
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (p.isEmpty()) return SITE.resolve("index.md");
        Path direct = SITE.resolve(p + ".md");
        if (Files.isRegularFile(direct)) return direct;
        Path index = SITE.resolve(p).resolve("index.md");
        if (Files.isRegularFile(index)) return index;
        return null;
    }

    /** The redirect source key for a URL path, as redirect_maps spells it. */
    static List<String> redirectKeys(String path) {
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (p.isEmpty()) return List.of("index.md");
        return List.of(p + ".md", p + "/index.md");
    }

    /** Heading anchors MkDocs' toc extension makes for a page (its default slugify; {#id} attributes honoured). */
    static Set<String> anchors(Path md) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        boolean fence = false;
        for (String line : Files.readAllLines(md)) {
            String t = line.stripLeading();
            if (t.startsWith("```") || t.startsWith("~~~")) { fence = !fence; continue; }
            if (fence || !t.startsWith("#")) continue;
            Matcher h = Pattern.compile("^#{1,6}\\s+(.*?)\\s*(\\{#([^}]+)\\})?\\s*#*\\s*$").matcher(t);
            if (!h.matches()) continue;
            if (h.group(3) != null) { out.add(h.group(3)); continue; }
            out.add(slug(h.group(1)));
        }
        return out;
    }

    static String slug(String heading) {
        String s = heading.replaceAll("`", "").replaceAll("\\*\\*|__", "").replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("<[^>]+>", "");
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD).replaceAll("[^\\x00-\\x7F]", "");
        s = s.replaceAll("[^\\w\\s-]", "").trim().toLowerCase(Locale.ROOT);
        return s.replaceAll("[-\\s]+", "-");
    }

    /** Where a docs-relative URL lands: the page and its anchor, following one redirect; or a reason it does not. */
    static String problem(String url, Map<String, String> redirects) throws IOException {
        String path = url, anchor = null;
        int hash = url.indexOf('#');
        if (hash >= 0) { path = url.substring(0, hash); anchor = url.substring(hash + 1); }
        Path landed = page(path);
        if (landed == null) {
            for (String key : redirectKeys(path)) {
                String target = redirects.get(key);
                if (target == null) continue;
                String tPage = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
                Path t = SITE.resolve(tPage);
                if (!Files.isRegularFile(t)) return "redirect " + key + " → " + target + " names no page";
                landed = t;
                break;
            }
        }
        if (landed == null) return "no page and no redirect for '" + path + "'";
        if (anchor != null && !anchor.isEmpty() && !anchors(landed).contains(anchor))
            return "'" + url + "' lands on " + SITE.relativize(landed) + ", which has no heading #" + anchor;
        return null;
    }

    @Test
    void everyDocsUrlTheAppOpensResolves() throws IOException {
        Map<String, String> targets = targetsInMain();
        // Not vacuous: the three pages the app is known to open, and the site root, are found by the scan.
        for (String known : List.of("ai-and-runbooks/", "user-guide/start-page/#prompt-packs", "evidence-bundles/opening/", ""))
            assertTrue(targets.containsKey(known), "the scan no longer finds '" + known + "' in src/main: " + targets.keySet());
        Map<String, String> redirects = redirects();
        List<String> problems = new ArrayList<>();
        for (var e : targets.entrySet()) {
            String p = problem(e.getKey(), redirects);
            if (p != null) problems.add(e.getValue() + ": " + p);
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyRedirectLandsOnAPageAndNoRedirectShadowsAPage() throws IOException {
        Map<String, String> redirects = redirects();
        assertFalse(redirects.isEmpty(), "mkdocs.yml has no redirect_maps — moved pages would 404");
        List<String> problems = new ArrayList<>();
        for (var e : redirects.entrySet()) {
            if (Files.isRegularFile(SITE.resolve(e.getKey())))
                problems.add(e.getKey() + " is both a page and a redirect source");
            String target = e.getValue().contains("#") ? e.getValue().substring(0, e.getValue().indexOf('#')) : e.getValue();
            if (target.startsWith("http")) continue;
            if (!Files.isRegularFile(SITE.resolve(target))) problems.add(e.getKey() + " → " + e.getValue() + ": no such page");
            else if (e.getValue().contains("#")) {
                String a = e.getValue().substring(e.getValue().indexOf('#') + 1);
                if (!anchors(SITE.resolve(target)).contains(a)) problems.add(e.getKey() + " → " + e.getValue() + ": no such heading");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void theCheckerCatchesAMissingPageAndAMissingAnchor() throws IOException {
        // Wrong-result witnesses (rule 8): the checker must reject what it is meant to reject.
        assertNotNull(problem("no-such-page/", Map.of()));
        assertNotNull(problem("user-guide/start-page/#no-such-heading", Map.of()));
        assertNull(problem("user-guide/start-page/#prompt-packs", Map.of()));
        assertNull(problem("moved/", Map.of("moved.md", "index.md")));
        assertNotNull(problem("moved/", Map.of("moved.md", "nowhere.md")));
        assertEquals("prompt-packs", slug("Prompt packs"));
        assertEquals("is-the-assistants-action-socket-safe-to-enable", slug("Is the assistant's action socket safe to enable?"));
    }
}
