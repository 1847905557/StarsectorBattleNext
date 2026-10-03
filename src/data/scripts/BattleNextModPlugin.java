package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import data.missions.BattleNext_MissionRestrictedUnlock;
import data.order.BattleNext_OrderRegistry;
import data.restriction.BattleNext_RestrictionRegistry;
import data.scripts.campaign.BattleNext_LunaFleetSaveRefitButton;
import data.scripts.campaign.BattleNext_LunaPinnedVariantRefitButton;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_Settings;

public class BattleNextModPlugin extends BaseModPlugin {

	public static final String CLASS_MARK = "BattleNextModPlugin";

	private boolean afterApplicationLoad = true;
	private boolean afterNewGameLoad = true;

	@Override
	public void onApplicationLoad() {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("mod_id", "AI_Battles_Next").build();
		BattleNext_Logger.operationStarted(BattleNextModPlugin.class, "application.load", input);
		try {

			BattleNext_Settings.load();
			BattleNext_LunaPinnedVariantRefitButton.register();
			BattleNext_LunaFleetSaveRefitButton.register();

			if (Global.getSettings().getModManager().isModEnabled("aibattles")) {
				throw new IllegalArgumentException("请关闭旧版 AI Battles，只开启最新的 AI Battles Next");
			}

			afterApplicationLoad = false;
			onDevModeF8Reload();
			afterApplicationLoad = true;
			BattleNext_Logger.operationSucceeded(BattleNextModPlugin.class, "application.load",
					BattleNext_Logger.fields().put("mod_id", "AI_Battles_Next").build());
		} catch (RuntimeException exception) {
			throw exception;
		}
	}

	@Override
	public void onNewGame() {

		// add procgen names here

		afterNewGameLoad = false;
		onGameLoad(true);
		afterNewGameLoad = true;
	}

	@Override
	public void onGameLoad(boolean newGame) {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("new_game", newGame).build();
		BattleNext_Logger.operationStarted(BattleNextModPlugin.class, "campaign.load", input);
		try {

			BattleNext_MissionRestrictedUnlock.getInstance().forget();
			BattleNext_LunaPinnedVariantRefitButton.onGameLoad();
			BattleNext_LunaFleetSaveRefitButton.onGameLoad();

			boolean classLoad = false;
			if (Global.getSector().getPersistentData().containsKey(CLASS_MARK)) {
				classLoad = (Boolean)Global.getSector().getPersistentData().get(CLASS_MARK);
			}

			if (!classLoad) {
				Global.getSector().getPersistentData().put(CLASS_MARK, true);

				// add sector script and register sector plugin here

			}

			onDevModeF8Reload();

			if (afterNewGameLoad) {

				BarEventManager manager = BarEventManager.getInstance();
				// add bar event creator here?
			}

			if (newGame) {

				// maybe
			}
			BattleNext_Logger.operationSucceeded(BattleNextModPlugin.class, "campaign.load",
					BattleNext_Logger.fields().put("new_game", newGame).build());
		} catch (RuntimeException exception) {
			throw exception;
		}
	}

	@Override
	public void onDevModeF8Reload() {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("mod_id", "AI_Battles_Next").build();
		BattleNext_Logger.operationStarted(BattleNextModPlugin.class, "application.registries.reload", input);
		try {
			BattleNext_OrderRegistry.loadFromSettings();
			BattleNext_RestrictionRegistry.loadFromSettings();
			BattleNext_Logger.operationSucceeded(BattleNextModPlugin.class, "application.registries.reload",
					BattleNext_Logger.fields().put("mod_id", "AI_Battles_Next").build());
		} catch (RuntimeException exception) {
			throw exception;
		}
	}
}
