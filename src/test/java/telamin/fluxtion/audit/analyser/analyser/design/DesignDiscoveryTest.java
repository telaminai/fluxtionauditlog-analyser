package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DesignDiscoveryTest {
    static final String BUILDER="import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;\n"
            +"class DEMOBuilder implements FluxtionGraphBuilder { void buildGraph(Config cfg) {} }";
    @Test void scanFindsTheBuilderWithoutAnyGeneratedProcessorOrHeader(@TempDir Path root) throws Exception {
        Path file=Files.writeString(root.resolve("DEMOBuilder.java"),BUILDER);
        var found=DesignDiscovery.scan(new DesignFiles(List.of(root.toString()),root));
        assertEquals("builder",found.kind(),"★ T2 discovery must not depend on a processor header");
        assertEquals("source-scan",found.source());
        assertEquals(List.of(new DesignDiscovery.FileRef(file.toRealPath().toString(),"builder")),found.files());
    }
    @Test void mixedDefinitionsAreSortedAndUnrelatedFilesAreNotDefinitions(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("z.java"),BUILDER);
        Path xml=Files.writeString(root.resolve("a.xml"),"<beans><bean class='com.acme.FluxtionSpringConfig'/></beans>");
        Files.writeString(root.resolve("ordinary.xml"),"<beans><bean class='com.acme.DEMO'/></beans>");
        Files.writeString(root.resolve("comment.xml"),"<!-- <bean class='com.acme.FluxtionSpringConfig'/> --><beans/>");
        Files.writeString(root.resolve("other.java"),"class Other implements FluxtionGraphBuilder {}");
        var found=DesignDiscovery.scan(new DesignFiles(List.of(root.toString(),root.toString()),root));
        assertEquals("mixed",found.kind()); assertEquals(2,found.files().size());
        assertEquals(xml.toRealPath().toString(),found.files().getFirst().path());
    }
    @Test void rootAndFileLinksDoNotWidenReadAuthority(@TempDir Path base) throws Exception {
        Path root=Files.createDirectories(base.resolve("root")),outside=Files.createDirectories(base.resolve("outside"));
        Path foreign=Files.writeString(outside.resolve("Foreign.java"),BUILDER);
        Files.createSymbolicLink(root.resolve("file.java"),foreign);
        Files.createSymbolicLink(root.resolve("directory"),outside);
        assertEquals("none",DesignDiscovery.scan(new DesignFiles(List.of(root.toString()),null)).kind());
        Files.writeString(root.resolve("Local.java"),BUILDER);
        Path alias=base.resolve("alias");Files.createSymbolicLink(alias,root);
        assertEquals(1,DesignDiscovery.scan(new DesignFiles(List.of(alias.toString()),null)).files().size());
    }
    @Test void anExceededBudgetNeverReturnsAPartialCandidateList(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("A.java"),BUILDER);
        for(int i=0;i<DesignDiscovery.MAX_FILES;i++) Files.createFile(root.resolve("entry-"+i));
        var found=DesignDiscovery.scan(new DesignFiles(List.of(root.toString()),null));
        assertTrue(found.source().startsWith("unavailable")); assertEquals(List.of(),found.files());
        assertTrue(found.source().contains("10,000-file scan limit"), "the cap has its own explicit reason");
    }
    @Test void individualReadFailuresKeepCandidatesAndNameEveryUnreadableFile(@TempDir Path root) throws Exception {
        Path good=Files.writeString(root.resolve("A.java"),BUILDER);
        Files.write(root.resolve("bad.java"),new byte[]{(byte)0xff});
        Files.writeString(root.resolve("large.xml")," ".repeat(DesignFiles.MAX_BYTES+1));
        var found=DesignDiscovery.scan(new DesignFiles(List.of(root.toString()),null));
        assertEquals(List.of(new DesignDiscovery.FileRef(good.toRealPath().toString(),"builder")), found.files(),
                "individual read failures must not erase a readable definition");
        assertEquals("builder", found.kind());
        assertEquals("source-scan", found.source());
        var state = new telamin.fluxtion.audit.analyser.analyser.session.node.DesignSession(
                new telamin.fluxtion.audit.analyser.analyser.session.node.OperationGate(),
                new telamin.fluxtion.audit.analyser.analyser.session.node.EffectQueue());
        assertTrue(state.discovered(new DesignEvents.DiscoveryCompleted(found,state.discoveryGeneration())));
        String note = String.valueOf(state.echo().get("note"));
        assertTrue(note.contains("2 files could not be read:"),note);
        assertTrue(note.contains("bad.java") && note.contains("large.xml"),note);
        assertFalse(note.contains("10,000-file scan limit"),"file failures are not the cap");
    }
    @Test void aFailedFileAloneIsDisclosedWithoutClaimingNoDesignExists(@TempDir Path root) throws Exception {
        Files.write(root.resolve("unreadable.java"),new byte[]{(byte)0xff});
        var found=DesignDiscovery.scan(new DesignFiles(List.of(root.toString()),null));
        assertTrue(found.files().isEmpty());
        assertEquals("source-scan",found.source());
        var state=new telamin.fluxtion.audit.analyser.analyser.session.node.DesignSession(
                new telamin.fluxtion.audit.analyser.analyser.session.node.OperationGate(),
                new telamin.fluxtion.audit.analyser.analyser.session.node.EffectQueue());
        state.discovered(new DesignEvents.DiscoveryCompleted(found,state.discoveryGeneration()));
        assertTrue(state.discoveryNote().startsWith("No readable design found."));
        assertTrue(state.discoveryNote().contains("1 file could not be read:"));
        assertTrue(state.echo().get("note").toString().contains("unreadable.java"));
    }
    @Test void theFileBudgetHasNoDeadRootCountGuard() throws Exception {
        String source=Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/design/DesignDiscovery.java"));
        assertFalse(source.contains("access.roots().size() > MAX_FILES"),"the budget counts encountered files, not root declarations");
    }
}
