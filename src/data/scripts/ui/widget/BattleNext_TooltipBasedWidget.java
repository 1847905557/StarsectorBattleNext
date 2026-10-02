package data.scripts.ui.widget;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.ui.BattleNext_UiInputRouter;

import java.util.ArrayList;
import java.util.List;

public abstract class BattleNext_TooltipBasedWidget {

	protected TooltipMakerAPI host;
	protected CustomPanelAPI panelOwner;
	protected BattleNext_UiInputRouter inputRouter;
	private final List<OwnedPanel> ownedPanels = new ArrayList<>();

	protected BattleNext_TooltipBasedWidget() {
		this(BattleNext_UiInputRouter.current());
	}

	protected BattleNext_TooltipBasedWidget(BattleNext_UiInputRouter inputRouter) {
		this.inputRouter = inputRouter;
	}

	public final void attach(TooltipMakerAPI host, CustomPanelAPI panelOwner) {
		close();
		this.host = host;
		this.panelOwner = panelOwner;
		buildContent(host, panelOwner);
	}

	public final void refreshContent() {
		if (host == null) return;
		clearOverlays();
		clearContent();
		clearOwnedPanels();
		buildContent(host, panelOwner);
	}

	public final void closeOverlays() {
		clearOverlays();
	}

	public final void close() {
		clearOverlays();
		clearContent();
		clearOwnedPanels();
		host = null;
		panelOwner = null;
	}

	public final boolean handleGlobalInput(List<InputEventAPI> events) {
		return handleWidgetInput(events);
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

	protected void clearOverlays() {
	}

	protected void clearContent() {
	}

	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		return false;
	}

	protected abstract void buildContent(TooltipMakerAPI host, CustomPanelAPI panelOwner);
}
