package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.tools.BattleNext_GeometryRenderer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public class BattleNext_OptionPicker extends BattleNext_TooltipBasedWidget {

	public enum OptionSelectionMode {
		SINGLE, MULTIPLE
	}

	public static class OptionChoice {

		private final String id;
		private final String label;

		public OptionChoice(String id, String label) {
			this.id = id;
			this.label = label;
		}

		public String getId() {
			return id;
		}

		public String getLabel() {
			return label;
		}
	}

	public interface OptionChoiceSource {

		List<OptionChoice> getOptions();

		int getMaxCount(String id);

		String getIconPath(String id);

		default boolean expectsIcon() {
			return false;
		}

		default float getTooltipWidth() {
			return HOVER_MAX_WIDTH;
		}

		default boolean isTooltipScrollable() {
			return false;
		}

		void createTooltip(TooltipMakerAPI tooltip, OptionChoice option);
	}

	private enum OptionPickerAction {
		TOGGLE, CONFIRM, CANCEL
	}

	private static final float SEARCH_HEIGHT = 32f;
	private static final float TILE_SIZE = 64f;
	private static final float TILE_GAP = 8f;
	private static final float POPUP_INSET = 8f;
	private static final float POPUP_MAX_WIDTH = 540f;
	private static final float POPUP_MAX_HEIGHT = 420f;
	private static final float POPUP_ROW_GAP = 2f;
	private static final float POPUP_BORDER_THICKNESS = 2f;
	private static final float POPUP_ACTION_MAX_WIDTH = 120f;
	private static final float POPUP_ACTION_BUTTON_HEIGHT = 30f;
	private static final float POPUP_ACTION_BOTTOM_RESERVE = 38f;
	private static final float POPUP_GRID_TOP = 44f;
	private static final float POPUP_GRID_HEIGHT_RESERVE = 90f;
	private static final float SEARCH_LABEL_TOP_INSET = 6f;
	private static final float SEARCH_LABEL_WIDTH = 80f;
	private static final float SEARCH_FIELD_WIDTH_RESERVE = 96f;
	private static final float TILE_CONTENT_INSET = 2f;
	private static final float TILE_ICON_SIZE = 60f;
	private static final float TILE_LABEL_TRUNCATION_WIDTH = 54f;
	private static final int TILE_LABEL_MAX_CHARACTERS = 3;
	private static final float TILE_SELECTION_OUTLINE_INSET = 1f;
	private static final float TILE_CHECK_START_X = 46f;
	private static final float TILE_CHECK_START_Y = 51f;
	private static final float TILE_CHECK_MIDDLE_X = 51f;
	private static final float TILE_CHECK_MIDDLE_Y = 46f;
	private static final float TILE_CHECK_END_X = 60f;
	private static final float TILE_CHECK_END_Y = 59f;
	private static final float TILE_COUNT_PIP_RIGHT_INSET = 10f;
	private static final float TILE_COUNT_PIP_TOP_INSET = 4f;
	private static final float TILE_COUNT_PIP_SPACING = 7f;
	private static final float TILE_COUNT_PIP_SIZE = 5f;
	private static final float HOVER_MAX_WIDTH = 480f;
	private static final float HOVER_MAX_HEIGHT = 240f;
	private static final float HOVER_CONTENT_INSET = 6f;
	private static final float HOVER_SCROLL_STEP = 40f;
	private static final Color POPUP_BACKGROUND = new Color(8, 14, 17, 248);
	private static final Color BORDER_COLOR = BattleNext_UiTheme.PANEL;
	private static final Color TILE_SELECTED = new Color(32, 77, 91, 235);
	private static final Color TILE_NORMAL = new Color(20, 48, 57, 210);
	private static final Color HOVER_BACKGROUND = new Color(8, 14, 17, 250);
	private static final Color CHECK_COLOR = new Color(0.5f, 0.9f, 1f, 1f);
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final OptionChoiceSource source;
	private final OptionSelectionMode mode;
	private final Supplier<List<String>> initialSelection;
	private final Consumer<List<String>> onConfirmed;
	private final CustomPanelAPI overlayHost;
	private final float buttonWidth;
	private final float buttonHeight;
	private final String title;
	private final float topGap;
	private final TooltipMakerAPI[] viewports;
	private boolean enabled = true;
	private final Map<String, Integer> selection = new LinkedHashMap<>();

	@Override
	protected void buildContent(TooltipMakerAPI host, CustomPanelAPI panelOwner) {
		row = ownPanel(host, panelOwner.createCustomPanel(buttonWidth,
				buttonHeight, new OptionPickerPanelPlugin()));
		toggle = BattleNext_PageLayout.fixedButton(row,
				new UIRect(0f, 0f, buttonWidth, buttonHeight), title,
				OptionPickerAction.TOGGLE, CutStyle.ALL, false);
		host.addCustom(row, topGap);
		row.getPosition().inTL(0f, host.getHeightSoFar() - buttonHeight);
	}

	private List<OptionChoice> options = List.of();
	private List<String> previousIds = List.of();
	private CustomPanelAPI row;
	private ButtonAPI toggle;
	private CustomPanelAPI popup;
	private PopupOverlay popupOverlay;
	private CustomPanelAPI gridPanel;
	private ScrollPanelAPI gridScroller;
	private CustomPanelAPI hoverPanel;
	private TooltipMakerAPI hoverContent;
	private BattleNext_TextField search;
	private String lastSearch = "";
	private OptionChoice hoveredOption;
	private final List<OptionTileHoverTarget> hoverTargets = new ArrayList<>();

	public BattleNext_OptionPicker(BattleNext_UiInputRouter inputRouter, CustomPanelAPI overlayHost,
			float buttonWidth, float buttonHeight, String title, float topGap, OptionChoiceSource source,
			OptionSelectionMode mode, Supplier<List<String>> initialSelection,
			Consumer<List<String>> onConfirmed, TooltipMakerAPI... viewports) {
		super(inputRouter);
		this.source = source;
		this.mode = mode;
		this.initialSelection = initialSelection;
		this.onConfirmed = onConfirmed;
		this.overlayHost = overlayHost;
		this.buttonWidth = buttonWidth;
		this.buttonHeight = buttonHeight;
		this.title = title;
		this.topGap = topGap;
		this.viewports = viewports;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		if (toggle != null) toggle.setEnabled(enabled);
		if (!enabled) clearOverlays();
	}

	@Override
	protected void clearOverlays() {
		if (search != null) search.close();
		removeHover();
		if (gridPanel != null) releasePanel(gridPanel);
		if (popup != null) {
			if (popupOverlay != null) inputRouter.pop(popupOverlay);
		}
		if (popup != null) releasePanel(popup);
		popup = null;
		popupOverlay = null;
		gridPanel = null;
		gridScroller = null;
		hoverPanel = null;
		search = null;
		hoveredOption = null;
		options = List.of();
		selection.clear();
		hoverTargets.clear();
	}

	@Override
	protected void clearContent() {
		clearOverlays();
		row = null;
		toggle = null;
	}

	@Override
	protected boolean handleWidgetInput(List<InputEventAPI> events) {

		if (popup == null || !inputRouter.isTop(popupOverlay)) return false;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent()
					&& event.getEventValue() == Keyboard.KEY_ESCAPE) {
				clearOverlays();
				event.consume();
				return true;
			}
			if (event.isMouseScrollEvent() && hoverContent != null && source.isTooltipScrollable()
					&& contains(hoverPanel, event)) {
				ScrollPanelAPI scroller = hoverContent.getExternalScroller();
				float limit = Math.max(0f, hoverContent.getHeightSoFar() - scroller.getPosition().getHeight());
				float offset = scroller.getYOffset() - Math.signum(event.getEventValue()) * HOVER_SCROLL_STEP;
				scroller.setYOffset(Math.max(0f, Math.min(limit, offset)));
				event.consume();
				continue;
			}
			if (!event.isMouseDownEvent() && !event.isMouseScrollEvent()) continue;
			if (event.isMouseScrollEvent() && contains(row, event) && !contains(popup, event)) {
				clearOverlays();
				event.consume();
				continue;
			}
			if (event.isMouseDownEvent() && !contains(popup, event) && !contains(toggle, event)) {
				clearOverlays();
				event.consume();
				return true;
			}
		}
		return false;
	}

	private void open() {

		if (popup != null) return;
		inputRouter.releaseFocus();
		options = new ArrayList<>(source.getOptions());
		Set<String> known = new HashSet<>();
		for (OptionChoice option : options) {
			known.add(option.id);
		}
		previousIds = initialSelection == null ? List.of() : new ArrayList<>(initialSelection.get());
		selection.clear();
		for (Map.Entry<String, Integer> entry : parse(previousIds).entrySet()) {
			if (known.contains(entry.getKey())) selection.put(entry.getKey(), entry.getValue());
		}

		float hostWidth = overlayHost.getPosition().getWidth();
		float hostHeight = overlayHost.getPosition().getHeight();
		float width = Math.min(POPUP_MAX_WIDTH, hostWidth - POPUP_INSET * 2f);
		PositionAPI anchor = toggle.getPosition();
		float rowTop = overlayHost.getPosition().getY() + hostHeight
				- anchor.getY() - anchor.getHeight();
		float belowY = rowTop + anchor.getHeight() + POPUP_ROW_GAP;
		float belowSpace = hostHeight - belowY - POPUP_INSET;
		float aboveSpace = rowTop - POPUP_INSET - POPUP_ROW_GAP;
		boolean above = belowSpace < POPUP_MAX_HEIGHT && aboveSpace > belowSpace;
		float height = Math.min(POPUP_MAX_HEIGHT, above ? aboveSpace : belowSpace);
		float y = above ? rowTop - height - POPUP_ROW_GAP : belowY;
		float x = Math.max(POPUP_INSET, Math.min(anchor.getX() - overlayHost.getPosition().getX(),
				hostWidth - width - POPUP_INSET));

		popup = ownPanel(overlayHost, panelOwner.createCustomPanel(width, height, new OptionPickerPopupPlugin()));
		new UIRect(0f, 0f, width, height).addBorder(popup, BORDER_COLOR, POPUP_BORDER_THICKNESS);
		popup.addUIElement(searchElement(width)).inTL(POPUP_INSET, POPUP_INSET);
		float buttonWidth = Math.min(POPUP_ACTION_MAX_WIDTH, (width - 3f * POPUP_INSET) * 0.5f);
		float buttonY = height - POPUP_ACTION_BOTTOM_RESERVE;
		BattleNext_PageLayout.fixedButton(popup,
				new UIRect(width - 2f * buttonWidth - 2f * POPUP_INSET, buttonY,
						buttonWidth, POPUP_ACTION_BUTTON_HEIGHT),
				strings.get("confirm"), OptionPickerAction.CONFIRM, CutStyle.ALL, false);
		BattleNext_PageLayout.fixedButton(popup,
				new UIRect(width - buttonWidth - POPUP_INSET, buttonY,
						buttonWidth, POPUP_ACTION_BUTTON_HEIGHT),
				strings.get("cancel"), OptionPickerAction.CANCEL, CutStyle.ALL, false);
		overlayHost.addComponent(popup).inTL(x, y);
		overlayHost.bringComponentToTop(popup);
		popupOverlay = new PopupOverlay(popup);
		inputRouter.push(popupOverlay);
		inputRouter.registerModalField(search);
		lastSearch = "";
		rebuildGrid();
	}

	private final class PopupOverlay implements BattleNext_UiInputRouter.Overlay {
		private final CustomPanelAPI panel;

		private PopupOverlay(CustomPanelAPI panel) {
			this.panel = panel;
		}

		@Override
		public PositionAPI getBounds() {
			return panel.getPosition();
		}

		@Override
		public boolean handleGlobalInput(List<InputEventAPI> events) {
			return BattleNext_OptionPicker.this.handleWidgetInput(events);
		}

		@Override
		public void close() {
			if (popup != null) BattleNext_OptionPicker.this.clearOverlays();
		}
	}

	private TooltipMakerAPI searchElement(float width) {

		TooltipMakerAPI searchBar = popup.createUIElement(width - 2f * POPUP_INSET, SEARCH_HEIGHT, false);
		searchBar.addPara(strings.get("picker_search"), 0f)
				.getPosition().inTL(0f, SEARCH_LABEL_TOP_INSET);
		TooltipMakerAPI fieldHost = popup.createUIElement(width - SEARCH_FIELD_WIDTH_RESERVE, SEARCH_HEIGHT, false);
		BattleNext_TextField field = new BattleNext_TextField(inputRouter, popup,
				width - SEARCH_FIELD_WIDTH_RESERVE, BattleNext_PageLayout.FORM_FIELD_HEIGHT, fieldHost);
		field.attach(fieldHost, popup);
		search = field;
		search.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		fieldHost.setHeightSoFar(BattleNext_PageLayout.FORM_FIELD_HEIGHT);
		popup.addUIElement(fieldHost).inTL(POPUP_INSET + SEARCH_LABEL_WIDTH,
				POPUP_INSET + (SEARCH_HEIGHT - BattleNext_PageLayout.FORM_FIELD_HEIGHT) * 0.5f);
		searchBar.setHeightSoFar(SEARCH_HEIGHT);
		return searchBar;
	}

	private void rebuildGrid() {

		removeHover();
		hoverTargets.clear();
		if (gridPanel != null) releasePanel(gridPanel);
		gridScroller = null;
		float width = popup.getPosition().getWidth() - 2f * POPUP_INSET;
		float height = popup.getPosition().getHeight() - POPUP_GRID_HEIGHT_RESERVE;
		gridPanel = ownPanel(popup, popup.createCustomPanel(width, height, new BaseCustomUIPanelPlugin()));
		TooltipMakerAPI grid = gridPanel.createUIElement(width, height, true);
		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		List<OptionChoice> visible = new ArrayList<>();
		for (OptionChoice option : options) {
			if (query.isEmpty() || option.id.toLowerCase(Locale.ROOT).contains(query)
					|| option.label.toLowerCase(Locale.ROOT).contains(query)) {
				visible.add(option);
			}
		}
		if (visible.isEmpty()) {
			grid.addPara(strings.get("picker_no_options"), POPUP_INSET);
		} else {
			int columns = Math.max(1, (int) ((width - 2f * POPUP_INSET + TILE_GAP) / (TILE_SIZE + TILE_GAP)));
			for (int index = 0; index < visible.size(); index++) {
				OptionChoice option = visible.get(index);
				OptionPickerTilePlugin tilePlugin = new OptionPickerTilePlugin(option);
				CustomPanelAPI tile = gridPanel.createCustomPanel(TILE_SIZE, TILE_SIZE, tilePlugin);
				addIcon(tile, option, tilePlugin);
				grid.addCustomDoNotSetPosition(tile).getPosition().inTL(POPUP_INSET + index % columns * (TILE_SIZE + TILE_GAP),
						POPUP_INSET + index / columns * (TILE_SIZE + TILE_GAP));
				hoverTargets.add(new OptionTileHoverTarget(option, tile));
			}
			int rows = (visible.size() + columns - 1) / columns;
			grid.setHeightSoFar(2f * POPUP_INSET + rows * (TILE_SIZE + TILE_GAP));
		}
		gridPanel.addUIElement(grid).inTL(0f, 0f);
		gridScroller = grid.getExternalScroller();
		popup.addComponent(gridPanel).inTL(POPUP_INSET, POPUP_GRID_TOP);
	}

	private void addIcon(CustomPanelAPI tile, OptionChoice option, OptionPickerTilePlugin tilePlugin) {

		String icon = source.getIconPath(option.id);
		if (icon != null && !icon.isBlank()) {
			try {
				Global.getSettings().getSprite(icon);
				TooltipMakerAPI image = tile.createUIElement(TILE_ICON_SIZE, TILE_ICON_SIZE, false);
				image.addImage(icon, TILE_ICON_SIZE, TILE_ICON_SIZE, 0f);
				image.getPrev().getPosition().inTL(0f, 0f);
				tile.addUIElement(image).inTL(TILE_CONTENT_INSET, TILE_CONTENT_INSET);
				return;
			} catch (RuntimeException exception) {
				// A missing optional sprite leaves the option selectable.
			}
		}
		if (source.expectsIcon()) {
			tilePlugin.missingIcon = true;
			return;
		}
		TooltipMakerAPI fallback = tile.createUIElement(TILE_ICON_SIZE, TILE_ICON_SIZE, false);
		fallback.setParaSmallInsignia();
		String text = tileLabel(option.label, fallback::computeStringWidth);
		LabelAPI label = fallback.addPara(text, 0f);
		label.setAlignment(Alignment.MID);
		float labelHeight = label.getPosition().getHeight();
		label.getPosition().setSize(TILE_ICON_SIZE, labelHeight);
		label.getPosition().inTL(0f, (TILE_ICON_SIZE - labelHeight) * 0.5f);
		tile.addUIElement(fallback).inTL(TILE_CONTENT_INSET, TILE_CONTENT_INSET);
	}

	static String tileLabel(String value, ToDoubleFunction<String> widthOf) {
		String normalized = value.strip().replaceAll("\\s+", " ");
		int characters = Math.min(TILE_LABEL_MAX_CHARACTERS, normalized.codePointCount(0, normalized.length()));
		String result = normalized.substring(0, normalized.offsetByCodePoints(0, characters));
		while (!result.isEmpty() && widthOf.applyAsDouble(result) > TILE_LABEL_TRUNCATION_WIDTH) {
			result = result.substring(0, result.offsetByCodePoints(result.length(), -1));
		}
		return result;
	}

	private void confirm() {

		Map<String, Integer> previous = parse(previousIds);
		Set<String> known = new HashSet<>();
		for (OptionChoice option : options) {
			known.add(option.id);
		}
		if (mode == OptionSelectionMode.SINGLE) {
			List<String> selected = new ArrayList<>();
			if (!selection.isEmpty()) {
				selected.add(selection.keySet().iterator().next());
			} else if (!previous.isEmpty() && !known.contains(previous.keySet().iterator().next())) {
				selected.add(previous.keySet().iterator().next());
			}
			if (onConfirmed != null) onConfirmed.accept(selected);
			clearOverlays();
			return;
		}
		Map<String, Integer> result = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : previous.entrySet()) {
			if (!known.contains(entry.getKey())) {
				result.put(entry.getKey(), entry.getValue());
			} else if (selection.containsKey(entry.getKey())) {
				result.put(entry.getKey(), selection.get(entry.getKey()));
			}
		}
		for (OptionChoice option : options) {
			if (selection.containsKey(option.id) && !result.containsKey(option.id)) {
				result.put(option.id, selection.get(option.id));
			}
		}
		if (onConfirmed != null) onConfirmed.accept(expandList(result));
		clearOverlays();
	}

	private Map<String, Integer> parse(List<String> values) {

		Map<String, Integer> counts = new LinkedHashMap<>();
		if (values == null) return counts;
		if (mode == OptionSelectionMode.SINGLE) {
			if (!values.isEmpty() && values.get(0) != null && !values.get(0).isBlank()) {
				counts.put(values.get(0).trim(), 1);
			}
			return counts;
		}
		for (String value : values) {
			if (value == null) continue;
			String id = value.trim();
			if (id.isEmpty()) continue;
			counts.put(id, Math.min(counts.getOrDefault(id, 0) + 1, maxCount(id)));
		}
		return counts;
	}

	private List<String> expandList(Map<String, Integer> counts) {
		List<String> result = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			for (int index = 0; index < entry.getValue(); index++) result.add(entry.getKey());
		}
		return result;
	}

	private int maxCount(String id) {
		return Math.max(1, source.getMaxCount(id));
	}

	private boolean contains(UIComponentAPI component, InputEventAPI event) {
		return component != null && component.getPosition().containsEvent(event);
	}

	private boolean contains(PositionAPI position, float x, float y) {
		return position != null && x >= position.getX() && x < position.getX() + position.getWidth()
				&& y >= position.getY() && y < position.getY() + position.getHeight();
	}

	private boolean isInsideViewports(InputEventAPI event) {
		for (TooltipMakerAPI viewport : viewports) {
			ScrollPanelAPI scroller = viewport.getExternalScroller();
			PositionAPI position = scroller == null ? viewport.getPosition() : scroller.getPosition();
			if (!position.containsEvent(event)) return false;
		}
		return true;
	}

	private boolean isRowVisible() {
		if (row == null) return false;
		PositionAPI rowPosition = row.getPosition();
		for (TooltipMakerAPI host : viewports) {
			ScrollPanelAPI scroller = host.getExternalScroller();
			PositionAPI viewport = scroller == null ? host.getPosition() : scroller.getPosition();
			if (rowPosition.getX() + rowPosition.getWidth() <= viewport.getX()
					|| viewport.getX() + viewport.getWidth() <= rowPosition.getX()
					|| rowPosition.getY() + rowPosition.getHeight() <= viewport.getY()
					|| viewport.getY() + viewport.getHeight() <= rowPosition.getY()) return false;
		}
		return true;
	}

	private void updateHover(float x, float y) {

		if (popup == null || !contains(popup.getPosition(), x, y)) {
			removeHover();
			return;
		}
		if (hoverPanel != null) {
			PositionAPI hover = hoverPanel.getPosition();
			if (contains(hover, x, y)) return;
			for (OptionTileHoverTarget candidate : hoverTargets) {
				if (candidate.option != hoveredOption) continue;
				PositionAPI tile = candidate.tile.getPosition();
				float left = hover.getX() >= tile.getX() + tile.getWidth()
						? tile.getX() + tile.getWidth() : hover.getX() + hover.getWidth();
				float right = hover.getX() >= tile.getX() + tile.getWidth()
						? hover.getX() : tile.getX();
				float bottom = Math.max(tile.getY(), hover.getY());
				float top = Math.min(tile.getY() + tile.getHeight(), hover.getY() + hover.getHeight());
				if (x >= left && x <= right && y >= bottom && y <= top) return;
			}
		}
		PositionAPI gridViewport = gridScroller == null
				? gridPanel == null ? null : gridPanel.getPosition() : gridScroller.getPosition();
		if (!contains(gridViewport, x, y)) {
			removeHover();
			return;
		}
		OptionTileHoverTarget target = null;
		for (OptionTileHoverTarget candidate : hoverTargets) {
			PositionAPI position = candidate.tile.getPosition();
			if (contains(position, x, y)) {
				target = candidate;
				break;
			}
		}
		if (target == null) {
			removeHover();
			return;
		}
		if (hoverPanel != null && target.option == hoveredOption) return;
		showHover(target);
	}

	private void showHover(OptionTileHoverTarget target) {

		removeHover();
		float width = Math.min(source.getTooltipWidth(), popup.getPosition().getWidth() - POPUP_INSET * 2f);
		float height = Math.min(HOVER_MAX_HEIGHT, popup.getPosition().getHeight() - POPUP_INSET * 2f);
		hoverPanel = ownPanel(popup, popup.createCustomPanel(width, height, new OptionPickerHoverPlugin(target.option)));
		new UIRect(0f, 0f, width, height).addBorder(hoverPanel, BORDER_COLOR, POPUP_BORDER_THICKNESS);
		hoveredOption = target.option;
		TooltipMakerAPI tooltip = hoverPanel.createUIElement(width - HOVER_CONTENT_INSET * 2f,
				height - HOVER_CONTENT_INSET * 2f, source.isTooltipScrollable());
		hoverContent = tooltip;
		tooltip.getPosition().setSize(width - HOVER_CONTENT_INSET * 2f,
				height - HOVER_CONTENT_INSET * 2f);
		BattleNext_UiTheme.configure(tooltip);
		source.createTooltip(tooltip, target.option);
		if (source.isTooltipScrollable()) {
			float visibleHeight = Math.min(height - HOVER_CONTENT_INSET * 2f, tooltip.getHeightSoFar());
			tooltip.getPosition().setSize(width - HOVER_CONTENT_INSET * 2f, visibleHeight);
			height = visibleHeight + HOVER_CONTENT_INSET * 2f;
			hoverPanel.getPosition().setSize(width, height);
		}
		hoverPanel.addUIElement(tooltip).inTL(HOVER_CONTENT_INSET, HOVER_CONTENT_INSET);
		PositionAPI tile = target.tile.getPosition();
		float localX = tile.getX() - popup.getPosition().getX();
		float localY = popup.getPosition().getY() + popup.getPosition().getHeight()
				- tile.getY() - tile.getHeight();
		float x = localX + TILE_SIZE + HOVER_CONTENT_INSET;
		if (x + width > popup.getPosition().getWidth() - POPUP_INSET) x = localX - width - HOVER_CONTENT_INSET;
		float y = Math.max(POPUP_INSET, Math.min(localY, popup.getPosition().getHeight() - height - POPUP_INSET));
		if (x < POPUP_INSET) x = POPUP_INSET;
		popup.addComponent(hoverPanel).inTL(x, y);
		popup.bringComponentToTop(hoverPanel);
	}

	private void removeHover() {

		if (hoverPanel != null) releasePanel(hoverPanel);
		hoverPanel = null;
		hoverContent = null;
		hoveredOption = null;
	}

	private class OptionPickerPanelPlugin extends BaseCustomUIPanelPlugin {

		@Override
		public void advance(float amount) {
			if (toggle == null) return;
			toggle.setEnabled(enabled && isRowVisible()
					&& (popup == null || inputRouter.isEmpty() || inputRouter.isTop(popupOverlay)));
		}

		@Override
		public void processInput(List<InputEventAPI> events) {
			for (InputEventAPI event : events) {
				if (event.isConsumed() || !event.isMouseDownEvent()) continue;
				if (!inputRouter.isEmpty() && !inputRouter.isTop(popupOverlay)
						&& contains(row, event)) {
					event.consume();
					continue;
				}
				if (contains(row, event) && !isInsideViewports(event)) event.consume();
			}
		}

		@Override
		public void buttonPressed(Object buttonId) {
			if (buttonId != OptionPickerAction.TOGGLE) return;
			if (popup == null) open();
			else clearOverlays();
		}
	}

	private class OptionPickerPopupPlugin extends BaseCustomUIPanelPlugin {

		@Override
		public void renderBelow(float alphaMult) {
			if (popup == null) return;
			BattleNext_GeometryRenderer.fillRect(popup.getPosition(), POPUP_BACKGROUND, alphaMult);
		}

		@Override
		public void advance(float amount) {
			if (search == null || popup == null) return;
			if (!isRowVisible()) {
				clearOverlays();
				return;
			}
			float hostWidth = overlayHost.getPosition().getWidth();
			float hostHeight = overlayHost.getPosition().getHeight();
			PositionAPI anchor = toggle.getPosition();
			float rowTop = overlayHost.getPosition().getY() + hostHeight
					- anchor.getY() - anchor.getHeight();
			float belowY = rowTop + anchor.getHeight() + POPUP_ROW_GAP;
			float popupHeight = popup.getPosition().getHeight();
			boolean above = hostHeight - belowY - POPUP_INSET < popupHeight
					&& rowTop - POPUP_INSET - POPUP_ROW_GAP > hostHeight - belowY - POPUP_INSET;
			float y = above ? rowTop - popupHeight - POPUP_ROW_GAP : belowY;
			float x = Math.max(POPUP_INSET, Math.min(anchor.getX() - overlayHost.getPosition().getX(),
					hostWidth - popup.getPosition().getWidth() - POPUP_INSET));
			popup.getPosition().inTL(x,
					Math.max(POPUP_INSET, Math.min(y, hostHeight - popupHeight - POPUP_INSET)));
			String current = search.getText();
			if (!current.equals(lastSearch)) {
				lastSearch = current;
				rebuildGrid();
				return;
			}
			float scale = Global.getSettings().getScreenScaleMult();
			updateHover(Mouse.getX() / scale, Mouse.getY() / scale);
		}

		@Override
		public void buttonPressed(Object buttonId) {
			if (buttonId == OptionPickerAction.CONFIRM) confirm();
			if (buttonId == OptionPickerAction.CANCEL) clearOverlays();
		}
	}

	private class OptionPickerTilePlugin extends BaseCustomUIPanelPlugin {

		private final OptionChoice option;
		private PositionAPI position;
		private boolean missingIcon;

		private OptionPickerTilePlugin(OptionChoice option) {
			this.option = option;
		}

		@Override
		public void positionChanged(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void renderBelow(float alphaMult) {
			BattleNext_GeometryRenderer.fillRect(position,
					selection.getOrDefault(option.id, 0) > 0 ? TILE_SELECTED : TILE_NORMAL, alphaMult);
		}

		@Override
		public void render(float alphaMult) {
			if (missingIcon) {
				float size = TILE_ICON_SIZE * 0.75f;
				float inset = TILE_CONTENT_INSET + (TILE_ICON_SIZE - size) * 0.5f;
				BattleNext_PageLayout.renderMissingIcon(BattleNext_PageLayout.missingIcon(),
						position.getX() + inset, position.getY() + inset, size, alphaMult);
			}
			int count = selection.getOrDefault(option.id, 0);
			if (count == 0) return;
			BattleNext_GeometryRenderer.lineLoop(new float[]{
					position.getX() + TILE_SELECTION_OUTLINE_INSET, position.getY() + TILE_SELECTION_OUTLINE_INSET,
					position.getX() + TILE_SIZE - TILE_SELECTION_OUTLINE_INSET,
					position.getY() + TILE_SELECTION_OUTLINE_INSET,
					position.getX() + TILE_SIZE - TILE_SELECTION_OUTLINE_INSET,
					position.getY() + TILE_SIZE - TILE_SELECTION_OUTLINE_INSET,
					position.getX() + TILE_SELECTION_OUTLINE_INSET,
					position.getY() + TILE_SIZE - TILE_SELECTION_OUTLINE_INSET
			}, CHECK_COLOR, alphaMult, 1f);
			BattleNext_GeometryRenderer.lineStrip(new float[]{
					position.getX() + TILE_CHECK_START_X, position.getY() + TILE_CHECK_START_Y,
					position.getX() + TILE_CHECK_MIDDLE_X, position.getY() + TILE_CHECK_MIDDLE_Y,
					position.getX() + TILE_CHECK_END_X, position.getY() + TILE_CHECK_END_Y
			}, CHECK_COLOR, alphaMult, 1f);
			if (count > 1) {
				for (int index = 0; index < count - 1; index++) {
					float pipX = position.getX() + TILE_SIZE - TILE_COUNT_PIP_RIGHT_INSET
							- index * TILE_COUNT_PIP_SPACING;
					float pipY = position.getY() + TILE_COUNT_PIP_TOP_INSET;
					BattleNext_GeometryRenderer.fillRect(pipX, pipY,
							TILE_COUNT_PIP_SIZE, TILE_COUNT_PIP_SIZE, CHECK_COLOR, alphaMult);
				}
			}
		}

		@Override
		public void processInput(List<InputEventAPI> events) {
			for (InputEventAPI event : events) {
				if (event.isConsumed()) continue;
				if (hoverPanel != null && contains(hoverPanel, event)) continue;
				if (!event.isLMBDownEvent() || !position.containsEvent(event)) continue;
				if (mode == OptionSelectionMode.SINGLE) selection.clear();
				int max = maxCount(option.id);
				int next = selection.getOrDefault(option.id, 0) + 1;
				if (next > max) {
					next = 0;
				}
				if (next == 0) selection.remove(option.id);
				else selection.put(option.id, next);
				event.consume();
			}
		}
	}

	private class OptionPickerHoverPlugin extends BaseCustomUIPanelPlugin {

		private final OptionChoice option;

		private OptionPickerHoverPlugin(OptionChoice option) {
			this.option = option;
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (hoverPanel == null) return;
			BattleNext_GeometryRenderer.fillRect(hoverPanel.getPosition(), HOVER_BACKGROUND, alphaMult);
		}
	}

	private static class OptionTileHoverTarget {

		private final OptionChoice option;
		private final CustomPanelAPI tile;

		private OptionTileHoverTarget(OptionChoice option, CustomPanelAPI tile) {
			this.option = option;
			this.tile = tile;
		}
	}
}
