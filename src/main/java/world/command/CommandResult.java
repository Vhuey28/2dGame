package world.command;

/**
 * Result of a command. Always has a boolean accepted and a reason code
 * (useful for UI and tests to understand why a command was rejected).
 */
public final class CommandResult {
    public final boolean accepted;
    public final String reasonCode;
    public final String message;

    public CommandResult(boolean accepted, String reasonCode, String message) {
        this.accepted = accepted;
        this.reasonCode = reasonCode;
        this.message = message;
    }

    public static CommandResult accepted() {
        return new CommandResult(true, "ACCEPTED", "");
    }

    public static CommandResult rejected(String reasonCode, String message) {
        return new CommandResult(false, reasonCode, message);
    }

    @Override
    public String toString() {
        return accepted
                ? "CommandResult[accepted]"
                : String.format("CommandResult[rejected: %s — %s]", reasonCode, message);
    }
}