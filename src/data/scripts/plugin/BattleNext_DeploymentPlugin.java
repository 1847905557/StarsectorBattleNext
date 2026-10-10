package data.scripts.plugin;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import data.scripts.tools.BattleNext_Logger;
import data.simulator.BattleNext_SimulatorWorker;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class BattleNext_DeploymentPlugin extends BaseEveryFrameCombatPlugin {

	private static final float SHIP_SPACING = 800f;
	private static final float ROW_SPACING = 650f;
	private static final float SIDE_MARGIN_FRACTION = 0.25f;
	private static final float PLAYER_FACING = 90f;
	private static final float ENEMY_FACING = -90f;
	private static final float SPAWN_DELAY = 3f;

	private final int deploymentPointLimit;
	private final boolean playerDefaultAi;
	private final boolean enemyDefaultAi;

	private CombatEngineAPI engine;
	private boolean deployed;

	public BattleNext_DeploymentPlugin(int deploymentPointLimit, boolean playerDefaultAi, boolean enemyDefaultAi) {
		this.deploymentPointLimit = deploymentPointLimit;
		this.playerDefaultAi = playerDefaultAi;
		this.enemyDefaultAi = enemyDefaultAi;
	}

	@Override
	public void init(CombatEngineAPI engine) {

		this.engine = engine;
		engine.getContext().aiRetreatAllowed = false;
		engine.getContext().fightToTheLast = true;

		for (FleetSide side : FleetSide.values()) {

			engine.setMaxFleetPoints(side, deploymentPointLimit > 0 ? deploymentPointLimit : 999);

			CombatFleetManagerAPI manager = engine.getFleetManager(side);
			manager.setCanForceShipsToEngageWhenBattleClearlyLost(true);
			manager.getTaskManager(false).setPreventFullRetreat(true);

			if (side == FleetSide.PLAYER) {
				manager.getTaskManager(true).setPreventFullRetreat(true);
			}
			if (!(side == FleetSide.PLAYER ? playerDefaultAi : enemyDefaultAi)) {
				manager.getAdmiralAI().setNoOrders(true);
				manager.getTaskManager(false).clearTasks();
				if (side == FleetSide.PLAYER) {
					manager.getTaskManager(true).clearTasks();
				}
			}
		}

		if (deploymentPointLimit <= 0) {
			spawn(FleetSide.PLAYER);
			spawn(FleetSide.ENEMY);
		}
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (!BattleNext_SimulatorWorker.isWorker()) return;
		if (engine == null || deployed) return;

		deployed = true;
		for (FleetSide side : FleetSide.values()) {
			engine.getFleetManager(side).getAdmiralAI().preCombat();
		}
	}

	private void spawn(FleetSide side) {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("side", side).build();
		BattleNext_Logger.operationStarted(BattleNext_DeploymentPlugin.class, "battle.deployment", input);
		try {
			engine.getFleetManager(side).setSuppressDeploymentMessages(true);
			List<FleetMemberAPI> reserves = engine.getFleetManager(side).getReservesCopy();
			int columns = 1 + 2 * (int)Math.floor(engine.getMapWidth() * SIDE_MARGIN_FRACTION / SHIP_SPACING);
			float y = side == FleetSide.PLAYER ? -engine.getMapHeight() / 2f : engine.getMapHeight() / 2f;
			float direction = side == FleetSide.PLAYER ? 1f : -1f;
			int index = 0;
			for (FleetMemberAPI member : reserves) {
				if (member.isFighterWing()) continue;
				int row = index / columns;
				int position = index % columns;
				float x = ((position + 1) / 2) * (position % 2 == 0 ? 1f : -1f) * SHIP_SPACING;
				float spawnY = y + row * ROW_SPACING * direction;
				engine.getFleetManager(side).spawnFleetMember(member, new Vector2f(x, spawnY), side == FleetSide.PLAYER ? PLAYER_FACING : ENEMY_FACING, SPAWN_DELAY);
				index++;
			}
			engine.getFleetManager(side).setSuppressDeploymentMessages(false);
			BattleNext_Logger.operationSucceeded(BattleNext_DeploymentPlugin.class, "battle.deployment",
					BattleNext_Logger.fields().put("side", side).put("reserves", reserves).put("deployed_count", index).put("columns", columns).put("spacing", SHIP_SPACING).put("row_spacing", ROW_SPACING).build());
		} catch (RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_DeploymentPlugin.class, "battle.deployment", input, exception);
			throw exception;
		}
	}
}
