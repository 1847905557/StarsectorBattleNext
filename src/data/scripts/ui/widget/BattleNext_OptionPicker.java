package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.widget.BattleNext_PanelBasedWidget.VisibleRows;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public class BattleNext_OptionPicker extends BattleNext_TooltipBasedWidget {

	public enum OptionSelectionMode {
		SINGLE, MULTIPLE
	}

	public record Anchor(float x, float y, float width, float height) {

	}

	public record OptionChoice(String id, String label) {

	}

	public interface OptionChoiceSource {

		List<OptionChoice> getOptions();

		default boolean groupsChoices() {
			return false;
		}

		default List<OptionChoice> getGroups() {
			return List.of();
		}

		default List<OptionChoice> getGroupOptions(String id) {
			return getOptions();
		}

		default String getGroupIcon(String id) {
			return "";
		}

		int getMaxStack(String id);

		String getIconPath(String id);

		default boolean expectsIcon() {
			return false;
		}

		default float getTooltipWidth() {
			return BattleNext_EquipmentTooltip.WIDTH;
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
	private static final float TILE_CONTENT_INSET = 2f;
	private static final float TILE_ICON_SIZE = 60f;
	private static final float TILE_LABEL_WIDTH = TILE_SIZE - TILE_CONTENT_INSET * 2f;
	private static final int TILE_LABEL_MAX_LINES = 3;
	private static final float TILE_SELECTION_OUTLINE_INSET = 1f;
	private static final float TILE_CHECK_START_X = 46f;
	private static final float TILE_CHECK_START_Y = 51f;
	private static final float TILE_CHECK_MIDDLE_X = 51f;
	private static final float TILE_CHECK_MIDDLE_Y = 46f;
	private static final float TILE_CHECK_END_X = 60f;
	private static final float TILE_CHECK_END_Y = 59f;
	private static final float TILE_COUNT_RIGHT_INSET = 5f;
	private static final float TILE_COUNT_BOTTOM_INSET = 4f;
	private static final Color POPUP_BACKGROUND = new Color(8, 14, 17, 248);
	private static final Color BORDER_COLOR = BattleNext_UiTheme.PANEL;
	private static final Color TILE_SELECTED = new Color(32, 77, 91, 235);
	private static final Color TILE_NORMAL = new Color(20, 48, 57, 210);
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
	private Supplier<Anchor> externalAnchor;
	private final Map<String, Integer> selection = new LinkedHashMap<>();

	private List<OptionChoice> options = List.of();
	private List<String> previousIds = List.of();
	private CustomPanelAPI row;
	private ButtonAPI toggle;
	private CustomPanelAPI popup;
	private PopupOverlay popupOverlay;
	private CustomPanelAPI gridPanel;
	private BattleNext_TextField search;
	private String lastSearch = "";
	private String category = "";
	private boolean selectedOnly;
	private TooltipMakerAPI grid;
	private VisibleRows visibleRows;
	private final Map<Integer, List<CustomPanelAPI>> rowTiles = new HashMap<>();

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

	public BattleNext_OptionPicker(BattleNext_UiInputRouter inputRouter, CustomPanelAPI overlayHost,
			OptionChoiceSource source, OptionSelectionMode mode, Supplier<List<String>> initialSelection,
			Consumer<List<String>> onConfirmed) {
		this(inputRouter, overlayHost, 0f, 0f, "", 0f, source, mode, initialSelection, onConfirmed);
	}

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

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		if (toggle != null) toggle.setEnabled(enabled);
		if (!enabled) clearOverlays();
	}

	public void openAt(CustomPanelAPI panelOwner, Supplier<Anchor> anchor) {
		close();
		this.panelOwner = panelOwner;
		externalAnchor = anchor;
		open();
	}

	@Override
	protected void clearOverlays() {
		if (visibleRows != null) visibleRows.close();
		visibleRows = null;
		grid = null;
		if (search != null) search.close();
		if (gridPanel != null) releasePanel(gridPanel);
		if (popup != null) {
			if (popupOverlay != null) inputRouter.pop(popupOverlay);
		}
		if (popup != null) releasePanel(popup);

		popup = null;
		popupOverlay = null;
		gridPanel = null;
		search = null;
		options = List.of();
		selection.clear();
	}

	@Override
	protected void clearContent() {
		clearOverlays();
		row = null;
		toggle = null;
		externalAnchor = null;
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
		category = "";
		selectedOnly = false;
		Set<String> known = new HashSet<>();
		for (OptionChoice option : options) known.add(option.id);
		previousIds = initialSelection == null ? List.of() : new ArrayList<>(initialSelection.get());
		selection.clear();
		for (Map.Entry<String, Integer> entry : parse(previousIds).entrySet()) {
			if (known.contains(entry.getKey())) selection.put(entry.getKey(), entry.getValue());
		}

		float hostWidth = overlayHost.getPosition().getWidth();
		float hostHeight = overlayHost.getPosition().getHeight();
		float width = Math.min(POPUP_MAX_WIDTH, hostWidth - POPUP_INSET * 2f);
		Anchor anchor = anchor();
		float rowTop = overlayHost.getPosition().getY() + hostHeight
				- anchor.y() - anchor.height();
		float belowY = rowTop + anchor.height() + POPUP_ROW_GAP;
		float belowSpace = hostHeight - belowY - POPUP_INSET;
		float aboveSpace = rowTop - POPUP_INSET - POPUP_ROW_GAP;
		boolean above = belowSpace < POPUP_MAX_HEIGHT && aboveSpace > belowSpace;
		float height = Math.min(POPUP_MAX_HEIGHT, above ? aboveSpace : belowSpace);
		float y = above ? rowTop - height - POPUP_ROW_GAP : belowY;
		float x = Math.max(POPUP_INSET, Math.min(anchor.x() - overlayHost.getPosition().getX(),
				hostWidth - width - POPUP_INSET));

		popup = ownPanel(overlayHost, panelOwner.createCustomPanel(width, height, new OptionPickerPopupPlugin()));
		new UIRect(0f, 0f, width, height).addBorder(popup, BORDER_COLOR, POPUP_BORDER_THICKNESS);
		popup.addUIElement(searchElement(width)).inTL(2f * POPUP_INSET, POPUP_INSET);
		if (source.groupsChoices()) {
			String[] captions = {strings.get("picker_hulls"), strings.get("picker_pinned"), strings.get("back"), strings.get("picker_selected")};
			String[] actions = {"browse:hulls", "browse:pinned", "browse:back", "browse:selected"};
			for (int index = 0; index < captions.length; index++) {
				BattleNext_PageLayout.fixedButton(popup, new UIRect(16f + index * (width - 32f) / 4f, 42f, (width - 40f) / 4f, 28f),
						captions[index], actions[index], CutStyle.ALL, false);
			}
		}
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

		int columns = gridColumns(width);
		float contentWidth = columns * TILE_SIZE + (columns - 1) * TILE_GAP;
		float labelWidth = Math.min(SEARCH_LABEL_WIDTH, contentWidth * 0.5f);
		float fieldWidth = contentWidth - labelWidth;
		TooltipMakerAPI searchBar = popup.createUIElement(contentWidth, SEARCH_HEIGHT, false);
		searchBar.addPara(strings.get("picker_search"), 0f)
				.getPosition().inTL(0f, SEARCH_LABEL_TOP_INSET);
		TooltipMakerAPI fieldHost = popup.createUIElement(fieldWidth, SEARCH_HEIGHT, false);
		BattleNext_TextField field = new BattleNext_TextField(inputRouter, popup,
				fieldWidth, BattleNext_PageLayout.FORM_FIELD_HEIGHT, fieldHost);
		field.attach(fieldHost, popup);
		field.getPosition().inTL(0f, 0f);
		search = field;
		search.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		fieldHost.setHeightSoFar(BattleNext_PageLayout.FORM_FIELD_HEIGHT);
		popup.addUIElement(fieldHost).inTL(2f * POPUP_INSET + labelWidth,
				POPUP_INSET + (SEARCH_HEIGHT - BattleNext_PageLayout.FORM_FIELD_HEIGHT) * 0.5f);
		searchBar.setHeightSoFar(SEARCH_HEIGHT);
		return searchBar;
	}

	private int gridColumns(float popupWidth) {
		return Math.max(1, (int) ((popupWidth - 4f * POPUP_INSET + TILE_GAP) / (TILE_SIZE + TILE_GAP)));
	}

	private void rebuildGrid() {
		if (visibleRows != null) visibleRows.close();
		if (gridPanel != null) releasePanel(gridPanel);

		float width = popup.getPosition().getWidth() - 2f * POPUP_INSET;
		float top = source.groupsChoices() ? 80f : POPUP_GRID_TOP;
		float height = popup.getPosition().getHeight() - top - 46f;
		gridPanel = ownPanel(popup, popup.createCustomPanel(width, height, new BaseCustomUIPanelPlugin()));
		grid = gridPanel.createUIElement(width, height, true);

		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		List<OptionChoice> choices = selectedOnly ? options.stream().filter(option -> selection.containsKey(option.id)).toList()
				: source.groupsChoices() ? category.isEmpty() ? source.getGroups() : source.getGroupOptions(category) : options;
		List<OptionChoice> visible = choices.stream().filter(option -> query.isEmpty()
				|| option.id.toLowerCase(Locale.ROOT).contains(query) || option.label.toLowerCase(Locale.ROOT).contains(query)).toList();
		if (visible.isEmpty()) grid.addPara(strings.get("picker_no_options"), POPUP_INSET);

		int columns = selectedOnly ? 1 : gridColumns(popup.getPosition().getWidth());
		int count = (visible.size() + columns - 1) / columns;
		float rowHeight = selectedOnly ? 40f : TILE_SIZE + TILE_GAP;
		visibleRows = new VisibleRows(count, row -> rowHeight,
				row -> mountGridRow(row, columns, rowHeight, visible), this::releaseGridRow, row -> false);
		grid.setHeightSoFar(POPUP_INSET * 2f + visibleRows.height());

		gridPanel.addUIElement(grid).inTL(0f, 0f);
		popup.addComponent(gridPanel).inTL(POPUP_INSET, top);
		visibleRows.update(0f, height);
	}

	private void mountGridRow(int row, int columns, float rowHeight, List<OptionChoice> visible) {
		List<CustomPanelAPI> tiles = new ArrayList<>();
		rowTiles.put(row, tiles);
		for (int index = row * columns; index < Math.min(visible.size(), (row + 1) * columns); index++) {
			OptionChoice option = visible.get(index);
			boolean group = source.groupsChoices() && category.isEmpty() && !selectedOnly;
			OptionPickerTilePlugin tilePlugin = new OptionPickerTilePlugin(option, group);
			CustomPanelAPI tile = gridPanel.createCustomPanel(selectedOnly ? gridPanel.getPosition().getWidth() - 16f : TILE_SIZE,
					selectedOnly ? 36f : TILE_SIZE, selectedOnly ? new BaseCustomUIPanelPlugin() {
				@Override
				public void buttonPressed(Object id) {
					new OptionPickerPopupPlugin().buttonPressed(id);
				}
			} : tilePlugin);
			tiles.add(tile);
			if (selectedOnly) {
				TooltipMakerAPI label = tile.createUIElement(tile.getPosition().getWidth() - 112f, 36f, false);
				label.addPara(label.shortenString(option.label + " x" + selection.get(option.id), label.getWidthSoFar()), 0f);
				tile.addUIElement(label).inTL(0f, 8f);
				String[] captions = {"-", "+", "x"};
				int[] changes = {-1, 1, 0};
				for (int button = 0; button < captions.length; button++) {
					BattleNext_PageLayout.fixedButton(tile, new UIRect(tile.getPosition().getWidth() - 108f + button * 36f, 0f, 32f, 32f),
							captions[button], new SelectionChange(option.id, changes[button]), CutStyle.ALL, false);
				}
			} else {
				addIcon(tile, option, tilePlugin);
				TooltipMakerAPI badge = tile.createUIElement(TILE_SIZE - TILE_COUNT_RIGHT_INSET, 0f, false);
				badge.setParaSmallInsignia();
				int count = group ? 0 : selection.getOrDefault(option.id, 0);
				tilePlugin.countLabel = badge.addPara(count > 1 ? "x" + count : "", CHECK_COLOR, 0f);
				tilePlugin.countLabel.setAlignment(Alignment.RMID);
				float badgeHeight = tilePlugin.countLabel.getPosition().getHeight();
				tilePlugin.countLabel.getPosition().setSize(TILE_SIZE - TILE_COUNT_RIGHT_INSET, badgeHeight);
				badge.setHeightSoFar(badgeHeight);
				tile.addUIElement(badge).inBL(0f, TILE_COUNT_BOTTOM_INSET);
			}
			grid.addCustomDoNotSetPosition(tile).getPosition().inTL(POPUP_INSET + index % columns * (TILE_SIZE + TILE_GAP), POPUP_INSET + row * rowHeight);
			grid.addTooltipTo(new BaseTooltipCreator() {
				@Override
				public float getTooltipWidth(Object tooltipParam) {
					return source.getTooltipWidth();
				}

				@Override
				public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
					BattleNext_UiTheme.configure(tooltip);
					if (group) tooltip.addTitle(option.label);
					else source.createTooltip(tooltip, option);
				}
			}, tile, TooltipMakerAPI.TooltipLocation.RIGHT, false);
		}
	}

	private void releaseGridRow(int row) {
		for (CustomPanelAPI tile : rowTiles.remove(row)) grid.removeComponent(tile);
	}

	private void browse(String nextCategory, boolean pending) {
		category = nextCategory;
		selectedOnly = pending;
		search.setText("");
		lastSearch = "";
		rebuildGrid();
	}

	private void changeSelection(String id, int delta) {
		int count = delta == 0 ? 0 : Math.min(maxStack(id), Math.max(0, selection.getOrDefault(id, 0) + delta));
		if (mode == OptionSelectionMode.SINGLE) selection.clear();
		if (count == 0) selection.remove(id);
		else selection.put(id, count);
	}

	private record SelectionChange(String id, int delta) {

	}

	private void addIcon(CustomPanelAPI tile, OptionChoice option, OptionPickerTilePlugin tilePlugin) {

		String icon = tilePlugin.group ? source.getGroupIcon(option.id) : source.getIconPath(option.id);
		if (icon != null && !icon.isBlank()) {
			try {
				Global.getSettings().getSprite(icon);
				TooltipMakerAPI image = tile.createUIElement(TILE_ICON_SIZE, TILE_ICON_SIZE, false);
				image.addImage(icon, TILE_ICON_SIZE, TILE_ICON_SIZE, 0f);
				image.getPrev().getPosition().inTL(0f, 0f);
				tile.addUIElement(image).inTL(TILE_CONTENT_INSET, TILE_CONTENT_INSET);
				return;
			} catch (RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_OptionPicker.class, "picker.icon.degraded",
				BattleNext_Logger.fields().put("source", source.getClass()).put("option_id", option.id).put("label", option.label).put("path", icon).put("expects_icon", source.expectsIcon()).build(), exception);
			}
		}
		if (source.expectsIcon()) {
			BattleNext_Logger.warn(BattleNext_OptionPicker.class, "picker.icon.missing",
				BattleNext_Logger.fields().put("source", source.getClass()).put("option_id", option.id).put("label", option.label).put("path", icon).put("fallback", "Missing icon").build());
			tilePlugin.missingIcon = true;
			return;
		}
		TooltipMakerAPI fallback = tile.createUIElement(TILE_ICON_SIZE, TILE_ICON_SIZE, false);
		fallback.setParaSmallInsignia();
		fallback.setTextWidthOverride(TILE_LABEL_WIDTH);
		LabelAPI label = fallback.addPara("", 0f);
		String text = tileLabel(option.label, label::computeTextWidth);
		float labelWidth = TILE_LABEL_WIDTH;
		for (String line : text.split("\\n", -1)) {
			labelWidth = Math.max(labelWidth, label.computeTextWidth(line));
		}
		fallback.setTextWidthOverride(labelWidth);
		label.setText(text);
		label.autoSizeToWidth(labelWidth);
		label.setAlignment(Alignment.MID);
		float labelHeight = label.getPosition().getHeight();
		label.getPosition().setSize(labelWidth, labelHeight);
		label.getPosition().inTL((TILE_ICON_SIZE - labelWidth) * 0.5f,
				(TILE_ICON_SIZE - labelHeight) * 0.5f);
		tile.addUIElement(fallback).inTL(TILE_CONTENT_INSET, TILE_CONTENT_INSET);
	}

	static String tileLabel(String value, ToDoubleFunction<String> widthOf) {

		String normalized = value.strip().replaceAll("\\s+", " ");
		boolean words = normalized.indexOf(' ') >= 0;
		List<String> tokens = words
				? Arrays.asList(normalized.split(" "))
				: normalized.codePoints().mapToObj(codePoint -> new String(Character.toChars(codePoint))).toList();
		List<String> lines = new ArrayList<>();
		String line = "";
		int index = 0;
		while (index < tokens.size() && lines.size() < TILE_LABEL_MAX_LINES) {
			String token = tokens.get(index);
			String separator = line.isEmpty() || !words ? "" : " ";
			String candidate = line + separator + token;
			if (!line.isEmpty() && widthOf.applyAsDouble(candidate) > TILE_LABEL_WIDTH) {
				lines.add(line);
				line = "";
				continue;
			}
			line = candidate;
			index++;
		}
		if (!line.isEmpty() && lines.size() < TILE_LABEL_MAX_LINES) {
			lines.add(line);
		}
		if (index < tokens.size()) {
			int last = lines.size() - 1;
			line = lines.get(last);
			while (!line.isEmpty() && widthOf.applyAsDouble(line + "...") > TILE_LABEL_WIDTH) {
				line = line.substring(0, line.offsetByCodePoints(line.length(), -1));
			}
			lines.set(last, line + "...");
		}
		return String.join("\n", lines);
	}

	private void confirm() {

		Map<String, Integer> previous = parse(previousIds);
		Set<String> known = new HashSet<>();
		for (OptionChoice option : options) known.add(option.id);
		if (mode == OptionSelectionMode.SINGLE) {
			List<String> selected = new ArrayList<>();
			if (!selection.isEmpty()) {
				selected.addAll(expandList(selection));
			} else if (!previous.isEmpty() && !known.contains(previous.keySet().iterator().next())) {
				selected.add(previous.keySet().iterator().next());
			}
			clearOverlays();
			if (onConfirmed != null) onConfirmed.accept(selected);
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
		List<String> selected = expandList(result);
		clearOverlays();
		if (onConfirmed != null) onConfirmed.accept(selected);
	}

	private Map<String, Integer> parse(List<String> values) {

		Map<String, Integer> counts = new LinkedHashMap<>();
		if (values == null) return counts;
		for (String value : values) {
			if (value == null) continue;
			String id = value.trim();
			if (id.isEmpty()) continue;
			if (mode == OptionSelectionMode.SINGLE && !counts.isEmpty() && !counts.containsKey(id)) continue;
			counts.put(id, Math.min(counts.getOrDefault(id, 0) + 1, maxStack(id)));
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

	private int maxStack(String id) {
		return Math.max(1, source.getMaxStack(id));
	}

	private boolean contains(UIComponentAPI component, InputEventAPI event) {
		return component != null && component.getPosition().containsEvent(event);
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
		if (externalAnchor != null) {
			Anchor anchor = externalAnchor.get();
			return anchor != null && anchor.width() > 0f && anchor.height() > 0f;
		}
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

	private Anchor anchor() {
		if (externalAnchor != null) return externalAnchor.get();
		PositionAPI position = toggle.getPosition();
		return new Anchor(position.getX(), position.getY(), position.getWidth(), position.getHeight());
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

			if (visibleRows != null) visibleRows.update(grid.getExternalScroller().getYOffset(), gridPanel.getPosition().getHeight());
			if (!isRowVisible()) {
				clearOverlays();
				return;
			}

			float hostWidth = overlayHost.getPosition().getWidth();
			float hostHeight = overlayHost.getPosition().getHeight();
			Anchor anchor = anchor();
			float rowTop = overlayHost.getPosition().getY() + hostHeight
					- anchor.y() - anchor.height();
			float belowY = rowTop + anchor.height() + POPUP_ROW_GAP;
			float popupHeight = popup.getPosition().getHeight();
			boolean above = hostHeight - belowY - POPUP_INSET < popupHeight
					&& rowTop - POPUP_INSET - POPUP_ROW_GAP > hostHeight - belowY - POPUP_INSET;
			float y = above ? rowTop - popupHeight - POPUP_ROW_GAP : belowY;
			float x = Math.max(POPUP_INSET, Math.min(anchor.x() - overlayHost.getPosition().getX(),
					hostWidth - popup.getPosition().getWidth() - POPUP_INSET));

			popup.getPosition().inTL(x,
					Math.max(POPUP_INSET, Math.min(y, hostHeight - popupHeight - POPUP_INSET)));

			String current = search.getText();
			if (!current.equals(lastSearch)) {
				lastSearch = current;
				rebuildGrid();
				return;
			}
		}

		@Override
		public void buttonPressed(Object buttonId) {
			if (buttonId instanceof SelectionChange change) {
				changeSelection(change.id, change.delta);
				rebuildGrid();
			}
			if ("browse:hulls".equals(buttonId) || "browse:back".equals(buttonId)) browse("", false);
			if ("browse:pinned".equals(buttonId)) browse("@pinned", false);
			if ("browse:selected".equals(buttonId)) browse("", true);
			if (buttonId == OptionPickerAction.CONFIRM) confirm();
			if (buttonId == OptionPickerAction.CANCEL) clearOverlays();
		}
	}

	private class OptionPickerTilePlugin extends BaseCustomUIPanelPlugin {

		private final OptionChoice option;
		private final boolean group;
		private PositionAPI position;
		private boolean missingIcon;
		private LabelAPI countLabel;

		private OptionPickerTilePlugin(OptionChoice option, boolean group) {
			this.option = option;
			this.group = group;
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
		public void advance(float amount) {
			int count = group ? 0 : selection.getOrDefault(option.id, 0);
			countLabel.setText(count > 1 ? "x" + count : "");
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
		}

		@Override
		public void processInput(List<InputEventAPI> events) {
			for (InputEventAPI event : events) {
				if (event.isConsumed()) continue;
				if (!event.isLMBDownEvent() || !position.containsEvent(event)) continue;
				if (group) {
					browse(option.id, false);
					event.consume();
					return;
				}

				int max = maxStack(option.id);
				int next = selection.getOrDefault(option.id, 0) + 1;
				if (next > max) {
					next = 0;
				}
				if (mode == OptionSelectionMode.SINGLE) {
					selection.clear();
				}
				if (next == 0) selection.remove(option.id);
				else selection.put(option.id, next);
				event.consume();
			}
		}
	}

}
