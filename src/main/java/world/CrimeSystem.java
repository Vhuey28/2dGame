package world;

import java.util.ArrayList;
import java.util.List;

import world.event.WorldEvent;
import world.geography.Settlement;

/** Daily individual crime simulation with persistent, local economic and security consequences. */
public final class CrimeSystem {
    private final SimulationContext context;
    private final WorldState world;

    public CrimeSystem(SimulationContext context) {
        this.context = context;
        this.world = context.getWorld();
    }

    public void processDay(long minute) {
        SeededRandom random = context.getRandom("CRIME");
        for (Settlement settlement : world.geography.getSettlements().values()) {
            List<Long> residents = new ArrayList<>(world.indexes.livingPeopleAt(settlement.id));
            if (residents.size() < 2) continue;
            double risk = Math.max(0.01, 0.12 + settlement.unrest * 0.3 - settlement.security * 0.1);
            int attempts = Math.min(3, Math.max(1, (int) Math.ceil(residents.size() * risk / 20.0)));
            for (int i = 0; i < attempts; i++) {
                long offenderId = residents.get(random.nextInt(residents.size()));
                long victimId = residents.get(random.nextInt(residents.size()));
                if (victimId == offenderId) victimId = residents.get((residents.indexOf(victimId) + 1) % residents.size());
                Person offender = world.people.get(offenderId);
                CrimeIncident.CrimeType type = offender != null && offender.type == Person.PersonType.SOLDIER
                        ? CrimeIncident.CrimeType.ASSAULT : CrimeIncident.CrimeType.THEFT;
                int severity = 1 + random.nextInt(5);
                boolean discovered = random.nextDouble() < Math.max(0.15, settlement.security);
                CrimeIncident incident = new CrimeIncident(world.idGenerator.next(), settlement.id,
                        offenderId, victimId, type, minute, discovered, severity);
                world.crimeIncidents.put(incident.id, incident);
                settlement.unrest = clamp01(settlement.unrest + severity * 0.005);
                settlement.security = clamp01(settlement.security - severity * 0.003);
                Person victim = world.people.get(victimId);
                if (victim != null && type == CrimeIncident.CrimeType.THEFT) {
                    victim.wealth.netWorth = Math.max(0.0, victim.wealth.netWorth - severity * 2.0);
                    if (offender != null) offender.wealth.netWorth += severity * 2.0;
                }
                if (discovered) {
                    settlement.treasury.add(severity);
                    context.eventBus.publish(new WorldEvent(world.idGenerator.next(), minute, "CRIME_DISCOVERED"));
                }
            }
        }
        trim(minute);
    }

    private void trim(long minute) {
        long cutoff = minute - 5L * WorldConfig.MINUTES_PER_YEAR;
        world.crimeIncidents.values().removeIf(incident -> incident.minute < cutoff);
    }

    private double clamp01(double value) { return Math.max(0.0, Math.min(1.0, value)); }
}
