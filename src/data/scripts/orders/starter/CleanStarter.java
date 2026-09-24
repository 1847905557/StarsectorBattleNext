package data.scripts.orders.starter;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.OrderStarter;
import java.util.ArrayList;
import java.util.List;

public class CleanStarter implements OrderStarter<ShipAPI> {

	@Override
	public List<ShipAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine) {
		List<ShipAPI> targets = new ArrayList<>();
		targets.add(source);
		return targets;
	}

	@Override
	public CombatAssignmentType getCombatAssignmentType() {
		return null;
	}
}
