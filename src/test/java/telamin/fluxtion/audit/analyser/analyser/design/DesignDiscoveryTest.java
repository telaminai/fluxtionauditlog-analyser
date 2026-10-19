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
    }
    @Test void undecodableOrOversizedInputsCannotCertifyAnEmptyOrPartialScan(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("A.java"),BUILDER);
        Path bad=Files.write(root.resolve("bad.java"),new byte[]{(byte)0xff});
        assertTrue(DesignDiscovery.scan(new DesignFiles(List.of(root.toString()),null)).source().startsWith("unavailable"));
        Files.writeString(bad," ".repeat(DesignFiles.MAX_BYTES+1));
        var found=DesignDiscovery.scan(new DesignFiles(List.of(root.toString()),null));
        assertTrue(found.source().startsWith("unavailable")); assertTrue(found.files().isEmpty());
    }
}
