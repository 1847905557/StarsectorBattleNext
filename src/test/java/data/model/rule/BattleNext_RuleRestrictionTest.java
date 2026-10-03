package data.model.rule;

import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.numeric.FleetMaxShips;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

class BattleNext_RuleRestrictionTest {

	private Object entries;
	private Object factory;

	@BeforeEach
	void configureRegistry() throws Exception {
		var registry = BattleNext_RestrictionRegistry.getInstance();
		Field entryField = BattleNext_RestrictionRegistry.class.getDeclaredField("entries");
		Field factoryField = BattleNext_RestrictionRegistry.class.getDeclaredField("scriptFactory");
		entryField.setAccessible(true);
		factoryField.setAccessible(true);
		entries = entryField.get(registry);
		factory = factoryField.get(registry);
		var row = new JSONObject().put("id", "testRegisteredNumber").put("type", "number")
				.put("script", "data.restriction.numeric.FleetMaxShips").put("desc", "Test");
		registry.load(new JSONArray().put(row), name -> new FleetMaxShips());
	}

	@AfterEach
	void restoreRegistry() throws Exception {
		Field entryField = BattleNext_RestrictionRegistry.class.getDeclaredField("entries");
		Field factoryField = BattleNext_RestrictionRegistry.class.getDeclaredField("scriptFactory");
		entryField.setAccessible(true);
		factoryField.setAccessible(true);
		entryField.set(BattleNext_RestrictionRegistry.getInstance(), entries);
		factoryField.set(BattleNext_RestrictionRegistry.getInstance(), factory);
	}

	@Test
	void registeredValuesUseTheDeclaredTypeAndRejectConflictingPayloads() throws Exception {
		JSONObject json = new JSONObject().put("id", "testRegisteredNumber").put("enabled", true).put("value", 3.5);
		var restriction = BattleNext_RuleRestriction.fromJson(json);
		assertEquals("number", restriction.getType());
		assertEquals(3.5, restriction.getValue());
		assertTrue(restriction.isEnabled());
		json.remove("value");
		json.put("items", new JSONArray());
		assertThrows(IllegalArgumentException.class, () -> BattleNext_RuleRestriction.fromJson(json));
		json.put("value", 1);
		assertThrows(IllegalArgumentException.class, () -> BattleNext_RuleRestriction.fromJson(json));
		json.remove("value");
		json.remove("items");
		assertThrows(IllegalArgumentException.class, () -> BattleNext_RuleRestriction.fromJson(json));
	}

	@ParameterizedTest
	@ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
	void invalidNumbersPreserveTheExistingValue(double value) {
		var restriction = new BattleNext_RuleRestriction("number", "number");
		restriction.setValue(2.5);
		assertThrows(IllegalArgumentException.class, () -> restriction.setValue(value));
		assertEquals(2.5, restriction.getValue());
	}

	@Test
	void listsNormalizeAndRejectDuplicateOrEmptyItemsWithoutChangingTheOldList() {
		var restriction = new BattleNext_RuleRestriction("list", "list");
		restriction.setItems(List.of(" a ", "b"));
		assertEquals(List.of("a", "b"), restriction.getItems());
		assertThrows(IllegalArgumentException.class, () -> restriction.setItems(List.of("a", " a ")));
		assertThrows(IllegalArgumentException.class, () -> restriction.setItems(List.of("")));
		assertEquals(List.of("a", "b"), restriction.getItems());
		assertThrows(UnsupportedOperationException.class, () -> restriction.getItems().clear());
		restriction.setItems(restriction.getItems());
		assertEquals(List.of("a", "b"), restriction.getItems());
	}

	@Test
	void unknownRestrictionPreservesExtensionJsonUntilAnExplicitEdit() throws Exception {
		JSONObject json = new JSONObject().put("id", "testUnknown").put("enabled", false).put("value", 4)
				.put("extension", new JSONObject().put("text", "keep"));
		var restriction = BattleNext_RuleRestriction.fromJson(json);
		json.getJSONObject("extension").put("text", "changed");
		assertEquals("keep", restriction.toJson().getJSONObject("extension").getString("text"));
		JSONObject exported = restriction.toJson();
		exported.put("value", 99);
		assertEquals(4d, restriction.getValue());
		restriction.setEnabled(true);
		assertFalse(restriction.toJson().has("extension"));
		assertTrue(restriction.toJson().getBoolean("enabled"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "bad.id"})
	void restrictionIdsUseTheFlatIdentityContract(String id) {
		assertThrows(IllegalArgumentException.class, () -> new BattleNext_RuleRestriction(id, "number"));
		assertThrows(IllegalArgumentException.class, () -> new BattleNext_RuleRestriction("valid", "unsupported"));
	}
}
