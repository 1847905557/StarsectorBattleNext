package data.missions.BattleNext_pve;

import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.missions.BattleNext_MissionBattleMap;
import data.missions.BattleNext_MissionRestrictedUnlock;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_PveFleetGenerator;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.order.BattleNext_FleetOrderBehavior;
import data.scripts.plugin.*;
import data.scripts.tools.BattleNext_KeyTuning;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_Settings;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class MissionDefinition implements MissionDefinitionPlugin {

	private static final int MIN_EXPECTED_FP = 10;
	private static final int MAX_EXPECTED_FP = 2000;
	private static final int EXPECTED_DP_STEP = 10;
	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private final BattleNext_FleetBuilder fleetBuilder = new BattleNext_FleetBuilder();
	private final BattleNext_PveFleetGenerator enemyGenerator = new BattleNext_PveFleetGenerator();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");

	private static int expectedFp = 100;
	private static int factionIndex;
	private static int quickBattleCurrentIndex;

	@Override
	public void defineMission(MissionDefinitionAPI api) {
		BattleNext_MissionRestrictedUnlock.getInstance().enable(api);

		List<BattleNext_Match> matches = provider.loadQuickBattles();
		if (matches.isEmpty()) {
			showBlocked(api, strings.get("no_valid_battle"), provider.getWarnings());
			return;
		}
		List<FactionSpecAPI> factions;
		try {
			factions = enemyGenerator.loadFactions();
		} catch (IOException | JSONException | RuntimeException exception) {
			showBlocked(api, strings.get("pve_factions_load_failed"),
					List.of(BattleNext_PveFleetGenerator.FACTIONS_PATH + ": " + exception));
			return;
		}
		if (factions.isEmpty()) {
			showBlocked(api, strings.get("pve_no_factions"), provider.getWarnings());
			return;
		}

		expectedFp = BattleNext_KeyTuning.adjustVerticalClamped(expectedFp, EXPECTED_DP_STEP, MIN_EXPECTED_FP, MAX_EXPECTED_FP);
		factionIndex = BattleNext_KeyTuning.adjustHorizontalWrapped(factionIndex, factions.size());
		quickBattleCurrentIndex = BattleNext_KeyTuning.adjustAdWrapped(quickBattleCurrentIndex, matches.size() * 2);
		int quickBattleMatchIndex = quickBattleCurrentIndex / 2;
		FleetSide sourceSide = quickBattleCurrentIndex % 2 == 0 ? FleetSide.PLAYER : FleetSide.ENEMY;
		BattleNext_Match source = matches.get(quickBattleMatchIndex);
		String playerId = sourceSide == FleetSide.PLAYER ? source.getPlayer() : source.getEnemy();
		FactionSpecAPI faction = factions.get(factionIndex);
		BattleNext_AssembledFleet assembledPlayerFleet = fleetBuilder.build(provider.getFleetStore().get(playerId));
		if (!assembledPlayerFleet.isComplete()) {
			showBlocked(api, strings.get("invalid_battle_assembly"), assembledPlayerFleet.getMemberErrors());
			return;
		}

		BattleNext_AssembledFleet assembledEnemyFleet;
		try {
			assembledEnemyFleet = enemyGenerator.generate(faction.getId(), expectedFp);
		} catch (RuntimeException exception) {
			showBlocked(api, strings.get("pve_generation_failed"), List.of(faction.getId() + ": " + exception.getMessage()));
			return;
		}

		int actualPoints = assembledEnemyFleet.getMembers().stream().mapToInt(FleetMemberAPI::getFleetPointCost).sum();
		int map = BattleNext_MissionBattleMap.resolve(source.getMap(), quickBattleMatchIndex);
		BattleNext_Match match = new BattleNext_Match(source.getId(), playerId, assembledEnemyFleet.getDefinition().getId(),
				map, source.getMapScale());
		String reportMarker = strings.format("pve_report_marker", quickBattleMatchIndex + 1, sourceSide.name(),
				faction.getId(), expectedFp, actualPoints);

		configureFleet(api, FleetSide.PLAYER, assembledPlayerFleet, assembledPlayerFleet.getDefinition().getName());
		configureFleet(api, FleetSide.ENEMY, assembledEnemyFleet,
				strings.format("pve_enemy_tagline", faction.getDisplayName(), expectedFp, actualPoints));
		api.addBriefingItem(strings.format("map_summary", source.getMap(), source.getMapScale()));
		api.addBriefingItem(strings.format("pve_selection_summary", factionIndex + 1, factions.size(),
				quickBattleCurrentIndex + 1, matches.size() * 2));
		for (String warning : provider.getWarnings()) {
			api.addBriefingItem(warning);
		}
		BattleNext_MissionBattleMap.apply(api, map, match.getMapScale());

		float timeLimit = BattleNext_Settings.getBattleTimeLimit();
		api.addPlugin(new BattleNext_DeploymentPlugin());
		api.addPlugin(new BattleNext_EnvironmentPlugin(timeLimit));
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(assembledPlayerFleet, assembledEnemyFleet);
		api.addPlugin(scorekeeper);
		api.addPlugin(new BattleNext_BattleClockPlugin(timeLimit, scorekeeper));
		api.addPlugin(new BattleNext_BattleResultRecorder(scorekeeper, match, null,
				assembledPlayerFleet.getDefinition().getName(), assembledEnemyFleet.getDefinition().getName(), reportMarker));
		api.addPlugin(new BattleNext_FleetOrderBehavior(BattleNext_FleetOrderBehavior.fromTags(assembledPlayerFleet.getDefinition().getTags()),
				BattleNext_FleetOrderBehavior.fromTags(assembledEnemyFleet.getDefinition().getTags()), assembledPlayerFleet, assembledEnemyFleet));
		api.addPlugin(new BattleNext_FreeCameraPlugin());
		api.addPlugin(new BattleNext_AllShipsStatusPlugin());
		if (BattleNext_Settings.isRttInBattle()) {
			api.addPlugin(new BattleNext_RttPlugin());
		}
		BattleNext_PageantPlugin pageant = new BattleNext_PageantPlugin(scorekeeper,
				assembledPlayerFleet.getDefinition().getName(), assembledEnemyFleet.getDefinition().getName());
		api.addPlugin(pageant);
		BattleNext_Logger.info(MissionDefinition.class, "battle.created",
				BattleNext_Logger.fields().put("mission", "pve").put("match", match).put("source", source)
						.put("source_side", sourceSide).put("source_index", quickBattleMatchIndex).put("faction_id", faction.getId())
						.put("budget", expectedFp).put("actual_points", actualPoints).put("player", assembledPlayerFleet.getDefinition())
						.put("enemy", assembledEnemyFleet.getDefinition()).put("player_mode", BattleNext_FleetOrderBehavior.fromTags(assembledPlayerFleet.getDefinition().getTags()))
						.put("enemy_mode", BattleNext_FleetOrderBehavior.fromTags(assembledEnemyFleet.getDefinition().getTags()))
						.put("time_limit", timeLimit).put("rtt", BattleNext_Settings.isRttInBattle()).put("marker", reportMarker).build());
	}

	private void showBlocked(MissionDefinitionAPI api, String message, List<String> reasons) {
		BattleNext_Logger.warn(MissionDefinition.class, "battle.creation.rejected",
				BattleNext_Logger.fields().put("mission", "pve").put("message", message).put("reasons", reasons)
						.put("faction_index", factionIndex).put("budget", expectedFp).put("player_index", quickBattleCurrentIndex).build());
		api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 999);
		api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 999);
		api.setFleetTagline(FleetSide.PLAYER, message);
		api.setFleetTagline(FleetSide.ENEMY, message);
		api.addBriefingItem(message);
		for (String reason : reasons) api.addBriefingItem(reason);
		BattleNext_MissionBattleMap.apply(api, 0, 1f);
	}

	private void configureFleet(MissionDefinitionAPI api, FleetSide side, BattleNext_AssembledFleet assembledFleet, String tagline) {
		BattleNext_FleetModel definition = assembledFleet.getDefinition();
		api.initFleet(side, "", FleetGoal.ATTACK, definition.getTags().contains("default_ai"), 1);
		api.setFleetTagline(side, tagline);
		for (FleetMemberAPI member : assembledFleet.getMembers()) {
			api.addFleetMember(side, member);
		}
		BattleNext_PersonSkills.apply(api.getDefaultCommander(side), definition.getSkills());
	}
}
