package data.automation.suffix;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import data.automation.OrderSuffix;

public class PrioritySuffix implements OrderSuffix {

	@Override
	public float getFloatResultWithTarget(AssignmentTargetAPI target, String[] params, Object custom, CombatEngineAPI engine) {
		return Float.parseFloat(params[0]);
	}

	@Override
	public Object getCustomResultWithTarget(AssignmentTargetAPI target, String[] params, Object custom, CombatEngineAPI engine) {
		return null;
	}
}
