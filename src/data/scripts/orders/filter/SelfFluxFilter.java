package data.scripts.orders.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.BattleNext_OrderUtils;
import data.scripts.orders.OrderFilter;
import java.util.List;

public class SelfFluxFilter<E> implements OrderFilter<E> {

	@Override
	public void filterTargets(ShipAPI source, List<E> targets, String[] params, CombatEngineAPI engine) {
		float minFlux = -1f;
		float maxFlux = params.length == 1 ? BattleNext_OrderUtils.factor(params[0]) : BattleNext_OrderUtils.factor(params[1]);
		if (params.length > 1) {
			minFlux = BattleNext_OrderUtils.factor(params[0]);
		}
		if (maxFlux <= 0f) {
			targets.clear();
			return;
		}
		if (BattleNext_OrderUtils.percent(maxFlux) ? source.getFluxLevel() > maxFlux : source.getCurrFlux() > maxFlux) {
			targets.clear();
			return;
		}
		if (minFlux >= 0f && (BattleNext_OrderUtils.percent(minFlux) ? source.getFluxLevel() < minFlux : source.getCurrFlux() < minFlux)) {
			targets.clear();
		}
	}
}
