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

	private static final float FIELD_HEIGHT = 28f;
	private static final float SEARCH_HEIGHT = 32f;
	private static final float TILE_SIZE = 64f;
	private static final float TILE_GAP = 8f;
	private static final float PADDING = 8f;
	private static final Color POPUP_BACKGROUND = new Color(8, 14, 17, 248);
	private static final Color BORDER_COLOR = BattleNext_UiTheme.HEADER;
	private static final Color TILE_SELECTED = new Color(32, 77, 91, 235);
	private static final Color TILE_NORMAL = new Color(20, 48, 57, 210);
	private static final Color HOVER_BACKGROUND = new Color(8, 14, 17, 250);
	private static final Color CHECK_COLOR = new Color(0.5f, 0.9f, 1f, 1f);

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final CustomPanelAPI overlayHost;
	private final OptionChoiceSource source;
	private final OptionSelectionMode mode;
	private final Supplier<List<String>> initialSelection;
	private final Consumer<List<String>> onConfirmed;
	private final Map<String, Integer> selection = new LinkedHashMap<>();

	private List<OptionChoice> options = List.of();
	private List<String> previousIds = List.of();
	private CustomPanelAPI row;
	private ButtonAPI toggle;
	private CustomPanelAPI popup;
	private CustomPanelAPI gridPanel;
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
		row = owner.createCustomPanel(width, FIELD_HEIGHT, new OptionPickerPanelPlugin());
		toggle = BattleNext_PageLayout.fixedButton(row,
				new UIRect(0f, 0f, width, FIELD_HEIGHT), label,
				OptionPickerAction.TOGGLE, CutStyle.ALL, false);
		host.addCustom(row, 2f);
	}

	public void addButton(CustomPanelAPI owner, UIRect rect, String label) {
		row = owner.createCustomPanel(rect.getWidth(), rect.getHeight(), new OptionPickerPanelPlugin());
		toggle = BattleNext_PageLayout.fixedButton(row,
				new UIRect(0f, 0f, rect.getWidth(), rect.getHeight()), label,
				OptionPickerAction.TOGGLE, CutStyle.ALL, false);
		owner.addComponent(row).inTL(rect.getX(), rect.getY());
	}

	public void close() {
		BattleNext_TextField.releaseFocusedField();
		if (popup != null) overlayHost.removeComponent(popup);
		popup = null;
		gridPanel = null;
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
			float scale = Global.getSettings().getScreenScaleMult();
			float x = Mouse.getX() / scale;
			float y = Mouse.getY() / scale;
			if (event.isMouseScrollEvent() && row != null && contains(row, x, y) && !contains(popup, x, y)) {
				close();
				continue;
			}
			if (event.isMouseDownEvent() && !contains(popup, x, y) && !contains(toggle, x, y)) {
				close();
			}
		}
		return false;
	}

	private void open() {

		if (popup != null) return;
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
		float width = Math.min(540f, hostWidth - 16f);
		float rowTop = overlayHost.getPosition().getY() + hostHeight
				- row.getPosition().getY() - row.getPosition().getHeight();
		float belowY = rowTop + FIELD_HEIGHT + 2f;
		float belowSpace = hostHeight - belowY - PADDING;
		float aboveSpace = rowTop - PADDING - 2f;
		boolean above = belowSpace < 420f && aboveSpace > belowSpace;
		float height = Math.min(420f, above ? aboveSpace : belowSpace);
		float y = above ? rowTop - height - 2f : belowY;
		float x = Math.max(PADDING, Math.min(row.getPosition().getX() - overlayHost.getPosition().getX(),
				hostWidth - width - PADDING));

		popup = overlayHost.createCustomPanel(width, height, new OptionPickerPopupPlugin());
		new UIRect(0f, 0f, width, height).addBorder(popup, BORDER_COLOR, 2f);
		popup.addUIElement(searchElement(width)).inTL(PADDING, PADDING);
		float buttonWidth = Math.min(120f, (width - 3f * PADDING) * 0.5f);
		float buttonY = height - 38f;
		BattleNext_PageLayout.fixedButton(popup,
				new UIRect(width - 2f * buttonWidth - 2f * PADDING, buttonY, buttonWidth, 30f),
				strings.get("picker_confirm"), OptionPickerAction.CONFIRM, CutStyle.ALL, false);
		BattleNext_PageLayout.fixedButton(popup,
				new UIRect(width - buttonWidth - PADDING, buttonY, buttonWidth, 30f),
				strings.get("picker_cancel"), OptionPickerAction.CANCEL, CutStyle.ALL, false);
		overlayHost.addComponent(popup).inTL(x, y);
		overlayHost.bringComponentToTop(popup);
		lastSearch = "";
		rebuildGrid();
	}

	private TooltipMakerAPI searchElement(float width) {

		TooltipMakerAPI searchBar = popup.createUIElement(width - 2f * PADDING, SEARCH_HEIGHT, false);
		searchBar.addPara(strings.get("picker_search"), 0f, BattleNext_UiTheme.MUTED)
				.getPosition().inTL(0f, 6f);
		TooltipMakerAPI fieldHost = popup.createUIElement(width - 96f, SEARCH_HEIGHT, false);
		search = BattleNext_TextField.addTo(fieldHost, popup, width - 96f, FIELD_HEIGHT, "");
		search.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		fieldHost.setHeightSoFar(FIELD_HEIGHT);
		popup.addUIElement(fieldHost).inTL(PADDING + 80f, PADDING + (SEARCH_HEIGHT - FIELD_HEIGHT) * 0.5f);
		searchBar.setHeightSoFar(SEARCH_HEIGHT);
		return searchBar;
	}

	private void rebuildGrid() {

		removeHover();
		hoverTargets.clear();
		if (gridPanel != null) popup.removeComponent(gridPanel);
		float width = popup.getPosition().getWidth() - 2f * PADDING;
		float height = popup.getPosition().getHeight() - 90f;
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
			grid.addPara(strings.get("picker_no_options"), PADDING, BattleNext_UiTheme.MUTED);
		} else {
			int columns = Math.max(1, (int) ((width - 2f * PADDING + TILE_GAP) / (TILE_SIZE + TILE_GAP)));
			for (int index = 0; index < visible.size(); index++) {
				OptionChoice option = visible.get(index);
				CustomPanelAPI tile = gridPanel.createCustomPanel(TILE_SIZE, TILE_SIZE, new OptionPickerTilePlugin(option));
				addIcon(tile, option);
				grid.addCustomDoNotSetPosition(tile).getPosition().inTL(PADDING + index % columns * (TILE_SIZE + TILE_GAP),
						PADDING + index / columns * (TILE_SIZE + TILE_GAP));
				hoverTargets.add(new OptionTileHoverTarget(option, tile));
			}
			int rows = (visible.size() + columns - 1) / columns;
			grid.setHeightSoFar(2f * PADDING + rows * (TILE_SIZE + TILE_GAP));
		}
		gridPanel.addUIElement(grid).inTL(0f, 0f);
		popup.addComponent(gridPanel).inTL(PADDING, 44f);
	}

	private void addIcon(CustomPanelAPI tile, OptionChoice option) {

		String icon = source.getIconPath(option.id);
		if (icon != null && !icon.isBlank()) {
			try {
				Global.getSettings().getSprite(icon);
				TooltipMakerAPI image = tile.createUIElement(60f, 60f, false);
				image.addImage(icon, 60f, 60f, 0f);
				image.getPrev().getPosition().inTL(0f, 0f);
				tile.addUIElement(image).inTL(2f, 2f);
				return;
			} catch (RuntimeException exception) {
				// A missing optional sprite leaves the option selectable.
			}
		}
		TooltipMakerAPI fallback = tile.createUIElement(60f, 60f, false);
		fallback.setParaSmallInsignia();
		fallback.addPara(fallback.shortenString(option.label, 54f), 0f, BattleNext_UiTheme.MUTED)
				.getPosition().inMid();
		tile.addUIElement(fallback).inTL(2f, 2f);
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

	private boolean contains(UIComponentAPI component, float x, float y) {

		PositionAPI position = component.getPosition();
		return x >= position.getX() && x < position.getX() + position.getWidth()
				&& y >= position.getY() && y < position.getY() + position.getHeight();
	}

	private void updateHover(float x, float y) {

		OptionTileHoverTarget target = null;
		for (OptionTileHoverTarget candidate : hoverTargets) {
			PositionAPI position = candidate.tile.getPosition();
			if (x >= position.getX() && x < position.getX() + position.getWidth()
					&& y >= position.getY() && y < position.getY() + position.getHeight()) {
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
		float width = Math.min(300f, popup.getPosition().getWidth() - 16f);
		float height = Math.min(180f, popup.getPosition().getHeight() - 16f);
		hoverPanel = popup.createCustomPanel(width, height, new OptionPickerHoverPlugin(target.option));
		new UIRect(0f, 0f, width, height).addBorder(hoverPanel, BORDER_COLOR, 2f);
		hoveredOption = target.option;
		TooltipMakerAPI tooltip = hoverPanel.createUIElement(width - 12f, height - 12f, false);
		BattleNext_UiTheme.configure(tooltip);
		source.createTooltip(tooltip, target.option);
		hoverPanel.addUIElement(tooltip).inTL(6f, 6f);
		PositionAPI tile = target.tile.getPosition();
		float localX = tile.getX() - popup.getPosition().getX();
		float localY = tile.getY() - popup.getPosition().getY();
		float x = localX + TILE_SIZE + 6f;
		if (x + width > popup.getPosition().getWidth() - PADDING) x = localX - width - 6f;
		float y = Math.max(PADDING, Math.min(localY, popup.getPosition().getHeight() - height - PADDING));
		if (x < PADDING) x = PADDING;
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
			float hostWidth = overlayHost.getPosition().getWidth();
			float hostHeight = overlayHost.getPosition().getHeight();
			float rowTop = overlayHost.getPosition().getY() + hostHeight
					- row.getPosition().getY() - row.getPosition().getHeight();
			float belowY = rowTop + FIELD_HEIGHT + 2f;
			float popupHeight = popup.getPosition().getHeight();
			boolean above = hostHeight - belowY - PADDING < popupHeight
					&& rowTop - PADDING - 2f > hostHeight - belowY - PADDING;
			float y = above ? rowTop - popupHeight - 2f : belowY;
			float x = Math.max(PADDING, Math.min(row.getPosition().getX() - overlayHost.getPosition().getX(),
					hostWidth - popup.getPosition().getWidth() - PADDING));
			popup.getPosition().inTL(x, Math.max(PADDING, Math.min(y, hostHeight - popupHeight - PADDING)));
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
					position.getX() + 1f, position.getY() + 1f,
					position.getX() + TILE_SIZE - 1f, position.getY() + 1f,
					position.getX() + TILE_SIZE - 1f, position.getY() + TILE_SIZE - 1f,
					position.getX() + 1f, position.getY() + TILE_SIZE - 1f
			}, CHECK_COLOR, alphaMult, 1f);
			BattleNext_GeometryRenderer.lineStrip(new float[]{
					position.getX() + 46f, position.getY() + 51f,
					position.getX() + 51f, position.getY() + 46f,
					position.getX() + 60f, position.getY() + 59f
			}, CHECK_COLOR, alphaMult, 1f);
			if (count > 1) {
				for (int index = 0; index < count - 1; index++) {
					float pipX = position.getX() + TILE_SIZE - 10f - index * 7f;
					float pipY = position.getY() + 4f;
					BattleNext_GeometryRenderer.fillRect(pipX, pipY, 5f, 5f, CHECK_COLOR, alphaMult);
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
