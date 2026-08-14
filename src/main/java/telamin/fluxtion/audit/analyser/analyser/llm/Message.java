package telamin.fluxtion.audit.analyser.analyser.llm;

/** One conversation turn. {@code role} is {@code "user"} or {@code "assistant"}. */
public record Message(String role, String content) {
    public static Message user(String content) { return new Message("user", content); }
    public static Message assistant(String content) { return new Message("assistant", content); }
}
