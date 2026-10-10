package data.test.fixture;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.PersonalityAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.RepairTrackerAPI;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_TestMemberFixture extends BattleNext_TestVariantFixture {

	protected Variant selectedVariant;
	protected final List<PersonAPI> createdPeople = new ArrayList<>();

	@BeforeEach
	protected void configureMemberServices() throws Exception {
		selectedVariant = source();
		Global.setSector(proxy(SectorAPI.class, (method, args) -> null));
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if ("loadText".equals(method.getName()) && ((String) args[0]).startsWith("tournament/")) {
				return Files.readString(directory.resolve((String) args[0]), StandardCharsets.UTF_8);
			}
			if ("getString".equals(method.getName())) {
				return BattleNext_TestTextCatalog.lookup(args[0], args[1]);
			}
			if ("createPerson".equals(method.getName())) {
				PersonAPI person = person("Generated", "", "steady", false, false, false, Map.of());
				createdPeople.add(person);
				return person;
			}
			if ("createFleetMember".equals(method.getName())) return member("", (ShipVariantAPI) args[1], null, false);
			if ("getSkillSpec".equals(method.getName())) {
				return proxy(SkillSpecAPI.class, (name, values) -> switch (name) {
					case "getId" -> args[0];
					case "getName" -> "Skill " + args[0];
					default -> null;
				});
			}
			try {
				return method.invoke(previous, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	protected FleetMemberAPI member(String name, ShipVariantAPI variant, PersonAPI officer, boolean mothballed) {
		String[] shipName = {name};
		PersonAPI[] person = {officer};
		RepairTrackerAPI repair = proxy(RepairTrackerAPI.class, (method, args) ->
				"getMaxCR".equals(method) ? 0.7f : null);
		return proxy(FleetMemberAPI.class, (method, args) -> switch (method) {
			case "getVariant" -> variant;
			case "getShipName" -> shipName[0];
			case "setShipName" -> { shipName[0] = (String) args[0]; yield null; }
			case "getCaptain" -> person[0];
			case "setCaptain" -> { person[0] = (PersonAPI) args[0]; yield null; }
			case "isMothballed", "isCivilian" -> mothballed;
			case "getRepairTracker" -> repair;
			case "getCrewComposition" -> proxy(CrewCompositionAPI.class, (key, values) -> null);
			default -> null;
		});
	}

	protected PersonAPI person(String name, String portrait, String personality, boolean defaultPerson,
			boolean playerPerson, boolean aiCore, Map<String, Float> initialSkills) {

		Map<String, Float> skills = new LinkedHashMap<>(initialSkills);
		String[] sprite = {portrait};
		String[] behavior = {personality};
		MutableCharacterStatsAPI stats = proxy(MutableCharacterStatsAPI.class, (method, args) -> switch (method) {
			case "getSkillsCopy" -> skills.entrySet().stream().map(entry ->
					proxy(MutableCharacterStatsAPI.SkillLevelAPI.class, (key, values) -> switch (key) {
						case "getLevel" -> entry.getValue();
						case "getSkill" -> proxy(SkillSpecAPI.class, (spec, params) ->
								"getId".equals(spec) ? entry.getKey() : null);
						default -> null;
					})).toList();
			case "getSkillLevel" -> skills.getOrDefault(args[0], 0f);
			case "setSkillLevel" -> skills.put((String) args[0], (Float) args[1]);
			default -> null;
		});
		return proxy(PersonAPI.class, (method, args) -> switch (method) {
			case "getNameString" -> name;
			case "getPortraitSprite" -> sprite[0];
			case "setPortraitSprite" -> { sprite[0] = (String) args[0]; yield null; }
			case "getStats" -> stats;
			case "isDefault" -> defaultPerson;
			case "isPlayer" -> playerPerson;
			case "isAICore" -> aiCore;
			case "getPersonalityAPI" -> proxy(PersonalityAPI.class, (key, values) -> switch (key) {
				case "getId", "getDisplayName" -> behavior[0];
				default -> null;
			});
			case "setPersonality" -> { behavior[0] = (String) args[0]; yield null; }
			default -> null;
		});
	}

}
