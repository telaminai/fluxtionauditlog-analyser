package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.Optional;

/**
 * Given a fully-qualified class name, the source text for it — or nothing.
 *
 * <p>This was a bare {@code Function<String, Optional<String>>} threaded through eight signatures. The
 * type said what it took and returned and nothing about what it meant: a reader had to reach the call
 * site to learn that the string is an FQN rather than a file path, and that empty means *"not found"*
 * rather than *"found and empty"*. Both are now in the name and the contract.
 *
 * <p><b>Empty is not evidence.</b> Every caller must treat a missing answer as *"cannot say"*, never as
 * *"the class has no source"* — {@link NodeLogging} depends on that distinction to avoid excluding a
 * node from a coverage denominator on no evidence, which is the error that flatters a score and cannot
 * be spotted from the output.
 *
 * <p><b>Why this is an interface and not a Fluxtion service</b>, since an earlier draft of the idioms
 * document named it as the live candidate for one: a Fluxtion service is registered so that <i>nodes</i>
 * can query it. Nothing in the session graph resolves source — {@link NodeLogging} and
 * {@link CoverageScope} are plain classes called from the action surface, outside the processor
 * entirely. What was actually wrong here was an unnamed function type, and the fix for that is a name.
 */
@FunctionalInterface
public interface SourceResolver {

    /**
     * @param fullyQualifiedClassName e.g. {@code com.acme.node.PriceListener}
     * @return the source text, or empty when it cannot be found — which is not evidence of absence
     */
    Optional<String> sourceFor(String fullyQualifiedClassName);

    /** A resolver that finds nothing — the position an analyser is in with a stranger's log. */
    static SourceResolver none() {
        return fqn -> Optional.empty();
    }
}
