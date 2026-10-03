package data.missions.BattleNext_pvp;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.missions.BattleNext_MissionBattleMap;
import data.missions.BattleNext_MissionRestrictedUnlock;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_MatchProgress;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.order.BattleNext_FleetOrderBehavior;
import data.scripts.plugin.*;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tools.BattleNext_KeyTuning;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_Settings;

import java.util.List;

public class MissionDefinition implements MissionDefinitionPlugin {

	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private final BattleNext_FleetBuilder fleetBuilder = new BattleNext_FleetBuilder();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection ruleStrings = BattleNext_I18nSection.getInstance("Rule", "BattleNext_rule_");

	private static int quickBattleCurrentIndex = 0;

	@Override
	public void defineMission(MissionDefinitionAPI api) {
		BattleNext_MissionRestrictedUnlock.getInstance().enable(api);

		List<BattleNext_Match> matches = provider.load();
		if (matches.isEmpty()) {
			String message = provider.getActiveTournament() == null
					? strings.get("no_valid_battle") : strings.get("no_pending_match");
			showBlocked(api, message, provider.getWarnings());
			return;
		}

		BattleNext_TournamentModel tournament = provider.getActiveTournament();
		int mapIndex = 0;
		String reportMarker;
		BattleNext_Match match;
		String progress;
		if (tournament == null) {
			quickBattleCurrentIndex = BattleNext_KeyTuning.adjustHorizontalWrapped(
					quickBattleCurrentIndex, matches.size() * 2);
			int quickBattleMatchIndex = quickBattleCurrentIndex / 2;
			int quickBattleRoundIndex = quickBattleCurrentIndex % 2;
			match = matches.get(quickBattleMatchIndex);
			mapIndex = quickBattleMatchIndex;
			if (quickBattleRoundIndex == 1) match.setSides(match.getEnemy(), match.getPlayer());
			progress = BattleNext_MatchProgress.quickBattle(quickBattleMatchIndex + 1, matches.size(),
					quickBattleRoundIndex + 1).format(strings);
			reportMarker = "Quick Match " + (quickBattleMatchIndex + 1) + " Round " + (quickBattleRoundIndex + 1);
		} else {
			match = matches.get(0);
			progress = progress(tournament, match);
			reportMarker = "Tournament " + tournament.getName() + " Match " + match.getId()
					+ " Round " + (match.getRounds().size() + 1);
			if (!provider.getRuleWarnings().isEmpty()
					&& !BattleNext_BattleSignals.consumeAuthorization(tournament.getId(), match.getId())) {
				showBlocked(api, ruleStrings.get("match_blocked"), provider.getRuleWarnings());
				api.addBriefingItem(progress);
				return;
			}
			BattleNext_BattleSignals.clearAuthorization();
		}

		for (String warning : provider.getWarnings()) {
			api.addBriefingItem(warning);
		}
		BattleNext_FleetStore fleets = provider.getFleetStore();
		BattleNext_AssembledFleet assembledPlayerFleet = fleetBuilder.build(fleets.get(match.getPlayer()));
		BattleNext_AssembledFleet assembledEnemyFleet = fleetBuilder.build(fleets.get(match.getEnemy()));
		if (!assembledPlayerFleet.isComplete() || !assembledEnemyFleet.isComplete()) {
			api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 999);
			api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 999);
			api.setFleetTagline(FleetSide.PLAYER, assembledPlayerFleet.isComplete()
					? assembledPlayerFleet.getDefinition().getName() : strings.get("invalid_player_fleet"));
			api.setFleetTagline(FleetSide.ENEMY, assembledEnemyFleet.isComplete()
					? assembledEnemyFleet.getDefinition().getName() : strings.get("invalid_enemy_fleet"));
			api.addBriefingItem(strings.get("invalid_battle_assembly"));
			api.addBriefingItem(progress);
			BattleNext_MissionBattleMap.apply(api, 0, 1f);
			return;
		}

		configureFleet(api, FleetSide.PLAYER, assembledPlayerFleet);
		configureFleet(api, FleetSide.ENEMY, assembledEnemyFleet);
		api.addBriefingItem(strings.format("map_summary", match.getMap(), match.getMapScale()));
		api.addBriefingItem(progress);
		BattleNext_MissionBattleMap.apply(api, BattleNext_MissionBattleMap.resolve(match.getMap(), mapIndex), match.getMapScale());
		float timeLimit = BattleNext_Settings.getBattleTimeLimit();
		api.addPlugin(new BattleNext_DeploymentPlugin());
		api.addPlugin(new BattleNext_EnvironmentPlugin(timeLimit));
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(assembledPlayerFleet, assembledEnemyFleet);
		api.addPlugin(scorekeeper);
		api.addPlugin(new BattleNext_BattleClockPlugin(timeLimit, scorekeeper));
		api.addPlugin(new BattleNext_BattleResultRecorder(scorekeeper, match, provider,
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
				BattleNext_Logger.fields().put("match", match).put("tournament", tournament).put("player", assembledPlayerFleet.getDefinition()).put("enemy", assembledEnemyFleet.getDefinition()).put("player_mode", BattleNext_FleetOrderBehavior.fromTags(assembledPlayerFleet.getDefinition().getTags())).put("enemy_mode", BattleNext_FleetOrderBehavior.fromTags(assembledEnemyFleet.getDefinition().getTags())).put("time_limit", timeLimit).put("rtt", BattleNext_Settings.isRttInBattle()).put("marker", reportMarker).build());
	}

	private String progress(BattleNext_TournamentModel tournament, BattleNext_Match match) {
		return BattleNext_MatchProgress.tournament(tournament, match).format(strings);
	}

	private void showBlocked(MissionDefinitionAPI api, String message, List<String> reasons) {
		BattleNext_Logger.warn(MissionDefinition.class, "battle.creation.rejected",
				BattleNext_Logger.fields().put("message", message).put("reasons", reasons).build());
		api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 1);
		api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 1);
		api.setFleetTagline(FleetSide.PLAYER, message);
		api.setFleetTagline(FleetSide.ENEMY, message);
		api.addBriefingItem(message);
		for (String reason : reasons) api.addBriefingItem(reason);
		BattleNext_MissionBattleMap.apply(api, 0, 1f);
	}

	private void configureFleet(MissionDefinitionAPI api, FleetSide side, BattleNext_AssembledFleet assembledFleet) {
		BattleNext_FleetModel definition = assembledFleet.getDefinition();
		api.initFleet(side, "", FleetGoal.ATTACK, definition.getTags().contains("default_ai"), 1);
		api.setFleetTagline(side, definition.getName());
		for (FleetMemberAPI member : assembledFleet.getMembers()) {
			api.addFleetMember(side, member);
		}
		BattleNext_PersonSkills.apply(api.getDefaultCommander(side), definition.getSkills());
	}
}
