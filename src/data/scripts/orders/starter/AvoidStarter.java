package data.scripts.orders.starter;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.BattleNext_OrderUtils;
import data.scripts.orders.OrderStarter;
import java.util.List;

public class AvoidStarter implements OrderStarter<ShipAPI> {

	@Override
	public List<ShipAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine) {
		return BattleNext_OrderUtils.enemies(source);
	}

	@Override
	public CombatAssignmentType getCombatAssignmentType() {
		return CombatAssignmentType.AVOID;
	}
}
