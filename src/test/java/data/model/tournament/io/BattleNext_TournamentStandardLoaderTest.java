package data.model.tournament.io;

import data.model.BattleNext_LoadSource;
import data.model.tournament.BattleNext_TournamentModel;
import data.test.fixture.BattleNext_TestGameFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

class BattleNext_TournamentStandardLoaderTest extends BattleNext_TestGameFixture {
	@Test
	void configurationCatalogExcludesItsInstanceFileAndCarriesSourceIdentity() throws Exception {
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.ROUND_ROBIN, 3, 2);
		new BattleNext_TournamentSaver().save(configuration);
		Files.writeString(directory.resolve("tournament/_tournaments/started_tournament.json"), "{instance data}", StandardCharsets.UTF_8);
		List<BattleNext_TournamentModel> loaded = new BattleNext_TournamentStandardLoader().load();
		assertEquals(List.of("cup"), loaded.stream().map(BattleNext_TournamentModel::getId).toList());
		assertEquals(BattleNext_LoadSource.STANDARD, loaded.get(0).getSourceType());
		assertTrue(loaded.get(0).getSourcePath().endsWith("cup.json"));
		assertEquals(configuration.toJson().toString(), loaded.get(0).toJson().toString());
	}
}
