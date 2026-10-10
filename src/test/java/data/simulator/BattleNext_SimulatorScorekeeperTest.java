package data.simulator;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_SimulatorScorekeeperTest extends BattleNext_TestGameFixture {

	@ParameterizedTest
	@CsvSource({"0, player", "1, enemy", "0, draw"})
	void initializationWaitsForBothSidesAndAllRootMembersBeforeSettling(int firstOwner, String winner) {

		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> method.equals("getHullId") ? "root_hull" : null);
		ShipVariantAPI variant = proxy(ShipVariantAPI.class, (method, args) -> switch (method) {
			case "getHullVariantId" -> "root_variant";
			case "getHullSpec" -> hull;
			default -> null;
		});
		RepairTrackerAPI repair = proxy(RepairTrackerAPI.class, (method, args) -> method.equals("getCR") ? 0.7f : null);
		List<FleetMemberAPI> members = new ArrayList<>();
		List<ShipAPI> roots = new ArrayList<>();
		boolean[] alive = {false, false, false};
		for (int index = 0; index < 3; index++) {
			int memberIndex = index;
			FleetMemberAPI member = proxy(FleetMemberAPI.class, (method, args) -> switch (method) {
				case "getVariant" -> variant;
				case "getShipName" -> "Root " + memberIndex;
				case "getRepairTracker" -> repair;
				default -> null;
			});
			members.add(member);
			roots.add(proxy(ShipAPI.class, (method, args) -> switch (method) {
				case "getFleetMember" -> member;
				case "getOwner" -> memberIndex == 1 ? 1 : 0;
				case "isAlive" -> alive[memberIndex];
				case "getHullLevel" -> alive[memberIndex] ? 1f : 0f;
				default -> null;
			}));
		}
		List<ShipAPI> ships = new ArrayList<>();
		Map<String, Object> customData = new HashMap<>();
		List<Object> listeners = new ArrayList<>();
		List<Object> removed = new ArrayList<>();
		boolean[] paused = {false};
		float[] elapsed = {0f};
		CombatListenerManagerAPI manager = proxy(CombatListenerManagerAPI.class, (method, args) -> switch (method) {
			case "addListener" -> listeners.add(args[0]);
			case "removeListener" -> listeners.remove(args[0]);
			default -> null;
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getShips" -> ships;
			case "getTotalElapsedTime" -> elapsed[0];
			case "isPaused" -> paused[0];
			case "getListenerManager" -> manager;
			case "getCustomData" -> customData;
			case "removePlugin" -> removed.add(args[0]);
			default -> null;
		});
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(
				List.of(members.get(0), members.get(2)), List.of(members.get(1)));
		scorekeeper.init(engine);
		try {
			scorekeeper.advance(0f, List.of());
			assertFalse(scorekeeper.isFrozen());
			ships.add(roots.get(firstOwner));
			alive[firstOwner] = true;
			scorekeeper.advance(0.1f, List.of());
			assertFalse(scorekeeper.isFrozen());
			ships.add(roots.get(1 - firstOwner));
			alive[1 - firstOwner] = true;
			scorekeeper.advance(0.1f, List.of());
			assertFalse(scorekeeper.isFrozen());
			alive[1] = false;
			scorekeeper.advance(0.1f, List.of());
			assertFalse(scorekeeper.isFrozen());
			ships.add(roots.get(2));
			paused[0] = true;
			alive[1] = true;
			alive[2] = true;
			scorekeeper.advance(0f, List.of());
			alive[1] = false;
			paused[0] = false;
			scorekeeper.advance(0.1f, List.of());
			assertFalse(scorekeeper.isFrozen());
			alive[1] = true;
			scorekeeper.advance(0.1f, List.of());
			assertFalse(scorekeeper.isFrozen());
			for (int index = 0; index < alive.length; index++) {
				alive[index] = winner.equals(index == 1 ? "enemy" : "player");
			}
			elapsed[0] = 12f;
			scorekeeper.advance(0.1f, List.of());
			var report = scorekeeper.getFrozenReport();
			assertEquals(winner, report.getWinner());
			assertEquals(12000L, report.getDurationMillis());
			assertEquals(2, report.getPlayerMembers().size());
			assertEquals(1, report.getEnemyMembers().size());
			alive[0] = !alive[0];
			elapsed[0] = 20f;
			scorekeeper.advance(0.1f, List.of());
			assertSame(report, scorekeeper.getFrozenReport());
			assertEquals(12000L, report.getDurationMillis());
		} finally {
			scorekeeper.cleanup();
		}
		assertTrue(customData.isEmpty());
		assertTrue(listeners.isEmpty());
		assertEquals(List.of(scorekeeper), removed);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void rootShipsAndStationsShareWinnerAndFullCleanup(boolean station) {

		AtomicBoolean enemyAlive = new AtomicBoolean(true);
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> method.equals("getHullId") ? "root_hull" : null);
		ShipVariantAPI variant = proxy(ShipVariantAPI.class, (method, args) -> switch (method) {
			case "getHullVariantId" -> "root_variant";
			case "getHullSpec" -> hull;
			default -> null;
		});
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
		assertEquals("root_hull", scorekeeper.getFrozenReport().getPlayerMembers().get(0).getHullId());
		assertEquals("root_hull", scorekeeper.getFrozenReport().getEnemyMembers().get(0).getHullId());
		scorekeeper.cleanup();
		assertTrue(customData.isEmpty());
		assertEquals(List.of("addListener", "removeListener", "removePlugin"), lifecycle);
	}
}
