package data.scripts.ui.widget.provider;

import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_PersonalitySourceProviderTest {

	@Test
	void personalityRowsUseIdAndName() throws Exception {
		OptionChoice option = BattleNext_PersonalitySourceProvider.toOption(
				new JSONObject("{\"id\":\"aggressive\",\"name\":\"Aggressive\",\"desc\":\"desc\"}"));

		assertEquals("aggressive", option.getId());
		assertEquals("Aggressive", option.getLabel());
	}

	@Test
	void emptyPersonalityIdsAreSkipped() throws Exception {
		assertNull(BattleNext_PersonalitySourceProvider.toOption(
				new JSONObject("{\"id\":\"\",\"name\":\"Unknown\"}")));
	}

	@Test
	void missingPersonalityNameFallsBackToId() throws Exception {
		OptionChoice option = BattleNext_PersonalitySourceProvider.toOption(
				new JSONObject("{\"id\":\"steady\"}"));

		assertEquals("steady", option.getLabel());
	}

	@Test
	void optionsSkipEmptyIdsAndSortById() throws Exception {
		List<OptionChoice> options = BattleNext_PersonalitySourceProvider.toOptions(List.of(
				new JSONObject("{\"id\":\"steady\",\"name\":\"Steady\"}"),
				new JSONObject("{\"id\":\"\",\"name\":\"Unknown\"}"),
				new JSONObject("{\"id\":\"aggressive\",\"name\":\"Aggressive\"}")));

		assertEquals(List.of("aggressive", "steady"), options.stream().map(OptionChoice::getId).toList());
	}
}
