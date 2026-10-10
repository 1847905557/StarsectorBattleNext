package data.scripts.plugin;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.AdmiralAIPlugin;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import data.test.fixture.BattleNext_TestDeploymentFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.Test;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.BattleCreationContext;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_DeploymentPluginTest extends BattleNext_TestDeploymentFixture {

	@Test
	void positiveLimitKeepsOriginalAdmiralsAndClearsBothPlayerTaskManagersBeforeCombat() {
		BattleCreationContext context = new BattleCreationContext(null, null, null, null);
		context.aiRetreatAllowed = true;
		context.fightToTheLast = false;
		List<String> retreatSettings = new ArrayList<>();
		Map<FleetSide, Integer> caps = new EnumMap<>(FleetSide.class);
		List<CombatAssignmentType> playerTasks = new ArrayList<>(List.of(CombatAssignmentType.CAPTURE));
		List<CombatAssignmentType> alliedTasks = new ArrayList<>(List.of(CombatAssignmentType.DEFEND));
		List<String> calls = new ArrayList<>();
		AdmiralAIPlugin playerAi = proxy(AdmiralAIPlugin.class, (method, args) -> {
			if (method.equals("setNoOrders")) {
				assertEquals(true, args[0]);
				calls.add("player:noOrders");
			}
			return null;
		});
		AdmiralAIPlugin enemyAi = proxy(AdmiralAIPlugin.class, (method, args) -> {
			calls.add("enemy:" + method);
			return null;
		});
		FleetManager player = deploymentManager((method, args) -> switch (method) {
			case "setCanForceShipsToEngageWhenBattleClearlyLost" -> { assertEquals(true, args[0]); retreatSettings.add("force:player"); yield null; }
			case "getAdmiralAI" -> playerAi;
			case "getTaskManager" -> proxy(CombatTaskManagerAPI.class, (name, values) -> {
				if (name.equals("setPreventFullRetreat")) {
					assertEquals(true, values[0]);
					retreatSettings.add("prevent:player:" + args[0]);
				}
				if (name.equals("clearTasks")) {
					((Boolean) args[0] ? alliedTasks : playerTasks).clear();
					calls.add("player:clear:" + args[0]);
				}
				return null;
			});
			case "setAdmiralAI", "spawnFleetMember" -> throw new AssertionError(method);
			default -> null;
		});
		FleetManager enemy = deploymentManager((method, args) -> switch (method) {
			case "setCanForceShipsToEngageWhenBattleClearlyLost" -> { assertEquals(true, args[0]); retreatSettings.add("force:enemy"); yield null; }
			case "getAdmiralAI" -> enemyAi;
			case "getTaskManager" -> proxy(CombatTaskManagerAPI.class, (name, values) -> {
				assertEquals("setPreventFullRetreat", name);
				assertEquals(true, values[0]);
				retreatSettings.add("prevent:enemy:" + args[0]);
				return null;
			});
			case "setAdmiralAI", "spawnFleetMember" -> throw new AssertionError(method);
			default -> null;
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getContext" -> context;
			case "setMaxFleetPoints" -> caps.put((FleetSide) args[0], (Integer) args[1]);
			case "getFleetManager" -> args[0] == FleetSide.PLAYER ? player : enemy;
			default -> null;
		});
		BattleNext_DeploymentPlugin plugin = new BattleNext_DeploymentPlugin(10, false, true);
		plugin.init(engine);
		assertFalse(context.aiRetreatAllowed);
		assertTrue(context.fightToTheLast);
		assertEquals(List.of("force:player", "prevent:player:false", "prevent:player:true", "force:enemy", "prevent:enemy:false"), retreatSettings);
		assertEquals(Map.of(FleetSide.PLAYER, 10, FleetSide.ENEMY, 10), caps);
		assertEquals(List.of("player:noOrders", "player:clear:false", "player:clear:true"), calls);
		assertTrue(playerTasks.isEmpty());
		assertTrue(alliedTasks.isEmpty());
		assertSame(playerAi, player.getAdmiralAI());
		assertSame(enemyAi, enemy.getAdmiralAI());
		player.setMaxStrength(240);
		enemy.setMaxStrength(160);
		assertEquals(10, player.getMaxStrength());
		assertEquals(10, enemy.getMaxStrength());
		player.getMaxFleetPoints().modifyFlat("objective", 20f);
		player.getMaxFleetPoints().modifyPercent("skill", 50f);
		assertEquals(35, player.getMaxStrength());
		player.setMaxStrength(240);
		assertEquals(10, player.getMaxStrength());
	}

	@ParameterizedTest
	@ValueSource(ints = {-5, 0})
	void nonpositiveLimitsDeployBothSidesOnceInTheirGridAndKeepRuntimePointModifiers(int limit) {
		FleetMemberAPI member = proxy(FleetMemberAPI.class, (method, args) -> null);
		Map<FleetSide, Integer> caps = new EnumMap<>(FleetSide.class);
		List<Vector2f> locations = new ArrayList<>();
		List<Float> facings = new ArrayList<>();
		List<String> commands = new ArrayList<>();
		AdmiralAIPlugin playerAi = proxy(AdmiralAIPlugin.class, (method, args) -> { commands.add("player:" + method); return null; });
		AdmiralAIPlugin enemyAi = proxy(AdmiralAIPlugin.class, (method, args) -> { commands.add("enemy:" + method); return null; });
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getContext" -> new BattleCreationContext(null, null, null, null);
			case "setMaxFleetPoints" -> caps.put((FleetSide) args[0], (Integer) args[1]);
			case "getMapHeight" -> 12000f;
			case "getMapWidth" -> 12000f;
			case "getFleetManager" -> proxy(CombatFleetManagerAPI.class, (name, values) -> switch (name) {
				case "getAdmiralAI" -> args[0] == FleetSide.PLAYER ? playerAi : enemyAi;
				case "getTaskManager" -> proxy(CombatTaskManagerAPI.class, (key, input) -> {
					if (key.equals("clearTasks")) commands.add("clear:" + args[0]);
					return null;
				});
				case "getReservesCopy" -> List.of(member, member);
				case "spawnFleetMember" -> {
					assertSame(member, values[0]);
					locations.add(new Vector2f((Vector2f) values[1]));
					facings.add((Float) values[2]);
					yield null;
				}
				default -> null;
			});
			default -> null;
		});
		BattleNext_DeploymentPlugin plugin = new BattleNext_DeploymentPlugin(limit, true, false);
		plugin.init(engine);
		assertEquals(4, locations.size());
		assertEquals(List.of("enemy:setNoOrders", "clear:ENEMY"), commands);
		assertEquals(Map.of(FleetSide.PLAYER, 999, FleetSide.ENEMY, 999), caps);
		caps.put(FleetSide.PLAYER, 1019);
		String previousWorker = System.getProperty("BattleNext.simulator.worker");
		try {
			System.setProperty("BattleNext.simulator.worker", "true");
			plugin.advance(0.1f, List.of());
			plugin.advance(0.1f, List.of());
		} finally {
			if (previousWorker == null) {
				System.clearProperty("BattleNext.simulator.worker");
			} else {
				System.setProperty("BattleNext.simulator.worker", previousWorker);
			}
		}
		assertEquals(List.of(new Vector2f(0f, -6000f), new Vector2f(-800f, -6000f),
				new Vector2f(0f, 6000f), new Vector2f(-800f, 6000f)), locations);
		assertEquals(List.of(90f, 90f, -90f, -90f), facings);
		assertEquals(1019, caps.get(FleetSide.PLAYER));
		assertEquals(List.of("enemy:setNoOrders", "clear:ENEMY", "player:preCombat", "enemy:preCombat"), commands);
	}

	@ParameterizedTest
	@MethodSource("deploymentLayouts")
	void shipsFillEachRowFromItsCenterWithinTheMiddleHalfOfTheMap(float width, int shipCount, float[][] expected) {
		List<FleetMemberAPI> ships = new ArrayList<>();
		for (int index = 0; index < shipCount; index++) {
			ships.add(proxy(FleetMemberAPI.class, (method, args) -> null));
		}
		FleetMemberAPI wing = proxy(FleetMemberAPI.class, (method, args) -> method.equals("isFighterWing") ? true : null);
		List<FleetMemberAPI> reserves = new ArrayList<>();
		reserves.add(wing);
		for (FleetMemberAPI ship : ships) {
			reserves.add(ship);
			reserves.add(wing);
		}
		Map<FleetSide, List<Vector2f>> locations = new EnumMap<>(FleetSide.class);
		Map<FleetSide, List<FleetMemberAPI>> deployed = new EnumMap<>(FleetSide.class);
		for (FleetSide side : FleetSide.values()) {
			locations.put(side, new ArrayList<>());
			deployed.put(side, new ArrayList<>());
		}
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getContext" -> new BattleCreationContext(null, null, null, null);
			case "getMapWidth" -> width;
			case "getMapHeight" -> 12000f;
			case "getFleetManager" -> proxy(CombatFleetManagerAPI.class, (name, values) -> switch (name) {
				case "getTaskManager" -> proxy(CombatTaskManagerAPI.class, (key, input) -> null);
				case "getReservesCopy" -> reserves;
				case "spawnFleetMember" -> {
					FleetSide side = (FleetSide) args[0];
					deployed.get(side).add((FleetMemberAPI) values[0]);
					locations.get(side).add(new Vector2f((Vector2f) values[1]));
					assertEquals(side == FleetSide.PLAYER ? 90f : -90f, values[2]);
					yield null;
				}
				default -> null;
			});
			default -> null;
		});
		new BattleNext_DeploymentPlugin(0, true, true).init(engine);
		for (FleetSide side : FleetSide.values()) {
			assertEquals(ships, deployed.get(side));
			assertEquals(expected.length, locations.get(side).size());
			for (int index = 0; index < expected.length; index++) {
				Vector2f location = locations.get(side).get(index);
				assertEquals(expected[index][0], location.x);
				assertEquals(side == FleetSide.PLAYER ? -6000f + expected[index][1] : 6000f - expected[index][1], location.y);
				assertTrue(Math.abs(location.x) <= width * 0.25f);
			}
		}
	}

	static Stream<Arguments> deploymentLayouts() {
		return Stream.of(
				Arguments.of(12000f, 1, new float[][]{{0f, 0f}}),
				Arguments.of(12000f, 3, new float[][]{{0f, 0f}, {-800f, 0f}, {800f, 0f}}),
				Arguments.of(12000f, 8, new float[][]{{0f, 0f}, {-800f, 0f}, {800f, 0f}, {-1600f, 0f}, {1600f, 0f}, {-2400f, 0f}, {2400f, 0f}, {0f, 650f}}),
				Arguments.of(8000f, 6, new float[][]{{0f, 0f}, {-800f, 0f}, {800f, 0f}, {-1600f, 0f}, {1600f, 0f}, {0f, 650f}}),
				Arguments.of(3199f, 3, new float[][]{{0f, 0f}, {0f, 650f}, {0f, 1300f}}),
				Arguments.of(3200f, 4, new float[][]{{0f, 0f}, {-800f, 0f}, {800f, 0f}, {0f, 650f}}),
				Arguments.of(800f, 2, new float[][]{{0f, 0f}, {0f, 650f}}),
				Arguments.of(800f, 31, new float[][]{{0f, 0f}, {0f, 650f}, {0f, 1300f}, {0f, 1950f}, {0f, 2600f}, {0f, 3250f}, {0f, 3900f}, {0f, 4550f}, {0f, 5200f}, {0f, 5850f}, {0f, 6500f}, {0f, 7150f}, {0f, 7800f}, {0f, 8450f}, {0f, 9100f}, {0f, 9750f}, {0f, 10400f}, {0f, 11050f}, {0f, 11700f}, {0f, 12350f}, {0f, 13000f}, {0f, 13650f}, {0f, 14300f}, {0f, 14950f}, {0f, 15600f}, {0f, 16250f}, {0f, 16900f}, {0f, 17550f}, {0f, 18200f}, {0f, 18850f}, {0f, 19500f}}));
	}
}
