package data.scripts.tools;

import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

class BattleNext_ModFileManagerTest extends BattleNext_TestFleetIoFixture {

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
