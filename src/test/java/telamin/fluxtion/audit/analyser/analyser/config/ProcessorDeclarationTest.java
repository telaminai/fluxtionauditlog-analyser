package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessorDeclarationTest {
    private final SettingsShare share = new SettingsShare("/home/tester");
    @Test void declarationsRoundTripAndSwitchWithoutInventingTypes(@TempDir Path dir) throws Exception {
        var own = new AppConfig();
        var ownIntent = new ProcessorDeclaration("not decided", ProcessorDeclaration.Kind.UNSPECIFIED, "");
        own.processorDeclarations.add(ownIntent);
        var projectConfig = new AppConfig();
        projectConfig.eventProcessorFqns.clear();
        projectConfig.selectedEventProcessor = "";
        projectConfig.processorDeclarations.add(new ProcessorDeclaration("runtime", ProcessorDeclaration.Kind.RUNTIME, ""));
        projectConfig.processorDeclarations.add(new ProcessorDeclaration("aot", ProcessorDeclaration.Kind.DECLARED, "com.acme.Generated"));
        Path a = dir.resolve("a/.analyser/project.fluxtion-settings");
        Path b = dir.resolve("b/.analyser/project.fluxtion-settings");
        ProjectProfile.save(a, projectConfig, share);
        ProjectProfile.save(b, new AppConfig(), share);
        var session = new ProjectSession(own, share, null);
        assertTrue(session.open(a).loaded());
        assertEquals(projectConfig.processorDeclarations, own.processorDeclarations);
        assertTrue(own.eventProcessorFqns.isEmpty(), "intent is not a discovered source type");
        assertTrue(session.open(b).loaded());
        assertTrue(own.processorDeclarations.isEmpty());
        session.close();
        assertEquals(List.of(ownIntent), own.processorDeclarations);
    }
    @Test void invalidDeclarationsRefuseBeforeReplacingState(@TempDir Path dir) throws Exception {
        var config = new AppConfig();
        config.sourceRoots.add("keep-root");
        var before = ProjectProfile.snapshot(config);
        for (String body : List.of(
                "processorDeclaration.version=2\nprocessorDeclaration.count=0",
                "processorDeclaration.version=1\nprocessorDeclaration.count=1\nprocessorDeclaration.0.name=x\nprocessorDeclaration.0.kind=runtime\nprocessorDeclaration.0.fqcn=com.acme.Fake",
                "processorDeclaration.count=0",
                "processorDeclaration.version=1\nprocessorDeclaration.count=1\nprocessorDeclaration.0.name=x\nprocessorDeclaration.0.kind=declared")) {
            Path path = dir.resolve("bad.fluxtion-settings");
            Files.writeString(path, "share.version=1\n"+body+"\n");
            assertFalse(ProjectProfile.load(path, config, share).loaded(), body);
            assertEquals(before, ProjectProfile.snapshot(config));
        }
    }
    @Test void ownConfigKeepsItsOwnDeclarationsWhileAProjectIsOpen(@TempDir Path dir) {
        var c = new AppConfig();
        c.processorDeclarations.add(new ProcessorDeclaration("own", ProcessorDeclaration.Kind.RUNTIME, ""));
        var own = ProjectProfile.snapshot(c);
        c.processorDeclarations.clear();
        c.processorDeclarations.add(new ProcessorDeclaration("project", ProcessorDeclaration.Kind.DECLARED, "com.acme.P"));
        var store = new ConfigStore(dir.resolve("config"));
        store.save(c, own);
        assertEquals(own.processorDeclarations(), store.load().processorDeclarations);
    }
}
