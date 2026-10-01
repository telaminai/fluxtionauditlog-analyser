package telamin.fluxtion.audit.analyser.analyser.session;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.optional.ReplaceRegExp;
import org.apache.tools.ant.types.FileSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UPS-2, the independent review of PR #104, R1: the fixture generator's header strip must never delete real Java.
 *
 * <p>{@code examples/fixture-generator/pom.xml} strips the generator's attribution line and its confidentiality notice
 * (fluxtion#24) from every DEMO processor it writes. The notice expression was anchored at the file's start but stopped
 * at the first SPACE-prefixed {@code " *}{@code /"}, so a valid header closed at column 0 let it run on through the
 * package declaration and the next comment: the review's input came out as {@code public class NoSpace {}}, which still
 * compiles, in the default package. A compile error is therefore no witness; these assertions are.
 *
 * <p><b>The actual transformation.</b> Each {@code <replaceregexp>} of the pom's {@code strip-generated-attribution}
 * execution is read from the pom — pattern, flags, by-line, replacement and filesets — and run, in declaration order, by
 * Ant's own {@link ReplaceRegExp} task, the class maven-antrun-plugin 3.1.0 runs, over a scratch copy of both configured
 * generated-source directories. Nothing here re-implements the expressions. Keyless: no generation is involved. DEMO
 * values only; the attribution's address is a reserved {@code .invalid} name.
 */
class GeneratedHeaderStripTest {

    static final Path FIXTURE_POM = Path.of("examples/fixture-generator/pom.xml");
    static final String EXECUTION = "strip-generated-attribution";

    /** The generator's header as it is emitted: the notice block, carrying the attribution line. */
    static final String GENERATED_HEADER = """
            /*
             * Copyright: © 2025.  DEMO Author <demo@example.invalid> - All Rights Reserved
             * This source code is protected under international copyright law.  All rights
             * reserved and protected by the copyright holders.
             * This file is confidential and only available to authorized individuals with the
             * permission of the copyright holders.  If you encounter this file and do not have
             * permission, please contact the copyright holders and delete this file.
             */
            """;

    static final String BODY = """
            package com.acme.demo.generated;

            import java.util.List;

            /** DEMO later documentation */
            public class DemoStrip {
              /* a later block comment */
              public int answer() {
                return List.of(42).get(0);
              }
            }
            """;

    record Strip(String match, String flags, boolean byLine, String replace, List<String> dirs, String includes) {
    }

    /** The pom's strip steps, in declaration order. */
    static List<Strip> strips() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        Element root;
        try (InputStream in = Files.newInputStream(FIXTURE_POM)) {
            root = factory.newDocumentBuilder().parse(in).getDocumentElement();
        }
        NodeList executions = root.getElementsByTagName("execution");
        for (int i = 0; i < executions.getLength(); i++) {
            Element execution = (Element) executions.item(i);
            NodeList ids = execution.getElementsByTagName("id");
            if (ids.getLength() == 0 || !EXECUTION.equals(ids.item(0).getTextContent().trim())) continue;
            List<Strip> out = new ArrayList<>();
            NodeList steps = execution.getElementsByTagName("replaceregexp");
            for (int s = 0; s < steps.getLength(); s++) {
                Element step = (Element) steps.item(s);
                List<String> dirs = new ArrayList<>();
                String includes = null;
                NodeList sets = step.getElementsByTagName("fileset");
                for (int f = 0; f < sets.getLength(); f++) {
                    Element set = (Element) sets.item(f);
                    dirs.add(set.getAttribute("dir").replace("${project.basedir}/", ""));
                    includes = set.getAttribute("includes");
                }
                out.add(new Strip(step.getAttribute("match"), step.getAttribute("flags"),
                        Boolean.parseBoolean(step.getAttribute("byline")), step.getAttribute("replace"), dirs, includes));
            }
            return out;
        }
        throw new AssertionError("no '" + EXECUTION + "' execution in " + FIXTURE_POM);
    }

    /** The configured generated-source directories, every strip's filesets combined. */
    static List<String> configuredDirs() throws Exception {
        List<String> dirs = new ArrayList<>();
        for (Strip s : strips()) for (String d : s.dirs()) if (!dirs.contains(d)) dirs.add(d);
        return dirs;
    }

    /** Runs the pom's steps, as Ant runs them, over {@code project}, a scratch copy of the generator's tree. */
    static void runStrip(Path project) throws Exception {
        Project ant = new Project();
        ant.init();
        ant.setBaseDir(project.toFile());
        for (Strip s : strips()) {
            ReplaceRegExp task = new ReplaceRegExp();
            task.setProject(ant);
            task.setMatch(s.match());
            task.setReplace(s.replace());
            task.setFlags(s.flags());
            task.setByLine(s.byLine());
            task.setEncoding("UTF-8");
            for (String dir : s.dirs()) {
                FileSet set = new FileSet();
                set.setProject(ant);
                set.setDir(project.resolve(dir).toFile());
                set.setIncludes(s.includes());
                set.setErrorOnMissingDir(false);
                task.addFileset(set);
            }
            task.execute();
        }
    }

    /** Writes {@code text} as {@code name} in the first configured directory, strips, and returns the result. */
    static String stripped(Path project, String name, String text) throws Exception {
        Path dir = project.resolve(configuredDirs().get(0));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
        runStrip(project);
        return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the pom's strip execution exists, and covers both generated-source directories")
    void theStripCoversBothGeneratedDirectories() throws Exception {
        assertEquals(List.of("src/main/java/com/acme/demo/generated", "src/main/resources/com/acme/demo/generated"),
                configuredDirs(), "the two directories the generator writes DEMO processors into");
        assertFalse(strips().isEmpty());
    }

    @Test
    @DisplayName("positive control: the generated header is removed whole, and the code after it is untouched")
    void theGeneratedHeaderIsRemoved(@TempDir Path project) throws Exception {
        String out = stripped(project, "DemoStrip.java", GENERATED_HEADER + BODY);
        assertEquals(BODY, out, "the header goes and every byte after it stays");
    }

    @Test
    @DisplayName("R1: a header closed at column 0 removes the header only — never the package, imports or code")
    void aColumnZeroTerminatorNeverDeletesCode(@TempDir Path project) throws Exception {
        String header = GENERATED_HEADER.replace("\n */\n", "\n*/\n");
        String out = stripped(project, "DemoStrip.java", header + BODY);
        assertTrue(out.contains("package com.acme.demo.generated;"), "the package declaration was deleted: " + out);
        assertTrue(out.contains("/** DEMO later documentation */"), "the later documentation was deleted: " + out);
        assertEquals(BODY, out, "only the leading comment is removed");
    }

    @Test
    @DisplayName("R1, the review's own input: the column-0 terminator keeps its package")
    void theReviewsInputKeepsItsPackage(@TempDir Path project) throws Exception {
        String review = "/*\n * This source code is protected under international copyright law.\n * DEMO notice\n*/\n"
                + "package com.acme.demo.generated;\n/** DEMO later documentation */\npublic class NoSpace {}\n";
        String out = stripped(project, "NoSpace.java", review);
        assertEquals("package com.acme.demo.generated;\n/** DEMO later documentation */\npublic class NoSpace {}\n", out,
                "UPS2-STRIP: real Java package declaration was deleted");
    }

    @Test
    @DisplayName("CRLF line endings: the header is removed, and the CRLF body is kept byte for byte")
    void crlfLineEndingsAreStrippedSafely(@TempDir Path project) throws Exception {
        String crlfBody = BODY.replace("\n", "\r\n");
        String out = stripped(project, "DemoStrip.java", GENERATED_HEADER.replace("\n", "\r\n") + crlfBody);
        assertEquals(crlfBody, out, "a CRLF header is the same header; the body keeps its own line endings");
    }

    @Test
    @DisplayName("a later notice-looking comment after the header is never consumed with it")
    void aSecondCommentIsNeverConsumed(@TempDir Path project) throws Exception {
        String later = "/*\n * This source code is protected under international copyright law.\n */\n";
        String body = BODY.replace("/** DEMO later documentation */\n", later + "/** DEMO later documentation */\n");
        String out = stripped(project, "DemoStrip.java", GENERATED_HEADER + body);
        assertEquals(body, out, "the strip removes the leading comment only, never a second one");
    }

    @Test
    @DisplayName("a notice that is not the leading comment changes nothing: code first, notice later")
    void aNonLeadingNoticeDeletesNothing(@TempDir Path project) throws Exception {
        String text = BODY + "/*\n * This source code is protected under international copyright law.\n*/\n";
        assertEquals(text, stripped(project, "DemoStrip.java", text),
                "no code may be deleted for a notice the strip does not recognise as the header");
    }

    @Test
    @DisplayName("a leading comment that is not the notice is left untouched")
    void anUnrecognisedLeadingCommentIsLeftAlone(@TempDir Path project) throws Exception {
        String text = "/*\n * DEMO licence: not the generator's notice\n */\n" + BODY;
        assertEquals(text, stripped(project, "DemoStrip.java", text));
    }

    @Test
    @DisplayName("both configured directories are stripped by one run")
    void bothDirectoriesAreStripped(@TempDir Path project) throws Exception {
        List<String> dirs = configuredDirs();
        for (String dir : dirs) {
            Files.createDirectories(project.resolve(dir));
            Files.writeString(project.resolve(dir).resolve("DemoStrip.java"), GENERATED_HEADER + BODY);
        }
        runStrip(project);
        for (String dir : dirs) {
            assertEquals(BODY, Files.readString(project.resolve(dir).resolve("DemoStrip.java")), dir);
        }
    }

    /** Reads a file the way the strip writes it; used only to keep the helper honest about encodings. */
    static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
