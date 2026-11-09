package telamin.fluxtion.audit.analyser.analyser.config;

/**
 * RAPID-OFF (2026-11-07): how a BUILD signs in to the hosted compiler, in one place, so every analyser sentence that
 * tells a person or an agent how to regenerate says the same, working, command.
 *
 * <p>Builder 1.0.90 / fluxtion-maven-plugin 1.3.4 (RELEASE-1.0.90.md): the builder's mode defaults to {@code token}; the
 * credential is {@code fluxtion.remote.token}/{@code fluxtion.api.token}, then {@code FLUXTION_API_TOKEN}, then
 * {@code ~/.fluxtion/credentials} as the plugin's {@code login} writes it; the compiler address defaults to the managed
 * compiler, so the login needs no URL. {@code login} is {@code requiresProject = false}: the fully qualified form works
 * in any directory, and the short {@code fluxtion:} prefix only where a pom declares the plugin. Maven, Gradle, JBang
 * and runtime (in-process) builds all read the same stored login. Signed out, a build stops with FLX-1023 before any
 * connection. {@code FLUXTION_COMPILER_URL} is needed only for a compiler other than the managed one.
 *
 * <p><b>Not the in-app sign-in.</b> AI ▸ Compiler account… keeps its own credential in the OS credential store and shows
 * the account (plan, usage, signed-in email); it does not sign builds in. Sharing one store is tracker item 4.
 */
public final class CompilerSignIn {

    /** The first plugin whose login needs no URL and whose builds have no key fallback. */
    public static final String PLUGIN_VERSION = "1.3.4";

    /** Sign a machine's builds in, from any directory. */
    public static final String LOGIN = "mvn com.telamin.fluxtion:fluxtion-maven-plugin:" + PLUGIN_VERSION + ":login";

    /** The same, in a project whose pom declares the plugin. */
    public static final String PROJECT_LOGIN = "./mvnw fluxtion:login";

    /** What CI sets instead of an interactive sign-in. */
    public static final String CI = "FLUXTION_API_TOKEN (FLUXTION_COMPILER_URL only for a compiler other than the "
            + "managed one)";

    private CompilerSignIn() {
    }
}
