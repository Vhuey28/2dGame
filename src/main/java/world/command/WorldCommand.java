package world.command;

/**
 * Marker interface for all commands. Commands request a state change
 * and may be rejected with a reason.
 */
public interface WorldCommand {
    /** Return a CommandResult indicating if the command was accepted. */
    CommandResult execute();
}