package data.missions.BattleNext_pvp;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import org.apache.log4j.Logger;

import java.util.List;

public class MissionDefinition implements MissionDefinitionPlugin {

	private static final Logger LOGGER = Global.getLogger(MissionDefinition.class);

	@Override
	public void defineMission(MissionDefinitionAPI api) {

		api.addPlugin(new BattleNext_MissionPlugin());
	}

	public final static class BattleNext_MissionPlugin extends BaseEveryFrameCombatPlugin {

		private CombatEngineAPI engine = null;

		public BattleNext_MissionPlugin() {

		}

		private float timer = 0f;
		private boolean initialFleetDeployed = false;
		private boolean initialPlayerShown = false;
		private boolean initialSideShown = false;
		private boolean initialSoundPlay = false;
		private boolean revealMap = false;

		private int winner = -1;
		private boolean shouldShowEnd = false;
		private boolean shouldShameIfEnd = false;

		private float mapX;
		private float mapY;

		@Override
		public void init(CombatEngineAPI engine) {

			this.engine = engine;

		}

		@Override
		public void advance(float amount, List<InputEventAPI> events) {

			if (engine == null) return;

		}
	}
}