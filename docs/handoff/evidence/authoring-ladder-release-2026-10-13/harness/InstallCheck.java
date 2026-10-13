import java.nio.file.*;
import java.util.*;
/** Installs each given zip through the RELEASED analyser jar's TemplateArchive and lists every root *.sh / mvnw with its
 *  executable bit. Usage: java -cp <analyser.jar> InstallCheck.java <dest-dir> <zip>...   exit 1 if any root script is not executable. */
public class InstallCheck {
    public static void main(String[] a) throws Exception {
        boolean ok = true;
        for (int i = 1; i < a.length; i++) {
            Path dest = Paths.get(a[0]).resolve("install-" + i);
            var inst = new telamin.fluxtion.audit.analyser.analyser.template.TemplateArchive().install(Files.readAllBytes(Paths.get(a[i])), dest);
            Path root = inst.projectRoot();
            List<String> rows = new ArrayList<>();
            try (var s = Files.list(root)) {
                for (Path p : (Iterable<Path>) s.sorted()::iterator) {
                    String n = p.getFileName().toString();
                    if (Files.isRegularFile(p) && (n.endsWith(".sh") || n.equals("mvnw"))) {
                        boolean x = Files.isExecutable(p); ok &= x; rows.add(n + "=" + (x ? "x" : "NOT-EXECUTABLE"));
                    }
                }
            }
            System.out.println(Paths.get(a[i]).getFileName() + " -> " + root.getFileName() + " " + rows);
        }
        System.out.println(ok ? "INSTALL-CHECK PASS" : "INSTALL-CHECK FAIL");
        System.exit(ok ? 0 : 1);
    }
}
