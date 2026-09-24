package data.scripts.orders.starter;

import com.fs.starfarer.api.combat.BattleObjectiveAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.BattleNext_OrderUtils;
import data.scripts.orders.OrderStarter;
import java.util.List;

public class DefendStarter implements OrderStarter<BattleObjectiveAPI> {

	@Override
	public List<BattleObjectiveAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine) {
		return BattleNext_OrderUtils.allyObjectives(source);
	}

	@Override
	public CombatAssignmentType getCombatAssignmentType() {
		return CombatAssignmentType.DEFEND;
	}
}
