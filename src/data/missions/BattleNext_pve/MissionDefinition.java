package data.missions.BattleNext_pve;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import org.lwjgl.input.Keyboard;

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
		api.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 1);
		api.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, false, 1);
		api.addToFleet(FleetSide.PLAYER, "wolf_CS", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "lasher_Standard", FleetMemberType.SHIP, false);
		api.setFleetTagline(FleetSide.PLAYER, "Player fleet");
		api.setFleetTagline(FleetSide.ENEMY, "Faction fleet " + Math.floorMod(factionIndex, FACTION_COUNT));
		api.addBriefingItem("Expected enemy DP: " + expectedDp);
		api.addBriefingItem("Use left/right to rotate faction selection.");
		api.initMap(-MAP_HALF_SIZE, MAP_HALF_SIZE, -MAP_HALF_SIZE, MAP_HALF_SIZE);
	}
}
