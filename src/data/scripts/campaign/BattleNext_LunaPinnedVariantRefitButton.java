package data.scripts.campaign;

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

import java.util.List;

public class BattleNext_LunaPinnedVariantRefitButton extends BaseRefitButton implements EveryFrameScript {

	private static final BattleNext_I18nSection refitStrings = BattleNext_I18nSection.getInstance("Refit", "BattleNext_refit_");
	private static final BattleNext_I18nSection variantStrings = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");
	private CustomPanelAPI background;
	private UIPanelAPI host;
	private BattleNext_CampaignRefitContext openedContext;
	private BattleNext_LunaPinnedVariantPanel display;

	private BattleNext_LunaPinnedVariantRefitButton() {

	}

	@LifecycleEntry
	public static void register() {
		if (LunaRefitManager.hasButtonOfClass(BattleNext_LunaPinnedVariantRefitButton.class)) return;
		LunaRefitManager.addRefitButton(new BattleNext_LunaPinnedVariantRefitButton());
	}

	@LifecycleEntry
	public static void onGameLoad() {
		BattleNext_LunaPinnedVariantRefitButton button = (BattleNext_LunaPinnedVariantRefitButton)
				LunaRefitManager.getFirstButtonOfClass(BattleNext_LunaPinnedVariantRefitButton.class);

		if (button == null) throw new IllegalStateException("Pinned variant refit button is not registered");
		button.cleanup();
		Global.getSector().removeTransientScript(button);
		Global.getSector().addTransientScript(button);
	}

	@Override
	public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
		return variantStrings.get("pin_current_action");
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
	public boolean hasPanel(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		return true;
	}

	@Override
	public boolean hasTooltip(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		return true;
	}

	@Override
	public void addTooltip(TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		tooltip.addPara(variantStrings.get(member == null && variant != null ? "pin_return_to_parent" : "pin_description"), 0f);
	}

	@Override
	public float getPanelWidth(FleetMemberAPI member, ShipVariantAPI variant) {
		return Global.getSettings().getScreenWidth() * 0.8f;
	}

	@Override
	public float getPanelHeight(FleetMemberAPI member, ShipVariantAPI variant) {
		return Global.getSettings().getScreenHeight() * 0.8f;
	}

	@Override
	public void onClick(FleetMemberAPI member, ShipVariantAPI variant, InputEventAPI event, MarketAPI market) {
		if (!event.isLMBEvent()) return;
		cleanup();
	}

	@Override
	public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {

		background = backgroundPanel;
		String error = "";
		ShipVariantAPI selected = null;
		try {
			host = BattleNext_CampaignRefitContext.parentOf(backgroundPanel);
			if (host == null) throw new IllegalStateException("Collection panel is not attached to the campaign UI");
			openedContext = BattleNext_CampaignRefitContext.current();
			error = contextError(openedContext.member(), openedContext.variant());
			if (error.isEmpty()) {
				if (openedContext.member() != member || openedContext.variant() != variant) {
					throw new IllegalStateException("Refit selection changed before opening the collection panel");
				}
				selected = openedContext.variant();
			}
		} catch (Throwable exception) {
			error = variantStrings.get("pin_context_error") + ": " + exception;
			Global.getLogger(getClass()).warn("Unable to capture campaign refit context", exception);
		}
		display = new BattleNext_LunaPinnedVariantPanel(this::dismiss);
		display.open(selected, error);
		float width = backgroundPanel.getPosition().getWidth();
		float height = backgroundPanel.getPosition().getHeight();
		CustomPanelAPI content = backgroundPanel.createCustomPanel(width, height, display);
		backgroundPanel.addComponent(content).inTL(0f, 0f);
		display.attach(content, width, height);
	}

	static String contextError(FleetMemberAPI member, ShipVariantAPI variant) {

		if (variant == null) return refitStrings.get("no_ship_selected");
		// Vanilla refit panels have no fleet member while editing a module.
		if (member == null) return variantStrings.get("pin_return_to_parent");
		if (variant.isFighter() || variant.getHullSpec() == null) return variantStrings.get("invalid_reference");
		return "";
	}

	@Override
	public void onPanelClose(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
		cleanup();
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
			cleanup();
			return;
		}
		try {
			if (host == null || !BattleNext_CampaignRefitContext.childrenOf(host).contains(background)) {
				cleanup();
				return;
			}
			if (openedContext == null) return;
			BattleNext_CampaignRefitContext current = BattleNext_CampaignRefitContext.current();
			if (!openedContext.equals(current)) {
				cleanup();
			}
		} catch (Throwable exception) {
			Global.getLogger(getClass()).warn("Campaign refit collection panel detached", exception);
			cleanup();
		}
	}

	private void dismiss() {
		closePanel();
		cleanup();
	}

	private void cleanup() {
		if (display != null) {
			display.close();
		}
		if (background != null && host != null) {
			host.removeComponent(background);
		}
		display = null;
		background = null;
		host = null;
		openedContext = null;
	}

}
