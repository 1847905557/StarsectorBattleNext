package data.model.rule;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionSpec;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.tools.BattleNext_CsvLine;
import data.scripts.fleet.BattleNext_AssembledFleet;
import data.scripts.member.BattleNext_MemberModel;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_RulesCheckTest {

	private final BattleNext_RulesCheck check = new BattleNext_RulesCheck();

	@BeforeEach
	void loadRestrictions() throws Exception {
		JSONArray rows = new JSONArray();
		for (String line : Files.readAllLines(Path.of(BattleNext_RestrictionRegistry.PATH), StandardCharsets.UTF_8)) {
			if (line.startsWith("id,")) continue;
			List<String> fields = BattleNext_CsvLine.parse(line);
			rows.put(new JSONObject().put("id", fields.get(0)).put("type", fields.get(1))
					.put("script", fields.get(2)).put("desc", fields.get(3)));
		}
		BattleNext_RestrictionRegistry.getInstance().load(rows, this::createRestriction);
	}

	@Test
	void flatJsonRoundTrip() throws Exception {
		BattleNext_RuleModel rule = new BattleNext_RuleModel("rules_a", "Rules A");
		rule.setRestrictions(List.of(number("fleetMaxShips", 2), list("filterWhiteWeapon", "lightmg")));
		JSONObject json = rule.toJson();
		assertEquals(1, json.getInt("formatVersion"));
		assertEquals(2, json.getJSONArray("restrictions").length());
		BattleNext_RuleModel restored = new BattleNext_RuleModel();
		restored.fromJson(json);
		assertEquals("rules_a", restored.getId());
		assertEquals(2, restored.getRestrictions().size());
		assertEquals("lightmg", restored.getRestriction("filterWhiteWeapon").getItems().get(0));
	}

	@Test
	void newRuleHasNoRestrictions() {
		BattleNext_RuleModel rule = new BattleNext_RuleModel();
		assertTrue(rule.getRestrictions().isEmpty());
	}

	@Test
	void registeredRestrictionsHaveExpectedTypes() {
		List<BattleNext_RestrictionSpec> entries = BattleNext_RestrictionRegistry.getInstance().getAll();
		assertEquals(22, entries.size());
		assertEquals(22, entries.stream().map(BattleNext_RestrictionSpec::getId).distinct().count());
		assertEquals(22, entries.stream().map(BattleNext_RestrictionSpec::getScript).distinct().count());
		for (BattleNext_RestrictionSpec entry : entries) {
			String simpleName = entry.getScript().substring(entry.getScript().lastIndexOf('.') + 1);
			assertEquals(Character.toUpperCase(entry.getId().charAt(0)) + entry.getId().substring(1), simpleName);
			assertNotSame(BattleNext_RestrictionRegistry.getInstance().create(entry.getId()),
					BattleNext_RestrictionRegistry.getInstance().create(entry.getId()));
		}
		assertEquals(13, entries.stream()
				.filter(entry -> "number".equals(entry.getType())).count());
		assertEquals(9, entries.stream()
				.filter(entry -> "list".equals(entry.getType())).count());
		assertEquals(13, BattleNext_RestrictionRegistry.getInstance().getByType("number").size());
		assertEquals(9, BattleNext_RestrictionRegistry.getInstance().getByType("list").size());
		assertEquals("fleetMaxShips", entries.get(0).getId());
		assertEquals("fleetMaxShips", BattleNext_RestrictionRegistry.getInstance().getByType("number").get(0).getId());
	}

	@Test
	void invalidRestrictionKeepsPublishedSpecsAndLoader() throws Exception {
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		JSONArray valid = new JSONArray().put(new JSONObject().put("id", "extensionA")
				.put("type", "number").put("script", ExampleRestriction.class.getName()).put("desc", "A"));
		registry.load(valid, name -> new ExampleRestriction());
		JSONArray wrongInterface = new JSONArray().put(new JSONObject().put("id", "extensionA")
				.put("type", "list").put("script", String.class.getName()).put("desc", "Wrong"));
		IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
				() -> registry.load(wrongInterface, name -> "wrong"));
		assertTrue(mismatch.getMessage().contains("extensionA"));
		assertEquals("A", registry.get("extensionA").getDescription());
		assertTrue(registry.create("extensionA") instanceof ExampleRestriction);
		JSONArray duplicate = new JSONArray().put(valid.getJSONObject(0)).put(valid.getJSONObject(0));
		IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class,
				() -> registry.load(duplicate, name -> new ExampleRestriction()));
		assertTrue(repeated.getMessage().contains("row 2"));
		assertEquals(1, registry.getAll().size());
	}

	@Test
	void externalListRestrictionUsesItsOwnScript() throws Exception {
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		JSONArray rows = new JSONArray().put(new JSONObject().put("id", "extensionList")
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
		rows.put(new JSONObject().put("id", "extensionA").put("type", "number")
				.put("script", ExampleRestriction.class.getName()).put("desc", "Extension A"));
		rows.put(new JSONObject().put("id", "extensionB").put("type", "number")
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
		rows.put(new JSONObject().put("id", "bad").put("type", "number")
				.put("script", String.class.getName()).put("desc", "Bad"));
		assertThrows(IllegalArgumentException.class, () -> registry.load(rows, name -> "wrong"));
		assertEquals(2, registry.getAll().size());
		assertNotNull(registry.get("extensionA"));
		assertTrue(registry.create("extensionA") instanceof ExampleRestriction);
	}

	@Test
	void tournamentRuleIdRoundTrip() throws Exception {
		BattleNext_TournamentModel tournament = new BattleNext_TournamentModel();
		tournament.setRuleId("rules_a");
		JSONObject json = tournament.toJson();
		BattleNext_TournamentModel restored = new BattleNext_TournamentModel();
		restored.fromJson(json);
		assertEquals("rules_a", restored.getRuleId());
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
		for (BattleNext_RuleRestriction restriction : restrictions) {
			assertTrue(reasons.stream().anyMatch(reason -> reason.contains(restriction.getId())), restriction.getId());
		}
		assertEquals(8, reasons.size());
	}

	@Test
	void filtersApplyAcrossAllFourCategoriesAndSources() {
		for (String category : List.of("weapon", "wing", "hullmod", "hull")) {
			BattleNext_RuleModel rule = new BattleNext_RuleModel("rules", "Rules");
			String suffix = Character.toUpperCase(category.charAt(0)) + category.substring(1);
			rule.setRestrictions(List.of(list("filterAllowMods", "vanilla"),
					list("filterBlack" + suffix, "item"), list("filterWhite" + suffix, "other")));
			assertEquals(2, check.validateEquipment(rule, "fleet member[0]", category, "item", "vanilla").size());
			assertEquals(3, check.validateEquipment(rule, "fleet member[0]", category, "item", "someMod").size());
			assertTrue(check.validateEquipment(rule, "fleet member[0]", category, "other", "vanilla").isEmpty());
			rule.getRestriction("filterAllowMods").setItems(List.of());
			assertEquals(1, check.validateEquipment(rule, "fleet member[0]", category, "other", "vanilla").size());
			assertTrue(check.validateEquipment(rule, "fleet member[0]", category, "other", "").get(0)
					.contains("source mod unknown"));
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
		assertEquals(3, reasons.size());
		assertTrue(reasons.stream().anyMatch(reason -> reason.contains("fleetHullSizeLimitCapitalShip")));
		assertTrue(reasons.stream().anyMatch(reason -> reason.contains("refitMaxDecks")));
		assertTrue(reasons.stream().anyMatch(reason -> reason.contains("refitSmods")));
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
