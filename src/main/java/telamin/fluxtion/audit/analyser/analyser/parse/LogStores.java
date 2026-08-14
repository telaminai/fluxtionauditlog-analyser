package telamin.fluxtion.audit.analyser.analyser.parse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens a log with the right backend for its size (spec §7): {@link HeapLogStore} at or below the
 * threshold (whole file in heap, fastest), {@link MappedLogStore} above it (streamed index + on-demand
 * reads, scales past 2 GB).
 */
public final class LogStores {

    private LogStores() {
    }

    public static LogStore open(Path path, int thresholdMb) throws IOException {
        long size = Files.size(path);
        long thresholdBytes = (long) Math.max(0, thresholdMb) * 1024 * 1024;   // 0 → always memory-mapped
        if (size <= thresholdBytes) {
            return HeapLogStore.fromFile(path);
        }
        return new MappedLogStore(path);
    }
}
