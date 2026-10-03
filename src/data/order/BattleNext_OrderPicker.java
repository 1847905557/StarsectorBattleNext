package data.order;

import com.fs.starfarer.api.combat.*;

import java.util.List;

public interface BattleNext_OrderPicker {

	AssignmentTargetAPI pick(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatAssignmentType type, CombatEngineAPI engine);
}
