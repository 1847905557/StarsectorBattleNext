package data.scripts.fleet;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionScript;
import data.restriction.BattleNext_RestrictionSpec;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.tools.BattleNext_CsvLine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class BattleNext_RulesCheckTest {

	private final BattleNext_RulesCheck check = new BattleNext_RulesCheck();
	private Field registryEntries;
	private Field registryFactory;
	private Field registryErrors;
	private Object previousEntries;
	private Object previousFactory;
	private Object previousErrors;

	@BeforeEach
	void loadRestrictions() throws Exception {
		var registry = BattleNext_RestrictionRegistry.getInstance();
		registryEntries = BattleNext_RestrictionRegistry.class.getDeclaredField("entries");
		registryFactory = BattleNext_RestrictionRegistry.class.getDeclaredField("scriptFactory");
		registryEntries.setAccessible(true);
		registryFactory.setAccessible(true);
		registryErrors = BattleNext_RestrictionRegistry.class.getDeclaredField("loadErrors");
		registryErrors.setAccessible(true);
		previousEntries = registryEntries.get(registry);
		previousFactory = registryFactory.get(registry);
		previousErrors = registryErrors.get(registry);
		BattleNext_RestrictionRegistry.getInstance().load(restrictionRows(), this::createRestriction);
	}

	@AfterEach
	void restoreRestrictions() throws IllegalAccessException {
		var registry = BattleNext_RestrictionRegistry.getInstance();
		registryEntries.set(registry, previousEntries);
		registryFactory.set(registry, previousFactory);
		registryErrors.set(registry, previousErrors);
	}

	private JSONArray restrictionRows() throws Exception {
		JSONArray rows = new JSONArray();
		for (String line : Files.readAllLines(Path.of(BattleNext_RestrictionRegistry.PATH), StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.stripLeading().startsWith("#") || line.startsWith("id,")) continue;
			List<String> fields = BattleNext_CsvLine.parse(line);
			rows.put(new JSONObject().put("id", fields.get(0)).put("name", fields.get(1)).put("type", fields.get(2))
					.put("script", fields.get(3)).put("desc", fields.get(4)));
		}
		return rows;
	}

	@Test
	void registeredRestrictionsFollowInputRowsAcrossCatalogChanges() throws Exception {

		JSONArray current = restrictionRows();
		for (String change : List.of("current", "added", "removed", "reversed", "empty")) {
			List<JSONObject> inputs = new ArrayList<>();
			for (int index = 0; index < current.length(); index++) {
				inputs.add(current.getJSONObject(index));
			}
			if (change.equals("added")) {
				String id = "fixtureExtension";
				Set<String> names = new HashSet<>(inputs.stream().map(input -> input.optString("id")).toList());
				while (names.contains(id)) {
					id += "Extra";
				}
				inputs.add(new JSONObject().put("id", id).put("name", id).put("type", "number")
						.put("script", ExampleRestriction.class.getName()).put("desc", "Fixture extension"));
			} else if (change.equals("removed")) {
				inputs = new ArrayList<>(inputs.stream().skip(1).toList());
			} else if (change.equals("reversed")) {
				Collections.reverse(inputs);
			} else if (change.equals("empty")) {
				inputs.clear();
			}
			BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
			registry.load(new JSONArray(inputs), this::createRestriction);
			assertEquals(inputs.stream().map(input -> input.optString("id").trim()).toList(),
					registry.getAll().stream().map(BattleNext_RestrictionSpec::getId).toList());
			Set<String> ids = new HashSet<>();
			for (JSONObject input : inputs) {
				BattleNext_RestrictionSpec entry = registry.get(input.getString("id").trim());
				assertTrue(ids.add(entry.getId()), entry.getId());
				assertEquals(input.getString("name").trim(), entry.getName());
				assertEquals(input.getString("type").trim(), entry.getType());
				assertEquals(input.getString("script").trim(), entry.getScript());
				assertEquals(input.getString("desc").trim(), entry.getDescription());
				assertEquals(inputs.stream().filter(candidate -> candidate.optString("type").trim().equals(entry.getType()))
						.map(candidate -> candidate.optString("id").trim()).toList(),
						registry.getByType(entry.getType()).stream().map(BattleNext_RestrictionSpec::getId).toList());
				String simpleName = entry.getScript().substring(entry.getScript().lastIndexOf('.') + 1);
				if (entry.getScript().startsWith("data.restriction.")) {
					assertEquals(Character.toUpperCase(entry.getId().charAt(0)) + entry.getId().substring(1), simpleName);
				}
				Object instance = registry.create(entry.getId());
				assertInstanceOf(BattleNext_RestrictionScript.class, instance);
				assertNotSame(instance, registry.create(entry.getId()));
			}
		}
	}

	@Test
	void invalidRestrictionsPublishDiagnosticsAndRejectAllConflictingRows() throws Exception {
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		JSONArray valid = new JSONArray().put(new JSONObject().put("id", "extensionA").put("name", "extensionA")
				.put("type", "number").put("script", ExampleRestriction.class.getName()).put("desc", "A"));
		registry.load(valid, name -> new ExampleRestriction());
		JSONArray wrongInterface = new JSONArray().put(new JSONObject().put("id", "extensionA").put("name", "extensionA")
				.put("type", "list").put("script", String.class.getName()).put("desc", "Wrong"));
		registry.load(wrongInterface, name -> "wrong");
		assertTrue(registry.getAll().isEmpty());
		assertEquals("extensionA", registry.getLoadErrors().get(0).id());
		assertTrue(registry.getLoadErrors().get(0).diagnostic().contains("BattleNext_RestrictionScript"));
		JSONArray duplicate = new JSONArray().put(valid.getJSONObject(0)).put(valid.getJSONObject(0));
		registry.load(duplicate, name -> new ExampleRestriction());
		assertTrue(registry.getAll().isEmpty());
		assertEquals(List.of(0, 1), registry.getLoadErrors().stream().map(error -> error.position()).toList());
		assertTrue(registry.getLoadErrors().stream().allMatch(error -> error.diagnostic().contains("Duplicate restriction ID: extensionA")));
	}

	@Test
	void externalListRestrictionUsesItsOwnScript() throws Exception {
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		JSONArray rows = new JSONArray().put(new JSONObject().put("id", "extensionList").put("name", "extensionList")
				.put("type", "list").put("script", ExampleListRestriction.class.getName()).put("desc", "List"));
		registry.load(rows, name -> new ExampleListRestriction());
		BattleNext_RuleModel rule = new BattleNext_RuleModel("rule", "Rule");
		rule.setRestrictions(List.of(list("extensionList", "allowed")));
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), List.of());
		BattleNext_AssembledFleet assembledFleet = new BattleNext_AssembledFleet(fleet, null, List.of(), List.of());
		assertEquals(List.of("fleet allowed"), check.validateFleet(assembledFleet, rule));
	}

	@Test
	void missingEnabledRestrictionIsPreservedAndBlocksStart() throws Exception {
		BattleNext_RuleModel rule = new BattleNext_RuleModel("missing", "Missing");
		BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction("extensionLimit", "number");
		restriction.setEnabled(true);
		restriction.setValue(3);
		rule.setRestrictions(List.of(restriction));
		BattleNext_RuleModel restored = new BattleNext_RuleModel();
		restored.fromJson(rule.toJson());
		assertEquals(rule.toJson().toString(), restored.toJson().toString());
		assertEquals(List.of("Missing restriction script: extensionLimit"), check.missingRestrictions(restored));
	}

	@Test
	void mergedRegistrationValidatesScriptsAndDispatchesInRuleOrder() throws Exception {
		JSONArray rows = new JSONArray();
		rows.put(new JSONObject().put("id", "extensionA").put("name", "extensionA").put("type", "number")
				.put("script", ExampleRestriction.class.getName()).put("desc", "Extension A"));
		rows.put(new JSONObject().put("id", "extensionB").put("name", "extensionB").put("type", "number")
				.put("script", ExampleRestriction.class.getName()).put("desc", "Extension B"));
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		registry.load(rows, name -> new ExampleRestriction());
		BattleNext_RuleModel rule = new BattleNext_RuleModel("test", "Test");
		BattleNext_RuleRestriction second = number("extensionB", 2);
		BattleNext_RuleRestriction first = number("extensionA", 1);
		rule.setRestrictions(List.of(second, first));
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(), List.of());
		BattleNext_AssembledFleet assembledFleet = new BattleNext_AssembledFleet(fleet, null, List.of(), List.of());
		assertEquals(List.of("fleet extensionB 2.0", "fleet extensionA 1.0"), check.validateFleet(assembledFleet, rule));
		rows.put(new JSONObject().put("id", "bad").put("name", "bad").put("type", "number")
				.put("script", String.class.getName()).put("desc", "Bad"));
		registry.load(rows, name -> name.equals(String.class.getName()) ? "wrong" : new ExampleRestriction());
		assertEquals("bad", registry.getLoadErrors().get(0).id());
		assertEquals(2, registry.getAll().size());
		assertNotNull(registry.get("extensionA"));
		assertTrue(registry.create("extensionA") instanceof ExampleRestriction);
	}

	@Test
	void numericLimitsUseFleetAndOfficerCounts() {
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet_a", "Fleet A", List.of("skill_a"), List.of(),
				List.of(new BattleNext_MemberModel("variant", "", "", List.of("skill_a", "skill_a"), "", "", List.of()),
						new BattleNext_MemberModel("variant", "", "", List.of(), "", "", List.of())));
		BattleNext_AssembledFleet assembledFleet = new BattleNext_AssembledFleet(fleet, null,
				List.of(member("wolf", 12f), member("wolf", 12f)), List.of());
		List<BattleNext_RuleRestriction> restrictions = new ArrayList<>();
		restrictions.add(number("fleetMaxShips", 1));
		restrictions.add(number("fleetMaxDp", 20));
		restrictions.add(number("fleetMaxSameHull", 1));
		restrictions.add(number("fleetHullSizeLimitFrigate", 1));
		restrictions.add(number("officerMaxCount", 0));
		restrictions.add(number("officerMaxLevel", 0));
		restrictions.add(number("officerMaxElite", 0));
		restrictions.add(number("fleetSkillMaxCount", 0));
		BattleNext_RuleModel rule = new BattleNext_RuleModel("rules", "Rules");
		rule.setRestrictions(restrictions);
		List<String> reasons = check.validateFleet(assembledFleet, rule);
		assertEquals(List.of("fleet_a fleetMaxShips 2.0/1.0", "fleet_a fleetMaxDp 24.0/20.0",
				"fleet_a hull wolf fleetMaxSameHull 2.0/1.0", "fleet_a fleetHullSizeLimitFrigate 2.0/1.0",
				"fleet_a officerMaxCount 1.0/0.0", "fleet_a member[0] officerMaxLevel 1.0/0.0",
				"fleet_a member[0] officerMaxElite 1.0/0.0", "fleet_a fleetSkillMaxCount 1.0/0.0"), reasons);
	}

	@Test
	void filtersApplyAcrossAllFourCategoriesAndSources() {

		for (String category : List.of("weapon", "wing", "hullmod", "hull")) {
			BattleNext_RuleModel rule = new BattleNext_RuleModel("rules", "Rules");
			String suffix = Character.toUpperCase(category.charAt(0)) + category.substring(1);
			rule.setRestrictions(List.of(list("filterAllowMods", "vanilla"),
					list("filterBlack" + suffix, "item"), list("filterWhite" + suffix, "other")));
			String context = "fleet member[0] " + category + " ";
			assertEquals(List.of(context + "item in filterBlack" + suffix, context + "item outside filterWhite" + suffix),
					check.validateEquipment(rule, "fleet member[0]", category, "item", "vanilla"));
			assertEquals(List.of(context + "item mod someMod outside filterAllowMods",
					context + "item in filterBlack" + suffix, context + "item outside filterWhite" + suffix),
					check.validateEquipment(rule, "fleet member[0]", category, "item", "someMod"));
			assertTrue(check.validateEquipment(rule, "fleet member[0]", category, "other", "vanilla").isEmpty());
			rule.getRestriction("filterAllowMods").setItems(List.of());
			assertEquals(List.of(context + "other mod vanilla outside filterAllowMods"),
					check.validateEquipment(rule, "fleet member[0]", category, "other", "vanilla"));
			assertEquals(List.of(context + "other source mod unknown"),
					check.validateEquipment(rule, "fleet member[0]", category, "other", ""));
		}
	}

	@Test
	void remainingNumericLimitsUseAssembledVariantData() {
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(),
				List.of(new BattleNext_MemberModel("variant", "", "", List.of(), "", "", List.of())));
		BattleNext_AssembledFleet assembledFleet = new BattleNext_AssembledFleet(fleet, null,
				List.of(member("capital", 40f, ShipAPI.HullSize.CAPITAL_SHIP,
						List.of("wing"), Set.of("smod"))), List.of());
		BattleNext_RuleModel rule = new BattleNext_RuleModel("rule", "Rule");
		rule.setRestrictions(List.of(number("fleetHullSizeLimitCapitalShip", 0),
				number("refitMaxDecks", 0), number("refitSmods", 0)));
		List<String> reasons = check.validateFleet(assembledFleet, rule);
		assertEquals(List.of("fleet fleetHullSizeLimitCapitalShip 1.0/0.0",
				"fleet refitMaxDecks 1.0/0.0", "fleet refitSmods 1.0/0.0"), reasons);
	}

	@Test
	void eachHullSizeRestrictionChecksItsOwnSize() {
		List<ShipAPI.HullSize> sizes = List.of(ShipAPI.HullSize.FRIGATE, ShipAPI.HullSize.DESTROYER,
				ShipAPI.HullSize.CRUISER, ShipAPI.HullSize.CAPITAL_SHIP);
		List<String> ids = List.of("fleetHullSizeLimitFrigate", "fleetHullSizeLimitDestroyer",
				"fleetHullSizeLimitCruiser", "fleetHullSizeLimitCapitalShip");
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet", "Fleet", List.of(), List.of(),
				List.of(new BattleNext_MemberModel("variant", "", "", List.of(), "", "", List.of())));
		for (int index = 0; index < sizes.size(); index++) {
			BattleNext_AssembledFleet assembledFleet = new BattleNext_AssembledFleet(fleet, null,
					List.of(member("hull", 10f, sizes.get(index), List.of(), Set.of())), List.of());
			for (int restrictionIndex = 0; restrictionIndex < ids.size(); restrictionIndex++) {
				BattleNext_RuleModel rule = new BattleNext_RuleModel("rule", "Rule");
				rule.setRestrictions(List.of(number(ids.get(restrictionIndex), 0)));
				assertEquals(index == restrictionIndex ? 1 : 0, check.validateFleet(assembledFleet, rule).size(),
						ids.get(restrictionIndex) + " for " + sizes.get(index));
			}
		}
	}

	private BattleNext_RuleRestriction number(String id, double value) {
		BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(id, "number");
		restriction.setEnabled(true);
		restriction.setValue(value);
		return restriction;
	}

	private Object createRestriction(String name) {
		try {
			return Class.forName(name).getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalArgumentException(name, exception);
		}
	}

	private BattleNext_RuleRestriction list(String id, String item) {
		BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction(id, "list");
		restriction.setEnabled(true);
		restriction.setItems(List.of(item));
		return restriction;
	}

	private FleetMemberAPI member(String hullId, float dp) {
		return member(hullId, dp, ShipAPI.HullSize.FRIGATE, List.of(), Set.of());
	}

	private FleetMemberAPI member(String hullId, float dp, ShipAPI.HullSize hullSize,
			List<String> wings, Set<String> sMods) {
		ShipHullSpecAPI hull = (ShipHullSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipHullSpecAPI.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getHullId" -> hullId;
					case "getHullSize" -> hullSize;
					default -> empty(method.getReturnType());
				});
		ShipVariantAPI variant = (ShipVariantAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipVariantAPI.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getHullSpec" -> hull;
					case "getWings" -> wings;
					case "getSMods" -> new LinkedHashSet<>(sMods);
					default -> empty(method.getReturnType());
				});
		return (FleetMemberAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{FleetMemberAPI.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getVariant" -> variant;
					case "getDeploymentPointsCost" -> dp;
					default -> empty(method.getReturnType());
				});
	}

	private Object empty(Class<?> type) {
		if (type == LinkedHashSet.class) return new LinkedHashSet<>();
		if (type == ArrayList.class) return new ArrayList<>();
		if (Set.class.isAssignableFrom(type)) return Set.of();
		if (List.class.isAssignableFrom(type)) return List.of();
		if (Collection.class.isAssignableFrom(type)) return List.of();
		if (type == boolean.class) return false;
		if (type == int.class) return 0;
		if (type == float.class) return 0f;
		return null;
	}

	public static class ExampleRestriction implements BattleNext_RestrictionScript {

		@Override
		public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
			return List.of(assembledFleet.getDefinition().getId() + " " + restriction.getId() + " " + restriction.getValue());
		}
	}

	public static class ExampleListRestriction implements BattleNext_RestrictionScript {

		@Override
		public List<String> validate(BattleNext_AssembledFleet assembledFleet, BattleNext_RuleRestriction restriction) {
			return List.of(assembledFleet.getDefinition().getId() + " " + restriction.getItems().get(0));
		}
	}
}
