package data.missions.BattleNext_pvp;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.scripts.migration.BattleNext_PvpMigrationPrototype;

public class MissionDefinition implements MissionDefinitionPlugin {

	private static final BattleNext_PvpMigrationPrototype MIGRATION_PROTOTYPE = new BattleNext_PvpMigrationPrototype();

	@Override
	public void defineMission(MissionDefinitionAPI api) {
		MIGRATION_PROTOTYPE.defineMission(api);
	}
}
