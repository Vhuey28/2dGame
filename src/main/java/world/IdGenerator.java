package world;

/**
 * Monotonically increasing stable identifier source for persistent entities.
 * Stored in the save file so IDs remain unique across save/load cycles.
 * IDs are never recycled after entity death or destruction.
 */
public final class IdGenerator {
    private long nextId = 1L;

    public long next() {
        return nextId++;
    }

    public long getNextId() {
        return nextId;
    }

    public void restoreNextId(long value) {
        if (value < 1L) throw new IllegalArgumentException("Invalid next ID");
        nextId = value;
    }
}
