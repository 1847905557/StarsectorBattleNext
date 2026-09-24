package data.scripts.ui;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class BattleNext_PageLayout {

	public static final float GAP = 5f;
	public static final float HEADER_HEIGHT = 30f;
	public static final float CONTENT_TOP = 40f;
	public static final float FOOTER_HEIGHT = 30f;
	public static final float CONTENT_BOTTOM = 48f;

	private BattleNext_PageLayout() {
	}

	public static TooltipMakerAPI heading(CustomPanelAPI panel, String title) {
		TooltipMakerAPI maker = panel.createUIElement(panel.getPosition().getWidth(), HEADER_HEIGHT, false);
		LabelAPI label = maker.addSectionHeading(title, BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.HEADER, Alignment.MID, 0f);
		label.getPosition().inTL(0f, 0f);
		panel.addUIElement(maker).inTL(0f, 0f);
		return maker;
	}

	public static TooltipMakerAPI column(CustomPanelAPI panel, float x, float width) {
		float height = panel.getPosition().getHeight() - CONTENT_TOP - CONTENT_BOTTOM;
		TooltipMakerAPI maker = panel.createUIElement(width, height, true);
		BattleNext_UiTheme.configure(maker);
		panel.addUIElement(maker).inTL(x, CONTENT_TOP);
		return maker;
	}

	public static TooltipMakerAPI footer(CustomPanelAPI panel) {
		TooltipMakerAPI maker = panel.createUIElement(panel.getPosition().getWidth(), FOOTER_HEIGHT, false);
		panel.addUIElement(maker).inBL(0f, 8f);
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
