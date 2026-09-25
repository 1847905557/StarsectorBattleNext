package data.scripts.ui.page;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_PageView;
import data.scripts.ui.BattleNext_UiTheme;

public class BattleNext_SettingsPage implements BattleNext_PageView {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");

	@Override
	public void attach(CustomPanelAPI panel) {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] columns = rest[1].splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		BattleNext_PageLayout.heading(header[0], panel, strings.get("page_settings"));
		TooltipMakerAPI content = BattleNext_PageLayout.column(columns[0], panel);
		content.addTitle(strings.get("settings_heading"));
		content.addPara(strings.get("settings_description"), 10f, BattleNext_UiTheme.TEXT);
		content.addPara(strings.get("page_placeholder"), 8f, BattleNext_UiTheme.MUTED);
	}
}
