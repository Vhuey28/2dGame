package world;

import world.economy.Inventory;
import world.economy.MoneyAccount;

import java.util.ArrayList;
import java.util.List;

/**
 * Household model from world.md section 9.4.
 * Households pool food and housing. This reduces transaction volume
 * while preserving individual members.
 */
public final class Household {
    public long id;
    public Long homeBuildingId;
    public long homeSettlementId;
    public List<Long> memberIds = new ArrayList<>();
    public Inventory inventory = new Inventory();
    public MoneyAccount account = new MoneyAccount();
    public Long ownedWorkplaceId;
    public Long rentedPropertyId;
    public int socialStatus = 0;
    public double foodSecurity = 1.0;  // 0.0 - 1.0
    public double safety = 1.0;        // 0.0 - 1.0
    public Long headPersonId;         // Head of household

    public Household() {
        // Default constructor
    }

    public Household(long id, long homeSettlementId, Long headPersonId) {
        this.id = id;
        this.homeSettlementId = homeSettlementId;
        this.headPersonId = headPersonId;
        this.memberIds = new ArrayList<>();
        if (headPersonId != null) {
            memberIds.add(headPersonId);
        }
    }

    /** Add a member to this household. */
    public void addMember(long personId) {
        if (!memberIds.contains(personId)) {
            memberIds.add(personId);
        }
    }

    /** Remove a member from this household. */
    public void removeMember(long personId) {
        memberIds.remove(Long.valueOf(personId));
    }

    /** Get the number of members in this household. */
    public int getMemberCount() {
        return memberIds.size();
    }

    /** Check if a person is a member of this household. */
    public boolean isMember(long personId) {
        return memberIds.contains(Long.valueOf(personId));
    }

    /** Check if household has food security. */
    public boolean hasFoodSecurity() {
        return foodSecurity > 0.5;
    }

    @Override
    public String toString() {
        return String.format("Household[id=%d, head=%d, members=%d, settlementId=%d, foodSecurity=%.2f]",
            id, headPersonId != null ? headPersonId : -1, memberIds.size(), homeSettlementId, foodSecurity);
    }
}