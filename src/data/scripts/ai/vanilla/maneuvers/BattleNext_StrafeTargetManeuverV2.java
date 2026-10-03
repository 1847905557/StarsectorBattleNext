package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.*;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.attack.BattleNext_WeaponGroupEvaluation;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionCheckSegment;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionDangerEntry;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionTimeRange;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.StrafeTargetManeuverV2.
 * Primary brawling maneuver: orbits the target, strafes on broadside, decides
 * when to back off and repositions around collision arcs.
 * Public surface computeFacing(boolean), getDesiredFacing(), getTargetFacing()
 * and getTarget() is kept because BasicShipAI and ManeuverManager depend on it;
 * computeFacing keeps its vanilla side effect of running the facing execution.
 * Profiler hooks dropped.
 */
public class BattleNext_StrafeTargetManeuverV2 implements BattleNext_Maneuver {
	private ShipAPI ship;
	private ShipAPI targetShip;
	private float unusedRange;
	private float attackRange;
	private BattleNext_FlockingAI flockingAI;
	private BattleNext_ManeuverAPI maneuverAPI;
	private float collisionDistance;
	private float backOffDirection = 0.0F;
	private boolean unusedFinished = false;
	private boolean backingOff = false;
	private float strafeAngle = Float.MAX_VALUE;
	private boolean backOffLeft = false;
	private boolean backOffRight = false;
	private IntervalUtil decisionTracker = new IntervalUtil(0.5F, 1.0F);
	private IntervalUtil targetUpdateTracker = new IntervalUtil(1.0F, 2.0F);
	private BattleNext_WeaponFireControlHelper weaponFireControlHelper = new BattleNext_WeaponFireControlHelper();
	private boolean selfIsNonCombat = false;
	private boolean targetIsNonCombat = false;
	private float targetSide = 0.0F;
	private float strafeDirection = 0.0F;
	private boolean holdTarget;
	private boolean blockedBehind = false;
	private float desiredRange;
	private boolean aggressivePush = false;
	private float emergencyBackOffTimer = 0.0F;
	private float lastFrameDt = 0.0F;
	private boolean enabled = true;
	private boolean isReckless;
	private boolean isAggressive;
	private boolean isSteady;
	private boolean isCautious;
	private boolean isTimid;
	private boolean directionFlipped = false;
	private boolean allowBackOff = true;
	private float alternateTimer = 0.0F;
	private boolean alternateSide = true;
	private float currentFacing = Float.MAX_VALUE;
	private float desiredFacing = Float.MAX_VALUE;
	private boolean shouldBackOff = false;
	private boolean targetInArc = true;
	private Vector2f moveDest = null;

	public BattleNext_StrafeTargetManeuverV2(ShipAPI ship, ShipAPI targetShip, boolean holdTarget, float unusedRange, float desiredRange, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI, boolean enabled) {
		this.enabled = enabled;
		String personality = BattleNext_ShipAIHelper.getPersonality(ship);
		this.isReckless = "reckless".equals(personality);
		this.isAggressive = "aggressive".equals(personality);
		this.isSteady = "steady".equals(personality);
		this.isCautious = "cautious".equals(personality);
		this.isTimid = "timid".equals(personality);
		if (BattleNext_ShipAIHelper.isShipOrderedToAttack(ship)) {
			this.isReckless = true;
			this.isAggressive = this.isSteady = this.isCautious = this.isTimid = false;
		}

		this.ship = ship;
		this.targetShip = targetShip;
		this.holdTarget = holdTarget;
		this.targetIsNonCombat = targetShip != null && isNonCombatVariant(targetShip);
		this.unusedRange = unusedRange;
		// vanilla oddity: collision distance multiplies the target radius by the
		// distance instead of adding it
		this.collisionDistance = ship.getCollisionRadius()
			+ targetShip.getCollisionRadius() * BattleNext_ShipAIHelper.getDistanceToEntity(ship.getLocation(), targetShip);
		this.desiredRange = desiredRange;
		this.attackRange = this.desiredRange + this.collisionDistance;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.backOffDirection = BattleNext_ShipAIHelper.getAngleToEntity(ship, targetShip, holdTarget);
		this.weaponFireControlHelper.init(ship, targetShip, maneuverAPI);
		this.selfIsNonCombat = ship.isNonCombat(true);
		this.initialize();
	}

	public BattleNext_StrafeTargetManeuverV2(ShipAPI ship, ShipAPI targetShip, boolean holdTarget, float unusedRange, float desiredRange, float backOffDirection, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI, boolean enabled) {
		this.enabled = enabled;
		String personality = BattleNext_ShipAIHelper.getPersonality(ship);
		this.isReckless = "reckless".equals(personality);
		this.isAggressive = "aggressive".equals(personality);
		this.isSteady = "steady".equals(personality);
		this.isCautious = "cautious".equals(personality);
		this.isTimid = "timid".equals(personality);
		if (BattleNext_ShipAIHelper.isShipOrderedToAttack(ship)) {
			this.isReckless = true;
			this.isAggressive = this.isSteady = this.isCautious = this.isTimid = false;
		}

		this.ship = ship;
		this.targetShip = targetShip;
		this.holdTarget = holdTarget;
		this.targetIsNonCombat = targetShip != null && isNonCombatVariant(targetShip);
		this.unusedRange = unusedRange;
		// vanilla oddity: collision distance multiplies the target radius by the
		// distance instead of adding it
		this.collisionDistance = ship.getCollisionRadius()
			+ targetShip.getCollisionRadius() * BattleNext_ShipAIHelper.getDistanceToEntity(ship.getLocation(), targetShip);
		this.desiredRange = desiredRange;
		this.attackRange = this.desiredRange + this.collisionDistance;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		if (backOffDirection != 0.0F) {
			this.backOffDirection = backOffDirection;
		} else {
			// intentionally kept dead: vanilla assigns the parameter but never reads
			// it afterwards
			backOffDirection = BattleNext_ShipAIHelper.getAngleToEntity(ship, targetShip, holdTarget);
		}

		this.weaponFireControlHelper.init(ship, targetShip, maneuverAPI);
		this.selfIsNonCombat = ship.isNonCombat(true);
		this.initialize();
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	private void initialize() {
		this.aggressivePush = (float) Math.random() > 0.5F;
		this.updateTargeting();
		this.strafeAngle = this.computeStrafeAngle();
		if (this.targetShip != null) {
			float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
			int threatsBehind = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(this.ship, this.ship.getFacing() + 180.0F, 150.0F, Math.max(1500.0F, distance + 500.0F));
			this.blockedBehind = threatsBehind > 0;
		} else {
			this.blockedBehind = false;
		}
	}

	private void scanForTargets() {
		this.targetSide = 0.0F;
		if (!this.holdTarget) {
			float gridSpan = 4000.0F;
			// approximation: the engine spatial ship grid returns every ship in cells
			// overlapping a 4000 x 4000 query; the public API exposes the ship list,
			// so an exact 4000 x 4000 box is used as the candidate boundary.
			List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
			float bestSide = 0.0F;
			float bestScore = 0.0F;

			for (ShipAPI candidate : ships) {
				if (Math.abs(candidate.getLocation().x - this.ship.getLocation().x) > gridSpan / 2.0F
					|| Math.abs(candidate.getLocation().y - this.ship.getLocation().y) > gridSpan / 2.0F) {
					continue;
				}

				if (candidate != this.ship
					&& !candidate.isHulk()
					&& !candidate.isFighter()
					&& candidate.getOwner() == this.ship.getOwner()
					&& candidate.getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
					float targetVentTime = 0.0F;
					// vanilla oddity: the vent check reads the maneuver target instead of
					// the candidate
					if (this.targetShip.getFluxTracker().isVenting()) {
						targetVentTime = this.targetShip.getFluxTracker().getTimeToVent();
					} else if (this.targetShip.getFluxTracker().isOverloaded()) {
						targetVentTime = this.targetShip.getFluxTracker().getOverloadTimeRemaining();
					}

					if (!(targetVentTime > 3.0F)) {
						Vector2f origin = new Vector2f(this.ship.getLocation());
						Vector2f leftRay = Misc.getUnitVectorAtDegreeAngle(this.ship.getFacing() + 90.0F);
						leftRay.scale(10000.0F);
						Vector2f.add(leftRay, origin, leftRay);
						Vector2f rightRay = Misc.getUnitVectorAtDegreeAngle(this.ship.getFacing() - 90.0F);
						rightRay.scale(10000.0F);
						Vector2f.add(rightRay, origin, rightRay);
						float broadsideFacing = candidate.getFacing();
						ShipAPI broadsideTarget = null;
						if (candidate.getAI() instanceof ShipAIPlugin candidateAI) {
							if (candidateAI.getAIFlags() != null
								&& candidateAI.getAIFlags().hasFlag(AIFlags.MANEUVER_TARGET)
								&& candidateAI.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET) instanceof ShipAPI) {
								broadsideTarget = (ShipAPI) candidateAI.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
							}
						}

						float maxWeaponRange = 0.0F;
						if (broadsideTarget == null && candidate == BattleNext_ShipAIHelper.getEngine().getPlayerShip()) {
							maxWeaponRange = this.computeMaxWeaponRange(candidate);
							BattleNext_WeaponFireControlHelper playerFireControl = new BattleNext_WeaponFireControlHelper();
							candidate.getAIFlags().setFlag(AIFlags.PREFER_LEFT_BROADSIDE, 1.0F);
							playerFireControl.setActive(true);
							playerFireControl.updateTargeting(candidate, null, candidate.getCollisionRadius() + 500.0F);
							boolean needsLeft = playerFireControl.needsLeftTurn();
							float leftOffset = playerFireControl.getAbsAngleOffset();
							candidate.getAIFlags().setFlag(AIFlags.PREFER_RIGHT_BROADSIDE, 1.0F);
							playerFireControl.updateTargeting(candidate, null, candidate.getCollisionRadius() + 500.0F);
							boolean needsRight = playerFireControl.needsRightTurn();
							float rightOffset = playerFireControl.getAbsAngleOffset();
							if (needsLeft || needsRight) {
								if (needsLeft) {
									broadsideFacing = candidate.getFacing() + leftOffset;
									broadsideTarget = BattleNext_ShipAIHelper.getNearestEnemyInArcRange(candidate, candidate.getLocation(), broadsideFacing, 60.0F, HullSize.FRIGATE, maxWeaponRange + 500.0F);
								}

								if (broadsideTarget == null && needsRight) {
									broadsideFacing = candidate.getFacing() - rightOffset;
									broadsideTarget = BattleNext_ShipAIHelper.getNearestEnemyInArcRange(candidate, candidate.getLocation(), broadsideFacing, 60.0F, HullSize.FRIGATE, maxWeaponRange + 500.0F);
								}
							}

							if (broadsideTarget == null) {
								broadsideTarget = BattleNext_ShipAIHelper.getNearestEnemyInArcRange(candidate, candidate.getLocation(), broadsideFacing, 60.0F, HullSize.FRIGATE, maxWeaponRange + 500.0F);
							}
						}

						if (broadsideTarget != null) {
							broadsideFacing = Misc.getAngleInDegrees(candidate.getLocation(), broadsideTarget.getLocation());
						}

						if (broadsideTarget != null) {
							Vector2f sternPoint = Misc.getUnitVectorAtDegreeAngle(broadsideFacing + 180.0F);
							sternPoint.scale(this.ship.getCollisionRadius() + candidate.getCollisionRadius() + 50.0F);
							Vector2f.add(sternPoint, candidate.getLocation(), sternPoint);
							Vector2f targetRay = Misc.getUnitVectorAtDegreeAngle(broadsideFacing);
							targetRay.scale(10000.0F);
							Vector2f.add(targetRay, origin, targetRay);
							Vector2f leftHit = null;
							Vector2f rightHit = null;
							leftHit = segmentIntersection(origin, leftRay, sternPoint, targetRay);
							if (leftHit == null) {
								rightHit = segmentIntersection(origin, rightRay, sternPoint, targetRay);
							}

							if (leftHit != null || rightHit != null) {
								float blockRange = candidate.getCollisionRadius() + this.ship.getCollisionRadius() + Math.min(150.0F, this.ship.getMaxSpeed() * 0.33F);
								float selfDistance = 10000.0F;
								if (leftHit != null) {
									selfDistance = Misc.getDistance(leftHit, this.ship.getLocation());
								} else {
									selfDistance = Misc.getDistance(rightHit, this.ship.getLocation());
								}

								if (!(selfDistance > blockRange)) {
									if (maxWeaponRange <= 0.0F) {
										maxWeaponRange = this.computeMaxWeaponRange(candidate);
									}

									float targetDistance = 10000.0F;
									if (leftHit != null) {
										targetDistance = Misc.getDistance(leftHit, candidate.getLocation());
									} else {
										targetDistance = Misc.getDistance(rightHit, candidate.getLocation());
									}

									if (!(targetDistance > maxWeaponRange)) {
										float score = 10000.0F * candidate.getHullSize().ordinal() - targetDistance;
										if (score > bestScore) {
											bestScore = score;
											if (leftHit != null) {
												bestSide = 1.0F;
											} else {
												bestSide = -1.0F;
											}
										}
									}
								}
							}
						}
					}
				}
			}

			this.targetSide = bestSide;
		}
	}

