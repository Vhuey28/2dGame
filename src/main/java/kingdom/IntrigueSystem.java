package kingdom;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IntrigueSystem {
    public List<Scheme> activeSchemes = new ArrayList<>();
    public java.util.function.Consumer<String> onEvent = msg -> {};

    public void update(List<Kingdom> allKingdoms) {
        Iterator<Scheme> it = activeSchemes.iterator();
        while (it.hasNext()) {
            Scheme s = it.next();
            if (!s.active) {
                it.remove();
                continue;
            }

            rollDetection(s);
            if (s.discovered) continue;

            s.progress++;
            if (s.progress >= s.durationTicks) {
                resolveScheme(s);
                s.active = false;
            }
        }
        activeSchemes.removeIf(s -> !s.active);
    }

    public boolean startScheme(Scheme scheme) {
        boolean alreadyScheming = activeSchemes.stream().anyMatch(s -> s.schemer == scheme.schemer && s.active);
        if (alreadyScheming) return false;
        activeSchemes.add(scheme);
        return true;
    }

    private void rollDetection(Scheme s) {
        double baseDetectionChance = 0.03;
        double schemerSkill = s.schemer.intrigue / 300.0;
        double targetDefense = 0.0;

        if (s.target != null) {
            targetDefense += s.target.intrigue / 250.0;
        }
        if (s.targetKingdom != null && s.schemer.rulesKingdom != null) {
            targetDefense += s.targetKingdom.getEspionageAgainst(s.schemer.rulesKingdom) / 100.0;
        }

        double detectionChance = Math.max(0.01, baseDetectionChance - schemerSkill + targetDefense);
        if (Math.random() < detectionChance) {
            s.discovered = true;
            handleDiscovery(s);
        }
    }

    private void handleDiscovery(Scheme s) {
        if (s.target != null) {
            s.target.adjustOpinion(s.schemer, -40);
        }
        if (s.type == Scheme.Type.ASSASSINATE && s.target != null && s.target.rulesKingdom != null
            && s.schemer.rulesKingdom != null) {
            s.target.rulesKingdom.grievancesAgainst.add(s.schemer.rulesKingdom);
        }
        onEvent.accept(s.schemer.name + "'s " + s.type + " scheme against " + (s.target != null ? s.target.name : s.targetKingdom.name) + " was discovered");
        s.active = false;
    }

    private void resolveScheme(Scheme s) {
        double successChance = computeSuccessChance(s);
        boolean success = Math.random() < successChance;
        if (!success) return;

        switch (s.type) {
            case FABRICATE_CLAIM :
                if (!s.schemer.claims.contains(s.targetKingdom)) {
                    s.schemer.claims.add(s.targetKingdom);
                }
                onEvent.accept(s.schemer.name + " fabricated a claim on " + s.targetKingdom.name);
            break;
            case ASSASSINATE:
                if (s.target != null) {
                    s.target.alive = false;
                    if (s.target.rulesKingdom != null) {
                        s.target.rulesKingdom.stability -= 15;
                    }
                    onEvent.accept(s.target.name + " was assassinated by " + s.schemer.name);
                }
            break;
            case SPY_NETWORK:
                if (s.schemer.rulesKingdom != null && s.targetKingdom != null) {
                    s.schemer.rulesKingdom.espionageAdvantage.merge(s.targetKingdom, 15, Integer::sum);
                    onEvent.accept(s.schemer.rulesKingdom.name + " expanded its spy network against " + s.targetKingdom.name);
                }
            break;
        }
    }

    private double computeSuccessChance(Scheme s) {
        double base = 0;
        switch (s.type) {
            case FABRICATE_CLAIM :base= 0.7; break;
            case ASSASSINATE :base= 0.4;break;
            case SPY_NETWORK :base = 0.8;break;
        };
        base += s.schemer.intrigue / 100.0;
        if (s.target != null) base -= s.target.intrigue / 150.0;
        return Math.max(0.05, Math.min(0.95, base));
    }
}
