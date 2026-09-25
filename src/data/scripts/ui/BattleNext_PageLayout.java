package data.scripts.ui;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.ui.UIPanelManager;
import data.scripts.battlesnext.util.ui.UIRect;

public class BattleNext_PageLayout {

	public static final float GAP = 5f;
	public static final float HEADER_HEIGHT = 30f;
	public static final float CONTENT_TOP = 40f;
	public static final float FOOTER_HEIGHT = 30f;
	public static final float CONTENT_BOTTOM = 48f;
	public static final float FOOTER_MARGIN = 8f;

	private BattleNext_PageLayout() {
	}

	public static TooltipMakerAPI heading(UIRect rect, CustomPanelAPI panel, String title) {
		TooltipMakerAPI maker = rect.beginTooltip(panel, false);
		LabelAPI label = maker.addSectionHeading(title, BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.HEADER, Alignment.MID, 0f);
		label.getPosition().inTL(0f, 0f);
		rect.addTooltip();
		return maker;
	}

	public static TooltipMakerAPI column(UIRect rect, CustomPanelAPI panel) {
		rect.removeLatestSubPanel();
		UIPanelManager manager = rect.createSubPanel(panel, null);
		TooltipMakerAPI maker = manager.getPanel().createUIElement(rect.getWidth(), rect.getHeight(), true);
		BattleNext_UiTheme.configure(maker);
		manager.getPanel().addUIElement(maker).inTL(0f, 0f);
		return maker;
	}

	public static TooltipMakerAPI footer(UIRect rect, CustomPanelAPI panel) {
		TooltipMakerAPI maker = rect.beginTooltip(panel, false);
		rect.addTooltip();
		return maker;
	}

	public static ButtonAPI button(TooltipMakerAPI maker, String title, Object id, float width) {
		ButtonAPI button = maker.addButton(title, id, BattleNext_UiTheme.ACCENT,
				BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.ALL, width, FOOTER_HEIGHT, GAP);
		button.setMouseOverSound("BUtil_button_in");
		button.setButtonPressedSound("BUtil_button_down");
		return button;
	}

	public static float listWidth(CustomPanelAPI panel) {
		return Math.min(480f, Math.max(320f, panel.getPosition().getWidth() * 0.38f));
	}
}
