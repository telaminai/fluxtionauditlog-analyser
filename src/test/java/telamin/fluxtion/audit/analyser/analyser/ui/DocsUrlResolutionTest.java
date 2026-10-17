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

    /** Any call of the two docs openers, literal or not, so a non-literal argument cannot slip past the scan. */
    private static final Pattern ANY_CALL = Pattern.compile("\\b(openDocsPage|openUserGuide)\\(([^)]*)\\)");
    /**
     * The non-literal call sites the scan knows, each reviewed: the two declarations and MainFrame's forwarder, which
     * passes on a page that was itself a literal at an openUserGuide call. A new non-literal call fails the test until
     * it is listed here, with the targets it can open checked by hand.
     */
    static final Set<String> NON_LITERAL_ALLOWED = Set.of("openUserGuide(String page)", "openDocsPage(String page)",
            "openDocsPage(page)");

    /**
     * Docs-relative targets ("" is the site root) the application can open, with where each was found. An
     * openUserGuide("") opens the in-app help, not the site, so it is not a target.
     */
    static Map<String, String> targetsInMain() throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        for (Path f : mainSources()) {
            String text = Files.readString(f);
            for (Pattern p : CALLS) {
                Matcher m = p.matcher(text);
                while (m.find()) {
                    if (p.pattern().startsWith("openUserGuide") && m.group(1).isEmpty()) continue;
                    found.putIfAbsent(m.group(1), f.toString());
                }
            }
        }
        return found;
    }

    static List<Path> mainSources() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().matches(".*\\.(java|properties|html|md|txt|json)$")).sorted().toList();
        }
    }

    /** Every call of the docs openers whose argument is neither a string literal nor a reviewed exception. */
    static List<String> unreviewedCalls() throws IOException {
        List<String> out = new ArrayList<>();
        for (Path f : mainSources()) {
            Matcher m = ANY_CALL.matcher(Files.readString(f));
            while (m.find()) {
                String arg = m.group(2).trim();
                String call = m.group(1) + "(" + arg + ")";
                if (arg.matches("\"[^\"]*\"") || NON_LITERAL_ALLOWED.contains(call)) continue;
                out.add(f + ": " + call);
            }
        }
        return out;
    }

    /**
     * The pages the site is built from: tracked by git (a page on disk that git ignores is not published — the
     * restructure's build/ folder was, for one commit), minus mkdocs.yml's exclude_docs.
     */
    static Set<Path> builtPages() throws IOException {
        Set<Path> pages = new LinkedHashSet<>();
        Process git;
        try {
            git = new ProcessBuilder("git", "ls-files", "--", SITE.toString()).redirectErrorStream(true).start();
            String out = new String(git.getInputStream().readAllBytes());
            if (git.waitFor() != 0) throw new IOException("git ls-files failed: " + out);
            for (String line : out.split("\\R")) if (line.endsWith(".md")) pages.add(Path.of(line).normalize());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        boolean in = false;
        for (String line : Files.readAllLines(MKDOCS)) {
            if (line.startsWith("exclude_docs:")) { in = true; continue; }
            if (in) {
                if (!line.startsWith(" ")) break;
                if (!line.isBlank()) pages.remove(SITE.resolve(line.trim()).normalize());
            }
        }
        return pages;
    }

    private static Set<Path> built;

    static boolean isPage(Path p) throws IOException {
        if (built == null) built = builtPages();
        return built.contains(p.normalize());
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
    static Path page(String path) throws IOException {
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (p.isEmpty()) return SITE.resolve("index.md");
        Path direct = SITE.resolve(p + ".md");
        if (isPage(direct)) return direct;
        Path index = SITE.resolve(p).resolve("index.md");
        if (isPage(index)) return index;
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
                if (!isPage(t)) return "redirect " + key + " → " + target + " names no page";
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
        // Not vacuous: the three pages the app is known to open, and the site root (AskPointers.DOCS_URL), are found.
        for (String known : List.of("ai-and-runbooks/", "user-guide/start-page/#prompt-packs", "evidence-bundles/opening/", ""))
            assertTrue(targets.containsKey(known), "the scan no longer finds '" + known + "' in src/main: " + targets.keySet());
        Map<String, String> redirects = redirects();
        List<String> problems = new ArrayList<>();
        for (var e : targets.entrySet()) {
            String p = problem(e.getKey(), redirects);
            if (p != null) problems.add(e.getValue() + ": " + p);
        }
        assertEquals(List.of(), problems);
        assertEquals(List.of(), unreviewedCalls(), "a docs opener called with a non-literal page: check the pages it "
                + "can open, then list it in NON_LITERAL_ALLOWED");
    }

    /**
     * Docs restructure, phase 7: Help ▸ User guide is a page of deep links into the site (help.html), not a second copy of
     * it. Every link must be an address on the site, and must resolve — page, redirect and anchor — as the app's own
     * targets do. Witness: rename a heading a help link anchors to, or delete a page it links without a redirect; the
     * assertion names the link.
     */
    @Test
    void helpPageLinksResolve() throws IOException {
        String help = Files.readString(Path.of("src/main/resources/help/help.html"));
        Matcher m = Pattern.compile("href=\"([^\"]*)\"").matcher(help);
        Map<String, String> redirects = redirects();
        List<String> problems = new ArrayList<>();
        Set<String> linked = new LinkedHashSet<>();
        while (m.find()) {
            String href = m.group(1);
            if (!href.startsWith(BASE)) { problems.add(href + " is not an address on the docs site"); continue; }
            String rel = href.substring(BASE.length());
            linked.add(rel);
            String p = problem(rel, redirects);
            if (p != null) problems.add(p);
        }
        assertTrue(linked.size() >= 40, "the help page should link the site's pages, found " + linked.size());
        for (String door : List.of("start-here/", "diagnose/", "inapp/", "build/", "teach/", "admin/security/",
                "reference/verbs/", "faq/"))
            assertTrue(linked.contains(door), "the help page must link the section page " + door);
        assertEquals(List.of(), problems);
    }

    @Test
    void everyNavPageIsPublished() throws IOException {
        // The page the nav names must be one git tracks: a gitignored page builds locally and 404s once deployed.
        List<String> problems = new ArrayList<>();
        boolean nav = false;
        for (String line : Files.readAllLines(MKDOCS)) {
            if (line.startsWith("nav:")) { nav = true; continue; }
            if (!nav) continue;
            if (!line.startsWith(" ") && !line.isBlank()) break;
            Matcher m = Pattern.compile("([\\w./-]+\\.md)\\s*$").matcher(line);
            if (m.find() && !isPage(SITE.resolve(m.group(1)))) problems.add(m.group(1) + " is in the nav but not published");
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyRedirectLandsOnAPageAndNoRedirectShadowsAPage() throws IOException {
        Map<String, String> redirects = redirects();
        assertFalse(redirects.isEmpty(), "mkdocs.yml has no redirect_maps — moved pages would 404");
        List<String> problems = new ArrayList<>();
        for (var e : redirects.entrySet()) {
            if (isPage(SITE.resolve(e.getKey())))
                problems.add(e.getKey() + " is both a page and a redirect source");
            String target = e.getValue().contains("#") ? e.getValue().substring(0, e.getValue().indexOf('#')) : e.getValue();
            if (target.startsWith("http")) continue;
            if (!isPage(SITE.resolve(target))) problems.add(e.getKey() + " → " + e.getValue() + ": no such page");
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
        assertFalse(isPage(SITE.resolve("assets/README.md")), "exclude_docs is honoured");
        assertTrue(isPage(SITE.resolve("index.md")));
        assertNotNull(problem("moved/", Map.of("moved.md", "nowhere.md")));
        assertEquals("prompt-packs", slug("Prompt packs"));
        assertEquals("is-the-assistants-action-socket-safe-to-enable", slug("Is the assistant's action socket safe to enable?"));
    }
}
