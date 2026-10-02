package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_BestTargetEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionCheckSegment;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionDangerEntry;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionTimeRange;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.RetreatManeuver.
 * Long-lived retreat maneuver: heads for the retreat map side while avoiding
 * collision arcs and deciding when to face threats.
 * The retreat statics startRetreat, getRetreatSide, getRetreatDirection,
 * canRetreatToSide and distanceToSide keep their vanilla visibility because
 * other engine systems call them.
 */
public class BattleNext_RetreatManeuver implements BattleNext_Maneuver {
	// port of engine enum com.fs.starfarer.combat.MapSide, ordinal order preserved
	public enum MapSide {
		TOP,
		BOTTOM,
		LEFT,
		RIGHT
	}

	private ShipAPI ship;
	private BattleNext_FlockingAI flockingAI;
	private BattleNext_ManeuverAPI maneuverAPI;
	private float retreatFacing = Float.MAX_VALUE;
	private IntervalUtil intervalTracker = new IntervalUtil(0.5F, 1.0F);
	private ShipAPI retreatTarget;
	private DeployedFleetMemberAPI deployedFleetMember;
	private float desiredFacing = Float.MAX_VALUE;

	public BattleNext_RetreatManeuver(ShipAPI ship, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.deployedFleetMember = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner()).getDeployedFleetMember(ship);
		this.intervalTracker.forceIntervalElapsed();
	}

	@Override
	public void advance(float amount) {
		this.intervalTracker.advance(amount);
		if (this.intervalTracker.intervalElapsed()) {
			if (!BattleNext_ShipAIHelper.isTravelDriveOn(this.ship)) {
				startRetreat(this.ship, true);
			}

			if (!BattleNext_ShipAIHelper.isTravelDriveOn(this.ship)) {
				BattleNext_BestTargetEval eval = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
				if (eval != null) {
					this.retreatTarget = eval.getTarget();
				} else {
					if (this.ship.isCruiser() || this.ship.isCapital()) {
						this.retreatTarget = BattleNext_ShipAIHelper.getNearestEnemyCruiserOrCapital(this.ship);
						if (this.retreatTarget != null) {
							float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.retreatTarget.getLocation());
							if (distance > 3000.0F) {
								this.retreatTarget = null;
							}
						}
					}

					if (this.retreatTarget == null) {
						if (this.ship.isFrigate()) {
							this.retreatTarget = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, 3000.0F, true);
							if (this.retreatTarget != null && this.retreatTarget.isFighter() && this.retreatTarget.getWing() != null) {
								ShipAPI sourceShip = this.retreatTarget.getWing().getSourceShip();
								if (sourceShip != null) {
									float distance = BattleNext_ShipAIHelper.getDistance(this.retreatTarget.getLocation(), sourceShip.getLocation());
									if (distance < sourceShip.getCollisionRadius() + 700.0F && sourceShip.isPullBackFighters()) {
										this.retreatTarget = BattleNext_ShipAIHelper.getNearestEnemy(this.ship, true);
									}
								}
							}
						} else {
							this.retreatTarget = BattleNext_ShipAIHelper.getNearestEnemy(this.ship, true);
						}

						if (this.retreatTarget != null) {
							float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.retreatTarget.getLocation());
							if (distance > 3000.0F) {
								this.retreatTarget = null;
							}
						}

						if (this.retreatTarget == null) {
							this.retreatTarget = BattleNext_ShipAIHelper.getNearestEnemyInRangeNoFighters(this.ship, this.ship.getLocation(), Float.MAX_VALUE, true);
							if (this.retreatTarget != null) {
								float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.retreatTarget.getLocation());
								if (distance > 3000.0F) {
									this.retreatTarget = null;
								}
							}
						}
					}

					if (this.retreatTarget == null) {
						this.retreatTarget = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, 3000.0F, false);
					}
				}
			}

			List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
			boolean targetAlive = this.retreatTarget != null && !this.retreatTarget.isHulk() && ships.contains(this.retreatTarget);
			if (!targetAlive) {
				this.retreatTarget = null;
			}
		}

		if (this.retreatTarget != null) {
			float sideDistance = 1000.0F;
			MapSide retreatSide = getRetreatSide(this.ship.getOriginalOwner(), BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner()).getGoal());
			float distance = distanceToSide(this.ship, retreatSide);
			if (distance < sideDistance) {
				this.retreatTarget = null;
			}
		}

		this.retreatFacing = this.computeRetreatFacing();
		this.desiredFacing = this.retreatFacing;
		if (this.retreatTarget != null && this.deployedFleetMember != null && !this.deployedFleetMember.isDirectRetreat()) {
			this.desiredFacing = this.computeThreatRetreatFacing();
			this.executeRetreatMovement();
		}
	}

	public static void startRetreat(ShipAPI ship, boolean checkConditions) {
		MapSide side = getRetreatSide(ship.getOriginalOwner(), BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner()).getGoal());
		boolean canRetreat = canRetreatToSide(ship, side, false);
		canRetreat |= !checkConditions;
		if (canRetreat) {
			ship.turnOnTravelDrive(100.0F);
			float travelFacing = 0.0F;
			switch (side) {
				case TOP:
					travelFacing = 90.0F;
					break;
				case BOTTOM:
					travelFacing = 270.0F;
					break;
				case LEFT:
					travelFacing = 180.0F;
					break;
				case RIGHT:
					travelFacing = 0.0F;
			}

			// approximation: the engine travel-drive heading and linger flag are not
			// exposed on ShipAPI; only turning the drive on is possible
			if (ship.isFighter() && ship.getWing() != null) {
				for (ShipAPI member : ship.getWing().getWingMembers()) {
					if (ship != member) {
						member.turnOnTravelDrive(100.0F);
					}
				}
			}
		}
	}

	public static MapSide getRetreatSide(int owner, FleetGoal goal) {
		if (goal == FleetGoal.ESCAPE) {
			return MapSide.TOP;
		}

		boolean escapeGoal = false;
		int escapeOwner = 0;
		if (owner == 0) {
			escapeOwner = 1;
		}

		CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(escapeOwner);
		if (fleetManager != null && fleetManager.getGoal() == FleetGoal.ESCAPE) {
			escapeGoal = true;
		}

		if (escapeGoal) {
			return MapSide.BOTTOM;
		} else {
			return owner == 0 ? MapSide.BOTTOM : MapSide.TOP;
		}
	}

	public static Vector2f getRetreatDirection(Vector2f location, int owner, FleetGoal goal) {
		Vector2f direction = null;
		if (goal == FleetGoal.ESCAPE) {
			direction = new Vector2f(0.0F, 1.0F);
		} else {
			boolean escapeGoal = false;
			int escapeOwner = 0;
			if (owner == 0) {
				escapeOwner = 1;
			}

			CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(escapeOwner);
			if (fleetManager != null && fleetManager.getGoal() == FleetGoal.ESCAPE) {
				escapeGoal = true;
			}

			if (escapeGoal) {
				direction = new Vector2f(0.0F, -1.0F);
			} else {
				if (owner != 0) {
					return new Vector2f(0.0F, 1.0F);
				}

				direction = new Vector2f(0.0F, -1.0F);
			}
		}

		return direction;
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public float getDesiredFacing() {
		return this.retreatFacing;
	}

	@Override
	public void init() {
	}

	@Override
	public float getTargetFacing() {
		return this.desiredFacing;
	}

	private float computeRetreatFacing() {
		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		ArrayList<BattleNext_CollisionCheckSegment> segments = new ArrayList<>();

		for (ShipAPI other : ships) {
			if (!other.isShuttlePod()
				&& !other.isHulk()
				&& !other.isFighter()
				&& other.getHullSize().ordinal() >= this.ship.getHullSize().ordinal()
				&& this.ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation());
				if (distance < 2000.0F) {
					BattleNext_CollisionCheckSegment segment = new BattleNext_CollisionCheckSegment(other.getLocation(), other.getVelocity(), other.getCollisionRadius(), other.getFacing());
					float angle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), other.getLocation());
					float span = circumferenceAngleSpan(other.getCollisionRadius(), distance);
					segment.setAngleSpan(span);
					segment.setCenterAngle(angle);
					segments.add(segment);
				}
			}
		}

		Vector2f retreatDirection = getRetreatDirection(
			this.ship.getLocation(), this.ship.getOwner(), BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner()).getGoal()
		);
		float retreatAngle = BattleNext_ShipAIHelper.getAngleFast(retreatDirection);
		MapSide actualSide = getRetreatSide(this.ship.getOriginalOwner(), BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner()).getGoal());
		this.addSideDangerSegment(segments, MapSide.TOP, actualSide);
		this.addSideDangerSegment(segments, MapSide.BOTTOM, actualSide);
		this.addSideDangerSegment(segments, MapSide.LEFT, actualSide);
		this.addSideDangerSegment(segments, MapSide.RIGHT, actualSide);
		List<BattleNext_CollisionTimeRange> ranges = this.buildDangerSegments(segments);
		float widest = 0.0F;
		BattleNext_CollisionTimeRange bestRange = null;

		for (BattleNext_CollisionTimeRange range : ranges) {
			if (widest < range.getWidth()) {
				bestRange = range;
				widest = range.getWidth();
			}

			if (BattleNext_ShipAIHelper.isInArc(range.getStartAngle(), range.getWidth(), retreatAngle) && range.getWidth() > 30.0F) {
				bestRange = range;
				widest = range.getWidth();
				break;
			}
		}

		return bestRange == null ? retreatAngle : BattleNext_ShipAIHelper.clampAngleToArc(bestRange.getStartAngle(), bestRange.getWidth() * 0.25F, retreatAngle);
	}

	private void addSideDangerSegment(List<BattleNext_CollisionCheckSegment> segments, MapSide side, MapSide retreatSide) {
		if (side != retreatSide) {
			float distance = distanceToSide(this.ship, side);
			if (distance < 2500.0F) {
				BattleNext_CollisionCheckSegment segment = new BattleNext_CollisionCheckSegment(new Vector2f(), new Vector2f(), 500.0F, 0.0F);
				switch (side) {
					case TOP:
						segment.getLocation().set(this.ship.getLocation().x, BattleNext_ShipAIHelper.getMapMaxY());
						break;
					case BOTTOM:
						segment.getLocation().set(this.ship.getLocation().x, BattleNext_ShipAIHelper.getMapMinY());
						break;
					case LEFT:
						segment.getLocation().set(BattleNext_ShipAIHelper.getMapMinX(), this.ship.getLocation().y);
						break;
					case RIGHT:
						segment.getLocation().set(BattleNext_ShipAIHelper.getMapMaxX(), this.ship.getLocation().y);
				}

				float angle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), segment.getLocation());
				segment.setAngleSpan(170.0F);
				segment.setCenterAngle(angle);
				segments.add(segment);
			}
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

		Collections.sort(entries, new BattleNext_RetreatCollisionComparator(this));
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

	public static boolean canRetreatToSide(ShipAPI ship, MapSide side, boolean ignoreThreats) {
		if (BattleNext_ShipAIHelper.isEngineDisabled(ship)) {
			return false;
		}

		float maxDistance = 2000.0F;
		CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner());
		if (fleetManager != null && fleetManager.getGoal() == FleetGoal.ESCAPE) {
			maxDistance = 2000.0F;
		}

		float distance = distanceToSide(ship, side);
		if (distance > maxDistance) {
			return false;
		}

		float sideFacing = 0.0F;
		switch (side) {
			case TOP:
				sideFacing = 90.0F;
				break;
			case BOTTOM:
				sideFacing = 270.0F;
				break;
			case LEFT:
				sideFacing = 180.0F;
				break;
			case RIGHT:
				sideFacing = 0.0F;
		}

		float facingDiff = BattleNext_ShipAIHelper.getAngleDiff(ship.getFacing(), sideFacing);
		if (facingDiff > 30.0F) {
			return false;
		}

		if (ignoreThreats) {
			return true;
		}

		int threats = 0;
		if (!ship.isFighter()) {
			HullSize hullSize = ship.getHullSize();
			threats = BattleNext_ShipAIHelper.countEnemiesInRange(ship, ship.getLocation(), sideFacing, 60.0F, maxDistance + 500.0F, new BattleNext_RetreatPositionEvaluator(hullSize, true, true, false));
		}

		if (threats > 0) {
			return false;
		}

		boolean lowHitpoints = ship.getHitpoints() < 300.0F * ship.getHullSize().ordinal();
		boolean asteroidThreat = false;
		if (!ship.isFighter() && (ship.isFrigate() || lowHitpoints)) {
			float threat = BattleNext_ShipAIHelper.getAsteroidThreat(ship, ship.getLocation(), ship.getFacing(), 30.0F, maxDistance + 500.0F);
			asteroidThreat = threat >= 100.0F;
		}

		return !asteroidThreat;
	}

	public static float distanceToSide(ShipAPI ship, MapSide side) {
		Vector2f location = ship.getLocation();
		switch (side) {
			case TOP:
				return BattleNext_ShipAIHelper.getMapMaxY() - location.y;
			case BOTTOM:
				return location.y - BattleNext_ShipAIHelper.getMapMinY();
			case LEFT:
				return location.x - BattleNext_ShipAIHelper.getMapMinX();
			case RIGHT:
				return BattleNext_ShipAIHelper.getMapMaxX() - location.x;
			default:
				return 5000.0F;
		}
	}

	@Override
	public ShipAPI getTarget() {
		return this.retreatTarget;
	}

	private void executeRetreatMovement() {
		float fluxThreshold = 0.95F;
		BattleNext_ThreatData maxThreat = this.maneuverAPI.getThreatEvaluator().getMaxThreatData();
		if (maxThreat != null && maxThreat.getShip().getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
			fluxThreshold -= 0.09F * (maxThreat.getShip().getHullSize().ordinal() - this.ship.getHullSize().ordinal() + 1.0F);
		}

		// intentionally kept dead: vanilla overwrites the scaled threshold with 1.0F
		fluxThreshold = 1.0F;
		boolean backOff = this.ship.getFluxTracker().getOverloadTimeRemaining() > 2.0F
			|| this.ship.getFluxTracker().isVenting() && this.ship.getFluxTracker().getTimeToVent() > 2.0F
			|| this.ship.getFluxLevel() > fluxThreshold;
		boolean lowHitpoints = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
		backOff |= lowHitpoints && (!(this.ship.getFluxLevel() < 0.8F) || !isShieldOn(this.ship));
		backOff |= maxThreat != null
			&& !maxThreat.getShip().isFighter()
			&& this.ship.getShield() != null
			&& this.ship.getShield().isOff()
			&& !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.SAFE_FROM_DANGER_TIME);
		backOff |= this.ship.getShield() == null && this.ship.getFluxLevel() > 0.95F;
		backOff &= this.maneuverAPI.getEval() != null
			&& (
				this.maneuverAPI.getEval().hasDetectedIncomingDamage()
					|| this.maneuverAPI.getThreatEvaluator().getTotalThreat() > 0.05F && !this.maneuverAPI.getThreatEvaluator().isAllThreatsValid()
			);
		backOff &= this.maneuverAPI.getThreatEvaluator().getMaxThreatData() != null;
		if (this.ship.getShield() != null && this.ship.getShield().getType() == ShieldAPI.ShieldType.FRONT) {
			backOff &= this.ship.getArmorGrid().getArmorRating() > 50.0F * this.ship.getHullSize().ordinal();
		}

		backOff &= !this.ship.isPhased();
		backOff &= !BattleNext_ShipAIHelper.isPhaseShip(this.ship) || !(this.ship.getFluxLevel() < 0.85F);
		if (maxThreat != null) {
			ShipAPI threatShip = maxThreat.getShip();
			float radiiSum = threatShip.getCollisionRadius() + this.ship.getCollisionRadius();
			float distance = BattleNext_ShipAIHelper.getDistance(threatShip.getLocation(), this.ship.getLocation());
			if (distance + radiiSum > maxThreat.getThreatRange()) {
				backOff = false;
			}
		}

		if (!this.maneuverAPI.getConfig().turnToFaceWithUndamagedArmor) {
			backOff = false;
		}

		if (backOff) {
			boolean unusedAlwaysFalse = true;
			unusedAlwaysFalse = false;
			if (!unusedAlwaysFalse) {
				float bestArmorAngle = this.computeBestArmorAngle(this.ship.getFacing(), this.ship.getShield() != null && this.ship.getShield().getType() == ShieldAPI.ShieldType.FRONT ? 150.0F : 210.0F);
				float armorOffset = bestArmorAngle - this.ship.getFacing();
				this.desiredFacing -= armorOffset;
			}
		}
	}

	private float computeThreatRetreatFacing() {
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
			threatShip = this.retreatTarget;
		}

		// intentionally kept dead: vanilla overwrites the threat fallback with the
		// retreat target
		threatShip = this.retreatTarget;
		BattleNext_ThreatData targetThreat = threatEvaluator.computeThreatData(this.retreatTarget);
		boolean targetIsThreat = targetThreat.isThreat();
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

		float retreatFacing = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), threatShip.getLocation());
		float bestThreatValue = 0.0F;
		float bestThreatAngle = 0.0F;
		boolean targetThreatFree = false;

		for (BattleNext_ThreatData threatData : threatEvaluator.getAllThreatData()) {
			if (threatData.getShip() == threatShip) {
				targetThreatFree = !threatData.isThreat();
			} else {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), threatShip.getLocation());
				float radiiSum = this.ship.getCollisionRadius() + threatData.getShip().getCollisionRadius();
				if (!(threatData.getThreatRange() + radiiSum + 500.0F < distance)) {
					float threatAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), threatData.getShip().getLocation());
					float threatDiff = BattleNext_ShipAIHelper.getAngleDiff(retreatFacing, threatAngle);
					if (threatData.getThreatValue() > bestThreatValue && !threatData.isThreat()) {
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
				float avoidanceDiff = BattleNext_ShipAIHelper.getAngleDiff(retreatFacing, avoidanceAngle);
				if (this.maneuverAPI.getEval().hasSolution() || avoidanceDiff < shieldArc) {
					if ((targetThreatFree || !(avoidanceDiff > shieldArc) || !this.maneuverAPI.getEval().hasSolution()) && !targetIsThreat) {
						bestThreatValue = 1.0F;
						bestThreatAngle = avoidanceAngle;
					} else {
						retreatFacing = avoidanceAngle;
						bestThreatValue = 0.0F;
					}

					if (avoidanceDiff > shieldArc && this.maneuverAPI.getEval().hasSolution() && !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.BACK_OFF)) {
						this.maneuverAPI.getAIFlags().setFlag(AIFlags.BACK_OFF, 0.1F);
					}
				}
			}
		}

		float result = retreatFacing;
		if (bestThreatValue > 0.0F && this.ship.getShield() != null) {
			retreatFacing = BattleNext_ShipAIHelper.normalizeAngle(retreatFacing);
			bestThreatAngle = BattleNext_ShipAIHelper.normalizeAngle(bestThreatAngle);
			float threatDiff = BattleNext_ShipAIHelper.getAngleDiff(retreatFacing, bestThreatAngle);
			float shift = Math.min(threatDiff - shieldArc / 2.0F, shieldArc * 0.5F);
			if (shift < 0.0F) {
				shift = 0.0F;
			}

			result = retreatFacing + shift * BattleNext_ShipAIHelper.getTurnDirection(retreatFacing, bestThreatAngle);
			if (threatDiff > shieldArc && BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship)) {
				result = retreatFacing;
			}
		}

		return result;
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

	// approximation: ShipAPI.isShieldOn is not present in the mod-visible API
	private static boolean isShieldOn(ShipAPI ship) {
		return ship.getShield() != null && ship.getShield().isOn();
	}

	// port of engine Utils.m_a6e824ce: the angular span a circle of the given
	// radius covers when seen from the given distance
	private static float circumferenceAngleSpan(float radius, float distance) {
		return 2.0F * radius / ((float) (Math.PI * 2) * distance) * 360.0F;
	}
}
