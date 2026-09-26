package data.automation.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.automation.BattleNext_OrderUtils;
import data.automation.OrderFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class HullSizeFilter implements OrderFilter {

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, String[] params, CombatEngineAPI engine) {
		Set<ShipAPI.HullSize> sizes = EnumSet.noneOf(ShipAPI.HullSize.class);
		for (String param : params) {
			ShipAPI.HullSize hullSize = BattleNext_OrderUtils.hullSize(param);
			if (hullSize != null) {
				sizes.add(hullSize);
			}
		}
		if (sizes.isEmpty() && !targets.isEmpty()) {
			if (Arrays.asList(params).contains("largest")) {
				sizes.add(targets.stream().map(target -> ((ShipAPI)target).getHullSize()).max(Comparator.comparingInt(Enum::ordinal)).get());
			}
			if (Arrays.asList(params).contains("smallest")) {
				sizes.add(targets.stream().map(target -> ((ShipAPI)target).getHullSize()).min(Comparator.comparingInt(Enum::ordinal)).get());
			}
		}
		targets.removeIf(target -> !sizes.contains(((ShipAPI)target).getHullSize()));
	}
}
