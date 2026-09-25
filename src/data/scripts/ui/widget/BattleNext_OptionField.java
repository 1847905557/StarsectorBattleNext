package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BattleNext_OptionField {

	public enum Mode {
		SINGLE, MULTIPLE
	}

	public static class Option {

		private final String id;
		private final String label;

		public Option(String id, String label) {
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

	public interface Source {

		List<Option> getOptions();

		int getMaxCount(String id);

		String getIcon(String id);

		void createTooltip(TooltipMakerAPI tooltip, Option option);
	}

	private enum Action {
		TOGGLE, CONFIRM, CANCEL
	}

	private static final float FIELD_HEIGHT = 28f;
	private static final float SEARCH_HEIGHT = 32f;
	private static final float BUTTON_WIDTH = 32f;
	private static final float TILE_SIZE = 64f;
	private static final float TILE_GAP = 8f;
	private static final float PADDING = 8f;
	private static final Color POPUP_BACKGROUND = new Color(8, 14, 17, 248);
	private static final Color TILE_SELECTED = new Color(32, 77, 91, 235);
	private static final Color TILE_NORMAL = new Color(20, 48, 57, 210);
	private static final Color HOVER_BACKGROUND = new Color(8, 14, 17, 250);

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private final CustomPanelAPI overlayHost;
	private final Source source;
	private final Mode mode;
	private final Map<String, Integer> selection = new LinkedHashMap<>();

	private List<Option> options = List.of();
	private TooltipMakerAPI fieldHost;
	private CustomPanelAPI row;
	private ButtonAPI toggle;
	private TextFieldAPI field;
	private CustomPanelAPI popup;
	private CustomPanelAPI gridPanel;
	private CustomPanelAPI hoverPanel;
	private TextFieldAPI search;
	private String lastSearch = "";
	private Option hoveredOption;
	private final List<HoverTarget> hoverTargets = new ArrayList<>();

	public BattleNext_OptionField(CustomPanelAPI overlayHost, Source source, Mode mode) {
		this.overlayHost = overlayHost;
		this.source = source;
		this.mode = mode;
	}

	public TextFieldAPI addTo(TooltipMakerAPI host, CustomPanelAPI owner, float width, String value) {

		fieldHost = host;
		row = owner.createCustomPanel(width, FIELD_HEIGHT, new FieldPlugin());
		TooltipMakerAPI input = row.createUIElement(width - BUTTON_WIDTH, FIELD_HEIGHT, false);
		field = BattleNext_TextField.addTo(input, row, width - BUTTON_WIDTH, FIELD_HEIGHT,
				value == null ? "" : value);
		field.getPosition().inTL(0f, 0f);
		field.setLimitByStringWidth(false);
		field.setVerticalCursor(true);
		field.setMaxChars(BattleNext_TextField.RECOMMENDED_MAX_CHARS);
		row.addUIElement(input).inTL(0f, 0f);
		toggle = BattleNext_PageLayout.fixedButton(row,
				new UIRect(width - BUTTON_WIDTH, 0f, BUTTON_WIDTH, FIELD_HEIGHT),
				"...", Action.TOGGLE, CutStyle.ALL, false);
		host.addCustom(row, 2f);
		return field;
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
			if (event.isMouseScrollEvent() && contains(fieldHost, x, y) && !contains(popup, x, y)) {
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
		for (Option option : options) {
			known.add(option.id);
		}
		selection.clear();
		for (Map.Entry<String, Integer> entry : parse(field.getText()).entrySet()) {
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

		popup = overlayHost.createCustomPanel(width, height, new PopupPlugin());
		popup.addUIElement(searchElement(width)).inTL(PADDING, PADDING);
		float buttonWidth = Math.min(120f, (width - 3f * PADDING) * 0.5f);
		float buttonY = height - 38f;
		BattleNext_PageLayout.fixedButton(popup,
				new UIRect(width - 2f * buttonWidth - 2f * PADDING, buttonY, buttonWidth, 30f),
				strings.get("picker_confirm"), Action.CONFIRM, CutStyle.ALL, false);
		BattleNext_PageLayout.fixedButton(popup,
				new UIRect(width - buttonWidth - PADDING, buttonY, buttonWidth, 30f),
				strings.get("picker_cancel"), Action.CANCEL, CutStyle.ALL, false);
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
		List<Option> visible = new ArrayList<>();
		for (Option option : options) {
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
				Option option = visible.get(index);
				CustomPanelAPI tile = gridPanel.createCustomPanel(TILE_SIZE, TILE_SIZE, new TilePlugin(option));
				addIcon(tile, option);
				grid.addCustomDoNotSetPosition(tile).getPosition().inTL(PADDING + index % columns * (TILE_SIZE + TILE_GAP),
						PADDING + index / columns * (TILE_SIZE + TILE_GAP));
				hoverTargets.add(new HoverTarget(option, tile));
			}
			int rows = (visible.size() + columns - 1) / columns;
			grid.setHeightSoFar(2f * PADDING + rows * (TILE_SIZE + TILE_GAP));
		}
		gridPanel.addUIElement(grid).inTL(0f, 0f);
		popup.addComponent(gridPanel).inTL(PADDING, 44f);
	}

	private void addIcon(CustomPanelAPI tile, Option option) {

		String icon = source.getIcon(option.id);
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
		fallback.addPara("?", 0f, BattleNext_UiTheme.MUTED).getPosition().inMid();
		tile.addUIElement(fallback).inTL(2f, 2f);
	}

	private void confirm() {

		Map<String, Integer> previous = parse(field.getText());
		Set<String> known = new HashSet<>();
		for (Option option : options) {
			known.add(option.id);
		}
		if (mode == Mode.SINGLE) {
			if (!selection.isEmpty()) {
				field.setText(selection.keySet().iterator().next());
			} else if (!previous.isEmpty() && known.contains(previous.keySet().iterator().next())) {
				field.setText("");
			}
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
		for (Option option : options) {
			if (selection.containsKey(option.id) && !result.containsKey(option.id)) {
				result.put(option.id, selection.get(option.id));
			}
		}
		field.setText(expand(result));
		close();
	}

	private String expand(Map<String, Integer> counts) {
		StringBuilder value = new StringBuilder();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			for (int index = 0; index < entry.getValue(); index++) {
				if (value.length() > 0) value.append(',');
				value.append(entry.getKey());
			}
		}
		return value.toString();
	}

	private Map<String, Integer> parse(String value) {

		Map<String, Integer> counts = new LinkedHashMap<>();
		if (value == null || value.isBlank()) return counts;
		if (mode == Mode.SINGLE) {
			counts.put(value.trim(), 1);
			return counts;
		}
		for (String part : value.split(",")) {
			String id = part.trim();
			if (id.isEmpty()) continue;
			counts.put(id, Math.min(counts.getOrDefault(id, 0) + 1, maxCount(id)));
		}
		return counts;
	}

	private int maxCount(String id) {
		return Math.max(1, source.getMaxCount(id));
	}

	private boolean contains(UIComponentAPI component, float x, float y) {

		PositionAPI position = component.getPosition();
		return x >= position.getX() && x < position.getX() + position.getWidth()
				&& y >= position.getY() && y < position.getY() + position.getHeight();
	}

	private void drawRect(PositionAPI position, Color color, float alphaMult) {

		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
				color.getAlpha() / 255f * alphaMult);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex2f(position.getX(), position.getY());
		GL11.glVertex2f(position.getX() + position.getWidth(), position.getY());
		GL11.glVertex2f(position.getX() + position.getWidth(), position.getY() + position.getHeight());
		GL11.glVertex2f(position.getX(), position.getY() + position.getHeight());
		GL11.glEnd();
		GL11.glPopAttrib();
	}

	private void updateHover(float x, float y) {

		HoverTarget target = null;
		for (HoverTarget candidate : hoverTargets) {
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

	private void showHover(HoverTarget target) {

		removeHover();
		float width = Math.min(300f, popup.getPosition().getWidth() - 16f);
		float height = Math.min(180f, popup.getPosition().getHeight() - 16f);
		hoverPanel = popup.createCustomPanel(width, height, new HoverPlugin(target.option));
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

	private class FieldPlugin extends BaseCustomUIPanelPlugin {

		@Override
		public void buttonPressed(Object buttonId) {
			if (buttonId != Action.TOGGLE) return;
			if (popup == null) open();
			else close();
		}
	}

	private class PopupPlugin extends BaseCustomUIPanelPlugin {

		@Override
		public void renderBelow(float alphaMult) {
			if (popup == null) return;
			drawRect(popup.getPosition(), POPUP_BACKGROUND, alphaMult);
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
			if (buttonId == Action.CONFIRM) confirm();
			if (buttonId == Action.CANCEL) close();
		}
	}

	private class TilePlugin extends BaseCustomUIPanelPlugin {

		private final Option option;
		private PositionAPI position;

		private TilePlugin(Option option) {
			this.option = option;
		}

		@Override
		public void positionChanged(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void renderBelow(float alphaMult) {
			drawRect(position, selection.getOrDefault(option.id, 0) > 0 ? TILE_SELECTED : TILE_NORMAL, alphaMult);
		}

		@Override
		public void render(float alphaMult) {
			int count = selection.getOrDefault(option.id, 0);
			if (count == 0) return;
			GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glColor4f(0.5f, 0.9f, 1f, alphaMult);
			GL11.glLineWidth(1f);
			GL11.glBegin(GL11.GL_LINE_LOOP);
			GL11.glVertex2f(position.getX() + 1f, position.getY() + 1f);
			GL11.glVertex2f(position.getX() + TILE_SIZE - 1f, position.getY() + 1f);
			GL11.glVertex2f(position.getX() + TILE_SIZE - 1f, position.getY() + TILE_SIZE - 1f);
			GL11.glVertex2f(position.getX() + 1f, position.getY() + TILE_SIZE - 1f);
			GL11.glEnd();
			GL11.glBegin(GL11.GL_LINE_STRIP);
			GL11.glVertex2f(position.getX() + 46f, position.getY() + 51f);
			GL11.glVertex2f(position.getX() + 51f, position.getY() + 46f);
			GL11.glVertex2f(position.getX() + 60f, position.getY() + 59f);
			GL11.glEnd();
			if (count > 1) {
				GL11.glBegin(GL11.GL_QUADS);
				for (int index = 0; index < count - 1; index++) {
					float pipX = position.getX() + TILE_SIZE - 10f - index * 7f;
					float pipY = position.getY() + 4f;
					GL11.glVertex2f(pipX, pipY);
					GL11.glVertex2f(pipX + 5f, pipY);
					GL11.glVertex2f(pipX + 5f, pipY + 5f);
					GL11.glVertex2f(pipX, pipY + 5f);
				}
				GL11.glEnd();
			}
			GL11.glPopAttrib();
		}

		@Override
		public void processInput(List<InputEventAPI> events) {
			for (InputEventAPI event : events) {
				if (event.isConsumed()) continue;
				if (!event.isLMBDownEvent() || !position.containsEvent(event)) continue;
				if (mode == Mode.SINGLE) selection.clear();
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

	private class HoverPlugin extends BaseCustomUIPanelPlugin {

		private final Option option;

		private HoverPlugin(Option option) {
			this.option = option;
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (hoverPanel != null) drawRect(hoverPanel.getPosition(), HOVER_BACKGROUND, alphaMult);
		}
	}

	private static class HoverTarget {

		private final Option option;
		private final CustomPanelAPI tile;

		private HoverTarget(Option option, CustomPanelAPI tile) {
			this.option = option;
			this.tile = tile;
		}
	}
}
