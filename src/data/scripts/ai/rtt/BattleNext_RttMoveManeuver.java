package data.scripts.ai.rtt;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import data.scripts.ai.vanilla.BattleNext_BestTargetEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.maneuvers.BattleNext_Maneuver;
import org.lwjgl.util.vector.Vector2f;
import java.util.function.Supplier;

public class BattleNext_RttMoveManeuver implements BattleNext_Maneuver {

	private final ShipAPI ship;
	private final Supplier<Vector2f> destination;
	private ShipAPI defenseTarget;

	public BattleNext_RttMoveManeuver(ShipAPI ship, Supplier<Vector2f> destination) {
		this.ship = ship;
		this.destination = destination;
	}

	@Override
	public void advance(float amount) {
		move();
	}

	@Override
	public boolean isDone() {
		return Vector2f.sub(destination.get(), ship.getLocation(), null).length() > BattleNext_RttShipAI.ARRIVAL_RANGE;
	}

	@Override
	public void init() {
		move();
	}

	private void move() {
		ship.giveCommand(ShipCommand.DECELERATE, null, -1);
		BattleNext_BestTargetEval evaluation = BattleNext_ShipAIHelper.getBestManeuverTarget(ship);
		defenseTarget = evaluation != null ? evaluation.getTarget()
				: BattleNext_ShipAIHelper.getNearestEnemyInRangeIncludingFighters(ship, 2200.0F);
		if (defenseTarget != null) {
			ship.getAIFlags().setFlag(AIFlags.MANEUVER_TARGET, 1.0F, defenseTarget);
		}
	}

	@Override
	public float getDesiredFacing() {
		return Float.MAX_VALUE;
	}

	@Override
	public float getTargetFacing() {
		return defenseTarget == null ? Float.MAX_VALUE
				: BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), defenseTarget.getLocation());
	}

	@Override
	public CombatEntityAPI getTarget() {
		return defenseTarget;
	}
}
