package data.scripts.ui.widget;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiInputRouter;

public abstract class BattleNext_TooltipBasedWidget extends BattleNext_Widget {

	protected TooltipMakerAPI host;
	protected CustomPanelAPI panelOwner;

	protected BattleNext_TooltipBasedWidget() {
		this(BattleNext_UiInputRouter.current());
	}

	protected BattleNext_TooltipBasedWidget(BattleNext_UiInputRouter inputRouter) {
		super(inputRouter);
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

	@Override
	public final void close() {
		clearOverlays();
		clearContent();
		clearOwnedPanels();
		host = null;
		panelOwner = null;
	}

	protected abstract void buildContent(TooltipMakerAPI host, CustomPanelAPI panelOwner);
}
