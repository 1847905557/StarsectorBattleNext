package data.scripts.plugin;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_BattleClockPluginTest extends BattleNext_TestGameFixture {

	@ParameterizedTest
	@ValueSource(ints = {0, 2})
	void timeoutAppliesOnceAtItsThresholdWithGraphicsDisabledAndReleasesOnCombatEnd(int timeLimit) {
		BattleNext_Settings.save(false, false, false, false, 500f, 1f, 3f, 50, 0);
		List<String> personalities = new ArrayList<>();
		List<String> assaults = new ArrayList<>();
		List<Integer> reveals = new ArrayList<>();
		List<Object> removed = new ArrayList<>();
		float[] elapsed = {1.99f};
		boolean[] combatOver = {false};
		PersonAPI captain = proxy(PersonAPI.class, (method, args) -> {
			if (method.equals("setPersonality")) personalities.add((String) args[0]);
			return null;
		});
		ShipAPI living = proxy(ShipAPI.class, (method, args) -> switch (method) {
			case "isAlive" -> true;
			case "getCaptain" -> captain;
			default -> null;
		});
		ShipAPI wreck = proxy(ShipAPI.class, (method, args) -> switch (method) {
			case "isAlive" -> false;
			case "getCaptain" -> captain;
			default -> null;
		});
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> switch (method) {
			case "getShips" -> List.of(living, wreck);
			case "getTotalElapsedTime" -> elapsed[0];
			case "isCombatOver" -> combatOver[0];
			case "getMapWidth" -> 12000f;
			case "getMapHeight" -> 9000f;
			case "getFleetManager" -> proxy(CombatFleetManagerAPI.class, (name, values) -> name.equals("getTaskManager")
					? proxy(CombatTaskManagerAPI.class, (key, input) -> {
						assertEquals("setFullAssault", key);
						assertEquals(true, input[0]);
						assaults.add(args[0] + ":" + values[0]);
						return null;
					}) : null);
			case "getFogOfWar" -> proxy(FogOfWarAPI.class, (name, values) -> {
				assertEquals("revealAroundPoint", name);
				assertEquals(0f, values[1]);
				assertEquals(0f, values[2]);
				assertEquals(42000f, values[3]);
				reveals.add((Integer) args[0]);
				return null;
			});
			case "removePlugin" -> removed.add(args[0]);
			default -> null;
		});
		BattleNext_BattleClockPlugin clock = new BattleNext_BattleClockPlugin(timeLimit, null);
		clock.init(engine);
		clock.advance(0.1f, List.of());
		assertTrue(personalities.isEmpty());
		elapsed[0] = 2f;
		clock.advance(0.1f, List.of());
		elapsed[0] = 4f;
		clock.advance(0.1f, List.of());
		assertEquals(timeLimit > 0 ? List.of(Personalities.RECKLESS) : List.of(), personalities);
		assertEquals(timeLimit > 0 ? List.of("PLAYER:false", "PLAYER:true", "ENEMY:false") : List.of(), assaults);
		assertEquals(timeLimit > 0 ? List.of(0, 1) : List.of(), reveals);
		combatOver[0] = true;
		clock.advance(0.1f, List.of());
		clock.advance(0.1f, List.of());
		assertEquals(List.of(clock), removed);
	}
}
