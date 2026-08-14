package telamin.fluxtion.audit.analyser.analyser.llm;

/**
 * Describes the on-disk audit log so an agentic LLM can read/grep it for follow-up questions
 * (read-ahead <b>and</b> read-behind from a record's byte offset). Seeded into the prompt; for a
 * non-agentic target it is simply inert text.
 *
 * @param displayLocation what the user opened (a local path, or an {@code s3://} URI)
 * @param localPath       the actual local file the store reads (equals {@code displayLocation} for a
 *                        local open; the fetched temp file for an S3 open); {@code null} if none
 * @param sizeBytes       file size in bytes, or {@code -1} if unknown
 * @param recordCount     number of indexed records
 * @param minTime         earliest logTime (epoch millis), or {@code null}
 * @param maxTime         latest logTime (epoch millis), or {@code null}
 */
public record LogFileInfo(String displayLocation, String localPath, long sizeBytes,
                          int recordCount, Long minTime, Long maxTime) {

    public boolean hasLocalFile() {
        return localPath != null && !localPath.isBlank();
    }

    public boolean isRemote() {
        return displayLocation != null && !displayLocation.equals(localPath);
    }
}
