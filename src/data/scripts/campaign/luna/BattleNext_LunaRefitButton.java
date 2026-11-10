package data.scripts.campaign.luna;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.tools.BattleNext_Logger;
import lunalib.lunaRefit.BaseRefitButton;

public abstract class BattleNext_LunaRefitButton extends BaseRefitButton implements EveryFrameScript {

	private final String detachedLogId;
	private CustomPanelAPI background;
	private CustomPanelAPI content;
	private UIPanelAPI host;
	protected BattleNext_CampaignRefitContext openedContext;

	protected BattleNext_LunaRefitButton(String detachedLogId) {
		this.detachedLogId = detachedLogId;
	}

	protected final void captureContext(CustomPanelAPI backgroundPanel) throws Throwable {
		background = backgroundPanel;
		host = BattleNext_CampaignRefitContext.parentOf(backgroundPanel);
		if (host == null) throw new IllegalStateException("Refit panel is not attached to the campaign UI");
		openedContext = BattleNext_CampaignRefitContext.current();
	}

	protected final CustomPanelAPI attachContent(CustomUIPanelPlugin display) {
		content = background.createCustomPanel(background.getPosition().getWidth(), background.getPosition().getHeight(), display);
		background.addComponent(content).inTL(0f, 0f);
		return content;
	}

	@Override
	public final void onClick(FleetMemberAPI member, ShipVariantAPI variant, InputEventAPI event, MarketAPI market) {
		if (!event.isLMBEvent()) return;
		cleanup(true);
	}

	@Override
	public final void onPanelClose(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		cleanup(false);
	}

	@Override
	public final boolean isDone() {
		return false;
	}

	@Override
	public final boolean runWhilePaused() {
		return true;
	}

	@Override
	public final void advance(float amount) {
		if (background == null) return;
		if (Global.getSector().getCampaignUI().getCurrentCoreTab() != CoreUITabId.REFIT) {
			cleanup(true);
			return;
		}

		try {
			if (!BattleNext_CampaignRefitContext.childrenOf(host).contains(background)) {
				cleanup(false);
				return;
			}
			if (openedContext == null) return;
			if (!openedContext.equals(BattleNext_CampaignRefitContext.current())) {
				cleanup(true);
			}
		} catch (Throwable exception) {
			BattleNext_Logger.warn(getClass(), detachedLogId,
				BattleNext_Logger.fields().put("owns_background", background != null).put("owns_host", host != null).build(), exception);
			cleanup(true);
		}
	}

	protected final void dismiss() {
		closePanel();
		cleanup(true);
	}

	protected final void cleanup(boolean removeBackground) {
		closeDisplay();
		if (content != null) background.removeComponent(content);
		if (removeBackground && background != null && host != null) host.removeComponent(background);
		content = null;
		background = null;
		host = null;
		openedContext = null;
	}

	protected abstract void closeDisplay();
}
