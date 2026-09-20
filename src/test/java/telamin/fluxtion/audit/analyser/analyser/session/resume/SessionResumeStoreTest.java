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
}
