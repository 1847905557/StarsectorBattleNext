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
		TooltipMakerAPI maker = panel.createUIElement(Math.max(1f, rect.getWidth() - 10f), rect.getHeight(), false);
		LabelAPI label = maker.addSectionHeading(maker.shortenString(title, rect.getWidth() - 10f), BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.HEADER, Alignment.MID, 0f);
		label.getPosition().inTL(0f, 0f);
		maker.setHeightSoFar(rect.getHeight());
		panel.addUIElement(maker).inTL(rect.getX(), rect.getY());
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

	public static ButtonAPI button(TooltipMakerAPI maker, String title, Object id, float width) {
		ButtonAPI button = maker.addButton(maker.shortenString(title, Math.max(1f, width - 16f)), id, BattleNext_UiTheme.ACCENT,
				BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.ALL, width, FOOTER_HEIGHT, GAP);
		button.setMouseOverSound("BUtil_button_in");
		button.setButtonPressedSound("BUtil_button_down");
		return button;
	}

	public static ButtonAPI fixedButton(CustomPanelAPI panel, UIRect rect, String title, Object id,
			CutStyle style, boolean prominent) {
		TooltipMakerAPI maker = panel.createUIElement(Math.max(1f, rect.getWidth() - 10f), rect.getHeight(), false);
		maker.setButtonFontOrbitron20();
		ButtonAPI button = maker.addButton(maker.shortenString(title, Math.max(1f, rect.getWidth() - 16f)), id,
				prominent ? BattleNext_UiTheme.ACCENT : BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID, style, rect.getWidth(), rect.getHeight(), 0f);
		button.getPosition().inTL(0f, 0f);
		button.setMouseOverSound("BUtil_button_in");
		button.setButtonPressedSound("BUtil_button_down");
		maker.setHeightSoFar(rect.getHeight());
		panel.addUIElement(maker).inTL(rect.getX(), rect.getY());
		return button;
	}

	public static float listWidth(CustomPanelAPI panel) {
		return Math.min(480f, Math.max(320f, panel.getPosition().getWidth() * 0.38f));
	}
}
