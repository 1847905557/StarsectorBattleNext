package data.scripts.campaign.luna;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import data.scripts.tools.BattleNext_Logger;
import lunalib.lunaRefit.LunaRefitManager;

public class BattleNext_LunaPinnedVariantRefitButton extends BattleNext_LunaRefitButton {

	private static final BattleNext_I18nSection REFIT_STRINGS = BattleNext_I18nSection.getInstance("Refit", "BattleNext_refit_");
	private static final BattleNext_I18nSection VARIANT_STRINGS = BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_");

	private BattleNext_LunaPinnedVariantPanel display;

	private BattleNext_LunaPinnedVariantRefitButton() {
		super("variant.pin.refit.detached");
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
		button.cleanup(true);
		Global.getSector().removeTransientScript(button);
		Global.getSector().addTransientScript(button);
	}

	@Override
	public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
		return VARIANT_STRINGS.get("pin_current_action");
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
		tooltip.addPara(VARIANT_STRINGS.get(member == null && variant != null ? "pin_return_to_parent" : "pin_description"), 0f);
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
	public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {

		String error = "";
		ShipVariantAPI selected = null;
		try {
			captureContext(backgroundPanel);
			error = contextError(openedContext.member(), openedContext.variant());
			if (error.isEmpty()) {
				if (openedContext.member() != member || openedContext.variant() != variant) {
					throw new IllegalStateException("Refit selection changed before opening the collection panel");
				}
				selected = openedContext.variant();
			}
		} catch (Throwable exception) {
			error = VARIANT_STRINGS.get("pin_context_error") + ": " + exception;
			BattleNext_Logger.warn(BattleNext_LunaPinnedVariantRefitButton.class, "variant.pin.refit.context.unavailable",
				BattleNext_Logger.fields().put("member", member).put("variant", variant).build(), exception);
		}
		display = new BattleNext_LunaPinnedVariantPanel(this::dismiss);
		display.open(selected, error);
		CustomPanelAPI content = attachContent(display);
		display.attach(content, content.getPosition().getWidth(), content.getPosition().getHeight());
	}

	static String contextError(FleetMemberAPI member, ShipVariantAPI variant) {

		if (variant == null) return REFIT_STRINGS.get("no_ship_selected");
		// Vanilla refit panels have no fleet member while editing a module.
		if (member == null) return VARIANT_STRINGS.get("pin_return_to_parent");
		if (variant.isFighter() || variant.getHullSpec() == null) return VARIANT_STRINGS.get("invalid_reference");
		return "";
	}

	@Override
	protected void closeDisplay() {
		if (display != null) display.close();
		display = null;
	}

}
