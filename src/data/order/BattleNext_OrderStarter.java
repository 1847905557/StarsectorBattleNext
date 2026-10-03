package data.order;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.List;

public interface OrderStarter {

	List<? extends CombatEntityAPI> getInitialTargets(ShipAPI source, CombatEngineAPI engine);
	CombatAssignmentType getCombatAssignmentType();
}
