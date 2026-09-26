package data.order.suffix;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import data.order.BattleNext_OrderUtils;
import data.order.OrderSuffix;

public class PersistSuffix implements OrderSuffix {

	private AssignmentTargetAPI lastTarget;

	@Override
	public AssignmentTargetAPI selectTarget(AssignmentTargetAPI target, java.util.List<? extends CombatEntityAPI> candidates,
			boolean continuing, String[] params, CombatEngineAPI engine) {
		if (!continuing) {
			lastTarget = null;
		}
		if (lastTarget instanceof DeployedFleetMemberAPI
				&& !BattleNext_OrderUtils.isValid(((DeployedFleetMemberAPI)lastTarget).getShip())) {
			lastTarget = null;
		}
		if (lastTarget != null) {
			CombatEntityAPI candidate = lastTarget instanceof DeployedFleetMemberAPI
					? ((DeployedFleetMemberAPI)lastTarget).getShip()
					: lastTarget instanceof CombatEntityAPI ? (CombatEntityAPI)lastTarget : null;
			if (candidate == null || !candidates.contains(candidate)) {
				lastTarget = null;
			}
		}
		if (lastTarget == null) {
			lastTarget = target;
		}
		return lastTarget;
	}

	@Override
	public float resolvePriority(float current, AssignmentTargetAPI target, String[] params, CombatEngineAPI engine) {
		return current;
	}
}
