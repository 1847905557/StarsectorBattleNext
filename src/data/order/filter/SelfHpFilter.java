package data.automation.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.automation.BattleNext_OrderUtils;
import data.automation.OrderFilter;
import java.util.List;

public class SelfHpFilter implements OrderFilter {

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, String[] params, CombatEngineAPI engine) {
		float minHp = -1f;
		float maxHp = params.length == 1 ? BattleNext_OrderUtils.factor(params[0]) : BattleNext_OrderUtils.factor(params[1]);
		if (params.length > 1) {
			minHp = BattleNext_OrderUtils.factor(params[0]);
		}
		if (maxHp <= 0f) {
			targets.clear();
			return;
		}
		if (BattleNext_OrderUtils.percent(maxHp) ? source.getHullLevel() > maxHp : source.getHitpoints() > maxHp) {
			targets.clear();
			return;
		}
		if (minHp >= 0f && (BattleNext_OrderUtils.percent(minHp) ? source.getHullLevel() < minHp : source.getHitpoints() < minHp)) {
			targets.clear();
		}
	}
}
