package data.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

/**
 * TextFieldAPI implementation with a clipped viewport and an editable caret.
 * The public TextFieldAPI has no horizontal scrolling state, so this widget
 * keeps the text model and viewport state itself.
 */
public final class BattleNext_TextField implements TextFieldAPI {

	private static final FocusState FOCUS = new FocusState();
	private static final DragState DRAG = new DragState();

	private static final Color DEFAULT_TEXT_COLOR = Color.WHITE;
	private static final Color DEFAULT_BACKGROUND = new Color(20, 48, 57, 210);
	private static final Color DEFAULT_BORDER = new Color(35, 105, 123, 220);
	private static final float CARET_WIDTH = 2f;
	public static final int RECOMMENDED_MAX_CHARS = 360;
	public static final float AUTO_SCROLL_START_SPEED = 40f;
	public static final float AUTO_SCROLL_MAX_SPEED = 160f;
	public static final float AUTO_SCROLL_ACCELERATION = 20f;

	private final CustomPanelAPI panel;
	private final TooltipMakerAPI textHost;
	private final LabelAPI textLabel;
	private final float width;
	private final float height;

	private String text = "";
	private int cursor;
	private int anchor;
	private int maxChars = -1;
	private float pad = 3f;
	private float scroll;
	private float blink;
	private float dragMouseX;
	private float dragMouseY;
	private boolean showCursor = true;
	private boolean dragging;
	private boolean limitByStringWidth;
	private boolean undoOnEscape = true;
	private boolean handleCtrlV = true;
	private boolean verticalCursor = true;
	private String undoText = "";
	private Color textColor = DEFAULT_TEXT_COLOR;
	private Color background = DEFAULT_BACKGROUND;
	private Color border = DEFAULT_BORDER;

	private static final class FocusState {
		private BattleNext_TextField current;
	}

	private static final class DragState {
		private BattleNext_TextField current;
	}

	private BattleNext_TextField(CustomPanelAPI panel, float width, float height) {
		this.panel = panel;
		this.width = width;
		this.height = height;
		this.textColor = Misc.getButtonTextColor();
		this.textHost = panel.createUIElement(width, height, false);
		this.textHost.setParaSmallInsignia();
		this.textHost.setParaFontColor(this.textColor);
		this.textLabel = textHost.addPara("", 0f);
		this.textLabel.setAlignment(Alignment.LMID);
		this.textLabel.setColor(this.textColor);
		textHost.setHeightSoFar(height);
		panel.addUIElement(textHost).inTL(0f, 0f);
		refreshVisual();
	}

	public static TextFieldAPI addTo(TooltipMakerAPI host, CustomPanelAPI owner,
			float width, float height, String value) {
		FieldPlugin plugin = new FieldPlugin();
		CustomPanelAPI panel = owner.createCustomPanel(width, height, plugin);
		BattleNext_TextField field = new BattleNext_TextField(panel, width, height);
		plugin.field = field;
		field.setText(value == null ? "" : value);
		host.addCustom(panel, 0f);
		return field;
	}

	public static TextFieldAPI addTo(TooltipMakerAPI host, CustomPanelAPI owner,
			float width, float height) {
		return addTo(host, owner, width, height, "");
	}

	public static void releaseFocusedField() {
		clearDragOwner();
		if (FOCUS.current != null) FOCUS.current.dragging = false;
		FOCUS.current = null;
	}

	private static void clearDragOwner() {
		if (DRAG.current != null) DRAG.current.dragging = false;
		DRAG.current = null;
	}

	private static void takeDragOwnership(BattleNext_TextField field) {
		if (DRAG.current != field) clearDragOwner();
		DRAG.current = field;
		field.dragging = true;
	}

