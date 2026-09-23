package data.scripts.ai.vanilla.collisions;

import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.collisions.CollisionCheckSegment.
 * One potential collision projected onto the circle around the own ship: the
 * threat entity, its course, and the angular span it blocks over time.
 */
public class BattleNext_CollisionCheckSegment {
	private Vector2f location;
	private Vector2f velocity;
	private float collisionRadius;
	private float centerAngle;
	private float angleSpan;
	private float timeToCollision;
	private float facing;
	private Object entity;

	public BattleNext_CollisionCheckSegment(Vector2f location, Vector2f velocity, float collisionRadius, float facing) {
		this(null, location, velocity, collisionRadius, facing);
	}

	public BattleNext_CollisionCheckSegment(Object entity, Vector2f location, Vector2f velocity, float collisionRadius, float facing) {
		this.entity = entity;
		this.location = location;
		this.velocity = velocity;
		this.collisionRadius = collisionRadius;
		this.facing = facing;
	}

	public Object getEntity() {
		return this.entity;
	}

	public void setEntity(Object entity) {
		this.entity = entity;
	}

	public float getFacing() {
		return this.facing;
	}

	public Vector2f getLocation() {
		return this.location;
	}

	public void setLocation(Vector2f location) {
		this.location = location;
	}

	public Vector2f getVelocity() {
		return this.velocity;
	}

	public void setVelocity(Vector2f velocity) {
		this.velocity = velocity;
	}

	public float getCollisionRadius() {
		return this.collisionRadius;
	}

	public void setCollisionRadius(float collisionRadius) {
		this.collisionRadius = collisionRadius;
	}

	public float getCenterAngle() {
		return this.centerAngle;
	}

	public void setCenterAngle(float centerAngle) {
		this.centerAngle = centerAngle;
	}

	public float getAngleSpan() {
		return this.angleSpan;
	}

	public void setAngleSpan(float angleSpan) {
		this.angleSpan = angleSpan;
	}

	public float getTimeToCollision() {
		return this.timeToCollision;
	}

	public void setTimeToCollision(float timeToCollision) {
		this.timeToCollision = timeToCollision;
	}
}
