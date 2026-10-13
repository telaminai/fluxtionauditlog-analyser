package telamin.fluxtion.audit.analyser.analyser.template;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectSession;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.core.ReleaseNotes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Optional network leg used by tools/bench/template-bench.py --live once UP-PG-03 is deployed. */
public final class TemplateLiveBench {
    private TemplateLiveBench() { }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("analyser-template-live-");
        try {
            TemplateClient client = TemplateClient.playground();
            var selection = client.catalogue(ReleaseNotes.version());
            check(!selection.entries().isEmpty(), "catalogue offers at least one template");
            var entry = selection.entries().stream()
                    .filter(candidate -> candidate.file().equals("analyser-bundle.starter.json"))
                    .findFirst().orElse(selection.entries().getFirst());
            System.out.println("  PASS  selected catalogue template — " + entry.file());
            var defaults = client.defaults(entry);
            byte[] zip = client.download(new TemplateClient.Download(entry, defaults.artifact(),
                    defaults.group(), defaults.basePackage()));
            check(zip.length > 0, "real scaffold endpoint returned a zip");
            var installed = new TemplateArchive().install(zip, work.resolve(defaults.artifact()));
            check(installed.profile() != null && Files.isRegularFile(installed.profile()),
                    "downloaded project contains its analyser profile");

            AppConfig config = new AppConfig();
            ProjectSession project = new ProjectSession(config, new SettingsShare(work.toString()), () -> { });
            var opened = project.open(installed.profile());
            check(opened.loaded(), "downloaded profile opens through ProjectSession");
            check(config.runbooks.values().stream().allMatch(pointer ->
                            Files.isRegularFile(installed.projectRoot().resolve(pointer.path()).normalize())),
                    "profile runbooks resolve inside the downloaded project");
            int checks = 6;
            // Drift: every root script a deployed template marks executable must be executable once installed.
            // The archive's marks are read only for this comparison; the installer never applies them (D-4).
            for (var each : selection.entries()) {
                var eachDefaults = client.defaults(each);
                byte[] eachZip = client.download(new TemplateClient.Download(each, eachDefaults.artifact(),
                        eachDefaults.group(), eachDefaults.basePackage()));
                var eachInstalled = new TemplateArchive().install(eachZip,
                        work.resolve("drift-" + checks + "-" + eachDefaults.artifact()));
                var marked = TemplateExecutablesTest.archiveMarkedRootExecutables(eachZip);
                // Without marks there is nothing to compare: if the scaffold stopped writing unix modes, an empty
                // set would pass every check below. Every catalogue template ships mvnw, so it must be marked.
                check(marked.contains("mvnw"), each.file() + " archive marks its root scripts executable"
                        + " (mvnw among them) — marked: " + marked);
                checks++;
                var notRunnable = new java.util.TreeSet<String>();
                for (String script : marked) {
                    if (!TemplateExecutablesTest.executable(eachInstalled.projectRoot().resolve(script))) {
                        notRunnable.add(script);
                    }
                }
                check(notRunnable.isEmpty(), each.file() + " root scripts installed executable"
                        + (notRunnable.isEmpty() ? "" : " — not: " + notRunnable));
                checks++;
            }
            System.out.println(checks + " passed, 0 failed");
        } finally {
            try (var paths = Files.walk(work)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean value, String description) {
        if (!value) throw new IllegalStateException("FAIL  " + description);
        System.out.println("  PASS  " + description);
    }
}
