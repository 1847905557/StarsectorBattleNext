package data.model.rule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_RuleModelTest {

	@Test
	void restrictionsReplaceInOrderAndTheirOwnViewCanBeReassigned() throws Exception {
		var rule = new BattleNext_RuleModel(" id ", " Name ");
		var number = new BattleNext_RuleRestriction("testNumeric", "number");
		var items = new BattleNext_RuleRestriction("testItems", "list");
		rule.setRestrictions(List.of(number, items));
		rule.setRestrictions(rule.getRestrictions());
		assertEquals(List.of(number, items), rule.getRestrictions());
		assertSame(number, rule.getRestriction("testNumeric"));
		assertNull(rule.getRestriction("missing"));
		assertThrows(UnsupportedOperationException.class, () -> rule.getRestrictions().clear());
		var loaded = new BattleNext_RuleModel();
		loaded.fromJson(rule.toJson());
		assertEquals("id", loaded.getId());
		assertEquals("Name", loaded.getName());
		assertEquals(rule.toJson().toString(), loaded.toJson().toString());
	}

	@Test
	void malformedOrDuplicateRestrictionParsingKeepsTheWholePublishedModel() throws Exception {
		var rule = new BattleNext_RuleModel("original", "Original");
		var restriction = new BattleNext_RuleRestriction("testLimit", "number");
		rule.setRestrictions(List.of(restriction));
		String before = rule.toJson().toString();
		JSONObject invalid = rule.toJson().put("id", "replacement").put("name", "Replacement")
				.put("restrictions", new JSONArray().put(restriction.toJson()).put(restriction.toJson()));
		assertThrows(IllegalArgumentException.class, () -> rule.fromJson(invalid));
		assertEquals(before, rule.toJson().toString());
		invalid.put("restrictions", new JSONArray().put(new JSONObject().put("id", "broken")));
		assertThrows(IllegalArgumentException.class, () -> rule.fromJson(invalid));
		assertEquals(before, rule.toJson().toString());
		invalid.remove("formatVersion");
		assertThrows(JSONException.class, () -> rule.fromJson(invalid));
		assertEquals(before, rule.toJson().toString());
	}

	@Test
	void successfulRepeatedParsingReplacesItsRestrictionSet() throws Exception {
		var rule = new BattleNext_RuleModel("one", "One");
		rule.setRestrictions(List.of(new BattleNext_RuleRestriction("testLimit", "number")));
		rule.fromJson(new BattleNext_RuleModel("two", "Two").toJson());
		assertEquals("two", rule.getId());
		assertTrue(rule.getRestrictions().isEmpty());
	}
}
