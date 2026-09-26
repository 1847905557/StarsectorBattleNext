package data.order.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderUtils;
import data.order.OrderFilter;
import java.util.List;

public class TargetFluxFilter implements OrderFilter {

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, String[] params, CombatEngineAPI engine) {
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
			ShipAPI ship = (ShipAPI)target;
			if (ship.getFluxTracker().isOverloaded()) {
				return false;
			}
			float current = BattleNext_OrderUtils.percent(upperBound) ? ship.getFluxLevel() : ship.getCurrFlux();
			float minimum = lowerBound < 0f ? Float.NEGATIVE_INFINITY : BattleNext_OrderUtils.percent(lowerBound) ? ship.getFluxLevel() : ship.getCurrFlux();
			return current > upperBound || minimum < lowerBound;
		});
	}
}
