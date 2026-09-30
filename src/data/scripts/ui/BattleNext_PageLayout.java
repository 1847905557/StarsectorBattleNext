package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
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

import java.awt.Color;

public class BattleNext_PageLayout {

	public static final float BUTTON_VERTICAL_GAP = 5f;
	public static final float HEADER_HEIGHT = 30f;
	public static final float CONTENT_TOP = 40f;
	public static final float FOOTER_HEIGHT = 30f;
	public static final float CONTENT_BOTTOM = 48f;
	public static final float FOOTER_MARGIN = 8f;

	public static final float MANAGEMENT_CONTENT_INSET = 8f;
	public static final float MANAGEMENT_SCROLLBAR_RESERVE = 2f;
	public static final float MANAGEMENT_REGION_BORDER_THICKNESS = 2f;
	public static final float MANAGEMENT_LIST_BUTTON_MIN_WIDTH = 80f;
	public static final float MANAGEMENT_LIST_BUTTON_WIDTH_RESERVE = 30f;
	public static final float MANAGEMENT_LIST_ERROR_TOP_GAP = 8f;
	public static final float MANAGEMENT_LIST_EMPTY_TOP_GAP = 10f;
	public static final float MANAGEMENT_EDITOR_FOOTER_RESERVE = 42f;
	public static final float MANAGEMENT_DELETE_BUTTON_WIDTH = 145f;
	public static final float MANAGEMENT_DELETE_BUTTON_HEIGHT = 28f;
	public static final float MANAGEMENT_DELETE_BUTTON_BOTTOM_INSET = 8f;
	public static final float MANAGEMENT_SOURCE_NOTE_LEFT = 165f;
	public static final float MANAGEMENT_SOURCE_NOTE_WIDTH_RESERVE = 170f;
	public static final float MANAGEMENT_SOURCE_NOTE_TEXT_RESERVE = 180f;
	public static final float MANAGEMENT_SOURCE_NOTE_BOTTOM_INSET = 6f;
	public static final float MANAGEMENT_FOOTER_BUTTON_MAX_WIDTH = 160f;
	public static final float MANAGEMENT_FOOTER_BUTTON_GAP = 10f;
	public static final float MANAGEMENT_FOOTER_BUTTON_WIDTH_RESERVE = 20f;
	public static final float MANAGEMENT_FOOTER_STATUS_OFFSET = 30f;
	public static final float MANAGEMENT_FOOTER_STATUS_MIN_WIDTH = 60f;
	public static final float MANAGEMENT_FOOTER_STATUS_TOP_INSET = 5f;

	public static final float FORM_FIELD_HEIGHT = 28f;
	public static final float EDITOR_COLUMN_GAP = 10f;
	public static final float EDITOR_LEFT_COLUMN_SHARE = 0.45f;
	public static final float EDITOR_MIN_SPLIT_WIDTH = 2f;

	public static final float TABLE_HEADER_HEIGHT = 18f;
	public static final float TABLE_ROW_HEIGHT = 38f;
	public static final float TABLE_COLUMN_GAP = 8f;
	public static final float TABLE_ROW_BOTTOM_PADDING = 10f;
	public static final float TABLE_ROW_BORDER_THICKNESS = 2f;
	public static final float TABLE_SIDE_MARGIN = 12f;
	public static final float SECTION_HEADING_EXTRA_WIDTH = 10f;
	public static final float SCROLLBAR_RESERVED_WIDTH = 5f;
	public static final float CUSTOM_COMPONENT_LEFT_INSET = 5f;
	public static final float TABLE_FIELD_LEFT_INSET = 3f;
	public static final float TABLE_FIELD_RIGHT_INSET = 7f;
	public static final float TABLE_CHECKBOX_MAX_WIDTH = 22f;
	public static final float TABLE_ACTION_BUTTON_HEIGHT = 28f;

	public static final float CELL_LABEL_SIDE_INSET = 2f;
	public static final float CELL_LABEL_TRUNCATION_PADDING = 10f;

	private static final float MIN_UI_WIDTH = 1f;
	static final String MISSING_ICON_CATEGORY = "ui";
	static final String MISSING_ICON_KEY = "checkmark_x";
	private static final float PAGE_TITLE_TRUNCATION_PADDING = 10f;

	private static final float BUTTON_TEXT_TRUNCATION_PADDING = 16f;
	private static final float BUTTON_EXTRA_DRAW_WIDTH = 10f;

	private static final float TOOLTIP_MIN_WIDTH = 220f;
	private static final float TOOLTIP_MAX_WIDTH = 500f;
	private static final float TOOLTIP_WIDTH_PER_CHAR = 10f;

	private static final float LIST_MIN_WIDTH = 320f;
	private static final float LIST_MAX_WIDTH = 480f;
	private static final float LIST_WIDTH_SHARE = 0.38f;

