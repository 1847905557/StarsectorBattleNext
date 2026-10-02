package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionCheckSegment;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionDangerEntry;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionTimeRange;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import data.scripts.battlesnext.util.annotation.PublicData;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.EscortTargetManeuverV3.
 * Station-keeping escort maneuver: picks a spot around the escorted ship while
 * engaging nearby threats.
 * The public constructor (ship, target, attack AI, flocking AI, maneuver API)
 * is kept because OrderResponseModule and BasicShipAI construct this maneuver.
 * Fighter-only engine dependencies (FighterAI, SpreadAI) are not ported; the
 * affected branches keep their structure with inert substitutes.
 */
public class BattleNext_EscortTargetManeuverV3 implements BattleNext_Maneuver {
	// package-visible for the same-package escort spot comparators, matching the
	// synthetic outer references of the decompiled engine sources
	private ShipAPI ship;
	private ShipAPI targetShip;
	private CombatEntityAPI targetEntity;
	private BattleNext_FlockingAI flockingAI;
	private boolean done = false;
	private float desiredFacing = Float.MAX_VALUE;
	private float collisionRadiiSum;
	private IntervalUtil intervalTracker = new IntervalUtil(0.5F, 0.7F);
	private float unusedFloat;
	private final BattleNext_AttackAIModule attackAI;
	private final BattleNext_ManeuverAPI maneuverAPI;
	// protected in vanilla; the same-package escort spot comparators read it
	private ShipAPI threatShip = null;
	private float slowdownTimer = 0.0F;
	// port of engine enum OO0O$oo; f_6a00a25e_3 -> LEFT, f_6a00a25e -> RIGHT,
	// f_6a00a25e_2 -> EITHER
	private enum EscortSide {
		LEFT,
		RIGHT,
		EITHER
	}
	private EscortSide escortSide = EscortSide.EITHER;
	// vanilla never assigns escortSide outside the declaration, so it stays EITHER
	private BattleNext_CollisionTimeRange collisionTimeRange = null;
	private Vector2f escortOffset = new Vector2f();
	private float targetValue = Float.MAX_VALUE;
	private int unusedInt = -1;
	// port of engine class com.fs.starfarer.combat.EscortAssignment reduced to the
	// members this maneuver reads; the engine class is not ported wholesale and
	// this field is never assigned, matching vanilla where it stays null
	// port of engine class com.fs.starfarer.EscortAssignment reduced to the
	// members this maneuver reads; the engine class is not ported wholesale and
	// this field is never assigned, matching vanilla where it stays null
	@PublicData
	private static class EscortAssignment {
		public Vector2f position = new Vector2f();
		// f_d435ae80_2: the stand-off distance the assignment wants to keep
		public float standOffDistance;
	}
	private EscortAssignment escortAssignment = null;
	private List<BattleNext_EscortSpot> escortSpots = new ArrayList<>();
	private List<BattleNext_EscortSpot> unusedEscortSpots = new ArrayList<>();
	private boolean unusedShieldOn = true;
	// protected in vanilla; the same-package escort spot comparators read it
	private CombatEntityAPI threatEntity;
	private boolean hasPD;

	// the same-package escort spot comparators read these formerly outer-visible fields
	public ShipAPI getShipField() {
		return this.ship;
	}

	public CombatEntityAPI getThreatEntityField() {
		return this.threatEntity;
	}

	public BattleNext_EscortTargetManeuverV3(ShipAPI ship, ShipAPI targetShip, BattleNext_AttackAIModule attackAI, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.targetShip = targetShip;
		this.attackAI = attackAI;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.collisionRadiiSum = ship.getCollisionRadius() + targetShip.getCollisionRadius();
		this.refreshEscortData();
		Vector2f escortLocation = this.getEscortLocation();
		Vector2f.sub(escortLocation, this.targetShip.getLocation(), this.escortOffset);
		this.hasPD = false;
		boolean interceptorWing = ship.isFighter() && ship.getWing() != null && ship.getWing().getSpec().isInterceptor();

		for (WeaponAPI weapon : ship.getUsableWeapons()) {
			if (weapon.hasAIHint(AIHints.PD) && (weapon.getArc() < 360.0F || interceptorWing)) {
				this.hasPD = true;
				break;
			}
		}
	}

	public ShipAPI getTargetShip() {
		return this.targetShip;
	}

	public Vector2f getEscortOffset() {
		return this.escortOffset;
	}

	@Override
	public void advance(float amount) {
		this.slowdownTimer -= amount;
		if (this.slowdownTimer < 0.0F) {
			this.slowdownTimer = 0.0F;
		}

		if (!this.isTargetValid()) {
			this.done = false;
			this.desiredFacing = Float.MAX_VALUE;
		} else {
			float assignmentDistance = 0.0F;
			if (this.escortAssignment != null) {
				assignmentDistance = BattleNext_ShipAIHelper.getDistance(this.escortAssignment.position, this.ship.getLocation());
			}

			float targetDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
			this.intervalTracker.advance(amount);
			if (this.intervalTracker.intervalElapsed()) {
				this.refreshEscortData();
				Vector2f escortLocation = this.getEscortLocation();
				Vector2f.sub(escortLocation, this.targetShip.getLocation(), this.escortOffset);
			}

			this.targetValue = this.computeThreatFacing(this.targetEntity instanceof ShipAPI ? (ShipAPI) this.targetEntity : null);
			boolean unusedAlwaysFalse = false;
			if ((!(assignmentDistance > 1000.0F + this.collisionRadiiSum) || this.targetEntity != null || this.threatEntity != null) && !unusedAlwaysFalse) {
				this.done = true;
				Vector2f holdPoint = Vector2f.add(this.escortOffset, this.targetShip.getLocation(), new Vector2f());
				float holdAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), holdPoint);
				float carrierDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
				float toTargetAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.targetShip.getLocation());
				// intentionally kept dead: vanilla computes this angular span but never
				// reads it
				float unusedAngularSpan = this.targetShip.getCollisionRadius() * 2.5F / ((float) (Math.PI * 2) * carrierDistance) * 360.0F;
				float speedFactor = 0.95F;
				if (this.ship.isCruiser() || this.ship.isCapital()) {
					speedFactor = 1.25F;
				}

				float targetVelocityAngle = BattleNext_ShipAIHelper.getAngleFast(this.targetShip.getVelocity());
				if (!BattleNext_ShipAIHelper.isInArc(targetVelocityAngle, 60.0F, this.targetShip.getLocation(), this.ship.getLocation())) {
					if (targetDistance < (this.ship.getCollisionRadius() + this.targetShip.getCollisionRadius()) * speedFactor || this.targetShip.isStation()) {
						this.slowdownTimer = 0.75F + (float) Math.random() * 0.75F;
					}

					if (this.slowdownTimer <= 0.0F && !BattleNext_ShipAIHelper.isPhaseShip(this.ship)) {
						this.flockingAI.addCollisionObject(this.targetShip);
					} else if (this.slowdownTimer > 0.0F && (this.ship.isCruiser() || this.ship.isCapital())) {
						this.flockingAI.registerCollisionObject(this.targetShip);
					}
				}

