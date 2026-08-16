package telamin.fluxtion.audit.analyser.analyser.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mapping a class name to the file it lives in.
 *
 * <p>The case worth testing is the <b>nested</b> class: Fluxtion's own examples group node classes inside
 * a holder, so {@code com.acme.node.Nodes.QuotePublisher} is the normal shape rather than an exotic one,
 * and it does not live in {@code Nodes/QuotePublisher.java}.
 */
class SourceRootResolverTest {

    private static Path tree(Path root, String relativePath) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "// " + relativePath + "\n");
        return file;
    }

    @Test
    void findsATopLevelClass(@TempDir Path dir) throws IOException {
        Path file = tree(dir, "com/acme/node/PriceListener.java");
        SourceRootResolver resolver = new SourceRootResolver(List.of(dir.toString()));
        assertEquals(file, resolver.find("com.acme.node.PriceListener").orElse(null));
    }

    @Test
    void findsANestedClassInItsEnclosingFile(@TempDir Path dir) throws IOException {
        Path file = tree(dir, "com/acme/node/Nodes.java");
        SourceRootResolver resolver = new SourceRootResolver(List.of(dir.toString()));
        assertEquals(file, resolver.find("com.acme.node.Nodes.QuotePublisher").orElse(null),
                "the nested class is inside Nodes.java, not Nodes/QuotePublisher.java");
    }

    @Test
    void findsADeeplyNestedClass(@TempDir Path dir) throws IOException {
        Path file = tree(dir, "com/acme/node/Outer.java");
        SourceRootResolver resolver = new SourceRootResolver(List.of(dir.toString()));
        assertEquals(file, resolver.find("com.acme.node.Outer.Middle.Inner").orElse(null));
    }

    @Test
    void prefersTheExactFileWhenBothCouldExist(@TempDir Path dir) throws IOException {
        tree(dir, "com/acme/node/Nodes.java");
        Path exact = tree(dir, "com/acme/node/Nodes/QuotePublisher.java");
        SourceRootResolver resolver = new SourceRootResolver(List.of(dir.toString()));
        assertEquals(exact, resolver.find("com.acme.node.Nodes.QuotePublisher").orElse(null),
                "a real file wins over the enclosing-file fallback");
    }

    @Test
    void doesNotWalkPastThePackageIntoNonsense(@TempDir Path dir) throws IOException {
        // com/acme/node.java must NOT be offered for com.acme.node.Missing — 'node' is a package
        tree(dir, "com/acme/node.java");
        SourceRootResolver resolver = new SourceRootResolver(List.of(dir.toString()));
        assertTrue(resolver.find("com.acme.node.Missing").isEmpty(),
                "a lower-case segment is a package, and a package cannot enclose a class");
    }

    @Test
    void searchesEveryRootInOrder(@TempDir Path a, @TempDir Path b) throws IOException {
        Path file = tree(b, "com/acme/Thing.java");
        SourceRootResolver resolver = new SourceRootResolver(List.of(a.toString(), b.toString()));
        assertEquals(file, resolver.find("com.acme.Thing").orElse(null));
    }

    @Test
    void missingOrBlankIsEmptyRatherThanAThrow(@TempDir Path dir) {
        SourceRootResolver resolver = new SourceRootResolver(List.of(dir.toString()));
        assertTrue(resolver.find(null).isEmpty());
        assertTrue(resolver.find("  ").isEmpty());
        assertTrue(resolver.find("com.acme.NotThere").isEmpty());
        assertTrue(resolver.find("NoPackage").isEmpty());
    }

    @Test
    void readReturnsTheEnclosingFilesContentForANestedClass(@TempDir Path dir) throws IOException {
        tree(dir, "com/acme/node/Nodes.java");
        SourceRootResolver resolver = new SourceRootResolver(List.of(dir.toString()));
        assertTrue(resolver.read("com.acme.node.Nodes.OrderTracker").orElse("")
                .contains("com/acme/node/Nodes.java"));
    }
}
