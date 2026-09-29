package data.scripts.ai.vanilla.movement;

import com.fs.starfarer.api.combat.CombatAsteroidAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionAnalysisModule;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.FlockingAI.
 * Feeds nearby entities into the collision analysis and publishes an avoidance
 * heading to the engine AI.
 */
public class BattleNext_FlockingAI {
	private ShipAPI ship;
	private BattleNext_EngineAI engineAI;
	private float maxSpeedFraction = 1.0F;
	private BattleNext_CollisionAnalysisModule collisionAnalysis;
	private float avoidanceRadius = Float.MAX_VALUE;
	private float desiredFacing = Float.MAX_VALUE;
	private float currentMaxSpeed;
	// intentionally kept dead: vanilla never reads this field
	private Vector2f unusedVector2f = null;

	public BattleNext_FlockingAI(ShipAPI ship, BattleNext_EngineAI engineAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.engineAI = engineAI;
		this.collisionAnalysis = new BattleNext_CollisionAnalysisModule(ship, maneuverAPI);
	}

	public void addCollisionObject(CombatEntityAPI entity) {
		if (entity != null) {
			this.collisionAnalysis.addIgnoredObject(entity);
		}
	}

	public void registerCollisionObject(CombatEntityAPI entity) {
		if (entity != null) {
			this.collisionAnalysis.addDangerousObject(entity);
		}
	}

	public void setMaxSpeedFraction(float maxSpeedFraction) {
		this.maxSpeedFraction = maxSpeedFraction;
	}

	public void addCollisionEntityByType(CombatEntityAPI entity) {
		if (entity instanceof ShipAPI) {
			this.collisionAnalysis.addShip((ShipAPI)entity);
		} else if (entity instanceof CombatAsteroidAPI) {
			this.collisionAnalysis.addAsteroid((CombatAsteroidAPI)entity);
		} else if (entity instanceof MissileAPI) {
			this.collisionAnalysis.addMissile((MissileAPI)entity);
		} else {
			// vanilla only routed non-background stars here via Planet.isStar()/isBackgroundPlanet(); star state is not visible through CombatEntityAPI
			this.collisionAnalysis.addPlanet(entity);
		}
	}

	public void addCollisionEntity(CombatEntityAPI entity) {
		this.addCollisionEntityByType(entity);
	}

	public void setDestination(Vector2f destination) {
		this.collisionAnalysis.setHeading(BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), destination));
	}

	public void setAvoidanceRadius(float heading) {
		// vanilla stores a heading in avoidanceRadius and forwards it to the collision heading
		this.avoidanceRadius = heading;
		this.collisionAnalysis.setHeading(heading);
	}

	public float getAvoidanceRadius() {
		return this.avoidanceRadius;
	}

	public void setDesiredFacing(float facing) {
		this.desiredFacing = facing;
	}

	public void updateMaxSpeed(float amount) {
		// Ship.getMaxSpeed() delegates to the engine controller's effective max speed.
		this.currentMaxSpeed = this.ship.getMaxSpeed();
		this.collisionAnalysis.advance(amount);
	}

	public boolean hasCollisionWarning() {
		return this.collisionAnalysis.needsAvoidance();
	}

	public float getCurrentMaxSpeed() {
		return this.currentMaxSpeed;
	}

	public void setCurrentMaxSpeed(float maxSpeed) {
		this.currentMaxSpeed = maxSpeed;
	}

	public void update(float amount) {
		// Ship.getMaxSpeed() delegates to the engine controller's effective max speed.
		float speedFraction = this.currentMaxSpeed / this.ship.getMaxSpeed();
		if (speedFraction > this.maxSpeedFraction) {
			speedFraction = this.maxSpeedFraction;
		}

		if (speedFraction > 1.0F) {
			speedFraction = 1.0F;
		}

		if (speedFraction < 0.0F) {
			speedFraction = 0.0F;
		}

		boolean hasHeading = false;
		float heading = 0.0F;
		if (this.collisionAnalysis.needsAvoidance()) {
			this.collisionAnalysis.computeAvoidanceCourse();
		}

		if (this.collisionAnalysis.hasAvoidanceHeading()) {
			heading = this.collisionAnalysis.getAvoidanceHeading();
			hasHeading = true;
		} else if (this.collisionAnalysis.hasHeading()) {
			heading = this.collisionAnalysis.getHeading();
			hasHeading = true;
		}

		if (hasHeading) {
			this.engineAI.setDesiredHeading(heading, speedFraction);
		} else {
			this.engineAI.setDesiredHeading(this.ship.getFacing(), 0.0F);
		}

		this.engineAI.setDesiredFacing(this.desiredFacing);
		// Ship.getMaxSpeed() delegates to the engine controller's effective max speed.
		this.currentMaxSpeed = this.ship.getMaxSpeed();
	}

	public BattleNext_EngineAI getEngineAI() {
		return this.engineAI;
	}

	public Vector2f getAvoidanceVector() {
		return this.collisionAnalysis.getMissileThreatDirection();
	}

	public Vector2f getAvoidanceLocation() {
		return this.collisionAnalysis.getObjectThreatDirection();
	}

	public CombatEntityAPI getNearestThreat() {
		return this.collisionAnalysis.getThreatEntity();
	}

	public BattleNext_CollisionAnalysisModule getCollisionAnalysis() {
		return this.collisionAnalysis;
	}

	public float getDesiredFacing() {
		return this.desiredFacing;
	}
}
