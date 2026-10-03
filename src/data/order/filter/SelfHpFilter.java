package data.order.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderFilter;
import data.order.BattleNext_OrderRange;
import data.order.BattleNext_OrderStarter.TargetKind;

import java.util.List;

public class SelfHpFilter implements BattleNext_OrderFilter {

	private BattleNext_OrderRange range;

	@Override
	public TargetKind getTargetKind() {
		return TargetKind.ANY;
	}

	@Override
	public void configure(List<String> params) {
		range = BattleNext_OrderRange.compile(params, true, false);
	}

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatEngineAPI engine) {
		if (!range.contains(source.getHitpoints(), source.getHullLevel(), 0f)) {
			targets.clear();
		}
	}
}
