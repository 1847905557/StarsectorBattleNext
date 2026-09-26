package data.order.starter;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderUtils;
import data.order.OrderStarter;
import java.util.List;

public class EscortStarter implements OrderStarter {

	@Override
	public List<? extends CombatEntityAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine) {
		return BattleNext_OrderUtils.allies(source);
	}

	@Override
	public CombatAssignmentType getCombatAssignmentType() {
		return CombatAssignmentType.LIGHT_ESCORT;
	}
}
