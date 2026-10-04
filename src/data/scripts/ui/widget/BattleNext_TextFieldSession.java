package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.*;
import java.util.function.BiPredicate;

public class BattleNext_TextFieldSession {

	private enum TextFieldPointerPhase {
		IDLE,
		PRESSED,
		DRAGGING
	}

	private final Map<BattleNext_TextField, List<TooltipMakerAPI>> registrations = new WeakHashMap<>();
	private final BiPredicate<BattleNext_TextField, InputEventAPI> inputScope;

	private BattleNext_TextField focusedField;
	private BattleNext_TextField pointerOwner;
	private TextFieldPointerPhase phase = TextFieldPointerPhase.IDLE;
	private float pointerX;
	private float pointerY;
	private long generation;

	public BattleNext_TextFieldSession(BiPredicate<BattleNext_TextField, InputEventAPI> inputScope) {
		this.inputScope = inputScope;
	}

	void register(BattleNext_TextField field, TooltipMakerAPI... viewports) {
		registrations.put(field, List.of(viewports));
	}

	public void clear() {
		finish(true);
		registrations.clear();
	}

	public void unregister(BattleNext_TextField field) {
		registrations.remove(field);
		if (pointerOwner == field) {
			pointerOwner = null;
			phase = TextFieldPointerPhase.IDLE;
			generation++;
			field.endPointer(true);
		}
		if (focusedField == field) {
			focusedField = null;
			field.endFocus(true);
		}
	}

	void focus(BattleNext_TextField field) {
		if (focusedField == field) {
			field.onFocusGained();
			return;
		}
		finish(true);
		focusedField = field;
		field.onFocusGained();
	}

	private void beginPointer(BattleNext_TextField field, InputEventAPI event) {
		boolean preserveSelection = focusedField == field && event.isShiftDown();
		finish(!preserveSelection);
		focusedField = field;
		field.onFocusGained();
		field.playFocusSound();
		pointerOwner = field;
		phase = TextFieldPointerPhase.PRESSED;
		generation++;
		pointerX = event.getX();
		pointerY = event.getY();
		field.beginPointer(pointerX, pointerY, event.isShiftDown());
	}

	boolean isFocused(BattleNext_TextField field) {
		return focusedField == field;
	}

	boolean isDragging(BattleNext_TextField field) {
		return pointerOwner == field && phase == TextFieldPointerPhase.DRAGGING;
	}

	float getPointerX() {
		return pointerX;
	}

	float getPointerY() {
		return pointerY;
	}

	public void finish(boolean clearSelection) {
		BattleNext_TextField oldFocus = focusedField;
		BattleNext_TextField oldPointer = pointerOwner;
		focusedField = null;
		pointerOwner = null;
		phase = TextFieldPointerPhase.IDLE;
		generation++;
		if (oldPointer != null) {
			oldPointer.endPointer(clearSelection);
		}
		if (oldFocus != null && oldFocus != oldPointer) {
			oldFocus.endFocus(clearSelection);
		}
	}

	private void finishPointer() {
		BattleNext_TextField oldPointer = pointerOwner;
		pointerOwner = null;
		phase = TextFieldPointerPhase.IDLE;
		generation++;
		if (oldPointer != null) {
			oldPointer.endPointer(false);
		}
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
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
					&& inputScope.test(pointerOwner, event)
					&& (phase == TextFieldPointerPhase.PRESSED || phase == TextFieldPointerPhase.DRAGGING)) {
				pointerX = event.getX();
				pointerY = event.getY();
				pointerOwner.updateDragFromPointer(pointerX, pointerY, true);
				phase = TextFieldPointerPhase.DRAGGING;
				event.consume();
				handled = true;
				continue;
			}

			if (event.isLMBUpEvent() && activeGeneration == generation && pointerOwner != null
					&& inputScope.test(pointerOwner, event)
					&& (phase == TextFieldPointerPhase.PRESSED || phase == TextFieldPointerPhase.DRAGGING)) {
				pointerX = event.getX();
				pointerY = event.getY();
				pointerOwner.updateDragFromPointer(pointerX, pointerY, true);
				finishPointer();
				event.consume();
				handled = true;
				continue;
			}

			if (event.isKeyboardEvent() && (event.isKeyDownEvent() || event.isRepeat())
					&& focusedField != null && inputScope.test(focusedField, event)) {
				BattleNext_TextField field = focusedField;
				field.processKeyboard(event);
				event.consume();
				handled = true;
			}
		}
		return handled;
	}

	public boolean hasInputState() {
		return focusedField != null || pointerOwner != null || !registrations.isEmpty();
	}

	private BattleNext_TextField findTarget(InputEventAPI event) {
		for (var registration : registrations.entrySet()) {
			BattleNext_TextField field = registration.getKey();
			if (field.isUsableForInput() && inputScope.test(field, event)
					&& field.getPosition().containsEvent(event) && isInsideViewports(registration.getValue(), event)) {
				return field;
			}
		}
		return null;
	}

	private boolean isInsideViewports(List<TooltipMakerAPI> viewports, InputEventAPI event) {
		for (TooltipMakerAPI host : viewports) {
			ScrollPanelAPI scroller = host.getExternalScroller();
			PositionAPI position = scroller == null ? host.getPosition() : scroller.getPosition();
			if (!position.containsEvent(event)) return false;
		}
		return true;
	}
}
