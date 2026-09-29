package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.DamperFieldStats;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.DamperFieldOmegaSystemAI.
 */
public class BattleNext_DamperFieldOmegaSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final ShipSystemAPI system;
	private final BattleNext_FiringSolutionEval eval;
	private final IntervalUtil interval = new IntervalUtil(0.0F, 0.2F);
	// intentionally kept dead: vanilla computes these costs and never reads them.
	private final float activationFluxCost;
	private final float fluxPerSecondCost;

	public BattleNext_DamperFieldOmegaSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval) {
		this.ship = ship;
		this.flags = flags;
		this.threatEval = threatEval;
		this.system = DamperFieldStats.getDamper(ship);
		this.eval = new BattleNext_FiringSolutionEval(ship, flags);
		this.eval.setUseBurstDps(true);
		this.eval.setAssumeFiring(true);
		this.eval.setAlwaysEvaluateDamage(true);
		float capacity = ship.getMutableStats().getFluxCapacity().getBaseValue();
		float dissipation = ship.getMutableStats().getFluxDissipation().getBaseValue();
		this.activationFluxCost = system.getSpecAPI().getFluxPerUse()
				+ system.getSpecAPI().getFluxPerUseBaseCap() * capacity
				+ system.getSpecAPI().getFluxPerUseBaseRate() * dissipation;
		this.fluxPerSecondCost = system.getSpecAPI().getFluxPerSecond()
				+ system.getSpecAPI().getFluxPerSecondBaseCap() * capacity
				+ system.getSpecAPI().getFluxPerSecondBaseRate() * dissipation;
	}

	public boolean isPhaseCloakSystem() {
		return system == ship.getPhaseCloak();
	}

	public void giveSystemCommand() {
		ShipCommand command = isPhaseCloakSystem()
				? ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK : ShipCommand.USE_SYSTEM;
		ship.giveCommand(command, null, -1);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (system.getAmmo() <= 0 || !interval.intervalElapsed()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;
		if (system.isOn()) {
			flags.setFlag(AIFlags.DO_NOT_VENT);
			return;
		}
		boolean missileCovered = false;
		boolean targetCovered = false;
		if (ship.getShield() != null) {
			if (ship.getShield().isOn()) {
				float facing = ship.getShield().getFacing();
				float arc = Math.max(90.0F, ship.getShield().getActiveArc());
				if (missileDangerDir != null) missileCovered = BattleNext_ShipAIHelper.isInArc(facing, arc,
						BattleNext_ShipAIHelper.getAngleFast(missileDangerDir));
				if (target != null) targetCovered = BattleNext_ShipAIHelper.isInArc(facing, arc,
						ship.getLocation(), target.getLocation());
			} else if (ship.getShield().getType() == ShieldType.OMNI) {
				missileCovered = true;
				targetCovered = true;
			} else {
				if (missileDangerDir != null) missileCovered = BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 90.0F,
						BattleNext_ShipAIHelper.getAngleFast(missileDangerDir));
				if (target != null) targetCovered = BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 90.0F,
						ship.getLocation(), target.getLocation());
			}
		}
		eval.setAssumeFiring(!missileCovered);
		eval.resetSolution();
		boolean incoming = missileDangerDir != null && eval.hasSolution();
		float timeToHit = eval.getBestTimeToHit();
		float maxDamage = eval.getMaxWeaponDamage();
		eval.updateShieldState(threatEval, missileDangerDir, collisionDangerDir);
		if (ship.getShield() != null) maxDamage -= eval.getMaxWeaponDamage();
		boolean criticalHull = ship.getHitpoints() < 150.0F * ship.getHullSize().ordinal();
		if (ship.getShield() != null) {
			if (incoming && !criticalHull && (eval.isShieldFacingOk() || !missileCovered) && timeToHit < 1.0F) {
				giveSystemCommand();
				return;
			}
		} else if (ship.getPhaseCloak() != null && !isPhaseCloakSystem()) {
			if (incoming && (eval.isShieldFacingOk() || !missileCovered) && timeToHit < 1.0F
					&& ship.getFluxLevel() > 0.9F) {
				giveSystemCommand();
				return;
			}
		} else if (incoming && timeToHit < 1.0F) {
			giveSystemCommand();
			return;
		}

		float armor = ship.getArmorGrid().getArmorRating() + ship.getHitpoints() * 0.1F;
		if (threatEval.getMaxThreatData() != null) {
			// approximation: the engine armor evaluator exposes directional armor;
			// the public average armor slice is the nearest directional measure.
			float direction = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(),
						threatEval.getMaxThreatData().getShip().getLocation());
			armor = ship.getAverageArmorInSlice(direction, 90.0F) + ship.getHitpoints() * 0.1F;
		}
		boolean heavyDamage = maxDamage > armor;
		boolean nearbyExplosion = false;
		if (ship.getShield() == null || !targetCovered) {
			boolean targetCritical = target != null
					&& target.getHitpoints() < 150.0F * target.getHullSize().ordinal();
			if (target != null && !target.isFighter()) {
				float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
				nearbyExplosion = targetCritical && distance < ship.getCollisionRadius()
						+ DamagingExplosionSpec.getShipExplosionRadius(target) + 50.0F;
				heavyDamage |= nearbyExplosion;
			}
			if (maxDamage > armor * 0.25F && criticalHull) heavyDamage = true;
		}
		if (ship.getShield() != null) {
			if (eval.isShieldFacingOk() && !nearbyExplosion || heavyDamage && (!nearbyExplosion || !targetCovered)) {
				giveSystemCommand();
				return;
			}
		} else if (ship.getPhaseCloak() != null && !isPhaseCloakSystem()) {
			if (heavyDamage && !threatEval.isAllThreatsActive() && ship.getFluxLevel() > 0.9F) {
				giveSystemCommand();
				return;
			}
		} else if (heavyDamage && !threatEval.isAllThreatsActive()) {
			giveSystemCommand();
			return;
		}
		if (system.getId().endsWith("_omega")) {
			float disabled = 0.0F;
			float total = 0.0F;
			for (WeaponAPI weapon : ship.getAllWeapons()) {
				if (weapon.isDecorative()) continue;
				float weight = switch (weapon.getSize()) {
					case LARGE -> 4.0F;
					case MEDIUM -> 2.0F;
					default -> 1.0F;
				};
				total += weight;
				if (weapon.isDisabled()) disabled += weight;
			}
			// approximation: the public engine controller omits the engine-only
			// computeDisabledFraction(false) parameter.
			disabled += ship.getEngineController().computeDisabledFraction() * total;
			total *= 2.0F;
			if (eval.isShieldFacingOk() && disabled > 0.33F) giveSystemCommand();
		}
	}
}
