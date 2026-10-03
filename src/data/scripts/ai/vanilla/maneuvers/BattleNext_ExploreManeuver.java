package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionCheckSegment;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionDangerEntry;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionTimeRange;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.ExploreManeuver.
 * One-shot heading pick at deployment: faces the nearest enemy or the safest
 * arc toward the map interior.
 */
public class BattleNext_ExploreManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private BattleNext_FlockingAI flockingAI;
	private BattleNext_ManeuverAPI maneuverAPI;
	private float exploreFacing = Float.MAX_VALUE;

	public BattleNext_ExploreManeuver(ShipAPI ship, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		CombatEntityAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemyEntity(ship, CollisionClass.SHIP, CollisionClass.FIGHTER);
		if (nearestEnemy != null) {
			this.exploreFacing = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), nearestEnemy.getLocation());
		} else {
			this.exploreFacing = this.computeExploreFacing();
		}

		int escapeOwner = 0;
		if (ship.getOwner() == 0) {
			escapeOwner = 1;
		}

		CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(escapeOwner);
		boolean escapeGoal = fleetManager != null && fleetManager.getGoal() == FleetGoal.ESCAPE;
		// approximation: the engine combat map bounds are taken from the helper,
		// which derives them from the engine map size
		float mapMinX = BattleNext_ShipAIHelper.getMapMinX();
		float mapMinY = BattleNext_ShipAIHelper.getMapMinY();
		float mapWidth = BattleNext_ShipAIHelper.getMapMaxX() - mapMinX;
		float mapHeight = BattleNext_ShipAIHelper.getMapMaxY() - mapMinY;
		if (escapeGoal
			&& ship.getFullTimeDeployed() < 180.0F
			&& ship.getLocation().y < mapMinY + mapHeight * 0.5F
			&& ship.getLocation().x > mapMinX + mapWidth * 0.25F
			&& ship.getLocation().x < mapMinX + mapWidth * 0.75F) {
			this.exploreFacing = 90.0F;
		}

		if (!escapeGoal && ship.getFullTimeDeployed() < 180.0F) {
			if (ship.getOwner() == 0 && ship.getLocation().y < mapMinY + mapHeight * 0.5F) {
				this.exploreFacing = 90.0F;
			} else if (ship.getOwner() == 1 && ship.getLocation().y > mapMinY + mapHeight * 0.5F) {
				this.exploreFacing = 270.0F;
			}
		}
	}

	@Override
	public void advance(float amount) {
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public float getDesiredFacing() {
		return this.exploreFacing;
	}

	@Override
	public void init() {
	}

	@Override
	public float getTargetFacing() {
		return Float.MAX_VALUE;
	}

	private float computeExploreFacing() {
		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		ArrayList<BattleNext_CollisionCheckSegment> segments = new ArrayList<>();

		for (ShipAPI other : ships) {
			if (!other.isShuttlePod() && !other.isHulk() && (!other.isFighter() || other.isWingLeader()) && this.ship != other && this.ship.getOwner() == other.getOwner()) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation());
				if (distance < 5000.0F) {
					BattleNext_CollisionCheckSegment segment = new BattleNext_CollisionCheckSegment(other.getLocation(), other.getVelocity(), other.getCollisionRadius(), other.getFacing());
					float angle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), other.getLocation());
					float span = circumferenceAngleSpan(other.getCollisionRadius(), distance);
					segment.setAngleSpan(span);
					segment.setCenterAngle(angle);
					segments.add(segment);
				}
			}
		}

		// approximation: the engine combat map center is reconstructed from the
		// helper map bounds, then vanilla shifts its x toward the ship
		Vector2f center = new Vector2f(
			(BattleNext_ShipAIHelper.getMapMinX() + BattleNext_ShipAIHelper.getMapMaxX()) / 2.0F,
			(BattleNext_ShipAIHelper.getMapMinY() + BattleNext_ShipAIHelper.getMapMaxY()) / 2.0F
		);
		center.x = this.ship.getLocation().x * 0.5F;
		float centerDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), center);
		float pullRange = 1000.0F;
		if (Vector2f.dot(this.ship.getVelocity(), Vector2f.sub(center, this.ship.getLocation(), new Vector2f())) < 0.0F) {
			pullRange = 3000.0F;
		}

		Vector2f heading;
		if (centerDistance > pullRange) {
			heading = Vector2f.sub(center, this.ship.getLocation(), new Vector2f());
		} else if (this.ship.getVelocity().length() < 10.0F) {
			heading = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.ship.getFacing());
		} else {
			heading = new Vector2f(this.ship.getVelocity());
		}

		float headingAngle = BattleNext_ShipAIHelper.getAngleFast(heading);
		List<BattleNext_CollisionTimeRange> ranges = this.buildDangerSegments(segments);
		float closestDiff = Float.MAX_VALUE;
		float widest = 0.0F;
		BattleNext_CollisionTimeRange bestRange = null;

		for (BattleNext_CollisionTimeRange range : ranges) {
			float diff = BattleNext_ShipAIHelper.getAngleDiff(range.getStartAngle(), headingAngle);
			if (closestDiff > diff && range.getWidth() > 30.0F) {
				closestDiff = diff;
				bestRange = range;
			}
		}

		if (bestRange == null && !ranges.isEmpty()) {
			for (BattleNext_CollisionTimeRange range : ranges) {
				if (widest < range.getWidth()) {
					bestRange = range;
					widest = range.getWidth();
				}
			}
		}

		return bestRange == null ? headingAngle : BattleNext_ShipAIHelper.clampAngleToArc(bestRange.getStartAngle(), bestRange.getWidth() * 0.4F, headingAngle);
	}

	private List<BattleNext_CollisionTimeRange> buildDangerSegments(List<BattleNext_CollisionCheckSegment> segments) {
		ArrayList<BattleNext_CollisionDangerEntry> entries = new ArrayList<>();

		for (BattleNext_CollisionCheckSegment segment : segments) {
			BattleNext_CollisionDangerEntry start = new BattleNext_CollisionDangerEntry(segment, BattleNext_CollisionDangerEntry.DANGER_START, segment.getCenterAngle() - segment.getAngleSpan() / 2.0F);
			BattleNext_CollisionDangerEntry end = new BattleNext_CollisionDangerEntry(segment, BattleNext_CollisionDangerEntry.DANGER_END, segment.getCenterAngle() + segment.getAngleSpan() / 2.0F);
			entries.add(start);
			entries.add(end);
		}

		Collections.sort(entries, new BattleNext_ExploreCollisionComparator(this));
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
		return null;
	}

	// port of engine Utils.m_a6e824ce: the angular span a circle of the given
	// radius covers when seen from the given distance
	private static float circumferenceAngleSpan(float radius, float distance) {
		return 2.0F * radius / ((float) (Math.PI * 2) * distance) * 360.0F;
	}
}
