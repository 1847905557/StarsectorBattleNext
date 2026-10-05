package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.DroneStrikeStats;
import com.fs.starfarer.api.impl.combat.MineStrikeStats;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.movement.BattleNext_EngineAI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_SystemAIFactoryTest extends BattleNext_TestGameFixture {

	private CombatEngineAPI previousEngine;
	private final AtomicInteger customInitializations = new AtomicInteger();

	@BeforeEach
	void installConstructionEngine() {
		previousEngine = Global.getCombatEngine();
		Global.setCombatEngine(proxy(CombatEngineAPI.class, (method, args) -> null));
	}

	@AfterEach
	void restoreConstructionEngine() {
		Global.setCombatEngine(previousEngine);
	}

	@Test
	void systemTypesConstructTheirActualAiWithValidRuntimeDependencies() throws JSONException {

		List<String> types = List.of("FLARE", "FLARE_JAMMER", "CANISTER_FLAK", "BURN_DRIVE",
				"BURN_DRIVE_TOGGLE", "MANEUVERING_JETS", "FAST_RELOAD", "AMMO_RELOAD",
				"FORTRESS_SHIELD", "DAMPER_FIELD", "HIGH_ENERGY_FOCUS", "PHASE_TELEPORTER",
				"PHASE_TELEPORTER_2", "PHASE_DISPLACER", "DRONE_LAUNCHER_PD",
				"DRONE_LAUNCHER_STANDARD", "WEAPON_BOOST", "DYNAMIC_STABILIZER",
				"LIDAR_ARRAY", "TEMPORAL_SHELL", "ACAUSAL_DISRUPTOR", "MOTE_ATTRACTOR",
				"ENTROPY_AMPLIFIER", "INTERDICTOR", "MINE_STRIKE", "DRONE_STRIKE",
				"EMP", "PHASE_CLOAK", "NONE", "TARGETING_FEED", "RESERVE_DEPLOYMENT",
				"RECALL_DEVICE", "CUSTOM");
		for (String type : types) {
			ShipSystemAPI system = system(type);
			MutableShipStatsAPI stats = proxy(MutableShipStatsAPI.class, (method, args) -> switch (method) {
				case "getFluxCapacity" -> new MutableStat(1000f);
				case "getFluxDissipation", "getMaxSpeed" -> new MutableStat(100f);
				case "getSystemRangeBonus" -> new StatBonus();
				default -> null;
			});
			ShipAPI ship = proxy(ShipAPI.class, (method, args) -> switch (method) {
				case "getSystem" -> system;
				case "getMutableStats" -> stats;
				case "getMaxFlux" -> 1000f;
				case "getMaxSpeed", "getDeceleration" -> 100f;
				case "getAllWeapons", "getWeaponGroupsCopy" -> List.of();
				default -> null;
			});
			ShipwideAIFlags flags = new ShipwideAIFlags();
			BattleNext_ManeuverAPI maneuver = proxy(BattleNext_ManeuverAPI.class, (method, args) -> switch (method) {
				case "getAIFlags" -> flags;
				case "getShip" -> ship;
				default -> null;
			});
			BattleNext_FlockingAI flocking = new BattleNext_FlockingAI(ship, proxy(BattleNext_EngineAI.class, (method, args) -> null), maneuver);
			BattleNext_ThreatEvalAI threat = new BattleNext_ThreatEvalAI(ship, maneuver, flocking);
			BattleNext_AttackAIModule attack = new BattleNext_AttackAIModule(ship, flags);
			BattleNext_SystemAI ai = assertDoesNotThrow(() -> BattleNext_SystemAIFactory.create(ship, system, flags, threat, attack, flocking, maneuver), type);
			if (type.equals("NONE")) {
				assertNull(ai);
			} else {
				assertNotNull(ai, type);
				if (type.equals("EMP")) {
					assertInstanceOf(BattleNext_EmpSystemAI.class, ai);
				}
			}
		}
		assertEquals(1, customInitializations.get());
	}

	@Test
	void missingAndUnknownTypesFailWithSystemIdentity() throws JSONException {
		assertThrows(IllegalStateException.class,
				() -> BattleNext_SystemAIFactory.create(null, system(null), null, null, null, null, null));
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> BattleNext_SystemAIFactory.create(null, system("NOT_REGISTERED"), null, null, null, null, null));
		assertTrue(error.getMessage().contains("test_system"));
	}

	private ShipSystemAPI system(String type) throws JSONException {
		JSONObject data = new JSONObject();
		if (type != null) data.put("aiType", type);
		ShipSystemSpecAPI spec = proxy(ShipSystemSpecAPI.class, (method, args) -> switch (method) {
			case "getSpecJson" -> data;
			case "getId" -> "test_system";
			case "getAIScript" -> proxy(ShipSystemAIScript.class, (name, values) -> {
				if (name.equals("init")) {
					assertNotNull(values[0]);
					assertNotNull(values[1]);
					assertNotNull(values[2]);
					assertSame(Global.getCombatEngine(), values[3]);
					customInitializations.incrementAndGet();
				}
				return null;
			});
			default -> null;
		});
		return proxy(ShipSystemAPI.class, (method, args) -> switch (method) {
			case "getSpecAPI" -> spec;
			case "getScript" -> "MINE_STRIKE".equals(type) ? new MineStrikeStats() : new DroneStrikeStats();
			default -> null;
		});
	}
}
