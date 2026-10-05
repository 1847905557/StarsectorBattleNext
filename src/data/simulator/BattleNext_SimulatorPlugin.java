package data.simulator;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;

import java.util.List;
import java.util.Locale;

public class BattleNext_SimulatorPlugin extends BaseEveryFrameCombatPlugin {

	private final List<String> player;
	private final List<String> enemy;
	private final BattleNext_SimulationConfig config;
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private CombatEngineAPI engine;
	private BattleNext_SimulationTask task;
	private int displayedStages;

	public BattleNext_SimulatorPlugin(List<String> player, List<String> enemy, BattleNext_SimulationConfig config) {
		this.player = List.copyOf(player);
		this.enemy = List.copyOf(enemy);
		this.config = config;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		if (task != null) return;
		if (BattleNext_SimulatorWorker.isWorker() || Global.getCurrentState() != GameState.COMBAT) return;
		task = BattleNext_SimulatorService.getInstance().submit(player, enemy, config);
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {

		if (task == null) return;
		List<BattleNext_SimulationTask.State> history = task.getStateHistory();
		while (displayedStages < history.size()) {
			showState(history.get(displayedStages++));
		}
	}

	private void showState(BattleNext_SimulationTask.State displayed) {

		String details = task.getRequest().id();
		BattleNext_SimulationResult result = task.getResult();
		if (displayed == BattleNext_SimulationTask.State.FINISHED && result != null) {
			try {
				details = result.outcome() == BattleNext_SimulationResult.Outcome.COMPLETED
						? strings.format("simulator_result", result.report().getWinner(), result.simulationSeconds(),
								result.executionNanos() / 1000000000d, result.report().getName())
						: result.diagnostic();
			} catch (Exception exception) {
				throw new IllegalStateException("Invalid completed simulator report", exception);
			}
		} else if (displayed == BattleNext_SimulationTask.State.IMPORT_FAILED) {
			details = task.getImportError();
		}
		engine.getCombatUI().addMessage(0, strings.format("simulator_status",
				strings.get("simulator_" + displayed.name().toLowerCase(Locale.ROOT)), details));
	}

	public BattleNext_SimulationTask getTask() {
		return task;
	}
}
