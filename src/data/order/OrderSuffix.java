package data.automation;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;

public interface OrderSuffix {

	float getFloatResultWithTarget(AssignmentTargetAPI target, String[] params, Object custom, CombatEngineAPI engine);
	Object getCustomResultWithTarget(AssignmentTargetAPI target, String[] params, Object custom, CombatEngineAPI engine);
}
