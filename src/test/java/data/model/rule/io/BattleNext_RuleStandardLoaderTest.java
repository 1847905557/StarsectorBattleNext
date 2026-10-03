package data.model.rule.io;

import data.model.BattleNext_LoadSource;
import data.model.rule.BattleNext_RuleModel;
import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

class BattleNext_RuleStandardLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void nativeJsonReadsFlatResourcesInPathOrderAndMarksTheirOrigins() throws Exception {
		var root = directory.resolve("tournament/_rules");
		Files.createDirectories(root.resolve("nested"));
		for (String id : List.of("a", "b")) {
			Files.writeString(root.resolve(id + ".json"), new BattleNext_RuleModel(id, id).toJson().toString(), StandardCharsets.UTF_8);
		}
		Files.writeString(root.resolve("nested/ignored.json"), "broken", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("ignored.txt"), "broken", StandardCharsets.UTF_8);
		var rules = new BattleNext_RuleStandardLoader().load();
		assertEquals(List.of("a", "b"), rules.stream().map(rule -> rule.getId()).toList());
		assertEquals(BattleNext_LoadSource.STANDARD, rules.get(0).getSourceType());
		assertEquals("tournament/_rules/a.json", rules.get(0).getSourcePath());
		assertEquals(List.of("loadJSON:tournament/_rules/a.json", "loadJSON:tournament/_rules/b.json"), dataReads);
	}

	@Test
	void missingOptionalDirectoryLoadsAnEmptyCatalog() throws Exception {
		BattleNext_RuleStandardLoader loader = new BattleNext_RuleStandardLoader();
		assertTrue(loader.load().isEmpty());
	}
}