	private static final float MANAGEMENT_OUTER_MARGIN = 8f;
	private static final float MANAGEMENT_COLUMN_GAP = 20f;
	private static final float MANAGEMENT_LIST_MIN_WIDTH = 260f;
	private static final float MANAGEMENT_LIST_MAX_WIDTH = 400f;
	private static final float MANAGEMENT_LIST_WIDTH_SHARE = 0.22f;
	private static final float MANAGEMENT_LIST_MAX_SHARE = 0.4f;

	private BattleNext_PageLayout() {
	}

	public static SpriteAPI missingIcon() {
		return Global.getSettings().getSprite(MISSING_ICON_CATEGORY, MISSING_ICON_KEY);
	}

	public static void renderMissingIcon(SpriteAPI sprite, float x, float y, float size, float alpha) {
		renderIcon(sprite, x, y, size, BattleNext_UiTheme.ACCENT, alpha);
	}

	public static float spriteScaleToFit(float width, float height, float availableWidth, float availableHeight) {
		return Math.min(1f, Math.min(availableWidth / width, availableHeight / height));
	}

	public static void renderFittedSprite(SpriteAPI sprite, float centerX, float centerY,
			float availableWidth, float availableHeight, float alpha) {
		float width = sprite.getWidth();
		float height = sprite.getHeight();
		float previousCenterX = sprite.getCenterX();
		float previousCenterY = sprite.getCenterY();
		float previousAlpha = sprite.getAlphaMult();
		float scale = spriteScaleToFit(width, height, availableWidth, availableHeight);
		try {
			sprite.setSize(width * scale, height * scale);
			sprite.setCenter(width * scale * 0.5f, height * scale * 0.5f);
			sprite.setAlphaMult(previousAlpha * alpha);
			sprite.renderAtCenter(centerX, centerY);
		} finally {
			sprite.setSize(width, height);
			sprite.setCenter(previousCenterX, previousCenterY);
			sprite.setAlphaMult(previousAlpha);
		}
	}

	static void renderIcon(SpriteAPI sprite, float x, float y, float size, Color color, float alpha) {
		Color previousColor = sprite.getColor();
		float previousWidth = sprite.getWidth();
		float previousHeight = sprite.getHeight();
		float previousAlpha = sprite.getAlphaMult();
		try {
			sprite.setColor(color);
			sprite.setSize(size, size);
			sprite.setAlphaMult(previousAlpha * alpha);
			sprite.renderAtCenter(x + size * 0.5f, y + size * 0.5f);
		} finally {
			sprite.setColor(previousColor);
			sprite.setSize(previousWidth, previousHeight);
			sprite.setAlphaMult(previousAlpha);
		}
	}

	public static TooltipMakerAPI heading(UIRect rect, CustomPanelAPI panel, String title) {
		TooltipMakerAPI maker = panel.createUIElement(Math.max(MIN_UI_WIDTH, rect.getWidth()), rect.getHeight(), false);
		LabelAPI label = maker.addSectionHeading(maker.shortenString(title,
				rect.getWidth() - PAGE_TITLE_TRUNCATION_PADDING), BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID, 0f);
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
		ButtonAPI button = maker.addButton(maker.shortenString(title,
				Math.max(MIN_UI_WIDTH, width - BUTTON_TEXT_TRUNCATION_PADDING)), id, BattleNext_UiTheme.ACCENT,
				BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.ALL, width, FOOTER_HEIGHT, BUTTON_VERTICAL_GAP);
		applyButtonSounds(button);
		return button;
	}

