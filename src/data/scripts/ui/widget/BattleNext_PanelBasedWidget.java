package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;

import java.util.ArrayList;
import java.util.List;

public abstract class BattleNext_PanelBasedWidget {

	protected CustomPanelAPI panel;
	protected BattleNext_UiInputRouter inputRouter;
	private final List<OwnedPanel> ownedPanels = new ArrayList<>();

	protected BattleNext_PanelBasedWidget() {
		this(BattleNext_UiInputRouter.current());
	}

	protected BattleNext_PanelBasedWidget(BattleNext_UiInputRouter inputRouter) {
		this.inputRouter = inputRouter;
	}

	public final CustomPanelAPI attach(CustomPanelAPI panel) {
		close();
		this.panel = panel;
		clearPageState();
		bindInput();
		buildContent(panel);
		return panel;
	}

	public final CustomPanelAPI refreshContent() {
		if (panel == null) return null;
		closeOverlays();
		clearContent();
		clearOwnedPanels();
		buildContent(panel);
		return panel;
	}

	public final void close() {
		closeOverlays();
		clearContent();
		clearOwnedPanels();
		panel = null;
		clearPageState();
	}

	public final void closeOverlays() {
		clearOverlays();
	}

	public final boolean handleGlobalInput(List<InputEventAPI> events) {
		return handleWidgetInput(events);
	}

	public final CustomPanelAPI getPanel() {
		return panel;
	}

	protected final <T extends CustomPanelAPI> T ownPanel(UIPanelAPI parent, T child) {
		ownedPanels.add(new OwnedPanel(parent, child));
		return child;
	}

	protected final void releasePanel(CustomPanelAPI child) {
		for (int index = ownedPanels.size() - 1; index >= 0; index--) {
			OwnedPanel owned = ownedPanels.get(index);
			if (owned.child != child) continue;
			owned.parent.removeComponent(child);
			ownedPanels.remove(index);
			return;
		}
	}

	private void clearOwnedPanels() {
		while (!ownedPanels.isEmpty()) {
			releasePanel(ownedPanels.get(ownedPanels.size() - 1).child);
		}
	}

	private record OwnedPanel(UIPanelAPI parent, CustomPanelAPI child) {
	}

	protected void bindInput() {
	}

	protected void clearPageState() {
	}

	protected void clearContent() {
	}

	protected void clearOverlays() {
	}

	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		return false;
	}

	protected abstract void buildContent(CustomPanelAPI panel);
}
