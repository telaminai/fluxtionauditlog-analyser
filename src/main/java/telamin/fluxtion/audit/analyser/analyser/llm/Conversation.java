package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.ArrayList;
import java.util.List;

/** Ordered user/assistant turns for one LLM chat. The system prompt is kept separately. */
public final class Conversation {

    private final List<Message> messages = new ArrayList<>();

    public void add(Message m) {
        messages.add(m);
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /** Clears the conversation (Reset button); the system prompt is unaffected. */
    public void reset() {
        messages.clear();
    }
}
