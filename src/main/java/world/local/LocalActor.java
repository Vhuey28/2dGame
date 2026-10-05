package world.local;

import world.PersonActivity;

/** Physical projection of one canonical strategic entity in a loaded local scene. */
public final class LocalActor {
    public final long sourceId;
    public final ActorKind kind;
    public final String label;
    public final boolean companion;
    public double x;
    public double y;
    public double targetX;
    public double targetY;
    public double speed;
    public PersonActivity activity;

    LocalActor(long sourceId, ActorKind kind, String label, boolean companion,
            double x, double y, double speed, PersonActivity activity) {
        this.sourceId = sourceId;
        this.kind = kind;
        this.label = label;
        this.companion = companion;
        this.x = x;
        this.y = y;
        this.targetX = x;
        this.targetY = y;
        this.speed = speed;
        this.activity = activity;
    }

    public enum ActorKind { PERSON, CARAVAN, ARMY }
}
