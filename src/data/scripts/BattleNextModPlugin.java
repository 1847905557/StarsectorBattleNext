package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import data.scripts.orders.BattleNext_OrderRegistry;
import data.scripts.util.BattleNext_FontData;
import data.scripts.util.BattleNext_I18nSection;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public class BattleNextModPlugin extends BaseModPlugin {

	public static final String CLASS_MARK = "BattleNextModPlugin";

	public static final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_");

	@Override
	public void onApplicationLoad() {

		if (Global.getSettings().getModManager().isModEnabled("aibattles")) {
			throw new IllegalArgumentException("请关闭旧版 AI Battles，只开启最新的 Ai Battles Next");
		}
		try {
			BattleNext_FontData.init();
		} catch (RuntimeException ignored) {
		}
		String orders;
		try {
			orders = Global.getSettings().loadText("data/automation/orders.csv", "AI_Battles_Next");
		} catch (IOException | JSONException exception) {
			throw new IllegalArgumentException("Unable to read data/automation/orders.csv", exception);
		}
		BattleNext_OrderRegistry registry = new BattleNext_OrderRegistry();
		List<String> errors = registry.loadCsv(orders);
		errors.addAll(registry.validate());
		if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid data/automation/orders.csv: " + String.join("; ", errors));

	}

	@Override
	public void onNewGame() {

		// add procgen names here

		afterNewGameLoad = false;
		onGameLoad(true);
		afterNewGameLoad = true;
	}

	private boolean afterNewGameLoad = true;

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

	}
}
