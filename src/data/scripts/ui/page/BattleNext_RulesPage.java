package data.scripts.ui.page;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

public class BattleNext_RulesPage implements BattleNext_PageView {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		createContents();
	}

	@Override
	public void createContents() {
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		BattleNext_PageLayout.heading(header[0], panel, strings.get("page_rules"));

		UIRect contentRect = rest[1];
		TooltipMakerAPI content = contentRect.beginTooltip(panel, true);
		BattleNext_UiTheme.configure(content);
		content.addTitle(strings.get("rules_heading"));
		content.addPara(strings.get("rules_description"), 10f, BattleNext_UiTheme.TEXT);
		content.addPara(strings.get("page_placeholder"), 8f, BattleNext_UiTheme.MUTED);
		contentRect.addTooltip();
	}

	// static page: contents are created once and never refreshed
	@Override
	public void refreshContent() {

	}
}
