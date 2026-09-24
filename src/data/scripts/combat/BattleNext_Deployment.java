package data.scripts.combat;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public final class BattleNext_Deployment extends BaseEveryFrameCombatPlugin {
	private CombatEngineAPI engine;
	private boolean deployed;

	@Override
	public void init(CombatEngineAPI engine) { this.engine = engine; }

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || deployed) return;
		deployed = true;
		spawn(FleetSide.PLAYER);
		spawn(FleetSide.ENEMY);
	}

	private void spawn(FleetSide side) {
		engine.getFleetManager(side).setSuppressDeploymentMessages(true);
		List<FleetMemberAPI> reserves = engine.getFleetManager(side).getReservesCopy();
		int ships = 0;
		for (FleetMemberAPI member : reserves) if (!member.isFighterWing()) ships++;
		int columns = ships <= 30 ? 7 : Math.max(7, (int)Math.ceil(Math.sqrt(ships)));
		float spacing = ships <= 30 ? 800f : Math.max(450f, engine.getMapWidth() / (columns + 2f));
		float rowSpacing = ships <= 30 ? 650f : Math.max(400f, spacing * 0.8f);
		float y = side == FleetSide.PLAYER ? -engine.getMapHeight() / 2f : engine.getMapHeight() / 2f;
		float direction = side == FleetSide.PLAYER ? 1f : -1f;
		int index = 0;
		for (FleetMemberAPI member : reserves) {
			if (member.isFighterWing()) continue;
			int row = index / columns;
			int position = index % columns;
			float x = (position - (columns - 1) / 2f) * spacing;
			float spawnY = y + row * rowSpacing * direction;
			engine.getFleetManager(side).spawnFleetMember(member, new Vector2f(x, spawnY), side == FleetSide.PLAYER ? 90f : -90f, 3f);
			index++;
		}
		engine.getFleetManager(side).setSuppressDeploymentMessages(false);
	}
}
