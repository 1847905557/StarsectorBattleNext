package data.missions.BattleNext_pve;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.scripts.combat.BattleNext_Scorekeeper;
import data.scripts.fleet.BattleNext_FleetAssembler;
import data.scripts.fleet.BattleNext_FleetAssembly;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.match.BattleNext_Match;
import data.scripts.match.BattleNext_MatchRecorder;
import data.scripts.member.BattleNext_MemberModel;
import org.lwjgl.input.Keyboard;

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
		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) expectedDp = Math.min(MAX_EXPECTED_DP, expectedDp + EXPECTED_DP_STEP);
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) expectedDp = Math.max(MIN_EXPECTED_DP, expectedDp - EXPECTED_DP_STEP);
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) factionIndex++;
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) factionIndex--;
		BattleNext_FleetAssembler assembler = new BattleNext_FleetAssembler();
		BattleNext_FleetAssembly player = assembler.assemble(createFleet("pve-player", "Player fleet", "wolf_CS"));
		BattleNext_FleetAssembly enemy = assembler.assemble(createFleet("pve-enemy", "Faction fleet " + Math.floorMod(factionIndex, FACTION_COUNT), "lasher_Standard"));
		configureFleet(api, FleetSide.PLAYER, player, true);
		configureFleet(api, FleetSide.ENEMY, enemy, false);
		BattleNext_Scorekeeper scorekeeper = new BattleNext_Scorekeeper("pve-player", player.getMembers(), "pve-enemy", enemy.getMembers());
		api.addPlugin(scorekeeper);
		api.addPlugin(new BattleNext_MatchRecorder(scorekeeper,
				new BattleNext_Match("pve", "pve-player", "pve-enemy", -1, 1f), null));
		api.addBriefingItem("Expected enemy DP: " + expectedDp);
		api.addBriefingItem("Use left/right to rotate faction selection.");
		api.initMap(-MAP_HALF_SIZE, MAP_HALF_SIZE, -MAP_HALF_SIZE, MAP_HALF_SIZE);
	}

	private BattleNext_FleetModel createFleet(String id, String name, String variantId) {
		BattleNext_MemberModel member = new BattleNext_MemberModel(variantId, "", "", List.of(), "", "", List.of());
		return new BattleNext_FleetModel(id, name, "", List.of(), List.of(), List.of(member));
	}

	private void configureFleet(MissionDefinitionAPI api, FleetSide side, BattleNext_FleetAssembly assembly,
			boolean defaultAi) {
		api.initFleet(side, "", FleetGoal.ATTACK, defaultAi, 1);
		api.setFleetTagline(side, assembly.getDefinition().getName());
		for (com.fs.starfarer.api.fleet.FleetMemberAPI member : assembly.getMembers()) {
			api.addFleetMember(side, member);
		}
	}
}
