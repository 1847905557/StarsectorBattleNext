package data.scripts.ui;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;

public class BattleNext_RulesPage {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");

	public void attach(CustomPanelAPI panel) {
		BattleNext_PageLayout.heading(panel, strings.get("page_rules"));
		TooltipMakerAPI content = BattleNext_PageLayout.column(panel, 0f, panel.getPosition().getWidth());
		content.addTitle(strings.get("rules_heading"));
		content.addPara(strings.get("rules_description"), 10f, BattleNext_UiTheme.TEXT);
		content.addPara(strings.get("page_placeholder"), 8f, BattleNext_UiTheme.MUTED);
	}
}
