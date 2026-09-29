package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.MoteControlScript;
import com.fs.starfarer.api.impl.combat.MoteControlScript.SharedMoteAIData;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.MoteControlSystemAI.
 */
public class BattleNext_MoteControlSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipwideAIFlags flags;
	private final BattleNext_ThreatEvalAI threatEval;
	private final ShipSystemAPI system;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	// intentionally kept dead: vanilla configures this evaluator and never reads it.
	private final BattleNext_FiringSolutionEval firingSolutionEval;
	private float timeSinceUse = 10000.0F;

	public BattleNext_MoteControlSystemAI(ShipAPI ship, ShipwideAIFlags flags, BattleNext_ThreatEvalAI threatEval) {
		this.ship = ship;
		this.flags = flags;
		this.threatEval = threatEval;
		this.system = ship.getSystem();
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship, flags);
		this.firingSolutionEval.setUseBurstDps(true);
		this.firingSolutionEval.setAssumeFiring(true);
		this.firingSolutionEval.setAlwaysEvaluateDamage(true);
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		timeSinceUse += amount;
		if (!interval.intervalElapsed()) return;
		if (system.getCooldownRemaining() > 0.0F) return;
		if (system.isOutOfAmmo()) return;
		if (ship.getFluxTracker().isOverloadedOrVenting()) return;
		FogOfWarAPI fog = BattleNext_ShipAIHelper.getEngine().getFogOfWar(ship.getOriginalOwner());
		if (target != null && fog != null && !fog.isVisible(target)) target = null;
		boolean critical = false;
		ShipAPI maximumThreat = threatEval.getMaxThreatData() == null ? null : threatEval.getMaxThreatData().getShip();
		if (maximumThreat != null && flags.hasFlag(AIFlags.IN_CRITICAL_DPS_DANGER)) {
			target = maximumThreat;
			critical = true;
		}
		if (target == null || target.isHulk() || target.isFighter()) return;
		MoteControlScript script = (MoteControlScript) system.getScript();
		Vector2f targetLocation = target.getLocation();
		SharedMoteAIData shared = MoteControlScript.getSharedData(ship);
		float motes = shared.motes.size();
		float maxMotes = MoteControlScript.getMaxMotes(ship);
		float fluxFraction = system.getFluxPerUse() / ship.getMaxFlux();
		if (ship.getFluxLevel() + fluxFraction >= 0.9999F && fluxFraction > 0.0F) return;

		float enemyThreat = computeEnemyThreat(ship);
		int incomingMissiles = BattleNext_ShipAIHelper.getNumberOfIncomingMissiles(ship, 3.0F);
		if (enemyThreat + incomingMissiles > 0.0F && ship.getFluxLevel() > 0.85F && timeSinceUse > 5.0F) {
			if (shared.attractorLock != null) activateSystem(ship.getLocation());
			return;
		}
		float moteFraction = maxMotes > 0.0F ? motes / maxMotes : 1.0F;
		if (timeSinceUse > 10.0F && moteFraction > 0.5F) {
			ShipAPI ally = findBestAllyTarget();
			if (ally != null) {
				Object reportedThreat = ally.getAIFlags().getCustom(AIFlags.BIGGEST_THREAT);
				// approximation: native AI stores an internal ThreatData instance;
				// only ported threat data exposes the ship through this class.
				BattleNext_ThreatData threat = reportedThreat instanceof BattleNext_ThreatData
						? (BattleNext_ThreatData) reportedThreat : null;
				int incoming = BattleNext_ShipAIHelper.getNumberOfIncomingMissiles(ally, 10.0F);
				if (incoming > 0 || threat != null) {
					if (incoming > 0 || threat == null || threat.getShip() == null || moteFraction < 0.8F) {
						activateSystem(ally.getLocation());
						return;
					}
					activateSystem(threat.getShip().getLocation());
					return;
				}
			}
		}
		if ((critical && timeSinceUse > 5.0F || timeSinceUse > 20.0F)
				&& moteFraction >= 0.8F && script.getLockTarget(ship, targetLocation) == target) {
			activateSystem(targetLocation);
		}
	}

	public void activateSystem(Vector2f location) {
		timeSinceUse = 0.0F;
		ship.giveCommand(ShipCommand.USE_SYSTEM, location, -1);
	}

	protected ShipAPI findBestAllyTarget() {
		CombatFleetManagerAPI fleet = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner());
		float range = MoteControlScript.getRange(ship);
		ShipAPI best = null;
		float bestThreat = 0.0F;
		for (DeployedFleetMemberAPI member : fleet.getDeployedCopyDFM()) {
			ShipAPI ally = member.getShip();
			if (ally == ship || ally.isFighter() || ally.getVariant().isCarrier()) continue;
			float distance = BattleNext_ShipAIHelper.getDistance(ally.getLocation(), ship.getLocation()) - ally.getCollisionRadius();
			if (distance > range) continue;
			float threat = computeTargetThreat(ally, 2000.0F) + computeEnemyThreat(ally) * 3.0F;
			if (BattleNext_ShipAIHelper.getTimeToRecover(ally) >= 4.0F) threat *= 1.5F;
			if (threat > BattleNext_ShipAIHelper.getThreat(ally) && threat > bestThreat) {
				best = ally;
				bestThreat = threat;
			}
		}
		return best;
	}

	protected float computeTargetThreat(ShipAPI target, float range) {
		float friendly = BattleNext_ShipAIHelper.getTotalThreatInRange(target, 0.0F, 360.0F, range, true, true)
				+ BattleNext_ShipAIHelper.getThreat(target);
		float total = BattleNext_ShipAIHelper.getTotalThreatInRange(target, 0.0F, 360.0F, range, false);
		return total - friendly;
	}

	protected float computeEnemyThreat(ShipAPI target) {
		int enemyOwner = target.getOriginalOwner() == 0 ? 1 : 0;
		CombatFleetManagerAPI enemyFleet = BattleNext_ShipAIHelper.getEngine().getFleetManager(enemyOwner);
		float threat = 0.0F;
		for (DeployedFleetMemberAPI member : enemyFleet.getDeployedCopyDFM()) {
			ShipAPI other = member.getShip();
			if (other.isFighter()) {
				if (BattleNext_ShipAIHelper.getDistance(other.getLocation(), target.getLocation())
						< 1000.0F + target.getCollisionRadius()) threat += 0.5F;
			} else if (!other.getLaunchBaysCopy().isEmpty()
					&& target == BattleNext_ShipAIHelper.getCarrierTarget(other)) {
				threat += other.getLaunchBaysCopy().size();
			}
		}
		return threat;
	}
}
