package telamin.fluxtion.audit.analyser.analyser.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edit-loop spec §I1: the Windows-only install fixtures — drive-relative {@code C:foo} and rooted {@code \foo},
 * where Windows resolves them. They run only on Windows and are NOT verified by a macOS or Linux branch check.
 * (On every OS the same inputs are already refused by syntax: see TemplateRootContainmentTest.) Kept in its own
 * class so the mutation gate's zero-skip baseline for the other containment tests is not affected by the OS guard.
 */
@EnabledOnOs(OS.WINDOWS)
class TemplateRootWindowsTest {

    @TempDir Path temp;

    @Test
    void onWindowsDriveRelativeAndRootedRootsAreRefused() throws Exception {
        for (String root : List.of("C:foo", "\\foo")) {
            Path destination = temp.resolve("windows-" + Math.abs(root.hashCode()));
            byte[] zip = template("share.version=1\nsourceRoot.count=1\nsourceRoot.0=" + root.replace("\\", "\\\\") + "\n");
            IOException error = assertThrows(IOException.class, () -> new TemplateArchive().install(zip, destination), root);
            assertTrue(error.getMessage().contains("drive letter") || error.getMessage().contains("backslash")
                    || error.getMessage().contains("root component"), root + ": " + error.getMessage());
            assertFalse(Files.exists(destination), "nothing installed for " + root);
        }
    }

    private static byte[] template(String profile) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("bundle/.analyser/project.fluxtion-settings"));
            out.write(profile.getBytes());
            out.closeEntry();
            out.putNextEntry(new ZipEntry("bundle/src/main/java/Keep.java"));
            out.write("class Keep {}\n".getBytes());
            out.closeEntry();
        }
        return bytes.toByteArray();
    }
}
