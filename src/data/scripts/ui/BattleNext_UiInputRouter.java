package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_TextFieldSession;

import java.util.*;

/** Coordinates modal UI layers and their text-input scope for one root panel. */
public final class BattleNext_UiInputRouter {

	public interface Overlay {
		PositionAPI getBounds();

		boolean handleGlobalInput(List<InputEventAPI> events);

		void close();
	}

	private final BattleNext_TextFieldSession session = new BattleNext_TextFieldSession();

	public BattleNext_TextFieldSession getSession() { return session; }

	private final Deque<Overlay> overlays = new ArrayDeque<>();
	private final Map<Overlay, List<BattleNext_TextField>> modalFields = new IdentityHashMap<>();
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
		modalFields.computeIfAbsent(overlay, ignored -> new ArrayList<>());
		session.finish(true);
		session.setModalRegion(overlay.getBounds());
	}

	public void pop(Overlay overlay) {
		if (overlay == null) return;
		overlays.remove(overlay);
		modalFields.remove(overlay);
		updateModalRegion();
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
		modalFields.computeIfAbsent(overlay, ignored -> new ArrayList<>()).add(field);
		session.registerModalField(field);
	}

	public void releaseFocus() {
		session.finish(true);
	}

	public void clear() {
		while (!overlays.isEmpty()) overlays.pop().close();
		modalFields.clear();
		session.clearModalRegion();
		session.clearRegisteredFields();
	}

	public boolean isEmpty() {
		return overlays.isEmpty();
	}

	public boolean isTop(Overlay overlay) {
		return overlay != null && overlays.peek() == overlay;
	}

	private void updateModalRegion() {
		Overlay overlay = overlays.peek();
		if (overlay == null) session.clearModalRegion();
		else {
			session.setModalRegion(overlay.getBounds());
			for (BattleNext_TextField field : modalFields.getOrDefault(overlay, List.of())) {
				session.registerModalField(field);
			}
		}
	}
}
