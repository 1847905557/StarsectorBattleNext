package data.scripts.ai.vanilla.shields;

import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

public class BattleNext_FrontShieldAI implements BattleNext_ShieldAI {
	private static final float unused = 3.0F;
	private ShipAPI ship;
	private float hitpoints;
	private float fluxCapacity;
	private final ShipwideAIFlags aiFlags;
	private BattleNext_FiringSolutionEval eval;
	private float shieldUnfoldTime;
	private float timeSinceShieldToggle = 3.0F;
	private float shieldHitTolerance = 3.0F;
	private float shieldHitTime = 0.0F;
	private boolean shieldsOn = false;

	public BattleNext_FrontShieldAI(ShipAPI ship, ShipwideAIFlags aiFlags) {
		this.ship = ship;
		this.aiFlags = aiFlags;
		this.hitpoints = ship.getHitpoints();
		// engine flux capacity is the max flux in the public API
		this.fluxCapacity = ship.getMaxFlux();
		this.eval = new BattleNext_FiringSolutionEval(ship, aiFlags);
		this.eval.setRequireDamageToReachHull(true);
		// engine reads the shield charge tracker, whose charge time is seeded from getUnfoldTime()
		this.shieldUnfoldTime = ship.getShield().getUnfoldTime();
	}

	@Override
	public boolean shouldKeepShieldOn() {
		return this.shieldsOn;
	}

	@Override
	public void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector, Vector2f collisionThreatDir, ShipAPI target) {

		if (this.ship.getFluxTracker().isOverloadedOrVenting()) {
			this.shieldHitTime = 0.0F;
			this.timeSinceShieldToggle = 0.0F;
			this.shieldsOn = false;
		} else if (this.aiFlags.hasFlag(AIFlags.DO_NOT_USE_SHIELDS)) {
			if (this.ship.getShield().isOn()) {
				this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
				this.shieldsOn = false;
			}
		} else {
			this.shieldHitTolerance = 0.5F;
			this.eval.advance(amount, threatEvalAI, avoidanceVector, collisionThreatDir);
			if (this.eval.isShieldFacingOk() && this.ship.getShield().isOn()) {
				this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
				this.shieldsOn = false;
				this.timeSinceShieldToggle = 0.0F;
			} else if (!this.ship.getShield().isOn() || !this.aiFlags.hasFlag(AIFlags.KEEP_SHIELDS_ON)) {
				if (this.ship.getShield().isOn()) {
					this.shieldHitTime += amount;
				} else {
					this.shieldHitTime = 0.0F;
				}

				float totalThreat = threatEvalAI.getTotalThreat();
				ShieldAPI shield = this.ship.getShield();
				if (this.aiFlags.hasFlag(AIFlags.DO_NOT_USE_FLUX)) {
					if (shield.isOn()) {
						this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
						this.shieldsOn = false;
					}
				} else {
					// intentionally kept dead: read in vanilla and never used
					float safeFromDangerTime = 0.0F;
					if (this.aiFlags.getCustom(AIFlags.SAFE_FROM_DANGER_TIME) != null) {
						safeFromDangerTime = (Float) this.aiFlags.getCustom(AIFlags.SAFE_FROM_DANGER_TIME);
					}

					// intentionally kept dead: read in vanilla and never used
					float unfoldTime = shield.getUnfoldTime();
					float energy = BattleNext_ShipAIHelper.getFluxAvailable(this.ship);
					float energyFraction = energy / this.fluxCapacity;
					boolean dangerBeforeUnfold = this.eval.hasDetectedIncomingDamage() && this.eval.getBestTimeToHit() < this.shieldUnfoldTime + 1.0F
						|| this.ship.getSinceLastDamageTaken() <= 0.5F;
					boolean retreatHoldOpen = false;
					if (this.ship.isRetreating() && shield.isOn() && shield.getActiveArc() >= shield.getArc()) {
						dangerBeforeUnfold &= this.eval.getHullDamage() > 0.0F;
						retreatHoldOpen = this.eval.getHullDamage() <= 0.0F;
					} else if (this.ship.isRetreating() && this.ship.isDirectRetreat()) {
						retreatHoldOpen = true;
					}

					// intentionally kept dead: vanilla resets this flag right after computing it
					retreatHoldOpen = false;
					if (shield.isOn()
						&& (totalThreat == 0.0F || threatEvalAI.isAllThreatsActive())
						&& !dangerBeforeUnfold
						&& avoidanceVector == null
						&& collisionThreatDir == null
						&& this.shieldHitTime > this.shieldHitTolerance) {
						this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
						this.shieldsOn = false;
					} else {
						if (!shield.isOn() || totalThreat != 0.0F && !threatEvalAI.isAllThreatsActive() || avoidanceVector != null || collisionThreatDir != null || dangerBeforeUnfold) {
							this.shieldHitTime = 0.0F;
						}

						boolean criticalHull = this.ship.getHitpoints() < 50.0F * this.ship.getHullSize().ordinal();
						// approximation: engine fluxAvailable is maxFlux - currFlux in the public API
						if (this.ship.getMaxFlux() - this.ship.getFluxTracker().getCurrFlux() < 1000.0F && this.ship.getFluxLevel() > 0.8F) {
							// intentionally kept dead: neither branch has an effect in vanilla
							boolean unusedFluxReserveLow = true;
						} else {
							// intentionally kept dead: neither branch has an effect in vanilla
							boolean unusedFalseBranch = false;
						}

						// intentionally kept dead: computed in vanilla and never read
						boolean unusedFluxBelowRaiseThreshold = this.ship.getFluxLevel() < 0.6F;
						// intentionally kept dead: computed in vanilla and never read
						boolean unusedHighMinFlux = this.ship.getMinFluxLevel() > 0.7F;
						if (!(threatEvalAI.getTotalThreat() > 0.0F) && avoidanceVector == null && collisionThreatDir == null) {
							// intentionally kept dead: neither branch has an effect in vanilla
							boolean unusedNoThreat = false;
						} else {
							// intentionally kept dead: neither branch has an effect in vanilla
							boolean unusedThreat = true;
						}

						if (!retreatHoldOpen && (energy >= this.fluxCapacity * 0.2F || criticalHull)
								&& BattleNext_ShipAIHelper.isShieldIdle(this.ship)) {
							if (totalThreat > 0.0F && !threatEvalAI.isAllThreatsActive() || dangerBeforeUnfold || avoidanceVector != null || collisionThreatDir != null) {
								this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
								this.shieldsOn = true;
							}
						} else if (energyFraction < 0.5F) {
							this.timeSinceShieldToggle += amount;
							if ((energy < this.fluxCapacity * 0.2F || criticalHull)
								&& this.timeSinceShieldToggle > 3.0F
								&& (totalThreat == 0.0F || threatEvalAI.isAllThreatsActive())
								&& avoidanceVector == null
								&& collisionThreatDir == null
								&& !dangerBeforeUnfold
								&& shield.isOn()
								&& avoidanceVector == null) {
								this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
								this.timeSinceShieldToggle = 0.0F;
								this.shieldsOn = false;
							}
						} else if (shield.isOn() && (threatEvalAI.isAllThreatsActive() || retreatHoldOpen) && avoidanceVector == null && collisionThreatDir == null && !dangerBeforeUnfold) {
							this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
							this.shieldsOn = false;
						}
					}
				}
			}
		}
	}

	public void unused() {

	}

	@Override
	public boolean hasFiringSolution() {
		return this.eval.hasSolution();
	}

	@Override
	public BattleNext_FiringSolutionEval getEval() {
		return this.eval;
	}
}
