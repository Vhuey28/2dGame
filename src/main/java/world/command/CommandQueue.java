package world.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Buffered queue of pending commands. UI can queue commands here;
 * the simulation will process them on the next simulation tick.
 */
public class CommandQueue {
    private final List<WorldCommand> pending = new ArrayList<>();

    /** Add a command to the pending queue. */
    public void submit(WorldCommand command) {
        if (command != null) {
            pending.add(command);
        }
    }

    /** Poll the next pending command. */
    public WorldCommand poll() {
        return pending.isEmpty() ? null : pending.remove(0);
    }

    /** Get all pending commands without removing them. */
    public List<WorldCommand> peekAll() {
        return pending;
    }

    public int size() {
        return pending.size();
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public void clear() {
        pending.clear();
    }
}