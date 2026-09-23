package telamin.fluxtion.audit.analyser.analyser.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceDocumentTest {
    @TempDir Path tmp;
    public static void jar(Path path, Map<String,String> entries) throws Exception {
        Files.createDirectories(path.getParent());
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var e : entries.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue().getBytes(StandardCharsets.UTF_8)); out.closeEntry(); }
        }
    }
    @Test void originAndRevisionTravelWithCachedTextAndMissesCanBeReread() throws Exception {
        Path jar = tmp.resolve("demo-sources.jar"); jar(jar, Map.of("demo/A.java", "old"));
        var r = new MavenSourceResolver(List.of(tmp.toString()), true);
        var old = r.document("demo.A").orElseThrow();
        assertEquals(jar.toString(), old.archive()); assertEquals("demo/A.java", old.entry());
        assertEquals("cba06b5736faf67e54b07b561eae94395e774c517a7d910a54369e1263ccfbd4", old.revision());
        assertTrue(r.document("demo.B").isEmpty());
        jar(jar, Map.of("demo/A.java", "new", "demo/B.java", "added"));
        assertEquals(old, r.document("demo.A").orElseThrow());
        assertEquals("new", r.freshDocument("demo.A").orElseThrow().text());
        assertEquals("added", r.freshDocument("demo.B").orElseThrow().text());
        jar(tmp.resolve("later-sources.jar"), Map.of("demo/C.java", "later"));
        assertTrue(r.freshDocument("demo.C").isEmpty());
        assertEquals("later", new MavenSourceResolver(List.of(tmp.toString()), true).document("demo.C").orElseThrow().text());
        assertTrue(new MavenSourceResolver(List.of(tmp.toString()), false).document("demo.A").isEmpty());
    }
    @Test void rootOrderAndSelectedModelFollowAcceptedSnapshotOnly() throws Exception {
        Path a = Files.createDirectories(tmp.resolve("a/demo")), b = Files.createDirectories(tmp.resolve("b/demo"));
        String before="package demo;\npublic class Processor {\n public OldType node;\n}";
        String after="package demo;\npublic class Processor {\n public NewType node;\n}";
        Files.writeString(a.resolve("Processor.java"), before); Files.writeString(b.resolve("Processor.java"), "other");
        var service = new SourceService(); service.configure(List.of(a.getParent().toString(), b.getParent().toString()), "demo.Processor");
        assertEquals("demo.OldType", service.fqnForInstance("node"));
        var lookup=service.captureLookup(); Files.writeString(a.resolve("Processor.java"),after);
        var snapshot=lookup.freshDocumentForSpotlight("demo.Processor").orElseThrow();
        assertEquals(a.getParent().toString(),snapshot.root()); assertEquals(after,snapshot.text());
        assertEquals("demo.OldType",service.fqnForInstance("node"),"preparation does not publish model");
        service.acceptSpotlightModel(lookup,"demo.Processor",EventProcessorModel.parse("demo.Processor", snapshot.text()));
        assertEquals("demo.NewType",service.fqnForInstance("node"));
        service.configure(List.of(b.getParent().toString(),a.getParent().toString()),"demo.Processor");
        assertFalse(service.isCurrent(lookup));
        assertThrows(IllegalStateException.class,()->service.acceptSpotlightModel(lookup,"demo.Processor",null));
        assertEquals("other",service.captureLookup().freshDocumentForSpotlight("demo.Processor").orElseThrow().text());
    }
    @Test void originIsTheWinningRootNotJustAnAncestorOfTheFile() throws Exception {
        Path nested=Files.createDirectories(tmp.resolve("nested/demo"));
        Files.writeString(nested.resolve("Node.java"),"package demo; class Node {}\n");
        var resolver=new SourceRootResolver(List.of(tmp.toString(),nested.getParent().toString()));
        assertEquals(nested.getParent().toString(),resolver.document("demo.Node").orElseThrow().root());
    }

}
