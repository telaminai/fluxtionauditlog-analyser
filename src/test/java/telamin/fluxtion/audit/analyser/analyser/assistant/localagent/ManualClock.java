package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Virtual time for the adapter: a delay of 0 runs at once; anything later waits for {@link #advance}. */
final class ManualClock implements LocalAgentAdapter.Clock {

    private record Task(long due, long seq, Runnable run) { }

    private final List<Task> tasks = new ArrayList<>();
    private long now;
    private long seq;

    /** Run once at the next {@link #nowMillis} read: a test's hook into a point inside a spawn. */
    Runnable onNextRead;

    @Override
    public long nowMillis() {
        Runnable hook = onNextRead;
        onNextRead = null;
        if (hook != null) hook.run();
        return now;
    }

    @Override
    public void schedule(long delayMs, Runnable task) {
        if (delayMs <= 0) {
            task.run();
            return;
        }
        tasks.add(new Task(now + delayMs, seq++, task));
    }

    /** Move time on by {@code ms}, running everything that falls due, in order. */
    void advance(long ms) {
        long until = now + ms;
        while (true) {
            Task next = tasks.stream().filter(t -> t.due() <= until)
                    .min(Comparator.comparingLong(Task::due).thenComparingLong(Task::seq)).orElse(null);
            if (next == null) break;
            tasks.remove(next);
            now = Math.max(now, next.due());
            next.run().run();
        }
        now = until;
    }

    int pending() {
        return tasks.size();
    }
}
