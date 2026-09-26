package telamin.fluxtion.audit.analyser.analyser.session.resume;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
     * that path gets the same key. The profile identity is what tells them apart — stable across the
     * analyser's own in-place saves, different once the file is deleted and written again.
     */
    @Test void profileIdentityOutlivesInPlaceSavesButNotARecreatedProfile() throws Exception {
        Path project = Files.createDirectories(dir.resolve("project"));
        Path profile = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(project);
        var share = new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare();
        var config = new telamin.fluxtion.audit.analyser.analyser.config.AppConfig();
        telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile, config, share);
        String first = SessionResumeStore.profileIdentity(profile);
        config.sourceRoots.add(project.resolve("src").toString());
        telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile, config, share);
        assertEquals(first, SessionResumeStore.profileIdentity(profile), "an ordinary save keeps the profile's identity");

        Path log = Files.writeString(dir.resolve("run.yml"), "x");
        var store = new SessionResumeStore(dir.resolve("own"));
        String key = SessionResumeStore.key(profile);
        store.save(store.capture(key, first, List.of(new SessionResumeStore.Input("log", log.toString())), Map.of()));
        assertEquals(first, store.load(key).orElseThrow().profileIdentity());

        deleteTree(project);
        Thread.sleep(20);                                   // a distinct creation instant where birth time is reported
        Files.createDirectories(project);
        telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile, new telamin.fluxtion.audit.analyser.analyser.config.AppConfig(), share);
        assertEquals(key, SessionResumeStore.key(profile), "same path, same key: the path alone cannot tell them apart");
        assertNotEquals(first, SessionResumeStore.profileIdentity(profile), "a recreated profile is a different file");
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
