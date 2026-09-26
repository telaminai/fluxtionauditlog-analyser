package telamin.fluxtion.audit.analyser.analyser.session.resume;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionResumeStoreTest {
    @TempDir Path dir;
    @Test void contentChangesWithIdenticalSizeAndTimeStillRefuse() throws Exception {
        Path input = dir.resolve("run.yml"); Files.writeString(input,"first");
        var stamp = Files.getLastModifiedTime(input);
        var store = new SessionResumeStore(dir.resolve("own"));
        var saved = store.capture(SessionResumeStore.NO_PROJECT, List.of(new SessionResumeStore.Input("log",input.toString())), Map.of());
        store.save(saved);
        Files.writeString(input,"other"); Files.setLastModifiedTime(input,stamp);
        assertEquals("content changed",store.check(store.load(SessionResumeStore.NO_PROJECT).orElseThrow()).getFirst().status());
        Files.delete(input);
        assertTrue(store.check(saved).getFirst().status().startsWith("unavailable"));
    }
    @Test void projectsAndOwnSettingsStaySeparateAndRolledOrderSurvives() throws Exception {
        Path a=dir.resolve("a.profile"), b=dir.resolve("b.profile"), log1=dir.resolve("first.yml"),log2=dir.resolve("second.yml");
        for(Path p:List.of(a,b,log1,log2))Files.writeString(p,p.getFileName().toString());
        var store=new SessionResumeStore(dir.resolve("own"));
        String keyA=SessionResumeStore.key(a),keyB=SessionResumeStore.key(b);
        var saved=store.capture(keyA,List.of(new SessionResumeStore.Input("log",log2.toString()),new SessionResumeStore.Input("log",log1.toString())),Map.of("record",3));
        store.save(saved);
        assertTrue(store.load(keyB).isEmpty());assertTrue(store.load(SessionResumeStore.NO_PROJECT).isEmpty());
        assertEquals(List.of(log2.toRealPath().toString(),log1.toRealPath().toString()),store.load(keyA).orElseThrow().inputs().stream().map(SessionResumeStore.Identity::path).toList());
        assertTrue(store.check(saved).stream().allMatch(SessionResumeStore.Check::unchanged));
        Files.delete(a);assertThrows(java.io.IOException.class,()->SessionResumeStore.key(a));
    }
    @Test void aFileMissingAtCaptureDoesNotBecomeVerifiedWhenItAppears() throws Exception {
        Path missing=dir.resolve("later.graphml");var store=new SessionResumeStore(dir.resolve("own"));
        var snapshot=store.capture(SessionResumeStore.NO_PROJECT,List.of(new SessionResumeStore.Input("topology",missing.toString())),Map.of());
        Files.writeString(missing,"new content");
        assertTrue(store.check(snapshot).getFirst().status().startsWith("unverified at capture"));
    }

    /**
     * Edit-loop spec §E, first fixture at the store level: the key is a real path, so a project recreated at
     * that path gets the same key. The profile's creation nonce is what tells them apart — kept by the
     * analyser's own saves, new once the project is deleted and a profile created again. It is a random value
     * written into the profile, so this holds by construction on every OS: no inode, no birth time, no sleep.
     */
    @Test void profileIdentityOutlivesInPlaceSavesButNotARecreatedProfile() throws Exception {
        Path project = Files.createDirectories(dir.resolve("project"));
        Path profile = ProjectProfile.pathFor(project);
        var share = new SettingsShare();
        var config = new AppConfig();
        ProjectProfile.save(profile, config, share);
        String first = ProjectProfile.nonce(profile).orElse(null);
        assertNotNull(first, "a created profile carries a creation nonce");
        config.sourceRoots.add(project.resolve("src").toString());
        assertTrue(ProjectProfile.save(profile, config, share), "a real change is written");
        assertEquals(first, ProjectProfile.nonce(profile).orElse(null), "an ordinary save keeps the profile's identity");

        Path log = Files.writeString(dir.resolve("run.yml"), "x");
        var store = new SessionResumeStore(dir.resolve("own"));
        String key = SessionResumeStore.key(profile);
        store.save(store.capture(key, first, List.of(new SessionResumeStore.Input("log", log.toString())), Map.of()));
        assertEquals(first, store.load(key).orElseThrow().profileIdentity());

        deleteTree(project);
        Files.createDirectories(project);
        ProjectProfile.save(profile, new AppConfig(), share);
        assertEquals(key, SessionResumeStore.key(profile), "same path, same key: the path alone cannot tell them apart");
        String recreated = ProjectProfile.nonce(profile).orElse(null);
        assertNotNull(recreated, "a recreated profile carries its own creation nonce");
        assertNotEquals(first, recreated, "a recreated profile is a different profile");
    }
    @Test void aSnapshotSavedBeforeProfileIdentityLoadsWithoutOne() throws Exception {
        var store = new SessionResumeStore(dir.resolve("own"));
        store.save(store.capture("legacy", List.of(), Map.of()));
        assertNull(store.load("legacy").orElseThrow().profileIdentity());
    }
    private static void deleteTree(Path root) throws java.io.IOException {
        try (var walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
