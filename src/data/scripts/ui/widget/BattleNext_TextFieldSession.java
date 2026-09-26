package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class BattleNext_TextFieldSession {

	private enum Phase {
		IDLE,
		PRESSED,
		DRAGGING
	}

	private static final Map<BattleNext_TextField, Boolean> REGISTERED = new WeakHashMap<>();

	private static BattleNext_TextField focusedField;
	private static BattleNext_TextField pointerOwner;
	private static Phase phase = Phase.IDLE;
	private static float pointerX;
	private static float pointerY;
	private static long generation;

	private BattleNext_TextFieldSession() {
	}

	static void register(BattleNext_TextField field) {
		REGISTERED.put(field, Boolean.TRUE);
	}

	static void clearRegisteredFields() {
		finish(true);
		REGISTERED.clear();
	}

	static void focus(BattleNext_TextField field) {
		if (focusedField == field) {
			field.onFocusGained();
			return;
		}
		finish(true);
		focusedField = field;
		field.onFocusGained();
	}

	static void beginPointer(BattleNext_TextField field, InputEventAPI event) {
		boolean preserveSelection = focusedField == field && event.isShiftDown();
		finish(!preserveSelection);
		focusedField = field;
		field.onFocusGained();
		pointerOwner = field;
		phase = Phase.PRESSED;
		generation++;
		pointerX = event.getX();
		pointerY = event.getY();
		field.beginPointer(pointerX, pointerY, event.isShiftDown());
	}

	static boolean isFocused(BattleNext_TextField field) {
		return focusedField == field;
	}

	static boolean isDragging(BattleNext_TextField field) {
		return pointerOwner == field && phase == Phase.DRAGGING;
	}

	static float getPointerX() {
		return pointerX;
	}

	static float getPointerY() {
		return pointerY;
	}

	static void finish(boolean clearSelection) {
		BattleNext_TextField oldFocus = focusedField;
		BattleNext_TextField oldPointer = pointerOwner;
		focusedField = null;
		pointerOwner = null;
		phase = Phase.IDLE;
		generation++;
		if (oldPointer != null) {
			oldPointer.endPointer(clearSelection);
		}
		if (oldFocus != null && oldFocus != oldPointer) {
			oldFocus.endFocus(clearSelection);
		}
	}

	static void finishPointer() {
		BattleNext_TextField oldPointer = pointerOwner;
		pointerOwner = null;
		phase = Phase.IDLE;
		generation++;
		if (oldPointer != null) {
			oldPointer.endPointer(false);
		}
	}

	static boolean handleGlobalInput(List<InputEventAPI> events) {
		boolean handled = false;
		long activeGeneration = generation;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;

			if (event.isLMBDownEvent()) {
				BattleNext_TextField target = findTarget(event);
				if (target == null) {
					finish(true);
					continue;
				}
				beginPointer(target, event);
				activeGeneration = generation;
				event.consume();
				handled = true;
				continue;
			}

			if (event.isMouseMoveEvent() && activeGeneration == generation && pointerOwner != null
					&& (phase == Phase.PRESSED || phase == Phase.DRAGGING)) {
				pointerX = event.getX();
				pointerY = event.getY();
				pointerOwner.updateDragFromPointer(pointerX, pointerY, true);
				phase = Phase.DRAGGING;
				event.consume();
				handled = true;
				continue;
			}

			if (event.isLMBUpEvent() && activeGeneration == generation && pointerOwner != null
					&& (phase == Phase.PRESSED || phase == Phase.DRAGGING)) {
				pointerX = event.getX();
				pointerY = event.getY();
				pointerOwner.updateDragFromPointer(pointerX, pointerY, true);
				finishPointer();
				event.consume();
				handled = true;
				continue;
			}

			if (event.isKeyboardEvent() && (event.isKeyDownEvent() || event.isRepeat())
					&& focusedField != null) {
				BattleNext_TextField field = focusedField;
				field.processKeyboard(event);
				event.consume();
				handled = true;
			}
		}
		return handled;
	}

	private static BattleNext_TextField findTarget(InputEventAPI event) {
		for (BattleNext_TextField field : REGISTERED.keySet()) {
			if (field.isUsableForInput() && field.getPosition().containsEvent(event)) {
				return field;
			}
		}
		return null;
	}
}
