package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.ThreatAvoidanceManeuver.
 * Faces away from the surrounding threat cluster, biasing the facing by avoidOffset.
 */
public class BattleNext_ThreatAvoidanceManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private float unusedTimeSinceThreat = 0.0F;
	private float avoidOffset = 20.0F + 40.0F * (float) Math.random();
	private float avoidanceFacing;

	public BattleNext_ThreatAvoidanceManeuver(ShipAPI ship, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.maneuverAPI = maneuverAPI;
	}

	public BattleNext_ThreatAvoidanceManeuver(ShipAPI ship, float avoidOffset, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.avoidOffset = avoidOffset;
		this.maneuverAPI = maneuverAPI;
	}

	@Override
	public void advance(float amount) {
		float avoidRange = this.ship.getCollisionRadius() * 5.0F;
		if (this.ship.isFighter()) {
			avoidRange *= 4.0F;
		}

		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		ShipAPI nearestShip = null;
		ArrayList<Float> threatAngles = new ArrayList<>();
		float nearestDistance = Float.MAX_VALUE;

		for (ShipAPI other : ships) {
			if (this.ship.getOwner() != other.getOwner() && !other.isHulk()) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation());
				if (distance < avoidRange) {
					float angle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), other.getLocation());
					threatAngles.add(angle);
				} else if (distance < nearestDistance) {
					nearestDistance = distance;
					nearestShip = other;
				}
			}
		}

		if (!threatAngles.isEmpty()) {
			this.avoidanceFacing = this.computeAvoidanceFacing(threatAngles);
			if (threatAngles.size() == 1) {
				this.avoidanceFacing = this.adjustFacing(this.avoidanceFacing);
			}
		} else if (nearestShip != null) {
			this.avoidanceFacing = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), nearestShip.getLocation()) + 180.0F;
			this.avoidanceFacing = this.adjustFacing(this.avoidanceFacing);
		}

		if (!this.maneuverAPI.getThreatEvaluator().isThreatHigh()) {
			this.unusedTimeSinceThreat += amount;
		} else {
			this.unusedTimeSinceThreat = 0.0F;
		}
	}

	private float adjustFacing(float facing) {
		float offsetUp = BattleNext_ShipAIHelper.getAngleDiff(this.ship.getFacing(), facing + this.avoidOffset);
		float offsetDown = BattleNext_ShipAIHelper.getAngleDiff(this.ship.getFacing(), facing - this.avoidOffset);
		return offsetUp < offsetDown ? facing + this.avoidOffset : facing - this.avoidOffset;
	}

	@Override
	public boolean isDone() {
		return false;
	}

	private float computeAvoidanceFacing(List<Float> angles) {
		if (angles.isEmpty()) {
			angles.add(this.ship.getFacing() + 180.0F);
		}

		return BattleNext_ShipAIHelper.getLargestAngleGapMidpoint(angles);
	}

	public float getAvoidOffset() {
		return this.avoidOffset;
	}

	@Override
	public float getDesiredFacing() {
		return this.avoidanceFacing;
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
		return null;
	}
}
