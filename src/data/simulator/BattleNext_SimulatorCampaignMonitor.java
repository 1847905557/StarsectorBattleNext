package data.simulator;

import com.fs.starfarer.api.EveryFrameScript;

public class BattleNext_SimulatorCampaignMonitor implements EveryFrameScript {

	@Override
	public void advance(float amount) {
		BattleNext_SimulatorService.advance();
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return true;
	}
}
