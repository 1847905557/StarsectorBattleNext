package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.TargetScanWeaponSystemAI.
 */
public class BattleNext_TargetScanWeaponSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final IntervalUtil decisionTracker = new IntervalUtil(0.25F, 0.5F);
	private final BattleNext_FiringSolutionEval eval;
	private final float missileFlightTime;
	private final float scanRange;

	public BattleNext_TargetScanWeaponSystemAI(ShipAPI ship) {
		this.ship = ship;
		this.system = ship.getSystem() != null ? ship.getSystem() : ship.getPhaseCloak();
		this.eval = new BattleNext_FiringSolutionEval(ship);
		float flightTime = 1.0F;
		float weaponRange = 0.0F;
		// WeaponSystem creates its weapons in hull system slots.
		for (WeaponAPI weapon : ship.getAllWeapons()) {
			if (!weapon.getSlot().isSystemSlot()) continue;
			weaponRange = Math.max(weaponRange, weapon.getRange());
			if (weapon.getSpec().getProjectileSpec() instanceof MissileSpecAPI missileSpec) {
				flightTime = missileSpec.getMaxFlightTime() * 0.5F;
			}
		}
		this.missileFlightTime = flightTime;
		this.scanRange = ship.getCollisionRadius() * 0.75F + (weaponRange > 0.0F ? weaponRange : 500.0F);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		decisionTracker.advance(amount);
		if (!decisionTracker.intervalElapsed()) return;
		if (system.isActive()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;

		float immediateTargets = 0.0F;
		float laterTargets = 0.0F;
		for (ShipAPI other : Global.getCombatEngine().getShips()) {
			if (other.isHulk() || other.getOwner() == ship.getOwner() || !other.isTargetable() || !other.isFighter()) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), other.getLocation());
			if (distance < scanRange) {
				immediateTargets++;
			} else {
				float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(ship, other);
				float arrivalTime = (distance - scanRange) / Math.max(closingSpeed, 1.0F);
				if (closingSpeed >= other.getVelocity().length() * 0.5F) {
					if (arrivalTime <= missileFlightTime) immediateTargets++;
					else laterTargets++;
				}
			}
		}
		boolean fighterPressure = immediateTargets > laterTargets || immediateTargets > 3.0F;
		boolean firingSolution = eval.hasFiringSolution(1000.0F, new BattleNext_FiringSolutionEval.SolutionResult())
				&& missileDangerDir != null;
		if (fighterPressure || firingSolution) {
			ShipCommand command = ship.getPhaseCloak() == system
					? ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK : ShipCommand.USE_SYSTEM;
			ship.giveCommand(command, null, -1);
		}
	}
}
