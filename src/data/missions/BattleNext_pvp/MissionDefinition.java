package data.missions.BattleNext_pvp;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MissionDefinition implements MissionDefinitionPlugin {

	private static final Logger log = Global.getLogger(MissionDefinition.class);

	@Override
	public void defineMission(MissionDefinitionAPI api) {

		api.addPlugin(new BattleNext_MissionPlugin(playerFleet, enemyFleet));
	}

	public final static class BattleNext_MissionPlugin extends BaseEveryFrameCombatPlugin {

		private CombatEngineAPI engine = null;
		private final Fleet playerFleet;
		private final Fleet enemyFleet;

		public Plugin(Fleet player, Fleet enemy) {
			this.playerFleet = player;
			this.enemyFleet = enemy;
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