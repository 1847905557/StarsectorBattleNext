package data.scripts.orders.picker;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.orders.BattleNext_OrderUtils;
import org.lazywizard.lazylib.CollectionUtils;
import java.util.List;

public class SpreadPicker extends NearestPicker {

	@Override
	public AssignmentTargetAPI pick(ShipAPI source, List<CombatEntityAPI> targets, CombatAssignmentType type, CombatEngineAPI engine) {
		targets.sort(new CollectionUtils.SortEntitiesByDistance(source.getLocation()));
		CombatTaskManagerAPI tasks = engine.getFleetManager(source.getOwner()).getTaskManager(source.isAlly());
		for (CombatEntityAPI target : targets) {
			AssignmentTargetAPI assignmentTarget = target instanceof ShipAPI
					? engine.getFleetManager(target.getOwner()).getDeployedFleetMember((ShipAPI)target)
					: (AssignmentTargetAPI)target;
			AssignmentInfo current = tasks.getAssignmentFor(source);
			if (current != null && current.getTarget() == assignmentTarget && BattleNext_OrderUtils.assignmentCount(assignmentTarget, tasks) <= 1) {
				return assignmentTarget;
			}
			if (BattleNext_OrderUtils.assignmentCount(assignmentTarget, tasks) == 0) {
				return assignmentTarget;
			}
		}
		return super.pick(source, targets, type, engine);
	}
}
