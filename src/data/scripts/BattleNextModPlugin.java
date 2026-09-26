package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import data.order.BattleNext_OrderRegistry;
import data.restriction.BattleNext_RestrictionRegistry;
import data.scripts.battlesnext.util.BattleNext_I18nSection;

public class BattleNextModPlugin extends BaseModPlugin {

	public static final String CLASS_MARK = "BattleNextModPlugin";

	public static final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_");

	private boolean afterApplicationLoad = true;
	private boolean afterNewGameLoad = true;

	@Override
	public void onApplicationLoad() {

		if (Global.getSettings().getModManager().isModEnabled("aibattles")) {
			throw new IllegalArgumentException("请关闭旧版 AI Battles，只开启最新的 AI Battles Next");
		}

		afterApplicationLoad = false;
		onDevModeF8Reload();
		afterApplicationLoad = true;
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
	}

	@Override
	public void onDevModeF8Reload() {
		BattleNext_OrderRegistry.loadFromSettings();
		BattleNext_RestrictionRegistry.loadFromSettings();
	}
}
