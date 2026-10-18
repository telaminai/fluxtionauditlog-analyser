package telamin.fluxtion.audit.analyser.analyser.config;

/**
 * Owner decision 2026-10-18: a chart definition that was deleted, or replaced by a later call, and can still be
 * restored — as {@link DeletedReport} is for a report and {@link DeletedWalk} for a walk.
 *
 * @param project   the profile the chart belonged to ({@link AppConfig#activeProjectPath}), "" for none — a chart is
 *                  restored only into the project it came from
 * @param deletedAt ISO instant it entered the bin, display-only (and part of the entry's id)
 * @param reason    why it is here: the chart was deleted, or its definition was replaced
 * @param chart     the definition exactly as it was before
 */
public record DeletedChart(String project, String deletedAt, ChartBin.Reason reason, GraphSpec chart) {
    public DeletedChart {
        project = project == null ? "" : project;
        deletedAt = deletedAt == null ? "" : deletedAt;
        reason = reason == null ? ChartBin.Reason.DELETED : reason;
    }
}
