package data.order;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;

import java.util.List;

public interface OrderSuffix {

	AssignmentTargetAPI selectTarget(AssignmentTargetAPI target, List<? extends CombatEntityAPI> candidates,
			boolean continuing, String[] params, CombatEngineAPI engine);

	float resolvePriority(float current, AssignmentTargetAPI target, String[] params, CombatEngineAPI engine);
}
