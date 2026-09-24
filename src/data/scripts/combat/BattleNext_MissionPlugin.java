package data.scripts.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;

import java.util.List;

public final class BattleNext_MissionPlugin extends BaseEveryFrameCombatPlugin {
	private static final float DEFAULT_TIME_LIMIT = 500f;
	private final float timeLimit;
	private CombatEngineAPI engine;
	private boolean timeoutApplied;
	private int elapsedSecond = -1;

	public BattleNext_MissionPlugin() {
		this(DEFAULT_TIME_LIMIT);
	}

	public BattleNext_MissionPlugin(float timeLimit) {
		this.timeLimit = Math.max(0f, timeLimit);
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		engine.setMaxFleetPoints(FleetSide.PLAYER, 9999);
		engine.setMaxFleetPoints(FleetSide.ENEMY, 9999);
		configureSide(FleetSide.PLAYER);
		configureSide(FleetSide.ENEMY);
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || engine.isCombatOver()) return;
		int second = (int)engine.getTotalElapsedTime(false);
		if (second != elapsedSecond) elapsedSecond = second;
		if (timeLimit > 0f && !timeoutApplied && engine.getTotalElapsedTime(false) >= timeLimit) applyTimeout();
	}

	private void configureSide(FleetSide side) {
		engine.getContext().aiRetreatAllowed = false;
		engine.getContext().fightToTheLast = true;
		engine.getFleetManager(side).setCanForceShipsToEngageWhenBattleClearlyLost(true);
		engine.getFleetManager(side).getTaskManager(false).setPreventFullRetreat(true);
		engine.getFleetManager(side).getTaskManager(false).getCommandPointsStat().modifyFlat("BattleNext_MissionPlugin", 9999f);
		if (side == FleetSide.PLAYER) {
			engine.getFleetManager(side).getTaskManager(true).setPreventFullRetreat(true);
			engine.getFleetManager(side).getTaskManager(true).getCommandPointsStat().modifyFlat("BattleNext_MissionPlugin", 9999f);
		}
	}

	private void applyTimeout() {
		timeoutApplied = true;
		for (ShipAPI ship : engine.getShips()) {
			if (!ship.isAlive()) continue;
			PersonAPI captain = ship.getCaptain();
			if (captain != null) captain.setPersonality(Personalities.RECKLESS);
		}
		engine.getFleetManager(FleetSide.PLAYER).getTaskManager(false).setFullAssault(true);
		engine.getFleetManager(FleetSide.PLAYER).getTaskManager(true).setFullAssault(true);
		engine.getFleetManager(FleetSide.ENEMY).getTaskManager(false).setFullAssault(true);
		engine.getFogOfWar(FleetSide.PLAYER.ordinal()).revealAroundPoint(engine, 0f, 0f, 90000f);
		engine.getFogOfWar(FleetSide.ENEMY.ordinal()).revealAroundPoint(engine, 0f, 0f, 90000f);
		Global.getLogger(BattleNext_MissionPlugin.class).info("BattleNext timeout applied at " + timeLimit + " seconds");
	}
}
