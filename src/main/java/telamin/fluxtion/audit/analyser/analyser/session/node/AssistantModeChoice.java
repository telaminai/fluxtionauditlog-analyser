package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * M74 §4.1: the one assistant mode in force, as a session fact. Upstream of {@code assistantLoop}, so it reads the mode
 * as it is after the person's choice in the same cycle. Downstream of {@code localAgentSession}, which pushes into it
 * (review M2): that node decides first whether the person may leave Claude Code now — never while a turn is open — and,
 * when not, tells this node to keep the mode for this choice. In-app is the default, so nothing changes for anyone who
 * never chooses Claude Code.
 */
public class AssistantModeChoice implements EventLogSource {

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private String mode = AssistantMode.IN_APP.name();
    private boolean refuseThisChoice;

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onModeChosen(SessionEvents.AssistantModeChosen e) {
        if (refuseThisChoice) {                   // localAgentSession refused it, by name, earlier in this cycle
            refuseThisChoice = false;
            auditLog.info("assistantModeKept", mode).info("origin", String.valueOf(e.origin()));
            return false;
        }
        if (e.mode() == null || e.mode().name().equals(mode)) return false;
        mode = e.mode().name();
        auditLog.info("assistantMode", mode).info("origin", String.valueOf(e.origin()));
        return true;
    }

    /** Called by {@code localAgentSession}, earlier in the same cycle, when it refuses the choice being dispatched. */
    public void refuseThisChoice() {
        refuseThisChoice = true;
    }

    public AssistantMode mode() {
        return AssistantMode.valueOf(mode);
    }

    public boolean localAgent() {
        return AssistantMode.LOCAL_AGENT.name().equals(mode);
    }
}
