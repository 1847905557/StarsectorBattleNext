package data.scripts.ui;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
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

	public static ButtonAPI buttonWithTooltip(TooltipMakerAPI maker, String title, Object id, float width) {
		ButtonAPI button = button(maker, title, id, width);
		maker.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return Math.min(500f, Math.max(220f, title.length() * 10f));
			}
			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				tooltip.addPara(title, 0f, BattleNext_UiTheme.TEXT);
			}
		}, TooltipLocation.RIGHT);
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

	public static UIRect[] managementRegions(CustomPanelAPI panel) {
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		float listWidth = Math.min(400f, Math.max(260f, width * 0.22f));
		listWidth = Math.min(listWidth, width * 0.4f);
		float contentHeight = height - CONTENT_TOP - CONTENT_BOTTOM - FOOTER_MARGIN;
		UIRect list = new UIRect(8f, CONTENT_TOP, listWidth, contentHeight);
		UIRect detail = new UIRect(28f + listWidth, CONTENT_TOP, width - listWidth - 36f, contentHeight);
		UIRect footer = new UIRect(8f, height - CONTENT_BOTTOM + FOOTER_MARGIN,
				width - 16f, FOOTER_HEIGHT);
		return new UIRect[]{list, detail, footer};
	}
}
