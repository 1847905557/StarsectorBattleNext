package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.BomberWeaponSystemAI.
 */
public class BattleNext_BomberWeaponSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_FiringSolutionEval firingSolutionEval;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private final List<WeaponAPI> systemWeapons = new ArrayList<>();
	private final float weaponRange;

	public BattleNext_BomberWeaponSystemAI(ShipAPI ship, BattleNext_ThreatEvalAI threatEval) {
		this.ship = ship;
		this.system = ship.getSystem();
		this.threatEval = threatEval;
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship);
		float range = 0.0F;
		// WeaponSystem creates its weapons in hull system slots.
		for (WeaponAPI weapon : ship.getAllWeapons()) {
			if (!weapon.getSlot().isSystemSlot()) continue;
			systemWeapons.add(weapon);
			range = Math.max(range, weapon.getRange());
		}
		this.weaponRange = range;
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		if (system.isActive()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		boolean bomber = ship.isFighter() && ship.getWing() != null && ship.getWing().getSpec().isBomber();
		if (missileDangerDir == null && !bomber) return;
		boolean activeWeapon = system.getId().contains("active");
		boolean unlimitedAmmo = !system.getSpecAPI().usesAmmo();
		if (!firingSolutionEval.hasFiringSolution(1000.0F, new BattleNext_FiringSolutionEval.SolutionResult())
				&& system.getAmmo() <= 3) return;

		if (ship.getShield() != null && ship.getShield().isOn() && !unlimitedAmmo
				&& ship.getFluxLevel() < 0.9F && missileDangerDir != null
				&& BattleNext_ShipAIHelper.isInArc(ship.getShield().getFacing(), ship.getShield().getActiveArc(),
						BattleNext_ShipAIHelper.getAngleFast(missileDangerDir))) {
			BattleNext_ThreatData maximum = threatEval.getMaxThreatData();
			if (maximum == null || !threatEval.isThreatHigh()
					|| BattleNext_ShipAIHelper.isInArc(ship.getShield().getFacing(), ship.getShield().getActiveArc(),
						ship.getLocation(), maximum.getShip().getLocation())) return;
		}

		float range = weaponRange + ship.getCollisionRadius() + (activeWeapon ? 500.0F : 0.0F);
		Iterator<?> missiles = BattleNext_ShipAIHelper.getEngine().getAiGridMissiles()
				.getCheckIterator(ship.getLocation(), range * 2.0F, range * 2.0F);
		while (missiles.hasNext()) {
			Object next = missiles.next();
			if (!(next instanceof MissileAPI missile)) continue;
			if (missile.getOwner() == ship.getOwner() || missile.isFlare() || missile.isFizzling()) continue;
			if (missile.getSpec() != null && !missile.getSpec().isRenderTargetIndicator()) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), missile.getLocation());
			if (activeWeapon) distance -= BattleNext_ShipAIHelper.getClosingSpeed(ship, missile);
			if (distance > range) continue;
			float arc = activeWeapon ? 360.0F : missile.isGuided() ? 180.0F : 90.0F;
			for (WeaponAPI weapon : systemWeapons) {
				float angle = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), missile.getLocation());
				if (BattleNext_ShipAIHelper.isInArc(weapon.getSlot().computeMidArcAngle(ship), arc, angle)) {
					ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
					return;
				}
			}
		}
	}
}