				this.desiredFacing = this.computeDesiredFacing(holdAngle);
				if (!this.isCurrentTargetValid()) {
					this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyNotInArc(
						this.ship,
						this.targetShip,
						this.attackAI.getAttackRange() + this.ship.getCollisionRadius() + 100.0F,
						!this.ship.isCapital() && !this.ship.isCruiser()
					);
					if (this.targetEntity == null) {
						this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyNotInArc(
							this.ship, this.targetShip, this.attackAI.getAttackRange() + this.ship.getCollisionRadius() + 100.0F, true
						);
					}
				}

				if (this.targetEntity != null) {
					float leadRange = Float.MAX_VALUE;
					if (this.attackAI.getAttackingGroup() != null) {
						leadRange = this.attackAI.getAttackingGroup().getRange();
					} else if (this.maneuverAPI.getAttackAI() != null) {
						leadRange = this.maneuverAPI.getAttackAI().getAverageCooldown();
					}

					Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetEntity, leadRange * 0.8F, !this.attackAI.isAttackGroupReady());
					if (this.targetValue == Float.MAX_VALUE) {
						this.targetValue = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), aimPoint);
					}
				} else if (this.targetValue == Float.MAX_VALUE) {
					float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
					if (!(distance > this.ship.getCollisionRadius() + this.targetShip.getCollisionRadius() + 1000.0F)
						&& (!this.ship.isFighter() || !(distance > this.ship.getCollisionRadius() + this.targetShip.getCollisionRadius() + 200.0F))) {
						this.targetValue = this.getEscortFacing();
					} else {
						this.targetValue = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.targetShip.getLocation());
					}
				}
			} else {
				this.done = false;
				float holdAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), Vector2f.add(this.targetShip.getLocation(), this.escortOffset, new Vector2f()));
				this.desiredFacing = this.computeDesiredFacing(holdAngle);
			}
		}
	}

	protected ShipAPI findBlockingShip(CombatEntityAPI entity) {
		if (entity == null) {
			return null;
		}

		float selfDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), entity.getLocation()) - entity.getCollisionRadius() - this.ship.getCollisionRadius();
		float selfSpan = Math.min(150.0F, (float) Math.toDegrees(entity.getCollisionRadius() * 2.0F / selfDistance));
		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		float bestDistance = Float.MAX_VALUE;
		ShipAPI blockingShip = null;
		float entityAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), entity.getLocation());

		for (ShipAPI other : ships) {
			if (!other.isFighter()
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& other.isTargetable()
				&& this.ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100) {
				float otherDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation()) - other.getCollisionRadius() - this.ship.getCollisionRadius();
				float otherSpan = Math.min(150.0F, (float) Math.toDegrees(other.getCollisionRadius() * 2.0F / otherDistance));
				float distanceDifference = Math.max(0.0F, selfDistance - otherDistance);
				float blockArc = selfSpan + otherSpan + 10.0F + Math.min(20.0F, distanceDifference * 0.015F);
				if (BattleNext_ShipAIHelper.isInArc(entityAngle, blockArc, this.ship.getLocation(), other.getLocation()) && otherDistance < bestDistance && otherDistance < selfDistance) {
					bestDistance = otherDistance;
					blockingShip = other;
				}
			}
		}

		return blockingShip;
	}

	public void refreshEscortData() {
		this.collisionTimeRange = null;
		BattleNext_EscortTaskData taskData = this.getEscortTaskData();
		if (taskData == null) {
			this.threatShip = BattleNext_ShipAIHelper.getNearestEnemy(this.ship, false);
		} else {
			this.threatShip = taskData.threatShip;
			this.collisionTimeRange = taskData.collisionTimeRange;
		}
	}

	public float getEscortFacing() {
		if (this.targetShip.getFixedLocation() != null) {
			return this.targetShip.getOwner() == 0 ? 90.0F : 270.0F;
		} else {
			return this.threatShip == null ? this.targetShip.getFacing() : Misc.getAngleInDegrees(this.targetShip.getLocation(), this.threatShip.getLocation());
		}
	}

	@Override
	public void init() {
		Vector2f holdPoint = Vector2f.add(this.escortOffset, this.targetShip.getLocation(), new Vector2f());
		boolean facedOverride = false;
		float facingOverride = this.getFacingOverride();
		if (facingOverride != Float.MAX_VALUE) {
			BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, facingOverride);
			facedOverride = true;
		}

		float standOff = 200.0F;
		if (this.ship.isFighter()) {
			standOff = 100.0F;
		}

		if (this.escortAssignment != null) {
			standOff = this.escortAssignment.standOffDistance;
		}

		if (!this.isCurrentTargetValid() && this.targetEntity != null) {
			this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyNotInArc(
				this.ship,
				this.targetShip,
				this.attackAI.getAttackRange() + this.ship.getCollisionRadius() + standOff * 0.5F + 50.0F,
				!this.ship.isCapital() && !this.ship.isCruiser()
			);
			if (this.targetEntity == null) {
				this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyNotInArc(
					this.ship, this.targetShip, this.attackAI.getAttackRange() + this.ship.getCollisionRadius() + standOff * 0.5F + 50.0F, true
				);
			}

			if (this.threatShip != null) {
				this.targetEntity = this.threatShip;
			}
		}

		if (this.targetEntity == null) {
			this.targetEntity = this.threatShip;
		}

		if (this.targetEntity != null) {
			ShipAPI blockingShip = this.findBlockingShip(this.targetEntity);
			if (blockingShip != null && blockingShip != this.targetEntity) {
				if (blockingShip != this.threatShip) {
					this.threatShip = blockingShip;
				}

				this.targetEntity = blockingShip;
			}
		}

		if (this.targetEntity == null) {
			standOff = 100.0F;
		}

		if (this.ship.isFighter() && this.targetEntity != null) {
			boolean supportWing = this.ship.getWing() != null && (this.ship.getWing().getSpec().isSupport() || this.ship.getWing().getRange() <= 0.0F);
			if (this.ship.getWing() != null && this.ship.getWing().getSpec().isBomber()) {
				// intentionally kept dead: vanilla discards the bomber check result
				boolean unusedBomberFlag = true;
			} else {
				// intentionally kept dead: vanilla discards the else result
				boolean unusedElseFlag = false;
			}

			boolean beingReplaced = BattleNext_ShipAIHelper.isFighterBeingReplaced(this.ship);
			if (!supportWing && !beingReplaced) {
				float carrierDistance = BattleNext_ShipAIHelper.getDistance(this.targetShip.getLocation(), this.targetEntity.getLocation());
				if (carrierDistance < 600.0F + this.collisionRadiiSum + standOff) {
					this.moveTowardTarget();
					return;
				}
			}
		}

		if (this.targetEntity != null) {
			float leadRange = Float.MAX_VALUE;
			if (this.attackAI.getAttackingGroup() != null) {
				leadRange = this.attackAI.getAttackingGroup().getRange();
			} else if (this.maneuverAPI.getAttackAI() != null) {
				leadRange = this.maneuverAPI.getAttackAI().getAverageCooldown();
			}

			Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetEntity, leadRange, !this.attackAI.isAttackGroupReady());
			float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.targetEntity);
			if (!facedOverride) {
				if (this.targetValue == Float.MAX_VALUE) {
					BattleNext_ShipAIHelper.turnToFace(this.ship, aimPoint, 0.0F, predictedAngleChange);
				} else {
					BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, this.targetValue, predictedAngleChange);
				}
			}
		} else if (!facedOverride) {
			if (this.targetValue == Float.MAX_VALUE) {
				BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, this.getEscortFacing());
			} else {
				BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, this.targetValue);
			}
		}

		if (this.targetEntity != null) {
			boolean stayInFront = this.hasStayInFrontTag();
			boolean supportWing = this.ship.getWing() != null && (this.ship.getWing().getSpec().isSupport() || this.ship.getWing().getRange() <= 0.0F);
			if (supportWing && !this.targetShip.getFluxTracker().isOverloadedOrVenting() && !stayInFront) {
				float holdRange = this.maneuverAPI.getAttackAI().getBestGroupRange()
					+ BattleNext_ShipAIHelper.getDistanceToEntity(this.ship, this.targetEntity) * this.targetEntity.getCollisionRadius() * 0.75F;
				float targetDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetEntity.getLocation());
				float carrierTargetDistance = BattleNext_ShipAIHelper.getDistance(this.targetShip.getLocation(), this.targetEntity.getLocation());
				float escortDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
				if (escortDistance > carrierTargetDistance * 0.33F) {
					float toTargetAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.targetShip.getLocation());
					BattleNext_ShipAIHelper.giveMoveCommands(this.ship, toTargetAngle, 10000.0F);
					return;
				}

				// approximation: vanilla FighterAI uses SpreadAI direction to reposition;
				// the public maneuver API has no spread state, so escort range decides.

				if (targetDistance < holdRange - 100.0F || targetDistance < carrierTargetDistance + 100.0F && targetDistance < holdRange + 1000.0F) {
					float awayAngle = BattleNext_ShipAIHelper.getAngleFast(this.targetEntity.getLocation(), this.ship.getLocation());
					BattleNext_ShipAIHelper.giveMoveCommands(this.ship, awayAngle, 10000.0F);
					return;
				}
			}
		}

		float standOffDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), holdPoint);
		boolean spreadRepositioning = false;
		// approximation: vanilla FighterAI reads SpreadAI spreading state;
		// the public maneuver API has no spread state, so it remains idle.

		if (standOffDistance > standOff || spreadRepositioning) {
			BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.desiredFacing, 10000.0F);
		} else if (standOffDistance < standOff / 2.0F) {
			if (this.targetShip.getVelocity().length() < 10.0F && this.ship.getVelocity().length() > 5.0F) {
				this.ship.giveCommand(ShipCommand.DECELERATE, null, -1);
			} else {
				BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.getEscortFacing(), this.targetShip.getVelocity().length());
			}
		}
	}

	private Vector2f getEscortLocation() {
		return this.computeEscortLocation();
	}

	// original method: computeEscortLocation; split into sequential phases below,
	// the evaluation order is preserved exactly
	private Vector2f computeEscortLocation() {
		Vector2f awayFromTarget = Vector2f.sub(this.ship.getLocation(), this.targetShip.getLocation(), new Vector2f());
		BattleNext_ShipAIHelper.normalise(awayFromTarget);
		BattleNext_EscortSpot[] spots = this.buildEscortSpots();
		float range = this.computeEscortRange();
		boolean shy = this.isShyEscort();
		HullSize minHullSize = this.getMinEscortHullSize();
		// original method: computeEscortLocation, spot placement phase
		this.placeEscortSpots(spots, range, minHullSize);

		if (this.ship.isFighter()) {
			// original method: computeEscortLocation, fighter target resolution phase
			this.resolveFighterTarget(range);
			// original method: computeEscortLocation, fighter spot pick phase; null
			// means vanilla falls through to the ship targeting below
			Vector2f fighterSpot = this.pickFighterEscortSpot(spots, shy);
			if (fighterSpot != null) {
				return fighterSpot;
			}
		}

		// original method: computeEscortLocation, ship target resolution phase
		this.resolveEscortTarget(range);
		if (this.targetEntity instanceof ShipAPI) {
			float threatScore = computeThreatScore(this.ship, (ShipAPI) this.targetEntity);
			if (!this.maneuverAPI.getConfig().backingOffWhileNotVentingAllowed) {
				threatScore = 1.0F;
			}

			if (this.ship.getFluxLevel() > 0.9F || this.ship.getHardFluxLevel() > threatScore) {
				shy = true;
			}
		} else {
			this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyMissile(this.ship, 600.0F + this.collisionRadiiSum);
		}

		// original method: computeEscortLocation, back-off decision phase
		boolean backOff = this.computeEscortBackoff(shy);
		// original method: computeEscortLocation, spot selection phase
		return this.pickEscortSpot(spots, range, shy, backOff);
	}

	private BattleNext_EscortSpot[] buildEscortSpots() {
		BattleNext_EscortSpot[] spots;
		if (this.ship.isFighter()) {
			float spotCount = 12.0F;
			spots = new BattleNext_EscortSpot[(int) spotCount];

			for (int index = 0; index < spots.length; index++) {
				spots[index] = new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() + index * 360.0F / spotCount), index * 360.0F / spotCount);
			}
		} else {
			// intentionally kept dead: vanilla builds this candidate array and
			// discards it; EscortSpot construction is package-visible here because
			// vanilla constructs spots directly despite the private constructor,
			// a residual artifact of the decompilation
			BattleNext_EscortSpot[] unusedSpots = new BattleNext_EscortSpot[]{
				new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() + 110.0F), 110.0F),
				new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() - 110.0F), -110.0F),
				new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() + 150.0F), 150.0F),
				new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() - 150.0F), -150.0F),
				new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() + 60.0F), 60.0F),
				new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() - 60.0F), -60.0F)
			};
			spots = new BattleNext_EscortSpot[12];
			float step = 30.0F;
			float[] orderedAngles = new float[]{
				step * 5.0F - step / 2.0F,
				step * 4.0F - step / 2.0F,
				step * 2.0F - step / 2.0F,
				step * 1.0F - step / 2.0F,
				step * 3.0F - step / 2.0F,
				step * 6.0F - step / 2.0F
			};

			for (int index = 0; index < spots.length / 2; index++) {
				float angle = orderedAngles[index];
				spots[index * 2] = new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() + angle), angle);
				spots[index * 2 + 1] = new BattleNext_EscortSpot(BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.getEscortFacing() - angle), -angle);
			}
		}

		return spots;
	}

	private float computeEscortRange() {
		float range = 300.0F;
		if (this.ship.isFrigate() && this.targetShip.isFrigate()) {
			range = 300.0F;
		}

		if (this.ship.isFighter()) {
			range = 200.0F;
		}

		if (BattleNext_ShipAIHelper.isPhaseShip(this.ship)) {
			range += 100.0F;
		}

		if (this.targetShip.isStation()) {
			range = 1000.0F;
		}

		if (!BattleNext_ShipAIHelper.hasEscortOrder(this.ship) && !this.ship.isFighter() && !this.isCarrierWithBays()) {
			range = 600.0F;
		}

		if (!this.ship.isFighter()) {
			range += 100.0F;
		}

		if (this.ship.getAIFlags().hasFlag(AIFlags.ESCORT_RANGE_MODIFIER)) {
			float modifier = (Float) this.ship.getAIFlags().getCustom(AIFlags.ESCORT_RANGE_MODIFIER);
			range += modifier;
		}

		return range;
	}

	private boolean isCarrierWithBays() {
		return this.ship.getVariant().isCarrier()
			&& !this.ship.getHullSpec().getHints().contains(ShipTypeHints.NO_AUTO_ESCORT)
			&& this.ship.getLaunchBaysCopy().size() >= this.ship.getMutableStats().getNumFighterBays().getModifiedValue() * 0.66F;
	}

	private boolean isShyEscort() {
		boolean shy = this.ship.isNonCombat(true);
		if (this.ship.isFighter()
			&& this.ship.getWing() != null
			&& this.ship.getWing().getSpec().isBomber()
			&& this.ship.getWing().getRange() > 0.0F
			&& this.targetShip != null) {
			shy = true;
		}

		if (this.isCarrierWithBays()) {
			shy = true;
		}

		return shy;
	}

	private HullSize getMinEscortHullSize() {
		HullSize hullSize = this.ship.getHullSize();
		if (hullSize.ordinal() < HullSize.DESTROYER.ordinal()) {
			hullSize = HullSize.DESTROYER;
		}

		return hullSize;
	}

	private void placeEscortSpots(BattleNext_EscortSpot[] spots, float range, HullSize minHullSize) {
		int sequence = 0;

		for (BattleNext_EscortSpot spot : spots) {
			spot.sequenceIndex = sequence++;
			float extraDistance = 0.0F;
			if (!this.ship.isFighter() && sequence != 5) {
				// intentionally kept dead: vanilla's condition has an empty body
			}

			float distance = this.collisionRadiiSum + range + extraDistance;
			if (this.ship.isFighter()) {
				float angleFromBow = BattleNext_ShipAIHelper.getAngleDiff(0.0F, spot.angle);
				float falloff = 0.0F + 1.0F * Math.min(1.0F, angleFromBow / 90.0F);
				distance = this.collisionRadiiSum + (range + extraDistance) * falloff;
			}

			spot.location.scale(distance);
			Vector2f.add(this.targetShip.getLocation(), spot.location, spot.location);
			float allyRange = 500.0F;
			if (this.ship.isFighter()) {
				allyRange = 200.0F;
			}

			spot.index = BattleNext_ShipAIHelper.getNumberOfAlliesEscortingTarget(this.ship, this.targetShip, spot.location, allyRange, minHullSize);
		}
	}

	private void resolveFighterTarget(float range) {
		this.threatEntity = null;
		float scanRange = 1200.0F;
		boolean interceptorWing = this.ship.getWing() != null && this.ship.getWing().getSpec().isInterceptor();
		boolean supportWing = this.ship.getWing() != null && (this.ship.getWing().getSpec().isSupport() || this.ship.getWing().getRange() <= 0.0F);
		boolean bomberWithStandoff = this.ship.getWing() != null && this.ship.getWing().getSpec().isBomber() && this.ship.getWing().getRange() > 0.0F;
		boolean beingReplaced = BattleNext_ShipAIHelper.isFighterBeingReplaced(this.ship);
		if (supportWing) {
			scanRange = 2000.0F;
		}

		if (interceptorWing) {
			if (this.hasPD) {
				this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyMissile(this.targetShip, scanRange + this.collisionRadiiSum);
				this.threatEntity = this.targetEntity;
				if (this.targetEntity != null && ((MissileAPI) this.targetEntity).getDamage().getDamage() < 400.0F) {
					this.targetEntity = null;
				}
			}

			if (this.targetEntity == null) {
				this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyInRangeNoFighters(this.targetShip, scanRange + this.collisionRadiiSum);
				this.threatEntity = this.targetEntity;
			}
		} else {
			this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyInRangeNoFighters(this.targetShip, scanRange + this.collisionRadiiSum);
			this.threatEntity = this.targetEntity;
			if (this.targetEntity == null && this.hasPD) {
				this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyMissile(this.targetShip, scanRange + this.collisionRadiiSum);
				this.threatEntity = this.targetEntity;
				if (this.targetEntity != null && ((MissileAPI) this.targetEntity).getDamage().getDamage() < 400.0F) {
					this.targetEntity = null;
				}
			}
		}

		if (this.targetEntity == null) {
			this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, scanRange + this.collisionRadiiSum);
			this.threatEntity = this.targetEntity;
			if (this.targetEntity != null) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.targetShip.getLocation(), this.targetEntity.getLocation());
				if (distance > scanRange + this.collisionRadiiSum) {
					this.targetEntity = null;
				}
			}
		}

		if (supportWing && this.ship.getWing() != null && this.ship.getWing().getSpec().hasTag("target_escort_target")) {
			ShipAPI escortedShip = this.targetShip;
			if (escortedShip != null) {
				ShipAPI carrierTarget = BattleNext_ShipAIHelper.getCarrierTarget(escortedShip);
				if (carrierTarget != null) {
					float distance = BattleNext_ShipAIHelper.getDistance(this.targetShip.getLocation(), carrierTarget.getLocation());
					if (distance < scanRange + this.collisionRadiiSum) {
						this.targetEntity = carrierTarget;
					}
				}
			}
		}
	}

	private Vector2f pickFighterEscortSpot(BattleNext_EscortSpot[] spots, boolean shy) {
		boolean bomberWithStandoff = this.ship.getWing() != null && this.ship.getWing().getSpec().isBomber() && this.ship.getWing().getRange() > 0.0F;
		boolean beingReplaced = BattleNext_ShipAIHelper.isFighterBeingReplaced(this.ship);
		boolean enemiesNearTarget = BattleNext_ShipAIHelper.getNearestEnemyInRangeIncludingFighters(this.targetShip, 1500.0F) != null;
		if (this.threatEntity != null || enemiesNearTarget) {
			if (this.threatEntity != null) {
				Arrays.sort(spots, new BattleNext_EscortSpotRangeComparator(this));
				return !shy && !bomberWithStandoff && !beingReplaced ? spots[1].location : spots[spots.length - 1].location;
			}

			if (!shy && !bomberWithStandoff && !beingReplaced) {
				if (this.unusedInt == -1) {
					this.unusedInt = (int) (Math.random() * spots.length);
				}

				Vector2f spotLocation = spots[this.unusedInt].location;
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), spotLocation);
				if (distance < 50.0F) {
					this.unusedInt = (int) (Math.random() * spots.length);
				}

				return spotLocation;
			} else {
				int middle = spots.length / 2;
				return spots[middle].location;
			}
		}

		return null;
	}

	private void resolveEscortTarget(float range) {
		this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyInRange(
			this.ship,
			this.ship.getLocation(),
			Math.max(4500.0F, this.attackAI.getAttackRange() + this.collisionRadiiSum + range + 1000.0F),
			null,
			!this.ship.isCapital() && !this.ship.isCruiser()
		);
		if (this.targetEntity == null) {
			this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyInRange(
				this.ship, this.ship.getLocation(), Math.max(4500.0F, this.attackAI.getAttackRange() + this.collisionRadiiSum + range + 1000.0F), null, true
			);
		}

		if (this.threatShip != null) {
			this.targetEntity = this.threatShip;
		}

		if (this.targetEntity != null) {
			ShipAPI blockingShip = this.findBlockingShip(this.targetEntity);
			if (blockingShip != null && blockingShip != this.targetEntity) {
				if (blockingShip != this.threatShip) {
					this.threatShip = blockingShip;
				}

				this.targetEntity = blockingShip;
			}
		}

		this.threatEntity = this.targetEntity;
	}

	private boolean computeEscortBackoff(boolean shy) {
		ShipAPI target = null;
		if (this.targetEntity instanceof ShipAPI) {
			target = (ShipAPI) this.targetEntity;
		}

		boolean targetDisabled = target != null
			&& (
				target.getFluxTracker().isOverloadedOrVenting()
					&& (target.getFluxTracker().getOverloadTimeRemaining() > 3.0F || target.getFluxTracker().getTimeToVent() > 4.0F)
				|| target.isPhased() && target.getFluxLevel() > 0.8F
			);
		boolean backOff = this.maneuverAPI.getAIFlags().hasFlag(AIFlags.BACK_OFF);
		boolean regroup = false;
		if (target != null && !targetDisabled) {
			if (this.ship.getFluxLevel() > target.getFluxLevel() + 0.1F && this.ship.getFluxLevel() > 0.5F) {
				regroup = true;
			}

			boolean targetNonCombat = target != null && isNonCombatVariant(target);
			boolean smallerTarget = (target.getHullSize().ordinal() < this.ship.getHullSize().ordinal() || targetNonCombat) && !shy;
			if (this.ship.getHardFluxLevel() > target.getHardFluxLevel() + 0.25F && !smallerTarget && this.ship.getFluxLevel() > target.getFluxLevel() + 0.1F) {
				regroup = true;
			}
		}

		backOff |= regroup;
		backOff |= this.ship.getFluxTracker().isOverloadedOrVenting();
		backOff |= this.targetEntity == null && this.maneuverAPI.getThreatEvaluator().getTotalThreat() > 0.0F;
		backOff |= shy;
		boolean beingReplaced = BattleNext_ShipAIHelper.isFighterBeingReplaced(this.ship);
		backOff |= beingReplaced;
		String personality = BattleNext_ShipAIHelper.getPersonality(this.ship);
		boolean timid = "timid".equals(personality);
		timid |= this.ship.getAIFlags().hasFlag(AIFlags.TIMID_ESCORT);
		backOff |= timid;
		this.maneuverAPI.getAIFlags().unsetFlag(AIFlags.PHASE_ATTACK_RUN);
		return backOff;
	}

	private Vector2f pickEscortSpot(BattleNext_EscortSpot[] spots, float range, boolean shy, boolean backOff) {
		ArrayList<BattleNext_EscortSpot> candidates = new ArrayList<>();
		float holdFacing = this.targetShip.getFacing();
		if (this.targetShip.getFixedLocation() != null) {
			if (this.targetShip.getOwner() == 0) {
				holdFacing = 90.0F;
			} else {
				holdFacing = 270.0F;
			}
		}

		Vector2f holdPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(holdFacing);
		holdPoint.scale(range + this.collisionRadiiSum);
		Vector2f.add(holdPoint, this.targetShip.getLocation(), holdPoint);
		boolean holdStationKeeping = this.targetShip.getFluxTracker().isOverloadedOrVenting()
			&& (this.targetShip.getFluxTracker().getOverloadTimeRemaining() > 2.0F || this.targetShip.getFluxTracker().getTimeToVent() > 2.0F);
		boolean stayInFront = this.hasStayInFrontTag();
		holdStationKeeping |= stayInFront;
		if (this.isCarrierWithBays()) {
			holdStationKeeping = false;
		}

		if (backOff) {
			holdStationKeeping = false;
		}

		for (BattleNext_EscortSpot spot : spots) {
			if ((
					this.ship.isFighter()
						|| !BattleNext_ShipAIHelper.isShipInRangeOf(this.ship, spot.location, this.targetShip) && !BattleNext_ShipAIHelper.isPointInRangeOf(this.ship, spot.location, holdPoint, range)
				)
				&& (this.ship.isFighter() || spot.index == 0)) {
				candidates.add(spot);
			}
		}

		if (candidates.isEmpty()) {
			for (BattleNext_EscortSpot spot : spots) {
				if (spot.index == 0) {
					return spot.location;
				}
			}

			Arrays.sort(spots, new BattleNext_EscortSpotIndexComparator(this));
			return spots[0].location;
		} else {
			boolean sortedByThreat = false;
			if (this.ship.isFighter() && shy) {
				this.targetEntity = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, this.attackAI.getAttackRange() + this.collisionRadiiSum + range + 10000.0F);
				this.threatEntity = this.targetEntity;
				if (this.threatEntity != null) {
					Collections.sort(candidates, new BattleNext_EscortSpotRangeComparatorV2(this));
				}

				sortedByThreat = true;
			}

			ArrayList<BattleNext_EscortSpot> backoffCandidates = new ArrayList<>(candidates);
			if (!sortedByThreat) {
				float sideAngle = 60.0F;
				Vector2f test1 = Misc.getUnitVectorAtDegreeAngle(this.getEscortFacing() + sideAngle);
				Vector2f test2 = Misc.getUnitVectorAtDegreeAngle(this.getEscortFacing() - sideAngle);
				test1.scale(range + this.collisionRadiiSum);
				test2.scale(range + this.collisionRadiiSum);
				Vector2f.add(test1, this.targetShip.getLocation(), test1);
				Vector2f.add(test2, this.targetShip.getLocation(), test2);
				Vector2f backOffAwayFrom = new Vector2f();
				if (this.threatEntity != null) {
					backOffAwayFrom.set(this.threatEntity.getLocation());
				}

				if (this.collisionTimeRange != null) {
					Vector2f awayPoint = Misc.getUnitVectorAtDegreeAngle(this.collisionTimeRange.getStartAngle() + 180.0F);
					awayPoint.scale(2000.0F);
					Vector2f.add(awayPoint, this.targetShip.getLocation(), awayPoint);
					backOffAwayFrom.set(awayPoint);
				}

				if (this.escortSide == EscortSide.LEFT) {
					test1.x += 1.0E7F;
				}

				if (this.escortSide == EscortSide.RIGHT) {
					test2.x += 1.0E7F;
				}

				float holdArc = 90.0F;
				if (holdStationKeeping) {
					holdArc = 40.0F;
				}

				if (this.collisionTimeRange != null && this.threatShip != null) {
					float clampedAngle = BattleNext_ShipAIHelper.clampAngleToArc(
						this.collisionTimeRange.getStartAngle(),
						Math.max(this.collisionTimeRange.getWidth() * 0.5F, this.collisionTimeRange.getWidth() - holdArc),
						BattleNext_ShipAIHelper.getAngleFast(this.targetShip.getLocation(), this.threatShip.getLocation())
					);
					Vector2f clampedPoint = Misc.getUnitVectorAtDegreeAngle(clampedAngle);
					clampedPoint.scale(range + this.collisionRadiiSum);
					Vector2f.add(clampedPoint, this.targetShip.getLocation(), clampedPoint);
					test1.set(clampedPoint);
					test2.set(clampedPoint);
				}

				Collections.sort(candidates, new BattleNext_EscortSpotTestPointComparator(this, test1, test2));
				Collections.sort(backoffCandidates, new BattleNext_EscortSpotBackoffComparator(this, backOffAwayFrom));
			}

			this.escortSpots.clear();
			this.escortSpots.addAll(candidates);
			if (this.ship.isFighter() || (this.ship.getShield() == null || !(this.ship.getFluxLevel() > 0.95)) && !backOff) {
				if (backOff) {
					int index = backoffCandidates.size() - 1;
					if (this.threatEntity == null && backOff) {
						index = backoffCandidates.size() / 2;
					}

					return backoffCandidates.get(index).location;
				} else {
					int index = 0;
					return candidates.get(index).location;
				}
			} else if (backOff) {
				int index = backoffCandidates.size() - 1;
				return backoffCandidates.get(index).location;
			} else {
				return backoffCandidates.get(backoffCandidates.size() - 1).location;
			}
		}
	}

	public ShipAPI getThreatShip() {
		return this.threatShip;
	}

	public List<BattleNext_EscortSpot> getEscortSpots() {
		return this.escortSpots;
	}

	public List<BattleNext_EscortSpot> getUnusedEscortSpots() {
		return this.unusedEscortSpots;
	}

	public static float computeThreatScore(ShipAPI ship, ShipAPI target) {
		return ship != null && target != null ? BattleNext_StrafeTargetManeuverV2.computeThreatScore(ship, target, ship.isNonCombat(true), target.isNonCombat(false)) : 0.0F;
	}

	public void moveTowardTarget() {
		float side = BattleNext_ShipAIHelper.getAngleToEntity(this.ship, this.targetEntity, false);
		if (side == 0.0F) {
			side = -1.0F;
		}

		float attackRange = this.attackAI.getAttackRange() + this.targetEntity.getCollisionRadius() * 0.25F;
		float leadRange = Float.MAX_VALUE;
		if (this.attackAI.getAttackingGroup() != null) {
			leadRange = this.attackAI.getAttackingGroup().getRange();
		} else if (this.maneuverAPI.getAttackAI() != null) {
			leadRange = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		// intentionally kept dead: vanilla overwrites the lead range with max speed
		leadRange = this.ship.getMaxSpeed();
		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetEntity, leadRange, !this.attackAI.isAttackGroupReady());
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), aimPoint);
		float aimAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), aimPoint);
		float lead = attackRange * 1.0F;
		float hypotenuse = (float) Math.sqrt(lead * lead + distance * distance);
		float offset = (float) Math.toDegrees((float) Math.asin(lead / hypotenuse));
		if (distance < attackRange && this.targetEntity instanceof ShipAPI && ((ShipAPI) this.targetEntity).getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
			offset += 60.0F;
		} else if (distance < attackRange && this.targetEntity instanceof MissileAPI) {
			offset += 30.0F;
		} else if (distance > attackRange * 1.5F) {
			offset = 0.0F;
		}

		float moveAngle = aimAngle + side * offset;
		BattleNext_ShipAIHelper.giveMoveCommands(this.ship, moveAngle, 1000.0F);
		if (distance < attackRange + 200.0F) {
			float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.targetEntity);
			BattleNext_ShipAIHelper.turnToFace(this.ship, aimPoint, 0.0F, predictedAngleChange);
		} else {
			BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, moveAngle);
		}
	}

	@Override
	public boolean isDone() {
		return this.done;
	}

	private boolean isTargetValid() {
		// approximation: the engine entity object list is replaced by the ship list
		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		return ships.contains(this.targetShip) && !this.targetShip.isExpired() || this.isInLaunchBays(this.targetShip);
	}

	// approximation: the engine lists the fighters waiting in launch bays on the
	// engine object; the API cannot, so a fighter with a living source carrier
	// counts as bay-held
	private boolean isInLaunchBays(ShipAPI fighter) {
		return fighter.isFighter() && fighter.getWing() != null && fighter.getWing().getSourceShip() != null;
	}

	private boolean isCurrentTargetValid() {
		if (this.targetEntity == null) {
			return false;
		}

		// approximation: the engine entity object list is replaced by the ship list;
		// non-ship targets cannot be confirmed and count as expired
		if (this.targetEntity instanceof ShipAPI) {
			List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
			ShipAPI targetShip = (ShipAPI) this.targetEntity;
			return ships.contains(targetShip) && !targetShip.isExpired();
		}

		return false;
	}

	@Override
	public float getDesiredFacing() {
		return this.desiredFacing;
	}

	@Override
	public float getTargetFacing() {
		float override = this.getFacingOverride();
		return override != Float.MAX_VALUE ? override : this.targetValue;
	}

	// intentionally kept dead: vanilla carries this empty entry point
	public static void unusedMain(String[] args) {
	}

	private float computeDesiredFacing(float desiredAngle) {
		if (this.ship.isFighter()) {
			// approximation: vanilla FighterAI adds SpreadAI heading;
			// the public maneuver API has no spread vector, so desiredAngle is used.
			Vector2f heading = BattleNext_ShipAIHelper.getUnitVectorAtAngle(desiredAngle);
			BattleNext_ShipAIHelper.normalise(heading);
			return BattleNext_ShipAIHelper.getAngleFast(heading);
		} else {
			ArrayList<BattleNext_CollisionCheckSegment> segments = new ArrayList<>();
			BattleNext_CollisionCheckSegment segment = new BattleNext_CollisionCheckSegment(
				this.targetShip.getLocation(), this.targetShip.getVelocity(), this.targetShip.getCollisionRadius(), this.getEscortFacing()
			);
			float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
			float span = circumferenceAngleSpan(this.targetShip.getCollisionRadius(), distance);
			float angle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.targetShip.getLocation());
			Vector2f aheadPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.targetShip.getFacing());
			aheadPoint.scale(100000.0F);
			Vector2f.add(aheadPoint, this.targetShip.getLocation(), aheadPoint);
			float aheadAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), aheadPoint);
			angle = BattleNext_ShipAIHelper.normalizeAngle(angle);
			aheadAngle = BattleNext_ShipAIHelper.normalizeAngle(aheadAngle);
			float centerAngle = angle + BattleNext_ShipAIHelper.getAngleDiff(angle, aheadAngle) * 0.5F * BattleNext_ShipAIHelper.getTurnDirection(angle, aheadAngle);
			float totalSpan = BattleNext_ShipAIHelper.getAngleDiff(angle, aheadAngle) + span;
			segment.setAngleSpan(totalSpan);
			segment.setCenterAngle(centerAngle);
			segments.add(segment);
			List<BattleNext_CollisionTimeRange> ranges = this.buildDangerSegments(segments);
			float widest = 0.0F;
			BattleNext_CollisionTimeRange bestRange = null;

			for (BattleNext_CollisionTimeRange range : ranges) {
				if (widest < range.getWidth()) {
					bestRange = range;
					widest = range.getWidth();
				}
			}

			return bestRange == null ? desiredAngle : BattleNext_ShipAIHelper.clampAngleToArc(bestRange.getStartAngle(), bestRange.getWidth(), desiredAngle);
		}
	}

	private List<BattleNext_CollisionTimeRange> buildDangerSegments(List<BattleNext_CollisionCheckSegment> segments) {
		ArrayList<BattleNext_CollisionDangerEntry> entries = new ArrayList<>();

		for (BattleNext_CollisionCheckSegment segment : segments) {
			BattleNext_CollisionDangerEntry start = new BattleNext_CollisionDangerEntry(segment, BattleNext_CollisionDangerEntry.DANGER_START, segment.getCenterAngle() - segment.getAngleSpan() / 2.0F);
			BattleNext_CollisionDangerEntry end = new BattleNext_CollisionDangerEntry(segment, BattleNext_CollisionDangerEntry.DANGER_END, segment.getCenterAngle() + segment.getAngleSpan() / 2.0F);
			entries.add(start);
			entries.add(end);
		}

		Collections.sort(entries, new BattleNext_CollisionObjectComparator(this));
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

	@Override
	public CombatEntityAPI getTarget() {
		return this.targetEntity;
	}

	private float computeThreatFacing(ShipAPI targetShip) {
		if (this.ship.isFighter()) {
			return Float.MAX_VALUE;
		}

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

		if (targetShip != null) {
			threatShip = targetShip;
		}

		if (threatShip == null) {
			return Float.MAX_VALUE;
		}

		if (targetShip == null) {
			targetShip = threatShip;
		}

		BattleNext_ThreatData targetThreat = threatEvaluator.computeThreatData(targetShip);
		boolean targetIsThreat = targetThreat.isThreat();
		this.unusedShieldOn = true;
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

		float threatFacing = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), threatShip.getLocation());
		float bestThreatValue = 0.0F;
		float bestThreatAngle = 0.0F;
		boolean unusedTargetThreatFree = true;

		for (BattleNext_ThreatData threatData : threatEvaluator.getAllThreatData()) {
			if (threatData.getShip() != threatShip) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), threatShip.getLocation());
				float radiiSum = this.ship.getCollisionRadius() + threatData.getShip().getCollisionRadius();
				if (!(threatData.getThreatRange() + radiiSum + 100.0F < distance)) {
					float threatAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), threatData.getShip().getLocation());
					// intentionally kept dead: vanilla computes this difference but never
					// reads it
					float unusedAngleDiff = BattleNext_ShipAIHelper.getAngleDiff(threatFacing, threatAngle);
					if (threatData.getThreatValue() > bestThreatValue && !threatData.isThreat()) {
						bestThreatAngle = threatAngle;
						bestThreatValue = threatData.getThreatValue();
						unusedTargetThreatFree = threatData.isThreat();
					}
				}
			}
		}

		if (this.ship.getShield() != null && this.maneuverAPI.getEval() != null && this.maneuverAPI.getFlockingAI() != null) {
			Vector2f avoidanceVector = this.maneuverAPI.getFlockingAI().getAvoidanceVector();
			if (avoidanceVector != null) {
				float avoidanceAngle = BattleNext_ShipAIHelper.getAngleFast(avoidanceVector);
				float avoidanceDiff = BattleNext_ShipAIHelper.getAngleDiff(threatFacing, avoidanceAngle);
				if (this.maneuverAPI.getEval().hasSolution()) {
					if ((!(avoidanceDiff > shieldArc) || !this.maneuverAPI.getEval().hasSolution()) && !targetIsThreat) {
						bestThreatValue = 1.0F;
						bestThreatAngle = avoidanceAngle;
					} else {
						threatFacing = avoidanceAngle;
						bestThreatValue = 0.0F;
					}
				}
			}
		}

		float result = threatFacing;
		if (bestThreatValue > 0.0F && this.ship.getShield() != null && this.ship.getShield().isOn() && !targetIsThreat) {
			threatFacing = BattleNext_ShipAIHelper.normalizeAngle(threatFacing);
			bestThreatAngle = BattleNext_ShipAIHelper.normalizeAngle(bestThreatAngle);
			float threatDiff = BattleNext_ShipAIHelper.getAngleDiff(threatFacing, bestThreatAngle);
			float shift = Math.min(threatDiff - shieldArc / 2.0F, shieldArc * 0.5F);
			if (shift < 0.0F) {
				shift = 0.0F;
			}

			result = threatFacing + shift * BattleNext_ShipAIHelper.getTurnDirection(threatFacing, bestThreatAngle);
			if (threatDiff > shieldArc) {
				this.unusedShieldOn = false;
			}
		}

		return result;
	}

	public BattleNext_EscortTaskData getEscortTaskData() {
		List<ShipAPI> allShips = BattleNext_ShipAIHelper.getEngine().getShips();
		float queryRange = this.attackAI == null ? 1000.0F : this.attackAI.getAttackRange() + 100.0F;
		if (queryRange < 500.0F) {
			queryRange = 500.0F;
		}

		List<ShipAPI> threats = this.collectEscortThreats(allShips, queryRange);
		if (threats.isEmpty()) {
			return null;
		}

		ArrayList<BattleNext_CollisionCheckSegment> segments = new ArrayList<>();

		for (ShipAPI threat : threats) {
			BattleNext_CollisionCheckSegment segment = new BattleNext_CollisionCheckSegment(threat, threat.getLocation(), threat.getVelocity(), threat.getCollisionRadius(), threat.getFacing());
			float distance = BattleNext_ShipAIHelper.getDistance(threat.getLocation(), this.targetShip.getLocation());
			float span = circumferenceAngleSpan(threat.getCollisionRadius(), distance);
			float angle = BattleNext_ShipAIHelper.getAngleFast(this.targetShip.getLocation(), threat.getLocation());
			angle = BattleNext_ShipAIHelper.normalizeAngle(angle);
			segment.setAngleSpan(span);
			segment.setCenterAngle(angle);
			segments.add(segment);
		}

		List<BattleNext_CollisionTimeRange> ranges = this.buildDangerSegments(segments);
		float widest = 0.0F;
		BattleNext_CollisionTimeRange bestRange = null;

		for (BattleNext_CollisionTimeRange range : ranges) {
			if (widest < range.getWidth()) {
				bestRange = range;
				widest = range.getWidth();
			}
		}

		if (bestRange == null) {
			bestRange = new BattleNext_CollisionTimeRange();
			bestRange.setStartAngle(this.targetShip.getFacing() + 180.0F);
			bestRange.setWidth(90.0F);
		}

		float bestAngleDistance = 10000.0F;
		BattleNext_EscortTaskData taskData = new BattleNext_EscortTaskData();
		taskData.collisionTimeRange = bestRange;
		float rangeStart = bestRange.getStartAngle() - bestRange.getWidth() / 2.0F;
		float rangeEnd = bestRange.getStartAngle() + bestRange.getWidth() / 2.0F;
		float startDiff = Misc.getAngleDiff(rangeStart, this.targetShip.getFacing() + 180.0F);
		float endDiff = Misc.getAngleDiff(rangeEnd, this.targetShip.getFacing() + 180.0F);
		float threatAngle = rangeStart;
		if (startDiff > endDiff) {
			threatAngle = rangeEnd;
		}

		if (Math.abs(startDiff - endDiff) < 20.0F) {
			threatAngle = bestRange.getStartAngle();
		}

		float ownAngle = Misc.normalizeAngle(BattleNext_ShipAIHelper.getAngleFast(this.targetShip.getLocation(), this.ship.getLocation()));
		float ownStartDiff = Misc.getAngleDiff(ownAngle, rangeStart);
		float ownEndDiff = Misc.getAngleDiff(ownAngle, rangeEnd);
		List<ShipAPI> allies = BattleNext_ShipAIHelper.getAlliedShips(this.targetShip, this.ship);
		float startAllies = 0.0F;
		float endAllies = 0.0F;

		for (ShipAPI ally : allies) {
			float allyAngle = Misc.normalizeAngle(BattleNext_ShipAIHelper.getAngleFast(this.targetShip.getLocation(), ally.getLocation()));
			float allyStartDiff = Misc.getAngleDiff(allyAngle, rangeStart);
			float allyEndDiff = Misc.getAngleDiff(allyAngle, rangeEnd);
			if (allyStartDiff < ownStartDiff) {
				startAllies++;
			}

			if (allyEndDiff < ownEndDiff) {
				endAllies++;
			}
		}

		float allyHalf = (startAllies + endAllies + 1.0F) / 2.0F;
		if (startAllies >= allyHalf) {
			threatAngle = rangeEnd;
		}

		if (endAllies >= allyHalf) {
			threatAngle = rangeStart;
		}

		for (ShipAPI threat : threats) {
			float angle = BattleNext_ShipAIHelper.getAngleFast(this.targetShip.getLocation(), threat.getLocation());
			float angleDiff = Misc.getAngleDiff(angle, threatAngle);
			float distance = Misc.getDistance(this.targetShip.getLocation(), threat.getLocation());
			distance = Math.max(0.0F, distance - this.targetShip.getCollisionRadius() - 500.0F);
			if (angleDiff < bestAngleDistance) {
				bestAngleDistance = angleDiff;
				taskData.threatShip = threat;
			}
		}

		return taskData;
	}

	private List<ShipAPI> collectEscortThreats(List<ShipAPI> allShips, float queryRange) {
		ArrayList<ShipAPI> ships = new ArrayList<>();
		ArrayList<ShipAPI> fighters = new ArrayList<>();

		for (ShipAPI other : allShips) {
			if (other.getOwner() != this.ship.getOwner()
				&& (!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isHulk()
				&& !other.isShuttlePod()) {
				if (other.isDrone()) {
					// intentionally kept dead: vanilla's drone mothership proximity check
					// has an empty body
					Object mothership = other.getAIFlags().getCustom(AIFlags.DRONE_MOTHERSHIP);
					if (mothership instanceof ShipAPI) {
						float distance = BattleNext_ShipAIHelper.getDistance(other.getLocation(), ((ShipAPI) mothership).getLocation());
						if (distance < ((ShipAPI) mothership).getCollisionRadius() + 300.0F) {
						}
					}
				} else {
					if (other.isFighter() && other.getWing() != null) {
						ShipAPI sourceShip = other.getWing().getSourceShip();
						if (sourceShip != null) {
							float distance = BattleNext_ShipAIHelper.getDistance(other.getLocation(), sourceShip.getLocation());
							if (distance < sourceShip.getCollisionRadius() + 700.0F && sourceShip.isPullBackFighters()) {
								continue;
							}
						}

						float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation()) - this.ship.getCollisionRadius();
						if (this.ship.isFrigate() && distance < 750.0F || this.ship.isDestroyer() && distance < 1250.0F) {
							continue;
						}
					}

					if ((other.getHullSize().ordinal() >= this.ship.getHullSize().ordinal() - 2 || other.isDestroyer() || other.getParentStation() != null)
						&& other.isTargetable()
						&& this.ship.getOwner() != other.getOwner()
						&& other.getOwner() != 100) {
						float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation());
						distance -= (this.ship.getCollisionRadius() + other.getCollisionRadius()) * 0.7F;
						float threatRange = 700.0F + (other.getHullSize().ordinal() - 2) * 200.0F;
						BattleNext_ThreatEvalAI threatEvaluator = this.maneuverAPI == null ? null : this.maneuverAPI.getThreatEvaluator();
						if (threatEvaluator != null) {
							BattleNext_ThreatData threatData = threatEvaluator.getThreatData(other);
							if (threatData != null) {
								threatRange = threatData.getThreatRange() + 100.0F;
							}
						}

						float effectiveRange = Math.max(threatRange, queryRange);
						if (distance < effectiveRange) {
							if (other.isFighter()) {
								fighters.add(other);
							} else {
								ships.add(other);
							}
						}
					}
				}
			}
		}

		if (ships.isEmpty()) {
			ships.addAll(fighters);
		}

		return ships;
	}

	public CombatEntityAPI getTargetEntity() {
		return this.targetEntity;
	}

	public boolean hasStayInFrontTag() {
		return this.ship.getWing() != null && this.ship.getWing().getSpec() != null
			? this.ship.getWing().getSpec().hasTag("stay_in_front_of_ship") || this.ship.hasTag("stay_in_front_of_ship")
			: this.ship.hasTag("stay_in_front_of_ship");
	}

	private float getFacingOverride() {
		Object override = this.ship.getAIFlags().getCustom(AIFlags.FACING_OVERRIDE_FOR_MOVE_AND_ESCORT_MANEUVERS);
		return override instanceof Float ? (Float) override : Float.MAX_VALUE;
	}

	// port of engine HullVariantSpec.isNonCombat: carrier or civilian hints and
	// no combat hint
	private static boolean isNonCombatVariant(ShipAPI ship) {
		return (ship.getVariant().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CARRIER)
			|| ship.getVariant().getHints().contains(ShipHullSpecAPI.ShipTypeHints.CIVILIAN))
			&& !ship.getVariant().getHints().contains(ShipHullSpecAPI.ShipTypeHints.COMBAT);
	}

	// port of engine Utils.m_a6e824ce: the angular span a circle of the given
	// radius covers when seen from the given distance
	private static float circumferenceAngleSpan(float radius, float distance) {
		return 2.0F * radius / ((float) (Math.PI * 2) * distance) * 360.0F;
	}
}
