package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import data.scripts.battlesnext.util.ui.UIPanelManager;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.widget.BattleNext_TextField;

import java.awt.Color;

public class BattleNext_PageLayout {

	public static final float BUTTON_VERTICAL_GAP = 5f;
	public static final float HEADER_HEIGHT = 30f;
	public static final float CONTENT_TOP = 40f;
	public static final float FOOTER_HEIGHT = 30f;
	public static final float CONTENT_BOTTOM = 48f;
	public static final float FOOTER_MARGIN = 8f;
	public static final float PAGE_CONTENT_INSET = 8f;

	public static final float MANAGEMENT_CONTENT_INSET = PAGE_CONTENT_INSET;
	public static final float MANAGEMENT_REGION_BORDER_THICKNESS = 2f;
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
	public static final float EDITOR_FIELD_INSET = 6f;
	public static final float EDITOR_COLUMN_GAP = 10f;
	public static final float EDITOR_LEFT_COLUMN_SHARE = 0.45f;
	public static final float EDITOR_LABEL_GAP = 5f;
	public static final float EDITOR_READ_ONLY_VALUE_GAP = 2f;
	public static final float EDITOR_TABLE_TITLE_HEIGHT = 24f;
	public static final float TABLE_ACTION_SIDE_INSET = 4f;

	public static final float TABLE_HEADER_HEIGHT = 18f;
	public static final float TABLE_ROW_HEIGHT = 38f;
	public static final float TABLE_COLUMN_GAP = 8f;
	public static final float TABLE_ROW_BOTTOM_PADDING = 10f;
	public static final float TABLE_ROW_BORDER_THICKNESS = 2f;
	public static final float SECTION_HEADING_EXTRA_WIDTH = 10f;
	public static final float SCROLLBAR_RESERVED_WIDTH = 5f;
	public static final float CUSTOM_COMPONENT_LEFT_INSET = 5f;
	public static final float TABLE_CHECKBOX_MAX_WIDTH = 22f;
	public static final float TABLE_ACTION_BUTTON_HEIGHT = 30f;
	public static final float EDITOR_BUTTON_WIDTH = 190f;
	public static final float EDITOR_BUTTON_HEIGHT = 30f;
	public static final float EDITOR_BUTTON_GAP = 4f;

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

	private static final float MANAGEMENT_OUTER_MARGIN = PAGE_CONTENT_INSET;
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

