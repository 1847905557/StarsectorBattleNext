package data.missions.BattleNext_pvp;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.scripts.fleet.BattleNext_FleetAssembler;
import data.scripts.fleet.BattleNext_FleetAssembly;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.match.BattleNext_Match;
import data.scripts.match.BattleNext_MatchListProvider;
import data.scripts.match.BattleNext_MatchRecorder;
import data.scripts.combat.BattleNext_BattleMap;
import data.scripts.combat.BattleNext_Deployment;
import data.scripts.combat.BattleNext_MissionPlugin;
import data.scripts.combat.BattleNext_FleetBehavior;
import data.scripts.combat.BattleNext_PresentationPlugin;
import data.scripts.combat.BattleNext_PostReport;
import data.scripts.combat.BattleNext_Scorekeeper;
import org.lwjgl.input.Keyboard;

import java.util.List;

class NativeMission {

	private final BattleNext_MatchListProvider provider;
	private final BattleNext_FleetAssembler assembler = new BattleNext_FleetAssembler();
	private int currentIndex;

	NativeMission(BattleNext_MatchListProvider provider) {
		this.provider = provider;
	}

	void defineMission(MissionDefinitionAPI api) {
		List<BattleNext_Match> matches = provider.load();
		if (matches.isEmpty()) {
			api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 1);
			api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 1);
			api.addBriefingItem("No valid player/enemy match is available.");
			BattleNext_BattleMap.apply(api, 0, 1f);
			return;
		}

		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) currentIndex++;
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) currentIndex--;
		BattleNext_Match match = matches.get(Math.floorMod(currentIndex, matches.size()));
		BattleNext_FleetStore fleets = provider.getFleetStore();
		BattleNext_FleetAssembly player = assembler.assemble(fleets.get(match.getPlayer()));
		BattleNext_FleetAssembly enemy = assembler.assemble(fleets.get(match.getEnemy()));
		if (!player.isComplete() || !enemy.isComplete()) {
			api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 1);
			api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 1);
			api.addBriefingItem("Selected match has an invalid fleet assembly.");
			BattleNext_BattleMap.apply(api, 0, 1f);
			return;
		}
		configureFleet(api, FleetSide.PLAYER, player);
		configureFleet(api, FleetSide.ENEMY, enemy);
		api.addBriefingItem("Player: " + player.getDefinition().getName());
		api.addBriefingItem("Enemy: " + enemy.getDefinition().getName());
		api.addBriefingItem("Map " + match.getMap() + ", size " + match.getSize());
		BattleNext_BattleMap.apply(api, BattleNext_BattleMap.resolve(match.getMap(), currentIndex), match.getSize());
		api.addPlugin(new BattleNext_Deployment());
		api.addPlugin(new BattleNext_MissionPlugin());
		BattleNext_Scorekeeper scorekeeper = new BattleNext_Scorekeeper(match.getPlayer(), player, match.getEnemy(), enemy);
		api.addPlugin(scorekeeper);
		api.addPlugin(new BattleNext_MatchRecorder(scorekeeper, match, provider));
		api.addPlugin(new BattleNext_FleetBehavior(BattleNext_FleetBehavior.fromTags(player.getDefinition().getTags()),
				BattleNext_FleetBehavior.fromTags(enemy.getDefinition().getTags()), player.getDefinition(), enemy.getDefinition()));
		api.addPlugin(new BattleNext_PresentationPlugin(player.getDefinition().getName(), enemy.getDefinition().getName()));
		api.addPlugin(new BattleNext_PostReport(scorekeeper));
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
