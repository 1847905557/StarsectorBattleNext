package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class BattleNext_TextFieldSession {

	private enum TextFieldPointerPhase {
		IDLE,
		PRESSED,
		DRAGGING
	}

	private static final Map<BattleNext_TextField, Boolean> REGISTERED = new WeakHashMap<>();
	private static final Map<BattleNext_TextField, List<TooltipMakerAPI>> VIEWPORTS = new WeakHashMap<>();
	private static PositionAPI modalRegion;

	private static BattleNext_TextField focusedField;
	private static BattleNext_TextField pointerOwner;
	private static TextFieldPointerPhase phase = TextFieldPointerPhase.IDLE;
	private static float pointerX;
	private static float pointerY;
	private static long generation;

	private BattleNext_TextFieldSession() {
	}

	static void register(BattleNext_TextField field, TooltipMakerAPI... viewports) {
		REGISTERED.put(field, Boolean.TRUE);
		List<TooltipMakerAPI> hosts = new ArrayList<>();
		if (viewports != null) {
			for (TooltipMakerAPI viewport : viewports) {
				if (viewport != null) hosts.add(viewport);
			}
		}
		VIEWPORTS.put(field, hosts);
	}

	static void clearRegisteredFields() {
		finish(true);
		REGISTERED.clear();
		VIEWPORTS.clear();
		modalRegion = null;
	}

	static void setModalRegion(PositionAPI position) {
		modalRegion = position;
	}

	static void clearModalRegion(PositionAPI position) {
		if (modalRegion == position) modalRegion = null;
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
		phase = TextFieldPointerPhase.PRESSED;
		generation++;
		pointerX = event.getX();
		pointerY = event.getY();
		field.beginPointer(pointerX, pointerY, event.isShiftDown());
	}

	static boolean isFocused(BattleNext_TextField field) {
		return focusedField == field;
	}

	static boolean isDragging(BattleNext_TextField field) {
		return pointerOwner == field && phase == TextFieldPointerPhase.DRAGGING;
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
		phase = TextFieldPointerPhase.IDLE;
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
		phase = TextFieldPointerPhase.IDLE;
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
			if (field.isUsableForInput() && isInsideModalRegion(event)
					&& field.getPosition().containsEvent(event) && isInsideViewports(field, event)) {
				return field;
			}
		}
		return null;
	}

	private static boolean isInsideModalRegion(InputEventAPI event) {
		return modalRegion == null || modalRegion.containsEvent(event);
	}

	private static boolean isInsideViewports(BattleNext_TextField field, InputEventAPI event) {
		for (TooltipMakerAPI host : VIEWPORTS.getOrDefault(field, Collections.emptyList())) {
			ScrollPanelAPI scroller = host.getExternalScroller();
			PositionAPI position = scroller == null ? host.getPosition() : scroller.getPosition();
			if (!position.containsEvent(event)) return false;
		}
		return true;
	}
}
