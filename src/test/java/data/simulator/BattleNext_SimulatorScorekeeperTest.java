package data.simulator;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorScorekeeperTest extends BattleNext_TestGameFixture {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void rootShipsAndStationsShareWinnerAndFullCleanup(boolean station) {

		AtomicBoolean enemyAlive = new AtomicBoolean(true);
		ShipVariantAPI variant = proxy(ShipVariantAPI.class, (method, args) -> method.equals("getHullVariantId") ? "root_variant" : null);
		RepairTrackerAPI repair = proxy(RepairTrackerAPI.class, (method, args) -> method.equals("getCR") ? 0.7f : null);
		FleetMemberAPI playerMember = proxy(FleetMemberAPI.class, (method, args) -> switch (method) {
			case "getVariant" -> variant;
			case "getShipName" -> "Player";
			case "getRepairTracker" -> repair;
			default -> null;
		});
		FleetMemberAPI enemyMember = proxy(FleetMemberAPI.class, (method, args) -> switch (method) {
			case "getVariant" -> variant;
			case "getShipName" -> "Enemy";
			case "getRepairTracker" -> repair;
			default -> null;
		});
		ShipAPI player = proxy(ShipAPI.class, (method, args) -> switch (method) {
			case "getFleetMember" -> playerMember;
			case "getOwner" -> 0;
			case "isAlive" -> true;
			case "isStation" -> station;
			case "getHullLevel" -> 1f;
			default -> null;
		});
		ShipAPI enemy = proxy(ShipAPI.class, (method, args) -> switch (method) {
			case "getFleetMember" -> enemyMember;
			case "getOwner" -> 1;
			case "isAlive" -> enemyAlive.get();
			case "isStation" -> station;
			case "getHullLevel" -> enemyAlive.get() ? 1f : 0f;
			default -> null;
		});
		List<String> lifecycle = new ArrayList<>();
		Map<String, Object> customData = new HashMap<>();
		CombatListenerManagerAPI listeners = proxy(CombatListenerManagerAPI.class, (method, args) -> {
			lifecycle.add(method);
			return null;
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getShips" -> List.of(player, enemy);
			case "getTotalElapsedTime" -> 10f;
			case "getListenerManager" -> listeners;
			case "getCustomData" -> customData;
			case "removePlugin" -> { lifecycle.add("removePlugin"); yield null; }
			default -> null;
		});
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(List.of(playerMember), List.of(enemyMember));
		scorekeeper.init(engine);
		scorekeeper.advance(1f / 60f, List.of());
		assertFalse(scorekeeper.isFrozen());
		enemyAlive.set(false);
		scorekeeper.advance(1f / 60f, List.of());
		assertEquals("player", scorekeeper.getFrozenReport().getWinner());
		assertEquals(10000L, scorekeeper.getFrozenReport().getDurationMillis());
		assertEquals(1, scorekeeper.getFrozenReport().getEnemyMembers().size());
		scorekeeper.cleanup();
		assertTrue(customData.isEmpty());
		assertEquals(List.of("addListener", "removeListener", "removePlugin"), lifecycle);
	}
}
