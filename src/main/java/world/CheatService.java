package world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import world.command.CommandResult;
import world.economy.GoodType;
import world.event.WorldEvent;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.military.Regiment;
import world.politics.Title;

/** Explicit developer-only mutations used by the F1 testing menu. */
public final class CheatService {
    private final WorldSimulation simulation;
    private final WorldState world;

    public CheatService(WorldSimulation simulation) {
        this.simulation = simulation;
        this.world = simulation.getWorld();
    }

    public CommandResult addFood(long settlementId, int amount) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (settlement == null || amount <= 0) return rejected("Select a settlement");
        settlement.publicStockpile.add(GoodType.GRAIN, amount);
        settlement.publicStockpile.add(GoodType.VEGETABLES, amount);
        return accepted("Added " + amount + " grain and vegetables to " + settlement.name);
    }

    public CommandResult addPlayerGold(long amount) {
        if (world.player == null) return rejected("No campaign player");
        WorldParty party = world.parties.get(world.player.partyId);
        if (party == null || amount < 0) return rejected("Player party unavailable");
        party.money.add(amount);
        return accepted("Added " + amount + " coins");
    }

    public CommandResult addPlayerCargo(int amount) {
        if (world.player == null) return rejected("No campaign player");
        WorldParty party = world.parties.get(world.player.partyId);
        if (party == null || amount <= 0) return rejected("Player party unavailable");
        for (GoodType good : GoodType.values()) party.cargo.add(good, amount);
        party.cargoCapacity = Math.max(party.cargoCapacity, party.cargo.totalQuantity() + 100);
        world.player.cargoCapacity = party.cargoCapacity;
        return accepted("Added all goods and expanded cargo capacity");
    }

    public CommandResult healPlayer() {
        if (world.player == null) return rejected("No campaign player");
        Person person = world.people.get(world.player.personId);
        if (person == null) return rejected("Player person unavailable");
        person.health.healthLevel = 100.0;
        person.health.injured = false;
        person.health.wounded = false;
        return accepted("Player healed");
    }

    public CommandResult spawnArmy(long settlementId, int requested) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (settlement == null || settlement.controllerRealmId == null) return rejected("Settlement needs an owner");
        List<Person> recruits = availableResidents(settlementId, requested);
        if (recruits.isEmpty()) return rejected("No available residents");
        long realmId = settlement.controllerRealmId;
        Regiment regiment = new Regiment(world.idGenerator.next(), realmId,
                Regiment.RegimentType.LEVY_INFANTRY, settlementId);
        for (Person person : recruits) {
            person.preMilitaryType = person.type;
            person.type = Person.PersonType.SOLDIER;
            person.regimentId = regiment.id;
            regiment.soldierPersonIds.add(person.id);
        }
        regiment.officerPersonId = recruits.get(0).id;
        world.regiments.put(regiment.id, regiment);
        Army army = new Army(world.idGenerator.next(), realmId, recruits.get(0).id, settlementId,
                new WorldPosition(settlement.position.x, settlement.position.y));
        army.regimentIds.add(regiment.id);
        army.order = Army.ArmyOrder.DEFEND;
        army.supplies.add(GoodType.GRAIN, recruits.size() * 20);
        world.armies.put(army.id, army);
        publish("CHEAT_ARMY_SPAWNED");
        return accepted("Spawned army " + army.id + " with " + recruits.size() + " troops");
    }

    public CommandResult addTroops(long settlementId, int requested) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (settlement == null || settlement.controllerRealmId == null) return rejected("Select an owned settlement");
        Army army = world.armies.values().stream()
                .filter(value -> value.realmId == settlement.controllerRealmId
                        && value.state != Army.ArmyState.DISBANDED)
                .min(Comparator.comparingLong(value -> value.id)).orElse(null);
        if (army == null) return spawnArmy(settlementId, requested);
        List<Person> recruits = availableResidents(settlementId, requested);
        if (recruits.isEmpty()) return rejected("No available residents");
        Regiment regiment = new Regiment(world.idGenerator.next(), army.realmId,
                Regiment.RegimentType.LEVY_INFANTRY, settlementId);
        for (Person person : recruits) {
            person.preMilitaryType = person.type;
            person.type = Person.PersonType.SOLDIER;
            person.regimentId = regiment.id;
            regiment.soldierPersonIds.add(person.id);
        }
        regiment.officerPersonId = recruits.get(0).id;
        world.regiments.put(regiment.id, regiment);
        army.regimentIds.add(regiment.id);
        return accepted("Added " + recruits.size() + " troops to army " + army.id);
    }

    public CommandResult spawnCaravan(long settlementId) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        if (settlement == null) return rejected("Select a settlement");
        Person leader = world.people.values().stream()
                .filter(person -> person.alive && person.currentSettlementId != null
                        && person.currentSettlementId == settlementId && person.householdId != null)
                .min(Comparator.comparingLong(person -> person.id)).orElse(null);
        if (leader == null) return rejected("No resident can lead a caravan");
        Caravan caravan = new Caravan(world.idGenerator.next(), leader.householdId, leader.id,
                settlementId, new WorldPosition(settlement.position.x, settlement.position.y));
        caravan.cargo.add(GoodType.GRAIN, 40);
        caravan.cash.add(500);
        world.caravans.put(caravan.id, caravan);
        return accepted("Spawned caravan " + caravan.id);
    }

    public CommandResult makePlayerKing(long realmId) {
        if (world.player == null) return rejected("No campaign player");
        return setKing(realmId, world.player.personId);
    }

    public CommandResult setKing(long realmId, long personId) {
        Realm realm = world.realms.get(realmId);
        Person person = world.people.get(personId);
        if (realm == null || person == null || !person.alive) return rejected("Realm or ruler unavailable");
        realm.rulerPersonId = personId;
        person.type = Person.PersonType.NOBLE;
        Title title = world.titles.get(realm.rulerTitleId);
        if (title != null) title.installHolder(personId, minute(), "CHEAT_MENU");
        publish("CHEAT_RULER_CHANGED");
        return accepted(person.givenName + " is now ruler of " + realm.name);
    }

    public CommandResult changeSettlementOwner(long settlementId, long realmId) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        Realm realm = world.realms.get(realmId);
        if (settlement == null || realm == null) return rejected("Settlement or realm unavailable");
        settlement.controllerRealmId = realmId;
        settlement.occupyingRealmId = null;
        publish("CHEAT_SETTLEMENT_TRANSFERRED");
        return accepted(settlement.name + " now belongs to " + realm.name);
    }

    public CommandResult appointLord(long settlementId, long personId) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        Person person = world.people.get(personId);
        if (settlement == null || person == null || settlement.controllerRealmId == null) {
            return rejected("Settlement, owner, or person unavailable");
        }
        person.type = Person.PersonType.NOBLE;
        Title title = new Title(world.idGenerator.next(), "Lordship of " + settlement.name,
                Title.Rank.BARONY, settlement.controllerRealmId);
        title.deJureProvinceId = settlement.provinceId;
        title.installHolder(person.id, minute(), "CHEAT_APPOINTMENT");
        world.titles.put(title.id, title);
        return accepted(person.givenName + " appointed lord of " + settlement.name);
    }

    public CommandResult toggleWar(long firstRealmId, long secondRealmId) {
        if (firstRealmId == secondRealmId || !world.realms.containsKey(firstRealmId)
                || !world.realms.containsKey(secondRealmId)) return rejected("Choose two different realms");
        War active = world.wars.values().stream().filter(war -> war.state == War.WarState.ACTIVE
                && war.opposing(firstRealmId, secondRealmId)).findFirst().orElse(null);
        if (active != null) {
            active.state = War.WarState.ENDED;
            active.endMinute = minute();
            return accepted("Ended war " + active.id);
        }
        War war = new War(world.idGenerator.next(), firstRealmId, secondRealmId, War.WarGoal.PUNITIVE, minute());
        war.declarationReason = "CHEAT_MENU";
        world.wars.put(war.id, war);
        return accepted("Started war " + war.id);
    }

    public CommandResult triggerCrime(long settlementId) {
        Settlement settlement = world.geography.getSettlement(settlementId);
        List<Person> residents = world.people.values().stream().filter(person -> person.alive
                && person.currentSettlementId != null && person.currentSettlementId == settlementId)
                .sorted(Comparator.comparingLong(person -> person.id)).toList();
        if (settlement == null || residents.size() < 2) return rejected("Not enough residents");
        CrimeIncident incident = new CrimeIncident(world.idGenerator.next(), settlementId,
                residents.get(0).id, residents.get(1).id, CrimeIncident.CrimeType.THEFT,
                minute(), true, 5);
        world.crimeIncidents.put(incident.id, incident);
        settlement.security = Math.max(0.0, settlement.security - 0.1);
        settlement.unrest = Math.min(1.0, settlement.unrest + 0.1);
        publish("CRIME_DISCOVERED");
        return accepted("Triggered theft in " + settlement.name);
    }

    public CommandResult completePlayerContracts() {
        if (world.player == null) return rejected("No campaign player");
        WorldParty party = world.parties.get(world.player.partyId);
        int completed = 0;
        for (Long id : new ArrayList<>(world.player.acceptedContractIds)) {
            Contract contract = world.contracts.get(id);
            if (contract == null || contract.status != Contract.ContractStatus.ACTIVE) continue;
            party.money.add(contract.escrowCoins);
            contract.escrowCoins = 0;
            contract.status = Contract.ContractStatus.COMPLETED;
            contract.resolvedMinute = minute();
            world.player.acceptedContractIds.remove(id);
            completed++;
        }
        return accepted("Completed " + completed + " active contracts");
    }

    private List<Person> availableResidents(long settlementId, int count) {
        return world.people.values().stream().filter(person -> person.alive && person.regimentId == null
                && person.currentSettlementId != null && person.currentSettlementId == settlementId
                && !person.isChild(minute())).sorted(Comparator.comparingLong(person -> person.id))
                .limit(Math.max(1, count)).toList();
    }

    private long minute() { return simulation.getClock().getWorldMinute(); }
    private void publish(String type) {
        simulation.getContext().eventBus.publish(new WorldEvent(world.idGenerator.next(), minute(), type));
    }
    private CommandResult accepted(String message) { return new CommandResult(true, "CHEAT", message); }
    private CommandResult rejected(String message) { return CommandResult.rejected("CHEAT_INVALID", message); }
}
