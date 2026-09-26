package data.order.suffix;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import data.order.OrderSuffix;
import java.util.List;

public class PrioritySuffix implements OrderSuffix {

	@Override
	public AssignmentTargetAPI selectTarget(AssignmentTargetAPI target, List<? extends CombatEntityAPI> candidates,
			boolean continuing, String[] params, CombatEngineAPI engine) {
		return target;
	}

	@Override
	public float resolvePriority(float current, AssignmentTargetAPI target, String[] params, CombatEngineAPI engine) {
		return Float.parseFloat(params[0]);
	}
}
