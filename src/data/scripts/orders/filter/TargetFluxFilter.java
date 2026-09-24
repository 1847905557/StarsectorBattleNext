package data.scripts.orders.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.BattleNext_OrderUtils;
import data.scripts.orders.OrderFilter;
import java.util.List;

public class TargetFluxFilter implements OrderFilter<ShipAPI> {

	@Override
	public void filterTargets(ShipAPI source, List<ShipAPI> targets, String[] params, CombatEngineAPI engine) {
		float minFlux = -1f;
		float maxFlux = params.length == 1 ? BattleNext_OrderUtils.factor(params[0]) : BattleNext_OrderUtils.factor(params[1]);
		if (params.length > 1) {
			minFlux = BattleNext_OrderUtils.factor(params[0]);
		}
		if (maxFlux <= 0f) {
			targets.clear();
			return;
		}
		final float lowerBound = minFlux;
		final float upperBound = maxFlux;
		targets.removeIf(target -> {
			if (target.getFluxTracker().isOverloaded()) {
				return false;
			}
			float current = BattleNext_OrderUtils.percent(upperBound) ? target.getFluxLevel() : target.getCurrFlux();
			float minimum = lowerBound < 0f ? Float.NEGATIVE_INFINITY : BattleNext_OrderUtils.percent(lowerBound) ? target.getFluxLevel() : target.getCurrFlux();
			return current > upperBound || minimum < lowerBound;
		});
	}
}
