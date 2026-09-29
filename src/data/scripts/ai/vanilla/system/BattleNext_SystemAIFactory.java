package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.json.JSONObject;

/**
 * Public-API equivalent of ShipSystemSpec.createSystemAI().
 */
public class BattleNext_SystemAIFactory {
	private BattleNext_SystemAIFactory() {
	}

	public static BattleNext_SystemAI create(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags,
			BattleNext_ThreatEvalAI threatEval, BattleNext_AttackAIModule attackAI,
			BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		JSONObject data = system.getSpecAPI().getSpecJson();
		String type = data == null ? null : data.optString("aiType", null);
		try {
			if (type == null || type.isEmpty()) {
				throw new IllegalArgumentException("Missing aiType for ship system " + system.getSpecAPI().getId());
			}
			return switch (type) {
				case "FLARE" -> new BattleNext_BomberWeaponSystemAI(ship, threatEval);
				case "FLARE_JAMMER" -> new BattleNext_WingWeaponSystemAI(ship);
				case "CANISTER_FLAK" -> new BattleNext_TargetScanWeaponSystemAI(ship);
				case "BURN_DRIVE" -> new BattleNext_RetreatSystemAI(ship, flags, threatEval, attackAI, flockingAI, maneuverAPI);
				case "BURN_DRIVE_TOGGLE" -> new BattleNext_EngineModSystemAI(ship, flags, threatEval, attackAI, flockingAI, maneuverAPI);
				case "MANEUVERING_JETS" -> new BattleNext_WingEngineModSystemAI(ship, threatEval, flockingAI);
				case "FAST_RELOAD" -> new BattleNext_FastReloadSystemAI(ship);
				case "AMMO_RELOAD" -> new BattleNext_AmmoReloadSystemAI(ship, threatEval);
				case "FORTRESS_SHIELD" -> new BattleNext_ShieldModSystemAI(ship, flags);
				case "DAMPER_FIELD" -> new BattleNext_DamperFieldOmegaSystemAI(ship, flags, threatEval);
				case "HIGH_ENERGY_FOCUS" -> new BattleNext_ShieldFluxSystemAI(ship);
				case "PHASE_TELEPORTER" -> new BattleNext_TeleporterTaskSystemAI(ship, flags, threatEval, attackAI, flockingAI);
				case "PHASE_TELEPORTER_2" -> new BattleNext_TeleporterTargetingSystemAI(ship, flags, threatEval, attackAI, flockingAI);
				case "PHASE_DISPLACER" -> new BattleNext_DisplacerSystemAI(ship, flags, threatEval, attackAI, flockingAI);
				case "DRONE_LAUNCHER_PD" -> new BattleNext_DroneLauncherSystemAI(ship, threatEval, true);
				case "DRONE_LAUNCHER_STANDARD" -> new BattleNext_DroneLauncherSystemAI(ship, threatEval, false);
				case "WEAPON_BOOST" -> new BattleNext_WeaponTypeSystemAI(ship, flags);
				case "DYNAMIC_STABILIZER" -> new BattleNext_GenericSystemAI(ship, flags, threatEval, attackAI, flockingAI);
				case "LIDAR_ARRAY" -> new BattleNext_LidarArraySystemAI(ship, flags);
				case "TEMPORAL_SHELL" -> new BattleNext_EngagementSystemAI(ship, flags, threatEval, attackAI, flockingAI);
				case "ACAUSAL_DISRUPTOR" -> new BattleNext_AcausalDisruptorSystemAI(ship, flags, threatEval);
				case "MOTE_ATTRACTOR" -> new BattleNext_MoteControlSystemAI(ship, flags, threatEval);
				case "ENTROPY_AMPLIFIER" -> new BattleNext_EntropyAmplifierSystemAI(ship, flags, threatEval, attackAI);
				case "INTERDICTOR" -> new BattleNext_PhaseStateSystemAI(ship, flags);
				case "MINE_STRIKE" -> new BattleNext_MineStrikeSystemAI(ship, flags);
				case "DRONE_STRIKE" -> new BattleNext_DroneStrikeSystemAI(ship);
				case "EMP" -> new BattleNext_EmpSystemAI(ship, flags);
				case "PHASE_CLOAK" -> new BattleNext_PhaseCloakSystemAI(ship, flags, threatEval, maneuverAPI);
				case "NONE" -> null;
				case "TARGETING_FEED" -> new BattleNext_WingSupportSystemAI(ship, flags);
				case "RESERVE_DEPLOYMENT" -> new BattleNext_ReserveWingSystemAI(ship, flags);
				case "RECALL_DEVICE" -> new BattleNext_RecallDeviceSystemAI(ship, flags);
				case "CUSTOM" -> new BattleNext_CustomSystemAI(ship, system, flags);
				default -> throw new IllegalArgumentException("Unknown aiType " + type);
			};
		} catch (Exception exception) {
			throw new IllegalStateException("Error creating AI of type [" + type + "] for ship system ["
					+ system.getSpecAPI().getId() + "]", exception);
		}
	}
}
