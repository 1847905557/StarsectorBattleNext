package data.order.picker;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lazywizard.lazylib.CollectionUtils;
import java.util.List;

public class ConcentratePicker extends NearestPicker {

	@Override
	public AssignmentTargetAPI pick(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatAssignmentType type, CombatEngineAPI engine) {
		targets.sort(new CollectionUtils.SortEntitiesByDistance(source.getLocation()));
		CombatTaskManagerAPI tasks = engine.getFleetManager(source.getOwner()).getTaskManager(source.isAlly());
		AssignmentInfo current = tasks.getAssignmentFor(source);
		for (CombatEntityAPI target : targets) {
			AssignmentTargetAPI assignmentTarget = target instanceof ShipAPI
					? engine.getFleetManager(target.getOwner()).getDeployedFleetMember((ShipAPI)target)
					: (AssignmentTargetAPI)target;
			for (AssignmentInfo assignment : tasks.getAllAssignments()) {
				if (assignment.getTarget() == assignmentTarget && assignment.getType() == type) {
					return assignmentTarget;
				}
			}
		}
		if (current != null && current.getType() == type) {
			for (CombatEntityAPI target : targets) {
				AssignmentTargetAPI assignmentTarget = target instanceof ShipAPI
						? engine.getFleetManager(target.getOwner()).getDeployedFleetMember((ShipAPI)target)
						: (AssignmentTargetAPI)target;
				if (assignmentTarget == current.getTarget()) return assignmentTarget;
			}
		}
		return super.pick(source, targets, type, engine);
	}
}
