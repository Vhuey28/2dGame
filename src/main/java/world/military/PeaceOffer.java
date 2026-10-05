package world.military;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated collection of terms applied atomically when peace is accepted. */
public final class PeaceOffer {
    public long id;
    public long warId;
    public long proposerRealmId;
    public long recipientRealmId;
    public final List<PeaceTerm> terms = new ArrayList<>();
    public long createdMinute;
    public OfferState state = OfferState.PENDING;
    public String evaluation = "Not evaluated";

    public PeaceOffer(long id, long warId, long proposerRealmId, long recipientRealmId, long createdMinute) {
        this.id = id;
        this.warId = warId;
        this.proposerRealmId = proposerRealmId;
        this.recipientRealmId = recipientRealmId;
        this.createdMinute = createdMinute;
    }

    public List<PeaceTerm> viewTerms() { return Collections.unmodifiableList(terms); }
    public enum OfferState { PENDING, ACCEPTED, REJECTED, INVALID }

    public static final class PeaceTerm {
        public final TermType type;
        public final Long provinceId;
        public final long payerRealmId;
        public final long receiverRealmId;
        public final long amount;

        public PeaceTerm(TermType type, Long provinceId, long payerRealmId,
                long receiverRealmId, long amount) {
            this.type = type;
            this.provinceId = provinceId;
            this.payerRealmId = payerRealmId;
            this.receiverRealmId = receiverRealmId;
            this.amount = amount;
        }
    }

    public enum TermType { WHITE_PEACE, TRANSFER_PROVINCE, REPARATIONS, VASSALIZATION, INDEPENDENCE }
}
