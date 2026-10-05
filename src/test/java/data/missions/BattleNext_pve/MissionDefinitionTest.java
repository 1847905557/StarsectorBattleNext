package data.missions.BattleNext_pve;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import data.model.report.BattleNext_BattleReportStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_BattleResultRecorder;
import data.scripts.battle.BattleNext_Match;
import data.scripts.plugin.BattleNext_RttPlugin;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_BattleSignals;
import data.scripts.tools.BattleNext_KeyTuning;
import data.scripts.tools.BattleNext_Settings;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.io.IOException;

class MissionDefinitionTest extends BattleNext_TestMissionFixture {

	@Test
	void projectTuningCalculationsHandleWrappingClampingAndOpposingKeys() throws Exception {

		var wrapped = BattleNext_KeyTuning.class.getDeclaredMethod("adjustWrapped", int.class, int.class, boolean.class, boolean.class);
		var clamped = BattleNext_KeyTuning.class.getDeclaredMethod("adjustClamped", int.class, int.class, int.class, int.class, boolean.class, boolean.class);
		wrapped.setAccessible(true);
		clamped.setAccessible(true);
		assertEquals(1, wrapped.invoke(null, 0, 2, true, false));
		assertEquals(0, wrapped.invoke(null, 1, 2, false, true));
		assertEquals(1, wrapped.invoke(null, 1, 2, true, true));
		assertEquals(110, clamped.invoke(null, 100, 10, 10, 2000, false, true));
		assertEquals(10, clamped.invoke(null, 10, 10, 10, 2000, true, false));
		assertEquals(2000, clamped.invoke(null, 2000, 10, 10, 2000, false, true));
		assertEquals(100, clamped.invoke(null, 100, 10, 10, 2000, true, true));
	}

