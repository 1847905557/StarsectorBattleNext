package data.model.rule;

import data.model.rule.BattleNext_RuleStore;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

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
	void invalidReloadKeepsPriorCatalogAndCanRecoverAfterFileRepair() throws Exception {
		var store = new BattleNext_RuleStore();
		store.reload();
		var original = new BattleNext_RuleModel("rule", "Original");
		store.save(original);
		var path = directory.resolve("tournament/_rules/rule.json");
		Files.writeString(path, "{\"formatVersion\":2}", StandardCharsets.UTF_8);
		assertThrows(IllegalStateException.class, store::reload);
		assertSame(original, store.get("rule"));
		assertFalse(store.isLoaded());
		assertThrows(IllegalStateException.class, () -> store.save(original));
		Files.writeString(path, original.toJson().toString(), StandardCharsets.UTF_8);
		store.reload();
		assertTrue(store.isLoaded());
		assertEquals("Original", store.get("rule").getName());
	}

	@Test
	void missingOptionalDirectoryPublishesAnEmptyLoadedCatalog() throws Exception {
		BattleNext_RuleStore store = new BattleNext_RuleStore();
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
	}
}
