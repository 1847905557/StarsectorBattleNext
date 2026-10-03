package data.order.picker;

import com.fs.starfarer.api.combat.AssignmentTargetAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.order.BattleNext_OrderUtils;
import org.lazywizard.lazylib.CollectionUtils;

import java.util.List;

public class ConcentratePicker extends NearestPicker {

	@Override
	public AssignmentTargetAPI pick(ShipAPI source, List<? extends CombatEntityAPI> targets,
			CombatAssignmentType type, CombatEngineAPI engine) {
		targets.sort(new CollectionUtils.SortEntitiesByDistance(source.getLocation()));
		CombatTaskManagerAPI tasks = engine.getFleetManager(source.getOwner()).getTaskManager(source.isAlly());
		for (CombatEntityAPI target : targets) {
			AssignmentTargetAPI assignmentTarget = BattleNext_OrderUtils.toTarget(target, engine);
			if (BattleNext_OrderUtils.assignmentCount(assignmentTarget, type, tasks, engine) > 0) return assignmentTarget;
		}
		return super.pick(source, targets, type, engine);
	}
}
