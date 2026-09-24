package data.scripts.orders;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.List;

public interface OrderFilter<E> {
	void filterTargets(ShipAPI source, List<E> targets, String[] params, CombatEngineAPI engine);
}
