package data.scripts.fleet;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.tools.BattleNext_Logger;
import data.test.fixture.BattleNext_TestMissionFixture;
import com.fs.starfarer.api.Global;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_PveFleetGeneratorTest extends BattleNext_TestMissionFixture {

	@Test
	void discoversVisibleFactionsInCurrentRegistrationOrder() throws Exception {
		var generator = new BattleNext_PveFleetGenerator();
		var expected = factionSpecs.stream().filter(faction -> faction.isShowInIntelTab()).toList();
		assertEquals(expected, generator.loadFactions());
		assertTrue(dataReads.contains("getMergedSpreadsheetData:id:" + BattleNext_PveFleetGenerator.FACTIONS_PATH));
		factionSpecs.add(1, faction("additional_mod", true));
		assertEquals(factionSpecs.stream().filter(faction -> faction.isShowInIntelTab()).toList(), generator.loadFactions());
	}

	@Test
	void factionCsvDeclaresTheSingleIdColumn() throws Exception {
		String text = Files.readString(Path.of(BattleNext_PveFleetGenerator.FACTIONS_PATH), StandardCharsets.UTF_8);
		assertFalse(text.startsWith("\uFEFF"));
		assertEquals("id", text.split("\\R", -1)[0]);
	}

	@Test
	void mergedOverridesKeepRegistrationOrderAndIgnoreUnknownIds() throws Exception {

		pveFactionRows.put(new JSONObject().put("id", " mod_faction "));
		pveFactionRows.put(new JSONObject().put("id", " hidden "));
		pveFactionRows.put(new JSONObject().put("id", "hidden"));
		pveFactionRows.put(new JSONObject().put("id", "missing"));
		pveFactionRows.put(new JSONObject().put("id", "   "));
		pveFactionRows.put(new JSONObject().put("id", "HIDDEN"));
		List<LoggingEvent> events = new ArrayList<>();
		AppenderSkeleton appender = new AppenderSkeleton() {
			@Override
			protected void append(LoggingEvent event) {
				events.add(event);
			}
			@Override
			public void close() {

			}
			@Override
			public boolean requiresLayout() {
				return false;
			}
		};
		var logger = Global.getLogger(BattleNext_Logger.class);
		logger.addAppender(appender);
		try {
			assertEquals(factionSpecs, new BattleNext_PveFleetGenerator().loadFactions());
			List<String> rejected = new ArrayList<>();
			for (LoggingEvent event : events) {
				JSONObject message = new JSONObject(event.getRenderedMessage());
				if (!message.getString("event").equals("pve.faction.override.rejected")) continue;
				assertEquals(Level.WARN, event.getLevel());
				assertEquals(BattleNext_PveFleetGenerator.FACTIONS_PATH, message.getJSONObject("fields").getString("path"));
				rejected.add(message.getJSONObject("fields").getString("faction_id"));
			}
			assertEquals(List.of("missing", "", "HIDDEN"), rejected);
		} finally {
			logger.removeAppender(appender);
		}
	}

	@Test
	void reloadUsesCurrentMergedOverridesAndCurrentFactionCatalog() throws Exception {

		var generator = new BattleNext_PveFleetGenerator();
		pveFactionRows.put(new JSONObject().put("id", "hidden"));
		assertEquals(factionSpecs, generator.loadFactions());
		pveFactionRows.put(0, new JSONObject().put("id", "hegemony"));
		assertEquals(factionSpecs.stream().filter(faction -> faction.isShowInIntelTab()).toList(), generator.loadFactions());
		pveFactionRows.put(0, new JSONObject().put("id", "late_loaded"));
		factionSpecs.add(1, faction("late_loaded", false));
		assertEquals(factionSpecs.stream().filter(faction -> faction.isShowInIntelTab()
				|| faction.getId().equals("late_loaded")).toList(), generator.loadFactions());
	}

	@Test
	void callsNativeFactoryWithPatrolDefaultsAndInflatesBeforeReadingMembers() {

		var result = new BattleNext_PveFleetGenerator().generate("mod_faction", 170);
		var params = generatedParameters.get(0);
		assertEquals("mod_faction", params.factionId);
		assertEquals(FleetTypes.PATROL_LARGE, params.fleetType);
		assertNull(params.source);
		assertEquals(0f, params.locInHyper.x);
		assertEquals(0f, params.locInHyper.y);
		assertEquals(170f, params.combatPts);
		assertEquals(170f, params.getTotalPts());
		assertNull(params.qualityOverride);
		assertEquals(0f, params.qualityMod);
		assertTrue(params.withOfficers);
		assertNull(params.ignoreMarketFleetSizeMult);
		assertNull(params.modeOverride);
		assertNull(params.averageSMods);
		assertNull(params.maxNumShips);
		assertNull(params.noCommanderSkills);
		assertEquals(1f, params.officerNumberMult);
		assertTrue(generatedFleets.get(0).isInflated());
		assertTrue(result.isComplete());
		assertEquals(List.of("default_ai"), result.getDefinition().getTags());
	}

	@Test
	void preservesNativeMembersCaptainsVariantsModulesAndSkillLevels() throws Exception {

		var result = new BattleNext_PveFleetGenerator().generate("mod_faction", 100);
		var nativeMembers = generatedFleets.get(0).getMembers();
		assertEquals(nativeMembers, result.getMembers());
		assertSame(player, result.getCommander());
		assertEquals(BattleNext_PersonSkills.capture(player), result.getDefinition().getSkills());
		for (int index = 0; index < nativeMembers.size(); index++) {
			FleetMemberAPI member = nativeMembers.get(index);
			assertSame(member, result.getMembers().get(index));
			assertEquals(member.getShipName(), result.getDefinition().getMembers().get(index).getName());
			assertEquals(member.getVariant().getHullVariantId(), result.getDefinition().getMembers().get(index).getVariantId());
			assertEquals(member.getRepairTracker().getMaxCR(), member.getRepairTracker().getCR());
		}
		assertSame(activeVariant.getApi(), result.getMembers().get(0).getVariant());
		assertSame(officer, result.getMembers().get(0).getCaptain());
		assertEquals(7, result.getMembers().get(0).getVariant().getModuleVariant("module").toJSONObject().getInt("fluxVents"));
		var model = result.getDefinition().getMembers().get(0);
		assertEquals(BattleNext_PersonSkills.capture(officer), model.getSkills());
		assertEquals("officer.png", model.getPortrait());
		assertEquals("reckless", model.getPersonality());
		var defaultCaptain = result.getDefinition().getMembers().get(1);
		assertTrue(defaultCaptain.getSkills().isEmpty());
		assertEquals("", defaultCaptain.getPortrait());
		assertEquals("", defaultCaptain.getPersonality());
	}

	@Test
	void repeatedRequestsUseFreshNativeFactoryResults() {
		var generator = new BattleNext_PveFleetGenerator();
		var first = generator.generate("hegemony", 100);
		var second = generator.generate("hegemony", 100);
		assertEquals(2, generatedParameters.size());
		assertNotEquals(first.getDefinition().getId(), second.getDefinition().getId());
		assertNotSame(first.getMembers().get(0), second.getMembers().get(0));
	}

	@Test
	void generationFailureAndEmptyFleetsRemainExplicitFailures() {
		var generator = new BattleNext_PveFleetGenerator();
		this.generator = params -> null;
		assertThrows(IllegalStateException.class, () -> generator.generate("hegemony", 100));
		this.generator = params -> new GeneratedFleet("empty", params.factionId, List.of(), player).getApi();
		assertThrows(IllegalStateException.class, () -> generator.generate("hegemony", 100));
		this.generator = params -> { throw new IllegalArgumentException("Unavailable hull"); };
		assertEquals("Unavailable hull", assertThrows(IllegalArgumentException.class,
				() -> generator.generate("hegemony", 100)).getMessage());
	}
}
