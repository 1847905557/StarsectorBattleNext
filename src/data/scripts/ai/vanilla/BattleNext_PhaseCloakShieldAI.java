package data.scripts.ai.vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.impl.combat.PhaseCloakStats;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.maneuvers.BattleNext_StrafeTargetManeuverV2;
import data.scripts.ai.vanilla.shields.BattleNext_ShieldAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.PhaseCloakShieldAI.
 */
public class BattleNext_PhaseCloakShieldAI implements BattleNext_ShieldAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_FiringSolutionEval eval;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private final IntervalUtil interval = new IntervalUtil(0.1F, 0.2F);
	private final ShipSystemAPI phaseSystem;
	private final boolean phaseBrawler;
	private boolean usePhaseCloak;
	private float phaseActiveTime;
	private float minPhaseActiveTime = 0.01F;
	private float activationCooldown = 1.0F;
	private float timeSinceDecision = 100.0F;
	private float decisionInterval = 10.0F + (float) Math.random() * 10.0F;
	private boolean wantsToPhase;

	public BattleNext_PhaseCloakShieldAI(ShipAPI ship, ShipwideAIFlags flags,
			BattleNext_ManeuverAPI maneuverAPI, boolean usePhaseCloak) {
		this.ship = ship;
		this.flags = flags;
		this.maneuverAPI = maneuverAPI;
		this.usePhaseCloak = usePhaseCloak;
		this.eval = new BattleNext_FiringSolutionEval(ship, flags, 5000.0F);
		this.phaseSystem = ship.getPhaseCloak() != null ? ship.getPhaseCloak() : ship.getSystem();
		boolean brawler = false;
		if (ship.getVariant() != null) {
			for (String hullmodId : ship.getVariant().getHullMods()) {
				if (Global.getSettings().getHullModSpec(hullmodId).hasTag("phase_brawler")) {
					brawler = true;
					break;
				}
			}
		}
		this.phaseBrawler = brawler;
	}

	@Override
	public boolean shouldKeepShieldOn() {
		return false;
	}

	@Override
	public boolean hasFiringSolution() {
		return eval.hasSolution();
	}

	@Override
	public BattleNext_FiringSolutionEval getEval() {
		return eval;
	}

	public void setUsePhaseCloak(boolean usePhaseCloak) {
		this.usePhaseCloak = usePhaseCloak;
	}

	@Override
	public void advance(float amount, BattleNext_ThreatEvalAI threatEval, Vector2f avoidanceVector,
			Vector2f collisionDangerDir, ShipAPI target) {
		if (ship.isDefenseDisabled()) return;
		activationCooldown += amount;
		timeSinceDecision += amount;
		SystemState state = phaseSystem.getState();
		boolean active = state == SystemState.IN || state == SystemState.ACTIVE;
		if (state == SystemState.OUT) return;
		if (active && flags.hasFlag(AIFlags.STAY_PHASED)) return;
		if (active) flags.setFlag(AIFlags.DO_NOT_VENT);
		if (active) phaseActiveTime += amount;
		else {
			minPhaseActiveTime = 0.01F;
			phaseActiveTime = 0.0F;
		}
		if (active && phaseActiveTime < minPhaseActiveTime) return;
		if (active && flags.hasFlag(AIFlags.DO_NOT_USE_FLUX)) {
			activatePhase();
			return;
		}
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;

		AssignmentInfo assignment = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner())
				.getTaskManager(ship.isAlly()).getAssignmentFor(ship);
		Vector2f assignmentPoint = assignment != null && assignment.getType() != CombatAssignmentType.RETREAT
				&& assignment.getTarget() != null ? assignment.getTarget().getLocation() : null;
		float distanceToOrder = assignmentPoint == null ? 0.0F
				: BattleNext_ShipAIHelper.getDistance(ship.getLocation(), assignmentPoint);
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		boolean engaged = ai != null && ai.isEngaged();
		boolean safe = threatEval.getTotalThreat() <= 0.0F || threatEval.isAllThreatsValid();
		boolean traveling = !engaged && distanceToOrder > 1000.0F && safe;
		traveling |= target == null && !engaged && (ship.isPhased()
				|| ship.getVelocity().length() > ship.getMaxSpeed() * 0.5F) && safe;
		if (traveling || ship.isRetreating()) {
			if (!active && ship.getHardFluxLevel() <= 0.0F) {
				activatePhase();
				return;
			}
			if (active && ship.getHardFluxLevel() > 0.25F && traveling
					&& avoidanceVector == null && collisionDangerDir == null) {
				activatePhase();
				return;
			}
			if (active && traveling) return;
		}

		boolean selfNonCombat = ship.isNonCombat(true);
		// approximation: the engine variant's non-combat property is internal;
		// ShipAPI exposes the equivalent combat capability check.
		boolean targetNonCombat = target != null && target.isNonCombat(false);
		// intentionally kept dead: vanilla computes the strafe target threat score
		// without consuming its result.
		BattleNext_StrafeTargetManeuverV2.computeThreatScore(ship, target, selfNonCombat, targetNonCombat);
		boolean brawler = phaseBrawler || ship.isCruiser() || ship.isCapital();
		if (!ship.isFrigate() && target != null && target.getHullSize().ordinal() >= ship.getHullSize().ordinal()) brawler = true;
		if (brawler) {
			float totalWeight = 0.0F;
			float reloading = 0.0F;
			float burstFlux = 0.0F;
			boolean firingNonPd = false;
			for (WeaponAPI weapon : ship.getAllWeapons()) {
				if (weapon.isDecorative()) continue;
				float weight = switch (weapon.getSize()) {
					case LARGE -> 8.0F;
					case MEDIUM -> 4.0F;
					default -> 1.0F;
				};
				totalWeight += weight;
				boolean recovering = weapon.getCooldownRemaining() >= 0.7F
						|| weapon.getAmmoPerSecond() > 0.1F && weapon.getAmmo() < weapon.getMaxAmmo();
				boolean firing = weapon.isInBurst() || weapon.isFiring() && weapon.getCooldownRemaining() <= 0.0F;
				firingNonPd |= firing && !weapon.hasAIHint(AIHints.PD);
				if (recovering) reloading += weight;
				burstFlux += weapon.getDerivedStats().getBurstDamage() * weapon.getDerivedStats().getFluxPerDam();
			}
			totalWeight = Math.max(1.0F, totalWeight);
			float softFlux = ship.getFluxLevel() - ship.getHardFluxLevel();
			float available = BattleNext_ShipAIHelper.getFluxAvailable(ship);
			if (!active && phaseSystem.getCooldownRemaining() <= 0.0F) {
				if (ship.getFluxLevel() >= 0.95F && softFlux > 0.1F
						|| ship.getFluxLevel() >= 0.85F && softFlux > 0.1F && !firingNonPd
						|| !firingNonPd && reloading / totalWeight > 0.49F) {
					flags.setFlag(AIFlags.STAY_PHASED, 0.25F);
					flags.setFlag(AIFlags.PHASE_BRAWLER_DUMPING_FLUX, 0.5F);
					activatePhase();
					return;
				}
			} else if (active && (flags.hasFlag(AIFlags.PHASE_BRAWLER_DUMPING_FLUX)
					&& softFlux > 0.03F && available < burstFlux * 1.25F
					|| reloading > 0.0F && ship.getHardFluxLevel() < 0.8F && softFlux > 0.03F)) {
				flags.setFlag(AIFlags.STAY_PHASED, 0.25F);
				flags.setFlag(AIFlags.PHASE_BRAWLER_DUMPING_FLUX, 0.5F);
				return;
			}
		}

		eval.setAssumingFiring(phaseSystem.isOn());
		eval.setConsiderMissiles(true);
		eval.computeSolution(phaseSystem.getCooldown() + 1.0F);
		float armor = ship.getArmorGrid().getArmorRating();
		if (threatEval.getMaxThreatData() != null) {
			float direction = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(),
						threatEval.getMaxThreatData().getShip().getLocation());
			armor = ship.getAverageArmorInSlice(direction, 90.0F);
		}
		boolean incoming = eval.hasSolution() && (ship.isPhased()
				|| eval.getBestTimeToHit() <= phaseSystem.getCooldownRemaining() + 0.2F);
		boolean critical = eval.getMaxWeaponDamage() > armor * 3.0F
				|| eval.getShieldDamage() > ship.getHitpoints() * (ship.getHullLevel() < 0.75F ? 0.2F : 0.33F);
		if (critical) flags.setFlag(AIFlags.IN_CRITICAL_DPS_DANGER, 0.2F);
		else flags.unsetFlag(AIFlags.IN_CRITICAL_DPS_DANGER);
		float distance = target == null ? Float.MAX_VALUE
				: BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
		float combinedRadius = ship.getCollisionRadius() + (target == null ? 0.0F : target.getCollisionRadius()) * 0.75F;
		boolean outOfAttackRange = distance - combinedRadius > maneuverAPI.getAttackAI().getAttackRange() * 1.2F;
		float rearArc = 90.0F;
		float goodSpotArc = 90.0F;
		if (target != null && target.getShield() != null && target.getShield().getType() == com.fs.starfarer.api.combat.ShieldAPI.ShieldType.FRONT) {
			rearArc = Math.max(25.0F, Math.min(120.0F, 360.0F - Math.min(360.0F, target.getShield().getArc() + 40.0F)));
			goodSpotArc = Math.max(25.0F, Math.min(120.0F, 360.0F - target.getShield().getArc() + 30.0F));
		}
		float prolongedPhaseArc = Math.max(0.0F, phaseActiveTime - 3.0F) * 15.0F;
		rearArc = Math.min(300.0F, rearArc + prolongedPhaseArc);
		goodSpotArc = Math.min(300.0F, goodSpotArc + prolongedPhaseArc);
		if (target != null && (target.isFrigate() || target.isFighter())) {
			rearArc = Math.max(rearArc, 270.0F);
			goodSpotArc = Math.max(goodSpotArc, 235.0F);
		}
		float targetAngle = target == null ? 0.0F
				: BattleNext_ShipAIHelper.getAngleFast(target.getLocation(), ship.getLocation());
		boolean behind = target != null && BattleNext_ShipAIHelper.getAngleDiff(targetAngle, target.getFacing())
				>= 180.0F - rearArc * 0.5F;
		boolean goodSpot = target != null && BattleNext_ShipAIHelper.getAngleDiff(targetAngle, target.getFacing())
				>= 180.0F - goodSpotArc * 0.5F;
		float explosionRadius = target == null ? 0.0F
				: ship.getCollisionRadius() + DamagingExplosionSpec.getShipExplosionRadius(target) + 50.0F;
		boolean targetDoomed = target != null && target.getHitpoints() < 150.0F * target.getHullSize().ordinal();
		if (target != null) {
			float bestGroupDamage = 0.0F;
			for (com.fs.starfarer.api.combat.WeaponGroupAPI group : ship.getWeaponGroupsCopy()) {
				float groupDamage = 0.0F;
				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if (!BattleNext_ShipAIHelper.isInArc(weapon.getCurrAngle(), 90.0F,
							weapon.getLocation(), target.getLocation())) continue;
					float damage = weapon.getDerivedStats().getBurstDamage();
					if (damage <= 0.0F) damage = Math.max(weapon.getDerivedStats().getDamagePerShot(),
							weapon.getDerivedStats().getDps());
					groupDamage += damage;
				}
				bestGroupDamage = Math.max(bestGroupDamage, groupDamage);
			}
			targetDoomed |= target.getHitpoints() < bestGroupDamage
					* target.getMutableStats().getHullDamageTakenMult().getModifiedValue();
		}
		boolean imminentExplosion = target != null && !target.isFighter() && targetDoomed
				&& distance < explosionRadius;
		if (targetDoomed && behind) flags.setFlag(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL, 0.5F, explosionRadius);
		boolean targetRecovering = target != null && target.getFluxTracker().isOverloadedOrVenting()
				&& (target.getFluxTracker().getOverloadTimeRemaining() > 3.0F
						|| target.getFluxTracker().getTimeToVent() > 4.0F);
		if (goodSpot && !critical && ship.getHardFluxLevel() < 0.75F) flags.setFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT);
		else flags.unsetFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT);

		float fluxCost = phaseSystem.getFluxPerSecond() * 0.5F;
		float availableFlux = BattleNext_ShipAIHelper.getFluxAvailable(ship);
		boolean fluxLimited = availableFlux < phaseSystem.getFluxPerUse() + fluxCost
				|| active && BattleNext_ShipAIHelper.getFluxAvailable(ship) < fluxCost;
		boolean ampleFlux = availableFlux >= phaseSystem.getFluxPerUse() * 2.0F + fluxCost * 4.0F;
		if (ampleFlux) wantsToPhase = false;
		if (target != null && incoming && ship.getHardFluxLevel() > 0.0F
				&& target.getHullSize().ordinal() > ship.getHullSize().ordinal()
				&& !flags.hasFlag(AIFlags.PHASE_ATTACK_RUN)
				&& !flags.hasFlag(AIFlags.PHASE_ATTACK_RUN_TIMEOUT)
				&& !flags.hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT)
				&& !flags.hasFlag(AIFlags.BACK_OFF) && active
				&& BattleNext_ShipAIHelper.isInArc(target.getFacing(), 30.0F,
						target.getLocation(), ship.getLocation())) {
			flags.setFlag(AIFlags.BACK_OFF, 2.0F + (float) Math.random() * 2.0F);
		}
		boolean attackRun = active && (critical || flags.hasFlag(AIFlags.PHASE_ATTACK_RUN))
				&& !targetRecovering && !imminentExplosion && target != null
				&& target.getHullSize().ordinal() >= ship.getHullSize().ordinal()
				&& ship.getHullSize().ordinal() < HullSize.CRUISER.ordinal();
		if (attackRun && BattleNext_ShipAIHelper.getNumberOfEnemiesInLineOfFire(ship, target, distance + 1000.0F) > 0) attackRun = false;
		if (attackRun) flags.setFlag(AIFlags.PHASE_ATTACK_RUN);
		else flags.unsetFlag(AIFlags.PHASE_ATTACK_RUN);
		Vector2f intercept = target == null ? null
				: BattleNext_ShipAIHelper.getInterceptPoint(ship, ship.getAimAccuracy(), target, 5000.0F);
		boolean aligned = intercept != null && BattleNext_ShipAIHelper.isInArc(ship.getFacing(), 30.0F,
				ship.getLocation(), intercept);
		boolean slowChase = false;
		if (target != null && outOfAttackRange) {
			float closing = BattleNext_ShipAIHelper.getClosingSpeed(ship, target);
			float targetCruise = BattleNext_ShipAIHelper.getCruiseSpeed(target);
			float ownCruise = BattleNext_ShipAIHelper.getCruiseSpeed(ship);
			slowChase = closing < 20.0F && !flags.hasFlag(AIFlags.BACKING_OFF)
					&& Math.max(closing, ownCruise - targetCruise) < 20.0F;
		}
		if (fluxLimited && active) {
			wantsToPhase = true;
			activatePhase();
			return;
		}
		if (!active && (critical || incoming && !targetRecovering || imminentExplosion
				|| outOfAttackRange && collisionDangerDir != null || slowChase && ship.getHardFluxLevel() < 0.25F)) {
			activatePhase();
			return;
		}
		if (active && phaseActiveTime > minPhaseActiveTime && !critical && !incoming
				&& !outOfAttackRange && !wantsToPhase && !imminentExplosion
				&& !attackRun && aligned && timeSinceDecision > decisionInterval) {
			decisionInterval = 2.0F + (float) Math.random() * 6.0F;
			timeSinceDecision = (float) Math.random();
			activatePhase();
			return;
		}
		if (active && target == null && !critical && collisionDangerDir == null) activatePhase();
	}

	private void activatePhase() {
		if (activationCooldown < 0.5F) return;
		activationCooldown = 0.0F;
		if (phaseSystem.getState() != SystemState.IN && phaseSystem.getState() != SystemState.ACTIVE) {
			flags.setFlag(AIFlags.DO_NOT_VENT);
		}
		// approximation: the engine clears the private charge tracker before
		// toggling; the public system API only exposes the command transition.
		ShipCommand command = usePhaseCloak ? ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK : ShipCommand.USE_SYSTEM;
		ship.giveCommand(command, null, -1);
	}
}
