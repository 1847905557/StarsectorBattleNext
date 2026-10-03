package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.WeaponFireControlHelper.
 * Evaluates which broadside heading lets the most weapon groups bear on the target.
 */
public class BattleNext_WeaponFireControlHelper {
	private boolean onTargetHeading = false;
	private boolean needsLeftTurn = false;
	private boolean needsRightTurn = false;
	private float unused1 = 0.0F;
	private float unused2 = 0.0F;
	private float unused3 = 0.0F;
	private ShipAPI ship;
	private ShipAPI targetShip;
	private float idealAngleOffset = 0.0F;
	private boolean active = false;

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void init(ShipAPI ship, ShipAPI targetShip, BattleNext_ManeuverAPI maneuverAPI) {
		this.updateTargeting(ship, targetShip, maneuverAPI.getAttackAI().getAttackRange());
	}

	public void updateTargeting(ShipAPI ship, ShipAPI targetShip, float attackRange) {
		this.ship = ship;
		this.targetShip = targetShip;
		if (targetShip != null || this.active) {
			float minTargetAngle = 30.0F;
			if (targetShip != null) {
				minTargetAngle = Math.max(minTargetAngle, targetShip.getCollisionRadius() * 0.5F);
			}

			this.idealAngleOffset = this.computeIdealFiringAngle(30.0F, attackRange);
			if (this.idealAngleOffset > 180.0F) {
				this.idealAngleOffset = -1.0F * (360.0F - this.idealAngleOffset);
			}

			// intentionally kept dead: vanilla assigns 15.0F first, then overwrites it
			float turnThreshold = 15.0F;
			turnThreshold = 5.0F;
			if (!ship.isFrigate()) {
				turnThreshold = 11.0F;
			}

			this.onTargetHeading = Math.abs(this.idealAngleOffset) <= turnThreshold;
			this.needsLeftTurn = this.idealAngleOffset > turnThreshold;
			this.needsRightTurn = this.idealAngleOffset < -turnThreshold;
			if (this.onTargetHeading) {
				this.needsLeftTurn = this.needsRightTurn = false;
			}
		}
	}

	public boolean isOnTargetHeading() {
		return this.onTargetHeading;
	}

	public boolean needsLeftTurn() {
		return this.needsLeftTurn;
	}

	public boolean needsRightTurn() {
		return this.needsRightTurn;
	}

	public float getAbsAngleOffset() {
		return Math.abs(this.idealAngleOffset);
	}

	public float computeIdealFiringAngle(float radius, float attackRange) {
		float attackRangeScaled = attackRange;
		boolean preferLeft = (float) Math.random() > 0.5F;
		if (this.ship.getAIFlags().hasFlag(AIFlags.PREFER_LEFT_BROADSIDE)) {
			preferLeft = true;
		} else if (this.ship.getAIFlags().hasFlag(AIFlags.PREFER_RIGHT_BROADSIDE)) {
			preferLeft = false;
		} else {
			float preferenceDuration = 30.0F + 30.0F * (float) Math.random();
			if (preferLeft) {
				this.ship.getAIFlags().setFlag(AIFlags.PREFER_LEFT_BROADSIDE, preferenceDuration);
			} else {
				this.ship.getAIFlags().setFlag(AIFlags.PREFER_RIGHT_BROADSIDE, preferenceDuration);
			}
		}

		float bestFrontScore = 0.0F;
		float bestFrontAngle = this.ship.getFacing();
		float bestFrontDiff = Float.MAX_VALUE;
		float bestRearScore = 0.0F;
		float bestRearAngle = this.ship.getFacing();
		float bestRearDiff = Float.MAX_VALUE;
		float currentHeadingScore = 0.0F;
		boolean firstStep = true;

		for (float stepAngle = this.ship.getFacing(); stepAngle <= this.ship.getFacing() + 360.0F; stepAngle += 10.0F) {
			float normalizedAngle = BattleNext_ShipAIHelper.normalizeAngle(stepAngle);
			float relativeAngle = BattleNext_ShipAIHelper.normalizeAngle(stepAngle - this.ship.getFacing());
			boolean frontHalf = relativeAngle < 180.0F;
			if (relativeAngle == 0.0F && !firstStep) {
				frontHalf = false;
			}

			firstStep = false;
			boolean awayFromHeading = relativeAngle > 100.0F && relativeAngle < 260.0F;
			if (this.ship.getShield() != null && isFrontShield(this.ship)) {
				float usableArc = Math.max(0.0F, this.ship.getShield().getArc() - 20.0F);
				awayFromHeading = relativeAngle > usableArc / 2.0F && relativeAngle < 360.0F - usableArc / 2.0F;
			}

			if (relativeAngle == 0.0F) {
				awayFromHeading = false;
			}

			if (!awayFromHeading) {
				Vector2f samplePoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(normalizedAngle);
				samplePoint.scale(Math.max(attackRangeScaled * 0.8F, attackRangeScaled - this.ship.getCollisionRadius() * 0.75F));
				Vector2f.add(samplePoint, this.ship.getLocation(), samplePoint);
				float score = this.evaluateFiringScore(samplePoint, radius);
				boolean onCurrentHeading = Misc.getAngleDiff(stepAngle, this.ship.getFacing()) < 1.0F;
				if (onCurrentHeading) {
					currentHeadingScore = Math.max(score, currentHeadingScore);
				}

				if (frontHalf) {
					if (score >= bestFrontScore) {
						float angleDiff = BattleNext_ShipAIHelper.getAngleDiff(normalizedAngle, this.ship.getFacing());
						if (score > bestFrontScore || angleDiff < bestFrontDiff) {
							bestFrontAngle = normalizedAngle;
							bestFrontScore = score;
							bestFrontDiff = angleDiff;
						}
					}
				} else if (score >= bestRearScore) {
					float angleDiff = BattleNext_ShipAIHelper.getAngleDiff(normalizedAngle, this.ship.getFacing());
					if (score > bestRearScore || angleDiff < bestRearDiff) {
						bestRearAngle = normalizedAngle;
						bestRearScore = score;
						bestRearDiff = angleDiff;
					}
				}
			}
		}

		// intentionally kept dead: vanilla reads the owner and discards the result
		this.ship.getOwner();
		if (currentHeadingScore >= bestFrontScore - 0.5F && currentHeadingScore >= bestRearScore - 0.5F) {
			return 0.0F;
		} else if (bestFrontScore <= 0.0F && bestRearScore <= 0.0F) {
			return 0.0F;
		} else if (bestFrontScore > bestRearScore) {
			return BattleNext_ShipAIHelper.normalizeAngle(bestFrontAngle - this.ship.getFacing());
		} else if (bestFrontScore < bestRearScore) {
			return BattleNext_ShipAIHelper.normalizeAngle(bestRearAngle - this.ship.getFacing());
		} else if (preferLeft && bestFrontScore > 0.0F) {
			return BattleNext_ShipAIHelper.normalizeAngle(bestFrontAngle - this.ship.getFacing());
		} else {
			return !preferLeft && bestRearScore > 0.0F ? BattleNext_ShipAIHelper.normalizeAngle(bestRearAngle - this.ship.getFacing()) : 0.0F;
		}
	}

