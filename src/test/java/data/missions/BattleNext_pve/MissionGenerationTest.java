package data.missions.BattleNext_pve;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.mission.FleetSide;
import data.scripts.fleet.BattleNext_PveFleetGenerator;
import data.test.fixture.BattleNext_TestMissionFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

class MissionGenerationTest extends BattleNext_TestMissionFixture {

	@Test
	void mergedOverrideAddsHiddenFactionToSelectionCountsAndTagline() throws Exception {

		quickBattles("alpha,beta,0,1\n");
		pveFactionRows.put(new JSONObject().put("id", "hidden"));
		var hidden = new MissionState();
		new MissionDefinition().defineMission(hidden.getApi());
		assertEquals("hidden", generatedParameters.get(0).factionId);
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 1, 3, 1, 2), hidden.getBriefing().get(1));
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_enemy_tagline"),
				"hidden", 100, 45), hidden.getNames().get(FleetSide.ENEMY));
		keys.add(Keyboard.KEY_RIGHT);
		var next = new MissionState();
		new MissionDefinition().defineMission(next.getApi());
		assertEquals("mod_faction", generatedParameters.get(1).factionId);
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 2, 3, 1, 2), next.getBriefing().get(1));
		keys.clear();
		pveFactionRows.put(0, new JSONObject().put("id", "hegemony"));
		var reloaded = new MissionState();
		new MissionDefinition().defineMission(reloaded.getApi());
		assertEquals("hegemony", generatedParameters.get(2).factionId);
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 2, 2, 1, 2), reloaded.getBriefing().get(1));
	}

	@Test
	void overrideAllowsMissionWhenAllLoadedFactionsAreHidden() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		factionSpecs.removeIf(faction -> faction.isShowInIntelTab());
		pveFactionRows.put(new JSONObject().put("id", "hidden"));
		var mission = new MissionState();
		new MissionDefinition().defineMission(mission.getApi());
		assertEquals("hidden", generatedParameters.get(0).factionId);
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 1, 1, 1, 2), mission.getBriefing().get(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"read", "parse", "row", "runtime"})
	void factionSourceErrorsPublishUsableFactionsAndInternalErrorsBlockTheMission(String failure) throws Exception {

		quickBattles("alpha,beta,0,1\n");
		if (failure.equals("row")) {
			pveFactionRows.put("Malformed merged row");
		} else {
			pveFactionReadFailure = switch (failure) {
				case "read" -> new IOException("Injected faction CSV read failure");
				case "parse" -> new JSONException("Injected faction CSV parse failure");
				default -> new IllegalStateException("Injected faction CSV runtime failure");
			};
		}
		var blocked = new MissionState();
		new MissionDefinition().defineMission(blocked.getApi());
		if (failure.equals("runtime")) {
			assertEquals(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_factions_load_failed"), blocked.getBriefing().get(0));
			assertTrue(blocked.getBriefing().get(1).contains(BattleNext_PveFleetGenerator.FACTIONS_PATH));
			assertTrue(blocked.getPlugins().isEmpty());
			assertTrue(generatedParameters.isEmpty());
		} else {
			assertEquals("mod_faction", generatedParameters.get(0).factionId);
			assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_selection_summary"), 1, 2, 1, 2), blocked.getBriefing().get(1));
			assertFalse(blocked.getPlugins().isEmpty());
		}
		pveFactionReadFailure = null;
		pveFactionRows.put(0, new JSONObject().put("id", "hidden"));
		var recovered = new MissionState();
		new MissionDefinition().defineMission(recovered.getApi());
		assertEquals("hidden", generatedParameters.get(generatedParameters.size() - 1).factionId);
	}

	@ParameterizedTest
	@ValueSource(strings = {"null", "empty", "exception", "inflation"})
	void failedGenerationKeepsSelectionAndNextClickRetries(String failure) throws Exception {

		quickBattles("alpha,beta,0,1\n");
		Function<FleetParamsV3, CampaignFleetAPI> original = generator;
		generator = params -> switch (failure) {
			case "null" -> null;
			case "empty" -> new GeneratedFleet("empty", params.factionId, List.of(), player).getApi();
			case "inflation" -> proxy(CampaignFleetAPI.class, (method, args) -> {
				if (method.equals("inflateIfNeeded")) throw new IllegalStateException("Inflation failed");
				return null;
			});
			default -> throw new IllegalStateException("Generation failed");
		};
		keys.addAll(List.of(Keyboard.KEY_RIGHT, Keyboard.KEY_UP, Keyboard.KEY_D));
		var blocked = new MissionState();
		new MissionDefinition().defineMission(blocked.getApi());
		assertEquals(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_generation_failed"), blocked.getNames().get(FleetSide.ENEMY));
		assertTrue(blocked.getBriefing().get(1).startsWith("hegemony: "));
		assertTrue(blocked.getPlugins().isEmpty());
		assertTrue(blocked.getMembers().get(FleetSide.PLAYER).isEmpty());
		generator = original;
		keys.clear();
		var retry = new MissionState();
		new MissionDefinition().defineMission(retry.getApi());
		assertEquals("child_variant", retry.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals("hegemony", generatedParameters.get(1).factionId);
		assertEquals(110f, generatedParameters.get(1).combatPts);
	}

	@Test
	void emptySourcesAndFactionsUseBlockedMissionPresentation() throws Exception {
		quickBattles("# alpha,beta,0,1\n");
		var noRows = new MissionState();
		new MissionDefinition().defineMission(noRows.getApi());
		assertEquals(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_no_valid_battle"), noRows.getBriefing().get(0));
		assertTrue(noRows.getPlugins().isEmpty());
		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,beta,0,1\n");
		factionSpecs.removeIf(faction -> faction.isShowInIntelTab());
		var noFactions = new MissionState();
		new MissionDefinition().defineMission(noFactions.getApi());
		assertEquals(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_no_factions"), noFactions.getBriefing().get(0));
		assertTrue(generatedParameters.isEmpty());
		assertTrue(noFactions.getPlugins().isEmpty());
	}

}
