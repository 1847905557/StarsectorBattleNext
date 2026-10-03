package data.order.suffix;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import data.order.BattleNext_OrderSuffix;
import data.order.BattleNext_OrderUtils;

import java.util.List;

public class PersistSuffix implements BattleNext_OrderSuffix {

	@Override
	public void configure(List<String> params) {
		BattleNext_OrderUtils.requireCount(params, 0, 0);
	}

	@Override
	public AssignmentTargetAPI selectTarget(AssignmentTargetAPI target, List<? extends CombatEntityAPI> candidates,
			AssignmentTargetAPI previousTarget, CombatEngineAPI engine) {
		if (previousTarget != null && BattleNext_OrderUtils.isValidTarget(previousTarget, engine)
				&& candidates.contains(BattleNext_OrderUtils.toEntity(previousTarget))) {
			return previousTarget;
		}
		return target;
	}

	@Override
	public float resolvePriority(float current, AssignmentTargetAPI target, CombatEngineAPI engine) {
		return current;
	}
}
