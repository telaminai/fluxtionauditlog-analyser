package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.Map;

/**
 * Onboarding trial arm C (2026-11-09): with the Analyser connected, two assistants asked to build a Fluxtion app wrote a
 * project by hand from Maven Central (a pre-sign-in com.telamin.fluxtion 1.0.9, the retired com.fluxtion 9.x) instead
 * of starting from the Java-authoring starter. Neither the MCP server instructions nor {@code analyser_context} said
 * how to start a project. This is the one text for both: the instructions' section, and the pointer
 * {@code context.project.newProject} carries while no project is open.
 *
 * <p>Facts, not orders (benchmark 2026-11-09): text that told "the assistant" what to do was set aside as a prompt
 * injection, so the section states the product facts in the third person and the client acts on them itself.
 * {@link #SIGN_IN_FACTS} is its own constant: it is true while the starter ships no processor, and is the sentence to
 * change when it does. Its login version is the starters' fluxtion-maven-plugin pin (1.3.5); a signed-out first build
 * of the starter stops with FLX-1022 (ensure mode, no stamped processor), any other generating build with FLX-1023.
 */
public final class NewProjectRoute {

    private NewProjectRoute() {
    }

    public static final String STARTER_URL = "https://fluxtion-playground.dev/start/scaffold?template=java-authoring";

    public static final String SIGN_IN_FACTS = "The first build generates the processor on the hosted compiler after a "
            + "one-time compiler sign-in approved in a browser "
            + "(./mvnw com.telamin.fluxtion:fluxtion-maven-plugin:1.3.5:login prints a link and a code). Signed out, it "
            + "stops with FLX-1022 or FLX-1023, sending nothing. Another template does not avoid this; "
            + "-DskipFluxtion leaves no processor to run. ";

    /** The MCP server instructions' section. */
    public static final String INSTRUCTIONS =
            "STARTING A NEW FLUXTION PROJECT (asked to build or create an app or processor, or a new, blank or empty "
            + "project): the Java-authoring starter, " + STARTER_URL + ", is the blank Java project, a Mongoose app with "
            + "an empty graph. The route: download it, read its CLAUDE.md, then build. Maven Central does not work: its "
            + "com.telamin.fluxtion 1.0.9 predates compiler sign-in and com.fluxtion 9.x is "
            + "the retired earlier product. " + SIGN_IN_FACTS;

    /** {@code context.project.newProject}, while no project is open. */
    public static final String CONTEXT_POINTER = "a new or blank Fluxtion project starts from the Java-authoring starter, "
            + "the blank Java project (" + STARTER_URL + "): download, read its CLAUDE.md, build; the first build needs "
            + "compiler sign-in. Not Maven Central";

    /** Adds the pointer to the {@code project} map of a context with no project open. */
    public static void addTo(Map<String, Object> project) {
        project.put("newProject", CONTEXT_POINTER);
    }
}
