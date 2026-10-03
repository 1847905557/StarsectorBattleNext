package data.order.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderFilter;
import data.order.BattleNext_OrderRange;
import data.order.BattleNext_OrderStarter.TargetKind;

import java.util.List;

public class TargetHpFilter implements BattleNext_OrderFilter {

	private BattleNext_OrderRange range;

	@Override
	public TargetKind getTargetKind() {
		return TargetKind.SHIP;
	}

	@Override
	public void configure(List<String> params) {
		range = BattleNext_OrderRange.compile(params, true, false);
	}

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatEngineAPI engine) {
		targets.removeIf(target -> {
			ShipAPI ship = (ShipAPI) target;
			return !range.contains(ship.getHitpoints(), ship.getHullLevel(), 0f);
		});
	}
}