	public static void renderTintedFittedSprite(SpriteAPI sprite, float centerX, float centerY,
			float availableWidth, float availableHeight, Color tint, float alpha) {
		Color previousColor = sprite.getColor();
		try {
			sprite.setColor(tint);
			renderFittedSprite(sprite, centerX, centerY, availableWidth, availableHeight, alpha);
		} finally {
			sprite.setColor(previousColor);
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

	public static LabelAPI heading(UIRect rect, CustomPanelAPI panel, String title) {
		return heading(rect, panel, title, BattleNext_UiTheme.PANEL);
	}

	public static LabelAPI heading(UIRect rect, CustomPanelAPI panel, String title, Color backgroundColor) {
		TooltipMakerAPI maker = panel.createUIElement(Math.max(MIN_UI_WIDTH, rect.getWidth()), rect.getHeight(), false);
		return heading(rect, panel, maker, title, backgroundColor);
	}

	public static LabelAPI heading(UIRect rect, CustomPanelAPI panel, TooltipMakerAPI maker, String title) {
		return heading(rect, panel, maker, title, BattleNext_UiTheme.PANEL);
	}

	private static LabelAPI heading(UIRect rect, CustomPanelAPI panel, TooltipMakerAPI maker, String title, Color backgroundColor) {
		LabelAPI label = maker.addSectionHeading(maker.shortenString(title,
				rect.getWidth() - PAGE_TITLE_TRUNCATION_PADDING), BattleNext_UiTheme.TEXT,
				backgroundColor, Alignment.MID, 0f);
		label.getPosition().inTL(0f, 0f);
		maker.setHeightSoFar(rect.getHeight());
		panel.addUIElement(maker).inTL(rect.getX(), rect.getY());
		return label;
	}

	public static TooltipMakerAPI column(UIRect rect, CustomPanelAPI panel) {
		rect.removeLatestSubPanel();
		UIPanelManager manager = rect.createSubPanel(panel, null);
		TooltipMakerAPI maker = manager.getPanel().createUIElement(rect.getWidth(), rect.getHeight(), true);
		BattleNext_UiTheme.configure(maker);
		manager.getPanel().addUIElement(maker).inTL(0f, 0f);
		return maker;
	}

	public static UIRect pageContentRect(UIRect area) {
		return area.shrink(0f, 0f, PAGE_CONTENT_INSET, PAGE_CONTENT_INSET);
	}

	public static LabelAPI contentTitle(TooltipMakerAPI content, String title, float areaWidth, float inset) {
		LabelAPI label = content.addTitle(title);
		float previousHeight = label.getPosition().getHeight();
		float top = content.getHeightSoFar() - previousHeight;
		label.autoSizeToWidth(Math.max(MIN_UI_WIDTH, areaWidth - inset * 2f));
		label.getPosition().inTL(inset, top);
		content.setHeightSoFar(top + label.getPosition().getHeight());
		return label;
	}

	public static void refreshHeading(TooltipMakerAPI maker, LabelAPI label, float areaWidth, String title) {
		label.setText(maker.shortenString(title, areaWidth - PAGE_TITLE_TRUNCATION_PADDING));
	}

	public static void refreshButton(TooltipMakerAPI maker, ButtonAPI button, String title) {
		button.setText(maker.shortenString(title,
				Math.max(MIN_UI_WIDTH, button.getPosition().getWidth() - BUTTON_TEXT_TRUNCATION_PADDING)));
	}

	public static ButtonAPI button(TooltipMakerAPI maker, String title, Object id, float width) {
		ButtonAPI button = maker.addButton(maker.shortenString(title,
				Math.max(MIN_UI_WIDTH, width - BUTTON_TEXT_TRUNCATION_PADDING)), id, BattleNext_UiTheme.ACCENT,
				BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.ALL, width, FOOTER_HEIGHT, BUTTON_VERTICAL_GAP);
		applyButtonSounds(button);
		return button;
	}

	public static ButtonAPI editorButton(TooltipMakerAPI fieldContent, String title, Object id, float areaWidth) {
		float buttonWidth = editorButtonWidth(areaWidth);
		ButtonAPI button = fieldContent.addButton(fieldContent.shortenString(title,
				Math.max(MIN_UI_WIDTH, buttonWidth - BUTTON_TEXT_TRUNCATION_PADDING)), id,
				BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, Alignment.MID, CutStyle.ALL,
				buttonWidth, EDITOR_BUTTON_HEIGHT, EDITOR_BUTTON_GAP);
		applyButtonSounds(button);
		return button;
	}

	public static float editorButtonWidth(float areaWidth) {
		return Math.min(EDITOR_BUTTON_WIDTH, areaWidth);
	}

	public static BattleNext_TextField editorTextField(BattleNext_UiInputRouter router, CustomPanelAPI hostPanel,
			TooltipMakerAPI fieldContent, UIRect fieldArea, String value, TooltipMakerAPI... viewports) {
		BattleNext_TextField field = new BattleNext_TextField(router, hostPanel,
				fieldArea.getWidth(), FORM_FIELD_HEIGHT, viewports);
		field.attach(fieldContent, hostPanel);
		field.setText(value);
		field.setMaxChars(-1);
		field.getPosition().inTL(fieldArea.getX(), fieldContent.getHeightSoFar() - FORM_FIELD_HEIGHT);
		return field;
	}

	public static float editorFieldWidth(float areaWidth) {
		return Math.max(MIN_UI_WIDTH, areaWidth - editorFieldInset(areaWidth) * 2f);
	}

	public static BattleNext_TextField cellTextField(BattleNext_UiInputRouter router, CustomPanelAPI hostPanel,
			UIRect cell, String value, boolean editable, TooltipMakerAPI... viewports) {
		UIRect bounds = editorFieldCell(cell);
		TooltipMakerAPI content = hostPanel.createUIElement(bounds.getWidth(), bounds.getHeight(), false);
		BattleNext_TextField field = new BattleNext_TextField(router, hostPanel, bounds.getWidth(), bounds.getHeight(), viewports);
		field.attach(content, hostPanel);
		field.setEditable(editable);
		field.setText(value);
		field.getPosition().inTL(0f, 0f);
		content.setHeightSoFar(bounds.getHeight());
		hostPanel.addUIElement(content).inTL(bounds.getX(), bounds.getY());
		return field;
	}

	public static LabelAPI cellLabel(CustomPanelAPI hostPanel, UIRect cell, String text, boolean compact,
			TooltipMakerAPI.TooltipCreator tooltip, TooltipLocation location, boolean recreate) {

		UIRect bounds = editorLabelCell(cell);
		float cellWidth = bounds.getWidth();
		TooltipMakerAPI content = hostPanel.createUIElement(cellWidth, bounds.getHeight(), false);
		content.setTextWidthOverride(cellWidth);
		if (compact) {
			content.setParaSmallInsignia();
		}
		LabelAPI label = content.addPara(content.shortenString(text,
				Math.max(1f, cell.getWidth() - CELL_LABEL_TRUNCATION_PADDING)), 0f);
		label.setAlignment(Alignment.MID);
		label.getPosition().setSize(cellWidth, label.getPosition().getHeight());
		label.getPosition().inTL(0f, Math.max(0f, (cell.getHeight() - label.getPosition().getHeight()) * 0.5f));
		if (tooltip != null) {
			content.addTooltipTo(tooltip, content.getPrev(), location, recreate);
		} else {
			addMetadataTooltip(content, text);
		}
		content.setHeightSoFar(cell.getHeight());
		hostPanel.addUIElement(content).inTL(bounds.getX(), bounds.getY());
		return label;
	}

	public static float editorFieldInset(float areaWidth) {
		return Math.min(EDITOR_FIELD_INSET, Math.max(0f, (areaWidth - MIN_UI_WIDTH) * 0.5f));
	}

	public static UIRect editorFieldArea(float areaWidth) {
		float inset = editorFieldInset(areaWidth);
		float fieldWidth = editorFieldWidth(areaWidth);
		return new UIRect(inset, 0f, fieldWidth, FORM_FIELD_HEIGHT);
	}

	public static UIRect[] editorColumns(float areaWidth, float areaHeight) {
		float availableWidth = Math.max(MIN_UI_WIDTH * 2f, areaWidth - EDITOR_COLUMN_GAP);
		float columnWidth = (float) Math.floor(availableWidth * EDITOR_LEFT_COLUMN_SHARE);
		return new UIRect[]{new UIRect(0f, 0f, columnWidth, areaHeight),
				new UIRect(columnWidth + EDITOR_COLUMN_GAP, 0f, availableWidth - columnWidth, areaHeight)};
	}

	public static UIRect editorFieldCell(UIRect cell) {
		return new UIRect(cell.getX() + editorFieldInset(cell.getWidth()),
				cell.getY() + (cell.getHeight() - FORM_FIELD_HEIGHT) * 0.5f,
				editorFieldWidth(cell.getWidth()), FORM_FIELD_HEIGHT);
	}

	public static UIRect editorLabelCell(UIRect cell) {
		return new UIRect(cell.getX() + CELL_LABEL_SIDE_INSET, cell.getY(),
				Math.max(MIN_UI_WIDTH, cell.getWidth() - CELL_LABEL_SIDE_INSET * 2f), cell.getHeight());
	}

	public static UIRect editorCheckboxCell(UIRect cell) {
		float fieldWidth = Math.min(TABLE_CHECKBOX_MAX_WIDTH, cell.getWidth());
		return new UIRect(cell.getX() + (cell.getWidth() - fieldWidth) * 0.5f,
				cell.getY(), fieldWidth, cell.getHeight());
	}

	public static UIRect[] editorActions(UIRect cell, int actionCount) {
		float buttonWidth = Math.max(MIN_UI_WIDTH, cell.getWidth() - TABLE_ACTION_SIDE_INSET * 2f) / actionCount;
		float left = cell.getX() + (cell.getWidth() - buttonWidth * actionCount) * 0.5f;
		UIRect[] actions = new UIRect[actionCount];
		for (int index = 0; index < actionCount; index++) {
			actions[index] = new UIRect(left + buttonWidth * index,
					cell.getY() + (cell.getHeight() - TABLE_ACTION_BUTTON_HEIGHT) * 0.5f,
					buttonWidth, TABLE_ACTION_BUTTON_HEIGHT);
		}
		return actions;
	}

	public static ButtonAPI listButton(TooltipMakerAPI maker, String title, Object id, float width,
			TooltipMakerAPI.TooltipCreator tooltip) {
		ButtonAPI button = button(maker, title, id, width);
		button.getPosition().inTL(0f, maker.getHeightSoFar() - FOOTER_HEIGHT);
		maker.addTooltipToPrevious(tooltip == null ? new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return Math.min(TOOLTIP_MAX_WIDTH, Math.max(TOOLTIP_MIN_WIDTH,
						title.length() * TOOLTIP_WIDTH_PER_CHAR));
			}
			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				tooltip.addPara(title, 0f);
			}
		} : tooltip, TooltipLocation.RIGHT);
		return button;
	}

	public static void addIdTooltip(TooltipMakerAPI host, String title, String id) {
		addMetadataTooltip(host, title, id);
	}

	public static void addMetadataTooltip(TooltipMakerAPI host, String title, String... lines) {
		host.addTooltipToPrevious(new BaseTooltipCreator() {
			@Override
			public float getTooltipWidth(Object ignored) {
				return 320f;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				tooltip.addTitle(title);
				for (String line : lines) {
					addMutedIdLine(tooltip, line, 5f);
				}
			}
		}, TooltipLocation.RIGHT);
	}

	public static void addMutedIdLine(TooltipMakerAPI tooltip, String value, float pad) {
		tooltip.setParaSmallInsignia();
		tooltip.addPara(value, BattleNext_UiTheme.MUTED, pad);
		tooltip.setParaFontDefault();
	}

	public static ButtonAPI fixedButton(CustomPanelAPI panel, UIRect rect, String title, Object id,
			CutStyle style, boolean prominent) {
		return fixedButton(panel, rect, title, id, style,
				prominent ? BattleNext_UiTheme.ACCENT : BattleNext_UiTheme.TEXT);
	}

	public static ButtonAPI fixedButton(CustomPanelAPI panel, UIRect rect, String title, Object id,
			CutStyle style, Color textColor) {
		return fixedButton(panel, rect, title, id, style, textColor, null);
	}

	public static ButtonAPI fixedButton(CustomPanelAPI panel, UIRect rect, String title, Object id,
			CutStyle style, Color textColor, TooltipMakerAPI.TooltipCreator tooltip) {
		TooltipMakerAPI maker = panel.createUIElement(Math.max(MIN_UI_WIDTH,
				rect.getWidth() - BUTTON_EXTRA_DRAW_WIDTH), rect.getHeight(), false);
		maker.setButtonFontOrbitron20();
		ButtonAPI button = maker.addButton(maker.shortenString(title,
				Math.max(MIN_UI_WIDTH, rect.getWidth() - BUTTON_TEXT_TRUNCATION_PADDING)), id,
				textColor,
				BattleNext_UiTheme.PANEL, Alignment.MID, style, rect.getWidth(), rect.getHeight(), 0f);
		button.getPosition().inTL(0f, 0f);
		applyButtonSounds(button);
		if (tooltip != null) {
			maker.addTooltipTo(tooltip, button, TooltipLocation.RIGHT, true);
		}
		maker.setHeightSoFar(rect.getHeight());
		panel.addUIElement(maker).inTL(rect.getX(), rect.getY());
		return button;
	}

	static void applyButtonSounds(ButtonAPI button) {
		button.setMouseOverSound(BattleNext_UiTheme.BUTTON_HOVER_SOUND_ID);
		button.setButtonPressedSound(BattleNext_UiTheme.BUTTON_PRESSED_SOUND_ID);
	}

	public static UIRect[] managementRegions(CustomPanelAPI panel) {
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		float listWidth = Math.min(MANAGEMENT_LIST_MAX_WIDTH,
				Math.max(MANAGEMENT_LIST_MIN_WIDTH, width * MANAGEMENT_LIST_WIDTH_SHARE));
		listWidth = (float) Math.floor(Math.min(listWidth, width * MANAGEMENT_LIST_MAX_SHARE));
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
				MANAGEMENT_CONTENT_INSET, MANAGEMENT_CONTENT_INSET);
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
