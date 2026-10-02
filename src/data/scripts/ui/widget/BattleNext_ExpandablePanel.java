package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.awt.Color;
import java.util.List;
import java.util.function.Consumer;
import org.lwjgl.input.Mouse;

public class BattleNext_ExpandablePanel {

	public static final float HEADER_HEIGHT = 40f;
	public static final float ICON_SIZE = 32f;
	public static final float ROW_GAP = 4f;
	private static final float MISSING_ICON_SIZE = 24f;

	private static final Color BODY_BACKGROUND = new Color(0, 0, 0, 150);
	private static final float METRICS_MIN_WIDTH = 155f;
	private static final float METRICS_MAX_WIDTH = 260f;

	public record Header(String primarySprite, boolean secondarySlot, String secondarySprite,
			String firstLine, String secondLine, String rightFirstLine, String rightSecondLine) { }

	public record HeaderLayout(float textX, float textWidth, float metricsX, float metricsWidth) { }

	private BattleNext_ExpandablePanel() {
	}

	public static void add(TooltipMakerAPI list, CustomPanelAPI owner, float width,
			boolean expanded, float bodyHeight, Header header, Runnable onToggle,
			Consumer<Object> onBodyAction, Consumer<CustomPanelAPI> buildBody) {
		CustomPanelAPI root = owner.createCustomPanel(width, panelHeight(expanded, bodyHeight),
				new BaseCustomUIPanelPlugin());
		HeaderPlugin headerPlugin = new HeaderPlugin(expanded, list, onToggle);
		CustomPanelAPI top = root.createCustomPanel(width, HEADER_HEIGHT, headerPlugin);
		headerPlugin.setPosition(top.getPosition());
		addHeaderContent(top, width, header, headerPlugin);
		root.addComponent(top).inTL(0f, 0f);
		if (expanded) {
			BodyPlugin bodyPlugin = new BodyPlugin(onBodyAction);
			CustomPanelAPI body = root.createCustomPanel(width, bodyHeight, bodyPlugin);
			bodyPlugin.setPosition(body.getPosition());
			buildBody.accept(body);
			root.getPosition().setSize(width, panelHeight(true, body.getPosition().getHeight()));
			top.getPosition().inTL(0f, 0f);
			root.addComponent(body).inTL(0f, HEADER_HEIGHT);
		}
		list.addCustom(root, ROW_GAP);
	}

	public static HeaderLayout headerLayout(float width, boolean secondarySlot, boolean rightSummary) {
		float textX = secondarySlot ? ICON_SIZE * 2f + 12f : ICON_SIZE + 8f;
		float metricsWidth = rightSummary
				? Math.min(METRICS_MAX_WIDTH, Math.max(METRICS_MIN_WIDTH, width * 0.28f)) : 0f;
		float metricsX = rightSummary ? width - metricsWidth - 4f : width;
		return new HeaderLayout(textX, Math.max(1f, metricsX - textX - 8f), metricsX, metricsWidth);
	}

	public static float panelHeight(boolean expanded, float bodyHeight) {
		return HEADER_HEIGHT + (expanded ? bodyHeight : 0f);
	}

	public static Color headerBackground(boolean expanded, Color panelColor) {
		return expanded ? panelColor : panelColor.darker();
	}

	private static void addHeaderContent(CustomPanelAPI top, float width,
			Header header, HeaderPlugin plugin) {
		plugin.primarySprite = loadSprite(header.primarySprite());
		if (header.secondarySlot()) plugin.secondarySprite = loadSprite(header.secondarySprite());
		boolean rightSummary = !header.rightFirstLine().isEmpty() || !header.rightSecondLine().isEmpty();
		HeaderLayout layout = headerLayout(width, plugin.secondarySprite != null, rightSummary);
		TooltipMakerAPI names = top.createUIElement(layout.textWidth(), HEADER_HEIGHT, false);
		names.addPara(names.shortenString(header.firstLine(), Math.max(1f, layout.textWidth() - 6f)), 3f);
		if (!header.secondLine().isEmpty()) {
			names.addPara(names.shortenString(header.secondLine(), Math.max(1f, layout.textWidth() - 6f)), 1f);
		}
		top.addUIElement(names).inTL(layout.textX(), 0f);
		if (rightSummary) {
			TooltipMakerAPI metrics = top.createUIElement(layout.metricsWidth(), HEADER_HEIGHT, false);
			metrics.addPara(metrics.shortenString(header.rightFirstLine(), layout.metricsWidth() - 6f), 3f);
			metrics.addPara(metrics.shortenString(header.rightSecondLine(), layout.metricsWidth() - 6f), 1f);
			top.addUIElement(metrics).inTL(layout.metricsX(), 0f);
		}
	}

