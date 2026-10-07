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
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_SystemAIFactoryTest extends BattleNext_TestGameFixture {

	private CombatEngineAPI previousEngine;
	private final AtomicInteger customInitializations = new AtomicInteger();
	private final Object[] customInitialization = new Object[4];

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
				continue;
			}
			Class<? extends BattleNext_SystemAI> expected = switch (type) {
				case "FLARE" -> BattleNext_BomberWeaponSystemAI.class;
				case "FLARE_JAMMER" -> BattleNext_WingWeaponSystemAI.class;
				case "CANISTER_FLAK" -> BattleNext_TargetScanWeaponSystemAI.class;
				case "BURN_DRIVE" -> BattleNext_RetreatSystemAI.class;
				case "BURN_DRIVE_TOGGLE" -> BattleNext_EngineModSystemAI.class;
				case "MANEUVERING_JETS" -> BattleNext_WingEngineModSystemAI.class;
				case "FAST_RELOAD" -> BattleNext_FastReloadSystemAI.class;
				case "AMMO_RELOAD" -> BattleNext_AmmoReloadSystemAI.class;
				case "FORTRESS_SHIELD" -> BattleNext_ShieldModSystemAI.class;
				case "DAMPER_FIELD" -> BattleNext_DamperFieldOmegaSystemAI.class;
				case "HIGH_ENERGY_FOCUS" -> BattleNext_ShieldFluxSystemAI.class;
				case "PHASE_TELEPORTER" -> BattleNext_TeleporterTaskSystemAI.class;
				case "PHASE_TELEPORTER_2" -> BattleNext_TeleporterTargetingSystemAI.class;
				case "PHASE_DISPLACER" -> BattleNext_DisplacerSystemAI.class;
				case "DRONE_LAUNCHER_PD", "DRONE_LAUNCHER_STANDARD" -> BattleNext_DroneLauncherSystemAI.class;
				case "WEAPON_BOOST" -> BattleNext_WeaponTypeSystemAI.class;
				case "DYNAMIC_STABILIZER" -> BattleNext_GenericSystemAI.class;
				case "LIDAR_ARRAY" -> BattleNext_LidarArraySystemAI.class;
				case "TEMPORAL_SHELL" -> BattleNext_EngagementSystemAI.class;
				case "ACAUSAL_DISRUPTOR" -> BattleNext_AcausalDisruptorSystemAI.class;
				case "MOTE_ATTRACTOR" -> BattleNext_MoteControlSystemAI.class;
				case "ENTROPY_AMPLIFIER" -> BattleNext_EntropyAmplifierSystemAI.class;
				case "INTERDICTOR" -> BattleNext_PhaseStateSystemAI.class;
				case "MINE_STRIKE" -> BattleNext_MineStrikeSystemAI.class;
				case "DRONE_STRIKE" -> BattleNext_DroneStrikeSystemAI.class;
				case "EMP" -> BattleNext_EmpSystemAI.class;
				case "PHASE_CLOAK" -> BattleNext_PhaseCloakSystemAI.class;
				case "TARGETING_FEED" -> BattleNext_WingSupportSystemAI.class;
				case "RESERVE_DEPLOYMENT" -> BattleNext_ReserveWingSystemAI.class;
				case "RECALL_DEVICE" -> BattleNext_RecallDeviceSystemAI.class;
				case "CUSTOM" -> BattleNext_CustomSystemAI.class;
				default -> throw new AssertionError("Unspecified system test: " + type);
			};
			assertInstanceOf(expected, ai, type);
			if (type.equals("CUSTOM")) {
				assertSame(ship, customInitialization[0]);
				assertSame(system, customInitialization[1]);
				assertSame(flags, customInitialization[2]);
				assertSame(Global.getCombatEngine(), customInitialization[3]);
			}
		}
		assertEquals(1, customInitializations.get());
	}

	@Test
	void missingAndUnknownTypesFailWithSystemIdentity() throws JSONException {
		IllegalStateException missing = assertThrows(IllegalStateException.class,
				() -> BattleNext_SystemAIFactory.create(null, system(null), null, null, null, null, null));
		assertEquals("Error creating AI of type [null] for ship system [test_system]", missing.getMessage());
		assertInstanceOf(IllegalArgumentException.class, missing.getCause());
		assertEquals("Missing aiType for ship system test_system", missing.getCause().getMessage());
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> BattleNext_SystemAIFactory.create(null, system("NOT_REGISTERED"), null, null, null, null, null));
		assertEquals("Error creating AI of type [NOT_REGISTERED] for ship system [test_system]", error.getMessage());
		assertInstanceOf(IllegalArgumentException.class, error.getCause());
		assertEquals("Unknown aiType NOT_REGISTERED", error.getCause().getMessage());
	}

	private ShipSystemAPI system(String type) throws JSONException {
		JSONObject data = new JSONObject();
		if (type != null) data.put("aiType", type);
		ShipSystemSpecAPI spec = proxy(ShipSystemSpecAPI.class, (method, args) -> switch (method) {
			case "getSpecJson" -> data;
			case "getId" -> "test_system";
			case "getAIScript" -> proxy(ShipSystemAIScript.class, (name, values) -> {
				if (name.equals("init")) {
					System.arraycopy(values, 0, customInitialization, 0, customInitialization.length);
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
