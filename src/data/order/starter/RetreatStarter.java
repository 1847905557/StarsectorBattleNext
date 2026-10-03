package data.order.starter;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderStarter;
import java.util.ArrayList;
import java.util.List;

public class RetreatStarter implements BattleNext_OrderStarter {

	@Override
	public TargetKind getTargetKind() {
		return TargetKind.SHIP;
	}

	@Override
	public Action getAction() {
		return Action.RETREAT;
	}

	@Override
	public List<? extends CombatEntityAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine) {
		List<ShipAPI> targets = new ArrayList<>();
		targets.add(source);
		return targets;
	}

	@Override
	public CombatAssignmentType getCombatAssignmentType() {
		return CombatAssignmentType.RETREAT;
	}
}
