package data.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_CsvLine;
import data.test.fixture.BattleNext_TestCampaignFleetFixture;
import org.json.JSONObject;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class BattleNext_ImportFleetTest extends BattleNext_TestCampaignFleetFixture {

	private final List<FleetMemberAPI> importedMembers = new ArrayList<>();
	private final List<PersonAPI> importedOfficers = new ArrayList<>();
	private int syncCount;
	private StringBuilder consoleOutput;
	private int previousConsoleLength;
	private Method overlayOutputSetter;
	private String previousOverlayOutput;

	@BeforeEach
	void preparePlayerFleetImportTarget() throws Exception {

		registeredVariants.put("parent_variant", selectedVariant.getApi());
		FleetDataAPI data = proxy(FleetDataAPI.class, (method, args) -> switch (method) {
			case "addFleetMember" -> { importedMembers.add((FleetMemberAPI) args[0]); yield null; }
			case "addOfficer" -> { importedOfficers.add((PersonAPI) args[0]); yield null; }
			case "getMembersListCopy" -> List.copyOf(importedMembers);
			default -> null;
		});
		CampaignFleetAPI playerFleet = proxy(CampaignFleetAPI.class, (method, args) -> switch (method) {
			case "getFleetData" -> data;
			case "forceSync" -> { syncCount++; yield null; }
			default -> null;
		});
		Global.setSector(proxy(SectorAPI.class, (method, args) -> switch (method) {
			case "getPlayerFleet" -> playerFleet;
			case "getPlayerPerson" -> player;
			default -> null;
		}));

		Field consoleOutputField = Console.class.getDeclaredField("output");
		consoleOutputField.setAccessible(true);
		consoleOutput = (StringBuilder) consoleOutputField.get(null);
		previousConsoleLength = consoleOutput.length();
		Class<?> overlay = Class.forName("org.lazywizard.console.overlay.v2.panels.ConsoleOverlayPanel");
		Method overlayOutputGetter = overlay.getDeclaredMethod("getOutput");
		overlayOutputSetter = overlay.getDeclaredMethod("setOutput", String.class);
		overlayOutputGetter.setAccessible(true);
		overlayOutputSetter.setAccessible(true);
		previousOverlayOutput = (String) overlayOutputGetter.invoke(null);
	}

	@AfterEach
	void restoreConsoleOutput() throws Exception {
		consoleOutput.setLength(previousConsoleLength);
		overlayOutputSetter.invoke(null, previousOverlayOutput);
	}

	@Test
	void registeredCommandClassLoadsAsBaseCommand() throws Exception {
		List<String> lines = Files.readAllLines(Path.of("data/console/commands.csv"), StandardCharsets.UTF_8);
		List<String> columns = BattleNext_CsvLine.parse(lines.get(0));
		int commandIndex = columns.indexOf("command");
		int classIndex = columns.indexOf("class");
		List<String> row = lines.stream().skip(1).map(BattleNext_CsvLine::parse)
				.filter(fields -> fields.get(commandIndex).equals("BattleNext_ImportFleet")).findFirst().orElseThrow();
		String className = row.get(classIndex);
		assertEquals(BattleNext_ImportFleet.class.getName(), className);
		assertTrue(BaseCommand.class.isAssignableFrom(Class.forName(className)));
	}

	@Test
	void commandRequiresCampaignMapAndFleetId() {
		BattleNext_ImportFleet command = new BattleNext_ImportFleet();
		assertEquals(BaseCommand.CommandResult.WRONG_CONTEXT,
				command.runCommand("fleet", BaseCommand.CommandContext.CAMPAIGN_MARKET));
		assertEquals(BaseCommand.CommandResult.BAD_SYNTAX,
				command.runCommand(" \u3000\t", BaseCommand.CommandContext.CAMPAIGN_MAP));
		assertTrue(importedMembers.isEmpty());
		assertTrue(dataReads.isEmpty());
	}

	@Test
	void importsStandardAndLegacyFleetsWithMemberDataAndRepeatsAsNewMembers() throws Exception {

		BattleNext_MemberModel configured = new BattleNext_MemberModel("local_variant", "Reinforcement", "reckless",
				List.of("gunnery", "gunnery"), "officer.png", "", List.of());
		BattleNext_MemberModel plain = new BattleNext_MemberModel("parent_variant", "Escort", "", List.of(), "", "", List.of());
		writeStandard("standard", new BattleNext_FleetModel("reinforcements", "Reinforcements",
				List.of("fleet"), List.of(), List.of(configured, plain)));
		JSONObject localVariant = BattleNext_VariantBuilder.fromVariant(selectedVariant.getApi()).toJson()
				.put("variantId", "local_variant").put("fluxVents", 37);
		write("tournament/standard/local_variant.variant", localVariant.toString());

		BattleNext_ImportFleet command = new BattleNext_ImportFleet();
		assertEquals(BaseCommand.CommandResult.SUCCESS,
				command.runCommand("  reinforcements  ", BaseCommand.CommandContext.CAMPAIGN_MAP));
		assertEquals(List.of("Reinforcement", "Escort"), importedMembers.stream().map(FleetMemberAPI::getShipName).toList());
		assertEquals(37, importedMembers.get(0).getVariant().getNumFluxVents());
		PersonAPI importedOfficer = importedMembers.get(0).getCaptain();
		assertEquals("reckless", importedOfficer.getPersonalityAPI().getId());
		assertEquals("officer.png", importedOfficer.getPortraitSprite());
		assertEquals(2f, importedOfficer.getStats().getSkillLevel("gunnery"));
		assertEquals(1, importedOfficer.getStats().getLevel());
		assertEquals("Imported Officer", importedOfficer.getNameString());
		assertEquals(List.of(importedOfficer), importedOfficers);
		assertTrue(consoleOutput.toString().contains("2 ships, 1 officers"));
		assertEquals(1, syncCount);
		assertEquals(1f, player.getStats().getSkillLevel("fleet"));

		assertEquals(BaseCommand.CommandResult.SUCCESS,
				command.runCommand("reinforcements", BaseCommand.CommandContext.CAMPAIGN_MAP));
		assertEquals(4, importedMembers.size());
		assertEquals(2, importedOfficers.size());
		assertNotSame(importedMembers.get(0), importedMembers.get(2));
		assertNotSame(importedMembers.get(0).getCaptain(), importedMembers.get(2).getCaptain());
		assertEquals(2, syncCount);

		writeLegacy("01", "Legacy", "parent_variant,Legacy ship,1,steady,helmsmanship,legacy.png,,\n");
		assertEquals(BaseCommand.CommandResult.SUCCESS,
				command.runCommand("01", BaseCommand.CommandContext.CAMPAIGN_MAP));
		assertEquals("Legacy ship", importedMembers.get(4).getShipName());
		assertEquals("steady", importedMembers.get(4).getCaptain().getPersonalityAPI().getId());
		assertEquals("legacy.png", importedMembers.get(4).getCaptain().getPortraitSprite());
		assertEquals(1f, importedMembers.get(4).getCaptain().getStats().getSkillLevel("helmsmanship"));
		assertEquals(3, importedOfficers.size());
	}

	@Test
	void missingIdAndIncompleteBuildLeaveFleetAndOfficerCollectionsUnchanged() throws Exception {

		BattleNext_ImportFleet command = new BattleNext_ImportFleet();
		writeStandard("broken", new BattleNext_FleetModel("broken", "Broken fleet", List.of(), List.of(), List.of(
				new BattleNext_MemberModel("parent_variant", "Valid ship", "aggressive", List.of("gunnery"), "valid.png", "", List.of()),
				new BattleNext_MemberModel("missing_variant", "Missing ship", "reckless", List.of(), "missing.png", "", List.of()))));
		assertEquals(BaseCommand.CommandResult.ERROR,
				command.runCommand("absent", BaseCommand.CommandContext.CAMPAIGN_MAP));
		assertTrue(consoleOutput.toString().contains("Fleet ID not found: absent"));
		assertEquals(BaseCommand.CommandResult.ERROR,
				command.runCommand("BROKEN", BaseCommand.CommandContext.CAMPAIGN_MAP));
		assertTrue(consoleOutput.toString().contains("Fleet ID not found: BROKEN"));
		assertEquals(BaseCommand.CommandResult.ERROR,
				command.runCommand("broken", BaseCommand.CommandContext.CAMPAIGN_MAP));
		assertTrue(importedMembers.isEmpty());
		assertTrue(importedOfficers.isEmpty());
		assertEquals(0, syncCount);
		assertTrue(consoleOutput.toString().contains("Unable to build fleet broken: member[1]: Unknown variant: missing_variant"));
	}

	@Test
	void preservesOfficerIdentityDataWhenRegisteringACaptainCreatedAsADefaultPerson() throws Exception {

		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (method.getName().equals("createPerson")) {
				return person("Named Officer", "", "steady", true, false, false, Map.of());
			}
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		writeStandard("named", new BattleNext_FleetModel("named", "Named fleet", List.of(), List.of(), List.of(
				new BattleNext_MemberModel("parent_variant", "Named ship", "aggressive", List.of("gunnery"),
						"custom_officer.png", "", List.of()))));
		assertEquals(BaseCommand.CommandResult.SUCCESS,
				new BattleNext_ImportFleet().runCommand("named", BaseCommand.CommandContext.CAMPAIGN_MAP));
		assertEquals("Named Officer", importedOfficers.get(0).getNameString());
		assertEquals("aggressive", importedOfficers.get(0).getPersonalityAPI().getId());
		assertEquals("custom_officer.png", importedOfficers.get(0).getPortraitSprite());
		assertEquals(1f, importedOfficers.get(0).getStats().getSkillLevel("gunnery"));
		assertEquals(1, importedOfficers.get(0).getStats().getLevel());
		assertSame(importedOfficers.get(0), importedMembers.get(0).getCaptain());
	}

	private void writeStandard(String folder, BattleNext_FleetModel fleet) throws Exception {
		write("tournament/" + folder + "/fleet_data.json", fleet.toJson().toString());
	}

	private void writeLegacy(String id, String name, String members) throws Exception {
		write("tournament/player" + id + "_data.csv", "name,author,skills,tags\n" + name + ",Author,,\n");
		write("tournament/player" + id + "_fleet.csv",
				"variant,name,rowNumber,personality,skills,portrait,orders,tags\n" + members);
	}

	private void write(String path, String text) throws Exception {
		Path target = directory.resolve(path);
		Files.createDirectories(target.getParent());
		Files.writeString(target, text, StandardCharsets.UTF_8);
	}

}
