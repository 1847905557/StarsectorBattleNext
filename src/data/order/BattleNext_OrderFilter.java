package data.order;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.List;

public interface BattleNext_OrderFilter {

	BattleNext_OrderStarter.TargetKind getTargetKind();

	void configure(List<String> params);

	void filterTargets(ShipAPI source, List<? extends CombatEntityAPI> targets, CombatEngineAPI engine);
}
