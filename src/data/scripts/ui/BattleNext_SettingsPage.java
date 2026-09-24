package data.scripts.ui;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.util.BattleNext_I18nSection;

public class BattleNext_SettingsPage {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");

	public void attach(CustomPanelAPI panel) {
		BattleNext_PageLayout.heading(panel, strings.get("page_settings"));
		TooltipMakerAPI content = BattleNext_PageLayout.column(panel, 0f, panel.getPosition().getWidth());
		content.addTitle(strings.get("settings_heading"));
		content.addPara(strings.get("settings_description"), 10f, BattleNext_UiTheme.TEXT);
		content.addPara(strings.get("page_placeholder"), 8f, BattleNext_UiTheme.MUTED);
	}
}
