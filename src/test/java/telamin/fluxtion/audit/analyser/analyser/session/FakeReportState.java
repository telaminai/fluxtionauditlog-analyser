package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.llm.ExportGuard;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.ReportState;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Owner 2026-10-18: a fake of the frame's observation for a hosted {@code report}'s judgement — the active project, its
 * reports and their createdAt, the session's record of its own writes, and the exchange folder a path resolves in (the
 * real {@link ExportGuard}).
 */
public final class FakeReportState implements ReportState {
    public Path root;
    public Path exchange;
    public final Map<String, String> reports = new HashMap<>();
    public final Map<String, String> session = new HashMap<>();
    public long sessionGeneration;

    public FakeReportState() {
    }

    /** The project at {@code root}, its exchange folder the project itself. */
    public FakeReportState(Path root) {
        this.root = root;
        this.exchange = root;
    }

    public FakeReportState put(String name, String createdAt) {
        reports.put(name, createdAt);
        return this;
    }

    @Override public Path projectRoot() { return root; }
    @Override public boolean holds(String name) { return reports.containsKey(name); }
    @Override public String createdAt(String name) { return reports.get(name); }
    @Override public String sessionCreatedAt(long generation, String name) {
        return generation == sessionGeneration ? session.get(name) : null;
    }
    @Override public Path resolveOutput(String path) {
        var r = ExportGuard.resolve(path, exchange != null, exchange == null ? null : exchange.toString());
        return r.ok() ? r.path() : null;
    }
}
