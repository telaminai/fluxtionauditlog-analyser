package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a share category CARRIES must be what its two disclosure surfaces SAY — the export dialog's
 * checkbox label ({@code Category.label}) and the category table in
 * {@code docs/site/user-guide/sharing-setups.md}. A user ticking a box is consenting to what leaves
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

    private static final Path DOC = Path.of("docs/site/user-guide/sharing-setups.md");

    /** Every checkbox label appears in the docs table — a renamed or new category must update the page. */
    @Test
    void theSharingGuideNamesEveryCategoryByItsDialogLabel() throws Exception {
        String doc = Files.readString(DOC);
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

        String doc = Files.readString(DOC).toLowerCase(java.util.Locale.ROOT);
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
        String doc = Files.readString(DOC).toLowerCase(java.util.Locale.ROOT);
        assertTrue(doc.contains("provenance"),
                DOC + " never mentions provenance, but a shared report's fingerprint carries it — "
                        + "and it names internal systems, which a recipient's checkbox must warn about");
    }

    /** The one promise stronger than disclosure: the key never travels, and the page must keep saying so. */
    @Test
    void theApiKeyPromiseStaysOnThePage() throws Exception {
        String doc = Files.readString(DOC);
        assertTrue(doc.contains("never the API key"),
                DOC + " must keep stating that the API key is never exported");
    }
}
