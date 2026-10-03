package data.order.picker;

import com.fs.starfarer.api.combat.*;
import data.order.BattleNext_OrderPicker;
import data.order.BattleNext_OrderUtils;
import org.lazywizard.lazylib.CollectionUtils;

import java.util.List;

public class NearestPicker implements BattleNext_OrderPicker {

	@Override
	public AssignmentTargetAPI pick(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatAssignmentType type, CombatEngineAPI engine) {
		targets.sort(new CollectionUtils.SortEntitiesByDistance(source.getLocation()));
			return BattleNext_OrderUtils.toTarget(targets.get(0), engine);
	}
}
