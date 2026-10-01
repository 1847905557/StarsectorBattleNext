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
import com.fs.starfarer.campaign.CampaignState;
import com.fs.state.AppDriver;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import lunalib.lunaRefit.BaseRefitButton;
import lunalib.lunaRefit.LunaRefitManager;
import org.boxutil.backends.reflect.BUtil_RefMethod;

import java.util.List;

public class BattleNext_LunaPinnedVariantRefitButton extends BaseRefitButton implements EveryFrameScript {

	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");
	private CustomPanelAPI background;
	private UIPanelAPI host;
	private RefitContext openedContext;
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
		return strings.get("pin_variant");
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
		tooltip.addPara(strings.get(member == null && variant != null ? "pin_module" : "pin_description"), 0f);
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
			host = (UIPanelAPI) invoke(backgroundPanel, "getParent");
			if (host == null) throw new IllegalStateException("Collection panel is not attached to the campaign UI");
			openedContext = currentContext();
			error = contextError(openedContext.member(), openedContext.variant());
			if (error.isEmpty()) {
				if (openedContext.member() != member || openedContext.variant() != variant) {
					throw new IllegalStateException("Refit selection changed before opening the collection panel");
				}
				selected = openedContext.variant();
			}
		} catch (Throwable exception) {
			error = strings.get("pin_context_error") + ": " + exception;
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
		BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("UI", "BattleNext_ui_");

		if (variant == null) return strings.get("refit_no_member");
		// Vanilla refit panels have no fleet member while editing a module.
		if (member == null) return strings.get("pin_module");
		if (variant.isFighter() || variant.getHullSpec() == null) return strings.get("invalid_variant");
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
			if (host == null || !((List<?>) invoke(host, "getChildrenCopy")).contains(background)) {
				cleanup();
				return;
			}
			if (openedContext == null) return;
			RefitContext current = currentContext();
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

	static RefitContext currentContext() throws Throwable {

		Object state = AppDriver.getInstance().getCurrentState();
		if (!(state instanceof CampaignState)) throw new IllegalStateException("Campaign refit is not active");
		Object dialog = invoke(state, "getEncounterDialog");
		Object core = dialog == null ? invoke(state, "getCore") : invoke(dialog, "getCoreUI");
		return contextFrom(core, dialog == null ? (UIPanelAPI) core : (UIPanelAPI) dialog);
	}

	static RefitContext contextFrom(Object core, UIPanelAPI host) throws Throwable {

		Object tab = invoke(core, "getCurrentTab");
		for (Object child : (List<?>) invoke(tab, "getChildrenCopy")) {
			if (!hasMethod(child, "syncWithCurrentVariant")) continue;
			FleetMemberAPI member = (FleetMemberAPI) invoke(child, "getMember");
			Object shipDisplay = invoke(child, "getShipDisplay");
			ShipVariantAPI variant = (ShipVariantAPI) invoke(shipDisplay, "getCurrentVariant");
			return new RefitContext(host, child, member, variant);
		}
		throw new IllegalStateException("Campaign refit panel is unavailable");
	}

	private static boolean hasMethod(Object target, String name) throws Throwable {
		for (Object method : target.getClass().getMethods()) {
			if (BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals(name)) return true;
		}
		return false;
	}

	private static Object invoke(Object target, String name) throws Throwable {
		for (Object method : target.getClass().getMethods()) {
			if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals(name)) continue;
			return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, target);
		}
		throw new NoSuchMethodException(name);
	}

	record RefitContext(UIPanelAPI host, Object refitPanel, FleetMemberAPI member, ShipVariantAPI variant) {

	}
}
