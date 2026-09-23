package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.HoldPositionManeuver.
 * Drives back to the ship's fixed anchor point and stops there.
 */
public class BattleNext_HoldPositionManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private BattleNext_ManeuverAPI maneuverAPI;
	private final BattleNext_FlockingAI flockingAI;
	private float elapsedTime = 0.0F;

	public BattleNext_HoldPositionManeuver(ShipAPI ship, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
	}

	@Override
	public void advance(float amount) {
		this.elapsedTime += amount;
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public CombatEntityAPI getTarget() {
		return null;
	}

	@Override
	public float getDesiredFacing() {
		return Float.MAX_VALUE;
	}

	@Override
	public void init() {
		Vector2f fixedLocation = this.ship.getFixedLocation();
		if (fixedLocation != null) {
			float distance = BattleNext_ShipAIHelper.getDistance(fixedLocation, this.ship.getLocation());
			if (distance < 20.0F && this.ship.getVelocity().length() > 3.0F) {
				this.ship.giveCommand(ShipCommand.DECELERATE, null, -1);
			} else if (distance > 3.0F) {
				float heading = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), fixedLocation);
				BattleNext_ShipAIHelper.giveMoveCommands(this.ship, heading, 1000.0F);
				this.elapsedTime = 0.0F;
			}
		}
	}

	@Override
	public float getTargetFacing() {
		return Float.MAX_VALUE;
	}
}