	private static SpriteAPI loadSprite(String path) {
		if (path == null || path.isBlank()) return null;
		try {
			return Global.getSettings().getSprite(path);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	static boolean hoverEntered(boolean wasHovered, boolean isInside) {
		return isInside && !wasHovered;
	}

	static boolean contains(PositionAPI position, float x, float y) {
		return position != null && x >= position.getX() && x < position.getX() + position.getWidth()
				&& y >= position.getY() && y < position.getY() + position.getHeight();
	}

	static boolean handleHeaderInput(List<InputEventAPI> events, PositionAPI position,
			PositionAPI visible, Runnable playPressedSound, Runnable onToggle) {
		for (InputEventAPI event : events) {
			if (event.isConsumed() || !event.isLMBDownEvent()
					|| !position.containsEvent(event) || !visible.containsEvent(event)) continue;
			event.consume();
			playPressedSound.run();
			onToggle.run();
			return true;
		}
		return false;
	}

	private static class HeaderPlugin extends BaseCustomUIPanelPlugin {
		private final boolean expanded;
		private final TooltipMakerAPI viewport;
		private final Runnable onToggle;
		private PositionAPI position;
		private boolean hovered;
		private SpriteAPI primarySprite;
		private SpriteAPI secondarySprite;

		private HeaderPlugin(boolean expanded, TooltipMakerAPI viewport, Runnable onToggle) {
			this.expanded = expanded;
			this.viewport = viewport;
			this.onToggle = onToggle;
		}

		private void setPosition(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (position == null) return;
			BattleNext_GeometryRenderer.fillRect(position,
					headerBackground(expanded, BattleNext_UiTheme.PANEL), alphaMult);
			BattleNext_GeometryRenderer.lineLoop(new float[]{
					position.getX(), position.getY(),
					position.getX() + position.getWidth(), position.getY(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight(),
					position.getX(), position.getY() + position.getHeight()
			}, BattleNext_UiTheme.PANEL, alphaMult, expanded ? 2f : 1f);
		}

		@Override
		public void render(float alphaMult) {
			if (position == null) return;
			float centerY = position.getY() + HEADER_HEIGHT * 0.5f;
			float primaryCenterX = position.getX() + ICON_SIZE * 0.5f;
			if (primarySprite == null) {
				BattleNext_PageLayout.renderMissingIcon(BattleNext_PageLayout.missingIcon(),
						primaryCenterX - MISSING_ICON_SIZE * 0.5f,
						centerY - MISSING_ICON_SIZE * 0.5f, MISSING_ICON_SIZE, alphaMult);
			} else {
				BattleNext_PageLayout.renderFittedSprite(primarySprite,
						primaryCenterX, centerY, ICON_SIZE, ICON_SIZE, alphaMult);
			}
			if (secondarySprite != null) {
				float secondaryCenterX = position.getX() + ICON_SIZE * 1.5f + 4f;
				BattleNext_PageLayout.renderFittedSprite(secondarySprite,
						secondaryCenterX, centerY, ICON_SIZE, ICON_SIZE, alphaMult);
			}
		}

		@Override
		public void processInput(List<InputEventAPI> events) {
			if (position != null) {
				handleHeaderInput(events, position, visiblePosition(),
						() -> Global.getSoundPlayer().playUISound(BattleNext_UiTheme.BUTTON_PRESSED_SOUND_ID, 1f, 1f),
						onToggle);
			}
		}

		@Override
		public void advance(float amount) {
			if (position == null) return;
			float scale = Global.getSettings().getScreenScaleMult();
			float x = Mouse.getX() / scale;
			float y = Mouse.getY() / scale;
			boolean inside = contains(position, x, y) && contains(visiblePosition(), x, y);
			if (hoverEntered(hovered, inside)) {
				Global.getSoundPlayer().playUISound(BattleNext_UiTheme.BUTTON_HOVER_SOUND_ID, 1f, 1f);
			}
			hovered = inside;
		}

		private PositionAPI visiblePosition() {
			ScrollPanelAPI scroller = viewport.getExternalScroller();
			return scroller == null ? viewport.getPosition() : scroller.getPosition();
		}
	}

	static class BodyPlugin extends BaseCustomUIPanelPlugin {
		private final Consumer<Object> onAction;
		private PositionAPI position;

		BodyPlugin(Consumer<Object> onAction) {
			this.onAction = onAction;
		}

		private void setPosition(PositionAPI position) {
			this.position = position;
		}

		@Override
		public void buttonPressed(Object buttonId) {
			onAction.accept(buttonId);
		}

		@Override
		public void renderBelow(float alphaMult) {
			if (position == null) return;
			BattleNext_GeometryRenderer.fillRect(position, BODY_BACKGROUND, alphaMult);
			BattleNext_GeometryRenderer.lineLoop(new float[]{
					position.getX(), position.getY(),
					position.getX() + position.getWidth(), position.getY(),
					position.getX() + position.getWidth(), position.getY() + position.getHeight(),
					position.getX(), position.getY() + position.getHeight()
			}, BattleNext_UiTheme.PANEL, alphaMult, 2f);
		}
	}
}
