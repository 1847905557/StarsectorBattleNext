package data.scripts.combat;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import data.scripts.fleet.BattleNext_FleetAssembly;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.match.BattleNext_BattleReport;
import data.scripts.match.BattleNext_MemberReport;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_Scorekeeper extends BaseEveryFrameCombatPlugin implements DamageListener {

	private final List<TrackedMember> trackedMembers = new ArrayList<>();
	private final Map<FleetMemberAPI, TrackedMember> membersByFleetMember = new IdentityHashMap<>();
	private CombatEngineAPI engine;
	private BattleNext_BattleReport frozenReport;
	private boolean battleStarted;

	public BattleNext_Scorekeeper(String playerFleetId, BattleNext_FleetAssembly playerAssembly,
			String enemyFleetId, BattleNext_FleetAssembly enemyAssembly) {
		registerMembers(playerFleetId, playerAssembly);
		registerMembers(enemyFleetId, enemyAssembly);
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
		if (!battleStarted && hasActiveShip()) battleStarted = true;
		if (battleStarted) {
			String winner = determineWinner();
			if (winner != null) freeze(winner);
		}
	}

	@Override
	public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
		if (frozenReport != null || result == null) return;
		TrackedMember targetMember = findTracked(target);
		if (targetMember != null) {
			targetMember.report.addDamageTaken(result.getDamageToHull(), result.getDamageToShields(), result.getEmpDamage());
		}
		TrackedMember sourceMember = findTracked(sourceShip(source));
		if (sourceMember != null) {
			sourceMember.report.addDamageDealt(result.getDamageToHull(), result.getDamageToShields(), result.getEmpDamage());
		}
	}

	public BattleNext_BattleReport getFrozenReport() {
		return frozenReport;
	}

	public boolean isFrozen() {
		return frozenReport != null;
	}

	public String getCustomDataKey() {
		return "BattleNext_Scorekeeper";
	}

	private void registerMembers(String fleetId, BattleNext_FleetAssembly assembly) {
		BattleNext_FleetModel definition = assembly.getDefinition();
		List<FleetMemberAPI> members = assembly.getMembers();
		for (int index = 0; index < members.size(); index++) {
			String variantId = definition.getMembers().get(index).getVariantId();
			String name = definition.getMembers().get(index).getName();
			TrackedMember tracked = new TrackedMember(fleetId, index, variantId, name, members.get(index));
			trackedMembers.add(tracked);
			membersByFleetMember.put(members.get(index), tracked);
		}
	}

	private void refreshShips() {
		for (ShipAPI ship : engine.getShips()) {
			FleetMemberAPI member = ship.getFleetMember();
			TrackedMember tracked = membersByFleetMember.get(member);
			if (tracked != null) {
				tracked.ship = ship;
				tracked.everDeployed = true;
			}
		}
	}

	private boolean hasActiveShip() {
		for (TrackedMember tracked : trackedMembers) {
			if (tracked.ship != null && tracked.ship.isAlive() && !tracked.ship.isFighter()
					&& !tracked.ship.isStation()) return true;
		}
		return false;
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
		for (TrackedMember tracked : trackedMembers) {
			if (tracked.ship != null && tracked.ship.getOwner() == side.ordinal() && tracked.ship.isAlive()
					&& !tracked.ship.isFighter() && !tracked.ship.isStation()) return true;
		}
		return false;
	}

	private void freeze(String winner) {
		if (frozenReport != null) return;
		BattleNext_BattleReport report = new BattleNext_BattleReport();
		report.setWinner(winner);
		report.setDurationMillis((long)(engine.getTotalElapsedTime(false) * 1000f));
		for (TrackedMember tracked : trackedMembers) {
			tracked.freeze();
			report.addMember(tracked.report);
		}
		frozenReport = report;
	}

	private TrackedMember findTracked(CombatEntityAPI entity) {
		if (!(entity instanceof ShipAPI)) return null;
		return membersByFleetMember.get(((ShipAPI)entity).getFleetMember());
	}

	private ShipAPI sourceShip(Object source) {
		if (source instanceof ShipAPI) return (ShipAPI)source;
		if (source instanceof DamagingProjectileAPI) return ((DamagingProjectileAPI)source).getSource();
		return null;
	}

	private class TrackedMember {
		private final BattleNext_MemberReport report;
		private final FleetMemberAPI member;
		private ShipAPI ship;
		private boolean everDeployed;

		private TrackedMember(String fleetId, int memberIndex, String variantId, String name, FleetMemberAPI member) {
			report = new BattleNext_MemberReport(fleetId, memberIndex, variantId, name);
			this.member = member;
		}

		private void freeze() {
			String status = "alive";
			float hullFraction = 1f;
			if (ship != null) {
				hullFraction = ship.getHullLevel();
				if (!ship.isAlive()) status = "destroyed";
			} else if (everDeployed) {
				status = "retreated";
			}
			report.setFinalState(hullFraction, member.getRepairTracker().getCR(), status);
		}
	}
}
