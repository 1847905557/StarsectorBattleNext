package data.scripts.battle;

import data.model.BattleNext_BaseStore;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.model.quickbattle.BattleNext_QuickBattleStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.tournament.BattleNext_TournamentEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BattleNext_BattleSourceProvider {

	private final BattleNext_FleetStore fleetStore;
	private final BattleNext_QuickBattleStore quickBattleStore;
	private final BattleNext_BattleValidation validation;
	private final BattleNext_TournamentStore tournamentStore;
	private final BattleNext_TournamentEngine tournamentEngine;
	private final BattleNext_RuleStore ruleStore;
	private final List<String> warnings = new ArrayList<>();
	private final List<String> ruleWarnings = new ArrayList<>();
	private BattleNext_StartedTournament activeTournament;
	private final BattleNext_StartedTournamentStore startedStore = new BattleNext_StartedTournamentStore();
	private List<String> catalogErrors = List.of();

	public BattleNext_BattleSourceProvider() {
		fleetStore = new BattleNext_FleetStore();
		quickBattleStore = new BattleNext_QuickBattleStore();
		validation = new BattleNext_BattleValidation(fleetStore);
		tournamentStore = new BattleNext_TournamentStore();
		tournamentEngine = new BattleNext_TournamentEngine();
		ruleStore = new BattleNext_RuleStore();
	}

	public List<String> reloadCatalogs() {
		return reloadStores(List.of(fleetStore, quickBattleStore, tournamentStore, ruleStore));
	}

	public List<String> reloadQuickBattleCatalog() {
		return reloadStores(List.of(fleetStore, quickBattleStore));
	}

	public List<String> reloadTournamentCatalog() {
		return reloadStores(List.of(fleetStore, tournamentStore, ruleStore));
	}

	public List<String> reloadTournamentDefinitions() {
		return reloadStores(List.of(tournamentStore, ruleStore));
	}

	private List<String> reloadStores(List<BattleNext_BaseStore<?>> stores) {
		List<String> errors = new ArrayList<>();
		for (BattleNext_BaseStore<?> store : stores) {
			try {
				store.reload();
			} catch (Exception exception) {
				errors.add(store.getLoadError());
				BattleNext_Logger.warn(BattleNext_BattleSourceProvider.class, "battle.catalog.load.failed",
						BattleNext_Logger.fields().put("store", store.getClass()).build(), exception);
			}
		}
		try {
			startedStore.reload();
		} catch (Exception exception) {
			errors.add(startedStore.getLoadError());
		}
		validation.refresh();
		catalogErrors = List.copyOf(errors);
		return catalogErrors;
	}

	public List<String> getCatalogErrors() {
		return catalogErrors;
	}

	public BattleNext_QuickBattleStore getQuickBattleStore() {
		return quickBattleStore;
	}

	public BattleNext_BattleValidation getValidation() {
		return validation;
	}

	public List<String> quickBattleErrors(BattleNext_QuickBattleModel entry) {
		if (!quickBattleStore.isLoaded()) return List.of(quickBattleStore.getLoadError());
		if (entry.getRowType() == BattleNext_QuickBattleModel.QuickBattleRowType.RAW) {
			return List.of("Raw quick battle row");
		}
		return validation.quickBattleErrors(entry.getPlayer(), entry.getEnemy(), Integer.toString(entry.getMap()), Float.toString(entry.getMapScale()));
	}

	public List<String> tournamentErrors(BattleNext_TournamentModel tournament, List<String> fleetIds) {
		if (!tournamentStore.isLoaded()) return List.of(tournamentStore.getLoadError());
		return validation.tournamentErrors(tournament, fleetIds, ruleStore);
	}

	public BattleNext_BattleValidation.Result checkTournament(String id, List<String> fleetIds) {
		List<BattleNext_BattleValidation.Failure> failures = new ArrayList<>();
		for (String error : reloadTournamentCatalog()) {
			failures.add(new BattleNext_BattleValidation.Failure(BattleNext_BattleValidation.FailureKind.REFERENCE, id, error));
		}
		BattleNext_TournamentModel configuration = tournamentStore.get(id);
		if (configuration == null) {
			failures.add(new BattleNext_BattleValidation.Failure(BattleNext_BattleValidation.FailureKind.REFERENCE, id, "Missing tournament configuration: " + id));
		} else {
			failures.addAll(validation.tournamentResult(configuration, fleetIds, ruleStore).failures());
		}
		return new BattleNext_BattleValidation.Result(failures);
	}

	public List<BattleNext_Match> load() {
		warnings.clear();
		ruleWarnings.clear();
		activeTournament = null;
		try {
			fleetStore.reload();
			tournamentStore.reload();
			startedStore.reload();
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_BattleSourceProvider.class, "battle.sources.load.failed",
				BattleNext_Logger.fields().put("active_tournament", activeTournament).put("fleet_load_error", fleetStore.getLoadError()).put("tournament_load_error", tournamentStore.getLoadError()).build(), exception);
			warnings.add("Unable to load battle sources: " + exception.getMessage());
			return List.of();
		}

		String activeId = startedStore.getActiveId();
		warnings.addAll(fleetStore.getErrors().stream().map(error -> error.path() + " / " + error.id() + ": " + error.diagnostic()).toList());
		warnings.addAll(tournamentStore.getErrors().stream().map(error -> error.path() + " / " + error.id() + ": " + error.diagnostic()).toList());
		warnings.addAll(startedStore.getLoadErrors());
		if (!activeId.isEmpty()) {
			BattleNext_StartedTournament tournament = startedStore.get(activeId);
			if (tournament == null) {
				BattleNext_Logger.warn(BattleNext_BattleSourceProvider.class, "tournament.active.missing",
				BattleNext_Logger.fields().put("active_id", activeId).build());
				warnings.add("Active tournament is missing: " + activeId);
				return List.of();
			}
			activeTournament = tournament;
			try {
				if (BattleNext_ModFileManager.exists(BattleNext_StartedTournamentStore.pendingRoundPath(activeId))) {
					warnings.add(BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_").format("pending_round_submission", activeId));
					return List.of();
				}
			} catch (Exception exception) {
				warnings.add("Unable to read pending tournament result: " + activeId + ": " + exception.getMessage());
				return List.of();
			}
			if (tournament.isCompleted()) {
				return List.of();
			}
			BattleNext_TournamentModel configuration = tournamentStore.get(tournament.getSourceTournamentId());
			if (configuration == null) {
				warnings.add("Missing tournament configuration: " + tournament.getSourceTournamentId());
				return List.of();
			}
			if (!configuration.getRuleId().isEmpty()) {
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
		for (var error : fleetStore.getErrors()) {
			String diagnostic = error.path() + " / " + error.id() + ": " + error.diagnostic();
			if (warnings.contains(diagnostic)) continue;
			warnings.add(diagnostic);
		}
		try {
			quickBattleStore.reload();
		} catch (Exception exception) {
			BattleNext_Logger.error(BattleNext_BattleSourceProvider.class, "battle.sources.load.failed",
				BattleNext_Logger.fields().put("active_tournament", activeTournament).put("fleet_load_error", fleetStore.getLoadError()).put("tournament_load_error", tournamentStore.getLoadError()).build(), exception);
			warnings.add("Unable to load quick battle list: " + exception.getMessage());
		}
		List<BattleNext_Match> matches = new ArrayList<>();
		warnings.addAll(quickBattleStore.getErrors().stream().map(error -> error.path() + ": " + error.diagnostic()).toList());
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

	public List<String> getRuleWarnings() {
		return Collections.unmodifiableList(ruleWarnings);
	}

	public BattleNext_FleetStore getFleetStore() {
		return fleetStore;
	}

	public BattleNext_RuleStore getRuleStore() {
		return ruleStore;
	}

	public BattleNext_StartedTournament getActiveTournament() {
		return activeTournament;
	}

	public BattleNext_StartedTournamentStore getStartedStore() {
		return startedStore;
	}

	public BattleNext_TournamentStore getTournamentStore() {
		return tournamentStore;
	}

	public BattleNext_TournamentEngine getTournamentEngine() {
		return tournamentEngine;
	}

	private List<BattleNext_Match> validate(List<BattleNext_Match> source, String sourceName) {

		validation.refresh();
		List<BattleNext_Match> result = new ArrayList<>();
		String ruleId = activeTournament == null ? "" : tournamentStore.get(activeTournament.getSourceTournamentId()).getRuleId();
		BattleNext_BattleValidation.Result ruleState = validation.ruleResult(ruleId, ruleStore);
		warnings.addAll(ruleState.messages());
		if (ruleState.isBlocking()) return result;
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
			List<BattleNext_BattleValidation.Failure> failures = new ArrayList<>();
			failures.addAll(validation.fleetRuleResult(match.getPlayer(), ruleId, ruleStore).failures());
			failures.addAll(validation.fleetRuleResult(match.getEnemy(), ruleId, ruleStore).failures());
			BattleNext_BattleValidation.Result qualification = new BattleNext_BattleValidation.Result(failures);
			for (BattleNext_BattleValidation.Failure failure : qualification.failures()) {
				if (failure.kind() == BattleNext_BattleValidation.FailureKind.RULE) {
					ruleWarnings.add(failure.message());
					warnings.add(failure.message());
				} else {
					warnings.add(failure.message());
				}
			}
			if (qualification.failures().isEmpty()) {
				result.add(match);
			}
		}
		return result;
	}
}
