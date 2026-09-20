import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import java.nio.file.*;
import java.util.*;

/** Read-only cross-repo acceptance for ZIPs exported by the playground project-support test. */
public class VerifyStarterProfiles {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: VerifyStarterProfiles <downloads-dir> <expected-count>");
        Path root = Path.of(args[0]).toAbsolutePath();
        List<Path> profiles;
        try (var paths = Files.walk(root)) {
            profiles = paths.filter(p -> p.endsWith(ProjectProfile.CANONICAL_RELATIVE)).sorted().toList();
        }
        require(profiles.size() == Integer.parseInt(args[1]), "unexpected profile count: " + profiles.size());
        for (Path profile : profiles) {
            var config = new AppConfig();
            var result = ProjectProfile.load(profile, config, new SettingsShare());
            require(result.loaded() && !result.message().contains("REFUSED"), result.message());
            var declared = new Properties();
            try (var in = Files.newBufferedReader(profile)) { declared.load(in); }
            require(config.runbooks.size() == Integer.parseInt(declared.getProperty("runbook.count")), "runbooks dropped: " + profile);
            require(config.processorDeclarations.size() == Integer.parseInt(declared.getProperty("processorDeclaration.count")), "declarations dropped: " + profile);
            Path project = ProjectProfile.baseDirFor(profile);
            require(config.runbooks.containsKey("start-here"), "missing task entry: " + profile);
            for (var entry : config.runbooks.entrySet()) {
                Path target = project.resolve(entry.getValue().path()).normalize();
                require(target.startsWith(project) && Files.isRegularFile(target), "unresolved runbook: " + entry.getKey());
            }
            for (String guide : List.of("CLAUDE.md", "AGENTS.md"))
                require(Files.readString(project.resolve(guide)).contains("[PROJECT.md](PROJECT.md)"), "unrouted entry: " + guide);
            for (String source : config.sourceRoots)
                require(Files.isDirectory(Path.of(source)), "unresolved source root: " + source);
            System.out.println(root.relativize(profile) + ": loaded " + config.runbooks.size() + " runbooks, "
                    + config.processorDeclarations.size() + " processor declarations; all pointers resolve");
        }
        System.out.println("PASS: " + profiles.size() + " real download profiles, no rejected declarations");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
