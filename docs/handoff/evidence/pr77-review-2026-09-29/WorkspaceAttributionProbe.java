package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;

/** Source-informed counterexample on the real generated processor, with held adapter effects. */
public class WorkspaceAttributionProbe {
    public static void main(String[] args) {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        var route = new SessionEvents.AssistantRoute("anthropic", "DEMO", "", true, true, 3, 20, 30);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, route));
        long ticket = a.assistantContexts.getFirst().ticket();
        d.post(new SessionEvents.AssistantContextPrepared(ticket, 2));
        d.post(new SessionEvents.ViewFilterChanged("DEMO-new-filter"));
        System.out.println("ViewFilterChanged during provider request: " + d.snapshot().assistant().phase());
        a.assistantVerbs.put(10L, "open");
        a.assistantVerbs.put(11L, "flag");
        d.post(new SessionEvents.AssistantCompletionReceived(ticket, 1, 3, List.of(10L, 11L)));
        // The open effect is held. A separate person's open is requested and lands through the normal session route.
        SessionFixtures.openLog(d, a, "DEMO-persons-log.yaml");
        System.out.println("Independent open while assistant open held: " + d.snapshot().assistant().phase());
        d.post(new SessionEvents.AssistantActionFinished(ticket, 10, "open", true, 20));
        System.out.println("Next action requested: " + a.assistantActions.getLast().action());
        System.out.println("Frozen: " + d.snapshot().assistant().frozen());
        System.out.println("Basis adopted: " + d.snapshot().assistant().basis());
    }
}
