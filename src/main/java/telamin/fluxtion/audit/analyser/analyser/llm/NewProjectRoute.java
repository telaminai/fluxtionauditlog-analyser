package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.Map;

/**
 * Onboarding trial arm C (2026-11-09): with the Analyser connected, two assistants asked to build a Fluxtion app wrote a
 * project by hand from Maven Central (a pre-sign-in com.telamin.fluxtion 1.0.9, the retired com.fluxtion 9.x) instead
 * of starting from the Java-authoring starter. Neither the MCP server instructions nor {@code analyser_context} said
 * how to start a project. This is the one text for both: the instructions' section, and the pointer
 * {@code context.project.newProject} carries while no project is open.
 *
 * <p>{@link #SIGN_IN_STEP} is its own constant: it is true while the starter ships no processor, and is the sentence to
 * change when it does.
 */
public final class NewProjectRoute {

    private NewProjectRoute() {
    }

    public static final String STARTER_URL = "https://fluxtion-playground.dev/start/scaffold?template=java-authoring";

    public static final String SIGN_IN_STEP = "Sign-in is the expected next step, not a blocker: do not switch template, "
            + "delete or replace the project, or use -DskipFluxtion to avoid it; if the user declines, stop and say so. ";

    /** The MCP server instructions' section. */
    public static final String INSTRUCTIONS =
            "STARTING A NEW FLUXTION PROJECT (asked to build or create an app or processor): download the "
            + "Java-authoring starter, " + STARTER_URL + " (a Mongoose app), read its "
            + "CLAUDE.md, then build. Never hand-write a project from Maven Central: its com.telamin.fluxtion 1.0.9 "
            + "predates compiler sign-in and com.fluxtion 9.x is the retired earlier product; current "
            + "artifacts are on the Fluxtion public repository. The first build generates the processor on the hosted "
            + "compiler and needs compiler sign-in; on FLX-1022 or FLX-1023, start it and relay its link and code. "
            + SIGN_IN_STEP;

    /** {@code context.project.newProject}, while no project is open. */
    public static final String CONTEXT_POINTER = "to start a new Fluxtion project, download the Java-authoring starter ("
            + STARTER_URL + "), read its CLAUDE.md and build; the first build needs compiler sign-in. Not Maven Central "
            + "artifacts";

    /** Adds the pointer to the {@code project} map of a context with no project open. */
    public static void addTo(Map<String, Object> project) {
        project.put("newProject", CONTEXT_POINTER);
    }
}
