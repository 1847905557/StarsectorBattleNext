package data.missions.BattleNext_lab;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.missions.BattleNext_MissionRestrictedUnlock;
import data.scripts.plugin.BattleNext_AllShipsStatusPlugin;
import data.scripts.plugin.BattleNext_DeploymentPlugin;
import data.scripts.plugin.BattleNext_FreeCameraPlugin;
import data.scripts.plugin.BattleNext_RttPlugin;

public class MissionDefinition implements MissionDefinitionPlugin {

	private static final float MAP_HALF_SIZE = 6000f;

	@Override
	public void defineMission(MissionDefinitionAPI api) {
		BattleNext_MissionRestrictedUnlock.getInstance().enable(api);

		api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 999);
		api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, false, 999);

		api.addBriefingItem("TEST");
		api.setFleetTagline(FleetSide.PLAYER, "1");
		api.setFleetTagline(FleetSide.ENEMY, "2");

		api.addToFleet(FleetSide.PLAYER, "wolf_CS", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.PLAYER, "wolf_CS", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.PLAYER, "wolf_CS", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.PLAYER, "wolf_CS", FleetMemberType.SHIP, false);

		api.addToFleet(FleetSide.ENEMY, "wolf_CS", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "wolf_CS", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "wolf_CS", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "wolf_CS", FleetMemberType.SHIP, false);

		api.initMap(-MAP_HALF_SIZE, MAP_HALF_SIZE, -MAP_HALF_SIZE, MAP_HALF_SIZE);

		api.addPlugin(new BattleNext_DeploymentPlugin());
		api.addPlugin(new BattleNext_FreeCameraPlugin());
		api.addPlugin(new BattleNext_AllShipsStatusPlugin());
		api.addPlugin(new BattleNext_RttPlugin());

	}
}
