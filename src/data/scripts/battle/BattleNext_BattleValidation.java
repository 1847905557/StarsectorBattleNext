package data.scripts.battle;

import data.missions.BattleNext_MissionBattleMap;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_RulesCheck;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tournament.BattleNext_TournamentEngine;

import java.util.*;

public class BattleNext_BattleValidation {

	public enum FailureKind { CONFIGURATION, REFERENCE, ASSEMBLY, RULE, EVALUATION }

	public record Failure(FailureKind kind, String subject, String diagnostic, BattleNext_TournamentEngine.ConfigurationReason reason) {
		public Failure(FailureKind kind, String subject, String diagnostic) {
			this(kind, subject, diagnostic, null);
		}

		public String message() {
			if (reason == null) return diagnostic;
			BattleNext_I18nSection tournament = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");
			return switch (reason) {
				case MAP -> BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_").format("invalid_map", BattleNext_MissionBattleMap.LAST_MAP_ID);
				case LIMITS -> tournament.get("invalid_configuration");
				case DUPLICATE_FLEETS -> tournament.get("duplicate_fleets");
				case MINIMUM_FLEETS -> tournament.format("minimum_fleets", BattleNext_TournamentModel.MIN_FLEET_COUNT);
			};
		}
	}

	public static class Rejection extends IllegalArgumentException {
		private final Result result;

		public Rejection(String id, Result result) {
			super("Tournament qualification failed: " + id);
			this.result = result;
		}

		public Result getResult() {
			return result;
		}
	}

	public record Result(List<Failure> failures) {
		public Result {
			failures = List.copyOf(new LinkedHashSet<>(failures));
		}

		public List<String> messages() {
			return failures.stream().map(Failure::message).distinct().toList();
		}

		public boolean isBlocking() {
			return failures.stream().anyMatch(failure -> failure.kind() != FailureKind.RULE);
		}
	}

	private final BattleNext_FleetStore fleets;
	private final Map<String, BattleNext_AssembledFleet> assembled = new LinkedHashMap<>();
	private final Map<String, Result> fleetResults = new LinkedHashMap<>();
	private final Map<BattleNext_RuleModel, Map<String, Result>> ruleResults = new IdentityHashMap<>();
	private long fleetRevision = -1;
	private long ruleRevision = -1;
	private BattleNext_RuleStore ruleStore;
	private final BattleNext_RulesCheck rulesCheck = new BattleNext_RulesCheck();
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");

	public BattleNext_BattleValidation(BattleNext_FleetStore fleets) {
		this.fleets = fleets;
	}

	public void refresh() {

		assembled.clear();
		fleetResults.clear();
		ruleResults.clear();
		fleetRevision = fleets.getRevision();
		BattleNext_FleetBuilder builder = new BattleNext_FleetBuilder();
		for (BattleNext_FleetModel fleet : fleets.getAll()) {
			List<Failure> failures = new ArrayList<>();
			try {
				BattleNext_AssembledFleet result = builder.build(fleet);
				assembled.put(fleet.getId(), result);
				for (String error : result.getMemberErrors()) {
					failures.add(new Failure(FailureKind.ASSEMBLY, fleet.getId(), fleet.getId() + " " + error));
				}
			} catch (RuntimeException exception) {
				failures.add(new Failure(FailureKind.ASSEMBLY, fleet.getId(), fleet.getId() + ": " + exception.getMessage()));
				BattleNext_Logger.warn(BattleNext_BattleValidation.class, "battle.fleet.validation.failed",
						BattleNext_Logger.fields().put("fleet_id", fleet.getId()).build(), exception);
			}
			fleetResults.put(fleet.getId(), new Result(failures));
		}
	}

	public Result fleetResult(String id) {
		if (fleetRevision != fleets.getRevision()) {
			refresh();
		}
		Result reference = referenceResult(id);
		if (reference.isBlocking()) return reference;
		return fleetResults.get(id);
	}

	public Result referenceResult(String id) {
		if (!fleets.isLoaded()) return new Result(List.of(new Failure(FailureKind.REFERENCE, id, fleets.getLoadError())));
		if (fleets.get(id) == null) return new Result(List.of(new Failure(FailureKind.REFERENCE, id, "Missing fleet: " + id)));
		return new Result(List.of());
	}

