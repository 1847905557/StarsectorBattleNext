package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.test.fixture.BattleNext_TestUiFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.util.List;

class BattleNext_VariantSourceProviderTest extends BattleNext_TestUiFixture {

	@Test
	void unavailableVariantKeepsItsLiteralIdentityAndExactDiagnostic() {
		TooltipMakerAPI tooltip = tooltip(360f, 0f, false);
		new BattleNext_VariantSourceProvider().createTooltip(tooltip, new OptionChoice("variant_50%_ready", "Variant"));
		List<LabelAPI> rendered = tooltips.get(tooltip).getComponents().stream()
				.filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).toList();
		assertEquals(List.of("Variant", "variant_50%_ready",
				"java.lang.IllegalArgumentException: Unknown variant: variant_50%_ready"),
				rendered.stream().map(LabelAPI::getText).toList());
		assertEquals(BattleNext_UiTheme.MUTED, labelColors.get(rendered.get(1).getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(rendered.get(2).getPosition()));
	}
}
