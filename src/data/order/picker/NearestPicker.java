package data.order.picker;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.OrderPicker;
import org.lazywizard.lazylib.CollectionUtils;
import java.util.List;

public class NearestPicker implements OrderPicker {

	@Override
	public AssignmentTargetAPI pick(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatAssignmentType type, CombatEngineAPI engine) {
		targets.sort(new CollectionUtils.SortEntitiesByDistance(source.getLocation()));
		CombatEntityAPI picked = targets.get(0);
		if (picked instanceof ShipAPI) {
			return engine.getFleetManager(picked.getOwner()).getDeployedFleetMember((ShipAPI)picked);
		}
		return (AssignmentTargetAPI)picked;
	}
}
