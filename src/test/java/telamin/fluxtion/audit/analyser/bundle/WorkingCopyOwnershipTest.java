package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class WorkingCopyOwnershipTest {
    @TempDir Path home;
    String previousHome;
    Path root;
    @BeforeEach void isolate() throws Exception {
        previousHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        root = EvidenceBundle.workingCopiesRoot();
        Files.createDirectories(root);
    }
    @AfterEach void restoreHome() { System.setProperty("user.home", previousHome); }
    Path copy() throws Exception {
        Path copy = Files.createTempDirectory(root, "bundle-DEMO-");
        Files.writeString(copy.resolve("DEMO.txt"), "DEMO evidence");
        return copy;
    }
    @Test void anActiveOrPendingLeaseSurvivesCleanup() throws Exception {
        Path copy = copy();
        try (var lease = WorkingCopyOwnership.create(copy)) {
            assertEquals(0, EvidenceBundle.reap(List.of(copy), null), "activeLeaseMustPreventDeletion");
            assertEquals("DEMO evidence", Files.readString(copy.resolve("DEMO.txt")), "pendingInputMustRemainReadable");
        }
        assertEquals(1, EvidenceBundle.reap(List.of(copy), null), "releasedLeaseMustPermitCleanup");
        assertFalse(Files.exists(copy), "releasedCopyIsRemoved");
    }
    @Test void eachWindowKeepsItsOwnReference() throws Exception {
        Path copy = copy();
        try (var first = new WorkingCopyScope(); var second = new WorkingCopyScope()) {
            first.add(WorkingCopyOwnership.create(copy));
            second.hold(copy.resolve("DEMO.txt"));
            first.close();
            assertEquals(0, EvidenceBundle.reap(List.of(copy), null), "otherWindowMustKeepItsLease");
            assertFalse(second.retain(List.of(copy.resolve("DEMO.txt"))), "liveLogKeepsItsCopyWithoutAProject");
            assertTrue(second.retain(List.of()), "settledCloseReleasesTheResource");
            assertEquals(1, EvidenceBundle.reap(List.of(copy), null), "lastWindowClosedAllowsCleanup");
        }
    }
    @Test void unknownAndForeignCopiesAreKept() throws Exception {
        Path legacy = copy(), foreign = copy();
        Files.writeString(foreign.resolve(WorkingCopyOwnership.MARKER), "DEMO-other-host");
        assertEquals(0, EvidenceBundle.reap(List.of(legacy, foreign), null), "unknownOwnershipMustNeverBeDeleted");
        assertTrue(Files.exists(legacy.resolve("DEMO.txt")), "legacyEvidenceIsKept");
        assertTrue(Files.exists(foreign.resolve("DEMO.txt")), "foreignEvidenceIsKept");
    }
    @Test void cleanupCannotDeleteTheRootNestedOrLinkedDirectories() throws Exception {
        Path copy = copy(), nested = Files.createDirectories(copy.resolve("bundle-DEMO-nested"));
        try (var lease = WorkingCopyOwnership.create(copy)) { }
        try (var lease = WorkingCopyOwnership.create(nested)) { }
        Path alias = root.resolve("bundle-DEMO-link");
        Files.createSymbolicLink(alias, copy);
        // Even a marker at the root cannot make the root an eligible working copy.
        Files.writeString(root.resolve(WorkingCopyOwnership.MARKER), BundleWriter.HOST);
        assertEquals(0, EvidenceBundle.reap(List.of(alias, nested, root), null), "onlyDirectRealCopiesAreEligible");
        assertTrue(Files.exists(copy.resolve("DEMO.txt")), "rootAndLinkAttackMustLeaveEvidence");
    }
    @Test void anotherProcessProtectsTheCopyAndOurOwnLeaseSurvivesAProbe() throws Exception {
        Path copy = copy();
        try (var lease = WorkingCopyOwnership.create(copy)) { }
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.home=" + home, "-cp", System.getProperty("java.class.path"),
                Child.class.getName(), copy.toString()).redirectErrorStream(true).start();
        try {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream()));
            var ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try { return reader.readLine(); } catch (java.io.IOException e) { throw new RuntimeException(e); }
            });
            assertEquals("READY", ready.get(15, TimeUnit.SECONDS), "childOwnsTheCopyBeforeCleanup");
            assertEquals(0, EvidenceBundle.reap(List.of(copy), null), "anotherProcessMustPreventDeletion");
            try (var own = WorkingCopyOwnership.forPath(copy)) {
                assertEquals(0, EvidenceBundle.reap(List.of(copy), null), "probingOurOwnLeaseMustNotUnlockIt");
                child.getOutputStream().write('\n'); child.getOutputStream().flush();
                assertTrue(child.waitFor(15, TimeUnit.SECONDS), "childClosesItsLease");
                assertEquals(0, child.exitValue(), "childExitedCleanly");
                assertEquals(0, EvidenceBundle.reap(List.of(copy), null), "remainingLocalReaderKeepsTheCopy");
            }
            assertEquals(1, EvidenceBundle.reap(List.of(copy), null), "bothProcessesFinishedAllowsCleanup");
        } finally {
            child.destroyForcibly();
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "noChildProcessLeftBehind");
        }
    }
    public static class Child {
        public static void main(String[] args) throws Exception {
            try (var lease = WorkingCopyOwnership.forPath(Path.of(args[0]))) {
                if (lease == null) throw new IllegalStateException("no lease");
                System.out.println("READY"); System.out.flush();
                System.in.read();
            }
        }
    }
}
