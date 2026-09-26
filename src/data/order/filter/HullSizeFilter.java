package data.order.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderUtils;
import data.order.OrderFilter;

import java.util.*;

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
		if (Arrays.asList(params).contains(BattleNext_OrderUtils.HULL_SIZE_LARGEST) || Arrays.asList(params).contains(BattleNext_OrderUtils.HULL_SIZE_SMALLEST)) {
			Set<ShipAPI.HullSize> eligible = EnumSet.noneOf(ShipAPI.HullSize.class);
			for (CombatEntityAPI target : targets) {
				ShipAPI.HullSize size = ((ShipAPI)target).getHullSize();
				if (sizes.isEmpty() || sizes.contains(size)) eligible.add(size);
			}
			if (!eligible.isEmpty()) {
				ShipAPI.HullSize selected = Arrays.asList(params).contains(BattleNext_OrderUtils.HULL_SIZE_LARGEST)
						? eligible.stream().max(Comparator.comparingInt(Enum::ordinal)).get()
						: eligible.stream().min(Comparator.comparingInt(Enum::ordinal)).get();
				sizes.clear();
				sizes.add(selected);
			} else {
				sizes.clear();
			}
		}
		targets.removeIf(target -> !sizes.contains(((ShipAPI)target).getHullSize()));
	}
}
