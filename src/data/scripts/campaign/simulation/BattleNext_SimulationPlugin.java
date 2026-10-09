package data.scripts.campaign.simulation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import org.lwjgl.input.Keyboard;

import java.lang.invoke.MethodHandles;
import java.util.List;

public class BattleNext_SimulationPlugin extends BaseEveryFrameCombatPlugin {

	private Object window;
	private BattleNext_SimulationInjection injection;

	@Override
	public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {

		var engine = Global.getCombatEngine();
		if (!engine.isInCampaignSim() && !engine.isInMissionSim() && !engine.isSimulation()) return;
		var ui = engine.getCombatUI();
		try {
			Object current = null;
			if (ui != null && ui.isShowingDeploymentDialog()) {
				Class<?> screen = Class.forName("com.fs.starfarer.ui.impl.oO0O", false, ui.getClass().getClassLoader());
				current = MethodHandles.privateLookupIn(ui.getClass(), MethodHandles.lookup())
						.findGetter(ui.getClass(), "fdd", screen).invoke(ui);
			}
			if (current != window) {
				if (injection != null) {
					injection.cleanup();
				}
				window = current;
				injection = current == null ? null : new BattleNext_SimulationInjection(current,
						BattleNext_I18nSection.getInstance("Variant", "BattleNext_variant_").get("pinned_list_title"));
			}
			if (injection == null) return;
			injection.refresh();
			if (!injection.isSelected()) return;
			for (InputEventAPI event : events) {
				if (event.isConsumed() || !event.isKeyDownEvent()) continue;
				if (event.getEventValue() != Keyboard.KEY_RETURN && event.getEventValue() != Keyboard.KEY_SPACE) continue;
				injection.deploy();
				event.consume();
				break;
			}
		} catch (Throwable exception) {
			throw new IllegalStateException("Unable to inject pinned simulation deployment", exception);
		}
	}
}
