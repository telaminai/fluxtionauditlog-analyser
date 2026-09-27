package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #20 — the anchor control offers only ancestors, and says what each one buys.
 *
 * <p>Laid out as the case it exists for: a project with a sibling checkout beside it, whose source
 * root is stored absolute and therefore breaks on a colleague's machine. The question the control
 * has to answer is "which of these depths fixes that", and it has to answer it without the person
 * knowing what {@code ../..} means for their own layout.
 */
class WorkspaceAnchorChoicesTest {

    private static final String HOME = "/home/tester";
    private static final Path PROJECT = Path.of("/home/tester/work/maker");
    /** Beside the project, under the same parent — the monorepo neighbour PathForm was written for. */
    private static final String SIBLING = "/home/tester/work/shared-lib/src/main/java";
    private static final String INSIDE = "/home/tester/work/maker/src/main/java";
    /** Two levels up, outside `work` entirely. */
    private static final String FAR = "/home/tester/other/thing/src/main/java";

    private static WorkspaceAnchorChoices.Choice byAnchor(List<WorkspaceAnchorChoices.Choice> all, String anchor) {
        return all.stream().filter(c -> c.anchor().equals(anchor)).findFirst()
                .orElseThrow(() -> new AssertionError("no choice for '" + anchor + "' in " + all));
    }

    // ---- only ancestors -------------------------------------------------------------------------

    @Test
    @DisplayName("Every offered value is one refuseWorkspaceRoot accepts")
    void onlyStorableValuesAreOffered() {
        for (var choice : WorkspaceAnchorChoices.forProject(PROJECT, List.of(INSIDE), HOME)) {
            assertTrue(PathForm.refuseWorkspaceRoot(choice.anchor()).isEmpty(),
                    "offered '" + choice.anchor() + "' but PathForm refuses it: "
                            + PathForm.refuseWorkspaceRoot(choice.anchor()).orElse(""));
        }
    }

    @Test
    @DisplayName("The ladder is none plus six levels, no deeper")
    void sixLevelsIsTheLimit() {
        var deep = WorkspaceAnchorChoices.forProject(
                Path.of("/a/b/c/d/e/f/g/h/i/project"), List.of(), HOME);

        assertEquals(1 + WorkspaceAnchorChoices.MAX_LEVELS, deep.size());
        assertEquals("", deep.get(0).anchor());
        assertEquals("..", deep.get(1).anchor());
        assertEquals("../../../../../..", deep.get(deep.size() - 1).anchor(),
                "the deepest PathForm's pattern allows — one more would be refused on save");
    }

    @Test
    @DisplayName("The filesystem root is never offered as a workspace")
    void itDoesNotClimbPastTheTop() {
        assertEquals(1, WorkspaceAnchorChoices.forProject(Path.of("/project"), List.of(), HOME).size(),
                "only '(none)': the one ancestor of /project is / itself");

        var two = WorkspaceAnchorChoices.forProject(Path.of("/work/project"), List.of(), HOME);
        assertEquals(List.of("", ".."), two.stream().map(WorkspaceAnchorChoices.Choice::anchor).toList(),
                "'..' is /work, which is a workspace; '../..' would be / and is not");
    }

    /**
     * The reason the filesystem root is excluded rather than just unhelpful: every absolute path
     * starts with it, so it would report a perfect score while writing the machine's whole layout
     * into the profile as a run of '..' steps — a value that looks like the best answer in the list
     * and is the worst one in the file.
     */
    @Test
    @DisplayName("Anchoring at / would score perfectly and be nonsense — so it is not on offer")
    void theFilesystemRootWouldScorePerfectly() {
        assertEquals(0, WorkspaceAnchorChoices.portableCount(
                        Path.of("/work/project"), List.of("/somewhere/else/src"), "..", HOME),
                "precondition: '..' is /work, which does NOT cover a root outside it");
        assertEquals(1, WorkspaceAnchorChoices.portableCount(
                        Path.of("/work/project"), List.of("/somewhere/else/src"), "../..", HOME),
                "but anchoring at / would — which is why forProject never offers it");

        assertTrue(WorkspaceAnchorChoices.forProject(Path.of("/work/project"), List.of(), HOME)
                        .stream().noneMatch(c -> "../..".equals(c.anchor())));
    }

