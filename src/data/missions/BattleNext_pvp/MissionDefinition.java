package data.missions.BattleNext_pvp;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_BattleSourceProvider;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_MatchProgress;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.combat.*;
import data.scripts.fleet.BattleNext_FleetAssembler;
import data.scripts.fleet.BattleNext_FleetAssembly;
import data.scripts.tools.BattleNext_KeyTuning;

import java.util.List;

public class MissionDefinition implements MissionDefinitionPlugin {

	private final BattleNext_BattleSourceProvider provider = new BattleNext_BattleSourceProvider();
	private final BattleNext_FleetAssembler assembler = new BattleNext_FleetAssembler();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");

	private static int quickBattleCurrentIndex = 0;

	@Override
	public void defineMission(MissionDefinitionAPI api) {

		List<BattleNext_Match> matches = provider.load();
		if (matches.isEmpty()) {
			String message = provider.getActiveTournament() == null
					? strings.get("no_valid_battle") : strings.get("no_pending_match");
			api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 1);
			api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 1);
			api.setFleetTagline(FleetSide.PLAYER, message);
			api.setFleetTagline(FleetSide.ENEMY, message);
			api.addBriefingItem(message);
			BattleNext_BattleMap.apply(api, 0, 1f);
			return;
		}

		BattleNext_TournamentModel tournament = provider.getActiveTournament();
		int mapIndex = 0;
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
		} else {
			match = matches.get(0);
			progress = progress(tournament, match);
		}

		BattleNext_FleetStore fleets = provider.getFleetStore();
		BattleNext_FleetAssembly player = assembler.assemble(fleets.get(match.getPlayer()));
		BattleNext_FleetAssembly enemy = assembler.assemble(fleets.get(match.getEnemy()));
		if (!player.isComplete() || !enemy.isComplete()) {
			api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 1);
			api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 1);
			api.setFleetTagline(FleetSide.PLAYER, player.isComplete()
					? player.getDefinition().getName() : strings.get("invalid_player"));
			api.setFleetTagline(FleetSide.ENEMY, enemy.isComplete()
					? enemy.getDefinition().getName() : strings.get("invalid_enemy"));
			api.addBriefingItem(strings.get("invalid_battle_assembly"));
			api.addBriefingItem(progress);
			BattleNext_BattleMap.apply(api, 0, 1f);
			return;
		}

		configureFleet(api, FleetSide.PLAYER, player);
		configureFleet(api, FleetSide.ENEMY, enemy);
		api.addBriefingItem("Map " + match.getMap() + ", map scale " + match.getMapScale());
		api.addBriefingItem(progress);
		BattleNext_BattleMap.apply(api, BattleNext_BattleMap.resolve(match.getMap(), mapIndex), match.getMapScale());
		api.addPlugin(new BattleNext_Deployment());
		api.addPlugin(new BattleNext_MissionPlugin());
		BattleNext_Scorekeeper scorekeeper = new BattleNext_Scorekeeper(player, enemy);
		api.addPlugin(scorekeeper);
		api.addPlugin(new BattleNext_BattleResultRecorder(scorekeeper, match, provider));
		api.addPlugin(new BattleNext_FleetBehavior(BattleNext_FleetBehavior.fromTags(player.getDefinition().getTags()),
				BattleNext_FleetBehavior.fromTags(enemy.getDefinition().getTags()), player.getDefinition(), enemy.getDefinition()));
		api.addPlugin(new BattleNext_FreeCameraPlugin(player.getDefinition().getName(), enemy.getDefinition().getName()));
		api.addPlugin(new BattleNext_PostReport(scorekeeper));
	}

	private String progress(BattleNext_TournamentModel tournament, BattleNext_Match match) {
		return BattleNext_MatchProgress.tournament(tournament, match).format(strings);
	}

	private void configureFleet(MissionDefinitionAPI api, FleetSide side, BattleNext_FleetAssembly assembly) {
		BattleNext_FleetModel definition = assembly.getDefinition();
		api.initFleet(side, "", FleetGoal.ATTACK, definition.getTags().contains("default_ai"), 1);
		api.setFleetTagline(side, definition.getName());
		for (FleetMemberAPI member : assembly.getMembers()) {
			api.addFleetMember(side, member);
		}
	}
}
