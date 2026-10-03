package data.order.starter;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderStarter;
import data.order.BattleNext_OrderUtils;

import java.util.List;

public class AssaultStarter implements BattleNext_OrderStarter {

	@Override
	public TargetKind getTargetKind() {
		return TargetKind.OBJECTIVE;
	}

	@Override
	public Action getAction() {
		return Action.ASSIGN;
	}

	@Override
	public List<? extends CombatEntityAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine) {
		return BattleNext_OrderUtils.enemyObjectives(source, engine);
	}

	@Override
	public CombatAssignmentType getCombatAssignmentType() {
		return CombatAssignmentType.ASSAULT;
	}
}
