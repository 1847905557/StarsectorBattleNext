package data.order.suffix;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import data.order.BattleNext_OrderSuffix;
import data.order.BattleNext_OrderUtils;

import java.util.List;

public class PrioritySuffix implements BattleNext_OrderSuffix {

	private float priority;

	@Override
	public void configure(List<String> params) {
		BattleNext_OrderUtils.requireCount(params, 1, 1);
		priority = BattleNext_OrderUtils.number(params.get(0));
	}

	@Override
	public AssignmentTargetAPI selectTarget(AssignmentTargetAPI target, List<? extends CombatEntityAPI> candidates,
			AssignmentTargetAPI previousTarget, CombatEngineAPI engine) {
		return target;
	}

	@Override
	public float resolvePriority(float current, AssignmentTargetAPI target, CombatEngineAPI engine) {
		return priority;
	}
}
