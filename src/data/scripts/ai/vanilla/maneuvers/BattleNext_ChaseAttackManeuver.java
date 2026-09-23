package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.ChaseAttackManeuver.
 * Closes on a ship target and holds the attack run inside the attack range band.
 */
public class BattleNext_ChaseAttackManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private ShipAPI targetShip;
	private BattleNext_ManeuverAPI maneuverAPI;
	private float attackRange;
	private float rangeTolerance;
	private BattleNext_FlockingAI flockingAI;

	public BattleNext_ChaseAttackManeuver(ShipAPI ship, ShipAPI targetShip, float desiredRange, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		float radiiSum = (ship.getCollisionRadius() + targetShip.getCollisionRadius()) * 0.75F;
		this.flockingAI = flockingAI;
		this.attackRange = desiredRange + radiiSum;
		this.rangeTolerance = radiiSum / 2.0F;
		this.ship = ship;
		this.targetShip = targetShip;
		this.maneuverAPI = maneuverAPI;
	}

	@Override
	public void advance(float amount) {
		if (BattleNext_ShipAIHelper.isShipAboutToBeHit(this.ship, this.targetShip)) {
			this.flockingAI.addCollisionObject(this.targetShip);
		}
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public CombatEntityAPI getTarget() {
		return this.targetShip;
	}

	@Override
	public float getDesiredFacing() {
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
		return distance < this.attackRange
			? BattleNext_ShipAIHelper.getAngleFast(this.targetShip.getLocation(), this.ship.getLocation())
			: BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.targetShip.getLocation());
	}

	@Override
	public void init() {
		float lead = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null) {
			lead = this.maneuverAPI.getAttackingGroup().getRange();
		} else if (this.maneuverAPI.getAttackAI() != null) {
			lead = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetShip, lead, !this.maneuverAPI.getAttackAI().isAttackGroupReady());
		float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.targetShip);
		BattleNext_ShipAIHelper.turnToFace(this.ship, aimPoint, predictedAngleChange);
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), this.targetShip.getLocation());
		boolean worthAttacking = BattleNext_ShipAIHelper.isShipWorthAttacking(this.ship, this.targetShip, this.maneuverAPI);
		if (distance < this.attackRange - this.rangeTolerance / 2.0F) {
			this.ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, -1);
		} else if ((!(distance > this.attackRange + this.rangeTolerance / 2.0F) || worthAttacking) && !(distance > this.attackRange)) {
			this.ship.giveCommand(ShipCommand.DECELERATE, null, -1);
		} else {
			this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
		}
	}

	@Override
	public float getTargetFacing() {
		return Float.MAX_VALUE;
	}
}