	public static boolean handleGlobalInput(List<InputEventAPI> events) {
		BattleNext_TextField field = DRAG.current;
		if (field == null || !field.dragging) return false;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isMouseMoveEvent()) {
				field.updateDragFromPointer(event.getX(), event.getY(), true);
				event.consume();
				continue;
			}
			if (event.isLMBUpEvent()) {
				field.updateDragFromPointer(event.getX(), event.getY(), true);
				clearDragOwner();
				event.consume();
				return true;
			}
		}
		return true;
	}

	private boolean hasSelection() {
		return anchor != cursor;
	}

	private int selectionStart() {
		return Math.min(anchor, cursor);
	}

	private int selectionEnd() {
		return Math.max(anchor, cursor);
	}

	private void resetBlink() {
		blink = 0f;
	}

	private void notifyChanged() {
		resetBlink();
		refreshVisual();
	}

	private float getContentWidth() {
		return Math.max(1f, width - pad * 2f - CARET_WIDTH);
	}

	private float getMaxScroll() {
		return Math.max(0f, textLabel.computeTextWidth(text) - getContentWidth());
	}

	private void clampScroll() {
		scroll = Math.max(0f, Math.min(scroll, getMaxScroll()));
	}

	private void refreshVisual() {
		float contentWidth = getContentWidth();
		int safeCursor = Math.max(0, Math.min(cursor, text.length()));
		float cursorX = textLabel.computeTextWidth(text.substring(0, safeCursor));
		float totalWidth = textLabel.computeTextWidth(text);
		if (cursorX - scroll > contentWidth) scroll = cursorX - contentWidth;
		if (cursorX - scroll < 0f) scroll = cursorX;
		if (totalWidth <= contentWidth) scroll = 0f;
		scroll = Math.max(0f, Math.min(scroll, Math.max(0f, totalWidth - contentWidth)));

		textLabel.setText(text);
		textLabel.setColor(textColor);
		textLabel.getPosition().setSize(Math.max(1f, totalWidth + 4f), height);
		textLabel.getPosition().inLMid(pad - scroll);
	}

	private void replaceSelection(String replacement) {
		int start = selectionStart();
		int end = selectionEnd();
		String next = text.substring(0, start) + replacement + text.substring(end);
		if (maxChars >= 0 && next.length() > maxChars) return;
		text = next;
		cursor = start + replacement.length();
		anchor = cursor;
		notifyChanged();
	}

	private void deleteSelection() {
		if (hasSelection()) replaceSelection("");
	}

	private void moveCursor(int next, boolean extend) {
		cursor = Math.max(0, Math.min(text.length(), next));
		if (!extend) anchor = cursor;
		resetBlink();
		refreshVisual();
	}

	private void insert(char c) {
		if (!isValidChar(c)) return;
		if (limitByStringWidth && !hasSelection()
				&& textLabel.computeTextWidth(text + c) > width - pad * 2f) return;
		replaceSelection(String.valueOf(c));
	}

	private void deletePrevious() {
		if (hasSelection()) {
			deleteSelection();
			return;
		}
		if (cursor <= 0) return;
		text = text.substring(0, cursor - 1) + text.substring(cursor);
		cursor--;
		anchor = cursor;
		notifyChanged();
	}

	private void deleteNext() {
		if (hasSelection()) {
			deleteSelection();
			return;
		}
		if (cursor >= text.length()) return;
		text = text.substring(0, cursor) + text.substring(cursor + 1);
		anchor = cursor;
		notifyChanged();
	}

	private void copySelection() {
		if (!hasSelection()) return;
		try {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
					new StringSelection(text.substring(selectionStart(), selectionEnd())), null);
		} catch (IllegalStateException ignored) {
			// The game may temporarily own the clipboard. Editing remains usable.
		}
	}

	private void pasteClipboard() {
		String clipboard = readClipboard();
		if (clipboard == null) return;
		for (int i = 0; i < clipboard.length(); i++) {
			char c = clipboard.charAt(i);
			if (isValidChar(c)) insert(c);
		}
	}

	private String readClipboard() {
		try {
			Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
					.getData(DataFlavor.stringFlavor);
			return value instanceof String ? (String) value : null;
		} catch (IllegalStateException | HeadlessException | UnsupportedFlavorException | IOException ignored) {
			return null;
		}
	}

	private void processKeyboard(InputEventAPI event) {
		int key = event.getEventValue();
		boolean extend = event.isShiftDown();
		if (event.isCtrlDown() && key == Keyboard.KEY_A) {
			anchor = 0;
			cursor = text.length();
			refreshVisual();
			return;
		}
		if (event.isCtrlDown() && key == Keyboard.KEY_C) {
			copySelection();
			return;
		}
		if (event.isCtrlDown() && key == Keyboard.KEY_X) {
			copySelection();
			deleteSelection();
			return;
		}
		if (event.isCtrlDown() && key == Keyboard.KEY_V && handleCtrlV) {
			pasteClipboard();
			return;
		}
		switch (key) {
			case Keyboard.KEY_LEFT:
				moveCursor(cursor - 1, extend);
				break;
			case Keyboard.KEY_RIGHT:
				moveCursor(cursor + 1, extend);
				break;
			case Keyboard.KEY_HOME:
				moveCursor(0, extend);
				break;
			case Keyboard.KEY_END:
				moveCursor(text.length(), extend);
				break;
			case Keyboard.KEY_BACK:
				deletePrevious();
				break;
			case Keyboard.KEY_DELETE:
				deleteNext();
				break;
			case Keyboard.KEY_ESCAPE:
				if (undoOnEscape) setText(undoText);
				releaseFocus();
				break;
			default:
				char value = event.getEventChar();
				if (value != 0) insert(value);
		}
	}

	private int cursorForMouse(float mouseX) {
		clampScroll();
		float localX = mouseX - getPosition().getX() - pad + scroll;
		float totalWidth = textLabel.computeTextWidth(text);
		if (localX <= 0f) return 0;
		if (localX >= totalWidth) return text.length();
		int best = 0;
		float bestDistance = Float.MAX_VALUE;
		for (int i = 0; i <= text.length(); i++) {
			float distance = Math.abs(textLabel.computeTextWidth(text.substring(0, i)) - localX);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	private boolean updateDragFromPointer(float mouseX, float mouseY, boolean preserveAnchor) {
		dragMouseX = mouseX;
		dragMouseY = mouseY;
		float left = getPosition().getX() + pad;
		float right = getPosition().getX() + width - pad;
		float projectedX = Math.max(left, Math.min(mouseX, right));
		int next = cursorForMouse(projectedX);
		boolean changed = cursor != next;
		if (!preserveAnchor) anchor = next;
		if (changed) {
			cursor = next;
			resetBlink();
			refreshVisual();
		}
		return changed;
	}

	private void processMouseDown(InputEventAPI event) {
		if (!event.isLMBDownEvent() || !getPosition().containsEvent(event)) return;
		dragMouseX = event.getX();
		dragMouseY = event.getY();
		if (FOCUS.current != this) grabFocus(true);
		updateDragFromPointer(event.getX(), event.getY(), event.isShiftDown());
		takeDragOwnership(this);
		event.consume();
	}

	private void releaseFocus() {
		if (FOCUS.current == this) FOCUS.current = null;
		if (DRAG.current == this) clearDragOwner();
		else dragging = false;
	}

	private void drawRect(PositionAPI position, Color color, float alpha) {
		GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
				color.getBlue() / 255f, color.getAlpha() / 255f * alpha);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex2f(position.getX(), position.getY());
		GL11.glVertex2f(position.getX() + position.getWidth(), position.getY());
		GL11.glVertex2f(position.getX() + position.getWidth(), position.getY() + position.getHeight());
		GL11.glVertex2f(position.getX(), position.getY() + position.getHeight());
		GL11.glEnd();
	}

	private void pushTextScissor() {
		float xScale = Global.getSettings().getScreenWidthPixels()
				/ Math.max(1f, Global.getSettings().getScreenWidth());
		float yScale = Global.getSettings().getScreenHeightPixels()
				/ Math.max(1f, Global.getSettings().getScreenHeight());
		PositionAPI position = panel.getPosition();
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(
				(int) ((position.getX() + pad) * xScale),
				(int) (position.getY() * yScale),
				(int) (Math.max(1f, position.getWidth() - pad * 2f) * xScale),
				(int) (position.getHeight() * yScale));
	}

	private static final class FieldPlugin extends BaseCustomUIPanelPlugin {
		private BattleNext_TextField field;

		@Override
		public void renderBelow(float alphaMult) {
			if (field == null) return;
			GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_SCISSOR_BIT);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			field.drawRect(field.panel.getPosition(), field.background, alphaMult);
			field.pushTextScissor();
			if (field.hasSelection()) {
				float start = field.textLabel.computeTextWidth(field.text.substring(0, field.selectionStart()));
				float end = field.textLabel.computeTextWidth(field.text.substring(0, field.selectionEnd()));
				GL11.glColor4f(0.35f, 0.75f, 0.9f, 0.35f * alphaMult);
				GL11.glBegin(GL11.GL_QUADS);
				GL11.glVertex2f(field.panel.getPosition().getX() + field.pad + start - field.scroll, field.panel.getPosition().getY() + 3f);
				GL11.glVertex2f(field.panel.getPosition().getX() + field.pad + end - field.scroll, field.panel.getPosition().getY() + 3f);
				GL11.glVertex2f(field.panel.getPosition().getX() + field.pad + end - field.scroll, field.panel.getPosition().getY() + field.height - 3f);
				GL11.glVertex2f(field.panel.getPosition().getX() + field.pad + start - field.scroll, field.panel.getPosition().getY() + field.height - 3f);
				GL11.glEnd();
			}
		}

		@Override
		public void render(float alphaMult) {
			if (field == null) return;
			if (FOCUS.current == field && field.showCursor && ((int) (field.blink * 2f) % 2 == 0)) {
				GL11.glDisable(GL11.GL_TEXTURE_2D);
				GL11.glEnable(GL11.GL_BLEND);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				field.pushTextScissor();
				float x = field.panel.getPosition().getX() + field.pad
						+ field.textLabel.computeTextWidth(field.text.substring(0, field.cursor)) - field.scroll;
				GL11.glColor4f(field.textColor.getRed() / 255f, field.textColor.getGreen() / 255f,
						field.textColor.getBlue() / 255f, alphaMult);
				GL11.glBegin(GL11.GL_QUADS);
				GL11.glVertex2f(x, field.panel.getPosition().getY() + 4f);
				GL11.glVertex2f(x + CARET_WIDTH, field.panel.getPosition().getY() + 4f);
				GL11.glVertex2f(x + CARET_WIDTH, field.panel.getPosition().getY() + field.height - 4f);
				GL11.glVertex2f(x, field.panel.getPosition().getY() + field.height - 4f);
				GL11.glEnd();
			}
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
			GL11.glColor4f(field.border.getRed() / 255f, field.border.getGreen() / 255f,
					field.border.getBlue() / 255f, field.border.getAlpha() / 255f * alphaMult);
			GL11.glLineWidth(1f);
			GL11.glBegin(GL11.GL_LINE_LOOP);
			GL11.glVertex2f(field.panel.getPosition().getX(), field.panel.getPosition().getY());
			GL11.glVertex2f(field.panel.getPosition().getX() + field.width, field.panel.getPosition().getY());
			GL11.glVertex2f(field.panel.getPosition().getX() + field.width, field.panel.getPosition().getY() + field.height);
			GL11.glVertex2f(field.panel.getPosition().getX(), field.panel.getPosition().getY() + field.height);
			GL11.glEnd();
			GL11.glPopAttrib();
		}

		@Override
		public void advance(float amount) {
			if (FOCUS.current == field) {
				field.blink += amount;
				if (field.blink > 2f) field.blink = 0f;
			}
			if (field.dragging) {
				float mouseX = field.dragMouseX;
				float mouseY = field.dragMouseY;
				float left = field.getPosition().getX() + field.pad;
				float right = field.getPosition().getX() + field.width - field.pad;
				float overshoot = 0f;
				float direction = 0f;
				// Automatic scrolling uses the latest root InputEventAPI pointer position.
				if (mouseX < left) {
					direction = -1f;
					overshoot = left - mouseX;
				} else if (mouseX > right) {
					direction = 1f;
					overshoot = mouseX - right;
				}
				if (direction != 0f && field.textLabel.computeTextWidth(field.text)
						> field.getContentWidth()) {
					float speed = Math.min(AUTO_SCROLL_MAX_SPEED,
							AUTO_SCROLL_START_SPEED + overshoot * AUTO_SCROLL_ACCELERATION);
					float previousScroll = field.scroll;
					field.scroll += direction * speed * amount;
					field.clampScroll();
					boolean cursorChanged = field.updateDragFromPointer(mouseX, mouseY, true);
					if (field.scroll != previousScroll && !cursorChanged) {
						field.resetBlink();
						field.refreshVisual();
					}
				} else {
					field.updateDragFromPointer(mouseX, mouseY, true);
				}
			}
		}

		@Override
		public void processInput(List<InputEventAPI> events) {
			for (InputEventAPI event : events) {
				if (event.isConsumed()) continue;
				if (event.isMouseDownEvent()) {
					if (field.getPosition().containsEvent(event)) {
						field.processMouseDown(event);
					} else if (FOCUS.current == field) {
						field.releaseFocus();
					}
				} else if (FOCUS.current == field && event.isKeyboardEvent()
						&& (event.isKeyDownEvent() || event.isRepeat())) {
					field.processKeyboard(event);
					event.consume();
				}
			}
		}
	}

	@Override
	public PositionAPI getPosition() {
		return panel.getPosition();
	}
	@Override
	public void render(float alphaMult) {
		panel.render(alphaMult);
	}
	@Override
	public void processInput(List<InputEventAPI> events) {
		panel.processInput(events);
	}
	@Override
	public void advance(float amount) {
		panel.advance(amount);
	}
	@Override
	public void setOpacity(float opacity) {
		panel.setOpacity(opacity);
	}
	@Override
	public float getOpacity() {
		return panel.getOpacity();
	}

	@Override
	public void setPad(float pad) {
		this.pad = Math.max(0f, pad);
		refreshVisual();
	}
	@Override
	public LabelAPI getTextLabelAPI() {
		return textLabel;
	}
	@Override
	public void setMidAlignment() {
		textLabel.setAlignment(Alignment.LMID);
		refreshVisual();
	}
	@Override
	public void setColor(Color color) {
		textColor = color;
		textLabel.setColor(color);
	}
	@Override
	public void setBgColor(Color color) {
		background = color;
	}
	@Override
	public String getText() {
		return text;
	}
	@Override
	public void setText(String value) {
		text = value == null ? "" : value;
		if (maxChars >= 0 && text.length() > maxChars) text = text.substring(0, maxChars);
		cursor = text.length();
		anchor = cursor;
		undoText = text;
		notifyChanged();
	}
	@Override
	public boolean isValidChar(char c) {
		return c != 0 && c != '%' && c != '$';
	}
	@Override
	public boolean isLimitByStringWidth() {
		return limitByStringWidth;
	}
	@Override
	public void setLimitByStringWidth(boolean value) {
		limitByStringWidth = value;
	}
	@Override
	public boolean appendCharIfPossible(char c) {
		return appendCharIfPossible(c, true);
	}
	@Override
	public boolean appendCharIfPossible(char c, boolean withSound) {
		if (!isValidChar(c) || (maxChars >= 0 && !hasSelection() && text.length() >= maxChars)) return false;
		int oldCursor = cursor;
		insert(c);
		return oldCursor != cursor;
	}
	@Override
	public int getMaxChars() {
		return maxChars;
	}
	@Override
	public void setMaxChars(int maxChars) {
		this.maxChars = maxChars;
	}
	@Override
	public void deleteAll() {
		deleteAll(true);
	}
	@Override
	public void deleteAll(boolean withSound) {
		text = "";
		cursor = 0;
		anchor = 0;
		notifyChanged();
	}
	@Override
	public void deleteLastWord() {
		if (hasSelection()) {
			deleteSelection();
			return;
		}
		int end = cursor;
		while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		while (end > 0 && !Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		text = text.substring(0, end) + text.substring(cursor);
		cursor = end;
		anchor = end;
		notifyChanged();
	}
	@Override
	public void grabFocus() {
		grabFocus(true);
	}
	@Override
	public void grabFocus(boolean playSound) {
		FOCUS.current = this;
		undoText = text;
		resetBlink();
	}
	@Override
	public boolean hasFocus() {
		return FOCUS.current == this;
	}
	@Override
	public boolean isUndoOnEscape() {
		return undoOnEscape;
	}
	@Override
	public void setUndoOnEscape(boolean value) {
		undoOnEscape = value;
	}
	@Override
	public boolean isHandleCtrlV() {
		return handleCtrlV;
	}
	@Override
	public void setHandleCtrlV(boolean value) {
		handleCtrlV = value;
	}
	@Override
	public Color getBorderColor() {
		return border;
	}
	@Override
	public void setBorderColor(Color color) {
		border = color;
	}
	@Override
	public boolean isVerticalCursor() {
		return verticalCursor;
	}
	@Override
	public void setVerticalCursor(boolean value) {
		verticalCursor = value;
	}
	@Override
	public void hideCursor() {
		showCursor = false;
	}
	@Override
	public void showCursor() {
		showCursor = true;
	}
}
