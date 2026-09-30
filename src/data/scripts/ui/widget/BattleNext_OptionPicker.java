package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
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
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BattleNext_OptionPicker {

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
	private static final Color POPUP_BACKGROUND = new Color(8, 14, 17, 248);
	private static final Color BORDER_COLOR = BattleNext_UiTheme.PANEL;
	private static final Color TILE_SELECTED = new Color(32, 77, 91, 235);
	private static final Color TILE_NORMAL = new Color(20, 48, 57, 210);
	private static final Color HOVER_BACKGROUND = new Color(8, 14, 17, 250);
	private static final Color CHECK_COLOR = new Color(0.5f, 0.9f, 1f, 1f);
	private static BattleNext_OptionPicker activePicker;

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final CustomPanelAPI overlayHost;
	private final OptionChoiceSource source;
	private final OptionSelectionMode mode;
	private final Supplier<List<String>> initialSelection;
	private final Consumer<List<String>> onConfirmed;
	private List<TooltipMakerAPI> viewports = List.of();
	private final Map<String, Integer> selection = new LinkedHashMap<>();

	private List<OptionChoice> options = List.of();
	private List<String> previousIds = List.of();
	private CustomPanelAPI row;
	private ButtonAPI toggle;
	private CustomPanelAPI popup;
	private CustomPanelAPI gridPanel;
	private ScrollPanelAPI gridScroller;
	private CustomPanelAPI hoverPanel;
	private TextFieldAPI search;
	private String lastSearch = "";
	private OptionChoice hoveredOption;
	private final List<OptionTileHoverTarget> hoverTargets = new ArrayList<>();

	public BattleNext_OptionPicker(CustomPanelAPI overlayHost, OptionChoiceSource source,
			OptionSelectionMode mode, Supplier<List<String>> initialSelection,
			Consumer<List<String>> onConfirmed) {
		this.overlayHost = overlayHost;
		this.source = source;
		this.mode = mode;
		this.initialSelection = initialSelection;
		this.onConfirmed = onConfirmed;
	}

	public void addTo(TooltipMakerAPI host, CustomPanelAPI owner, float width, String label) {
		addTo(host, owner, width, label, new TooltipMakerAPI[0]);
	}

	public void addTo(TooltipMakerAPI host, CustomPanelAPI owner, float width, String label,
			TooltipMakerAPI... viewports) {
		setViewports(viewports);
		row = owner.createCustomPanel(width, BattleNext_PageLayout.FORM_FIELD_HEIGHT,
				new OptionPickerPanelPlugin());
		toggle = BattleNext_PageLayout.fixedButton(row,
				new UIRect(0f, 0f, width, BattleNext_PageLayout.FORM_FIELD_HEIGHT), label,
				OptionPickerAction.TOGGLE, CutStyle.ALL, false);
		host.addCustom(row, POPUP_ROW_GAP);
	}

	public void addButton(CustomPanelAPI owner, UIRect rect, String label) {
		addButton(owner, rect, label, new TooltipMakerAPI[0]);
	}

	public void addButton(CustomPanelAPI owner, UIRect rect, String label,
			TooltipMakerAPI... viewports) {
		setViewports(viewports);
		row = owner.createCustomPanel(rect.getWidth(), rect.getHeight(), new OptionPickerPanelPlugin());
		toggle = BattleNext_PageLayout.fixedButton(row,
				new UIRect(0f, 0f, rect.getWidth(), rect.getHeight()), label,
				OptionPickerAction.TOGGLE, CutStyle.ALL, false);
		owner.addComponent(row).inTL(rect.getX(), rect.getY());
	}

	public void setEnabled(boolean enabled) {
		if (toggle != null) toggle.setEnabled(enabled);
		if (!enabled) close();
	}

	public void close() {
		BattleNext_TextField.releaseFocusedField();
		if (activePicker == this) activePicker = null;
		if (popup != null) BattleNext_TextFieldSession.clearModalRegion(popup.getPosition());
		if (popup != null) overlayHost.removeComponent(popup);
		popup = null;
		gridPanel = null;
		gridScroller = null;
		hoverPanel = null;
		search = null;
		hoveredOption = null;
		options = List.of();
		selection.clear();
		hoverTargets.clear();
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {

		if (popup == null) return false;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent()
					&& event.getEventValue() == Keyboard.KEY_ESCAPE) {
				close();
				event.consume();
				return true;
			}
			if (!event.isMouseDownEvent() && !event.isMouseScrollEvent()) continue;
			if (event.isMouseScrollEvent() && row != null && contains(row, event)
					&& !contains(popup, event)) {
				close();
				event.consume();
				continue;
			}
			if (event.isMouseDownEvent() && !contains(popup, event) && !contains(toggle, event)) {
				close();
				event.consume();
				return true;
			}
		}
		return false;
	}

	private void open() {

		if (popup != null) return;
		if (activePicker != null && activePicker != this) activePicker.close();
		BattleNext_TextField.releaseFocusedField();
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
		float rowTop = overlayHost.getPosition().getY() + hostHeight
				- row.getPosition().getY() - row.getPosition().getHeight();
		float belowY = rowTop + BattleNext_PageLayout.FORM_FIELD_HEIGHT + POPUP_ROW_GAP;
		float belowSpace = hostHeight - belowY - POPUP_INSET;
		float aboveSpace = rowTop - POPUP_INSET - POPUP_ROW_GAP;
		boolean above = belowSpace < POPUP_MAX_HEIGHT && aboveSpace > belowSpace;
		float height = Math.min(POPUP_MAX_HEIGHT, above ? aboveSpace : belowSpace);
		float y = above ? rowTop - height - POPUP_ROW_GAP : belowY;
		float x = Math.max(POPUP_INSET, Math.min(row.getPosition().getX() - overlayHost.getPosition().getX(),
				hostWidth - width - POPUP_INSET));

		popup = overlayHost.createCustomPanel(width, height, new OptionPickerPopupPlugin());
		new UIRect(0f, 0f, width, height).addBorder(popup, BORDER_COLOR, POPUP_BORDER_THICKNESS);
		popup.addUIElement(searchElement(width)).inTL(POPUP_INSET, POPUP_INSET);
		float buttonWidth = Math.min(POPUP_ACTION_MAX_WIDTH, (width - 3f * POPUP_INSET) * 0.5f);
		float buttonY = height - POPUP_ACTION_BOTTOM_RESERVE;
		BattleNext_PageLayout.fixedButton(popup,
				new UIRect(width - 2f * buttonWidth - 2f * POPUP_INSET, buttonY,
						buttonWidth, POPUP_ACTION_BUTTON_HEIGHT),
				strings.get("picker_confirm"), OptionPickerAction.CONFIRM, CutStyle.ALL, false);
		BattleNext_PageLayout.fixedButton(popup,
				new UIRect(width - buttonWidth - POPUP_INSET, buttonY,
						buttonWidth, POPUP_ACTION_BUTTON_HEIGHT),
				strings.get("picker_cancel"), OptionPickerAction.CANCEL, CutStyle.ALL, false);
		overlayHost.addComponent(popup).inTL(x, y);
		overlayHost.bringComponentToTop(popup);
		activePicker = this;
		BattleNext_TextFieldSession.setModalRegion(popup.getPosition());
		lastSearch = "";
		rebuildGrid();
	}

	private TooltipMakerAPI searchElement(float width) {

		TooltipMakerAPI searchBar = popup.createUIElement(width - 2f * POPUP_INSET, SEARCH_HEIGHT, false);
		searchBar.addPara(strings.get("picker_search"), 0f)
				.getPosition().inTL(0f, SEARCH_LABEL_TOP_INSET);
		TooltipMakerAPI fieldHost = popup.createUIElement(width - SEARCH_FIELD_WIDTH_RESERVE, SEARCH_HEIGHT, false);
		search = BattleNext_TextField.addTo(fieldHost, popup,
				width - SEARCH_FIELD_WIDTH_RESERVE, BattleNext_PageLayout.FORM_FIELD_HEIGHT, "");
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
		if (gridPanel != null) popup.removeComponent(gridPanel);
		gridScroller = null;
		float width = popup.getPosition().getWidth() - 2f * POPUP_INSET;
		float height = popup.getPosition().getHeight() - POPUP_GRID_HEIGHT_RESERVE;
		gridPanel = popup.createCustomPanel(width, height, new BaseCustomUIPanelPlugin());
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
				CustomPanelAPI tile = gridPanel.createCustomPanel(TILE_SIZE, TILE_SIZE, new OptionPickerTilePlugin(option));
				addIcon(tile, option);
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

	private void addIcon(CustomPanelAPI tile, OptionChoice option) {

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
		TooltipMakerAPI fallback = tile.createUIElement(TILE_ICON_SIZE, TILE_ICON_SIZE, false);
		fallback.setParaSmallInsignia();
		fallback.addPara(fallback.shortenString(option.label, TILE_LABEL_TRUNCATION_WIDTH), 0f)
				.getPosition().inMid();
		tile.addUIElement(fallback).inTL(TILE_CONTENT_INSET, TILE_CONTENT_INSET);
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
			close();
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
		close();
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

	private void setViewports(TooltipMakerAPI... hosts) {
		viewports = hosts == null ? List.of() : Arrays.stream(hosts)
				.filter(host -> host != null).toList();
	}

	private boolean isInsideViewports(InputEventAPI event) {
		for (TooltipMakerAPI host : viewports) {
			ScrollPanelAPI scroller = host.getExternalScroller();
			PositionAPI position = scroller == null ? host.getPosition() : scroller.getPosition();
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
		PositionAPI gridViewport = gridScroller == null
				? gridPanel == null ? null : gridPanel.getPosition()
				: gridScroller.getPosition();
		if (popup == null || !contains(popup.getPosition(), x, y)
				|| !contains(gridViewport, x, y)) {
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
		float width = Math.min(HOVER_MAX_WIDTH, popup.getPosition().getWidth() - POPUP_INSET * 2f);
		float height = Math.min(HOVER_MAX_HEIGHT, popup.getPosition().getHeight() - POPUP_INSET * 2f);
		hoverPanel = popup.createCustomPanel(width, height, new OptionPickerHoverPlugin(target.option));
		new UIRect(0f, 0f, width, height).addBorder(hoverPanel, BORDER_COLOR, POPUP_BORDER_THICKNESS);
		hoveredOption = target.option;
		TooltipMakerAPI tooltip = hoverPanel.createUIElement(width - HOVER_CONTENT_INSET * 2f,
				height - HOVER_CONTENT_INSET * 2f, false);
		BattleNext_UiTheme.configure(tooltip);
		source.createTooltip(tooltip, target.option);
		hoverPanel.addUIElement(tooltip).inTL(HOVER_CONTENT_INSET, HOVER_CONTENT_INSET);
		PositionAPI tile = target.tile.getPosition();
		float localX = tile.getX() - popup.getPosition().getX();
		float localY = tile.getY() - popup.getPosition().getY();
		float x = localX + TILE_SIZE + HOVER_CONTENT_INSET;
		if (x + width > popup.getPosition().getWidth() - POPUP_INSET) x = localX - width - HOVER_CONTENT_INSET;
		float y = Math.max(POPUP_INSET, Math.min(localY, popup.getPosition().getHeight() - height - POPUP_INSET));
		if (x < POPUP_INSET) x = POPUP_INSET;
		popup.addComponent(hoverPanel).inTL(x, y);
		popup.bringComponentToTop(hoverPanel);
	}

	private void removeHover() {

		if (popup != null && hoverPanel != null) popup.removeComponent(hoverPanel);
		hoverPanel = null;
		hoveredOption = null;
	}

	private class OptionPickerPanelPlugin extends BaseCustomUIPanelPlugin {

		@Override
		public void advance(float amount) {
			if (toggle == null) return;
			toggle.setEnabled(isRowVisible()
					&& (activePicker == null || activePicker == BattleNext_OptionPicker.this));
		}

		@Override
		public void processInput(List<InputEventAPI> events) {
			for (InputEventAPI event : events) {
				if (event.isConsumed() || !event.isMouseDownEvent()) continue;
				if (activePicker != null && activePicker != BattleNext_OptionPicker.this
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
			else close();
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
				close();
				return;
			}
			float hostWidth = overlayHost.getPosition().getWidth();
			float hostHeight = overlayHost.getPosition().getHeight();
			float rowTop = overlayHost.getPosition().getY() + hostHeight
					- row.getPosition().getY() - row.getPosition().getHeight();
			float belowY = rowTop + BattleNext_PageLayout.FORM_FIELD_HEIGHT + POPUP_ROW_GAP;
			float popupHeight = popup.getPosition().getHeight();
			boolean above = hostHeight - belowY - POPUP_INSET < popupHeight
					&& rowTop - POPUP_INSET - POPUP_ROW_GAP > hostHeight - belowY - POPUP_INSET;
			float y = above ? rowTop - popupHeight - POPUP_ROW_GAP : belowY;
			float x = Math.max(POPUP_INSET, Math.min(row.getPosition().getX() - overlayHost.getPosition().getX(),
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
			if (buttonId == OptionPickerAction.CANCEL) close();
		}
	}

	private class OptionPickerTilePlugin extends BaseCustomUIPanelPlugin {

		private final OptionChoice option;
		private PositionAPI position;

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
