package data.scripts.orders.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import data.scripts.orders.OrderFilter;
import java.util.Arrays;
import java.util.List;

public class HullTypeFilter implements OrderFilter<ShipAPI> {

	@Override
	public void filterTargets(ShipAPI source, List<ShipAPI> targets, String[] params, CombatEngineAPI engine) {
		List<String> requestedTypes = Arrays.asList(params);
		if (requestedTypes.contains("any")) {
			return;
		}
		targets.removeIf(target -> {
			boolean phase = requestedTypes.contains("phase") && target.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.PHASE);
			boolean carrier = requestedTypes.contains("carrier") && target.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CARRIER);
			return !phase && !carrier;
		});
	}
}
