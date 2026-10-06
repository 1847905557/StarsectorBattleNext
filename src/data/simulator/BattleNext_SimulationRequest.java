package data.simulator;

import data.missions.BattleNext_MissionBattleMap;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_VariantBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BattleNext_SimulationRequest(String id, Kind kind, String sourceJson, BattleNext_SimulationConfig config) {

	public enum Kind { VARIANTS, MATCH, TOURNAMENT }

	public BattleNext_SimulationRequest {
		UUID.fromString(id);
		if (kind == null || sourceJson == null || config == null) throw new IllegalArgumentException("Simulation source and configuration are required");
	}

	public static BattleNext_SimulationRequest fromVariants(List<String> player, List<String> enemy, BattleNext_SimulationConfig config) {
		if (player.isEmpty() || enemy.isEmpty()) throw new IllegalArgumentException("Both simulation sides require at least one variant");
		for (String id : player) {
			if (id.isBlank()) throw new IllegalArgumentException("Player variant ID must not be blank");
		}
		for (String id : enemy) {
			if (id.isBlank()) throw new IllegalArgumentException("Enemy variant ID must not be blank");
		}
		try {
			return create(Kind.VARIANTS, new JSONObject().put("player", new JSONArray(player)).put("enemy", new JSONArray(enemy)), config);
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Unable to construct variant simulation request", exception);
		}
	}

	public static BattleNext_SimulationRequest fromMatch(BattleNext_Match match, BattleNext_FleetStore fleets,
			BattleNext_SimulationConfig config) {

		if (match.getMap() < BattleNext_MissionBattleMap.RANDOM_MAP_ID || match.getMap() > BattleNext_MissionBattleMap.LAST_MAP_ID
				|| !Float.isFinite(match.getMapScale()) || match.getMapScale() <= 0f) throw new IllegalArgumentException("Invalid simulation match map");
		if (!fleets.isLoaded()) throw new IllegalStateException(fleets.getLoadError());
		try {
			JSONObject source = new JSONObject().put("match", match.toJson()).put("actualMap", BattleNext_MissionBattleMap.resolve(match.getMap()))
					.put("sourceType", match.getSourceType().name()).put("sourcePath", match.getSourcePath())
					.put("player", snapshot(match.getPlayer(), fleets)).put("enemy", snapshot(match.getEnemy(), fleets));
			return create(Kind.MATCH, source, config);
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Unable to construct match simulation request: " + match.getId(), exception);
		}
	}

	public static BattleNext_SimulationRequest fromTournament(BattleNext_TournamentModel tournament, BattleNext_FleetStore fleets,
			BattleNext_RuleStore rules, BattleNext_SimulationConfig config) {

		if (!fleets.isLoaded() || !rules.isLoaded()) throw new IllegalStateException(!fleets.isLoaded() ? fleets.getLoadError() : rules.getLoadError());
		try {
			JSONObject participants = new JSONObject();
			for (String id : tournament.getFleetIds()) {
				participants.put(id, snapshot(id, fleets));
			}
			JSONObject source = new JSONObject().put("tournament", tournament.toJson()).put("fleets", participants)
					.put("sourceType", tournament.getSourceType().name()).put("sourcePath", tournament.getSourcePath());
			if (!tournament.getRuleId().isEmpty()) {
				if (rules.get(tournament.getRuleId()) == null) throw new IllegalArgumentException("Missing simulation tournament rule: " + tournament.getRuleId());
				source.put("rule", rules.get(tournament.getRuleId()).toJson());
			}
			return create(Kind.TOURNAMENT, source, config);
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Unable to construct tournament simulation request: " + tournament.getId(), exception);
		}
	}

	private static BattleNext_SimulationRequest create(Kind kind, JSONObject source, BattleNext_SimulationConfig config) {
		return new BattleNext_SimulationRequest(UUID.randomUUID().toString(), kind, source.toString(), config);
	}

	private static JSONObject snapshot(String id, BattleNext_FleetStore fleets) throws JSONException {

		BattleNext_FleetModel fleet = fleets.get(id);
		if (fleet == null) throw new IllegalArgumentException("Simulation fleet reference is unavailable: " + id);
		if (!fleet.getLoadErrors().isEmpty()) throw new IllegalArgumentException("Incomplete simulation fleet: " + fleet.getId() + ": " + fleet.getLoadErrors());
		BattleNext_AssembledFleet assembled = new BattleNext_FleetBuilder().build(fleet);
		if (!assembled.isComplete() || assembled.getMembers().isEmpty()) throw new IllegalArgumentException("Simulation fleet requires complete root members: " + fleet.getId() + ": " + assembled.getMemberErrors());
		JSONObject variants = new JSONObject();
		for (int index = 0; index < assembled.getMembers().size(); index++) {
			variants.put(Integer.toString(index), BattleNext_VariantBuilder.fromVariant(assembled.getMembers().get(index).getVariant()).toJson());
		}
		return new JSONObject().put("definition", fleet.toJson()).put("variants", variants)
				.put("folderName", fleet.getFolderName()).put("sourceType", fleet.getSourceType().name()).put("sourcePath", fleet.getSourcePath());
	}

	public JSONObject source() {
		try {
			return new JSONObject(sourceJson);
		} catch (JSONException exception) {
			throw new IllegalStateException("Invalid simulation source snapshot: " + id, exception);
		}
	}

	public List<String> player() {
		return variants("player");
	}

	public List<String> enemy() {
		return variants("enemy");
	}

	private List<String> variants(String side) {
		try {
			JSONObject source = source();
			JSONArray values = kind == Kind.VARIANTS ? source.getJSONArray(side)
					: source.getJSONObject(side).getJSONObject("definition").getJSONArray("members");
			List<String> ids = new ArrayList<>();
			for (int index = 0; index < values.length(); index++) {
				ids.add(kind == Kind.VARIANTS ? values.getString(index) : values.getJSONObject(index).getString("variant_id"));
			}
			return List.copyOf(ids);
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to read simulation members: " + id, exception);
		}
	}

	public String label() {
		try {
			if (kind == Kind.VARIANTS) return String.join(",", player()) + " vs " + String.join(",", enemy());
			if (kind == Kind.TOURNAMENT) return source().getJSONObject("tournament").getString("name");
			return source().getJSONObject("player").getJSONObject("definition").getString("name") + " vs "
					+ source().getJSONObject("enemy").getJSONObject("definition").getString("name");
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to read simulation label: " + id, exception);
		}
	}

	public JSONObject toJson() {
		try {
			return new JSONObject().put("id", id).put("kind", kind.name()).put("source", source()).put("config", config.toJson());
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to serialize simulation request: " + id, exception);
		}
	}

	public static BattleNext_SimulationRequest fromJson(JSONObject json) throws JSONException {
		return new BattleNext_SimulationRequest(json.getString("id"), Kind.valueOf(json.getString("kind")),
				json.getJSONObject("source").toString(), BattleNext_SimulationConfig.fromJson(json.getJSONObject("config")));
	}
}
