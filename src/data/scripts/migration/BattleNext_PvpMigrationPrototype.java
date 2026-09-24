package data.scripts.migration;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.scripts.fleet.BattleNext_FleetAssembler;
import data.scripts.fleet.BattleNext_FleetAssembly;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_LegacyLoader;
import data.scripts.combat.BattleNext_BattleMap;
import data.scripts.combat.BattleNext_Deployment;
import data.scripts.combat.BattleNext_MissionPlugin;
import data.scripts.combat.BattleNext_FleetBehavior;
import data.scripts.combat.BattleNext_PresentationPlugin;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_PvpMigrationPrototype {

	private static final Logger LOGGER = Global.getLogger(BattleNext_PvpMigrationPrototype.class);
	private static final String MOD_ID = "AI_Battles_Next";
	private static final String MATCH_PATH = "tournament/PvP_matches.csv";
	private static final int MAP_NUMBER = 3;

	private static final float DEFAULT_MATCH_SIZE = 1f;
	private static final float MIGRATION_MAP_BASE_SIZE = 11000f;
	private static final float MIN_MAP_SIZE = 0.1f;
	private static final float DEPLOYMENT_SPACING = 800f;
	private static final float DEPLOYMENT_ROW_SPACING = 650f;
	private static final float DEPLOYMENT_SPAWN_DELAY = 3f;

	private int currentMatchIndex;

	private final BattleNext_LegacyLoader legacyLoader = new BattleNext_LegacyLoader();
	private final BattleNext_FleetAssembler fleetAssembler = new BattleNext_FleetAssembler();

	public void defineMission(MissionDefinitionAPI api) {
		Map<String, BattleNext_FleetModel> fleets = loadFleets();
		List<LegacyMatch> matches = loadMatches(fleets);
		if (matches.isEmpty()) {
			defineEmptyMission(api);
			return;
		}

		int requestedIndex = selectMatchIndex(matches.size());
		for (int offset = 0; offset < matches.size(); offset++) {
			int candidateIndex = Math.floorMod(requestedIndex + offset, matches.size());
			LegacyMatch match = matches.get(candidateIndex);
			BattleNext_FleetAssembly playerAssembly = assemble(fleets, match.player, match.lineNumber, "player");
			BattleNext_FleetAssembly enemyAssembly = assemble(fleets, match.enemy, match.lineNumber, "enemy");
			if (playerAssembly == null || enemyAssembly == null) continue;
			currentMatchIndex = candidateIndex;
			configureFleet(api, FleetSide.PLAYER, playerAssembly);
			configureFleet(api, FleetSide.ENEMY, enemyAssembly);
			configureBriefing(api, playerAssembly, enemyAssembly, match.size);
			BattleNext_BattleMap.apply(api, MAP_NUMBER, match.size);
			api.addPlugin(new BattleNext_Deployment());
			api.addPlugin(new BattleNext_MissionPlugin());
			api.addPlugin(new BattleNext_FleetBehavior(BattleNext_FleetBehavior.fromTags(playerAssembly.getDefinition().getTags()),
					BattleNext_FleetBehavior.fromTags(enemyAssembly.getDefinition().getTags()), playerAssembly.getDefinition(),
					enemyAssembly.getDefinition()));
			api.addPlugin(new BattleNext_PresentationPlugin(playerAssembly.getDefinition().getName(), enemyAssembly.getDefinition().getName()));
			return;
		}
		defineEmptyMission(api);
	}

	private List<LegacyMatch> loadMatches(Map<String, BattleNext_FleetModel> fleets) {
		String text;
		try {
			text = Global.getSettings().loadText(MATCH_PATH, MOD_ID);
		} catch (IOException | JSONException exception) {
			LOGGER.warn("Unable to read " + MATCH_PATH, exception);
			return List.of();
		}

		List<LegacyMatch> matches = new ArrayList<>();
		int lineNumber = 0;
		for (String line : text.split("\\R", -1)) {
			lineNumber++;
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("player,enemy,map,size")) continue;
			List<String> fields = parse(line);
			if (fields.size() < 2) {
				LOGGER.warn("Skipping legacy PvP line " + lineNumber + ": expected player,enemy,map,size");
				continue;
			}
			try {
				int player = Integer.parseInt(fields.get(0));
				int enemy = Integer.parseInt(fields.get(1));
				int map = fields.size() > 2 ? Integer.parseInt(fields.get(2)) : -1;
				float size = fields.size() > 3 ? Float.parseFloat(fields.get(3)) : DEFAULT_MATCH_SIZE;
				if (map != MAP_NUMBER) {
					LOGGER.warn("Skipping legacy PvP line " + lineNumber + ": unsupported map " + map);
					continue;
				}
				String playerId = "ab_player" + player;
				String enemyId = "ab_player" + enemy;
				if (!fleets.containsKey(playerId) || !fleets.containsKey(enemyId)) {
					LOGGER.warn("Skipping legacy PvP line " + lineNumber + ": missing fleet " + playerId + " or " + enemyId);
					continue;
				}
				matches.add(new LegacyMatch(playerId, enemyId, size, lineNumber));
				matches.add(new LegacyMatch(enemyId, playerId, size, lineNumber));
			} catch (NumberFormatException exception) {
				LOGGER.warn("Skipping legacy PvP line " + lineNumber + ": invalid numeric field", exception);
			}
		}
		return matches;
	}

	private Map<String, BattleNext_FleetModel> loadFleets() {
		Map<String, BattleNext_FleetModel> fleets = new HashMap<>();
		loadReferencedFleets(fleets);
		return fleets;
	}

	private void loadReferencedFleets(Map<String, BattleNext_FleetModel> fleets) {
		String text;
		try {
			text = Global.getSettings().loadText(MATCH_PATH, MOD_ID);
		} catch (IOException | JSONException exception) {
			return;
		}
		for (String line : text.split("\\R", -1)) {
			List<String> fields = parse(line);
			if (fields.size() < 2 || fields.get(0).equalsIgnoreCase("player") || fields.get(0).startsWith("#")) continue;
			for (int index = 0; index < 2; index++) {
				String number = fields.get(index);
				if (!number.matches("\\d+") || fleets.containsKey("ab_player" + number)) continue;
				try {
					BattleNext_FleetModel fleet = legacyLoader.loadResourceFleet(number, "tournament", MOD_ID);
					fleets.put(fleet.getId(), fleet);
				} catch (IOException | JSONException | RuntimeException exception) {
					LOGGER.warn("Unable to read legacy fleet ab_player" + number + " from tournament/", exception);
				}
			}
		}
	}

	private BattleNext_FleetAssembly assemble(Map<String, BattleNext_FleetModel> fleets,
			String fleetId, int lineNumber, String side) {
		BattleNext_FleetModel fleet = fleets.get(fleetId);
		if (fleet == null) {
			LOGGER.warn("Skipping legacy PvP line " + lineNumber + ": missing " + side + " fleet " + fleetId);
			return null;
		}
		BattleNext_FleetAssembly assembly = fleetAssembler.assemble(fleet);
		if (!assembly.isComplete()) {
			LOGGER.warn("Skipping legacy PvP line " + lineNumber + ": " + side + " fleet " + fleetId + " has assembly errors " + assembly.getMemberErrors());
			return null;
		}
		return assembly;
	}

	private void configureFleet(MissionDefinitionAPI api, FleetSide side, BattleNext_FleetAssembly assembly) {
		BattleNext_FleetModel definition = assembly.getDefinition();
		boolean defaultAi = definition.getTags().contains("default_ai");
		api.initFleet(side, "", FleetGoal.ATTACK, defaultAi, 1);
		api.setFleetTagline(side, definition.getName());
		for (FleetMemberAPI member : assembly.getMembers()) {
			api.addFleetMember(side, member);
		}
	}

	private void configureBriefing(MissionDefinitionAPI api, BattleNext_FleetAssembly playerAssembly,
			BattleNext_FleetAssembly enemyAssembly, float size) {
		api.addBriefingItem("Player: " + playerAssembly.getDefinition().getName() + " (" + deploymentCost(playerAssembly) + " DP)");
		api.addBriefingItem("Enemy: " + enemyAssembly.getDefinition().getName() + " (" + deploymentCost(enemyAssembly) + " DP)");
		api.addBriefingItem("Legacy migration map 3, size " + size);
	}

	private int deploymentCost(BattleNext_FleetAssembly assembly) {
		int result = 0;
		for (FleetMemberAPI member : assembly.getMembers()) {
			if (!member.isFighterWing()) result += (int)member.getDeploymentPointsCost();
		}
		return result;
	}

	private void configureMap(MissionDefinitionAPI api, float size) {
		float width = MIGRATION_MAP_BASE_SIZE * Math.max(size, MIN_MAP_SIZE);
		float height = MIGRATION_MAP_BASE_SIZE * Math.max(size, MIN_MAP_SIZE);
		api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);
		api.addObjective(-0.05f * width, 0.05f * height, "sensor_array");
		api.addObjective(0.05f * width, 0.05f * height, "nav_buoy");
		api.addObjective(0.05f * width, -0.05f * height, "sensor_array");
		api.addObjective(-0.05f * width, -0.05f * height, "nav_buoy");
		api.addObjective(0f, -0.05f * height, "comm_relay");
		api.addObjective(0f, 0.05f * height, "comm_relay");
	}

	private void defineEmptyMission(MissionDefinitionAPI api) {
		api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 1);
		api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 1);
		api.setFleetTagline(FleetSide.PLAYER, "No valid legacy PvP match");
		api.setFleetTagline(FleetSide.ENEMY, "No valid legacy PvP match");
		api.addBriefingItem("No valid legacy PvP matches found in tournament/PvP_matches.csv.");
		BattleNext_BattleMap.apply(api, MAP_NUMBER, 1f);
	}

	private int selectMatchIndex(int size) {
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
			currentMatchIndex++;
		} else if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
			currentMatchIndex--;
		}
		return Math.floorMod(currentMatchIndex, size);
	}

	private List<String> parse(String line) {
		List<String> result = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < line.length(); index++) {
			char value = line.charAt(index);
			if (value == '"') {
				quoted = !quoted;
			} else if (value == ',' && !quoted) {
				result.add(field.toString().trim());
				field.setLength(0);
			} else {
				field.append(value);
			}
		}
		result.add(field.toString().trim());
		return result;
	}

	private static class LegacyMatch {

		private final String player;
		private final String enemy;
		private final float size;
		private final int lineNumber;

		private LegacyMatch(String player, String enemy, float size, int lineNumber) {
			this.player = player;
			this.enemy = enemy;
			this.size = size;
			this.lineNumber = lineNumber;
		}
	}

	private class ForcedDeploymentPlugin extends BaseEveryFrameCombatPlugin {

		private CombatEngineAPI engine;
		private boolean deployed;

		@Override
		public void init(CombatEngineAPI engine) {
			this.engine = engine;
		}

		@Override
		public void advance(float amount, List<InputEventAPI> events) {
			if (engine == null || deployed) return;
			deployed = true;
			forceSpawn(FleetSide.PLAYER);
			forceSpawn(FleetSide.ENEMY);
		}

		private void forceSpawn(FleetSide side) {
			engine.getFleetManager(side).setSuppressDeploymentMessages(true);
			float spawnX = 0f;
			float spawnY = side == FleetSide.PLAYER ? -engine.getMapHeight() / 2f : engine.getMapHeight() / 2f;
			float facing = side == FleetSide.PLAYER ? 90f : -90f;
			for (FleetMemberAPI member : engine.getFleetManager(side).getReservesCopy()) {
				if (member.isFighterWing()) continue;
				engine.getFleetManager(side).spawnFleetMember(member, new Vector2f(spawnX, spawnY), facing, DEPLOYMENT_SPAWN_DELAY);
				spawnX = spawnX == 0f ? DEPLOYMENT_SPACING : -spawnX;
				if (Math.abs(spawnX) >= engine.getMapWidth() / 4f) {
					spawnX = 0f;
					spawnY += side == FleetSide.PLAYER ? -DEPLOYMENT_ROW_SPACING : DEPLOYMENT_ROW_SPACING;
				}
			}
			engine.getFleetManager(side).setSuppressDeploymentMessages(false);
		}
	}
}
