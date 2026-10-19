import java.lang.reflect.RecordComponent;
import java.util.List;

/** Current onboarding control inventory. No model, Swing, file chooser or provider is invoked. */
public class RecordPersonFacts {
    public static void main(String[] args) throws Exception {
        // Read SessionEvents and its UI posting sites when changing this inventory. Do not derive it from Python:
        // a Python omission must fail the cross-check, not silently shrink the Java witness too.
        String[] names = {
            "StartPromptPackDropped", "ReturnToMainOptions", "StartPagePromptSent", "LocalAgentStartRequested",
            "LocalAgentFolderAnswered", "LocalAgentSendRequested", "LocalAgentStopRequested", "ProposalApplyRequested",
            "ProposalDismissed", "ExampleDownloadRequested", "ExampleDownloadConfirmed", "LocalAgentSwitchChosen",
            "LocalAgentPermissionChosen", "ProposalItemAnswered", "LocalAgentRestartAnswered",
            "LocalAgentRestartOfferRequested", "LocalAgentNewChatRequested", "StartPageNoLogAnswered",
            "LocalAgentAskAgainRequested", "LocalAgentSetupDirectoryChosen", "ExternalAccessChosen",
            "AssistantProfileChosen", "AssistantFullProfileAnswered", "AiSetupRequested", "AiSetupChoiceMade",
            "AiSetupPostureChosen", "AiSetupClientChosen", "AiSetupBack", "AiSetupContinue", "AiSetupCancelled",
            "AiSetupFirstRunDismissed", "AiSetupSuggestedTaken", "AiSetupCheckAgain", "AiSetupCopyRequested",
            "AiSetupRegisterRequested", "AiSetupMoreOptions", "AiSetupBuildModeAnswered", "AiSetupAlsoTurnOffChosen",
            "ExternalAccessRequested", "ExternalAccessAnswered", "RegisterAnswered", "StarterMenuRequested",
            "StarterChosen", "StarterConfigured", "StarterDestinationChosen", "StarterReviewAnswered",
            "StarterGraphOfferAnswered"
        };
        for (String name : names) {
            Class<?> type = Class.forName("telamin.fluxtion.audit.analyser.analyser.session.SessionEvents$" + name);
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] types = new Class<?>[components.length];
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                values[i] = demoValue(components[i]);
            }
            Object fact = type.getDeclaredConstructor(types).newInstance(values);
            System.out.println("---\neventLogRecord:\n    event: " + name
                    + "\n    eventToString: " + fact + "\n    nodeLogs:\n---");
        }
    }

    private static Object demoValue(RecordComponent component) {
        Class<?> type = component.getType();
        if (type == long.class) return 7L;
        if (type == int.class) return 7;
        if (type == boolean.class) return true;
        if (type == String.class) return "DEMO";
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type == List.class) return component.getGenericType().getTypeName().contains("java.lang.Long")
                ? List.of(7L) : List.of("DEMO");
        return null; // complex observations (consent, source), deliberately not operator words or private paths
    }
}
