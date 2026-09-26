package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.tools.BattleNext_GeometryRenderer;
import org.lwjgl.input.Keyboard;

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

	private static final Color DEFAULT_TEXT_COLOR = Color.WHITE;
	private static final Color DEFAULT_BACKGROUND = new Color(20, 48, 57, 210);
	private static final Color DEFAULT_BORDER = new Color(35, 105, 123, 220);
	private static final Color SELECTION_COLOR = new Color(0.35f, 0.75f, 0.9f, 0.35f);
	private static final float CARET_WIDTH = 2f;
	public static final int RECOMMENDED_MAX_CHARS = 200;
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
	private boolean showCursor = true;
	private boolean limitByStringWidth;
	private boolean undoOnEscape = true;
	private boolean handleCtrlV = true;
	private boolean verticalCursor = true;
	private String undoText = "";
	private Color textColor = DEFAULT_TEXT_COLOR;
	private Color background = DEFAULT_BACKGROUND;
	private Color border = DEFAULT_BORDER;

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
		TextFieldPanelPlugin plugin = new TextFieldPanelPlugin();
		CustomPanelAPI panel = owner.createCustomPanel(width, height, plugin);
		BattleNext_TextField field = new BattleNext_TextField(panel, width, height);
		BattleNext_TextFieldSession.register(field);
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
		BattleNext_TextFieldSession.finish(true);
	}

	public static void clearRegisteredFields() {
		BattleNext_TextFieldSession.clearRegisteredFields();
	}

	public static boolean handleGlobalInput(List<InputEventAPI> events) {
		return BattleNext_TextFieldSession.handleGlobalInput(events);
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

	void processKeyboard(InputEventAPI event) {
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

	boolean updateDragFromPointer(float mouseX, float mouseY, boolean preserveAnchor) {
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

	void beginPointer(float mouseX, float mouseY, boolean extend) {
		updateDragFromPointer(mouseX, mouseY, extend);
	}

	void endPointer(boolean clearSelection) {
		if (clearSelection) anchor = cursor;
		resetBlink();
	}

	void endFocus(boolean clearSelection) {
		if (clearSelection) anchor = cursor;
		resetBlink();
	}

	boolean isUsableForInput() {
		PositionAPI position = getPosition();
		return position.getWidth() > 0f && position.getHeight() > 0f;
	}

	void onFocusGained() {
		undoText = text;
		resetBlink();
	}

	private void releaseFocus() {
		BattleNext_TextFieldSession.finish(true);
	}

	private static final class TextFieldPanelPlugin extends BaseCustomUIPanelPlugin {
		private BattleNext_TextField field;
		private BattleNext_GeometryRenderer.GeometryClipState clipState;

		@Override
		public void renderBelow(float alphaMult) {
			if (field == null) return;
			BattleNext_GeometryRenderer.fillRect(field.panel.getPosition(), field.background, alphaMult);
			PositionAPI position = field.panel.getPosition();
			clipState = BattleNext_GeometryRenderer.pushClip(position.getX() + field.pad, position.getY(),
					Math.max(1f, position.getWidth() - field.pad * 2f), position.getHeight());
			if (field.hasSelection()) {
				float start = field.textLabel.computeTextWidth(field.text.substring(0, field.selectionStart()));
				float end = field.textLabel.computeTextWidth(field.text.substring(0, field.selectionEnd()));
				BattleNext_GeometryRenderer.fillRect(field.panel.getPosition().getX() + field.pad + start - field.scroll,
						field.panel.getPosition().getY() + 3f, end - start, field.height - 6f,
						SELECTION_COLOR, alphaMult);
			}
		}

		@Override
		public void render(float alphaMult) {
			if (field == null) return;
			if (BattleNext_TextFieldSession.isFocused(field) && field.showCursor
					&& ((int) (field.blink * 2f) % 2 == 0)) {
				float x = field.panel.getPosition().getX() + field.pad
						+ field.textLabel.computeTextWidth(field.text.substring(0, field.cursor)) - field.scroll;
				BattleNext_GeometryRenderer.fillRect(x, field.panel.getPosition().getY() + 4f, CARET_WIDTH,
						field.height - 8f, field.textColor, alphaMult);
			}
			if (clipState != null) {
				BattleNext_GeometryRenderer.popClip(clipState);
				clipState = null;
			}
			BattleNext_GeometryRenderer.lineLoop(new float[]{
					field.panel.getPosition().getX(), field.panel.getPosition().getY(),
					field.panel.getPosition().getX() + field.width, field.panel.getPosition().getY(),
					field.panel.getPosition().getX() + field.width,
					field.panel.getPosition().getY() + field.height,
					field.panel.getPosition().getX(), field.panel.getPosition().getY() + field.height
			}, field.border, alphaMult, 1f);
		}

		@Override
		public void advance(float amount) {
			if (BattleNext_TextFieldSession.isFocused(field)) {
				field.blink += amount;
				if (field.blink > 2f) field.blink = 0f;
			}
			if (BattleNext_TextFieldSession.isDragging(field)) {
				float mouseX = BattleNext_TextFieldSession.getPointerX();
				float mouseY = BattleNext_TextFieldSession.getPointerY();
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
				if (event.isMouseDownEvent() && !field.getPosition().containsEvent(event)
						&& BattleNext_TextFieldSession.isFocused(field)) {
					field.releaseFocus();
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
		BattleNext_TextFieldSession.focus(this);
		undoText = text;
		resetBlink();
	}
	@Override
	public boolean hasFocus() {
		return BattleNext_TextFieldSession.isFocused(this);
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