	private float evaluateFiringScore(Vector2f point, float radius) {
		float score = 0.0F;
		boolean targetVulnerable = this.targetShip != null
			&& this.targetShip.getFluxTracker().isOverloadedOrVenting()
			&& (this.targetShip.getFluxTracker().getOverloadTimeRemaining() > 3.0F || this.targetShip.getFluxTracker().getTimeToVent() > 4.0F);
		targetVulnerable |= this.targetShip != null && this.targetShip.getShield() == null && this.targetShip.getPhaseCloak() == null;

		for (int groupIndex = 0; groupIndex < 7; groupIndex++) {
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
			if (group != null) {
				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if (!weapon.isDisabled() && weapon.getAmmo() > 0 && !(weapon.getCooldownRemaining() > 5.0F)) {
						Vector2f slotPosition = weapon.getSlot().computePosition(this.ship);
						float slotDistance = BattleNext_ShipAIHelper.getDistance(slotPosition, point);
						boolean poorOffAxisMissile = weapon.getType() == WeaponType.MISSILE
							&& Math.abs(weapon.getSlot().getAngle()) > 5.0F
							&& weapon.hasAIHint(AIHints.GUIDED_POOR);
						boolean doNotAim = weapon.hasAIHint(AIHints.DO_NOT_AIM) || poorOffAxisMissile;
						if (weapon.getType() != WeaponType.MISSILE || !doNotAim) {
							float angularSpan = 2.0F * radius / ((float) (Math.PI * 2) * slotDistance) * 360.0F;
							if (weapon.getSlot().isHardpoint()) {
								angularSpan += 20.0F;
							}

							// intentionally kept dead: vanilla recomputes the same slot distance
							float checkDistance = BattleNext_ShipAIHelper.getDistance(slotPosition, point);
							float rangeBonus = this.ship.getCollisionRadius();
							float slotArcAngle = weapon.getSlot().computeMidArcAngle(this.ship);
							if (BattleNext_ShipAIHelper.getAngleDiff(slotArcAngle, this.ship.getFacing()) < 15.0F) {
								rangeBonus += 300.0F;
							}

							if (doNotAim
								|| BattleNext_ShipAIHelper.isInArc(slotArcAngle, weapon.getSlot().getArc() + angularSpan, slotPosition, point)
									&& checkDistance <= weapon.getRange() + radius * 0.75F + rangeBonus) {
								// vanilla looks the weight up in a size table; the case labels are
								// aligned here by WeaponSize name
								float weight = 1.0F;
								switch (weapon.getSize()) {
									case SMALL:
										weight = 1.0F;
										break;
									case MEDIUM:
										weight = 3.0F;
										break;
									case LARGE:
										weight = 7.0F;
								}

								if (weapon.hasAIHint(AIHints.PD)) {
									if (!weapon.getSlot().isHardpoint()) {
										weight *= 0.0F;
									} else {
										weight *= 0.5F;
									}
								}

								if (weapon.hasAIHint(AIHints.PD_ONLY)) {
									weight *= 0.0F;
								}

								if (weapon.getSpec().getDerivedStats().getEmpPerSecond() > weight * 10.0F && targetVulnerable) {
									weight *= 1.5F;
								}

								if (weapon.getSlot().isHardpoint()) {
									weight *= 1.25F;
								}

								score += weight;
							}
						}
					}
				}
			}
		}

		return score;
	}

	// approximation: engine ShieldAPI.isFront is not present in the mod-visible
	// API; the shield type is used instead
	private static boolean isFrontShield(ShipAPI ship) {
		return ship.getShield() != null && ship.getShield().getType() == ShieldAPI.ShieldType.FRONT;
	}
}
