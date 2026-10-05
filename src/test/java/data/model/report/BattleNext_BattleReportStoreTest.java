package data.model.report;

import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

class BattleNext_BattleReportStoreTest extends BattleNext_TestGameFixture {

	@Test
	void saveReloadSortAndDeleteUseThePersistedCatalog() throws Exception {
		var store = new BattleNext_BattleReportStore();
		store.reload();
		var old = new BattleNext_BattleReportModel();
		old.setId("old");
		old.setName("Old");
		old.setCreatedAt(1);
		var recent = new BattleNext_BattleReportModel();
		recent.setId("recent");
		recent.setName("Recent");
		recent.setCreatedAt(10);
		var tie = new BattleNext_BattleReportModel();
		tie.setId("another_recent");
		tie.setName("Another recent");
		tie.setCreatedAt(recent.getCreatedAt());
		store.save(old);
		store.save(recent);
		store.save(tie);
		assertEquals("tournament/_reports/recent.json", recent.getSourcePath());
		store.reload();
		assertEquals(List.of("another_recent", "recent", "old"), store.getAll().stream().map(BattleNext_BattleReportModel::getId).toList());
		store.delete("recent");
		assertNull(store.get("recent"));
		assertFalse(Files.exists(directory.resolve("tournament/_reports/recent.json")));
		store.reload();
		assertEquals(List.of("another_recent", "old"), store.getAll().stream().map(BattleNext_BattleReportModel::getId).toList());
	}

	@Test
	void malformedReloadAndSaveFailureKeepPreviouslyPublishedObjects() throws Exception {
		var store = new BattleNext_BattleReportStore();
		store.reload();
		var report = new BattleNext_BattleReportModel();
		report.setId("valid");
		report.setName("Valid");
		store.save(report);
		Files.writeString(directory.resolve("tournament/_reports/broken.json"), "broken", StandardCharsets.UTF_8);
		assertThrows(IllegalStateException.class, store::reload);
		assertSame(report, store.get("valid"));
		assertFalse(store.isLoaded());
		Files.writeString(directory.resolve("tournament/_reports/broken.json"), report.toJson().put("id", "fixed").toString(), StandardCharsets.UTF_8);
		store.reload();
		var original = store.get("valid");
		Files.createDirectory(directory.resolve("tournament/_reports/blocked.json"));
		var blocked = new BattleNext_BattleReportModel();
		blocked.setId("blocked");
		assertThrows(IOException.class, () -> store.save(blocked));
		assertSame(original, store.get("valid"));
		assertNull(store.get("blocked"));
	}
}
