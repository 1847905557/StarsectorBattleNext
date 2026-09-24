package data.missions.BattleNext_lab;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

public class MissionDefinition implements MissionDefinitionPlugin {

	private static final float MAP_HALF_SIZE = 5500f;

	@Override
	public void defineMission(MissionDefinitionAPI api) {
		api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 1);
		api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, false, 1);
		api.addBriefingItem("BattleNext lab is not available yet.");
		api.initMap(-MAP_HALF_SIZE, MAP_HALF_SIZE, -MAP_HALF_SIZE, MAP_HALF_SIZE);
	}
}
