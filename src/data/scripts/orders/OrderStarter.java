package data.scripts.orders;

import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.List;

public interface OrderStarter<E> {

	List<E> getInitialTargets(ShipAPI source, CombatEngineAPI engine);
	CombatAssignmentType getCombatAssignmentType();
}
