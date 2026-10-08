package world.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import world.Army;
import world.Caravan;
import world.Person;
import world.PersonActivity;
import world.WorldParty;
import world.WorldState;

/**
 * Runtime local projection. Actors retain source IDs and never duplicate canonical
 * people, caravans, or armies. Positions are presentation-only and deterministic.
 */
public final class LocalSceneSimulation {
    private final WorldState world;
    private final long settlementId;
    private final List<LocalActor> actors = new ArrayList<>();
    private int width;
    private int height;
    private long scheduledHour = Long.MIN_VALUE;

    public LocalSceneSimulation(WorldState world, long settlementId, int width, int height) {
        this.world = world;
        this.settlementId = settlementId;
        this.width = Math.max(480, width);
        this.height = Math.max(360, height);
        projectCanonicalEntities();
    }

    private void projectCanonicalEntities() {
        WorldParty playerParty = world.player == null ? null : world.parties.get(world.player.partyId);
        for (Person person : world.people.values()) {
            if (!person.alive || person.currentSettlementId == null || person.currentSettlementId != settlementId) continue;
            if (world.player != null && person.id == world.player.personId) continue;
            boolean companion = playerParty != null && playerParty.memberPersonIds.contains(person.id);
            actors.add(create(person.id, LocalActor.ActorKind.PERSON,
                    person.givenName + " " + person.familyName, companion, person.currentActivity));
        }
        for (Caravan caravan : world.caravans.values()) {
            if (caravan.state != Caravan.CaravanState.DESTROYED
                    && caravan.state != Caravan.CaravanState.TRAVELING
                    && caravan.currentSettlementId == settlementId) {
                actors.add(create(caravan.id, LocalActor.ActorKind.CARAVAN,
                        "Caravan " + caravan.id, false, PersonActivity.TRAVELING));
            }
        }
        for (Army army : world.armies.values()) {
            if (army.state != Army.ArmyState.DISBANDED && army.state != Army.ArmyState.TRAVELING
                    && army.currentSettlementId == settlementId) {
                actors.add(create(army.id, LocalActor.ActorKind.ARMY,
                        "Army " + army.id, false, PersonActivity.GUARDING));
            }
        }
        actors.sort(Comparator.comparing((LocalActor actor) -> actor.kind).thenComparingLong(actor -> actor.sourceId));
    }

    private LocalActor create(long id, LocalActor.ActorKind kind, String label,
            boolean companion, PersonActivity activity) {
        double x = 96 + positiveHash(id * 31 + kind.ordinal() * 7) % Math.max(96, width - 192);
        double y = 96 + positiveHash(id * 47 + kind.ordinal() * 11) % Math.max(96, height - 192);
        return new LocalActor(id, kind, label, companion, x, y,
                kind == LocalActor.ActorKind.PERSON ? 34.0 + id % 18 : 22.0, activity);
    }

    public void update(double seconds, long worldMinute, int newWidth, int newHeight) {
        width = Math.max(480, newWidth);
        height = Math.max(360, newHeight);
        long hour = worldMinute / 60L;
        if (hour != scheduledHour) {
            scheduledHour = hour;
            assignTargets(hour);
        }
        for (LocalActor actor : actors) {
            if (actor.kind == LocalActor.ActorKind.PERSON) {
                Person person = world.people.get(actor.sourceId);
                if (person != null) {
                    actor.activity = person.currentActivity;
                    WorldParty playerParty = world.player == null ? null : world.parties.get(world.player.partyId);
                    actor.companion = playerParty != null && playerParty.memberPersonIds.contains(person.id);
                }
            }
            double dx = actor.targetX - actor.x;
            double dy = actor.targetY - actor.y;
            double distance = Math.hypot(dx, dy);
            if (distance < 3.0) continue;
            double step = Math.min(distance, actor.speed * seconds);
            actor.x += dx / distance * step;
            actor.y += dy / distance * step;
        }
    }

    private void assignTargets(long hour) {
        for (LocalActor actor : actors) {
            int zone = zoneFor(actor.activity);
            long hash = positiveHash(actor.sourceId * 131 + hour * 17 + zone * 53);
            double minX = 72 + zone * Math.max(60, (width - 144) / 5.0);
            double maxX = Math.min(width - 72, minX + Math.max(50, (width - 144) / 5.0 - 16));
            actor.targetX = minX + hash % Math.max(1, (int) (maxX - minX));
            actor.targetY = 72 + (hash / 97) % Math.max(1, height - 144);
        }
    }

    private int zoneFor(PersonActivity activity) {
        if (activity == PersonActivity.SLEEPING) return 0;
        if (activity == PersonActivity.WORKING || activity == PersonActivity.GUARDING) return 1;
        if (activity == PersonActivity.SHOPPING) return 2;
        if (activity == PersonActivity.SOCIALIZING) return 3;
        return 4;
    }

    private long positiveHash(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return value & Long.MAX_VALUE;
    }

    public long getSettlementId() { return settlementId; }
    public List<LocalActor> getActors() { return Collections.unmodifiableList(new ArrayList<>(actors)); }
}
