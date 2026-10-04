package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_TextFieldSession;

import java.util.*;

/** Coordinates modal UI layers and their text-input scope for one root panel. */
public class BattleNext_UiInputRouter {

	public interface Overlay {
		PositionAPI getBounds();

		boolean handleGlobalInput(List<InputEventAPI> events);

		void close();
	}

	private final BattleNext_TextFieldSession session = new BattleNext_TextFieldSession(this::isTextInputAllowed);

	public BattleNext_TextFieldSession getSession() {
		return session;
	}

	private final Deque<Overlay> overlays = new ArrayDeque<>();
	private final Map<Overlay, Set<BattleNext_TextField>> modalFields = new IdentityHashMap<>();
	private static final ThreadLocal<BattleNext_UiInputRouter> CURRENT = new ThreadLocal<>();

	public static BattleNext_UiInputRouter current() {
		BattleNext_UiInputRouter router = CURRENT.get();
		if (router == null) {
			router = new BattleNext_UiInputRouter();
			CURRENT.set(router);
		}
		return router;
	}

	public static void activate(BattleNext_UiInputRouter router) {
		if (router == null) CURRENT.remove();
		else CURRENT.set(Objects.requireNonNull(router));
	}

	public void push(Overlay overlay) {
		if (overlay == null) return;
		overlays.remove(overlay);
		overlays.push(overlay);
		modalFields.computeIfAbsent(overlay, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
		session.finish(true);
	}

	public void pop(Overlay overlay) {
		if (overlay == null) return;
		if (overlays.peek() == overlay) {
			session.finish(true);
		}
		overlays.remove(overlay);
		modalFields.remove(overlay);
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		Overlay overlay = overlays.peek();
		if (overlay != null && overlay.handleGlobalInput(events)) return true;
		return session.hasInputState()
				&& session.handleGlobalInput(events);
	}

	public void registerModalField(BattleNext_TextField field) {
		Overlay overlay = overlays.peek();
		if (overlay == null || field == null) return;
		modalFields.get(overlay).add(field);
	}

	public void unregisterTextField(BattleNext_TextField field) {
		session.unregister(field);
		for (Set<BattleNext_TextField> fields : modalFields.values()) {
			fields.remove(field);
		}
	}

	public void releaseFocus() {
		session.finish(true);
	}

	public void clear() {
		while (!overlays.isEmpty()) overlays.pop().close();
		modalFields.clear();
		session.clear();
	}

	public boolean isEmpty() {
		return overlays.isEmpty();
	}

	public boolean isTop(Overlay overlay) {
		return overlay != null && overlays.peek() == overlay;
	}

	private boolean isTextInputAllowed(BattleNext_TextField field, InputEventAPI event) {
		Overlay overlay = overlays.peek();
		if (overlay == null) return true;
		PositionAPI bounds = overlay.getBounds();
		if (bounds == null) return true;
		return modalFields.get(overlay).contains(field)
				&& (!event.isLMBDownEvent() || bounds.containsEvent(event));
	}
}
