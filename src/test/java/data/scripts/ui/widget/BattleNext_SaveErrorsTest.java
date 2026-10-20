package data.scripts.ui.widget;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.tools.BattleNext_Settings;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SaveErrorsTest extends BattleNext_TestPageFixture {

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void idDetailsPreserveInputsLocalizeReasonsAndExposeEveryErrorInOrder(boolean chinese) {
		BattleNext_Settings.save(chinese, true, false, false, 500f, 1f, 3f, 50, 0);
		var errors = new BattleNext_SaveErrors();
		List<String> inputs = List.of("", "123", "舰\u3000队", "  🚀?舰  ", "fleet.", "CON.txt", "舰\u0001队");
		List<String> reasons = List.of(
				BattleNext_TestTextCatalog.text("Common", "BattleNext_common_error_required"),
				BattleNext_TestTextCatalog.text("Common", "BattleNext_common_error_id_numeric"),
				String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_error_id_whitespace"), 2, "U+3000"),
				String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_error_id_character"), 4, "U+003F"),
				BattleNext_TestTextCatalog.text("Common", "BattleNext_common_error_id_trailing_dot"),
				BattleNext_TestTextCatalog.text("Common", "BattleNext_common_error_id_reserved"),
				String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_error_id_control"), 2, "U+0001"));
		List<String> expected = new ArrayList<>();
		for (int index = 0; index < inputs.size(); index++) {
			errors.id("Fleet", "ID", inputs.get(index));
			String visible = index == 6 ? "舰U+0001队" : inputs.get(index);
			expected.add(String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_field_error"),
					"Fleet", "ID", visible, reasons.get(index)));
		}
		assertEquals(expected, errors.getMessages());
		assertEquals(String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_errors_summary"), 7, expected.get(0)), errors.summary());
		TooltipMakerAPI hover = tooltip(480f, 0f, false);
		BattleNext_SaveErrors.tooltip(errors::getMessages).createTooltip(hover, false, null);
		assertEquals(IntStream.range(0, 7).mapToObj(index -> (index + 1) + ". " + expected.get(index)).toList(),
				tooltips.get(hover).getComponents().stream().map(LabelAPI.class::cast).map(LabelAPI::getText).toList());
		errors.clear();
		errors.id("Fleet", "ID", "  valid_id  ");
		assertEquals("", errors.summary());
		assertTrue(errors.getMessages().isEmpty());
	}
}
