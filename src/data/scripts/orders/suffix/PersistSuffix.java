package data.scripts.orders.suffix;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import data.scripts.orders.BattleNext_OrderUtils;
import data.scripts.orders.OrderSuffix;

public class PersistSuffix implements OrderSuffix {

	private AssignmentTargetAPI lastTarget;

	@Override
	public float getFloatResultWithTarget(AssignmentTargetAPI target, String[] params, Object custom, CombatEngineAPI engine) {
		return 0f;
	}

	@Override
	public Object getCustomResultWithTarget(AssignmentTargetAPI target, String[] params, Object custom, CombatEngineAPI engine) {
		if (Boolean.FALSE.equals(custom)) {
			lastTarget = null;
		}
		if (lastTarget instanceof DeployedFleetMemberAPI
				&& !BattleNext_OrderUtils.isValid(((DeployedFleetMemberAPI)lastTarget).getShip())) {
			lastTarget = null;
		}
		if (lastTarget == null) {
			lastTarget = target;
		}
		return lastTarget;
	}
}