	@Test
	void missionEntrypointsUseImplicitConstructionAndSharedKeyboardRouting() throws Exception {

		JavaParser parser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17).setCharacterEncoding(StandardCharsets.UTF_8));
		try (var paths = Files.walk(Path.of("src/data/missions"))) {
			for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
				var parsed = parser.parse(path);
				assertTrue(parsed.isSuccessful(), path.toString());
				for (var type : parsed.getResult().orElseThrow().findAll(ClassOrInterfaceDeclaration.class)) {
					if (!type.getNameAsString().equals("MissionDefinition")) continue;
					assertTrue(type.getConstructors().isEmpty(), path.toString());
					assertTrue(type.getFields().stream().flatMap(field -> field.getVariables().stream())
							.noneMatch(variable -> variable.getType().asString().equals("IntPredicate")), path.toString());
					assertTrue(type.findAll(MethodCallExpr.class).stream().noneMatch(call ->
							call.getScope().map(scope -> scope.toString().equals("Keyboard")).orElse(false)
									&& call.getNameAsString().equals("isKeyDown")), path.toString());
				}
			}
		}
	}

	@Test
	void pvpAndPveReadTheSameSharedKeyboardInput() throws Exception {

		quickBattles("alpha,beta,0,1\n");
		MissionDefinitionPlugin pvp = (MissionDefinitionPlugin) Class.forName("data.missions.BattleNext_pvp.MissionDefinition")
				.getDeclaredConstructor().newInstance();
		keys.add(Keyboard.KEY_RIGHT);
		var pvpNext = new MissionState();
		pvp.defineMission(pvpNext.getApi());
		var pveNext = new MissionState();
		new MissionDefinition().defineMission(pveNext.getApi());
		assertEquals("child_variant", pvpNext.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals("hegemony", generatedParameters.get(0).factionId);
		keys.clear();
		keys.add(Keyboard.KEY_LEFT);
		var pvpPrevious = new MissionState();
		pvp.defineMission(pvpPrevious.getApi());
		var pvePrevious = new MissionState();
		new MissionDefinition().defineMission(pvePrevious.getApi());
		assertEquals("parent_variant", pvpPrevious.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals("mod_faction", generatedParameters.get(1).factionId);
	}

	@Test
	void cyclesEachValidRowsSidesWithDuplicatesAndTheirMaps() throws Exception {

		quickBattles("alpha,beta,2,1.5\nalpha,alpha,-1,2\n");
		List<String> variants = List.of("parent_variant", "child_variant", "parent_variant", "parent_variant");
		for (int index = 0; index < variants.size(); index++) {
			var mission = new MissionState();
			new MissionDefinition().defineMission(mission.getApi());
			assertEquals(variants.get(index), mission.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
			assertEquals(List.of(index < 2 ? "Map 2, map scale 1.5" : "Map -1, map scale 2.0",
					"Faction 1 / 2, Player " + (index + 1) + " / " + variants.size()), mission.getBriefing());
			assertEquals("Standard", mission.getNames().get(FleetSide.PLAYER));
			assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_enemy_tagline"),
					"mod_faction", 100, 45), mission.getNames().get(FleetSide.ENEMY));
			for (FleetSide side : FleetSide.values()) {
				assertEquals(1L, mission.getEvents().stream().filter(event -> event.equals("tagline:" + side)).count());
			}
			float[] map = index < 2 ? new float[]{-9000f, 9000f, -6750f, 6750f}
					: new float[]{-12000f, 12000f, -8000f, 8000f};
			assertArrayEquals(map, mission.getMap());
			keys.add(Keyboard.KEY_D);
		}
		var wrapped = new MissionState();
		new MissionDefinition().defineMission(wrapped.getApi());
		assertEquals("Faction 1 / 2, Player 1 / 4", wrapped.getBriefing().get(1));
		keys.clear();
		keys.add(Keyboard.KEY_A);
		var previous = new MissionState();
		new MissionDefinition().defineMission(previous.getApi());
		assertEquals("Faction 1 / 2, Player 4 / 4", previous.getBriefing().get(1));
		assertEquals(variants.size() + 2, generatedParameters.size());
	}

	@Test
	void combinesIndependentKeysCancelsOppositesAndClampsBudgets() throws Exception {

		quickBattles("alpha,beta,0,1\n");
		keys.addAll(List.of(Keyboard.KEY_D, Keyboard.KEY_RIGHT, Keyboard.KEY_UP));
		var selected = new MissionState();
		new MissionDefinition().defineMission(selected.getApi());
		assertEquals("child_variant", selected.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals("hegemony", generatedParameters.get(0).factionId);
		assertEquals(110f, generatedParameters.get(0).combatPts);
		assertEquals(List.of("Map 0, map scale 1.0", "Faction 2 / 2, Player 2 / 2"), selected.getBriefing());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_enemy_tagline"),
				"hegemony", 110, 45), selected.getNames().get(FleetSide.ENEMY));
		keys.addAll(List.of(Keyboard.KEY_A, Keyboard.KEY_LEFT, Keyboard.KEY_DOWN));
		new MissionDefinition().defineMission(new MissionState().getApi());
		assertEquals("hegemony", generatedParameters.get(1).factionId);
		assertEquals(110f, generatedParameters.get(1).combatPts);
		keys.clear();
		select("expectedFp", 10);
		keys.add(Keyboard.KEY_DOWN);
		new MissionDefinition().defineMission(new MissionState().getApi());
		assertEquals(10f, generatedParameters.get(2).combatPts);
		select("expectedFp", 2000);
		keys.clear();
		keys.add(Keyboard.KEY_UP);
		new MissionDefinition().defineMission(new MissionState().getApi());
		assertEquals(2000f, generatedParameters.get(3).combatPts);
		keys.clear();
		keys.add(Keyboard.KEY_RIGHT);
		new MissionDefinition().defineMission(new MissionState().getApi());
		assertEquals("mod_faction", generatedParameters.get(4).factionId);
	}

	@Test
	void reloadNormalizesSelectionAgainstCurrentRowsAndFactions() throws Exception {
		quickBattles("alpha,beta,2,1\nalpha,alpha,3,1\n");
		select("quickBattleCurrentIndex", 3);
		select("factionIndex", 1);
		new MissionDefinition().defineMission(new MissionState().getApi());
		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,beta,4,2\n");
		factionSpecs.removeIf(faction -> faction.getId().equals("hegemony"));
		var current = new MissionState();
		new MissionDefinition().defineMission(current.getApi());
		assertEquals("child_variant", current.getMembers().get(FleetSide.PLAYER).get(0).getVariant().getHullVariantId());
		assertEquals(List.of("Map 4, map scale 2.0", "Faction 1 / 1, Player 2 / 2"), current.getBriefing());
		assertEquals("mod_faction", generatedParameters.get(1).factionId);
		assertArrayEquals(new float[]{-10000f, 10000f, -12000f, 12000f}, current.getMap());
	}

	@Test
	void successfulBriefingAppendsWarningsAfterMapAndSelection() throws Exception {
		quickBattles("missing,beta,0,1\nalpha,beta,2,1.5\n");
		var mission = new MissionState();
		new MissionDefinition().defineMission(mission.getApi());
		assertEquals(List.of("Map 2, map scale 1.5", "Faction 1 / 2, Player 1 / 2"), mission.getBriefing().subList(0, 2));
		assertTrue(mission.getBriefing().get(2).contains("missing player/enemy fleet reference"));
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_enemy_tagline"),
				"mod_faction", 100, 45), mission.getNames().get(FleetSide.ENEMY));
	}

	@Test
	void mergedOverrideAddsHiddenFactionToSelectionCountsAndTagline() throws Exception {

		quickBattles("alpha,beta,0,1\n");
		pveFactionRows.put(new JSONObject().put("id", "hidden"));
		var hidden = new MissionState();
		new MissionDefinition().defineMission(hidden.getApi());
		assertEquals("hidden", generatedParameters.get(0).factionId);
		assertEquals("Faction 1 / 3, Player 1 / 2", hidden.getBriefing().get(1));
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_pve_enemy_tagline"),
				"hidden", 100, 45), hidden.getNames().get(FleetSide.ENEMY));
		keys.add(Keyboard.KEY_RIGHT);
		var next = new MissionState();
		new MissionDefinition().defineMission(next.getApi());
		assertEquals("mod_faction", generatedParameters.get(1).factionId);
		assertEquals("Faction 2 / 3, Player 1 / 2", next.getBriefing().get(1));
		keys.clear();
		pveFactionRows.put(0, new JSONObject().put("id", "hegemony"));
		var reloaded = new MissionState();
		new MissionDefinition().defineMission(reloaded.getApi());
		assertEquals("hegemony", generatedParameters.get(2).factionId);
		assertEquals("Faction 2 / 2, Player 1 / 2", reloaded.getBriefing().get(1));
	}

	@Test
	void overrideAllowsMissionWhenAllLoadedFactionsAreHidden() throws Exception {
		quickBattles("alpha,beta,0,1\n");
		factionSpecs.removeIf(faction -> faction.isShowInIntelTab());
		pveFactionRows.put(new JSONObject().put("id", "hidden"));
		var mission = new MissionState();
		new MissionDefinition().defineMission(mission.getApi());
		assertEquals("hidden", generatedParameters.get(0).factionId);
		assertEquals("Faction 1 / 1, Player 1 / 2", mission.getBriefing().get(1));
	}

	@ParameterizedTest
	@ValueSource(strings = {"read", "parse", "row", "runtime"})
	void factionCsvFailureBlocksMissionAndRecoversOnNextClick(String failure) throws Exception {

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
		assertEquals("Could not load the PvE faction list.", blocked.getBriefing().get(0));
		assertTrue(blocked.getBriefing().get(1).contains(BattleNext_PveFleetGenerator.FACTIONS_PATH));
		assertEquals(blocked.getBriefing().get(0), blocked.getNames().get(FleetSide.PLAYER));
		assertEquals(blocked.getBriefing().get(0), blocked.getNames().get(FleetSide.ENEMY));
		assertTrue(blocked.getPlugins().isEmpty());
		assertTrue(generatedParameters.isEmpty());
		pveFactionReadFailure = null;
		pveFactionRows.put(0, new JSONObject().put("id", "hidden"));
		var recovered = new MissionState();
		new MissionDefinition().defineMission(recovered.getApi());
		assertEquals("hidden", generatedParameters.get(0).factionId);
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
		assertEquals("Could not generate the selected enemy fleet.", blocked.getNames().get(FleetSide.ENEMY));
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
		assertEquals("No valid battle is available.", noRows.getBriefing().get(0));
		assertTrue(noRows.getPlugins().isEmpty());
		write("tournament/quick_battle.csv", "player,enemy,map,size\nalpha,beta,0,1\n");
		factionSpecs.removeIf(faction -> faction.isShowInIntelTab());
		var noFactions = new MissionState();
		new MissionDefinition().defineMission(noFactions.getApi());
		assertEquals("No PvE factions are available.", noFactions.getBriefing().get(0));
		assertTrue(generatedParameters.isEmpty());
		assertTrue(noFactions.getPlugins().isEmpty());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void pvpAndPveRegisterSamePluginsAndApplyCommanderSkills(boolean rtt) throws Exception {

		quickBattles("alpha,beta,2,1.5\n");
		fleetSkills("alpha", List.of("combat", "combat", "fleet"), List.of("full_assault"));
		fleetSkills("beta", List.of("gunnery", "gunnery", "industry"), List.of("default_ai"));
		BattleNext_Settings.save(false, true, false, rtt, 123f, 1f, 3f, 50);
		var tournaments = new BattleNext_TournamentStore();
		tournaments.reload();
		var tournament = new BattleNext_TournamentModel();
		tournament.setId("active");
		tournament.setName("Active");
		tournament.setFleetIds(List.of("alpha", "beta"));
		tournament.addMatch(new BattleNext_Match("match", "alpha", "beta", 2, 1.5f));
		tournament.setStarted(true);
		tournaments.save(tournament);
		tournaments.setActiveId("active");
		var pvp = new MissionState();
		MissionDefinitionPlugin pvpDefinition = (MissionDefinitionPlugin) Class.forName("data.missions.BattleNext_pvp.MissionDefinition")
				.getDeclaredConstructor().newInstance();
		pvpDefinition.defineMission(pvp.getApi());
		var pve = new MissionState();
		new MissionDefinition().defineMission(pve.getApi());
		assertEquals(pvp.getPlugins().stream().map(Object::getClass).toList(), pve.getPlugins().stream().map(Object::getClass).toList());
		assertEquals(rtt, pve.getPlugins().stream().anyMatch(plugin -> plugin instanceof BattleNext_RttPlugin));
		for (MissionState mission : List.of(pvp, pve)) {
			assertEquals(2f, mission.getCommanders().get(FleetSide.PLAYER).getStats().getSkillLevel("combat"));
			assertEquals(1f, mission.getCommanders().get(FleetSide.PLAYER).getStats().getSkillLevel("fleet"));
			assertFalse(mission.getDefaultAi().get(FleetSide.PLAYER));
			assertTrue(mission.getDefaultAi().get(FleetSide.ENEMY));
			assertEquals(123f, getField(mission.getPlugins().get(1), "timeLimit"));
			assertEquals(123f, getField(mission.getPlugins().get(3), "timeLimit"));
			for (FleetSide side : FleetSide.values()) {
				assertTrue(mission.getEvents().indexOf("init:" + side) < mission.getEvents().indexOf("member:" + side));
				assertTrue(mission.getEvents().lastIndexOf("member:" + side) < mission.getEvents().indexOf("commander:" + side));
			}
		}
		assertEquals(2f, pvp.getCommanders().get(FleetSide.ENEMY).getStats().getSkillLevel("gunnery"));
		assertEquals(1f, pvp.getCommanders().get(FleetSide.ENEMY).getStats().getSkillLevel("industry"));
		assertEquals(2f, pve.getCommanders().get(FleetSide.ENEMY).getStats().getSkillLevel("combat"));
		assertEquals("mod_faction patrol", getField(pve.getPlugins().get(pve.getPlugins().size() - 1), "enemyName"));
		assertSame(generatedFleets.get(0).getMembers().get(0), pve.getMembers().get(FleetSide.ENEMY).get(0));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void pveReportUsesNativeMembersMarkerAndRetentionWithoutAdvancingTournament(int keepCount) throws Exception {

		quickBattles("alpha,beta,0,1\n");
		write("tournament/_tournaments/meta.json", "{\"formatVersion\":1,\"activeTournamentId\":\"missing\"}");
		var meta = directory.resolve("tournament/_tournaments/meta.json");
		byte[] original = Files.readAllBytes(meta);
		BattleNext_Settings.save(false, true, false, false, 500f, 1f, 3f, keepCount);
		var mission = new MissionState();
		new MissionDefinition().defineMission(mission.getApi());
		var combat = new CombatState(mission);
		BattleNext_ScorekeeperPlugin scorekeeper = mission.getPlugins().stream()
				.filter(plugin -> plugin instanceof BattleNext_ScorekeeperPlugin).map(plugin -> (BattleNext_ScorekeeperPlugin) plugin).findFirst().orElseThrow();
		BattleNext_BattleResultRecorder recorder = mission.getPlugins().stream()
				.filter(plugin -> plugin instanceof BattleNext_BattleResultRecorder).map(plugin -> (BattleNext_BattleResultRecorder) plugin).findFirst().orElseThrow();
		scorekeeper.init(combat.getApi());
		scorekeeper.advance(0.1f, List.of());
		assertTrue(scorekeeper.isFrozen());
		recorder.init(combat.getApi());
		recorder.advance(0.1f, List.of());
		recorder.advance(0.1f, List.of());
		var reports = new BattleNext_BattleReportStore();
		reports.reload();
		assertEquals(keepCount, reports.getAll().size());
		var report = scorekeeper.getFrozenReport();
		assertTrue(report.getName().contains("PvE row 1 (PLAYER), faction mod_faction, budget 100 FP, actual 45 FP"));
		assertTrue(report.getName().startsWith("Standard vs mod_faction patrol - "));
		assertEquals("player", report.getWinner());
		assertEquals(24000L, report.getDurationMillis());
		assertEquals(generatedFleets.get(0).getMembers().stream().map(member -> member.getShipName()).toList(),
				report.getEnemyMembers().stream().map(member -> member.getName()).toList());
		assertTrue(report.getEnemyMembers().stream().allMatch(member -> member.getStatus().equals("destroyed")));
		assertEquals(1, combat.getNotices().size());
		assertArrayEquals(original, Files.readAllBytes(meta));
		assertTrue(BattleNext_BattleSignals.consumePendingResult());
	}
}
