package telamin.fluxtion.audit.analyser.analyser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relative links between spec documents must resolve.
 *
 * <h2>Why a test, and why here</h2>
 * {@code mkdocs build --strict} is the link gate for this repo, and it <b>structurally cannot see this
 * directory</b>: {@code docs/specs/} is not part of the built site, so the checker never visits it. That
 * blind spot has now cost twice in two days — ten stale inter-spec links repaired by hand on 2026-08-27,
 * and two more found by reading on 2026-08-28. Twelve links, both times found by a person noticing.
 *
 * <p>This is the third member of a family worth naming: the four-term sweep cannot see inside images
 * (found 2026-08-16) or into git metadata (recorded in CLAUDE.md), and mkdocs cannot see outside the
 * site. Each gate is sound within its reach and silent outside it, and the silence is what gets trusted.
 *
 * <h2>The exemption is not an allowlist</h2>
 * The two surviving dead links are deliberate: files copied verbatim from another project keep their
 * STARTER-relative paths, because adapting them would put intentional differences into a snapshot whose
 * parity with its source cannot currently be checked. That is a real reason, so it is allowed — but only
 * while it is <b>written down where a reader following the link will find it</b>. If the explanation is
 * ever deleted, this test fails, because at that point the links are simply broken again.
 */
class SpecLinksResolveTest {

    private static final Path SPECS = Path.of("docs/specs");

    /** A markdown link to a relative {@code .md}, ignoring anchors, URLs and mail links. */
    private static final Pattern LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)#:]+\\.md)[^)]*\\)");

    /**
     * Links allowed to be unresolved HERE, mapped to the file that must justify each. A snapshot copied
     * from another repository keeps that repository's paths; the justification lives in its README.
     */
    private static final Map<String, String> STARTER_RELATIVE = Map.of(
            "docs/specs/mongoose-bootstrap-artefacts/specs/tracker.md -> ../../CLAUDE.md",
            "docs/specs/mongoose-bootstrap-artefacts/README.md",
            "docs/specs/mongoose-bootstrap-artefacts/specs/spec-mongoose-analyser-validation.md -> ../../CLAUDE.md",
            "docs/specs/mongoose-bootstrap-artefacts/README.md");

    private record Link(Path from, String target) {
        String key() {
            return from.toString().replace('\\', '/') + " -> " + target;
        }
    }

    private static List<Link> allLinks() throws IOException {
        List<Link> links = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SPECS)) {
            for (Path md : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                Matcher m = LINK.matcher(Files.readString(md));
                while (m.find()) links.add(new Link(md, m.group(1)));
            }
        }
        return links;
    }

    @Test
    void everyRelativeLinkBetweenSpecsResolves() throws IOException {
        List<Link> links = allLinks();
        assertTrue(links.size() > 50, "found only " + links.size() + " links — has docs/specs moved? "
                + "A link checker that checks nothing passes for the wrong reason.");

        List<String> broken = new ArrayList<>();
        for (Link link : links) {
            if (Files.exists(link.from().getParent().resolve(link.target()).normalize())) continue;
            if (STARTER_RELATIVE.containsKey(link.key())) continue;
            broken.add(link.key());
        }
        assertTrue(broken.isEmpty(),
                "dead links between spec documents — mkdocs --strict cannot catch these, because "
                        + "docs/specs is not part of the built site:\n  " + String.join("\n  ", broken));
    }

    @Test
    void everyExemptedLinkIsStillDead_soTheListCannotOutliveItsReason() throws IOException {
        // an exemption for a link that now resolves is stale, and a stale exemption is how a real break
        // gets waved through later under a rule nobody re-read
        List<String> live = new ArrayList<>();
        for (Link link : allLinks()) {
            if (STARTER_RELATIVE.containsKey(link.key())
                    && Files.exists(link.from().getParent().resolve(link.target()).normalize())) {
                live.add(link.key());
            }
        }
        assertTrue(live.isEmpty(), "these are exempted but now resolve — drop them from the list: " + live);
    }

    @Test
    void eachExemptionIsJUSTIFIEDwhereAReaderWouldLook() throws IOException {
        // the exemption is only acceptable while the reason is written where someone following the dead
        // link will find it. Delete the explanation and this fails — which is the point: at that moment
        // the links stopped being deliberate and went back to being broken.
        for (var entry : STARTER_RELATIVE.entrySet()) {
            Path justification = Path.of(entry.getValue());
            assertTrue(Files.exists(justification), "missing justification file: " + justification);
            String text = Files.readString(justification);
            assertTrue(text.contains("STARTER-relative") || text.contains("starter-relative"),
                    justification + " no longer explains why " + entry.getKey()
                            + " does not resolve — either restore the explanation or fix the link");
        }
    }
}
