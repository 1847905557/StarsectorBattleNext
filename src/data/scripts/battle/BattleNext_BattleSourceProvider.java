package data.scripts.battle;

import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_RulesCheck;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.tools.BattleNext_BattleSignals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_BattleSourceProvider {

	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_TournamentStore tournamentStore;
	private final BattleNext_TournamentEngine tournamentEngine;
	private final BattleNext_RuleStore ruleStore;
	private final BattleNext_RulesCheck rulesCheck;
	private final BattleNext_FleetBuilder fleetBuilder;
	private final List<String> warnings = new ArrayList<>();
	private final List<String> ruleWarnings = new ArrayList<>();
	private BattleNext_TournamentModel activeTournament;

	public BattleNext_BattleSourceProvider() {
		fleetStore = new BattleNext_FleetStore();
		tournamentStore = new BattleNext_TournamentStore();
		tournamentEngine = new BattleNext_TournamentEngine();
		ruleStore = new BattleNext_RuleStore();
		rulesCheck = new BattleNext_RulesCheck();
		fleetBuilder = new BattleNext_FleetBuilder();
	}

	public List<BattleNext_Match> load() {
		warnings.clear();
		ruleWarnings.clear();
		activeTournament = null;
		try {
			fleetStore.reload();
			tournamentStore.reload();
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_BattleSourceProvider.class, "battle.sources.load.failed",
				BattleNext_Logger.fields().put("active_tournament", activeTournament).put("fleet_load_error", fleetStore.getLoadError()).put("tournament_load_error", tournamentStore.getLoadError()).build(), exception);
			warnings.add("Unable to load battle sources: " + exception.getMessage());
			return List.of();
		}

		String activeId = tournamentStore.getActiveId();
		if (!activeId.isEmpty()) {
			BattleNext_TournamentModel tournament = tournamentStore.get(activeId);
			if (tournament == null) {
				BattleNext_Logger.warn(BattleNext_BattleSourceProvider.class, "tournament.active.missing",
				BattleNext_Logger.fields().put("active_id", activeId).build());
				warnings.add("Active tournament is missing: " + activeId);
				return List.of();
			}
			activeTournament = tournament;
			if (!tournament.isStarted() || tournament.isCompleted()) {
				return List.of();
			}
			if (!tournament.getRuleId().isEmpty()) {
				try {
					ruleStore.reload();
				} catch (Exception exception) {
					BattleNext_Logger.error(BattleNext_BattleSourceProvider.class, "battle.sources.load.failed",
				BattleNext_Logger.fields().put("active_tournament", activeTournament).put("fleet_load_error", fleetStore.getLoadError()).put("tournament_load_error", tournamentStore.getLoadError()).build(), exception);
			warnings.add("Unable to load tournament rules: " + exception.getMessage());
					return List.of();
				}
			}
			BattleNext_Match next = tournamentEngine.getNextMatch(tournament);
			return next == null ? List.of() : validate(List.of(next), "tournament " + activeId);
		}

		return loadQuickBattleMatches();
	}

	public List<BattleNext_Match> loadQuickBattles() {
		warnings.clear();
		ruleWarnings.clear();
		activeTournament = null;
		try {
			fleetStore.reload();
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_BattleSourceProvider.class, "battle.sources.load.failed",
					BattleNext_Logger.fields().put("source", "quick battle").put("fleet_load_error", fleetStore.getLoadError()).build(), exception);
			warnings.add("Unable to load battle sources: " + exception.getMessage());
			return List.of();
		}
		return loadQuickBattleMatches();
	}

	private List<BattleNext_Match> loadQuickBattleMatches() {
		BattleNext_QuickBattleStore quickBattleStore = new BattleNext_QuickBattleStore();
		try {
			quickBattleStore.reload();
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_BattleSourceProvider.class, "battle.sources.load.failed",
				BattleNext_Logger.fields().put("active_tournament", activeTournament).put("fleet_load_error", fleetStore.getLoadError()).put("tournament_load_error", tournamentStore.getLoadError()).build(), exception);
			warnings.add("Unable to load quick battle list: " + exception.getMessage());
		}
		List<BattleNext_Match> matches = new ArrayList<>();
		for (BattleNext_QuickBattleModel model : quickBattleStore.getAll()) {
			if (!model.isEnabled() || model.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
				continue;
			}
			matches.add(BattleNext_Match.fromQuickBattle(model));
		}
		return validate(matches, "quick battle");
	}

	public List<String> getWarnings() {
		return Collections.unmodifiableList(warnings);
	}

	public boolean authorizeNextMatch(String tournamentId, String matchId) {
		load();
		if (!warnings.isEmpty() || ruleWarnings.isEmpty()) return false;
		BattleNext_BattleSignals.authorizeMatch(tournamentId, matchId);
		return true;
	}

	public List<String> getRuleWarnings() {
		return Collections.unmodifiableList(ruleWarnings);
	}

	public List<String> validateDraft(BattleNext_TournamentModel tournament) {
		List<String> reasons = new ArrayList<>();
		if (tournament.getRuleId().isEmpty()) return reasons;
		BattleNext_RuleModel rule = ruleStore.get(tournament.getRuleId());
		if (rule == null) {
			reasons.add("Missing rule: " + tournament.getRuleId());
			return reasons;
		}
		reasons.addAll(rulesCheck.missingRestrictions(rule));
		for (String fleetId : tournament.getFleetIds()) {
			BattleNext_FleetModel fleet = fleetStore.get(fleetId);
			if (fleet == null) {
				reasons.add("Missing fleet: " + fleetId);
				continue;
			}
			BattleNext_AssembledFleet assembledFleet = fleetBuilder.build(fleet);
			for (String error : assembledFleet.getMemberErrors()) reasons.add(fleetId + " " + error);
			if (assembledFleet.isComplete()) reasons.addAll(rulesCheck.validateFleet(assembledFleet, rule));
		}
		return reasons;
	}

	public BattleNext_FleetStore getFleetStore() {
		return fleetStore;
	}

	public BattleNext_RuleStore getRuleStore() {
		return ruleStore;
	}

	public BattleNext_TournamentModel getActiveTournament() {
		return activeTournament;
	}

	public BattleNext_TournamentStore getTournamentStore() {
		return tournamentStore;
	}

	public BattleNext_TournamentEngine getTournamentEngine() {
		return tournamentEngine;
	}

	private List<BattleNext_Match> validate(List<BattleNext_Match> source, String sourceName) {
		List<BattleNext_Match> result = new ArrayList<>();
		BattleNext_RuleModel rule = null;
		if (activeTournament != null && !activeTournament.getRuleId().isEmpty()) {
			rule = ruleStore.get(activeTournament.getRuleId());
			if (rule == null) {
				warnings.add("Missing rule: " + activeTournament.getRuleId());
				return result;
			}
			warnings.addAll(rulesCheck.missingRestrictions(rule));
			if (!warnings.isEmpty()) return result;
		}
		for (BattleNext_Match match : source) {
			BattleNext_FleetModel player = fleetStore.get(match.getPlayer());
			BattleNext_FleetModel enemy = fleetStore.get(match.getEnemy());
			if (player == null || enemy == null) {
				BattleNext_Logger.warn(BattleNext_BattleSourceProvider.class, "battle.fleet.reference.rejected",
				BattleNext_Logger.fields().put("source_name", sourceName).put("match", match).put("player", player).put("enemy", enemy).build());
				warnings.add("Skipping " + sourceName + " " + (activeTournament == null ? "battle" : "match")
						+ " " + match.getId() + ": missing player/enemy fleet reference");
				continue;
			}
			BattleNext_AssembledFleet assembledPlayerFleet = fleetBuilder.build(player);
			BattleNext_AssembledFleet assembledEnemyFleet = fleetBuilder.build(enemy);
			if (!assembledPlayerFleet.isComplete() || !assembledEnemyFleet.isComplete()) {
				for (String error : assembledPlayerFleet.getMemberErrors()) warnings.add(player.getId() + " " + error);
				for (String error : assembledEnemyFleet.getMemberErrors()) warnings.add(enemy.getId() + " " + error);
				continue;
			}
			if (rule != null) {
				ruleWarnings.addAll(rulesCheck.validateFleet(assembledPlayerFleet, rule));
				ruleWarnings.addAll(rulesCheck.validateFleet(assembledEnemyFleet, rule));
			}
			result.add(match);
		}
		return result;
	}
}
