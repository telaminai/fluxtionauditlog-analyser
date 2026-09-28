package telamin.fluxtion.audit.analyser.analyser.config;

import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

/**
 * M69 — a walk that was deleted and can still be restored, as {@link DeletedReport} is for a report.
 *
 * @param project   the profile the walk was deleted from ({@link AppConfig#activeProjectPath}), "" for none — a walk is
 *                  restored only into the project it came from
 * @param deletedAt ISO instant of the delete, display-only
 * @param walk      the walk exactly as it was
 */
public record DeletedWalk(String project, String deletedAt, WalkSpec walk) {
    public DeletedWalk {
        project = project == null ? "" : project;
        deletedAt = deletedAt == null ? "" : deletedAt;
    }
}
