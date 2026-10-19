package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import java.util.Set;

/**
 * M74 P3 (spec §5.7, §4.1): a tool name the CLI reports, as what a FACT may carry — its class and a name from a fixed
 * vocabulary (a hosted verb, or one of the CLI's own tool names as P3-0 and PB-1 recorded them), never the raw string.
 * The raw name (a person's MCP server and tool names among them) stays in the bounded store, for the dialog.
 */
public final class ToolVocabulary {

    private ToolVocabulary() {
    }

    /** The CLI's own tools as CLI 2.1.282 lists them in {@code system/init.tools} (P3-0, PB-1), plus older names. */
    public static final Set<String> BUILT_INS = Set.of("AskUserQuestion", "Bash", "BashOutput", "CronCreate", "CronDelete",
            "CronList", "DesignSync", "Edit", "EnterPlanMode", "EnterWorktree", "ExitPlanMode", "ExitWorktree", "Glob",
            "Grep", "KillShell", "LS", "ListAgents", "MultiEdit", "NotebookEdit", "Read", "ReportFindings",
            "ScheduleWakeup", "SendMessage", "Skill", "Task", "TaskCreate", "TaskGet", "TaskList", "TaskOutput", "TaskStop",
            "TaskUpdate", "TodoWrite", "ToolSearch", "WebFetch", "WebSearch", "Workflow", "Write");

    /** A built-in the vocabulary does not know: classed as built-in, named only by this word. */
    public static final String OTHER_BUILT_IN = "other-built-in";

    /** A tool name's class and its vocabulary name (the hosted verb, the built-in, or null). */
    public record Named(ToolClass toolClass, String name) {
    }

    private static final String HOSTED = "mcp__" + LocalAgentPosture.HOSTED_SERVER + "__";
    private static final String REGISTRATION = "mcp__" + LocalAgentPosture.PERSONS_REGISTRATION + "__";

    public static Named of(String toolName) {
        if (toolName == null || toolName.isBlank()) return new Named(ToolClass.UNKNOWN, null);
        if (toolName.startsWith(HOSTED)) {
            String verb = McpTools.hostedVerbFor(toolName.substring(HOSTED.length()));
            if (verb == null) return new Named(ToolClass.UNKNOWN, null);
            return new Named(LocalAgentPosture.askable(verb) ? ToolClass.HOSTED_DESTRUCTIVE : ToolClass.HOSTED_OTHER, verb);
        }
        if (toolName.startsWith(REGISTRATION)) return new Named(ToolClass.PERSONS_ANALYSER_REGISTRATION, null);
        if (toolName.startsWith("mcp__")) return new Named(ToolClass.OTHER_MCP, null);
        if (BUILT_INS.contains(toolName)) return new Named(ToolClass.BUILT_IN, toolName);
        if (toolName.matches("[A-Z][A-Za-z0-9]{0,63}")) return new Named(ToolClass.BUILT_IN, OTHER_BUILT_IN);
        return new Named(ToolClass.UNKNOWN, null);
    }

    /** The word a fact may carry for {@code toolName}: the hosted tool as {@code analyser_<verb>}, a built-in, a class. */
    public static String word(String toolName) {
        Named n = of(toolName);
        return switch (n.toolClass()) {
            case HOSTED_DESTRUCTIVE, HOSTED_OTHER -> McpTools.PREFIX + n.name();
            case BUILT_IN -> n.name();
            case PERSONS_ANALYSER_REGISTRATION -> "persons-fluxtion-analyser";
            case OTHER_MCP -> "other-mcp";
            case UNKNOWN -> "unknown";
        };
    }
}
