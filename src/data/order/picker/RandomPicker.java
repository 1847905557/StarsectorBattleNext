package data.order.picker;

import com.fs.starfarer.api.combat.*;
import data.order.BattleNext_OrderPicker;
import data.order.BattleNext_OrderUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomPicker implements BattleNext_OrderPicker {

	@Override
	public AssignmentTargetAPI pick(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatAssignmentType type, CombatEngineAPI engine) {
		return BattleNext_OrderUtils.toTarget(targets.get(ThreadLocalRandom.current().nextInt(targets.size())), engine);
	}
}
