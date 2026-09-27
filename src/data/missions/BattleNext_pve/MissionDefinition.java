package data.missions.BattleNext_pve;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.fleet.BattleNext_FleetBuilder;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_KeyTuning;

import java.util.List;

public class MissionDefinition implements MissionDefinitionPlugin {

	private static final int MIN_EXPECTED_DP = 10;
	private static final int MAX_EXPECTED_DP = 2000;
	private static final int EXPECTED_DP_STEP = 10;
	private static final int FACTION_COUNT = 10;
	private static final float MAP_HALF_SIZE = 5500f;
	private int expectedDp = 100;
	private int factionIndex;

	@Override
	public void defineMission(MissionDefinitionAPI api) {
		expectedDp = BattleNext_KeyTuning.adjustVerticalClamped(expectedDp, EXPECTED_DP_STEP, MIN_EXPECTED_DP, MAX_EXPECTED_DP);
		factionIndex = BattleNext_KeyTuning.adjustHorizontalWrapped(factionIndex, FACTION_COUNT);
		BattleNext_FleetBuilder fleetBuilder = new BattleNext_FleetBuilder();
		BattleNext_AssembledFleet assembledPlayerFleet = fleetBuilder.build(createFleet("pve-player", "Player fleet", "wolf_CS"));
		BattleNext_AssembledFleet assembledEnemyFleet = fleetBuilder.build(createFleet("pve-enemy", "Faction fleet " + factionIndex, "lasher_Standard"));
		configureFleet(api, FleetSide.PLAYER, assembledPlayerFleet, true);
		configureFleet(api, FleetSide.ENEMY, assembledEnemyFleet, false);
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(assembledPlayerFleet.getMembers(), assembledEnemyFleet.getMembers());
		api.addPlugin(scorekeeper);
		api.addPlugin(new BattleNext_BattleResultRecorder(scorekeeper,
				new BattleNext_Match("pve", "pve-player", "pve-enemy", -1, 1f), null));
		api.addBriefingItem("Expected enemy DP: " + expectedDp);
		api.addBriefingItem("Use left/right to rotate faction selection.");
		api.initMap(-MAP_HALF_SIZE, MAP_HALF_SIZE, -MAP_HALF_SIZE, MAP_HALF_SIZE);
	}

	private BattleNext_FleetModel createFleet(String id, String name, String variantId) {
		BattleNext_MemberModel member = new BattleNext_MemberModel(variantId, "", "", List.of(), "", "", List.of());
		return new BattleNext_FleetModel(id, name, List.of(), List.of(), List.of(member));
	}

	private void configureFleet(MissionDefinitionAPI api, FleetSide side, BattleNext_AssembledFleet assembledFleet,
			boolean defaultAi) {
		api.initFleet(side, "", FleetGoal.ATTACK, defaultAi, 1);
		api.setFleetTagline(side, assembledFleet.getDefinition().getName());
		for (com.fs.starfarer.api.fleet.FleetMemberAPI member : assembledFleet.getMembers()) {
			api.addFleetMember(side, member);
		}
	}
}
