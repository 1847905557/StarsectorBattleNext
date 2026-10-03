package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.GenericSystemAI.
 * Periodically decides whether to activate the ship system while a target is
 * visible and the ship is misaligned, out of range, or about to collide.
 */
public class BattleNext_GenericSystemAI implements BattleNext_SystemAI {
	private ShipwideAIFlags flags;
	private ShipAPI ship;
	private final BattleNext_ThreatEvalAI threatEval;
	// approximation: engine IntervalTracker replaced by the API IntervalUtil, which exposes the same methods
	private IntervalUtil decisionInterval = new IntervalUtil(0.25F, 0.5F);
	private ShipSystemAPI system;
	private final BattleNext_AttackAIModule attackModule;
	private final BattleNext_FlockingAI flockingAI;

	public BattleNext_GenericSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval, BattleNext_AttackAIModule attackModule, BattleNext_FlockingAI flockingAI) {
		this.flags = flags;
		this.ship = ship;
		this.threatEval = threatEval;
		this.attackModule = attackModule;
		this.flockingAI = flockingAI;
		this.system = ship.getSystem();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		this.decisionInterval.advance(amount);
		if (this.decisionInterval.intervalElapsed()) {
			if (this.system.isActive()) {
				this.flags.setFlag(AIFlags.DO_NOT_VENT);
				return;
			}

			if (this.system.getCooldownRemaining() > 0.0F) {
				return;
			}

			if (this.system.isOutOfAmmo()) {
				return;
			}

			if (this.system.isActive()) {
				return;
			}

			if (this.ship.getFluxTracker().isOverloadedOrVenting()) {
				return;
			}

			FogOfWarAPI fogOfWar = BattleNext_ShipAIHelper.getEngine().getFogOfWar(this.ship.getOriginalOwner());
			if (target != null && fogOfWar != null && !fogOfWar.isVisible(target)) {
				target = null;
			}

			if (target == null) {
				return;
			}

			float fluxLevel = this.ship.getFluxLevel();
			if (fluxLevel > 0.9F) {
				return;
			}

			boolean hasMatchingWeapons = false;
			boolean weaponsFiring = false;
			float totalWeight = 0.0F;
			float firingWeight = 0.0F;
			if (this.system.getSpecAPI().getWeaponTypes() == null) {
				hasMatchingWeapons = true;
			} else {
				for (WeaponAPI weapon : this.ship.getAllWeapons()) {
					if (this.system.getSpecAPI().getWeaponTypes().contains(weapon.getType()) && weapon.getCooldownRemaining() <= 1.0F) {
						hasMatchingWeapons = true;
						float weight = 1.0F;
						if (weapon.getSize() == WeaponSize.LARGE) {
							weight = 4.0F;
						} else if (weapon.getSize() == WeaponSize.MEDIUM) {
							weight = 2.0F;
						}

						if (weapon.hasAIHint(AIHints.STRIKE)) {
							weight *= 2.0F;
						}

						totalWeight += weight;
						if (weapon.isFiring() && (weapon.getCooldown() < 2.0F || weapon.getCooldownRemaining() <= 0.0F)) {
							weaponsFiring = true;
							firingWeight += weight;
						}
					}
				}
			}

			if (hasMatchingWeapons) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), target.getLocation());
				float combinedRadius = (this.ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
				float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, target);
				boolean closingIn = closingSpeed > 5.0F;
				boolean inAttackRange = this.attackModule.getAttackRange() >= distance - combinedRadius;
				boolean inHalfAttackRange = this.attackModule.getAttackRange() * 0.5F >= distance - combinedRadius;
				if (inAttackRange && closingIn || inHalfAttackRange || firingWeight > 0.0F && firingWeight >= totalWeight * 0.5F) {
					this.activateSystem();
					return;
				}
			}

			if (!this.isEngineDisabled()) {
				if (this.system.getFluxPerUse() > 0.0F) {
					float fluxFractionPerUse = this.system.getFluxPerUse() / this.ship.getMaxFlux();
					if (this.ship.getFluxLevel() + fluxFractionPerUse > 0.75F) {
						return;
					}
				}

				float desiredFacing = this.flockingAI.getDesiredFacing();
				if (desiredFacing != Float.MAX_VALUE) {
					float angularVelocity = this.ship.getAngularVelocity();
					float maxTurnRate = this.ship.getMaxTurnRate();
					float turnAcceleration = this.ship.getTurnAcceleration();
					float facingDelta = Math.abs(desiredFacing - this.ship.getFacing());
					float turnSign = Math.signum(desiredFacing - this.ship.getFacing());
					float timeToFace = facingDelta / maxTurnRate;
					if (turnSign != Math.signum(this.ship.getAngularVelocity())) {
						timeToFace += (Math.abs(angularVelocity) + maxTurnRate / 2.0F) / turnAcceleration;
					}

					if (facingDelta > 90.0F || timeToFace > 2.0F || timeToFace > this.system.getCooldown() * 0.5F) {
						this.activateSystem();
						return;
					}
				}

				// vanilla stores a heading in FlockingAI.avoidanceRadius despite the field name
				float avoidanceHeading = this.flockingAI.getAvoidanceRadius();
				if (avoidanceHeading != Float.MAX_VALUE) {
					Vector2f desiredVelocity = BattleNext_ShipAIHelper.getUnitVectorAtAngle(avoidanceHeading);
					desiredVelocity.scale(this.flockingAI.getCurrentMaxSpeed());
					float averageAccel = (this.ship.getAcceleration() + this.ship.getDeceleration()) / 2.0F;
					float velocityGap = BattleNext_ShipAIHelper.getDistance(desiredVelocity, this.ship.getVelocity());
					float angleDiff;
					if (this.ship.getVelocity().length() > 5.0F) {
						angleDiff = BattleNext_ShipAIHelper.getAngleDiff(desiredVelocity, this.ship.getVelocity());
					} else {
						angleDiff = BattleNext_ShipAIHelper.getAngleDiff(desiredVelocity, BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.ship.getFacing()));
					}

					float timeToStop = velocityGap / averageAccel;
					if (angleDiff > 90.0F || timeToStop > 2.0F || timeToStop > this.system.getCooldown() * 0.5F) {
						this.activateSystem();
						return;
					}
				}
			}
		}
	}

	public void activateSystem() {
		this.ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
	}

	public ShipSystemAPI getSystem() {
		return this.system;
	}

	private boolean isEngineDisabled() {
		// approximation: ShipAPI has no isEngineDisabled(); the check lives on the engine controller
		return this.ship.getEngineController().isDisabled();
	}
}
