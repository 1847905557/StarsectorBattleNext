package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.ui.BattleNext_UiInputRouter;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * TextFieldAPI implementation with a clipped viewport and an editable caret.
 * The public TextFieldAPI has no horizontal scrolling state, so this widget
 * keeps the text model and viewport state itself.
 */
public class BattleNext_TextField extends BattleNext_TooltipBasedWidget implements TextFieldAPI {

	private static final Color DEFAULT_TEXT_COLOR = Color.WHITE;
	private static final Color DEFAULT_BACKGROUND = new Color(20, 48, 57, 210);
	private static final Color DEFAULT_BORDER = new Color(35, 105, 123, 220);
	private static final Color SELECTION_COLOR = new Color(0.35f, 0.75f, 0.9f, 0.35f);
	private static final float CARET_WIDTH = 2f;
	private static final float DEFAULT_TEXT_SIDE_PADDING = 3f;
	private static final float TEXT_LABEL_WIDTH_OVERHANG = 4f;
	private static final float SELECTION_VERTICAL_INSET = 3f;
	private static final float CARET_VERTICAL_INSET = 4f;
	private static final float CURSOR_BLINK_CYCLES_PER_SECOND = 2f;
	private static final int CURSOR_BLINK_PHASE_COUNT = 2;
	private static final float CURSOR_BLINK_PERIOD = 2f;
	static final String TYPE_SOUND_ID = "ui_typer_type";
	static final String BUZZ_SOUND_ID = "ui_typer_buzz";

	public static final int RECOMMENDED_MAX_CHARS = 200;
	public static final float AUTO_SCROLL_START_SPEED = 40f;
	public static final float AUTO_SCROLL_MAX_SPEED = 160f;
	public static final float AUTO_SCROLL_ACCELERATION = 20f;

	private final BattleNext_TextFieldSession session;
	private CustomPanelAPI panel;
	private TooltipMakerAPI textHost;
	private LabelAPI textLabel;
	private final float width;
	private final float height;
	private final TooltipMakerAPI[] viewports;
	private final Consumer<String> soundSink;

	private String text = "";
	private int cursor;
	private int anchor;
	private int maxChars = -1;
	private float pad = DEFAULT_TEXT_SIDE_PADDING;
	private float scroll;
	private float blink;
	private boolean showCursor = true;
	private boolean limitByStringWidth;
	private boolean undoOnEscape = true;
	private boolean handleCtrlV = true;
	private boolean verticalCursor = true;
	private String undoText = "";
	private char pendingHighSurrogate;
	private Color textColor = DEFAULT_TEXT_COLOR;
	private Color background = DEFAULT_BACKGROUND;
	private Color border = DEFAULT_BORDER;

	@Override
	protected void buildContent(TooltipMakerAPI host, CustomPanelAPI panelOwner) {
		TextFieldPanelPlugin plugin = new TextFieldPanelPlugin();
		panel = ownPanel(host, panelOwner.createCustomPanel(width, height, plugin));
		textHost = panel.createUIElement(width, height, false);
		textHost.setParaSmallInsignia();
		textHost.setParaFontColor(textColor);
		textLabel = textHost.addPara("", 0f);
		textLabel.setAlignment(Alignment.LMID);
		textLabel.setColor(textColor);
		textHost.setHeightSoFar(height);
		panel.addUIElement(textHost).inTL(0f, 0f);
		plugin.field = this;
		session.register(this, viewports);
		host.addCustom(panel, 0f);
		refreshVisual();
	}

	public TextFieldAPI getField() {
		return this;
	}

	public BattleNext_TextField(BattleNext_UiInputRouter router, CustomPanelAPI panelOwner, float width, float height,
			TooltipMakerAPI... viewports) {
		super(router);
		this.session = router.getSession();
		this.panel = null;
		this.width = width;
		this.height = height;
		this.viewports = viewports;
		this.soundSink = id -> Global.getSoundPlayer().playUISound(id, 1f, 1f);
		this.textColor = Misc.getButtonTextColor();
	}

	@Override
	protected void clearContent() {
		session.unregister(this);
		panel = null;
		textHost = null;
		textLabel = null;
	}

	public static void releaseFocusedField() {
		BattleNext_UiInputRouter.current().getSession().finish(true);
	}

	public static void clearRegisteredFields() {
		BattleNext_UiInputRouter.current().getSession().clearRegisteredFields();
	}

