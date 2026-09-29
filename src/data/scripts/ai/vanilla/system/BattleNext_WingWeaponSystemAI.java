package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.WingWeaponSystemAI.
 */
public class BattleNext_WingWeaponSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final IntervalUtil decisionTracker = new IntervalUtil(0.15F, 0.3F);
	// intentionally kept dead: vanilla constructs this evaluator and never reads it.
	private final BattleNext_FiringSolutionEval eval;

	public BattleNext_WingWeaponSystemAI(ShipAPI ship) {
		this.ship = ship;
		this.system = ship.getSystem();
		this.eval = new BattleNext_FiringSolutionEval(ship);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		decisionTracker.advance(amount);
		if (!decisionTracker.intervalElapsed()) return;
		if (system.isActive()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;

		ShipAPI candidate = BattleNext_ShipAIHelper.getNearestEnemy(ship, false, 1000.0F);
		if (candidate == null) {
			boolean searchNonFighters = true;
			if (ship.getWing() != null && ship.getWing().getWingMembers().size() > 1 && ship.getWing().isLeader(ship)) {
				searchNonFighters = false;
			}
			if (searchNonFighters) {
				candidate = BattleNext_ShipAIHelper.getNearestEnemyInRangeNoFighters(ship, 1000.0F);
			}
		}
		if (candidate == null) return;

		Vector2f targetLocation = candidate.getLocation();
		float radius = candidate.getCollisionRadius() + 75.0F;
		if (candidate.getShield() != null) {
			targetLocation = candidate.getShield().getLocation();
			radius = candidate.getShield().getRadius() + 75.0F;
		}
		float distance = BattleNext_ShipAIHelper.getDistance(targetLocation, ship.getLocation());
		if (distance < radius + 400.0F && distance > radius - 50.0F) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
		}
	}
}
