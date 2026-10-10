package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_Logger;
import org.lwjgl.opengl.Display;

import java.util.List;

public class BattleNext_SimulatorController extends BaseEveryFrameCombatPlugin {

	private final BattleNext_SimulationRequest request;
	private final BattleNext_ScorekeeperPlugin scorekeeper;
	private CombatEngineAPI engine;
	private BattleNext_SimulatorNative nativeAccess;
	private long started;
	private long lastMaintenance;
	private long steps;
	private long peakHeap;
	private float soundElapsed;
	private float previousSpeed;
	private float previousSteps;
	private boolean completed;
	private boolean configured;

	public BattleNext_SimulatorController(BattleNext_SimulationRequest request, BattleNext_ScorekeeperPlugin scorekeeper) {
		this.request = request;
		this.scorekeeper = scorekeeper;
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
		try {
			nativeAccess = new BattleNext_SimulatorNative(engine.getClass().getClassLoader());
			nativeAccess.prepareDeployment(engine, request.config().deploymentPointLimit() > 0);
			previousSpeed = Global.getSettings().getFloat("combatSpeedMult");
			previousSteps = Global.getSettings().getFloat("combatStepsPerFrame");
			Global.getSettings().setFloat("combatSpeedMult", 1f);
			Global.getSettings().setFloat("combatStepsPerFrame", request.config().strategy() == BattleNext_SimulationConfig.Strategy.COMPATIBLE
					? (float) request.config().stepsPerFrame() : 1f);
			configured = true;
			BattleNext_Logger.info(getClass(), "simulator.settings.acquired", BattleNext_Logger.fields()
					.put("id", request.id()).put("previous_speed", previousSpeed).put("previous_steps", previousSteps)
					.put("strategy", request.config().strategy()).put("steps_per_frame", request.config().stepsPerFrame()).build());
		} catch (Throwable exception) {
			throw new IllegalStateException("Unable to prepare simulator engine", exception);
		}
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {

		if (completed) return;
		try {
			if (started == 0L) {
				started = System.nanoTime();
				lastMaintenance = started;
				BattleNext_SimulatorWorker.status(request.id(), BattleNext_SimulationTask.State.RUNNING);
			}
			steps++;
			soundElapsed += amount;
			long now = System.nanoTime();
			if (now - lastMaintenance >= 50000000L) {
				lastMaintenance = now;
				nativeAccess.maintainWindowAndSound(engine, soundElapsed);
				soundElapsed = 0f;
				Runtime runtime = Runtime.getRuntime();
				peakHeap = Math.max(peakHeap, runtime.totalMemory() - runtime.freeMemory());
			}
			if (BattleNext_SimulatorWorker.isCancelled(request.id()) || Display.isCloseRequested()) {
				complete(BattleNext_SimulationResult.Outcome.CANCELLED, "Simulation cancelled");
			} else if (scorekeeper.isFrozen()) {
				complete(BattleNext_SimulationResult.Outcome.COMPLETED, "");
			} else if (engine.getTotalElapsedTime(false) >= request.config().simulationSeconds()
					|| request.config().strategy() == BattleNext_SimulationConfig.Strategy.AGGRESSIVE
					&& steps >= Math.ceil(request.config().simulationSeconds() * 60d)
					|| now - started >= request.config().executionSeconds() * 1000000000d) {
				complete(BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED, "Simulation or execution budget exhausted");
			}
		} catch (Throwable exception) {
			try {
				complete(BattleNext_SimulationResult.Outcome.FAILED, exception.getClass().getName() + ": " + exception.getMessage());
			} catch (Throwable cleanupFailure) {
				throw new IllegalStateException("Simulator cleanup failed", cleanupFailure);
			}
		}
	}

	private void complete(BattleNext_SimulationResult.Outcome outcome, String diagnostic) throws Throwable {

		completed = true;
		engine.getContext().setInitialNumSteps(0f);
		String reportJson = "";
		if (outcome == BattleNext_SimulationResult.Outcome.COMPLETED || outcome == BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED) {
			if (outcome == BattleNext_SimulationResult.Outcome.BUDGET_EXCEEDED) {
				scorekeeper.freeze("draw");
			}
			BattleNext_SimulatorWorker.status(request.id(), BattleNext_SimulationTask.State.FROZEN);
			BattleNext_BattleReportModel report = scorekeeper.getFrozenReport();
			report.setId(request.id());
			report.setCreatedAt(System.currentTimeMillis());
			String sourceId = request.kind() == BattleNext_SimulationRequest.Kind.MATCH
					? request.source().getJSONObject("match").getString("id") : "variants";
			if (request.source().has("tournamentId")) {
				sourceId = request.source().getString("tournamentName") + " / " + sourceId + " / Round "
						+ request.source().getInt("roundNumber") + " / " + request.source().getString("tournamentId");
			}
			report.setName("Simulation " + request.config().strategy() + ": " + request.label() + " - " + sourceId + " - " + request.id());
			reportJson = report.toJson().toString();
		}
		BattleNext_SimulatorWorker.status(request.id(), BattleNext_SimulationTask.State.CLEANING);
		if (configured) {
			Global.getSettings().setFloat("combatSpeedMult", previousSpeed);
			Global.getSettings().setFloat("combatStepsPerFrame", previousSteps);
			configured = false;
			BattleNext_Logger.info(getClass(), "simulator.settings.restored", BattleNext_Logger.fields()
					.put("id", request.id()).put("speed", previousSpeed).put("steps", previousSteps).build());
		}
		scorekeeper.cleanup();
		engine.setDoNotEndCombat(false);
		nativeAccess.finish(engine);
		BattleNext_SimulationResult result = new BattleNext_SimulationResult(request.id(), outcome, reportJson,
				engine.getTotalElapsedTime(false), System.nanoTime() - started, steps, peakHeap, diagnostic);
		BattleNext_Logger.info(getClass(), "simulator.execution.finished", BattleNext_Logger.fields().put("result", result.toJson()).build());
		BattleNext_SimulatorWorker.complete(result);
		engine.removePlugin(this);
	}
}