	public static ButtonAPI buttonWithTooltip(TooltipMakerAPI maker, String title, Object id, float width) {
		ButtonAPI button = button(maker, title, id, width);
		maker.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return Math.min(TOOLTIP_MAX_WIDTH, Math.max(TOOLTIP_MIN_WIDTH,
						title.length() * TOOLTIP_WIDTH_PER_CHAR));
			}
			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				tooltip.addPara(title, 0f);
			}
		}, TooltipLocation.RIGHT);
		return button;
	}

	public static ButtonAPI fixedButton(CustomPanelAPI panel, UIRect rect, String title, Object id,
			CutStyle style, boolean prominent) {
		TooltipMakerAPI maker = panel.createUIElement(Math.max(MIN_UI_WIDTH,
				rect.getWidth() - BUTTON_EXTRA_DRAW_WIDTH), rect.getHeight(), false);
		maker.setButtonFontOrbitron20();
		ButtonAPI button = maker.addButton(maker.shortenString(title,
				Math.max(MIN_UI_WIDTH, rect.getWidth() - BUTTON_TEXT_TRUNCATION_PADDING)), id,
				prominent ? BattleNext_UiTheme.ACCENT : BattleNext_UiTheme.TEXT,
				BattleNext_UiTheme.PANEL, Alignment.MID, style, rect.getWidth(), rect.getHeight(), 0f);
		button.getPosition().inTL(0f, 0f);
		applyButtonSounds(button);
		maker.setHeightSoFar(rect.getHeight());
		panel.addUIElement(maker).inTL(rect.getX(), rect.getY());
		return button;
	}

	static void applyButtonSounds(ButtonAPI button) {
		button.setMouseOverSound(BattleNext_UiTheme.BUTTON_HOVER_SOUND_ID);
		button.setButtonPressedSound(BattleNext_UiTheme.BUTTON_PRESSED_SOUND_ID);
	}

	public static float listWidth(CustomPanelAPI panel) {
		return Math.min(LIST_MAX_WIDTH, Math.max(LIST_MIN_WIDTH,
				panel.getPosition().getWidth() * LIST_WIDTH_SHARE));
	}

	public static UIRect[] managementRegions(CustomPanelAPI panel) {
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		float listWidth = Math.min(MANAGEMENT_LIST_MAX_WIDTH,
				Math.max(MANAGEMENT_LIST_MIN_WIDTH, width * MANAGEMENT_LIST_WIDTH_SHARE));
		listWidth = Math.min(listWidth, width * MANAGEMENT_LIST_MAX_SHARE);
		float contentHeight = height - CONTENT_TOP - CONTENT_BOTTOM - FOOTER_MARGIN;
		UIRect list = new UIRect(MANAGEMENT_OUTER_MARGIN, CONTENT_TOP, listWidth, contentHeight);
		UIRect detail = new UIRect(MANAGEMENT_OUTER_MARGIN + MANAGEMENT_COLUMN_GAP + listWidth,
				CONTENT_TOP, width - listWidth - MANAGEMENT_COLUMN_GAP - MANAGEMENT_OUTER_MARGIN * 2f, contentHeight);
		UIRect footer = new UIRect(MANAGEMENT_OUTER_MARGIN, height - CONTENT_BOTTOM + FOOTER_MARGIN,
				width - MANAGEMENT_OUTER_MARGIN * 2f, FOOTER_HEIGHT);
		return new UIRect[]{list, detail, footer};
	}

	public static UIRect managementContentRect(UIRect rect) {
		return rect.shrink(MANAGEMENT_CONTENT_INSET, MANAGEMENT_CONTENT_INSET,
				MANAGEMENT_CONTENT_INSET, MANAGEMENT_CONTENT_INSET + MANAGEMENT_SCROLLBAR_RESERVE);
	}

	public static UIRect managementEditorRect(UIRect rect) {
		return rect.shrink(MANAGEMENT_CONTENT_INSET,
				MANAGEMENT_CONTENT_INSET + MANAGEMENT_EDITOR_FOOTER_RESERVE,
				MANAGEMENT_CONTENT_INSET, MANAGEMENT_CONTENT_INSET);
	}

	public static UIRect managementDeleteButtonLocalRect(UIRect rect) {
		return new UIRect(MANAGEMENT_CONTENT_INSET,
				rect.getHeight() - MANAGEMENT_DELETE_BUTTON_BOTTOM_INSET - MANAGEMENT_DELETE_BUTTON_HEIGHT,
				MANAGEMENT_DELETE_BUTTON_WIDTH, MANAGEMENT_DELETE_BUTTON_HEIGHT);
	}

	public static float managementListButtonWidth(UIRect rect) {
		return Math.max(MANAGEMENT_LIST_BUTTON_MIN_WIDTH,
				rect.getWidth() - MANAGEMENT_LIST_BUTTON_WIDTH_RESERVE);
	}

	public static UIRect managementSourceNoteLocalRect(UIRect rect) {
		return new UIRect(MANAGEMENT_SOURCE_NOTE_LEFT,
				rect.getHeight() - MANAGEMENT_DELETE_BUTTON_HEIGHT - MANAGEMENT_SOURCE_NOTE_BOTTOM_INSET,
				rect.getWidth() - MANAGEMENT_SOURCE_NOTE_WIDTH_RESERVE, MANAGEMENT_DELETE_BUTTON_HEIGHT);
	}

	public static float managementFooterButtonWidth(UIRect footer, int buttonCount) {
		return Math.min(MANAGEMENT_FOOTER_BUTTON_MAX_WIDTH,
				(footer.getWidth() - MANAGEMENT_FOOTER_BUTTON_WIDTH_RESERVE) / buttonCount);
	}

	public static UIRect managementFooterButtonRect(UIRect footer, float buttonWidth, int index) {
		return new UIRect(footer.getX() + index * (buttonWidth + MANAGEMENT_FOOTER_BUTTON_GAP),
				footer.getY(), buttonWidth, FOOTER_HEIGHT);
	}

	public static UIRect managementFooterStatusRect(UIRect footer, float buttonWidth, int buttonCount) {
		float statusOffset = buttonCount * buttonWidth + MANAGEMENT_FOOTER_STATUS_OFFSET;
		return new UIRect(footer.getX() + statusOffset, footer.getY(),
				footer.getWidth() - statusOffset, FOOTER_HEIGHT);
	}
}
