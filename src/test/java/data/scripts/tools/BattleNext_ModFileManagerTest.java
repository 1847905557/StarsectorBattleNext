package data.scripts.tools;

import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class BattleNext_ModFileManagerTest extends BattleNext_TestFleetIoFixture {

	@ParameterizedTest
	@ValueSource(strings = {"普通名称", "Alpha, Beta", "Alpha \"Beta\"", "Alpha, \"Beta\""})
	void sessionCsvRetainsNamesWithCommasAndEscapedQuotes(String name) throws Exception {
		String path = "tournament/player1_data.csv";
		write(path, "name,author,tags,skills\n" + BattleNext_CsvLine.escape(name) + ",作者,,\"\"\n");
		var direct = BattleNext_ModFileManager.readCsv(getClass(), path, "AI_Battles_Next");
		BattleNext_ModFileManager.ReadSession session = new BattleNext_ModFileManager.ReadSession(directory.toString());
		session.activate();
		try {
			var rows = BattleNext_ModFileManager.readCsv(getClass(), path, "AI_Battles_Next");
			assertEquals(1, rows.length());
			assertEquals(name, rows.getJSONObject(0).getString("name"));
			assertEquals("作者", rows.getJSONObject(0).getString("author"));
			assertEquals("", rows.getJSONObject(0).getString("tags"));
			assertEquals("", rows.getJSONObject(0).getString("skills"));
			assertEquals(direct.toString(), rows.toString());
		} finally {
			BattleNext_ModFileManager.ReadSession.deactivate();
		}
	}

	@Test
	void readSessionPublishesAtomicWritesAndInvalidatesDeletedAndMovedFiles() throws Exception {
		String path = "tournament/fleet/fleet_data.json";
		write(path, "{\"name\":\"Original\"}");
		BattleNext_ModFileManager.ReadSession session = new BattleNext_ModFileManager.ReadSession(directory.toString());
		session.activate();
		try {
			assertEquals("Original", BattleNext_ModFileManager.readJson(getClass(), path, "AI_Battles_Next").getString("name"));
			BattleNext_ModFileManager.writeTextAtomic(path, "{\"name\":\"Saved\"}");
			assertEquals("Saved", BattleNext_ModFileManager.readJson(getClass(), path, "AI_Battles_Next").getString("name"));
			assertEquals("{\"name\":\"Saved\"}", new String(BattleNext_ModFileManager.readBytes(path), StandardCharsets.UTF_8));
			BattleNext_ModFileManager.moveDirectory("tournament/fleet", "tournament/renamed");
			assertThrows(IOException.class, () -> BattleNext_ModFileManager.readBytes(path));
			String renamed = "tournament/renamed/fleet_data.json";
			assertEquals("Saved", BattleNext_ModFileManager.readJson(getClass(), renamed, "AI_Battles_Next").getString("name"));
			BattleNext_ModFileManager.delete(renamed);
			assertThrows(IOException.class, () -> BattleNext_ModFileManager.readBytes(renamed));
			assertThrows(IOException.class, () -> BattleNext_ModFileManager.readJson(getClass(), renamed, "AI_Battles_Next"));
		} finally {
			BattleNext_ModFileManager.ReadSession.deactivate();
		}
	}

	@Test
	void boundedListingVisitsOnlySelectedDirectoriesAndKeepsTheirImmediateFiles() throws Exception {
		write("tournament/player1_data.csv", "root\n");
		write("tournament/fleet/fleet_data.json", "{}\n");
		write("tournament/fleet/local.variant", "{}\n");
		write("tournament/fleet/backup/fleet_data.json", "{}\n");
		write("tournament/_reports/report.json", "{}\n");
		List<String> visited = new ArrayList<>();
		List<String> paths = BattleNext_ModFileManager.list("tournament", 2, path -> {
			visited.add(path);
			return !path.endsWith("/_reports");
		});
		assertEquals(List.of("tournament/fleet/fleet_data.json", "tournament/fleet/local.variant", "tournament/player1_data.csv"), paths);
		assertEquals(List.of("tournament/_reports", "tournament/fleet"), visited.stream().sorted().toList());
		assertEquals(5, BattleNext_ModFileManager.list("tournament", true).size());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", ".", "starsector-core/.."})
	void recursiveDeletionHandlesModRootPathSegments(String suffix) throws Exception {
		Files.createDirectories(directory.resolve("starsector-core"));
		Path root = directory;
		directory = directory.resolve(suffix);
		String fleet = "tournament/98原版 白板  200dp 大鸟嚼嚼嚼";
		write(fleet + "/player6_data.csv", "name,author\nVR,Virtin\n");
		write(fleet + "/player6_fleet.csv", "variant\nheron\n");
		write(fleet + "/backup/snapshot.json", "{}\n");
		write("tournament/other/fleet_data.json", "{}\n");

		BattleNext_ModFileManager.deleteRecursively(fleet);

		assertFalse(Files.exists(root.resolve(fleet)));
		assertEquals("{}\n", Files.readString(root.resolve("tournament/other/fleet_data.json")));
		assertTrue(Files.isDirectory(root.resolve("starsector-core")));
	}
}
