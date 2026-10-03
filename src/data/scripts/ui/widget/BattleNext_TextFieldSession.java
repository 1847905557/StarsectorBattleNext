package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.*;

public class BattleNext_TextFieldSession {

	private enum TextFieldPointerPhase {
		IDLE,
		PRESSED,
		DRAGGING
	}

	private final Map<BattleNext_TextField, Boolean> REGISTERED = new WeakHashMap<>();
	private final Map<BattleNext_TextField, List<TooltipMakerAPI>> VIEWPORTS = new WeakHashMap<>();
	private final Map<BattleNext_TextField, Boolean> MODAL_FIELDS = new WeakHashMap<>();
	private PositionAPI modalRegion;

	private BattleNext_TextField focusedField;
	private BattleNext_TextField pointerOwner;
	private TextFieldPointerPhase phase = TextFieldPointerPhase.IDLE;
	private float pointerX;
	private float pointerY;
	private long generation;

	public BattleNext_TextFieldSession() {
	}

	public void register(BattleNext_TextField field, TooltipMakerAPI... viewports) {
		REGISTERED.put(field, Boolean.TRUE);
		List<TooltipMakerAPI> hosts = new ArrayList<>();
		if (viewports != null) {
			for (TooltipMakerAPI viewport : viewports) {
				if (viewport != null) hosts.add(viewport);
			}
		}
		VIEWPORTS.put(field, hosts);
	}

	public void clearRegisteredFields() {
		finish(true);
		REGISTERED.clear();
		VIEWPORTS.clear();
		MODAL_FIELDS.clear();
		clearModalRegion();
	}

	public void unregister(BattleNext_TextField field) {
		REGISTERED.remove(field);
		VIEWPORTS.remove(field);
		MODAL_FIELDS.remove(field);
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

	public void setModalRegion(PositionAPI position) {
		modalRegion = position;
		MODAL_FIELDS.clear();
	}

	public void clearModalRegion(PositionAPI position) {
		if (modalRegion == position) clearModalRegion();
	}

	public void clearModalRegion() {
		modalRegion = null;
		MODAL_FIELDS.clear();
	}

	public void registerModalField(BattleNext_TextField field) {
		if (field != null) MODAL_FIELDS.put(field, Boolean.TRUE);
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

	void beginPointer(BattleNext_TextField field, InputEventAPI event) {
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

	void finishPointer() {
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

	public boolean hasInputState() {
		return focusedField != null || pointerOwner != null || !REGISTERED.isEmpty();
	}

	private BattleNext_TextField findTarget(InputEventAPI event) {
		for (BattleNext_TextField field : REGISTERED.keySet()) {
			if (field.isUsableForInput() && isAllowedByModalScope(field, event)
					&& field.getPosition().containsEvent(event) && isInsideViewports(field, event)) {
				return field;
			}
		}
		return null;
	}

	private boolean isAllowedByModalScope(BattleNext_TextField field, InputEventAPI event) {
		if (!isInsideModalRegion(event)) return false;
		return modalRegion == null || MODAL_FIELDS.containsKey(field);
	}

	private boolean isInsideModalRegion(InputEventAPI event) {
		return modalRegion == null || modalRegion.containsEvent(event);
	}

	private boolean isInsideViewports(BattleNext_TextField field, InputEventAPI event) {
		for (TooltipMakerAPI host : VIEWPORTS.getOrDefault(field, Collections.emptyList())) {
			ScrollPanelAPI scroller = host.getExternalScroller();
			PositionAPI position = scroller == null ? host.getPosition() : scroller.getPosition();
			if (!position.containsEvent(event)) return false;
		}
		return true;
	}
}