    @Test
    @DisplayName("'.' is not offered — it resolves to the project root and changes nothing")
    void dotIsNotOffered() {
        var all = WorkspaceAnchorChoices.forProject(PROJECT, List.of(INSIDE), HOME);

        assertTrue(all.stream().noneMatch(c -> ".".equals(c.anchor())), all.toString());
    }

    @Test
    @DisplayName("…but a profile that already stores one is still shown it")
    void aStoredValueIsAlwaysPresent() {
        var all = WorkspaceAnchorChoices.withCurrent(PROJECT, List.of(INSIDE), HOME, ".");

        assertEquals(".", byAnchor(all, ".").anchor(),
                "hiding a value someone is using would present a different setting from the stored one");
    }

    // ---- and says what each one buys --------------------------------------------------------------

    @Test
    @DisplayName("Each choice counts the roots it would make portable")
    void eachChoiceStatesItsConsequence() {
        List<String> roots = List.of(INSIDE, SIBLING, FAR);

        var all = WorkspaceAnchorChoices.forProject(PROJECT, roots, HOME);

        assertEquals(1, byAnchor(all, "").portable(),
                "with no anchor only the root INSIDE the project is portable");
        assertEquals(2, byAnchor(all, "..").portable(),
                "one level up covers the sibling checkout — the case the anchor exists for");
        assertEquals(3, byAnchor(all, "../..").portable(),
                "two levels up also covers the one outside 'work'");
    }

    @Test
    @DisplayName("The label carries the depth, where it lands, and the count")
    void theLabelIsReadableWithoutKnowingTheLayout() {
        var all = WorkspaceAnchorChoices.forProject(PROJECT, List.of(INSIDE, SIBLING), HOME);

        String label = byAnchor(all, "..").label();

        assertTrue(label.startsWith(".."), label);
        assertTrue(label.contains("work"), "names the directory it resolves to: " + label);
        assertTrue(label.contains("2 of 2 roots portable"), label);
        assertEquals("(none)", byAnchor(all, "").label().split(" ")[0],
                "the absent state is spelled, not blank: " + byAnchor(all, "").label());
    }

    @Test
    @DisplayName("A deeper anchor never makes FEWER roots portable")
    void theCountIsMonotonic() {
        var all = WorkspaceAnchorChoices.forProject(PROJECT, List.of(INSIDE, SIBLING, FAR), HOME);

        for (int i = 1; i < all.size(); i++) {
            assertTrue(all.get(i).portable() >= all.get(i - 1).portable(),
                    "climbing from '" + all.get(i - 1).anchor() + "' to '" + all.get(i).anchor()
                            + "' lost a root — the list would then be unorderable by the thing it is "
                            + "sorted on, and 'pick the smallest that covers your roots' would be wrong");
        }
    }

    @Test
    @DisplayName("With no roots the label states the depth and nothing it cannot know")
    void noRootsMeansNoCount() {
        var all = WorkspaceAnchorChoices.forProject(PROJECT, List.of(), HOME);

        assertFalse(byAnchor(all, "..").label().contains("portable"),
                "'0 of 0 roots portable' is noise: " + byAnchor(all, "..").label());
    }

    @Test
    @DisplayName("With no project there is nothing to anchor to")
    void noProjectMeansOnlyTheEmptyChoice() {
        var all = WorkspaceAnchorChoices.forProject(null, List.of(INSIDE), HOME);

        assertEquals(1, all.size());
        assertTrue(all.get(0).isNone());
    }
}
