package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M44 §11 slice-1 acceptance — <b>the default build resolves the runtime and nothing else Fluxtion.</b>
 *
 * <p>The claim this guards is the one that made the dependency acceptable: a graph builder needs
 * {@code fluxtion-builder}, a closed-source toolchain, and no ordinary build of this project may
 * require it. CI, a fresh contributor, a reviewer without an API key and the release build all compile
 * a UI, and none of them should acquire a compiler to do it.
 *
 * <p>A claim of that kind rots silently — nothing fails on the day someone adds the builder to the main
 * dependency block for convenience, and the cost only appears in someone else's broken checkout. So it
 * is asserted mechanically here rather than described in a comment.
 *
 * <p>It reads the POM rather than running Maven so it works offline and in CI. The complementary check
 * is a real one, run by hand at slice close: {@code mvn dependency:tree} shows
 * {@code fluxtion-runtime -> agrona} and no builder.
 */
class PomShapeTest {

    private static final String REGEN_PROFILE = "regen";

    private static Document pom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        try (var in = Files.newInputStream(Path.of("pom.xml"))) {
            return factory.newDocumentBuilder().parse(in);
        }
    }

    @Test
    @DisplayName("the runtime is a direct dependency, pinned, and the builder is not one at all")
    void theDefaultDependenciesAreRuntimeOnly() throws Exception {
        Element project = pom().getDocumentElement();
        Element dependencies = child(project, "dependencies");
        Set<String> artifacts = new LinkedHashSet<>();
        for (Element dependency : children(dependencies, "dependency")) {
            artifacts.add(text(dependency, "artifactId"));
        }

        assertTrue(artifacts.contains("fluxtion-runtime"), artifacts.toString());
        assertFalse(artifacts.contains("fluxtion-builder"),
                "the builder belongs in the " + REGEN_PROFILE + " profile only — a graph builder in "
                        + "src/main/java is what puts it here, so check for one");
        assertFalse(artifacts.contains("fluxtion-compiler"), artifacts.toString());

        for (Element dependency : children(dependencies, "dependency")) {
            if ("fluxtion-runtime".equals(text(dependency, "artifactId"))) {
                String version = text(dependency, "version");
                assertEquals("${fluxtion.version}", version, "pinned via a property, not ranged");
            }
        }
        assertEquals("1.0.13", property(project, "fluxtion.version"));
    }

    @Test
    @DisplayName("the runtime's repository is declared — it is not on Maven Central")
    void theRepositoryIsDeclared() throws Exception {
        Element project = pom().getDocumentElement();
        List<String> urls = new ArrayList<>();
        for (Element repository : children(child(project, "repositories"), "repository")) {
            urls.add(text(repository, "url"));
        }
        assertTrue(urls.stream().anyMatch(u -> u.contains("repsy.io")),
                "without this the keyless build fails to resolve fluxtion-runtime at all: " + urls);
    }

    @Test
    @DisplayName("the builder, the AOT plugin and src/graph/java live only in the regen profile")
    void theToolchainIsConfinedToTheProfile() throws Exception {
        Element project = pom().getDocumentElement();
        Element regen = null;
        for (Element profile : children(child(project, "profiles"), "profile")) {
            if (REGEN_PROFILE.equals(text(profile, "id"))) {
                regen = profile;
            }
        }
        assertTrue(regen != null, "the " + REGEN_PROFILE + " profile is how the graph is regenerated");

        String inside = serialise(regen);
        assertTrue(inside.contains("fluxtion-builder"), "the builder is here");
        assertTrue(inside.contains("fluxtion-maven-plugin"), "and so is the AOT plugin");
        assertTrue(inside.contains("src/graph/java"), "and so is the graph source root");

        // And nowhere else. Everything outside the profiles block is what an ordinary build runs.
        String outside = serialise(project).replace(inside, "");
        assertFalse(outside.contains("fluxtion-builder"), "builder leaked out of the profile");
        assertFalse(outside.contains("fluxtion-maven-plugin"), "AOT plugin leaked out of the profile");
        assertFalse(outside.contains("src/graph/java"),
                "the graph source root leaked out of the profile — the default compile would then need "
                        + "the builder to compile SessionProcessorBuilder");
    }

    @Test
    @DisplayName("the generated processor and its GraphML are committed, so a keyless checkout builds")
    void theGeneratedArtefactsAreCommitted() {
        assertTrue(Files.isRegularFile(Path.of(
                        "src/main/java/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.java")),
                "generation is a hosted service; the source is checked in so nobody needs a key");
        assertTrue(Files.isRegularFile(Path.of(
                        "src/main/resources/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.graphml")),
                "the GraphML is what a reviewer reads instead of regenerating, and what "
                        + "SessionGraphShapeTest pins");
    }

    // ------------------------------------------------------------------ tiny DOM helpers

    private static Element child(Element parent, String name) {
        List<Element> found = children(parent, name);
        if (found.isEmpty()) {
            throw new AssertionError("pom.xml has no <" + name + "> under <" + parent.getTagName() + ">");
        }
        return found.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && element.getTagName().equals(name)) {
                found.add(element);
            }
        }
        return found;
    }

    private static String text(Element parent, String name) {
        List<Element> found = children(parent, name);
        return found.isEmpty() ? null : found.get(0).getTextContent().trim();
    }

    private static String property(Element project, String name) {
        return text(child(project, "properties"), name);
    }

    /** Element text content is enough here: every assertion is about a literal artefact id or path. */
    private static String serialise(Element element) {
        return element.getTextContent();
    }
}
