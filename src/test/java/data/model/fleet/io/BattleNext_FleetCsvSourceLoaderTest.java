package data.model.fleet.io;

import data.model.BattleNext_LoadSource;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_FleetCsvSourceLoaderTest extends BattleNext_TestFleetIoFixture {

	@Test
	void nativeCsvColumnsKeepIdentityMetadataEliteSkillsAndFileOrder() throws Exception {

		csvFleet("", "01", "");
		write("tournament/player01_data.csv", "tags,skills,author,name\n,,,,\n# comment\n"
				+ "full_assault,\"fleet,fleet\",作者,\"队伍,名称\"\n");
		write("tournament/player01_fleet.csv", "variant,name,rowNumber,skills,personality,portrait,orders,tags\n"
				+ ",,,,,,,\nparent_variant,First,9,\"gunnery,gunnery\",reckless,portrait.png,engage;,tag\n"
				+ "child_variant,Second,1,,steady,,,\n");
		var fleet = new BattleNext_FleetLegacyLoader().load().get(0);
		assertEquals("01", fleet.getId());
		assertEquals("legacy_01", fleet.getFolderName());
		assertEquals("队伍,名称", fleet.getName());
		assertEquals("作者", fleet.getAuthor());
		assertEquals(List.of("fleet", "fleet"), fleet.getSkills());
		assertEquals(List.of("full_assault"), fleet.getTags());
		assertEquals(List.of("First", "Second"), fleet.getMembers().stream().map(member -> member.getName()).toList());
		assertEquals(List.of("gunnery", "gunnery"), fleet.getMembers().get(0).getSkills());
		assertEquals("portrait.png", fleet.getMembers().get(0).getPortrait());
		assertEquals("engage;", fleet.getMembers().get(0).getOrders());
		assertEquals(BattleNext_LoadSource.LEGACY, fleet.getSourceType());
		assertEquals("tournament/player01_data.csv", fleet.getSourcePath());
		assertEquals(List.of("loadCSV:tournament/player01_data.csv", "loadCSV:tournament/player01_fleet.csv"), dataReads);
	}

	@Test
	void emptyMetadataReportsBothSourcePaths() throws Exception {
		csvFleet("", "1", "1,parent_variant,,,,,,\n");
		write("tournament/player1_data.csv", "name,author,skills,tags\n,,,,\n");
		var loader = new BattleNext_FleetLegacyLoader();
		assertTrue(loader.load().isEmpty());
		assertTrue(loader.getErrors().get(0).diagnostic().contains("player1_data.csv"));
		assertTrue(loader.getErrors().get(0).diagnostic().contains("player1_fleet.csv"));
	}

	@Test
	void rootSourceRecognizesItsOwnCompletePairs() throws Exception {
		csvFleet("", "1", "1,parent_variant,,,,,,\n");
		csvFleet("nested", "2", "1,parent_variant,,,,,,\n");
		write("tournament/player3_data.csv", "name,author,skills,tags\nTeam,,,\n");
		assertEquals(List.of("1"), new BattleNext_FleetLegacyLoader().load().stream().map(fleet -> fleet.getId()).toList());
	}
}