	public static boolean handleGlobalInputStatic(List<InputEventAPI> events) {
		return BattleNext_UiInputRouter.current().getSession().handleGlobalInput(events);
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
		textLabel.getPosition().setSize(Math.max(1f, totalWidth + TEXT_LABEL_WIDTH_OVERHANG), height);
		textLabel.getPosition().inLMid(pad - scroll);
	}

	private boolean replaceSelection(String replacement) {
		int start = selectionStart();
		int end = selectionEnd();
		String next = text.substring(0, start) + replacement + text.substring(end);
		if (maxChars >= 0 && next.length() > maxChars) return false;
		text = next;
		cursor = start + replacement.length();
		anchor = cursor;
		notifyChanged();
		return true;
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

	private boolean insertCodePoint(int codePoint) {
		if (!isAcceptedInputCodePoint(codePoint)) return false;
		String replacement = new String(Character.toChars(codePoint));
		if (limitByStringWidth && !hasSelection()
				&& textLabel.computeTextWidth(text + replacement) > width - pad * 2f) return false;
		return replaceSelection(replacement);
	}

	private void deletePrevious() {
		if (hasSelection()) {
			deleteSelection();
			return;
		}
		if (cursor <= 0) return;
		int previous = Character.offsetByCodePoints(text, cursor, -1);
		text = text.substring(0, previous) + text.substring(cursor);
		cursor = previous;
		anchor = cursor;
		notifyChanged();
	}

	private void deleteNext() {
		if (hasSelection()) {
			deleteSelection();
			return;
		}
		if (cursor >= text.length()) return;
		int next = Character.offsetByCodePoints(text, cursor, 1);
		text = text.substring(0, cursor) + text.substring(next);
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
		insertPastedText(clipboard == null ? "" : clipboard);
	}

	void insertPastedText(String clipboard) {
		boolean accepted = false;
		boolean rejected = false;
		for (int offset = 0; offset < clipboard.length();) {
			int codePoint = clipboard.codePointAt(offset);
			if (insertCodePoint(codePoint)) accepted = true;
			else rejected = true;
			offset += Character.charCount(codePoint);
		}
		if (accepted) soundSink.accept(TYPE_SOUND_ID);
		if (rejected || !accepted) soundSink.accept(BUZZ_SOUND_ID);
	}

	void playFocusSound() {
		soundSink.accept(BUZZ_SOUND_ID);
	}

	private void playEditSound(boolean accepted) {
		soundSink.accept(accepted ? TYPE_SOUND_ID : BUZZ_SOUND_ID);
	}

	static boolean isAcceptedInputCodePoint(int codePoint) {
		if (!Character.isValidCodePoint(codePoint) || codePoint == '%' || codePoint == '$') return false;
		int type = Character.getType(codePoint);
		return type != Character.CONTROL && type != Character.FORMAT
				&& type != Character.LINE_SEPARATOR && type != Character.PARAGRAPH_SEPARATOR
				&& type != Character.SURROGATE;
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
		if (event.isCtrlDown()) {
			pendingHighSurrogate = 0;
			if (!event.isAltDown() && key == Keyboard.KEY_A) {
				anchor = 0;
				cursor = text.length();
				refreshVisual();
			} else if (!event.isAltDown() && key == Keyboard.KEY_C) {
				copySelection();
			} else if (!event.isAltDown() && key == Keyboard.KEY_X) {
				copySelection();
				if (hasSelection()) {
					deleteSelection();
					playEditSound(true);
				}
			} else if (!event.isAltDown() && key == Keyboard.KEY_V && handleCtrlV) {
				pasteClipboard();
			}
			return;
		}
		switch (key) {
			case Keyboard.KEY_LEFT:
				pendingHighSurrogate = 0;
				moveCursor(cursor > 0 ? Character.offsetByCodePoints(text, cursor, -1) : 0, extend);
				break;
			case Keyboard.KEY_RIGHT:
				pendingHighSurrogate = 0;
				moveCursor(cursor < text.length() ? Character.offsetByCodePoints(text, cursor, 1) : cursor, extend);
				break;
			case Keyboard.KEY_HOME:
				pendingHighSurrogate = 0;
				moveCursor(0, extend);
				break;
			case Keyboard.KEY_END:
				pendingHighSurrogate = 0;
				moveCursor(text.length(), extend);
				break;
			case Keyboard.KEY_BACK:
				pendingHighSurrogate = 0;
				String beforeBack = text;
				deletePrevious();
				playEditSound(!text.equals(beforeBack));
				break;
			case Keyboard.KEY_DELETE:
				pendingHighSurrogate = 0;
				String beforeDelete = text;
				deleteNext();
				playEditSound(!text.equals(beforeDelete));
				break;
			case Keyboard.KEY_ESCAPE:
				pendingHighSurrogate = 0;
				soundSink.accept(TYPE_SOUND_ID);
				if (undoOnEscape) setText(undoText);
				releaseFocus();
				break;
			default:
				char value = event.getEventChar();
				if (pendingHighSurrogate != 0) {
					char high = pendingHighSurrogate;
					pendingHighSurrogate = 0;
					if (Character.isLowSurrogate(value)) {
						playEditSound(insertCodePoint(Character.toCodePoint(high, value)));
						break;
					}
				}
				if (Character.isHighSurrogate(value)) {
					pendingHighSurrogate = value;
				} else if (value != 0) {
					playEditSound(insertCodePoint(value));
				}
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
		for (int i = 0; i <= text.length(); i += i < text.length()
				? Character.charCount(text.codePointAt(i)) : 1) {
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
		pendingHighSurrogate = 0;
		updateDragFromPointer(mouseX, mouseY, extend);
	}

	void endPointer(boolean clearSelection) {
		pendingHighSurrogate = 0;
		if (clearSelection) anchor = cursor;
		resetBlink();
	}

	void endFocus(boolean clearSelection) {
		pendingHighSurrogate = 0;
		if (clearSelection) anchor = cursor;
		resetBlink();
	}

	boolean isUsableForInput() {
		PositionAPI position = getPosition();
		return position.getWidth() > 0f && position.getHeight() > 0f;
	}

	void onFocusGained() {
		pendingHighSurrogate = 0;
		undoText = text;
		resetBlink();
	}

	private void releaseFocus() {
		session.finish(true);
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
						field.panel.getPosition().getY() + SELECTION_VERTICAL_INSET, end - start,
						field.height - SELECTION_VERTICAL_INSET * 2f,
						SELECTION_COLOR, alphaMult);
			}
		}

		@Override
		public void render(float alphaMult) {
			if (field == null) return;
			if (field.session.isFocused(field) && field.showCursor
					&& ((int) (field.blink * CURSOR_BLINK_CYCLES_PER_SECOND)
							% CURSOR_BLINK_PHASE_COUNT == 0)) {
				float x = field.panel.getPosition().getX() + field.pad
						+ field.textLabel.computeTextWidth(field.text.substring(0, field.cursor)) - field.scroll;
				BattleNext_GeometryRenderer.fillRect(x,
						field.panel.getPosition().getY() + CARET_VERTICAL_INSET, CARET_WIDTH,
						field.height - CARET_VERTICAL_INSET * 2f, field.textColor, alphaMult);
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
			if (field.session.isFocused(field)) {
				field.blink += amount;
				if (field.blink > CURSOR_BLINK_PERIOD) field.blink = 0f;
			}
			if (field.session.isDragging(field)) {
				float mouseX = field.session.getPointerX();
				float mouseY = field.session.getPointerY();
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
						&& field.session.isFocused(field)) {
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
		pendingHighSurrogate = 0;
		text = value == null ? "" : value;
		if (maxChars >= 0 && text.length() > maxChars) {
			int end = maxChars;
			if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))
					&& Character.isLowSurrogate(text.charAt(end))) end--;
			text = text.substring(0, end);
		}
		cursor = text.length();
		anchor = cursor;
		undoText = text;
		notifyChanged();
	}

	@Override
	public boolean isValidChar(char c) {
		return isAcceptedInputCodePoint(c);
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
		boolean accepted = insertCodePoint(c);
		if (withSound) playEditSound(accepted);
		return accepted;
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
		boolean changed = !text.isEmpty();
		text = "";
		cursor = 0;
		anchor = 0;
		notifyChanged();
		if (withSound) playEditSound(changed);
	}

	@Override
	public void deleteLastWord() {
		if (hasSelection()) {
			deleteSelection();
			playEditSound(true);
			return;
		}
		String previous = text;
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
		playEditSound(!text.equals(previous));
	}

	@Override
	public void grabFocus() {
		grabFocus(true);
	}

	@Override
	public void grabFocus(boolean playSound) {
		session.focus(this);
		if (playSound) playFocusSound();
		undoText = text;
		resetBlink();
	}

	@Override
	public boolean hasFocus() {
		return session.isFocused(this);
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

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}
}
