package data.missions.BattleNext_pvp;

import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.scripts.match.BattleNext_MatchListProvider;
import data.scripts.migration.BattleNext_PvpMigrationPrototype;

public class MissionDefinition implements MissionDefinitionPlugin {

	private static final BattleNext_PvpMigrationPrototype MIGRATION_PROTOTYPE = new BattleNext_PvpMigrationPrototype();
	private static final BattleNext_MatchListProvider MATCH_PROVIDER = new BattleNext_MatchListProvider();
	private static final NativeMission NATIVE_MISSION = new NativeMission(MATCH_PROVIDER);

	@Override
	public void defineMission(MissionDefinitionAPI api) {
		if (MATCH_PROVIDER.hasLegacyMatches()) MIGRATION_PROTOTYPE.defineMission(api);
		else NATIVE_MISSION.defineMission(api);
	}
}
