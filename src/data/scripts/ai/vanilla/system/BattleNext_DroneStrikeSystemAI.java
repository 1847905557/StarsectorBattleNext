package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.impl.combat.DroneStrikeStatsAIInfoProvider;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.Set;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.DroneStrikeSystemAI.
 */
public class BattleNext_DroneStrikeSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final DroneStrikeStatsAIInfoProvider stats;
	private final IntervalUtil interval = new IntervalUtil(0.125F, 0.25F);
	private final float fluxPerUseFraction;
	private float strikeDelay;

	public BattleNext_DroneStrikeSystemAI(ShipAPI ship) {
		this.ship = ship;
		this.system = ship.getSystem();
		this.stats = (DroneStrikeStatsAIInfoProvider) system.getScript();
		this.fluxPerUseFraction = system.getFluxPerUse() / ship.getMaxFlux();
	}

	public float getIncomingMissileThreat() {
		return BattleNext_ShipAIHelper.getNumberOfIncomingMissiles(ship, 10.0F)
				+ BattleNext_ShipAIHelper.getNumberOfIncomingMissilesInArcRange(ship, 0.0F, 360.0F, 1500.0F);
	}

	public float getEnemyFighterWingCount() {
		float count = 0.0F;
		Set<FighterWingAPI> seen = new HashSet<>();
		for (ShipAPI fighter : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (!fighter.isFighter() || fighter.getOwner() == ship.getOwner()) continue;
			FighterWingAPI wing = fighter.getWing();
			if (wing == null || !seen.add(wing) || wing.getLeader() == null) continue;
			// approximation: internal FighterWing.getLeaderTarget is unavailable;
			// the leader's public ship target identifies its attack order.
			float range = wing.getLeader().getShipTarget() == ship ? 4000.0F : 2000.0F;
			if (BattleNext_ShipAIHelper.getDistance(ship.getLocation(), wing.getLeader().getLocation()) < range) count++;
		}
		return count;
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		strikeDelay -= amount;
		if (strikeDelay > 0.0F) return;
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;
		int maximumDrones = stats.getMaxDrones();
		int currentDrones = stats.getDrones(ship).size();
		if (currentDrones <= 0 || ship.getFluxLevel() + fluxPerUseFraction >= 0.95F) return;
		float range = stats.getMaxRange(ship);
		float bestWingScore = 0.0F;
		FighterWingAPI bestWing = null;
		if (stats.droneStrikeUsefulVsFighters()) {
			Set<FighterWingAPI> seen = new HashSet<>();
			for (ShipAPI fighter : BattleNext_ShipAIHelper.getEngine().getShips()) {
				if (!fighter.isFighter() || fighter.getOwner() == ship.getOwner()) continue;
				FighterWingAPI wing = fighter.getWing();
				if (wing == null || !seen.add(wing) || wing.getLeader() == null) continue;
				Vector2f center = wing.getLeader().getLocation();
				float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), center);
				if (distance > range + 200.0F) continue;
				float score = 0.0F;
				for (ShipAPI member : wing.getWingMembers()) {
					if (BattleNext_ShipAIHelper.getDistance(center, member.getLocation()) <= 300.0F) score++;
				}
				if (wing.getSpec().isBomber()) score *= 2.0F;
				if (distance < 500.0F) score += 3.0F;
				if (score > bestWingScore) {
					bestWingScore = score;
					bestWing = wing;
				}
			}
		}
		float bestShipScore = 0.0F;
		ShipAPI bestShip = null;
		for (ShipAPI enemy : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (enemy.isShuttlePod() || enemy.isFighter() || enemy.getOwner() == ship.getOwner() || enemy.isHulk()) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), enemy.getLocation());
			if (distance > range + enemy.getCollisionRadius() - 50.0F) continue;
			float pdActive = 0.0F;
			float pdTotal = 1.0E-4F;
			for (WeaponAPI weapon : enemy.getAllWeapons()) {
				if (weapon.isDecorative() || !weapon.hasAIHint(AIHints.PD)) continue;
				float weight = switch (weapon.getSize()) {
					case LARGE -> 4.0F;
					case MEDIUM -> 2.0F;
					default -> 1.0F;
				};
				pdTotal += weight;
				if (!weapon.isDisabled()) pdActive += weight;
			}
			float travelTime = distance / stats.getMissileSpeed();
			float closing = BattleNext_ShipAIHelper.getClosingSpeed(ship.getLocation(), enemy.getLocation(),
					new Vector2f(), enemy.getVelocity());
			if (distance - travelTime * closing > range + enemy.getCollisionRadius() - 50.0F) continue;
			boolean overloaded = enemy.getFluxTracker().isOverloadedOrVenting();
			if (overloaded) {
				float remaining = enemy.getFluxTracker().isVenting()
						? enemy.getFluxTracker().getTimeToVent() : enemy.getFluxTracker().getOverloadTimeRemaining();
				if (travelTime > remaining) overloaded = false;
			}
			float damage = BattleNext_ShipAIHelper.getStatisticsTracker(enemy).fitDamageTaken(10.0F).avgX * 10.0F;
			float score = enemy.getShield() == null && enemy.getPhaseCloak() == null ? 3.0F : 1.0F;
			if (pdActive / pdTotal < 0.34F) score += 2.0F;
			if (overloaded) score += 4.0F;
			if ((enemy.getFluxLevel() >= 0.9F || BattleNext_ShipAIHelper.getFluxAvailable(enemy) < 1200.0F)
					&& damage >= enemy.getMaxHitpoints() * 0.2F) score += 3.0F;
			if (enemy == target) score *= 2.0F;
			if (score > bestShipScore) {
				bestShipScore = score;
				bestShip = enemy;
			}
		}
		float incomingMissiles = stats.dronesUsefulAsPD() ? getIncomingMissileThreat() : 0.0F;
		float enemyWings = stats.dronesUsefulAsPD() ? getEnemyFighterWingCount() : 0.0F;
		float replacement = ship.getSharedFighterReplacementRate();
		boolean lowFlux = ship.getFluxLevel() < 0.2F;
		boolean spareFlux = ship.getFluxLevel() + fluxPerUseFraction < 0.8F;
		boolean strikeShip = incomingMissiles <= 0.0F && enemyWings <= 0.0F
				&& replacement >= 1.0F && lowFlux && currentDrones >= maximumDrones;
		if (bestShip != null && !bestShip.getFluxTracker().isOverloadedOrVenting()) {
			float chance = (bestShip.getFluxLevel() - 0.75F);
			if ((float) Math.random() > chance) {
				strikeShip = false;
				strikeDelay = 1.0F;
			}
			if (currentDrones < maximumDrones && maximumDrones > 1) strikeShip = false;
		}
		float wingThreshold = 3.0F + Math.round((1.0F - replacement) * 10.0F);
		if (spareFlux) wingThreshold += 2.0F;
		wingThreshold += incomingMissiles;
		if (currentDrones > maximumDrones - 1) wingThreshold += Math.max(enemyWings - 4.0F, 0.0F);
		wingThreshold += (maximumDrones - currentDrones) * 3.0F;
		boolean strikeWing = bestWingScore >= wingThreshold;
		float shipThreshold = 4.0F + Math.round((1.0F - replacement) * 10.0F);
		if (lowFlux) shipThreshold -= 1.0F;
		if (spareFlux) shipThreshold += 2.0F;
		shipThreshold += incomingMissiles + enemyWings + (maximumDrones - currentDrones) * 3.0F;
		strikeShip |= bestShipScore >= shipThreshold;
		if (strikeWing && bestWingScore > bestShipScore && bestWing != null
				&& (bestWing.getSpec().isBomber()
						|| bestWing.getWingMembers().size() >= 2 && bestWingScore >= 4.0F)) {
			strikeShip(bestWing.getLeader());
			return;
		}
		if (strikeShip && bestShip != null && bestShipScore >= 5.0F) strikeShip(bestShip);
	}

	public void strikeShip(ShipAPI target) {
		stats.setForceNextTarget(target);
		ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
	}
}
