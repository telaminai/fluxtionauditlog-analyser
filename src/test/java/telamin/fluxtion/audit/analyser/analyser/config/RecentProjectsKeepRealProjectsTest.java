package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recent-projects list is for projects a person keeps, not for the throwaway copies a bundle is
 * unpacked into. Reported in use, 2026-09-30: seven of ten slots were working copies and the real
 * projects had been pushed off the end.
 */
class RecentProjectsKeepRealProjectsTest {

    private static Path workingCopyProfile(String id) {
        return telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.workingCopiesRoot()
                .resolve("bundle-6f1f46d5f9aa-" + id).resolve("profile").resolve("project.fluxtion-settings");
    }

    @Test
    @DisplayName("opening a bundle does not mint a recent project")
    void aWorkingCopyIsNeverRecorded() {
        List<String> recents = new ArrayList<>();

        ProjectProfile.addRecent(recents, "/w/mine/.analyser/project.fluxtion-settings");
        for (int i = 0; i < 12; i++) ProjectProfile.addRecent(recents, workingCopyProfile("x" + i).toString());

        assertEquals(List.of("/w/mine/.analyser/project.fluxtion-settings"), recents,
                "aBundleOpenUnpacksAFreshCopyEachTime — twelve opens must not cost twelve slots");
    }

    @Test
    @DisplayName("a project a person keeps is still recorded")
    void realProjectsAreKept() {
        List<String> recents = new ArrayList<>();

        ProjectProfile.addRecent(recents, "/w/a/.analyser/project.fluxtion-settings");
        ProjectProfile.addRecent(recents, "/w/b/.analyser/project.reciprocal.fluxtion-settings");

        assertEquals(2, recents.size());
        assertEquals("/w/b/.analyser/project.reciprocal.fluxtion-settings", recents.getFirst(),
                "mostRecentFirst");
    }

    @Test
    @DisplayName("working copies an earlier build recorded are forgotten on load")
    void olderEntriesAreCleanedOnLoad(@TempDir Path tmp) throws Exception {
        Path mine = Path.of("/w/mine/.analyser/project.fluxtion-settings");
        Path cfg = tmp.resolve("config");
        Files.writeString(cfg, "recentProject.count=3\n"
                + "recentProject.0=" + mine.toString().replace(":", "\\:") + "\n"
                + "recentProject.1=" + workingCopyProfile("old1").toString().replace(":", "\\:") + "\n"
                + "recentProject.2=" + workingCopyProfile("old2").toString().replace(":", "\\:") + "\n");

        AppConfig loaded = new ConfigStore(cfg).load();

        assertEquals(List.of(mine.toString()), loaded.recentProjects,
                "theListComesBackWithOnlyTheProjectsYouKeep");
        assertTrue(loaded.recentProjects.stream().noneMatch(p -> p.contains("bundle-")));
    }
}
