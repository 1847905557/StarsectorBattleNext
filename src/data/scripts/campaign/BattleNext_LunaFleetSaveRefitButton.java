package data.scripts.campaign;

import data.scripts.tools.BattleNext_Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import lunalib.lunaRefit.BaseRefitButton;
import lunalib.lunaRefit.LunaRefitManager;

public class BattleNext_LunaFleetSaveRefitButton extends BaseRefitButton implements EveryFrameScript {

	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private CustomPanelAPI background;
	private CustomPanelAPI content;
	private UIPanelAPI host;
	private BattleNext_CampaignRefitContext openedContext;
	private BattleNext_LunaFleetSavePanel display;

	private BattleNext_LunaFleetSaveRefitButton() {

	}

	@LifecycleEntry
	public static void register() {
		if (LunaRefitManager.hasButtonOfClass(BattleNext_LunaFleetSaveRefitButton.class)) return;
		LunaRefitManager.addRefitButton(new BattleNext_LunaFleetSaveRefitButton());
	}

	@LifecycleEntry
	public static void onGameLoad() {
		BattleNext_LunaFleetSaveRefitButton button = (BattleNext_LunaFleetSaveRefitButton)
				LunaRefitManager.getFirstButtonOfClass(BattleNext_LunaFleetSaveRefitButton.class);
		if (button == null) throw new IllegalStateException("Player fleet refit button is not registered");
		button.cleanup(true);
		Global.getSector().removeTransientScript(button);
		Global.getSector().addTransientScript(button);
	}

	@Override
	public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
		return fleetStrings.get("player_save_action");
	}

	@Override
	public int getOrder(FleetMemberAPI member, ShipVariantAPI variant) {
		return 101;
	}

	@Override
	public String getIconName(FleetMemberAPI member, ShipVariantAPI variant) {
		return "graphics/icons/missions/analyze_entity.png";
	}

	@Override
	public boolean shouldShow(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		return true;
	}

	@Override
	public boolean isClickable(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		if (member == null || variant == null || variant.isFighter()) return false;
		return Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().contains(member);
	}

	@Override
	public boolean hasPanel(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		return true;
	}

	@Override
	public boolean hasTooltip(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		return true;
	}

	@Override
	public void addTooltip(TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		tooltip.addPara(fleetStrings.get(member == null && variant != null ? "player_save_return_to_parent"
				: isClickable(member, variant, market) ? "player_save_description" : "player_save_context_error"), 0f);
	}

	@Override
	public float getPanelWidth(FleetMemberAPI member, ShipVariantAPI variant) {
		return Math.min(560f, Global.getSettings().getScreenWidth() * 0.8f);
	}

	@Override
	public float getPanelHeight(FleetMemberAPI member, ShipVariantAPI variant) {
		return Math.min(300f, Global.getSettings().getScreenHeight() * 0.8f);
	}

	@Override
	public void onClick(FleetMemberAPI member, ShipVariantAPI variant, InputEventAPI event, MarketAPI market) {
		if (!event.isLMBEvent()) return;
		cleanup(true);
	}

	@Override
	public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {

		background = backgroundPanel;
		String error = "";
		try {
			host = BattleNext_CampaignRefitContext.parentOf(backgroundPanel);
			if (host == null) throw new IllegalStateException("Fleet save panel is not attached to the campaign UI");
			openedContext = BattleNext_CampaignRefitContext.current();
			if (openedContext.member() != member || openedContext.variant() != variant) {
				throw new IllegalStateException("Refit selection changed before opening the fleet save panel");
			}
			if (!isClickable(member, variant, market)) {
				error = fleetStrings.get(member == null ? "player_save_return_to_parent" : "player_save_context_error");
			}
		} catch (Throwable exception) {
			error = fleetStrings.get("player_save_context_error") + ": " + exception;
			BattleNext_Logger.warn(BattleNext_LunaFleetSaveRefitButton.class, "fleet.player.refit.context.unavailable",
				BattleNext_Logger.fields().put("member", member).put("variant", variant).build(), exception);
		}
		display = new BattleNext_LunaFleetSavePanel(this::dismiss);
		display.open(member, variant, error);
		float width = backgroundPanel.getPosition().getWidth();
		float height = backgroundPanel.getPosition().getHeight();
		content = backgroundPanel.createCustomPanel(width, height, display);
		backgroundPanel.addComponent(content).inTL(0f, 0f);
		display.attach(content, width, height);
	}

	@Override
	public void onPanelClose(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		cleanup(false);
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return true;
	}

	@Override
	public void advance(float amount) {
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
			BattleNext_Logger.warn(BattleNext_LunaFleetSaveRefitButton.class, "fleet.player.refit.detached",
				BattleNext_Logger.fields().put("owns_background", background != null).put("owns_host", host != null).build(), exception);
			cleanup(true);
		}
	}

	private void dismiss() {
		closePanel();
		cleanup(true);
	}

	private void cleanup(boolean removeBackground) {
		if (display != null) display.close();
		if (content != null) background.removeComponent(content);
		if (removeBackground && background != null && host != null) host.removeComponent(background);
		display = null;
		content = null;
		background = null;
		host = null;
		openedContext = null;
	}
}
