package data.scripts.plugin;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import data.model.fleet.BattleNext_FleetModel;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.report.BattleNext_DamageAttribution;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tools.BattleNext_Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_ScorekeeperPlugin extends BaseEveryFrameCombatPlugin implements DamageListener {

	private final List<ScoreTrackedFleetMember> trackedMembers = new ArrayList<>();
	private final Map<FleetMemberAPI, ScoreTrackedFleetMember> membersByFleetMember = new IdentityHashMap<>();

	private CombatEngineAPI engine;
	private BattleNext_BattleReportModel frozenReport;
	private boolean battleStarted;

	public BattleNext_ScorekeeperPlugin(BattleNext_AssembledFleet assembledPlayerFleet, BattleNext_AssembledFleet assembledEnemyFleet) {
		registerMembers(assembledPlayerFleet, true);
		registerMembers(assembledEnemyFleet, false);
	}

	public BattleNext_ScorekeeperPlugin(List<FleetMemberAPI> playerMembers, List<FleetMemberAPI> enemyMembers) {
		registerMembers(playerMembers, true);
		registerMembers(enemyMembers, false);
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		engine.getListenerManager().addListener(this);
		engine.getCustomData().put(getCustomDataKey(), this);
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null || frozenReport != null) return;
		refreshShips();
		if (engine.isPaused()) return;
		if (!battleStarted) {
			if (!isReadyToStart()) return;
			battleStarted = true;
			BattleNext_Logger.info(BattleNext_ScorekeeperPlugin.class, "battle.started",
				BattleNext_Logger.fields().put("ships", engine.getShips()).build());
		}
		if (battleStarted) {
			String winner = determineWinner();
			if (winner != null) {
				freeze(winner);
			}
		}
	}

	@Override
	public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
		if (frozenReport != null || result == null || !(target instanceof ShipAPI targetShip)) return;
		if (result.getDamageToHull() <= 0f && result.getTotalDamageToArmor() <= 0f
				&& result.getDamageToShields() <= 0f && result.getEmpDamage() <= 0f) return;
		Object damageParam = targetShip.getParamAboutToApplyDamage();
		BattleNext_DamageAttribution.SourceKey key = BattleNext_DamageAttribution.sourceKey(source, damageParam, result);
		if (!targetShip.isFighter()) {
			ScoreTrackedFleetMember targetMember = findTracked(BattleNext_DamageAttribution.memberShip(targetShip));
			if (targetMember != null) {
				targetMember.report.addDamageTaken(key.kind(), key.id(), result.getDamageToHull(),
						result.getTotalDamageToArmor(), result.getDamageToShields(), result.getEmpDamage());
			}
		}
		ShipAPI sourceShip = BattleNext_DamageAttribution.memberShip(
				BattleNext_DamageAttribution.sourceShip(source, damageParam));
		ScoreTrackedFleetMember sourceMember = findTracked(sourceShip);
		if (sourceMember != null) {
			sourceMember.report.addDamageDealt(key.kind(), key.id(), result.getDamageToHull(),
					result.getTotalDamageToArmor(), result.getDamageToShields(), result.getEmpDamage());
		}
	}

	public BattleNext_BattleReportModel getFrozenReport() {
		return frozenReport;
	}

	public boolean isFrozen() {
		return frozenReport != null;
	}

	public String getCustomDataKey() {
		return "BattleNext_Scorekeeper";
	}

	private void registerMembers(BattleNext_AssembledFleet assembledFleet, boolean playerSide) {
		BattleNext_FleetModel definition = assembledFleet.getDefinition();
		List<FleetMemberAPI> members = assembledFleet.getMembers();
		for (int index = 0; index < members.size(); index++) {
			String variantId = definition.getMembers().get(index).getVariantId();
			String name = definition.getMembers().get(index).getName();
			ScoreTrackedFleetMember tracked = new ScoreTrackedFleetMember(variantId, name, members.get(index), playerSide);
			trackedMembers.add(tracked);
			membersByFleetMember.put(members.get(index), tracked);
		}
	}

	private void registerMembers(List<FleetMemberAPI> members, boolean playerSide) {
		for (FleetMemberAPI member : members) {
			String variantId = member.getVariant().getHullVariantId();
			ScoreTrackedFleetMember tracked = new ScoreTrackedFleetMember(variantId, member.getShipName(), member, playerSide);
			trackedMembers.add(tracked);
			membersByFleetMember.put(member, tracked);
		}
	}

	private void refreshShips() {
		for (ShipAPI ship : engine.getShips()) {
			FleetMemberAPI member = ship.getFleetMember();
			ScoreTrackedFleetMember tracked = membersByFleetMember.get(member);
			if (tracked != null && !ship.isStationModule() && !ship.isFighter()) {
				tracked.ship = ship;
				tracked.everDeployed = true;
			}
		}
	}

	private boolean isReadyToStart() {
		for (ScoreTrackedFleetMember tracked : trackedMembers) {
			if (!tracked.member.isFighterWing() && tracked.ship == null) return false;
		}
		return hasAliveShips(FleetSide.PLAYER) && hasAliveShips(FleetSide.ENEMY);
	}

	private String determineWinner() {
		boolean playerAlive = hasAliveShips(FleetSide.PLAYER);
		boolean enemyAlive = hasAliveShips(FleetSide.ENEMY);
		if (playerAlive && enemyAlive) return null;
		if (playerAlive) return "player";
		if (enemyAlive) return "enemy";
		return "draw";
	}

	private boolean hasAliveShips(FleetSide side) {
		for (ScoreTrackedFleetMember tracked : trackedMembers) {
			if (tracked.ship != null && tracked.ship.getOwner() == side.ordinal() && tracked.ship.isAlive()
					&& !tracked.ship.isFighter()) return true;
		}
		return false;
	}

	public void freeze(String winner) {
		if (frozenReport != null) return;
		refreshShips();
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setWinner(winner);
		report.setDurationMillis((long)(engine.getTotalElapsedTime(false) * 1000f));
		for (ScoreTrackedFleetMember tracked : trackedMembers) {
			tracked.freeze();
			if (tracked.playerSide) {
				report.addPlayerMember(tracked.report);
			} else {
				report.addEnemyMember(tracked.report);
			}
		}
		frozenReport = report;
		BattleNext_Logger.info(BattleNext_ScorekeeperPlugin.class, "battle.result.frozen",
				BattleNext_Logger.fields().put("report", frozenReport).build());
	}

	private ScoreTrackedFleetMember findTracked(ShipAPI ship) {
		return ship == null ? null : membersByFleetMember.get(ship.getFleetMember());
	}

	public void cleanup() {
		engine.getListenerManager().removeListener(this);
		if (engine.getCustomData().get(getCustomDataKey()) == this) {
			engine.getCustomData().remove(getCustomDataKey());
		}
		engine.removePlugin(this);
	}

	private static class ScoreTrackedFleetMember {
		private final BattleNext_MemberReport report;
		private final FleetMemberAPI member;
		private final boolean playerSide;
		private ShipAPI ship;
		private boolean everDeployed;

		private ScoreTrackedFleetMember(String variantId, String name, FleetMemberAPI member, boolean playerSide) {
			report = new BattleNext_MemberReport(variantId, member.getVariant().getHullSpec().getHullId(), name);
			this.member = member;
			this.playerSide = playerSide;
		}

		private void freeze() {
			String status = "alive";
			float hullFraction = 1f;
			if (ship != null) {
				hullFraction = ship.getHullLevel();
				if (!ship.isAlive()) {
					status = "destroyed";
				}
			} else if (everDeployed) {
				status = "retreated";
			}
			report.setFinalState(hullFraction, member.getRepairTracker().getCR(), status);
		}
	}
}
