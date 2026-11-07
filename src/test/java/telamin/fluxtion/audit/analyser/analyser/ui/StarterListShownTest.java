package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.StarterState;
import telamin.fluxtion.audit.analyser.analyser.template.StarterSkills;
import telamin.fluxtion.audit.analyser.analyser.template.Starters;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogue;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogueTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Owner decision 2026-11-07: the Java-authoring starter is the first and recommended starter, and every starter list —
 * the picker, the start page's names, {@code context.starters} — shows only the recommended starters unless "Show all
 * templates" is ticked. These pin the lists AS SHOWN: the picker's model, and the assistant's catalogue.
 *
 * <p>The cause of "the recommended templates are at the bottom": the picker showed the catalogue's raw order (the
 * session's Spring-first rule never reached it) and the catalogue listed the recommended entries late.
 */
class StarterListShownTest {

    @TempDir Path temp;

    @AfterEach
    void untick() {
        StarterListPreference.setShowAll(false);
    }

    static List<String> names(List<TemplateCatalogue.Entry> entries) {
        return entries.stream().map(TemplateCatalogue.Entry::name).toList();
    }

    @Test
    void thePickerShowsJavaAuthoringFirstAndOnlyTheRecommendedUntilTicked() {
        var catalogue = TemplateCatalogue.parse(TemplateCatalogueTest.OLDER, "dev");
        var selection = catalogue.forPicker();
        assertFalse(StarterListPreference.showAll(), "the box starts unticked");
        assertEquals(List.of("Java authoring (Mongoose)", "Audit analyser bundle", "Fluxtion Spring in Mongoose"),
                names(TemplateProjectDialog.shownEntries(selection, StarterListPreference.showAll())));
        StarterListPreference.setShowAll(true);
        assertEquals(List.of("Java authoring (Mongoose)", "Audit analyser bundle", "Fluxtion Spring in Mongoose",
                        "Fluxtion embedded", "Mongoose dynamic feeds"),
                names(TemplateProjectDialog.shownEntries(selection, StarterListPreference.showAll())),
                "ticked: recommended first, then the rest in catalogue order");
    }

    /**
     * The picker's note said "All templates — Recommended starting points are listed first and marked." while, unticked,
     * the list held only the recommended ones: the note must say what the list shows, and change with the tick.
     */
    @Test
    void thePickerNoteSaysWhatTheListShows() {
        var selection = TemplateCatalogue.parse(TemplateCatalogueTest.OLDER, "dev").forPicker();
        String unticked = TemplateProjectDialog.shownNote(selection, false);
        assertFalse(unticked.startsWith("All templates"), unticked);
        assertTrue(unticked.contains(StarterListPreference.LABEL), unticked);
        assertEquals(selection.note(), TemplateProjectDialog.shownNote(selection, true));
        var none = TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[{"name":"A","description":"a","file":"a.starter.json"}]}
                """, "dev").forPicker();
        assertEquals(none.note(), TemplateProjectDialog.shownNote(none, false), "nothing hidden: the catalogue's note");
    }

    @Test
    void aCatalogueThatRecommendsNothingShowsEverything() {
        var catalogue = TemplateCatalogue.parse("""
                {"catalogue":1,"templates":[
                  {"name":"A","description":"a","file":"a.starter.json"},
                  {"name":"B","description":"b","file":"b.starter.json"}
                ]}
                """, "dev");
        assertEquals(List.of("A", "B"), names(TemplateProjectDialog.shownEntries(catalogue.forPicker(), false)));
    }

    @Test
    void theAssistantsCatalogueShowsWhatThePickerShows() {
        var catalogue = TemplateCatalogue.parse(TemplateCatalogueTest.OLDER, "dev");
        List<Starters.Entry> read = new ArrayList<>();
        for (var e : catalogue.templates()) read.add(Starters.of(e, null));
        read = Starters.ordered(read);
        List<StarterState.Entry> held = new ArrayList<>();
        for (var r : read) {
            held.add(new StarterState.Entry(LocalAgentPosture.digest(r.id()), r.authoring(), r.host(), r.mode(),
                    r.keyNeed(), r.recommended()));
        }
        var state = new StarterState(StarterState.READ, 7, held, 0, "", "IDLE", null, 0, 0, null, null, null,
                List.of(), List.of(), false, null);
        var unticked = StarterContext.render(state, read, false);
        assertEquals("recommended", unticked.get("shown"));
        assertEquals(List.of("java-authoring", "analyser-bundle", "fluxtion-spring-mongoose"), ids(unticked));
        var ticked = StarterContext.render(state, read, true);
        assertEquals("all", ticked.get("shown"));
        assertEquals(List.of("java-authoring", "analyser-bundle", "fluxtion-spring-mongoose", "fluxtion-embedded",
                "mongoose-dynamic-feed"), ids(ticked));
    }

    @SuppressWarnings("unchecked")
    static List<String> ids(Map<String, Object> rendered) {
        return ((List<Map<String, Object>>) rendered.get("entries")).stream().map(m -> (String) m.get("id")).toList();
    }

    @Test
    void theJavaAuthoringStarterKeepsItsOwnSkillsAndGetsAJavaDraft() throws Exception {
        Path project = Files.createDirectories(temp.resolve("java-app/.claude/skills/fluxtion-java-graph"));
        Files.writeString(project.resolve("SKILL.md"), "---\nname: fluxtion-java-graph\n---\n");
        var report = new StarterSkills().vendor(temp.resolve("java-app"), Starters.JAVA, "mongoose", null);
        assertNull(report.refusal());
        assertEquals(List.of(), report.written(), "nothing is added to the benchmarked starter");
        assertEquals(List.of("fluxtion-java-graph"), report.kept());
        try (var files = Files.list(temp.resolve("java-app/.claude/skills"))) {
            assertEquals(1, files.count(), "no guided-start, no digest record");
        }
        String draft = AskCatalogue.composeStarter("java-app", "java-authoring", Starters.JAVA).text();
        assertTrue(draft.contains("tools/context.sh") && draft.contains("compiler sign-in"), draft);
        assertFalse(draft.contains("Spring"), draft);
        assertTrue(AskCatalogue.composeStarter("s", "fluxtion-spring", "spring").text().contains("spring-authoring"));
    }
}
