package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.FaceTargetEntityManeuver.
 * Holds facing on a target entity, widening the offset the closer the target is.
 */
public class BattleNext_FaceTargetEntityManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private CombatEntityAPI target;
	private float targetRange;
	private float turnSpeed;
	private BattleNext_ManeuverAPI maneuverAPI;
	private float targetValue = Float.MAX_VALUE;

	public BattleNext_FaceTargetEntityManeuver(ShipAPI ship, CombatEntityAPI target, float extraRange, float turnSpeed, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.target = target;
		this.targetRange = extraRange + (ship.getCollisionRadius() + target.getCollisionRadius()) * 0.75F;
		this.turnSpeed = turnSpeed;
		this.maneuverAPI = maneuverAPI;
	}

	@Override
	public void advance(float amount) {
		if (this.isTargetValid()) {
			float angleToTarget = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.target.getLocation());
			float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.target.getLocation());
			float maxOffset = 0.0F;
			float range = this.targetRange;
			float scaledRange = range / 0.6F;
			if (distance < scaledRange) {
				float closeness = (scaledRange - distance) / scaledRange;
				closeness *= closeness;
				maxOffset = 180.0F * closeness;
			}

			Vector2f facingVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.ship.getFacing());
			float turn;
			if (this.turnSpeed == 0.0F) {
				turn = -1.0F * BattleNext_ShipAIHelper.getTurnDirection(facingVector, BattleNext_ShipAIHelper.getUnitVectorAtAngle(angleToTarget));
			} else {
				turn = this.turnSpeed;
			}

			this.targetValue = angleToTarget + turn * maxOffset;
		}
	}

	private boolean isTargetValid() {
		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		return this.target != null && ships.contains(this.target);
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public float getDesiredFacing() {
		return this.targetValue;
	}

	@Override
	public void init() {
	}

	@Override
	public float getTargetFacing() {
		return Float.MAX_VALUE;
	}

	@Override
	public CombatEntityAPI getTarget() {
		return this.target;
	}
}
