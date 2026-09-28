package telamin.fluxtion.audit.analyser.bundle;

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A second PROCESS holding a capture's owner lock, for BundleWriterReapTest: the neighbour a shared exchange directory
 * can have. It locks the marker, says so on stdout, and holds the lock until its stdin closes.
 */
public final class CaptureLockHolder {
    private CaptureLockHolder() {
    }

    public static void main(String[] args) throws Exception {
        try (FileChannel ch = FileChannel.open(Path.of(args[0]), StandardOpenOption.WRITE)) {
            ch.lock();
            System.out.println("locked");
            System.out.flush();
            while (System.in.read() >= 0) { /* hold until the test closes our stdin */ }
        }
    }
}
