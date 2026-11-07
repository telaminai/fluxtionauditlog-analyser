package telamin.fluxtion.audit.analyser.analyser.config;

/**
 * RAPID-OFF (2026-11-07): how a BUILD signs in to the hosted compiler, in one place, so every analyser sentence that
 * tells a person or an agent how to regenerate says the same, working, command.
 *
 * <p>Checked against fluxtion-maven-plugin 1.3.3: {@code login} is {@code requiresProject = false}, so the fully
 * qualified form works in any directory (the {@code fluxtion:} prefix resolves only where the plugin is declared), and
 * it fails with "No compiler URL" unless {@code fluxtion.compilerUrl} or {@code FLUXTION_COMPILER_URL} is given. The
 * credential is stored in {@code ~/.fluxtion/credentials}; in CI the plugin reads {@code FLUXTION_API_TOKEN} and needs
 * {@code FLUXTION_COMPILER_URL} beside it.
 *
 * <p><b>Not the in-app sign-in.</b> AI ▸ Compiler account… keeps its own credential in the OS credential store and shows
 * the account (plan, usage, signed-in email); it does not sign Maven builds in. Sharing one store is tracker item 4.
 *
 * <p>TODO(1.0.90/1.3.4): with builder 1.0.90 and plugin 1.3.4 the URL becomes optional and JBang/plain-Java builds read
 * the stored login; shorten these then.
 */
public final class CompilerSignIn {

    public static final String COMPILER_URL = "https://compiler.fluxtion-playground.dev";

    /** Sign a machine's builds in, from any directory. */
    public static final String LOGIN = "mvn com.telamin.fluxtion:fluxtion-maven-plugin:1.3.3:login -Dfluxtion.compilerUrl="
            + COMPILER_URL;

    /** The same, in a project whose pom declares the plugin. */
    public static final String PROJECT_LOGIN = "./mvnw fluxtion:login -Dfluxtion.compilerUrl=" + COMPILER_URL;

    /** What CI sets instead of an interactive sign-in. */
    public static final String CI = "FLUXTION_API_TOKEN and FLUXTION_COMPILER_URL";

    private CompilerSignIn() {
    }
}
