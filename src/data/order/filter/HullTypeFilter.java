package data.order.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import data.order.BattleNext_OrderFilter;
import data.order.BattleNext_OrderStarter.TargetKind;
import data.order.BattleNext_OrderUtils;

import java.util.List;

public class HullTypeFilter implements BattleNext_OrderFilter {

	private List<String> requestedTypes;

	@Override
	public TargetKind getTargetKind() {
		return TargetKind.SHIP;
	}

	@Override
	public void configure(List<String> params) {
		BattleNext_OrderUtils.requireCount(params, 1, Integer.MAX_VALUE);
		List<String> allowed = List.of(BattleNext_OrderUtils.HULL_TYPE_ANY,
				BattleNext_OrderUtils.HULL_TYPE_PHASE, BattleNext_OrderUtils.HULL_TYPE_CARRIER);
		for (String param : params) {
			if (!allowed.contains(param)) throw new IllegalArgumentException("Unknown hull type: " + param);
		}
		requestedTypes = List.copyOf(params);
	}

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatEngineAPI engine) {
		if (requestedTypes.contains(BattleNext_OrderUtils.HULL_TYPE_ANY)) return;
		targets.removeIf(target -> {
			ShipAPI ship = (ShipAPI) target;
			boolean phase = requestedTypes.contains(BattleNext_OrderUtils.HULL_TYPE_PHASE)
					&& ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.PHASE);
			boolean carrier = requestedTypes.contains(BattleNext_OrderUtils.HULL_TYPE_CARRIER)
					&& ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CARRIER);
			return !phase && !carrier;
		});
	}
}
