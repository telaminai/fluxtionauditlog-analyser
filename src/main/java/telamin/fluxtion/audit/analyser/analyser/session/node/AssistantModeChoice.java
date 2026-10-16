package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * M74 §4.1: the one assistant mode in force, as a session fact. Upstream of {@code assistantLoop} and
 * {@code localAgentSession}, so both read the mode as it is after the person's choice in the same cycle; each enforces
 * it in its own handlers. In-app is the default, so nothing changes for anyone who never chooses Claude Code.
 */
public class AssistantModeChoice implements EventLogSource {

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private String mode = AssistantMode.IN_APP.name();

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onModeChosen(SessionEvents.AssistantModeChosen e) {
        if (e.mode() == null || e.mode().name().equals(mode)) return false;
        mode = e.mode().name();
        auditLog.info("assistantMode", mode).info("origin", String.valueOf(e.origin()));
        return true;
    }

    public AssistantMode mode() {
        return AssistantMode.valueOf(mode);
    }

    public boolean localAgent() {
        return AssistantMode.LOCAL_AGENT.name().equals(mode);
    }
}
