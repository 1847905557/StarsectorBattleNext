package data.scripts.plugin;

import data.scripts.tools.BattleNext_Logger;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class BattleNext_DeploymentPlugin extends BaseEveryFrameCombatPlugin {

	private static final int FIXED_LAYOUT_SHIP_LIMIT = 30;
	private static final int MIN_COLUMNS = 7;
	private static final float FIXED_SHIP_SPACING = 800f;
	private static final float LARGE_SHIP_MIN_SPACING = 450f;
	private static final float FIXED_ROW_SPACING = 650f;
	private static final float LARGE_ROW_MIN_SPACING = 400f;
	private static final float LARGE_ROW_COMPRESSION = 0.8f;
	private static final float PLAYER_FACING = 90f;
	private static final float ENEMY_FACING = -90f;
	private static final float SPAWN_DELAY = 3f;

	private CombatEngineAPI engine;
	private boolean deployed;

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || deployed) return;
		deployed = true;
		spawn(FleetSide.PLAYER);
		spawn(FleetSide.ENEMY);
	}

	private void spawn(FleetSide side) {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("side", side).build();
		BattleNext_Logger.operationStarted(BattleNext_DeploymentPlugin.class, "battle.deployment", input);
		try {
			engine.getFleetManager(side).setSuppressDeploymentMessages(true);
			List<FleetMemberAPI> reserves = engine.getFleetManager(side).getReservesCopy();
			int ships = 0;
			for (FleetMemberAPI member : reserves) {
				if (!member.isFighterWing()) ships++;
			}
			int columns = ships <= FIXED_LAYOUT_SHIP_LIMIT ? MIN_COLUMNS : Math.max(MIN_COLUMNS, (int)Math.ceil(Math.sqrt(ships)));
			float spacing = ships <= FIXED_LAYOUT_SHIP_LIMIT ? FIXED_SHIP_SPACING : Math.max(LARGE_SHIP_MIN_SPACING, engine.getMapWidth() / (columns + 2f));
			float rowSpacing = ships <= FIXED_LAYOUT_SHIP_LIMIT ? FIXED_ROW_SPACING : Math.max(LARGE_ROW_MIN_SPACING, spacing * LARGE_ROW_COMPRESSION);
			float y = side == FleetSide.PLAYER ? -engine.getMapHeight() / 2f : engine.getMapHeight() / 2f;
			float direction = side == FleetSide.PLAYER ? 1f : -1f;
			int index = 0;
			for (FleetMemberAPI member : reserves) {
				if (member.isFighterWing()) continue;
				int row = index / columns;
				int position = index % columns;
				float x = (position - (columns - 1) / 2f) * spacing;
				float spawnY = y + row * rowSpacing * direction;
				engine.getFleetManager(side).spawnFleetMember(member, new Vector2f(x, spawnY), side == FleetSide.PLAYER ? PLAYER_FACING : ENEMY_FACING, SPAWN_DELAY);
				index++;
			}
			engine.getFleetManager(side).setSuppressDeploymentMessages(false);
			BattleNext_Logger.operationSucceeded(BattleNext_DeploymentPlugin.class, "battle.deployment",
					BattleNext_Logger.fields().put("side", side).put("reserves", reserves).put("deployed_count", index).put("columns", columns).put("spacing", spacing).put("row_spacing", rowSpacing).build());
		} catch (RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_DeploymentPlugin.class, "battle.deployment", input, exception);
			throw exception;
		}
	}
}
