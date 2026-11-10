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

public class BattleNext_LunaFleetSaveRefitButton extends BattleNext_LunaRefitButton {

	private final BattleNext_I18nSection fleetStrings = BattleNext_I18nSection.getInstance("Fleet", "BattleNext_fleet_");
	private BattleNext_LunaFleetSavePanel display;

	private BattleNext_LunaFleetSaveRefitButton() {
		super("fleet.player.refit.detached");
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
		return Math.min(400f, Global.getSettings().getScreenHeight() * 0.8f);
	}

	@Override
	public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {

		String error = "";
		try {
			captureContext(backgroundPanel);
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
		CustomPanelAPI content = attachContent(display);
		display.attach(content, content.getPosition().getWidth(), content.getPosition().getHeight());
	}

	@Override
	protected void closeDisplay() {
		if (display != null) display.close();
		display = null;
	}

}