	public Result ruleResult(String id, BattleNext_RuleStore rules) {
		if (ruleStore != rules || ruleRevision != rules.getRevision()) {
			ruleResults.clear();
			ruleStore = rules;
			ruleRevision = rules.getRevision();
		}
		if (id.isEmpty()) return new Result(List.of());
		if (!rules.isLoaded()) return new Result(List.of(new Failure(FailureKind.REFERENCE, id, rules.getLoadError())));
		BattleNext_RuleModel rule = rules.get(id);
		if (rule == null) return new Result(List.of(new Failure(FailureKind.REFERENCE, id, "Missing rule: " + id)));
		if (!rule.getLoadErrors().isEmpty()) return new Result(rule.getLoadErrors().stream().map(error -> new Failure(FailureKind.REFERENCE, id, error)).toList());
		Map<String, Result> results = ruleResults.computeIfAbsent(rule, ignored -> new LinkedHashMap<>());
		return results.computeIfAbsent("", ignored -> new Result(rulesCheck.missingRestrictions(rule).stream()
				.map(message -> new Failure(FailureKind.REFERENCE, id, message)).toList()));
	}

	public Result fleetRuleResult(String id, String ruleId, BattleNext_RuleStore rules) {

		List<Failure> failures = new ArrayList<>(fleetResult(id).failures());
		Result ruleState = ruleResult(ruleId, rules);
		failures.addAll(ruleState.failures());
		if (ruleId.isEmpty() || !failures.isEmpty()) return new Result(failures);
		BattleNext_RuleModel rule = rules.get(ruleId);
		Map<String, Result> results = ruleResults.get(rule);
		return results.computeIfAbsent(id, ignored -> {
			try {
				return new Result(rulesCheck.validateFleet(assembled.get(id), rule).stream()
						.map(message -> new Failure(FailureKind.RULE, id, message)).toList());
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_BattleValidation.class, "tournament.fleet.validation.failed",
						BattleNext_Logger.fields().put("fleet_id", id).put("rule_id", ruleId).build(), exception);
				return new Result(List.of(new Failure(FailureKind.EVALUATION, id, id + ": " + exception.getMessage())));
			}
		});
	}

	public List<String> quickBattleErrors(String player, String enemy, String mapInput, String scaleInput) {

		List<String> errors = new ArrayList<>();
		if (!fleets.isLoaded()) return List.of(fleets.getLoadError());
		if (fleets.get(player.trim()) == null) {
			errors.add(battleStrings.get("invalid_player_fleet"));
		} else {
			errors.addAll(fleetResult(player.trim()).messages());
		}
		if (fleets.get(enemy.trim()) == null) {
			errors.add(battleStrings.get("invalid_enemy_fleet"));
		} else {
			errors.addAll(fleetResult(enemy.trim()).messages());
		}
		try {
			int map = Integer.parseInt(mapInput.trim());
			if (map < BattleNext_MissionBattleMap.RANDOM_MAP_ID || map > BattleNext_MissionBattleMap.LAST_MAP_ID) {
				errors.add(battleStrings.format("invalid_map", BattleNext_MissionBattleMap.LAST_MAP_ID));
			}
		} catch (NumberFormatException exception) {
			errors.add(battleStrings.format("invalid_map", BattleNext_MissionBattleMap.LAST_MAP_ID));
		}
		try {
			float scale = Float.parseFloat(scaleInput.trim());
			if (!Float.isFinite(scale) || scale <= 0f) {
				errors.add(battleStrings.get("invalid_map_scale"));
			}
		} catch (NumberFormatException exception) {
			errors.add(battleStrings.get("invalid_map_scale"));
		}
		return List.copyOf(new LinkedHashSet<>(errors));
	}

	public Result tournamentResult(BattleNext_TournamentModel tournament, List<String> fleetIds, BattleNext_RuleStore rules) {
		List<Failure> failures = new ArrayList<>();
		try {
			new BattleNext_TournamentEngine().validateConfiguration(tournament);
			new BattleNext_TournamentEngine().validateParticipants(tournament, fleetIds);
		} catch (BattleNext_TournamentEngine.ConfigurationRejection exception) {
			BattleNext_Logger.warn(getClass(), "tournament.configuration.rejected", BattleNext_Logger.fields()
					.put("tournament_id", tournament.getId()).put("fleet_ids", fleetIds).put("reason", exception.getReason()).build(), exception);
			failures.add(new Failure(FailureKind.CONFIGURATION, tournament.getId(), exception.getMessage(), exception.getReason()));
		}
		failures.addAll(ruleResult(tournament.getRuleId(), rules).failures());
		for (String id : fleetIds) {
			if (id.isEmpty()) continue;
			failures.addAll(fleetRuleResult(id, tournament.getRuleId(), rules).failures());
		}
		return new Result(failures);
	}

	public List<String> tournamentErrors(BattleNext_TournamentModel tournament, List<String> fleetIds, BattleNext_RuleStore rules) {
		return tournamentResult(tournament, fleetIds, rules).messages();
	}
}
