package telamin.fluxtion.audit.analyser.analyser.ui;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Access to the release version and the changelog bundled in the jar (M16 §7). One source of truth for
 * Help ▸ About, Help ▸ Release notes and the what's-new-on-upgrade dialog — the same {@code CHANGELOG.md}
 * that ships as the GitHub release body.
 */
public final class ReleaseNotes {
    private ReleaseNotes() { }

    /**
     * The build's {@code Implementation-Version} from <em>our</em> jar's manifest, or {@code "dev"} when
     * run from the IDE. Uses {@code getPackage().getImplementationVersion()} — which reads the manifest
     * of the jar this class was loaded from — rather than scanning the classpath for
     * {@code /META-INF/MANIFEST.MF}, which would return whichever dependency jar (e.g. FlatLaf) came first.
     */
    public static String version() {
        String v = ReleaseNotes.class.getPackage().getImplementationVersion();
        return (v == null || v.isBlank()) ? "dev" : v;
    }

    /** True for a development build (run from the IDE, or a `-SNAPSHOT` placeholder jar). */
    public static boolean isDevBuild() {
        String v = version();
        return v.equals("dev") || v.contains("SNAPSHOT");
    }

    /** The full bundled changelog, or a short placeholder when running outside a packaged jar. */
    public static String changelog() {
        try (InputStream in = ReleaseNotes.class.getResourceAsStream("/release-notes/CHANGELOG.md")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignore) {
            // fall through
        }
        return "Release notes are bundled into packaged builds only.";
    }

    /**
     * The changelog section for {@code version} — the lines from {@code ## [version]} up to the next
     * {@code ## [} heading. Empty if that version isn't in the changelog (e.g. a dev build).
     */
    public static String sectionFor(String version) {
        return sectionFor(version, changelog());
    }

    /** Testable core of {@link #sectionFor(String)} — extract a version's section from changelog text. */
    static String sectionFor(String version, String changelogText) {
        String[] lines = changelogText.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean in = false;
        for (String line : lines) {
            boolean heading = line.startsWith("## [");
            if (heading) {
                if (in) break;                                   // reached the next version — stop
                in = line.startsWith("## [" + version + "]");    // start at our version's heading
                if (in) continue;                                // don't include the heading line itself
            }
            if (in) out.append(line).append('\n');
        }
        return out.toString().strip();
    }
}
