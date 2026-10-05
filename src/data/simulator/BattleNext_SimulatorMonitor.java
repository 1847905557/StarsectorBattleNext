package data.simulator;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

public class BattleNext_SimulatorMonitor extends BaseEveryFrameCombatPlugin {

	@Override
	public void init(CombatEngineAPI engine) {
		if (!BattleNext_SimulatorWorker.isWorker()) {
			BattleNext_SimulatorService.getInstance();
		}
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (BattleNext_SimulatorWorker.isWorker()) {
			BattleNext_SimulatorWorker.advance();
		} else {
			BattleNext_SimulatorService.advance();
		}
	}
}
