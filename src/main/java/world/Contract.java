package world;

import world.economy.GoodType;

/** Persistent simulation-generated work offer with explicit completion and failure terms. */
public final class Contract {
    public final long id;
    public final ContractType type;
    public final long issuerSettlementId;
    public final long destinationSettlementId;
    public final long createdMinute;
    public final long deadlineMinute;
    public final long rewardCoins;
    public final long penaltyCoins;
    public final GoodType requiredGood;
    public final int requiredQuantity;
    public ContractStatus status = ContractStatus.OPEN;
    public Long takerPartyId;
    public long resolvedMinute = -1L;
    public long escrowCoins;

    public Contract(long id, ContractType type, long issuerSettlementId, long destinationSettlementId,
            long createdMinute, long deadlineMinute, long rewardCoins, long penaltyCoins,
            GoodType requiredGood, int requiredQuantity) {
        this.id = id;
        this.type = type;
        this.issuerSettlementId = issuerSettlementId;
        this.destinationSettlementId = destinationSettlementId;
        this.createdMinute = createdMinute;
        this.deadlineMinute = deadlineMinute;
        this.rewardCoins = rewardCoins;
        this.penaltyCoins = penaltyCoins;
        this.requiredGood = requiredGood;
        this.requiredQuantity = requiredQuantity;
    }

    public enum ContractType { DELIVER_GOODS, DELIVER_MESSAGE, ESCORT_ROUTE }
    public enum ContractStatus { OPEN, ACTIVE, COMPLETED, FAILED, EXPIRED }
}