	private float computeMaxWeaponRange(ShipAPI ship) {
		float maxRange = 0.0F;

		for (WeaponAPI weapon : ship.getAllWeapons()) {
			if (!weapon.isDecorative() && !weapon.hasAIHint(AIHints.DO_NOT_AIM) && !weapon.hasAIHint(AIHints.GUIDED_POOR)) {
				float range = weapon.getRange();
				if (range > maxRange) {
					maxRange = range;
				}
			}
		}

		return maxRange + (this.ship.getCollisionRadius() + ship.getCollisionRadius());
	}

	private void updateTargeting() {
		this.scanForTargets();
		boolean sideTracked = false;
		if (this.ship.getAIFlags().hasFlag(AIFlags.MOVEMENT_DEST_WHILE_SIDETRACKED)) {
			Vector2f sidetrackedDest = (Vector2f) this.ship.getAIFlags().getCustom(AIFlags.MOVEMENT_DEST_WHILE_SIDETRACKED);
			if (sidetrackedDest != null && Misc.getDistance(this.ship.getLocation(), sidetrackedDest) > 1000.0F) {
				// vanilla calls getBestManeuverTarget(ship, EITHER_SIDE, false); the
				// one-argument overload delegates to exactly that call
				BattleNext_BestTargetEval eval = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
				if (eval == null && this.targetShip != null) {
					eval = new BattleNext_BestTargetEval();
					eval.setAimPoint(this.targetShip.getLocation());
					eval.setAlternateTarget(this.targetShip);
					eval.setTarget(this.targetShip);
				}

				if (eval != null && eval.getAlternateTarget() != null && eval.getAimPoint() != null) {
					float maneuverAngle = BattleNext_TaskMoveManeuver.computeManeuverAngle(this.ship, this.targetShip, eval.getAlternateTarget(), eval.getAimPoint(), sidetrackedDest, 0.0F);
					float leftFacing = this.ship.getFacing() + 90.0F;
					float rightFacing = this.ship.getFacing() - 90.0F;
					float leftDiff = Misc.getAngleDiff(leftFacing, maneuverAngle);
					float rightDiff = Misc.getAngleDiff(rightFacing, maneuverAngle);
					if (Misc.getAngleDiff(leftDiff, rightDiff) > 15.0F) {
						if (leftDiff > rightDiff) {
							this.strafeDirection = 1.0F;
						} else {
							this.strafeDirection = -1.0F;
						}

						sideTracked = true;
					}
				}
			}
		}

		if (!sideTracked) {
			Vector2f relativeVelocity = Vector2f.sub(this.targetShip.getVelocity(), this.ship.getVelocity(), new Vector2f());
			float velocityAngle = BattleNext_ShipAIHelper.getAngleFast(relativeVelocity);
			float leftDiff = BattleNext_ShipAIHelper.getAngleDiff(velocityAngle, this.ship.getFacing() + 90.0F);
			float rightDiff = BattleNext_ShipAIHelper.getAngleDiff(velocityAngle, this.ship.getFacing() - 90.0F);
			float speed = relativeVelocity.length();
			if (!(speed > 100.0F) && (!(speed > 10.0F) || this.strafeDirection != 0.0F)) {
				if (!this.directionFlipped && this.strafeDirection != 0.0F) {
					this.directionFlipped = true;
					this.strafeDirection = -this.strafeDirection;
				} else {
					this.strafeDirection = Math.signum((float) Math.random() - 0.5F);
					if (this.strafeDirection == 0.0F) {
						this.strafeDirection = -1.0F;
					}
				}
			} else if (leftDiff > rightDiff) {
				this.strafeDirection = 1.0F;
			} else {
				this.strafeDirection = -1.0F;
			}
		}

		this.allowBackOff = (float) Math.random() > 0.75F;
	}

	@Override
	public void advance(float amount) {
		this.lastFrameDt = amount;
		this.targetUpdateTracker.advance(amount * 5.0F);
		if (this.targetUpdateTracker.intervalElapsed()) {
			this.updateTargeting();
		}

		this.decisionTracker.advance(amount);
		if (this.decisionTracker.intervalElapsed() && !this.unusedFinished) {
			this.advanceDecisionCycle();
		}

		this.alternateTimer += amount;
		if (this.alternateTimer > 5.0F) {
			this.alternateSide = !this.alternateSide;
			this.alternateTimer = 0.0F;
		}

		// approximation: ShipAPI cannot report the un-phasing transition; the ship is
		// treated as never mid-transition
		boolean targetUntouchable = this.ship.isPhased() && !isUnphasing(this.ship);
		if ((BattleNext_ShipAIHelper.isShipAboutToBeHit(this.ship, this.targetShip) || targetUntouchable) && (this.targetShip.getHitpoints() > 500.0F || targetUntouchable)) {
			this.flockingAI.addCollisionObject(this.targetShip);
		}

		this.currentFacing = this.computeMoveDest();
		this.desiredFacing = this.currentFacing;
		if (this.targetShip != null && this.ship.getShield() != null && isFrontShield(this.ship)) {
			this.currentFacing = this.computeThreatFacing();
		} else if (this.targetShip != null && this.ship.getShield() != null) {
			this.computeThreatFacing();
		} else {
			this.computeThreatFacing();
		}

		float fluxThreshold = 0.95F;
		BattleNext_ThreatData maxThreat = this.maneuverAPI.getThreatEvaluator().getMaxThreatData();
		if (maxThreat != null && maxThreat.getShip().getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
			fluxThreshold -= 0.09F * (maxThreat.getShip().getHullSize().ordinal() - this.ship.getHullSize().ordinal() + 1.0F);
		}

		// intentionally kept dead: vanilla overwrites the scaled threshold with 1.0F
		fluxThreshold = 1.0F;
		this.shouldBackOff = this.ship.getFluxTracker().getOverloadTimeRemaining() > 2.0F
			|| this.ship.getFluxTracker().isVenting() && this.ship.getFluxTracker().getTimeToVent() > 2.0F
			|| this.ship.getFluxLevel() > fluxThreshold;
		boolean lowHitpoints = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
		this.shouldBackOff = this.shouldBackOff | (lowHitpoints && (!(this.ship.getFluxLevel() < 0.8F) || !isShieldOn(this.ship)));
		this.shouldBackOff = this.shouldBackOff
			| (
				maxThreat != null
					&& !maxThreat.getShip().isFighter()
					&& this.ship.getShield() != null
					&& this.ship.getShield().isOff()
					&& !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.SAFE_FROM_DANGER_TIME)
			);
		this.shouldBackOff = this.shouldBackOff | (this.ship.getShield() == null && this.ship.getFluxLevel() > 0.95F);
		this.shouldBackOff = this.shouldBackOff
			& (
				this.maneuverAPI.getEval() != null
					&& (
						this.maneuverAPI.getEval().hasDetectedIncomingDamage()
							|| this.maneuverAPI.getThreatEvaluator().getTotalThreat() > 0.05F && !this.maneuverAPI.getThreatEvaluator().isAllThreatsValid()
					)
			);
		this.shouldBackOff = this.shouldBackOff & (this.maneuverAPI.getThreatEvaluator().getMaxThreatData() != null);
		if (this.ship.getShield() != null && isFrontShield(this.ship)) {
			this.shouldBackOff = this.shouldBackOff & (this.ship.getArmorGrid().getArmorRating() > 50.0F * this.ship.getHullSize().ordinal());
		}

