package telamin.fluxtion.audit.analyser.analyser.config;

import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;

/**
 * A report that was deleted and can still be restored (PR #33; owner decision 2026-09-27: a delete is recoverable).
 *
 * @param project   the profile the report was deleted from ({@link AppConfig#activeProjectPath}), "" for none — a
 *                  report is restored only into the project it came from
 * @param deletedAt ISO instant of the delete, display-only
 * @param report    the report exactly as it was
 */
public record DeletedReport(String project, String deletedAt, ReportSpec report) {
    public DeletedReport {
        project = project == null ? "" : project;
        deletedAt = deletedAt == null ? "" : deletedAt;
    }
}
