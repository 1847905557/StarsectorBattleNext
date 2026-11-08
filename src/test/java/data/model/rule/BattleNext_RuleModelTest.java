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

		BattleNext_RuleModel rule = new BattleNext_RuleModel(" id ", " Name ");
		BattleNext_RuleRestriction number = new BattleNext_RuleRestriction("testNumeric", "number");
		BattleNext_RuleRestriction items = new BattleNext_RuleRestriction("testItems", "list");
		number.setEnabled(true);
		number.setValue(3.5);
		items.setItems(List.of("first", "second"));
		rule.setRestrictions(List.of(number, items));
		rule.setRestrictions(rule.getRestrictionsReadonly());
		assertEquals(List.of(number, items), rule.getRestrictionsReadonly());
		assertSame(number, rule.getRestriction("testNumeric"));
		assertNull(rule.getRestriction("missing"));
		assertThrows(UnsupportedOperationException.class, () -> rule.getRestrictionsReadonly().clear());

		BattleNext_RuleModel loaded = new BattleNext_RuleModel();
		loaded.fromJson(rule.toJson());
		assertEquals("id", loaded.getId());
		assertEquals("Name", loaded.getName());
		assertEquals(rule.toJson().toString(), loaded.toJson().toString());
		assertEquals(List.of("testNumeric", "testItems"), loaded.getRestrictionsReadonly().stream().map(BattleNext_RuleRestriction::getId).toList());
		assertEquals("number", loaded.getRestriction("testNumeric").getType());
		assertTrue(loaded.getRestriction("testNumeric").isEnabled());
		assertEquals(3.5, loaded.getRestriction("testNumeric").getValue());
		assertEquals("list", loaded.getRestriction("testItems").getType());
		assertFalse(loaded.getRestriction("testItems").isEnabled());
		assertEquals(List.of("first", "second"), loaded.getRestriction("testItems").getItemsReadonly());
	}

	@Test
	void malformedAndConflictingRestrictionsRetainRawItemsAndValidSiblings() throws Exception {

		BattleNext_RuleModel rule = new BattleNext_RuleModel("original", "Original");
		BattleNext_RuleRestriction restriction = new BattleNext_RuleRestriction("testLimit", "number");
		rule.setRestrictions(List.of(restriction));
		JSONObject invalid = rule.toJson().put("id", "replacement").put("name", "Replacement")
				.put("restrictions", new JSONArray().put(restriction.toJson()).put(restriction.toJson()));
		rule.fromJson(invalid);
		assertTrue(rule.getRestrictionsReadonly().isEmpty());
		assertEquals(2, rule.getLoadErrorsCopy().size());
		assertEquals(invalid.toString(), rule.toJson().toString());

		invalid.put("restrictions", new JSONArray().put(restriction.toJson()).put(new JSONObject().put("id", "broken")));
		rule.fromJson(invalid);
		assertEquals(List.of("testLimit"), rule.getRestrictionsReadonly().stream().map(BattleNext_RuleRestriction::getId).toList());
		assertEquals(1, rule.getLoadErrorsCopy().size());

		String before = rule.toJson().toString();
		invalid.remove("formatVersion");
		assertThrows(JSONException.class, () -> rule.fromJson(invalid));
		assertEquals(before, rule.toJson().toString());
	}

	@Test
	void successfulRepeatedParsingReplacesItsRestrictionSet() throws Exception {
		BattleNext_RuleModel rule = new BattleNext_RuleModel("one", "One");
		rule.setRestrictions(List.of(new BattleNext_RuleRestriction("testLimit", "number")));
		rule.fromJson(new BattleNext_RuleModel("two", "Two").toJson());
		assertEquals("two", rule.getId());
		assertTrue(rule.getRestrictionsReadonly().isEmpty());
	}
}
