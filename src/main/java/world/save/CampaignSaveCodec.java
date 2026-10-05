package world.save;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import world.CampaignSession;
import world.Contract;
import world.PlayerCampaignState;
import world.WorldParty;
import world.WorldState;
import world.economy.GoodType;
import world.economy.Inventory;
import world.geography.Settlement;
import world.geography.WorldPosition;
import world.politics.Government;

/** Versioned Phase 4 campaign checkpoint. Derived caches are rebuilt rather than serialized. */
public final class CampaignSaveCodec {
    private static final int MAGIC = 0x43435134; // CCQ4
    private static final int VERSION = 3;

    private CampaignSaveCodec() {}

    public static void save(CampaignSession session, Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Path backup = absolute.resolveSibling(absolute.getFileName() + ".bak");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            write(session, out);
        }
        // Parse before replacing the known-good save.
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(temporary)))) {
            validateHeader(in);
        }
        if (Files.exists(absolute)) Files.move(absolute, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static CampaignSession load(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            validateHeader(in);
            long seed = in.readLong();
            long minute = in.readLong();
            int speed = in.readInt();
            boolean paused = in.readBoolean();
            long nextId = in.readLong();
            CampaignSession session = new CampaignSession(seed);
            session.setWorldPaused(false);
            if (minute > 0L) session.getSimulation().advanceMinutes(minute);
            restoreWorld(session, in);
            session.getWorld().idGenerator.restoreNextId(nextId);
            session.setSpeed(speed);
            session.setWorldPaused(paused);
            session.rebuildDerivedStateAfterLoad();
            return session;
        }
    }

    private static void write(CampaignSession session, DataOutputStream out) throws IOException {
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        WorldParty party = world.parties.get(player.partyId);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeLong(session.getSeed());
        out.writeLong(session.getSimulation().getClock().getWorldMinute());
        out.writeInt(session.getSimulation().getClock().getSpeed());
        out.writeBoolean(session.getSimulation().getClock().isPaused());
        out.writeLong(world.idGenerator.getNextId());

        out.writeLong(player.personId);
        out.writeLong(player.householdId);
        out.writeLong(player.partyId);
        out.writeLong(player.currentSettlementId);
        out.writeInt(player.cargoCapacity);
        out.writeInt(party.memberPersonIds.size());
        for (Long memberId : party.memberPersonIds) out.writeLong(memberId);
        writeInventory(out, party.cargo);
        out.writeLong(party.money.copperCoins);
        writeMap(out, player.settlementReputation);
        writeMap(out, player.realmReputation);
        out.writeLong(player.affiliatedRealmId == null ? -1L : player.affiliatedRealmId);
        out.writeUTF(player.kingdomRole.name());
        out.writeInt(world.realms.size());
        for (world.Realm realm : world.realms.values()) {
            out.writeLong(realm.id);
            out.writeLong(realm.rulerPersonId == null ? -1L : realm.rulerPersonId);
            Government government = world.governments.get(realm.governmentId);
            for (Government.LawType law : Government.LawType.values()) {
                out.writeInt(government == null ? 1 : government.getLawLevel(law));
            }
        }

        out.writeInt(world.geography.getSettlements().size());
        for (Settlement settlement : world.geography.getSettlements().values()) {
            out.writeLong(settlement.id);
            out.writeLong(settlement.treasury.copperCoins);
            out.writeDouble(settlement.security);
            out.writeDouble(settlement.sanitation);
            out.writeDouble(settlement.prosperity);
            out.writeDouble(settlement.unrest);
            writeInventory(out, settlement.publicStockpile);
        }

        out.writeInt(world.contracts.size());
        for (Contract contract : world.contracts.values()) {
            out.writeLong(contract.id);
            out.writeUTF(contract.type.name());
            out.writeLong(contract.issuerSettlementId);
            out.writeLong(contract.destinationSettlementId);
            out.writeLong(contract.createdMinute);
            out.writeLong(contract.deadlineMinute);
            out.writeLong(contract.rewardCoins);
            out.writeLong(contract.penaltyCoins);
            out.writeInt(contract.requiredGood == null ? -1 : contract.requiredGood.ordinal());
            out.writeInt(contract.requiredQuantity);
            out.writeUTF(contract.status.name());
            out.writeLong(contract.takerPartyId == null ? -1L : contract.takerPartyId);
            out.writeLong(contract.resolvedMinute);
            out.writeLong(contract.escrowCoins);
        }
    }

    private static void restoreWorld(CampaignSession session, DataInputStream in) throws IOException {
        WorldState world = session.getWorld();
        PlayerCampaignState player = session.getPlayerState();
        long personId = in.readLong();
        long householdId = in.readLong();
        long partyId = in.readLong();
        if (personId != player.personId || householdId != player.householdId || partyId != player.partyId) {
            throw new IOException("Generated identity does not match save seed");
        }
        long settlementId = in.readLong();
        player.currentSettlementId = settlementId;
        player.cargoCapacity = in.readInt();
        WorldParty party = world.parties.get(player.partyId);
        party.memberPersonIds.clear();
        int memberCount = in.readInt();
        for (int i = 0; i < memberCount; i++) {
            long memberId = in.readLong();
            if (!world.people.containsKey(memberId)) throw new IOException("Unknown party member " + memberId);
            party.memberPersonIds.add(memberId);
            world.people.get(memberId).travelingPartyId = party.id;
            world.people.get(memberId).currentSettlementId = settlementId;
        }
        readInventory(in, party.cargo);
        party.cargoCapacity = player.cargoCapacity;
        party.money.copperCoins = in.readLong();
        player.settlementReputation.clear();
        readMap(in, player.settlementReputation);
        player.realmReputation.clear();
        readMap(in, player.realmReputation);
        long affiliatedRealmId = in.readLong();
        player.affiliatedRealmId = affiliatedRealmId < 0 ? null : affiliatedRealmId;
        try {
            player.kingdomRole = PlayerCampaignState.KingdomRole.valueOf(in.readUTF());
        } catch (IllegalArgumentException invalidRole) {
            throw new IOException("Unknown player kingdom role", invalidRole);
        }
        if (player.affiliatedRealmId != null && !world.realms.containsKey(player.affiliatedRealmId)) {
            throw new IOException("Unknown affiliated realm " + player.affiliatedRealmId);
        }
        int realmCount = in.readInt();
        for (int i = 0; i < realmCount; i++) {
            long realmId = in.readLong();
            world.Realm realm = world.realms.get(realmId);
            if (realm == null) throw new IOException("Save references unknown realm " + realmId);
            long rulerPersonId = in.readLong();
            realm.rulerPersonId = rulerPersonId < 0 ? null : rulerPersonId;
            if (realm.rulerPersonId != null && !world.people.containsKey(realm.rulerPersonId)) {
                throw new IOException("Save references unknown ruler " + realm.rulerPersonId);
            }
            Government government = world.governments.get(realm.governmentId);
            for (Government.LawType law : Government.LawType.values()) {
                int level = in.readInt();
                if (government != null) government.setLawLevel(law, level);
            }
        }
        player.reputation = player.settlementReputation.values().stream().mapToInt(Integer::intValue).sum();
        party.currentSettlementId = settlementId;
        party.destinationSettlementId = null;
        party.state = WorldParty.PartyState.AT_SETTLEMENT;
        Settlement current = world.geography.getSettlement(settlementId);
        if (current != null) party.position = new WorldPosition(current.position.x, current.position.y);
        world.people.get(player.personId).currentSettlementId = settlementId;
        world.people.get(player.personId).homeSettlementId = settlementId;
        world.households.get(player.householdId).homeSettlementId = settlementId;

        int settlementCount = in.readInt();
        for (int i = 0; i < settlementCount; i++) {
            long id = in.readLong();
            Settlement settlement = world.geography.getSettlement(id);
            if (settlement == null) throw new IOException("Save references unknown settlement " + id);
            settlement.treasury.copperCoins = in.readLong();
            settlement.security = in.readDouble();
            settlement.sanitation = in.readDouble();
            settlement.prosperity = in.readDouble();
            settlement.unrest = in.readDouble();
            readInventory(in, settlement.publicStockpile);
        }

        world.contracts.clear();
        player.acceptedContractIds.clear();
        int contractCount = in.readInt();
        for (int i = 0; i < contractCount; i++) {
            long id = in.readLong();
            Contract contract = new Contract(id, Contract.ContractType.valueOf(in.readUTF()),
                    in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong(),
                    readGood(in.readInt()), in.readInt());
            contract.status = Contract.ContractStatus.valueOf(in.readUTF());
            long taker = in.readLong();
            contract.takerPartyId = taker < 0 ? null : taker;
            contract.resolvedMinute = in.readLong();
            contract.escrowCoins = in.readLong();
            world.contracts.put(id, contract);
            if (contract.status == Contract.ContractStatus.ACTIVE && contract.takerPartyId != null
                    && contract.takerPartyId == player.partyId) player.acceptedContractIds.add(id);
        }
    }

    private static void validateHeader(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Not a Chronicle Conquest campaign save");
        int version = in.readInt();
        if (version != VERSION) throw new IOException("Unsupported campaign save version " + version);
    }

    private static void writeInventory(DataOutputStream out, Inventory inventory) throws IOException {
        for (GoodType good : GoodType.values()) out.writeInt(inventory.getQuantity(good));
    }

    private static void readInventory(DataInputStream in, Inventory inventory) throws IOException {
        for (GoodType good : GoodType.values()) {
            int current = inventory.getQuantity(good);
            if (current > 0) inventory.remove(good, current);
            inventory.add(good, in.readInt());
        }
    }

    private static GoodType readGood(int ordinal) throws IOException {
        if (ordinal < 0) return null;
        if (ordinal >= GoodType.values().length) throw new IOException("Unknown good ordinal " + ordinal);
        return GoodType.values()[ordinal];
    }

    private static void writeMap(DataOutputStream out, Map<Long, Integer> values) throws IOException {
        out.writeInt(values.size());
        for (Map.Entry<Long, Integer> entry : values.entrySet()) {
            out.writeLong(entry.getKey());
            out.writeInt(entry.getValue());
        }
    }

    private static void readMap(DataInputStream in, Map<Long, Integer> values) throws IOException {
        int count = in.readInt();
        for (int i = 0; i < count; i++) values.put(in.readLong(), in.readInt());
    }
}
