package data.scripts.orders.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.BattleNext_OrderUtils;
import data.scripts.orders.OrderFilter;
import java.util.List;

public class TargetHpFilter implements OrderFilter<ShipAPI> {

	@Override
	public void filterTargets(ShipAPI source, List<ShipAPI> targets, String[] params, CombatEngineAPI engine) {
		float minHp = -1f;
		float maxHp = params.length == 1 ? BattleNext_OrderUtils.factor(params[0]) : BattleNext_OrderUtils.factor(params[1]);
		if (params.length > 1) {
			minHp = BattleNext_OrderUtils.factor(params[0]);
		}
		if (maxHp <= 0f) {
			targets.clear();
			return;
		}
		final float lowerBound = minHp;
		final float upperBound = maxHp;
		targets.removeIf(target -> {
			float current = BattleNext_OrderUtils.percent(upperBound) ? target.getHullLevel() : target.getHitpoints();
			float minimum = lowerBound < 0f ? Float.NEGATIVE_INFINITY : BattleNext_OrderUtils.percent(lowerBound) ? target.getHullLevel() : target.getHitpoints();
			return current > upperBound || minimum < lowerBound;
		});
	}
}
