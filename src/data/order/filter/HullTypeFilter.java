package data.order.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import data.order.BattleNext_OrderUtils;
import data.order.OrderFilter;
import java.util.Arrays;
import java.util.List;

public class HullTypeFilter implements OrderFilter {

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, String[] params, CombatEngineAPI engine) {
		List<String> requestedTypes = Arrays.asList(params);
		if (requestedTypes.contains(BattleNext_OrderUtils.HULL_TYPE_ANY)) {
			return;
		}
		targets.removeIf(target -> {
			ShipAPI ship = (ShipAPI)target;
			boolean phase = requestedTypes.contains(BattleNext_OrderUtils.HULL_TYPE_PHASE) && ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.PHASE);
			boolean carrier = requestedTypes.contains(BattleNext_OrderUtils.HULL_TYPE_CARRIER) && ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CARRIER);
			return !phase && !carrier;
		});
	}
}
