package data.scripts.orders.picker;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.OrderPicker;
import java.util.List;
import java.util.Random;

public class RandomPicker implements OrderPicker<CombatEntityAPI> {

	@Override
	public AssignmentTargetAPI pick(ShipAPI source, List<CombatEntityAPI> targets, CombatAssignmentType type, CombatEngineAPI engine) {
		CombatEntityAPI picked = targets.get(new Random().nextInt(targets.size()));
		if (picked instanceof ShipAPI) {
			return engine.getFleetManager(picked.getOwner()).getDeployedFleetMember((ShipAPI)picked);
		}
		return (AssignmentTargetAPI)picked;
	}
}
