package data.order;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.List;

public interface BattleNext_OrderStarter {

	enum TargetKind {
		ANY, SHIP, OBJECTIVE
	}

	enum Action {
		ASSIGN, CLEAN, RETREAT
	}

	TargetKind getTargetKind();

	Action getAction();

	List<? extends CombatEntityAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine);
	CombatAssignmentType getCombatAssignmentType();
}
