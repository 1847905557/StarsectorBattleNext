package data.order;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;

import java.util.List;

public interface BattleNext_OrderSuffix {

	void configure(List<String> params);

	AssignmentTargetAPI selectTarget(AssignmentTargetAPI target, List<? extends CombatEntityAPI> candidates,
			AssignmentTargetAPI previousTarget, CombatEngineAPI engine);

	float resolvePriority(float current, AssignmentTargetAPI target, CombatEngineAPI engine);
}
