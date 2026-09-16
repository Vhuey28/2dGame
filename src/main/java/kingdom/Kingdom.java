package kingdom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Kingdom {
    public String name;
    public Character ruler;
    public List<Territory> territories = new ArrayList<>();
    public List<Kingdom> vassalKingdoms = new ArrayList<>();
    public Kingdom liegeKingdom;

    public int treasury = 1000;
    public int stability = 50;
    public int influence = 0;

    public SuccessionLaw successionLaw = SuccessionLaw.PRIMOGENITURE;
    public enum SuccessionLaw { PRIMOGENITURE, ELECTIVE }

    public Map<Kingdom, DiplomaticRelation> relations = new HashMap<>();
    public Map<Kingdom, Integer> espionageAdvantage = new HashMap<>();
    public Set<Kingdom> grievancesAgainst = new HashSet<>();
    public enum DiplomaticRelation { PEACE, WAR, ALLIANCE, TRUCE, VASSAL }

    public List<War> activeWars = new ArrayList<>();

    public int getTotalIncome() {
        int income = 0;
        for (Territory t : territories) income += t.getTaxIncome();
        return income;
    }

    public int getTotalLevy() {
        int levy = 0;
        for (Territory t : territories) levy += t.garrisonSize;
        return levy;
    }

    public int getEspionageAgainst(Kingdom other) {
        return espionageAdvantage.getOrDefault(other, 0);
    }
}
