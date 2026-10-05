package data.simulator;

import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.scripts.member.BattleNext_MemberBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.plugin.BattleNext_DeploymentPlugin;
import data.scripts.plugin.BattleNext_EnvironmentPlugin;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_SimulatorMission {

	private BattleNext_SimulatorMission() {
	}

	public static BattleNext_ScorekeeperPlugin define(MissionDefinitionAPI api, List<String> player,
			List<String> enemy, BattleNext_SimulationConfig config) {
		List<FleetMemberAPI> players = buildSide(api, FleetSide.PLAYER, player, config.playerAdmiral());
		List<FleetMemberAPI> enemies = buildSide(api, FleetSide.ENEMY, enemy, config.enemyAdmiral());
		api.initMap(-config.mapWidth() * 0.5f, config.mapWidth() * 0.5f, -config.mapHeight() * 0.5f, config.mapHeight() * 0.5f);
		api.addPlugin(new BattleNext_DeploymentPlugin());
		api.addPlugin(new BattleNext_EnvironmentPlugin(config.assaultAfterSeconds()));
		BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(players, enemies);
		api.addPlugin(scorekeeper);
		return scorekeeper;
	}

	private static List<FleetMemberAPI> buildSide(MissionDefinitionAPI api, FleetSide side, List<String> ids, boolean admiral) {
		api.initFleet(side, "", FleetGoal.ATTACK, admiral, 999);
		api.setFleetTagline(side, String.join(", ", ids));
		BattleNext_MemberBuilder builder = new BattleNext_MemberBuilder();
		List<FleetMemberAPI> members = new ArrayList<>();
		for (String id : ids) {
			ShipVariantAPI variant;
			try {
				variant = BattleNext_VariantBuilder.fromRegistered(id).build();
			} catch (Exception exception) {
				throw new IllegalArgumentException("Unable to assemble simulation variant " + id, exception);
			}
			if (variant.getHullSpec().getHullSize() == HullSize.FIGHTER) throw new IllegalArgumentException("Expected a registered root ship variant: " + id);
			BattleNext_MemberModel definition = new BattleNext_MemberModel(id, variant.getHullSpec().getHullName(), "", List.of(), "", "", List.of());
			FleetMemberAPI member = builder.build(definition, variant);
			api.addFleetMember(side, member);
			members.add(member);
		}
		return members;
	}
}
