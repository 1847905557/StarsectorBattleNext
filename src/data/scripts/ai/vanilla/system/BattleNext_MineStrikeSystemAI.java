package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.impl.combat.MineStrikeStatsAIInfoProvider;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.TimeoutTracker;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.Set;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.MineStrikeSystemAI.
 */
public class BattleNext_MineStrikeSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.125F, 0.25F);
	private final TimeoutTracker<FighterWingAPI> wingTimeout = new TimeoutTracker<>();
	private final float fluxPerUseFraction;
	private final float fuseTime;
	private final float mineRange;

	public BattleNext_MineStrikeSystemAI(ShipAPI ship, ShipwideAIFlags flags) {
		this.ship = ship;
		this.flags = flags;
		this.system = ship.getSystem();
		this.fluxPerUseFraction = system.getFluxPerUse() / ship.getMaxFlux();
		MineStrikeStatsAIInfoProvider info = (MineStrikeStatsAIInfoProvider) system.getScript();
		this.fuseTime = info.getFuseTime();
		this.mineRange = info.getMineRange(ship);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		wingTimeout.advance(amount);
		if (!interval.intervalElapsed()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;
		if (ship.getFluxLevel() + fluxPerUseFraction >= 0.95F) return;

		int enemyOwner = ship.getOwner() == 0 ? 1 : 0;
		CombatFleetManagerAPI enemyFleet = BattleNext_ShipAIHelper.getEngine().getFleetManager(enemyOwner);
		if (enemyFleet == null) return;
		float bestWingScore = 0.0F;
		Vector2f wingPoint = null;
		FighterWingAPI bestWing = null;
		Set<FighterWingAPI> seen = new HashSet<>();
		// approximation: DeployedFleetMemberAPI does not expose fighter-wing
		// members; active fighter entities are grouped by their public wing.
		for (ShipAPI fighter : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (!fighter.isFighter() || fighter.getOwner() == ship.getOwner()) continue;
			FighterWingAPI wing = fighter.getWing();
			if (wing == null || !seen.add(wing) || wing.getLeader() == null) continue;
			Vector2f center = wing.getLeader().getLocation();
			float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), center);
			if (distance > mineRange + 200.0F) continue;
			float score = 0.0F;
			for (ShipAPI member : wing.getWingMembers()) {
				if (BattleNext_ShipAIHelper.getDistance(center, member.getLocation()) <= 300.0F) score++;
			}
			if (score <= 0.0F) continue;
			if (wing.getSpec().isBomber()) score *= 2.0F;
			if (distance < 500.0F) score += 3.0F;
			score -= wingTimeout.getRemaining(wing);
			if (score <= bestWingScore) continue;
			ShipAPI leader = wing.getLeader();
			float lead = Math.min(Math.max(distance, 250.0F + ship.getCollisionRadius()),
					leader.getMaxSpeed() * fuseTime);
			Vector2f leadPoint = pointAlongVelocity(center, leader.getVelocity(), lead);
			if (leader.getShipTarget() == ship) {
				Vector2f towardShip = pointInDirection(center,
						BattleNext_ShipAIHelper.getAngleFast(center, ship.getLocation()), lead);
				Vector2f.add(leadPoint, towardShip, leadPoint);
				leadPoint.scale(0.5F);
			}
			Vector2f safe = findPointAwayFromFriendlies(leadPoint, center);
			if (safe != null) {
				bestWingScore = score;
				bestWing = wing;
				wingPoint = safe;
			}
		}

		float bestShipScore = 0.0F;
		Vector2f shipPoint = null;
		for (ShipAPI enemy : BattleNext_ShipAIHelper.getEngine().getShips()) {
			if (enemy.isShuttlePod() || enemy.isFighter() || enemy.getOwner() == ship.getOwner() || enemy.isHulk()) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), enemy.getLocation());
			if (distance > mineRange + enemy.getCollisionRadius() + 100.0F) continue;
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
			float score = 1.0F;
			if (pdActive / pdTotal < 0.34F) score += 2.0F;
			if (enemy.getFluxTracker().isOverloadedOrVenting()) score += 4.0F;
			// approximation: native damage history exposes sumX; the ported
			// tracker exposes its average over the same ten-second window.
			float damage = BattleNext_ShipAIHelper.getStatisticsTracker(enemy).fitDamageTaken(10.0F).avgX * 10.0F;
			if (enemy.getFluxLevel() >= 0.75F && damage >= enemy.getMaxHitpoints() * 0.2F) score += 3.0F;
			if (enemy == target) score *= 2.0F;
			if (score <= bestShipScore) continue;
			float radius = enemy.getCollisionRadius() + 100.0F;
			Vector2f center = enemy.getLocation();
			Vector2f ahead = pointAlongVelocity(center, enemy.getVelocity(), radius);
			Vector2f behind = pointInDirection(center, enemy.getFacing() + 180.0F, radius);
			Vector2f safe = enemy.isRetreating() || (float) Math.random() > 0.5F
					? findPointAwayFromFriendlies(ahead, center, behind)
					: findPointAwayFromFriendlies(behind, ahead, center);
			if (safe != null) {
				bestShipScore = score;
				shipPoint = safe;
			}
		}
		boolean fullAmmo = system.getAmmo() == system.getMaxAmmo();
		if (bestWingScore > bestShipScore && bestWing != null && bestWingScore > 0.0F
				&& (bestWing.getSpec().isBomber() || bestWing.getWingMembers().size() >= 2 || fullAmmo)) {
			useSystemAt(wingPoint);
			wingTimeout.add(bestWing, fuseTime + 5.0F);
			return;
		}
		if (shipPoint != null && bestShipScore > 0.0F && (fullAmmo || bestShipScore >= 5.0F)) {
			useSystemAt(shipPoint);
		}
	}

	public Vector2f findPointAwayFromFriendlies(Vector2f... points) {
		for (Vector2f point : points) {
			if (isPointAwayFromFriendlies(point, 500.0F)) return point;
		}
		return null;
	}

	public Vector2f pointAlongVelocity(Vector2f location, Vector2f velocity, float distance) {
		return pointInDirection(location, BattleNext_ShipAIHelper.getAngleFast(velocity), distance);
	}

	public Vector2f pointInDirection(Vector2f location, float angle, float distance) {
		Vector2f offset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(angle);
		offset.scale(distance);
		return Vector2f.add(location, offset, null);
	}

	public void useSystemAt(Vector2f point) {
		flags.setFlag(AIFlags.SYSTEM_TARGET_COORDS, 1.0F, point);
		ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
	}

	private boolean isPointAwayFromFriendlies(Vector2f point, float margin) {
		CombatFleetManagerAPI fleet = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOwner());
		if (fleet == null) return true;
		for (DeployedFleetMemberAPI member : fleet.getDeployedCopyDFM()) {
			if (member.isFighterWing() || member.getShip() == null) continue;
			float allowed = member.getShip() == ship ? 200.0F : margin;
			if (BattleNext_ShipAIHelper.getDistance(point, member.getShip().getLocation())
					<= allowed + member.getShip().getCollisionRadius()) return false;
		}
		return true;
	}
}
