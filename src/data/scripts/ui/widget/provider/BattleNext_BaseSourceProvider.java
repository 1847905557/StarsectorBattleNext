package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_EquipmentTooltip;
import data.scripts.ui.widget.BattleNext_OptionPicker;

public abstract class BattleNext_BaseSourceProvider implements BattleNext_OptionPicker.OptionChoiceSource {

	@Override
	public boolean expectsIcon() {
		return true;
	}

	private static final float KEY_GAP = 5f;
	private static final float BODY_GAP = 8f;

	protected void addHeader(TooltipMakerAPI tooltip, String title, String key) {
		tooltip.addTitle(title);
		BattleNext_PageLayout.addMutedIdLine(tooltip, key, KEY_GAP);
	}

	protected void addDescription(TooltipMakerAPI tooltip, String text) {
		String paragraph = BattleNext_EquipmentTooltip.firstParagraph(text);
		if (paragraph.isEmpty()) return;
		tooltip.setParaFontDefault();
		tooltip.setParaFontColor(BattleNext_UiTheme.TEXT);
		tooltip.addPara(paragraph, BattleNext_UiTheme.TEXT, BODY_GAP);
	}
}
