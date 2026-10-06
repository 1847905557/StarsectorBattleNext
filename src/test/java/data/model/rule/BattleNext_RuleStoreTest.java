package data.model.rule;

import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

class BattleNext_RuleStoreTest extends BattleNext_TestGameFixture {

	@Test
	void savingAndDeletingReloadedRulesMaintainCatalogAndFilesystem() throws Exception {
		var store = new BattleNext_RuleStore();
		store.reload();
		var rule = new BattleNext_RuleModel("rule", "规则");
		store.save(rule);
		assertEquals("tournament/_rules/rule.json", rule.getSourcePath());
		store.reload();
		assertEquals("规则", store.get("rule").getName());
		store.delete("rule");
		assertNull(store.get("rule"));
		assertFalse(Files.exists(directory.resolve("tournament/_rules/rule.json")));
		store.reload();
		assertTrue(store.getAll().isEmpty());
	}

	@Test
	void invalidEntryPublishesItsIdentityAndRecoversAfterFileRepair() throws Exception {
		var store = new BattleNext_RuleStore();
		store.reload();
		var original = new BattleNext_RuleModel("rule", "Original");
		store.save(original);
		var path = directory.resolve("tournament/_rules/rule.json");
		Files.writeString(path, original.toJson().put("restrictions", "invalid").toString(), StandardCharsets.UTF_8);
		store.reload();
		assertNull(store.get("rule"));
		assertTrue(store.isLoaded());
		assertEquals("rule", store.getErrors().get(0).id());
		assertTrue(store.getErrors().get(0).raw().contains("invalid"));
		Files.writeString(path, original.toJson().toString(), StandardCharsets.UTF_8);
		store.reload();
		assertTrue(store.isLoaded());
		assertEquals("Original", store.get("rule").getName());
		assertTrue(store.getErrors().isEmpty());
	}
}
