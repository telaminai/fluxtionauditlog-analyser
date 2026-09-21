package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DesignWorkspaceTest {
    @TempDir Path project;
    SessionDriver driver;
    DesignFiles files;
    DesignWorkspace workspace;
    @BeforeEach void setup() {
        driver = new SessionDriver(effect -> { throw new AssertionError("design facts must not perform session effects"); });
        files = new DesignFiles(List.of(project.toString()), project);
        workspace = new DesignWorkspace(() -> files, () -> driver.processor().designSession, driver::submit);
    }
    Path xml(String name, String text) throws Exception { return Files.writeString(project.resolve(name), text); }
    @Test void producerSnapshotDetectsNewReceiptAndEditedSourceWithoutReopening() throws Exception {
        Path target=Files.createDirectories(project.resolve("target"));
        Path sources=Files.createDirectories(project.resolve("src/main/java"));
        Path java=Files.writeString(sources.resolve("Node.java"),"class Node {}\n");
        Files.writeString(project.resolve("fluxtion-authoring.json"),"{\"sourceRoot\":\"src/main/java\"}");
        Path receipt=Files.writeString(target.resolve("fluxtion-run.json"),"{\"schemaVersion\":\"1.0\",\"stages\":{\"build\":{\"outcome\":\"ok\",\"compilerRan\":true}}}");
        Path result=Files.writeString(target.resolve("fluxtion-validation.json"),"{\"contractVersion\":\"1.0\",\"valid\":true,\"diagnosticReport\":{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}}");
        var loaded=workspace.diagnostics(result.toString());
        assertEquals("unchanged-metadata",ProducerResult.object(loaded.relationship(null).get("freshness")).get("state"));
        Files.writeString(receipt,Files.readString(receipt)+"\n ");
        assertEquals("changed-on-disk",ProducerResult.object(loaded.relationship(null).get("freshness")).get("state"),"rewritten receipt must be disclosed");
        assertTrue(loaded.description(null).contains("reopen diagnostics"));
        assertTrue(loaded.description(null).contains("as of intake"));
        loaded=workspace.diagnostics(result.toString());
        Files.writeString(java,"class Node { int value; }\n");
        var inputs=ProducerResult.object(loaded.relationship(null).get("inputChecks"));
        assertTrue(inputs.get("buildSourceHash").toString().startsWith("stale-check"),"old source match must expire");
        assertSame(loaded,driver.processor().designSession.result(),"observation does not replace loaded result");
    }

    @Test void refusedDesignNamesExactRootCallWithoutAddingIt() throws Exception {
        Path external = Files.createDirectories(project.resolve("needs root"));
        Path target = Files.writeString(external.resolve("design.xml"), "<beans/>");
        var restricted = new DesignFiles(List.of(), project);
        String msg = assertThrows(java.io.IOException.class, () -> restricted.resolve(target.toString())).getMessage();
        String call = telamin.fluxtion.audit.analyser.analyser.llm.Json.write(Map.of("add", List.of(external.toRealPath().toString())));
        assertTrue(msg.contains("source_root " + call), msg);
        assertTrue(restricted.roots().isEmpty());
        assertEquals(target.toRealPath(), new DesignFiles(List.of(external.toString()), project).resolve(target.toString()));
        assertFalse(assertThrows(java.io.IOException.class, () -> restricted.resolve(external.resolve("absent.xml").toString()))
                .getMessage().contains("source_root"), "missing files must not promise a root will fix them");
    }
    @Test void aGlanceAtBDoesNotChangeSessionAAndEveryCallRereads() throws Exception {
        Path a = xml("a.xml", "<beans><bean id='x'/></beans>");
        Path b = xml("b.xml", "<beans>\n\n<bean id='x'/></beans>");
        workspace.open(a.toString());
        assertEquals(3, workspace.source(Map.of("file", b.toString(), "bean", "x")).line());
        assertEquals(a.toRealPath().toString(), driver.processor().designSession.document().file());
        Files.writeString(a, "<beans>\n<bean id='x'/></beans>");
        assertEquals(2, workspace.source(Map.of("bean", "x")).line());
    }
    @Test void refusesMixedSelectorsAndDuplicateIds() throws Exception {
        workspace.open(xml("a.xml", "<beans>\n<bean id='x'/><bean id='x'/></beans>").toString());
        assertThrows(java.io.IOException.class, () -> workspace.source(Map.of("bean", "x", "line", 1)));
        assertThrows(java.io.IOException.class, () -> workspace.source(Map.of("method", "go")));
        assertTrue(assertThrows(java.io.IOException.class, () -> workspace.source(Map.of("bean", "x"))).getMessage().contains("ambiguous"));
        assertEquals(1, workspace.source(Map.of("line", 1)).line());
    }
    @Test void parseFailureRetainsLastGoodRevisionAndLaterGoodReadRecovers() throws Exception {
        Path a = xml("a.xml", "<beans><bean id='x'/></beans>"); workspace.open(a.toString());
        String revision = driver.processor().designSession.document().revision();
        Files.writeString(a, "<beans><bean");
        workspace.refreshed(DesignWorkspace.read(files, a.toString(), false, driver.processor().designSession.generation()));
        assertEquals(revision, driver.processor().designSession.document().revision());
        assertTrue(driver.processor().designSession.error().contains("parse error"));
        Files.writeString(a, "<beans><bean id='y'/></beans>");
        workspace.refreshed(DesignWorkspace.read(files, a.toString(), false, driver.processor().designSession.generation()));
        assertEquals(List.of("y"), driver.processor().designSession.document().beanIds());
        assertEquals("", driver.processor().designSession.error());
    }
    @Test void lateFollowCannotRestoreAClearedSessionOrOverwriteANewerRead() throws Exception {
        Path a = xml("a.xml", "<beans><bean id='x'/></beans>"); workspace.open(a.toString());
        var old = DesignWorkspace.read(files, a.toString(), false, driver.processor().designSession.generation());
        Files.writeString(a, "<beans><bean id='newer'/></beans>");
        workspace.source(Map.of("bean", "newer")); workspace.refreshed(old);
        assertEquals(List.of("newer"), driver.processor().designSession.document().beanIds());
        workspace.clear("project changed"); workspace.refreshed(old);
        assertNull(driver.processor().designSession.document());
    }
    @Test void refusesTraversalAndSymlinkEscapeEvenOnFollow() throws Exception {
        Path allowed = Files.createDirectory(project.resolve("allowed"));
        Path outside = xml("outside.xml", "<beans/>");
        files = new DesignFiles(List.of(allowed.toString()), project);
        assertThrows(java.io.IOException.class, () -> files.resolve("outside.xml"));
        assertThrows(java.io.IOException.class, () -> files.resolve("../outside.xml"));
        Path link = allowed.resolve("link.xml"); Files.createSymbolicLink(link, outside);
        assertThrows(java.io.IOException.class, () -> files.resolve(link.toString()));
        Files.delete(link); Files.writeString(link, "<beans/>"); workspace.open(link.toString());
        Files.delete(link); Files.createSymbolicLink(link, outside);
        var read = DesignWorkspace.read(files, link.toString(), false, driver.processor().designSession.generation());
        assertNull(read.document()); assertTrue(read.error().contains("outside authorised"));
    }
    @Test void javaReadsUseRootsAndCurrentDiskContent() throws Exception {
        Path source = Files.createDirectories(project.resolve("com/acme")).resolve("Node.java");
        Files.writeString(source, "package com.acme; class Node { void go() {} }");
        assertEquals("NODE", workspace.source(Map.of("fqn", "com.acme.Node", "method", "go")).mode());
        Files.writeString(source, "package com.acme; class Node { void next() {} }");
        assertThrows(java.io.IOException.class, () -> workspace.source(Map.of("fqn", "com.acme.Node", "method", "go")));
        assertTrue(workspace.source(Map.of("fqn", "com.acme.Node")).text().contains("next"));
        assertThrows(java.io.IOException.class, () -> workspace.source(Map.of("fqn", "/tmp/Other")));
    }
    @Test void diagnosticRefusalClearsPreviousResultAndDiscoveryDoesNotLoad() throws Exception {
        Path target = Files.createDirectory(project.resolve("target"));
        Path result = Files.writeString(target.resolve("fluxtion-validation.json"), "{\"contractVersion\":\"1.0\",\"valid\":false,\"diagnosticReport\":{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}}");
        assertEquals(1, files.discoverDiagnostics().size()); assertNull(driver.processor().designSession.result());
        workspace.diagnostics(result.toString()); assertNotNull(driver.processor().designSession.result());
        Files.writeString(result, "{\"diagnosticsVersion\":\"9.0\",\"diagnostics\":[]}");
        assertThrows(java.io.IOException.class, () -> workspace.diagnostics(result.toString()));
        assertNull(driver.processor().designSession.result());
    }

    @Test void malformedFirstOpenStillOffersInertTextForTheDocumentFinding() throws Exception {
        Path file = xml("broken.xml", "<beans>\n<bean");
        var view = workspace.open(file.toString());
        assertNull(view.document()); assertTrue(view.problem().contains("parse error"));
        assertEquals("<beans>\n<bean", workspace.source(Map.of("line", 2)).text());
        assertThrows(java.io.IOException.class, () -> workspace.source(Map.of("bean", "x")));
    }

    @Test void preparedReadsCannotMutateSessionAndNewerRequestsSupersedeThem() throws Exception {
        Path file = xml("a.xml", "<beans><bean id='x'/></beans>");
        var state = driver.processor().designSession;
        driver.submit(new DesignEvents.ReadRequested("open"));
        var snapshot = new DesignWorkspace.Snapshot(state.path(), state.document(), state.generation());
        var prepared = DesignWorkspace.prepare(files, snapshot, w -> w.open(file.toString()));
        assertNull(state.document()); assertEquals("", prepared.error());
        driver.submit(new DesignEvents.ReadRequested("source"));
        prepared.facts().forEach(driver::submit);
        assertNull(state.document());
        var latest = DesignWorkspace.prepare(files, new DesignWorkspace.Snapshot(state.path(), state.document(), state.generation()), w -> w.open(file.toString()));
        latest.facts().forEach(driver::submit);
        assertEquals(List.of("x"), state.document().beanIds());
    }

    @Test void ambiguousRelativeFilesAreRefusedAndReadOnlyFilesStayUnchanged() throws Exception {
        Path first = Files.createDirectory(project.resolve("first")), second = Files.createDirectory(project.resolve("second"));
        Path a = Files.writeString(first.resolve("design.xml"), "<beans><bean id='x'/></beans>");
        Files.writeString(second.resolve("design.xml"), "<beans/>");
        files = new DesignFiles(List.of(first.toString(), second.toString()), project);
        assertTrue(assertThrows(java.io.IOException.class, () -> files.resolve("design.xml")).getMessage().contains("ambiguous"));
        var before = Files.readAllBytes(a);
        Files.setPosixFilePermissions(a, java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
        Files.setPosixFilePermissions(first, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            workspace.open(a.toString()); workspace.source(Map.of("bean", "x"));
            assertArrayEquals(before, Files.readAllBytes(a));
        } finally { Files.setPosixFilePermissions(first, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")); }
    }
}