		this.shouldBackOff = this.shouldBackOff & !this.ship.isPhased();
		this.shouldBackOff = this.shouldBackOff & (!BattleNext_ShipAIHelper.isPhaseShip(this.ship) || !(this.ship.getFluxLevel() < 0.85F));
		if (this.ship.getShield() == null && this.ship.getPhaseCloak() == null) {
			this.shouldBackOff = this.shouldBackOff & this.allowBackOff;
		}

		if (maxThreat != null) {
			ShipAPI threatShip = maxThreat.getShip();
			float radiiSum = threatShip.getCollisionRadius() + this.ship.getCollisionRadius();
			float distance = BattleNext_ShipAIHelper.getDistance(threatShip.getLocation(), this.ship.getLocation());
			if (distance + radiiSum > maxThreat.getThreatRange()) {
				this.shouldBackOff = false;
			}
		}

		if (!this.maneuverAPI.getConfig().turnToFaceWithUndamagedArmor) {
			this.shouldBackOff = false;
		}

		if (this.shouldBackOff) {
			boolean unusedAlwaysFalse = true;
			unusedAlwaysFalse = false;
			if (!unusedAlwaysFalse) {
				float bestArmorAngle = this.computeBestArmorAngle(this.ship.getFacing(), isFrontShield(this.ship) ? 150.0F : 210.0F);
				float armorOffset = bestArmorAngle - this.ship.getFacing();
				this.currentFacing -= armorOffset;
			}
		}
	}

	// original method: advance, decision-interval phase
	private void advanceDecisionCycle() {
		if (this.maneuverAPI.keepTarget() && this.targetShip != null) {
			this.backOffDirection = BattleNext_ShipAIHelper.getAngleToEntity(this.ship, this.targetShip, this.holdTarget);
			this.updateTargeting();
		} else {
			this.ship.getAIFlags().setFlag(AIFlags.MANEUVER_TARGET, 1.0F, this.getTarget());
			BattleNext_BestTargetEval eval = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
			this.holdTarget = false;
			if (eval != null) {
				this.backOffDirection = eval.getRange();
				this.targetShip = eval.getTarget();
				this.holdTarget = eval.isBadTarget();
				this.targetIsNonCombat = this.targetShip != null && isNonCombatVariant(this.targetShip);
			} else {
				this.backOffDirection = 0.0F;
			}

			if (this.targetShip != null) {
				this.updateTargeting();
			}
		}

		if (this.targetShip != null) {
			// vanilla oddity: collision distance multiplies the target radius by the
			// distance instead of adding it
			this.collisionDistance = this.ship.getCollisionRadius()
				+ this.targetShip.getCollisionRadius() * BattleNext_ShipAIHelper.getDistanceToEntity(this.ship.getLocation(), this.targetShip);
			this.attackRange = this.desiredRange + this.collisionDistance;
		}

		if (this.targetShip != null) {
			float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
			int threatsBehind = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(this.ship, this.ship.getFacing() + 180.0F, 150.0F, Math.max(1500.0F, distance + 500.0F));
			this.blockedBehind = threatsBehind > 0;
		} else {
			this.blockedBehind = false;
		}

		if (this.targetShip != null) {
			this.weaponFireControlHelper.init(this.ship, this.targetShip, this.maneuverAPI);
			this.strafeAngle = this.computeStrafeAngle();
			int threatsFront = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(
				this.ship, this.desiredFacing, 180.0F, this.attackRange + 250.0F * (this.ship.getHullSize().ordinal() + 1)
			);
			int threatsRear = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(
				this.ship, this.desiredFacing + 180.0F, 180.0F, this.attackRange + 250.0F * (this.ship.getHullSize().ordinal() + 1)
			);
			int seriousFront = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRangeExcludingFighters(this.ship, this.desiredFacing, 180.0F, Math.min(this.attackRange, 1000.0F));
			int seriousRear = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRangeExcludingFighters(
				this.ship, this.desiredFacing + 180.0F, 180.0F, Math.min(this.attackRange, 1000.0F)
			);
			this.backingOff = false;
			if (threatsFront - seriousFront + threatsRear - seriousRear > 1 || threatsFront + threatsRear >= 2 || this.ship.getFluxTracker().isOverloadedOrVenting()) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
				if (threatsFront == 1 && threatsRear >= 2) {
					this.backOffLeft = false;
					this.backOffRight = false;
				} else if (distance < this.attackRange * 0.8F || threatsFront >= 2) {
					this.backOffLeft = true;
					this.backOffRight = false;
				} else if (distance > this.attackRange * 1.0F) {
					this.backOffLeft = false;
					this.backOffRight = true;
				} else {
					this.backOffLeft = false;
					this.backOffRight = false;
				}

				this.backingOff = true;
				if (BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship) && !this.ship.getFluxTracker().isOverloadedOrVenting()) {
					this.backOffLeft = false;
					this.backOffRight = true;
					this.backingOff = false;
				}
			}

			if (this.backOffDirection == 0.0F) {
				this.backingOff = false;
				float radius = this.ship.getCollisionRadius();
				int leftThreats = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(this.ship, this.desiredFacing + 90.0F, 179.0F, radius * 2.0F + 1250.0F, false, false);
				int rightThreats = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(this.ship, this.desiredFacing - 90.0F, 179.0F, radius * 2.0F + 1250.0F, false, false);
				if (leftThreats > rightThreats) {
					this.backOffDirection = 1.0F;
				}

				if (rightThreats > leftThreats) {
					this.backOffDirection = -1.0F;
				}
			} else {
				this.backOffDirection = BattleNext_ShipAIHelper.getAngleToEntity(this.ship, this.targetShip, this.holdTarget);
			}
		}
	}

	private float computeThreatFacing() {
		BattleNext_ThreatEvalAI threatEvaluator = this.maneuverAPI.getThreatEvaluator();
		ShipAPI threatShip = null;
		if (threatEvaluator.getMaxThreatData() != null) {
			threatShip = threatEvaluator.getMaxThreatData().getShip();
			float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), threatShip.getLocation());
			float radiiSum = this.ship.getCollisionRadius() + threatShip.getCollisionRadius();
			if (threatEvaluator.getMaxThreatData().getThreatRange() + radiiSum + 100.0F < distance) {
				threatShip = null;
			}
		}

		if (threatShip == null) {
			threatShip = this.targetShip;
		}

		// intentionally kept dead: vanilla overwrites the threat fallback with the
		// maneuver target
		threatShip = this.targetShip;
		BattleNext_ThreatData targetThreat = threatEvaluator.computeThreatData(this.targetShip);
		boolean targetIsThreat = targetThreat.isThreat();
		this.targetInArc = true;
		float shieldArc;
		if (this.ship.getShield() != null) {
			shieldArc = (this.ship.getShield().getActiveArc() - 20.0F) * 0.6F;
			if (this.ship.getShield().getActiveArc() >= 360.0F) {
				shieldArc = 360.0F;
			}
		} else {
			shieldArc = 60.0F;
		}

		if (shieldArc < 0.0F) {
			shieldArc = 0.0F;
		}

		float moveFacing = this.computeMoveDest();
		float bestThreatValue = 0.0F;
		float bestThreatAngle = 0.0F;
		boolean targetThreatFree = false;
		boolean lowHitpoints = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();

		for (BattleNext_ThreatData threatData : threatEvaluator.getAllThreatData()) {
			if (threatData.getShip() == threatShip) {
				targetThreatFree = !threatData.isThreat();
			} else {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), threatShip.getLocation());
				float radiiSum = this.ship.getCollisionRadius() + threatData.getShip().getCollisionRadius();
				if (!(threatData.getThreatRange() + radiiSum + 500.0F < distance)) {
					float threatAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), threatData.getShip().getLocation());
					if (threatData.getThreatValue() > bestThreatValue && !threatData.isThreat()) {
						if (!lowHitpoints) {
							// approximation: engine ArmorEvaluator.getArmorInArc is not in the
							// API; the API average armor slice over the same arc is used
							float armor = this.ship.getAverageArmorInSlice(Misc.getAngleInDegrees(this.ship.getLocation(), threatData.getShip().getLocation()), 90.0F);
							if (threatData.getMaxDamagePerShot() < armor * 0.2F) {
								continue;
							}
						}

						bestThreatAngle = threatAngle;
						bestThreatValue = threatData.getThreatValue();
					}
				}
			}
		}

		if (this.ship.getShield() != null && this.maneuverAPI.getEval() != null && this.maneuverAPI.getFlockingAI() != null) {
			Vector2f avoidanceVector = this.maneuverAPI.getFlockingAI().getAvoidanceVector();
			if (avoidanceVector != null) {
				float avoidanceAngle = BattleNext_ShipAIHelper.getAngleFast(avoidanceVector);
				float avoidanceDiff = BattleNext_ShipAIHelper.getAngleDiff(moveFacing, avoidanceAngle);
				if (this.maneuverAPI.getEval().hasSolution() || avoidanceDiff < shieldArc) {
					if ((targetThreatFree || !(avoidanceDiff > shieldArc) || !this.maneuverAPI.getEval().hasSolution()) && !targetIsThreat) {
						bestThreatValue = 1.0F;
						bestThreatAngle = avoidanceAngle;
					} else {
						moveFacing = avoidanceAngle;
						bestThreatValue = 0.0F;
					}

					if (avoidanceDiff > shieldArc && this.maneuverAPI.getEval().hasSolution() && !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.BACK_OFF)) {
						this.maneuverAPI.getAIFlags().setFlag(AIFlags.BACK_OFF, 0.1F);
					}
				}
			}
		}

		float result = moveFacing;
		if (bestThreatValue > 0.0F && this.ship.getShield() != null) {
			moveFacing = BattleNext_ShipAIHelper.normalizeAngle(moveFacing);
			bestThreatAngle = BattleNext_ShipAIHelper.normalizeAngle(bestThreatAngle);
			float threatDiff = BattleNext_ShipAIHelper.getAngleDiff(moveFacing, bestThreatAngle);
			float shift = Math.min(threatDiff - shieldArc / 2.0F, shieldArc * 0.5F);
			if (shift < 0.0F) {
				shift = 0.0F;
			}

			result = moveFacing + shift * BattleNext_ShipAIHelper.getTurnDirection(moveFacing, bestThreatAngle);
			if (threatDiff > shieldArc) {
				this.targetInArc = false;
				if (BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship)) {
					this.targetInArc = true;
					result = moveFacing;
				}
			}
		}

		return result;
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public ShipAPI getTarget() {
		return this.targetShip;
	}

	@Override
	public float getDesiredFacing() {
		return this.computeFacing(false);
	}

	public float computeFacing(boolean execute) {
		if (!execute) {
			this.executeFacing(false);
		}

		if (this.backOffLeft || this.backingOff) {
			if (this.strafeAngle == Float.MAX_VALUE) {
				this.strafeAngle = this.computeStrafeAngle();
			}

			if (this.strafeAngle != Float.MAX_VALUE) {
				return this.strafeAngle;
			}
		}

		if (!BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship)
			&& !this.ship.getFluxTracker().isOverloadedOrVenting()
			&& this.maneuverAPI != null
			&& this.maneuverAPI.getEval() != null
			&& this.maneuverAPI.getEval().hasSolution()) {
			if (this.strafeAngle == Float.MAX_VALUE) {
				this.strafeAngle = this.computeStrafeAngle();
			}

			if (this.strafeAngle != Float.MAX_VALUE) {
				return this.strafeAngle;
			}
		}

		if (this.targetSide == 1.0F) {
			return this.ship.getFacing() - 90.0F;
		}

		if (this.targetSide == -1.0F) {
			return this.ship.getFacing() + 90.0F;
		}

		if (this.targetShip == null) {
			return Float.MAX_VALUE;
		}

		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
		if (this.desiredFacing == Float.MAX_VALUE) {
			return distance < this.attackRange
				? BattleNext_ShipAIHelper.getAngleFast(this.targetShip.getLocation(), this.ship.getLocation())
				: BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.targetShip.getLocation());
		}

		if (distance < this.attackRange) {
			if (this.strafeAngle == Float.MAX_VALUE) {
				this.computeStrafeAngle();
				if (this.strafeAngle != Float.MAX_VALUE) {
					return this.strafeAngle;
				}
			}

			if (!this.ship.isPhased()) {
				return this.desiredFacing;
			}

			this.strafeAngle = this.computeStrafeAngle();
			return this.strafeAngle;
		} else {
			return this.desiredFacing;
		}
	}

	@Override
	public float getTargetFacing() {
		float backOffTurn = 0.0F;
		if (this.backingOff && this.strafeAngle != Float.MAX_VALUE) {
			backOffTurn = BattleNext_ShipAIHelper.getTurnDirection(this.strafeAngle + 180.0F, this.ship.getLocation(), this.targetShip.getLocation());
		}

		if ((!this.weaponFireControlHelper.needsLeftTurn() || !(backOffTurn > 0.0F)) && (!this.weaponFireControlHelper.needsRightTurn() || !(backOffTurn < 0.0F))) {
			float maxOffset = 80.0F;
			if (this.targetShip != null) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
				float rangeExcess = distance - this.attackRange;
				if (rangeExcess > 0.0F) {
					maxOffset -= rangeExcess / this.ship.getCollisionRadius() * 30.0F;
					if (maxOffset < 30.0F) {
						maxOffset = 30.0F;
					}
				}
			}

			float angleOffset = this.weaponFireControlHelper.getAbsAngleOffset();
			if (Math.abs(angleOffset) > maxOffset) {
				angleOffset = maxOffset * Math.signum(angleOffset);
			}

			if (this.weaponFireControlHelper.needsLeftTurn() && !this.shouldBackOff) {
				return this.currentFacing - angleOffset;
			} else {
				return this.weaponFireControlHelper.needsRightTurn() && !this.shouldBackOff ? this.currentFacing + angleOffset : this.currentFacing;
			}
		} else {
			return this.strafeAngle + 180.0F;
		}
	}

	public static float computeThreatScore(ShipAPI ship, ShipAPI target, boolean selfIsNonCombat, boolean targetIsNonCombat) {
		if (ship != null && target != null) {
			String personality = BattleNext_ShipAIHelper.getPersonality(ship);
			boolean reckless = "reckless".equals(personality);
			boolean aggressive = "aggressive".equals(personality);
			boolean steady = "steady".equals(personality);
			boolean cautious = "cautious".equals(personality);
			boolean timid = "timid".equals(personality);
			float baseScore = 0.5F;
			float sizePenalty = 0.15F;
			float minScore = 0.2F;
			if (reckless) {
				baseScore = 0.8F;
				sizePenalty = 0.05F;
				minScore = 0.5F;
			}

			if (aggressive) {
				baseScore = 0.8F;
				sizePenalty = 0.15F;
				minScore = 0.3F;
			}

			if (steady) {
				baseScore = 0.5F;
				sizePenalty = 0.15F;
				minScore = 0.2F;
			}

			if (timid || cautious) {
				baseScore = 0.3F;
				sizePenalty = 0.15F;
				minScore = 0.1F;
			}

			float score = baseScore;
			int sizeDifference = target.getHullSize().ordinal() - ship.getHullSize().ordinal();
			if (targetIsNonCombat && !selfIsNonCombat) {
				sizeDifference = 0;
			}

			if (sizeDifference > 0) {
				score -= sizeDifference * sizePenalty;
				if (score < minScore) {
					score = minScore;
				}
			}

			if (ship.getPhaseCloak() != null) {
				score = Math.min(score + 0.1F, 0.8F);
			}

			if (ship.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT)) {
				score = Math.min(score + 0.25F, 0.8F);
			}

			if (ship.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN)) {
				score = Math.min(score + 0.25F, 0.8F);
			}

			return score;
		} else {
			return 0.0F;
		}
	}

	@Override
	public void init() {
		this.executeFacing(true);
	}

	// original method: executeFacing; split into sequential phases below, the
	// evaluation order is preserved exactly
	public void executeFacing(boolean giveCommands) {
		float distanceToTarget = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
		float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.targetShip);
		if (this.targetShip != null && this.ship.getShield() != null && isFrontShield(this.ship)) {
			this.currentFacing = this.computeThreatFacing();
			this.desiredFacing = this.computeMoveDest();
			WeaponGroupAPI bestGroup = this.maneuverAPI.getAttackAI().getBestWeaponGroup();
			if (bestGroup != null) {
				BattleNext_WeaponGroupEvaluation groupEval = this.maneuverAPI.getAttackAI().getWeaponGroupEvaluation(bestGroup);
				if (groupEval != null && groupEval.hasAimableWeapons()) {
					float facingDiff = Misc.getAngleDiff(this.currentFacing, this.desiredFacing);
					if (facingDiff < 45.0F) {
						this.currentFacing = this.desiredFacing;
					}
				}
			}
		} else {
			this.currentFacing = this.computeMoveDest();
			this.desiredFacing = this.currentFacing;
		}

		if (this.shouldBackOff && !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.SAFE_FROM_DANGER_TIME) && !this.ship.isPhased()) {
			float bestArmorAngle = this.computeBestArmorAngle(this.ship.getFacing(), isFrontShield(this.ship) ? 150.0F : 210.0F);
			float armorOffset = bestArmorAngle - this.ship.getFacing();
			this.currentFacing -= armorOffset;
		}

		if (giveCommands) {
			BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, this.getTargetFacing(), predictedAngleChange);
		}

		ExecuteState state = new ExecuteState();
		this.executeFacingBackoffState(state);
		this.executeFacingRangePolicy(distanceToTarget, state);
		this.executeFacingMovement(distanceToTarget, state, giveCommands);
	}

	// original method: executeFacing, back-off decision phase
	private void executeFacingBackoffState(ExecuteState state) {
		boolean targetDisabled = this.targetShip.getFluxTracker().isOverloadedOrVenting()
				&& (this.targetShip.getFluxTracker().getOverloadTimeRemaining() > 3.0F || this.targetShip.getFluxTracker().getTimeToVent() > 4.0F)
			|| this.targetShip.isPhased() && this.targetShip.getFluxLevel() > 0.8F;
		state.targetDisabledOrPhased = targetDisabled;
		boolean backOff = this.maneuverAPI.getAIFlags().hasFlag(AIFlags.BACK_OFF);
		float threatScore = computeThreatScore(this.ship, this.targetShip, this.selfIsNonCombat, this.targetIsNonCombat);
		float crMult = BattleNext_ShipAIHelper.getCRMult(this.ship);
		if (BattleNext_ShipAIHelper.isPhaseShip(this.ship)) {
			crMult *= 0.25F;
		}

		threatScore += crMult;
		if (BattleNext_ShipAIHelper.hasInterceptTask(this.ship)) {
			ShipAPI interceptTarget = null;
			BattleNext_BasicShipAI basicShipAI = BattleNext_BasicShipAI.getActiveInstance(this.ship);
			if (basicShipAI != null) {
				BattleNext_OrderResponseModule orderResponse = basicShipAI.getOrderResponseModule();
				boolean interceptOrdered = orderResponse.getOrders() != null
					&& orderResponse.getOrders().getTaskType() == CombatAssignmentType.INTERCEPT;
				if (interceptOrdered && orderResponse.getOrders().getTaskTarget() != null) {
					if (orderResponse.getOrders().getTaskTarget() instanceof DeployedFleetMemberAPI taskMember) {
						if (!taskMember.isFighterWing()) {
							interceptTarget = taskMember.getShip();
						}
					}
					// approximation: internal task wrappers lack an AssignmentTargetAPI
					// mapping; directly deployed member targets retain their ship identity.
				}
			}

			if (interceptTarget == this.targetShip && this.targetShip != null) {
				threatScore = Math.min(0.85F, threatScore + 0.2F);
			}
		}

		if ((this.ship.getFluxLevel() > 0.9F + crMult * 0.2F || this.ship.getHardFluxLevel() > threatScore) && !targetDisabled) {
			backOff = true;
		}

		backOff |= this.holdTarget;
		boolean regroup = false;
		if (this.targetShip != null && !targetDisabled) {
			if (this.ship.getFluxLevel() > this.targetShip.getFluxLevel() + 0.1F + crMult && this.ship.getFluxLevel() > 0.5F + crMult) {
				regroup = true;
			}

			boolean smallerTarget = (this.targetShip.getHullSize().ordinal() < this.ship.getHullSize().ordinal() || this.targetIsNonCombat) && !this.selfIsNonCombat;
			if (this.ship.getHardFluxLevel() > this.targetShip.getHardFluxLevel() + 0.25F + crMult
				&& !smallerTarget
				&& this.ship.getFluxLevel() > this.targetShip.getFluxLevel() + 0.1F
				&& (!this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN) || this.ship.getHardFluxLevel() > 0.5F)) {
				regroup = true;
			}

			boolean backingOffTrend = BattleNext_ShipAIHelper.getStatisticsTracker(this.ship).fitBackingOff(5.0F).avgX > 0.5F;
			boolean distanceTrend = BattleNext_ShipAIHelper.getStatisticsTracker(this.ship).fitDistanceToEnemy(10.0F).slope > 0.0F
				|| BattleNext_ShipAIHelper.getStatisticsTracker(this.ship).fitDistanceToEnemy(2.0F).slope > 0.0F;
			float fluxThreshold = 0.015F;
			if (!this.ship.isPullBackFighters() && !this.ship.getLaunchBaysCopy().isEmpty()) {
				fluxThreshold = 0.05F;
			}

			if (backingOffTrend
				&& distanceTrend
				&& !smallerTarget
				&& this.ship.getFluxLevel() > fluxThreshold
				&& (this.ship.getHardFluxLevel() > this.targetShip.getHardFluxLevel() || this.ship.getHardFluxLevel() < 0.2F)) {
				regroup = true;
			}

			if (this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT)) {
				regroup = false;
			}
		}

		if (this.blockedBehind && !this.isReckless) {
			backOff |= !this.targetInArc;
			backOff |= regroup;
		}

		if (!this.maneuverAPI.getConfig().backingOffWhileNotVentingAllowed && !this.ship.getFluxTracker().isOverloadedOrVenting()) {
			backOff = false;
		}

		boolean attackHold = false;
		backOff |= this.ship.getFluxTracker().isOverloadedOrVenting() && !this.ship.getAIFlags().hasFlag(AIFlags.DO_NOT_BACK_OFF);
		if (this.targetShip != null && this.targetShip.isStation() && BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship)) {
			backOff = false;
			regroup = false;
		}

		boolean harass = this.ship.getAIFlags().hasFlag(AIFlags.HARASS_MOVE_IN);
		float maneuverRange = this.attackRange;
		if (this.ship.getAIFlags().hasFlag(AIFlags.DO_NOT_BACK_OFF)) {
			backOff = false;
			regroup = false;
		}

		boolean aggressiveHoldRange = false;
		if ((this.isReckless || this.isAggressive || this.isSteady || harass)
			&& !this.selfIsNonCombat
			&& this.enabled
			&& this.maneuverAPI.getAIFlags().hasFlag(AIFlags.SAFE_VENT)
			&& this.ship.getFluxLevel() > 0.1F
			&& !this.holdTarget
			&& !this.ship.getAIFlags().hasFlag(AIFlags.AVOIDING_BORDER)
			&& this.targetInArc) {
			BattleNext_ThreatData threatData = this.maneuverAPI.getThreatEvaluator().getThreatData(this.targetShip);
			if (threatData == null) {
				threatData = this.maneuverAPI.getThreatEvaluator().computeThreatData(this.targetShip);
			}

			int incomingMissiles = BattleNext_ShipAIHelper.getNumberOfIncomingMissiles(this.ship, this.ship.getFluxTracker().getTimeToVent() + 1.0F);
			if (threatData.getMinTimeToHit() > this.ship.getFluxTracker().getTimeToVent() + 1.0F && incomingMissiles <= 0) {
				float safeRange = Math.max(threatData.getThreatRange(), threatData.getBeamThreatRange()) + this.collisionDistance;
				maneuverRange = safeRange;
				BattleNext_AttackAIModule attackAI = this.maneuverAPI.getAttackAI();
				float minRange = attackAI.getMinRange();
				if (this.isAggressive || this.isReckless) {
					minRange = Math.min(Math.max(attackAI.getMaxThreatRange() * 0.7F, attackAI.getMaxThreatRange() - 200.0F), minRange);
					if (minRange < 100.0F) {
						minRange = 100.0F;
					}
				}

				minRange += this.collisionDistance;
				if (this.isSteady) {
					maneuverRange += 300.0F;
				}

				if (maneuverRange < minRange) {
					maneuverRange = minRange;
				}

				if (maneuverRange > this.attackRange && threatData.getShip().getFluxTracker().isOverloadedOrVenting()) {
					maneuverRange = this.attackRange;
				}

				if (maneuverRange > safeRange) {
					maneuverRange = safeRange;
				}

				backOff = false;
				regroup = false;
				attackHold = false;
				aggressiveHoldRange = true;
			}
		}

		state.backOff = backOff;
		state.regroup = regroup;
		state.attackHold = attackHold;
		state.harass = harass;
		state.maneuverRange = maneuverRange;
		state.aggressiveHoldRange = aggressiveHoldRange;
	}

	// original method: executeFacing, maneuver range policy phase
	private void executeFacingRangePolicy(float distanceToTarget, ExecuteState state) {
		boolean aggressiveHoldRange = state.aggressiveHoldRange;
		if (BattleNext_ShipAIHelper.isPhaseShip(this.ship) && !this.ship.getFluxTracker().isOverloadedOrVenting() && !this.ship.isCapital() && distanceToTarget > state.maneuverRange * 0.5F) {
			aggressiveHoldRange = true;
		}

		boolean stationCover = this.targetShip != null
			&& (this.targetShip.isStation() || this.targetShip.isStationModule() && this.targetShip.getParentStation().isStation())
			&& !this.ship.getFluxTracker().isOverloadedOrVenting();
		if ((state.backOff || state.regroup) && !aggressiveHoldRange && !stationCover) {
			this.ship.getAIFlags().setFlag(AIFlags.DO_NOT_PURSUE);
			this.maneuverAPI.getAIFlags().setFlag(AIFlags.BACKING_OFF);
		}

		float angleToTarget = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.targetShip.getLocation());
		float facingDiff = BattleNext_ShipAIHelper.getAngleDiff(this.getTargetFacing(), this.ship.getFacing());
		float targetFacingDiff = BattleNext_ShipAIHelper.getAngleDiff(angleToTarget + 180.0F, this.targetShip.getFacing());
		boolean sizeAdvantage = this.targetShip.getHullSize().ordinal() - this.ship.getHullSize().ordinal() >= 2;
		ShipSystemAPI ownPhaseCloak = this.ship.getPhaseCloak();
		ShipSystemAPI targetPhaseCloak = this.targetShip.getPhaseCloak();
		if (ownPhaseCloak != null) {
			sizeAdvantage |= this.targetShip.getHullSize().ordinal() - this.ship.getHullSize().ordinal() >= 1;
			if (targetPhaseCloak == null) {
				sizeAdvantage |= this.targetShip.getHullSize().ordinal() - this.ship.getHullSize().ordinal() >= 0;
			}
		}

		if (!state.backOff && !aggressiveHoldRange && this.targetShip != null) {
			float fluxScore = BattleNext_ShipAIHelper.getFluxScore(this.ship, this.selfIsNonCombat, this.targetShip, this.targetIsNonCombat);
			if (this.isTimid || this.isCautious || this.isSteady) {
				fluxScore *= 0.25F;
			}

			state.maneuverRange = Math.max(200.0F + this.collisionDistance, this.attackRange - fluxScore);
		}

		float explosionMargin = 0.0F;
		if (this.targetShip != null && this.targetShip.getHullLevel() < 0.25F) {
			float hullFraction = 1.0F - this.targetShip.getHullLevel() / 0.25F;
			float explosionBase = 200.0F;
			float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, this.targetShip);
			if (closingSpeed > 20.0F && (this.ship.getShield() == null || this.ship.getFluxLevel() > 0.95F || this.ship.getShield().isOff())) {
				explosionBase = 400.0F;
			}

			boolean unusedShieldInArc = false;
			float shieldFactor = 1.0F;
			if (this.ship.getShield() != null && this.ship.getShield().isOn()) {
				boolean targetInShieldArc = BattleNext_ShipAIHelper.isInArc(
					this.ship.getShield().getFacing(), Math.max(10.0F, this.ship.getShield().getActiveArc()), this.ship.getLocation(), this.targetShip.getLocation()
				);
				if (targetInShieldArc) {
					unusedShieldInArc = true;
				}

				shieldFactor = 0.5F + 0.5F * this.ship.getFluxLevel();
			}

			explosionMargin = this.ship.getCollisionRadius() + (DamagingExplosionSpec.getShipExplosionRadius(this.targetShip) + explosionBase * hullFraction) * shieldFactor;
			state.maneuverRange = Math.max(state.maneuverRange, explosionMargin);
		}

		if (this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN)) {
			if (ownPhaseCloak != null && ownPhaseCloak.isOn() && !state.backOff && !state.regroup) {
				Vector2f behindPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.targetShip.getFacing() + 180.0F);
				behindPoint.scale(5000.0F);
				Vector2f.add(behindPoint, this.targetShip.getLocation(), behindPoint);
				float behindAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), behindPoint);
				float targetAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.targetShip.getLocation());
				float approachDiff = BattleNext_ShipAIHelper.getAngleDiff(behindAngle, targetAngle);
				state.maneuverRange = approachDiff / 170.0F * this.collisionDistance + 10.0F;
				if (this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL)) {
					Float criticalRange = (Float) this.maneuverAPI.getAIFlags().getCustom(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL);
					if (state.maneuverRange < criticalRange) {
						state.maneuverRange = criticalRange;
					}
				}
			}
		} else if (this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT)) {
			float holdRange = this.attackRange * 0.5F;
			if (this.targetShip != null) {
				float targetTurnRate = this.targetShip.getMaxTurnRate();
				float flankSpeed = this.ship.getMaxSpeed() * 0.5F;
				if (flankSpeed < 10.0F) {
					flankSpeed = 10.0F;
				}

				float orbitRange = (float) (flankSpeed / Math.toRadians(targetTurnRate));
				if (orbitRange < holdRange) {
					orbitRange = holdRange;
				}

				holdRange = Math.min(this.attackRange, orbitRange);
			}

			state.maneuverRange = Math.max(200.0F + this.collisionDistance, holdRange);
			if (this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL)) {
				Float criticalRange = (Float) this.maneuverAPI.getAIFlags().getCustom(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL);
				if (state.maneuverRange < criticalRange) {
					state.maneuverRange = criticalRange;
				}
			}
		}

		Object minRangeFlag = this.maneuverAPI.getAIFlags().getCustom(AIFlags.BACK_OFF_MIN_RANGE);
		if (minRangeFlag instanceof Float) {
			state.maneuverRange = Math.max((Float) minRangeFlag, state.maneuverRange);
		}

		Object maxRangeFlag = this.maneuverAPI.getAIFlags().getCustom(AIFlags.BACK_OFF_MAX_RANGE);
		if (maxRangeFlag instanceof Float) {
			state.maneuverRange = Math.min((Float) maxRangeFlag, state.maneuverRange);
		}

		if (this.backOffLeft != state.backOff) {
			this.backOffLeft = state.backOff;
			this.strafeAngle = this.computeStrafeAngle();
		}

		if (stationCover && this.maneuverAPI instanceof BattleNext_BasicShipAI basicShipAI && basicShipAI.getOrderResponseModule() != null) {
			BattleNext_DirectOrders orders = basicShipAI.getOrderResponseModule().getOrders();
			if (orders != null
				&& orders.getTaskType() != null
				&& orders.getTaskTarget() != null) {
				if (!(orders.getTaskTarget() instanceof DeployedFleetMemberAPI)) {
					stationCover = false;
				} else {
					ShipAPI assignedShip = ((DeployedFleetMemberAPI) orders.getTaskTarget()).getShip();
					stationCover = isStationCoverTaskTarget(assignedShip, this.targetShip);
				}
				// approximation: vanilla CombatTask may wrap a deployed member; the
				// public assignment target exposes only direct member identity.
			}
		}

		if (stationCover) {
			state.maneuverRange = Math.min(this.attackRange, Math.max(300.0F + this.collisionDistance, this.attackRange * 0.75F));
			state.backOff = false;
		}

		this.maneuverAPI.getAIFlags().setFlag(AIFlags.MANEUVER_RANGE_FROM_TARGET, 0.5F, state.maneuverRange);
		state.aggressiveHoldRange = aggressiveHoldRange;
		state.stationCover = stationCover;
		state.angleToTarget = angleToTarget;
		state.facingDiff = facingDiff;
		state.targetFacingDiff = targetFacingDiff;
		state.sizeAdvantage = sizeAdvantage;
		state.explosionMargin = explosionMargin;
	}

	// original method: executeFacing, movement command phase
	private void executeFacingMovement(float distanceToTarget, ExecuteState state, boolean giveCommands) {
		if (state.aggressiveHoldRange
			|| !state.backOff
				&& (
					!this.ship.getFluxTracker().isOverloadedOrVenting()
						|| this.ship.getAIFlags().hasFlag(AIFlags.DO_NOT_BACK_OFF_EVEN_WHILE_VENTING) && !(distanceToTarget < state.explosionMargin + 100.0F)
				)
			|| !(distanceToTarget < state.maneuverRange) && !this.ship.isFrigate() && !this.ship.isDestroyer()) {
			boolean threatPresent = true;
			boolean threatHigh = true;
			if (this.maneuverAPI.getThreatEvaluator() != null && this.maneuverAPI.getThreatEvaluator().getThreat(this.targetShip) == 0.0F) {
				threatPresent = false;
			}

			if (this.maneuverAPI.getThreatEvaluator() != null) {
				threatHigh = this.maneuverAPI.getThreatEvaluator().isThreatHigh();
			}

			boolean accelerating = false;
			boolean strafed = false;
			boolean doNotBackOff = this.ship.getAIFlags().hasFlag(AIFlags.DO_NOT_BACK_OFF);
			if (!state.backOff
				&& this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN)
				&& !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT)
				&& !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL)
				&& distanceToTarget < this.collisionDistance * 2.0F) {
				Vector2f behindPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.targetShip.getFacing() + 180.0F);
				behindPoint.scale(5000.0F);
				Vector2f.add(behindPoint, this.targetShip.getLocation(), behindPoint);
				float retreatAngle = Misc.getAngleInDegrees(this.ship.getLocation(), behindPoint);
				if (giveCommands) {
					BattleNext_ShipAIHelper.giveMoveCommands(this.ship, retreatAngle, 1000.0F);
				}
			} else {
				if (!state.attackHold && state.targetDisabledOrPhased && distanceToTarget > state.explosionMargin && distanceToTarget > 200.0F + this.collisionDistance && !this.holdTarget && this.targetInArc && !state.regroup) {
					this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
					accelerating = true;
					if (giveCommands) {
						float facingDiff = Misc.getAngleDiff(this.ship.getFacing(), state.angleToTarget);
						float turnDirection = Misc.getClosestTurnDirection(this.ship.getFacing(), state.angleToTarget);
						if (this.targetSide != 0.0F) {
							turnDirection = this.targetSide;
						}

						if (facingDiff > 60.0F) {
							if (turnDirection >= 0.0F) {
								this.ship.giveCommand(ShipCommand.STRAFE_LEFT, null, -1);
							} else {
								this.ship.giveCommand(ShipCommand.STRAFE_RIGHT, null, -1);
							}

							strafed = true;
						}
					}
				} else if (!(distanceToTarget < state.maneuverRange - 100.0F) && !this.holdTarget && this.targetInArc && !state.regroup && !this.shouldBackOff) {
					if (!state.attackHold && distanceToTarget > state.maneuverRange + 20.0F) {
						if (this.ship.getAIFlags().hasFlag(AIFlags.AVOIDING_BORDER)) {
							float mapAngle = this.getAngleToMapCenter();
							if (giveCommands) {
								BattleNext_ShipAIHelper.giveMoveCommands(this.ship, mapAngle, 1000.0F);
								return;
							}
						}

						Vector2f heading = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.desiredFacing);
						float speedAlongHeading = Vector2f.dot(heading, this.ship.getVelocity());
						if (speedAlongHeading < this.ship.getMaxSpeed() * 1.05F && !state.attackHold) {
							accelerating = true;
							if (giveCommands) {
								float facingDiff = Misc.getAngleDiff(this.ship.getFacing(), state.angleToTarget);
								float turnDirection = Misc.getClosestTurnDirection(this.ship.getFacing(), state.angleToTarget);
								if (this.targetSide != 0.0F) {
									turnDirection = this.targetSide;
								}

								if (facingDiff > 60.0F) {
									if (turnDirection >= 0.0F) {
										this.ship.giveCommand(ShipCommand.STRAFE_LEFT, null, -1);
									} else {
										this.ship.giveCommand(ShipCommand.STRAFE_RIGHT, null, -1);
									}

									strafed = true;
								}

								if (facingDiff < 85.0F) {
									this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
								}
							}
						}

						accelerating = true;
					}
				} else if (!doNotBackOff && (distanceToTarget < state.maneuverRange - 100.0F || threatPresent || this.holdTarget || !this.targetInArc || state.regroup)) {
					float facingDiff = BattleNext_ShipAIHelper.getAngleDiff(state.angleToTarget, this.ship.getFacing());
					if (!(facingDiff < 30.0F) || this.blockedBehind) {
						if (this.blockedBehind && this.strafeAngle != Float.MAX_VALUE) {
							if (giveCommands) {
								BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.strafeAngle, 1000.0F);
							}
						} else if (giveCommands) {
							BattleNext_ShipAIHelper.giveMoveCommands(this.ship, state.angleToTarget + 180.0F, 1000.0F);
						}

						return;
					}

					this.ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, -1);
				}

				this.executeFacingStrafeDecision(distanceToTarget, state, giveCommands, threatPresent, threatHigh, accelerating, strafed);
			}
		} else {
			if (this.strafeAngle != Float.MAX_VALUE && (this.blockedBehind || this.holdTarget || !this.targetInArc)) {
				if (giveCommands) {
					BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.strafeAngle, 1000.0F);
				}
			} else {
				float backOffBias = 15.0F;
				if (this.ship.getAIFlags().hasFlag(AIFlags.AVOIDING_BORDER)) {
					backOffBias = 45.0F;
				}

				if (this.targetSide != 0.0F) {
					if (giveCommands) {
						BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.desiredFacing + 180.0F - this.targetSide * backOffBias, 1000.0F);
					}
				} else if (this.backOffDirection != 0.0F) {
					if (giveCommands) {
						BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.desiredFacing + 180.0F - this.backOffDirection * backOffBias, 1000.0F);
					}
				} else if (giveCommands) {
					BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.desiredFacing + 180.0F + this.strafeDirection * backOffBias, 1000.0F);
				}
			}
		}
	}

	// original method: executeFacing, strafe direction decision phase
	private void executeFacingStrafeDecision(float distanceToTarget, ExecuteState state, boolean giveCommands, boolean threatPresent, boolean threatHigh, boolean accelerating, boolean strafed) {
		boolean targetNotBigger = this.targetShip.getHullSize().ordinal() <= this.ship.getHullSize().ordinal();
		boolean targetFluxHigh = this.targetShip.getFluxLevel() >= 0.75F;
		boolean lowOwnFlux = this.ship.getFluxLevel() <= 0.35F;
		boolean midOwnFlux = this.ship.getFluxLevel() <= 0.65F;
		boolean hasShieldOrCloak = this.ship.getShield() != null || this.ship.getPhaseCloak() != null;
		boolean targetBigger = this.targetShip.getHullSize().ordinal() > this.ship.getHullSize().ordinal() && (!this.targetIsNonCombat || this.selfIsNonCombat);
		boolean targetSmaller = (this.targetShip.getHullSize().ordinal() < this.ship.getHullSize().ordinal() || this.targetIsNonCombat) && !this.selfIsNonCombat;
		// intentionally kept dead: vanilla computes this flag but never reads it
		boolean unusedTargetStrictlySmaller = this.targetShip.getHullSize().ordinal() < this.ship.getHullSize().ordinal();
		boolean equalSize = this.targetShip.getHullSize().ordinal() == this.ship.getHullSize().ordinal() && !this.targetIsNonCombat && !this.selfIsNonCombat;
		Vector2f awayFromTarget = Vector2f.sub(this.ship.getLocation(), this.targetShip.getLocation(), new Vector2f());
		BattleNext_ShipAIHelper.normalise(awayFromTarget);
		Vector2f targetHeading = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.targetShip.getFacing());
		float alignment = Vector2f.dot(awayFromTarget, targetHeading);
		boolean behindTarget = false;
		if (alignment < -0.5F) {
			behindTarget = true;
		}

		// intentionally kept dead: vanilla computes this flag but never reads it
		boolean unusedOffSide = alignment < 0.71F;
		boolean pushIn = targetSmaller
			|| equalSize && midOwnFlux && (hasShieldOrCloak || behindTarget)
			|| targetBigger && lowOwnFlux && targetFluxHigh && (hasShieldOrCloak || behindTarget)
			|| !this.selfIsNonCombat && fluxAvailable(this.ship) > fluxAvailable(this.targetShip) && (hasShieldOrCloak || behindTarget);
		float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, this.targetShip);
		boolean lowHitpoints = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
		pushIn &= !lowHitpoints || this.targetShip.isFighter();
		float relativeSpeed = Vector2f.sub(this.ship.getVelocity(), this.targetShip.getVelocity(), new Vector2f()).length();
		pushIn &= closingSpeed < relativeSpeed * 0.67F;
		if (state.facingDiff <= 45.0F && !strafed) {
			float strafeSide = 0.0F;
			if (this.targetSide != 0.0F) {
				strafeSide = this.targetSide;
			} else if (this.backOffDirection != 0.0F) {
				strafeSide = this.backOffDirection;
			} else if (!(state.targetFacingDiff > 120.0F) && !state.sizeAdvantage) {
				Vector2f relativeVelocity = Vector2f.sub(this.targetShip.getVelocity(), this.ship.getVelocity(), new Vector2f());
				if (!this.targetShip.isFighter()) {
					pushIn &= relativeVelocity.length() > 20.0F;
				}

				BattleNext_ThreatData threatData = this.maneuverAPI.getThreatEvaluator().getThreatData(this.targetShip);
				if (threatData != null) {
					if (distanceToTarget > threatData.getThreatRange() + this.collisionDistance + 100.0F && this.aggressivePush) {
						pushIn = true;
					}
				} else if (!threatPresent && this.aggressivePush) {
					pushIn = true;
				}

				if ((this.isAggressive || this.isReckless) && !threatHigh && (!targetBigger || behindTarget || state.targetDisabledOrPhased)) {
					pushIn = true;
				}

				if (accelerating) {
					this.emergencyBackOffTimer = 1.0F;
				} else {
					this.emergencyBackOffTimer = this.emergencyBackOffTimer - this.lastFrameDt;
					if (this.emergencyBackOffTimer < 0.0F) {
						this.emergencyBackOffTimer = 0.0F;
					}
				}

				if (this.emergencyBackOffTimer > 0.0F && closingSpeed < 0.0F && !threatHigh) {
					pushIn = true;
				}

				if (this.maneuverAPI.getConfig().alwaysStrafeOffensively) {
					pushIn = true;
				}

				if (pushIn) {
					strafeSide = this.strafeDirection;
					if (this.directionFlipped) {
						strafeSide = -strafeSide;
					}
				} else {
					strafeSide = -this.strafeDirection;
				}
			} else {
				Vector2f targetHeadingVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.targetShip.getFacing());
				Vector2f awayVector = Vector2f.sub(this.ship.getLocation(), this.targetShip.getLocation(), new Vector2f());
				strafeSide = BattleNext_ShipAIHelper.getTurnDirection(targetHeadingVector, awayVector);
			}

			ShipSystemAPI ownPhaseCloak = this.ship.getPhaseCloak();
			if (ownPhaseCloak != null
				&& (
					this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN) && ownPhaseCloak.isOn()
						|| this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT)
				)) {
				if (!(distanceToTarget > this.collisionDistance + 100.0F)
					|| this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_FROM_BEHIND_DIST_CRITICAL)
					|| !ownPhaseCloak.isOn() && this.maneuverAPI.getAIFlags().hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT)) {
					Vector2f targetHeadingVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.targetShip.getFacing());
					Vector2f awayVector = Vector2f.sub(this.ship.getLocation(), this.targetShip.getLocation(), new Vector2f());
					strafeSide = BattleNext_ShipAIHelper.getTurnDirection(targetHeadingVector, awayVector);
				} else {
					strafeSide = this.strafeDirection;
				}
			}

			if (strafeSide >= 0.0F) {
				if (giveCommands) {
					this.ship.giveCommand(ShipCommand.STRAFE_RIGHT, null, -1);
				}
			} else if (giveCommands) {
				this.ship.giveCommand(ShipCommand.STRAFE_LEFT, null, -1);
			}
		}
	}

	public Vector2f getMoveDest() {
		return this.moveDest;
	}

	private float computeMoveDest() {
		float leadRange = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null
			&& !this.maneuverAPI.getAttackingGroup().missilesAimable()
			&& !this.maneuverAPI.getAttackingGroup().isAllGuidedPoor()) {
			leadRange = this.maneuverAPI.getAttackingGroup().getRange();
		} else if (this.maneuverAPI.getAttackAI() != null) {
			leadRange = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetShip, leadRange, false);
		this.moveDest = aimPoint;
		return BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), aimPoint);
	}

	private float computeStrafeAngle() {
		return this.computeCollisionAngle(true);
	}

	private float computeCollisionAngle(boolean firstPass) {
		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		ArrayList<BattleNext_CollisionCheckSegment> segments = new ArrayList<>();

		for (ShipAPI other : ships) {
			boolean bomber = other.getWing() != null && other.getWing().getSpec().isBomber();
			if ((!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& (other.getHullSize().ordinal() >= this.ship.getHullSize().ordinal() - 2 || bomber || other.isDestroyer() || other.getParentStation() != null)
				&& !other.isRetreating()
				&& other != this.ship) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation());
				if (other.getOwner() != 100) {
					float radiiSum = this.ship.getCollisionRadius() + other.getCollisionRadius();
					if (this.ship.getOwner() == other.getOwner()) {
						float checkRange = 300.0F + radiiSum;
						if (distance < checkRange) {
							BattleNext_CollisionCheckSegment segment = new BattleNext_CollisionCheckSegment(other.getLocation(), other.getVelocity(), other.getCollisionRadius(), other.getFacing());
							float angle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), other.getLocation());
							float span = circumferenceAngleSpan(other.getCollisionRadius(), distance);
							segment.setAngleSpan(span);
							segment.setCenterAngle(angle);
							segments.add(segment);
						}
					} else {
						float checkRange = 1800.0F + radiiSum;
						if (distance < checkRange) {
							BattleNext_CollisionCheckSegment segment = new BattleNext_CollisionCheckSegment(other.getLocation(), other.getVelocity(), other.getCollisionRadius(), other.getFacing());
							float angle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), other.getLocation());
							float margin = 200.0F;
							BattleNext_ThreatData threatData = this.maneuverAPI.getThreatEvaluator().getThreatData(other);
							if (threatData != null && firstPass) {
								margin += Math.min(1000.0F, threatData.getThreatRange() + distance);
							}

							if (distance < radiiSum + 100.0F) {
								margin = 0.0F;
							}

							float span = circumferenceAngleSpan(other.getCollisionRadius() + margin, distance);
							if (span > 210.0F) {
								span = 210.0F;
							}

							segment.setAngleSpan(span);
							segment.setCenterAngle(angle);
							segments.add(segment);
						}
					}
				}
			}
		}

		List<BattleNext_CollisionTimeRange> ranges = this.buildCollisionTimeRanges(segments);
		float widest = 0.0F;
		BattleNext_CollisionTimeRange bestRange = null;

		for (BattleNext_CollisionTimeRange range : ranges) {
			if (widest < range.getWidth()) {
				bestRange = range;
				widest = range.getWidth();
			}
		}

		if (bestRange != null) {
			float angleBias = 0.0F;
			if (this.backOffLeft) {
				angleBias = 20.0F;
			} else if (this.backOffRight) {
				angleBias = -30.0F;
			}

			this.currentFacing = this.computeMoveDest();
			this.desiredFacing = this.currentFacing;
			float side = this.backOffDirection;
			if (side == 0.0F) {
				if (this.backOffLeft) {
					angleBias = 45.0F;
				}

				if (this.targetShip != null) {
					Vector2f relativeVelocity = Vector2f.sub(this.ship.getVelocity(), this.targetShip.getVelocity(), new Vector2f());
					if (relativeVelocity.length() >= 1.0F) {
						side = -1.0F * BattleNext_ShipAIHelper.getTurnDirection(this.desiredFacing, BattleNext_ShipAIHelper.getAngleFast(relativeVelocity));
					} else {
						side = -1.0F * this.strafeDirection;
					}
				} else {
					side = -1.0F * this.strafeDirection;
				}
			}

			if (this.ship.getFluxTracker().isOverloadedOrVenting()) {
				angleBias = 60.0F;
			}

			if (this.ship.isNonCombat(true)) {
				if (side == 0.0F) {
					return bestRange.getStartAngle();
				}

				angleBias = 60.0F;
			}

			if (this.ship.getAIFlags().hasFlag(AIFlags.AVOIDING_BORDER)) {
				float mapAngle = this.getAngleToMapCenter();
				return BattleNext_ShipAIHelper.clampAngleToArc(bestRange.getStartAngle(), bestRange.getWidth() * 0.75F, mapAngle);
			}

			float strafeAngle = this.desiredFacing - side * (90.0F + angleBias);
			if (!this.targetShip.isFrigate() && !this.targetShip.isFighter()) {
				return BattleNext_ShipAIHelper.clampAngleToArc(bestRange.getStartAngle(), bestRange.getWidth() * 0.25F, strafeAngle);
			}

			angleBias *= 1.25F;
			return BattleNext_ShipAIHelper.clampAngleToArc(bestRange.getStartAngle(), bestRange.getWidth() * 0.5F, strafeAngle);
		} else if (firstPass) {
			return this.computeCollisionAngle(false);
		} else {
			return this.ship.getAIFlags().hasFlag(AIFlags.AVOIDING_BORDER)
				? this.getAngleToMapCenter()
				: Float.MAX_VALUE;
		}
	}

	private List<BattleNext_CollisionTimeRange> buildCollisionTimeRanges(List<BattleNext_CollisionCheckSegment> segments) {
		ArrayList<BattleNext_CollisionDangerEntry> entries = new ArrayList<>();

		for (BattleNext_CollisionCheckSegment segment : segments) {
			BattleNext_CollisionDangerEntry start = new BattleNext_CollisionDangerEntry(segment, BattleNext_CollisionDangerEntry.DANGER_START, segment.getCenterAngle() - segment.getAngleSpan() / 2.0F);
			BattleNext_CollisionDangerEntry end = new BattleNext_CollisionDangerEntry(segment, BattleNext_CollisionDangerEntry.DANGER_END, segment.getCenterAngle() + segment.getAngleSpan() / 2.0F);
			entries.add(start);
			entries.add(end);
		}

		entries.sort(new BattleNext_StrafeCollisionComparator(this));
		ArrayList<BattleNext_CollisionTimeRange> ranges = new ArrayList<>();
		if (entries.isEmpty()) {
			return ranges;
		}

		int depth = 0;
		float width = 0.0F;
		float gapStartAngle = 0.0F;
		boolean gapOpen = false;

		for (BattleNext_CollisionDangerEntry entry : entries) {
			if (entry.isDangerStart()) {
				depth++;
			} else {
				depth--;
			}

			if (gapOpen && depth > 0) {
				width = entry.getAngle() - gapStartAngle;
				BattleNext_CollisionTimeRange range = new BattleNext_CollisionTimeRange();
				range.setStartAngle(BattleNext_ShipAIHelper.normalizeAngle(entry.getAngle() - width / 2.0F));
				range.setWidth(width);
				ranges.add(range);
				width = 0.0F;
				gapOpen = false;
			}

			if (depth == 0) {
				gapOpen = true;
				gapStartAngle = entry.getAngle();
			}
		}

		BattleNext_CollisionDangerEntry firstEntry = entries.get(0);
		float wrapAngle = firstEntry.getAngle() + 360.0F;
		if (wrapAngle > gapStartAngle) {
			width = wrapAngle - gapStartAngle;
			BattleNext_CollisionTimeRange range = new BattleNext_CollisionTimeRange();
			range.setStartAngle(BattleNext_ShipAIHelper.normalizeAngle(wrapAngle - width / 2.0F));
			range.setWidth(width);
			ranges.add(range);
		}

		return ranges;
	}

	public float getStrafeAngle() {
		return this.strafeAngle;
	}

	// shared mutable state of the split executeFacing phases
	private static class ExecuteState {
		private boolean targetDisabledOrPhased;
		private boolean backOff;
		private boolean regroup;
		private boolean attackHold;
		private boolean harass;
		private float maneuverRange;
		private boolean aggressiveHoldRange;
		private boolean stationCover;
		private float angleToTarget;
		private float facingDiff;
		private float targetFacingDiff;
		private boolean sizeAdvantage;
		private float explosionMargin;
	}

	// port of engine HullVariantSpec.isNonCombat: carrier or civilian hints and
	// no combat hint
	static boolean isStationCoverTaskTarget(ShipAPI assignedShip, ShipAPI targetShip) {
		return assignedShip == null || BattleNext_ShipAIHelper.isSameStationOrShip(assignedShip, targetShip);
	}

	private static boolean isNonCombatVariant(ShipAPI ship) {
		return (ship.getVariant().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CARRIER)
			|| ship.getVariant().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CIVILIAN))
			&& !ship.getVariant().getHints().contains(ShipHullSpecAPI.ShipTypeHints.COMBAT);
	}

	// approximation: ShipAPI.isShieldOn is not present in the mod-visible API
	private static boolean isShieldOn(ShipAPI ship) {
		return ship.getShield() != null && ship.getShield().isOn();
	}

	// approximation: the engine un-phasing transition state is not exposed through
	// the API; the ship is assumed to never be mid-transition
	private static boolean isUnphasing(ShipAPI ship) {
		return false;
	}

	// approximation: engine ShieldAPI.isFront is not present in the mod-visible
	// API; the shield type is used instead
	private static boolean isFrontShield(ShipAPI ship) {
		return ship.getShield() != null && ship.getShield().getType() == ShieldAPI.ShieldType.FRONT;
	}

	// approximation: engine ArmorEvaluator.getBestArmorAngle is not in the API;
	// sample slices across the arc and return the angle of the thickest one
	private float computeBestArmorAngle(float facing, float arc) {
		float bestAngle = facing;
		float bestArmor = -1.0F;
		float step = 15.0F;
		for (float angle = facing - arc / 2.0F; angle <= facing + arc / 2.0F; angle += step) {
			float armor = this.ship.getAverageArmorInSlice(angle - step / 2.0F, step);
			if (armor > bestArmor) {
				bestArmor = armor;
				bestAngle = angle;
			}
		}

		return bestAngle;
	}

	// port of engine Utils.m_a6e824ce: the angular span a circle of the given
	// radius covers when seen from the given distance
	private static float circumferenceAngleSpan(float radius, float distance) {
		return 2.0F * radius / ((float) (Math.PI * 2) * distance) * 360.0F;
	}

	private static float fluxAvailable(ShipAPI ship) {
		return BattleNext_ShipAIHelper.getFluxAvailable(ship);
	}

	// approximation: the engine combat map center is reconstructed from the helper
	// map bounds
	private float getAngleToMapCenter() {
		Vector2f mapCenter = new Vector2f(
			(BattleNext_ShipAIHelper.getMapMinX() + BattleNext_ShipAIHelper.getMapMaxX()) / 2.0F,
			(BattleNext_ShipAIHelper.getMapMinY() + BattleNext_ShipAIHelper.getMapMaxY()) / 2.0F
		);
		return BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), mapCenter);
	}

	// port of engine com.fs.util.VectorMathUtils.segmentIntersection: the point
	// where the two segments cross, or null when they do not
	private static Vector2f segmentIntersection(Vector2f p1, Vector2f p2, Vector2f p3, Vector2f p4) {
		float d1x = p2.x - p1.x;
		float d1y = p2.y - p1.y;
		float d2x = p4.x - p3.x;
		float d2y = p4.y - p3.y;
		float denominator = d1x * d2y - d1y * d2x;
		if (Math.abs(denominator) < 1.0E-7F) {
			return null;
		}

		float t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denominator;
		float u = ((p3.x - p1.x) * d1y - (p3.y - p1.y) * d1x) / denominator;
		if (t < 0.0F || t > 1.0F || u < 0.0F || u > 1.0F) {
			return null;
		}

		return new Vector2f(p1.x + d1x * t, p1.y + d1y * t);
	}
}
