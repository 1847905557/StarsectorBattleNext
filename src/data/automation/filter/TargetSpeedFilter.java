package data.scripts.orders.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.BattleNext_OrderUtils;
import data.scripts.orders.OrderFilter;
import java.util.List;

public class TargetSpeedFilter implements OrderFilter {

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, String[] params, CombatEngineAPI engine) {
		float minSpeed = -1f;
		float maxSpeed = params.length == 1 ? BattleNext_OrderUtils.self(params[0], source.getMaxSpeed()) : BattleNext_OrderUtils.self(params[1], source.getMaxSpeed());
		if (params.length > 1) {
			minSpeed = BattleNext_OrderUtils.self(params[0], source.getMaxSpeed());
		}
		if (maxSpeed <= 0f) {
			targets.clear();
			return;
		}
		final float lowerBound = minSpeed;
		final float upperBound = maxSpeed;
		targets.removeIf(target -> {
			ShipAPI ship = (ShipAPI)target;
			return ship.getMaxSpeed() > upperBound || ship.getMaxSpeed() < lowerBound;
		});
	}
}
