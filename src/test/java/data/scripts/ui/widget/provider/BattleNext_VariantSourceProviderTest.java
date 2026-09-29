package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleNext_VariantSourceProviderTest {

	@Test
	void variantsAreSingleSelection() {
		BattleNext_VariantSourceProvider provider = new BattleNext_VariantSourceProvider();

		assertEquals(1, provider.getMaxCount("any_variant"));
	}

	@Test
	void percentageInVariantIdUsesPlainParagraphOverload() {
		List<String> rendered = new ArrayList<>();
		TooltipMakerAPI tooltip = (TooltipMakerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{TooltipMakerAPI.class}, (instance, method, args) -> {
					if ("addPara".equals(method.getName())) {
						assertEquals(2, method.getParameterCount());
						rendered.add((String) args[0]);
					}
					return null;
				});

		new BattleNext_VariantSourceProvider().createTooltip(tooltip,
				new OptionChoice("variant_50%_ready", "Variant"));

		assertEquals(List.of("variant_50%_ready"), rendered);
	}
}
