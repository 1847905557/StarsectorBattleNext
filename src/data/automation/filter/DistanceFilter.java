package data.scripts.orders.filter;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.OrderFilter;
import org.lazywizard.lazylib.MathUtils;
import java.util.List;

public class DistanceFilter implements OrderFilter {

	@Override
	public void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, String[] params, CombatEngineAPI engine) {
		float minRange = -1f;
		float maxRange = params.length == 1 ? Float.parseFloat(params[0]) : Float.parseFloat(params[1]);
		if (params.length > 1) {
			minRange = Float.parseFloat(params[0]);
		}
		if (maxRange <= 0f) {
			targets.clear();
			return;
		}
		final float lowerBound = minRange;
		final float upperBound = maxRange;
		targets.removeIf(target -> {
			float distance = MathUtils.getDistance(source, target);
			return distance > upperBound || distance < lowerBound;
		});
	}
}
