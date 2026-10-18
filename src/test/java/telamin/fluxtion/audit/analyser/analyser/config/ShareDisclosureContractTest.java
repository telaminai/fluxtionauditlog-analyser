package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a share category CARRIES must be what its two disclosure surfaces SAY — the export dialog's
 * checkbox label ({@code Category.label}) and the category table in
 * {@code docs/site/user-guide/projects.md} (it absorbed the old sharing-setups page in the docs restructure). A user ticking a box is consenting to what leaves
 * the machine; an artifact riding a category those surfaces don't mention is undisclosed cargo.
 *
 * <p>This test exists because it happened (review F1, feat/b-m20-3-m26-m27): named focuses joined the
 * GRAPHS category (a sound decision — same lifecycle as saved graphs, and the five-category project
 * scope is pinned) while the label stayed "Graphs" and the docs table stayed graphs-only. Fourth
 * instance of the same drift class (assistant.md's tool count twice, the manifest's verb list, now
 * this); the established remedy is a contract test, not vigilance — {@code ManifestVerbContractTest}
 * is the pattern.
 */
class ShareDisclosureContractTest {

    private static final Path DOC = Path.of("docs/site/user-guide/projects.md");

    /** The admin pages that restate what a shared settings file carries (PR #152 review B1). */
    private static final java.util.List<String> ADMIN_PAGES = java.util.List.of(
            "security.md", "settings.md", "profiles.md", "external-access.md", "managed-policy.md");

    /**
     * Only the page's sharing section counts: projects.md is long, and "provenance" or "focus" elsewhere on it (the
     * environments section, say) must not satisfy a disclosure the category table has to make (phase 6 review).
     */
    static String sharingSection() throws java.io.IOException {
        String page = Files.readString(DOC);
        int at = page.indexOf("## Sharing settings with a file");
        assertTrue(at >= 0, DOC + " lost its '## Sharing settings with a file' section");
        int end = page.indexOf("\n## ", at + 1);
        return end < 0 ? page.substring(at) : page.substring(at, end);
    }

    /** Every checkbox label appears in the docs table — a renamed or new category must update the page. */
    @Test
    void theSharingGuideNamesEveryCategoryByItsDialogLabel() throws Exception {
        String doc = sharingSection();
        for (SettingsShare.Category c : SettingsShare.Category.values()) {
            // labels carry dialog-side phrasing after the name ("View (hidden columns)", "LLM
            // provider/model/…"); the table restates that in its Contents column. The contract here
            // is that the CATEGORY is named at all — full-cargo disclosure for the one category
            // that carries more than its name says is asserted separately below.
            String want = c.label.split("[\\s(]")[0];
            assertTrue(doc.contains(want),
                    "category '" + want + "' (label '" + c.label + "') is not in " + DOC + " — the "
                            + "export dialog and the sharing guide must describe the same categories");
        }
    }

    /** The artifacts GRAPHS actually writes (graph.* and focus.*) are both named on both surfaces. */
    @Test
    void everythingTheGraphsCategoryCarriesIsDisclosed() throws Exception {
        String label = SettingsShare.Category.GRAPHS.label.toLowerCase(java.util.Locale.ROOT);
        assertTrue(label.contains("graph") && label.contains("focus"),
                "GRAPHS exports named graphs AND named focuses; the checkbox said '"
                        + SettingsShare.Category.GRAPHS.label + "' — a user ticking it must be told both");

        String doc = sharingSection().toLowerCase(java.util.Locale.ROOT);
        assertTrue(doc.contains("focus"),
                DOC + " never mentions focuses, but the Graphs category exports them — "
                        + "disclose an artifact in the same change that makes it shareable");
    }

    /**
     * §E — a report's fingerprint can now carry a PROVENANCE string, and provenance names internal
     * systems ("risk-engine · localhost:8081 · ~/dev/risk"). That is business-context cargo riding a
     * category whose row promises "definitions + narrative — never log data", so the row has to say
     * so. Asserted here for the same reason focuses are: disclose an artifact in the change that
     * makes it shareable, not in the one that gets caught.
     */
    @Test
    void theReportsRowDisclosesProvenance() throws Exception {
        String doc = sharingSection().toLowerCase(java.util.Locale.ROOT);
        assertTrue(doc.contains("provenance"),
                DOC + " never mentions provenance, but a shared report's fingerprint carries it — "
                        + "and it names internal systems, which a recipient's checkbox must warn about");
    }

    /**
     * Docs restructure, phase 6 (before P3 made the switches machine tier): the ASSISTANT category carried the External MCP/REST access switch (assistant.rest) and the
     * in-app view switch, not only the caps the old page named. Importing it can open the endpoint an outside client uses,
     * so the page must say so where the categories are listed. Witness: drop "External MCP/REST access" from the page's
     * Assistant row and warning, or stop exporting assistant.rest — the first assertion names which surface moved.
     */
    @Test
    void theAssistantCategoryDisclosesTheExternalAccessSwitch() throws Exception {
        java.util.Properties p = new java.util.Properties();
        AppConfig c = new AppConfig();
        c.assistantActionsRest = true;
        String written = new SettingsShare("/home/x").export(c, java.util.EnumSet.of(SettingsShare.Category.ASSISTANT));
        p.load(new java.io.StringReader(written));
        boolean carriesRest = p.containsKey("assistant.rest");
        String doc = Files.readString(DOC).replaceAll("\\s+", " ");
        if (carriesRest) {
            assertTrue(doc.contains("A shared settings file can turn on External MCP/REST access"),
                    DOC + " must warn that the Assistant category carries the External MCP/REST access switch");
            assertTrue(doc.contains("| Assistant | *External MCP/REST access*"),
                    DOC + "'s Assistant row must name the switch it carries");
        } else {
            assertTrue(!doc.contains("A shared settings file can turn on External MCP/REST access"),
                    "assistant.rest no longer travels: remove the warning from " + DOC);
            assertTrue(!doc.contains("carries External MCP/REST access")
                            && !doc.contains("| Assistant | *External MCP/REST access*"),
                    "assistant.rest no longer travels: " + DOC + "'s Assistant limits row must not name it");
            // PR #152 review B1: the admin pages restate the sharing rule, so they drift with it. Each must not say a
            // shared settings file can turn a switch on, nor that its category carries External MCP/REST access.
            for (String page : ADMIN_PAGES) {
                Path at = Path.of("docs/site/admin", page);
                String text = Files.readString(at).replaceAll("\\s+", " ");
                // "can turn" only where a settings file is the subject: "any user can turn it on for themselves" is true
                for (String stale : java.util.List.of("settings file can turn", "carries External MCP/REST access",
                        "carries *External MCP/REST access*", "One exception travels",
                        "can come from a shared settings file")) {
                    assertTrue(!text.contains(stale), "assistant.rest no longer travels: " + at + " still says '"
                            + stale + "' — a shared settings file carries only the two assistant limits");
                }
            }
        }
    }

    /** The one promise stronger than disclosure: the key never travels, and the page must keep saying so. */
    @Test
    void theApiKeyPromiseStaysOnThePage() throws Exception {
        String doc = sharingSection();
        assertTrue(doc.contains("never the API key"),
                DOC + " must keep stating that the API key is never exported");
    }
}
