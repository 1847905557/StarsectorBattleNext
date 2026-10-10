package data.simulator;

import org.json.JSONException;
import org.json.JSONObject;

public record BattleNext_SimulationConfig(Strategy strategy, float mapWidth, float mapHeight,
		float assaultAfterSeconds, float simulationSeconds, float executionSeconds,
		int stepsPerFrame, boolean playerAdmiral, boolean enemyAdmiral, int deploymentPointLimit) {

	public enum Strategy { AGGRESSIVE, COMPATIBLE }

	public BattleNext_SimulationConfig {
		if (strategy == null) throw new IllegalArgumentException("Simulation strategy is required");
		if (!Float.isFinite(mapWidth) || mapWidth <= 0f || !Float.isFinite(mapHeight) || mapHeight <= 0f) throw new IllegalArgumentException("Simulation map dimensions must be finite and positive");
		if (!Float.isFinite(assaultAfterSeconds) || assaultAfterSeconds < 0f) throw new IllegalArgumentException("Assault delay must be finite and nonnegative");
		if (!Float.isFinite(simulationSeconds) || simulationSeconds <= 0f || simulationSeconds * 60d > Integer.MAX_VALUE) throw new IllegalArgumentException("Simulation duration is outside the supported step budget");
		if (!Float.isFinite(executionSeconds) || executionSeconds <= 0f) throw new IllegalArgumentException("Execution duration must be finite and positive");
		if (stepsPerFrame < 1) throw new IllegalArgumentException("Steps per frame must be positive");
	}

	public static BattleNext_SimulationConfig defaults() {
		return new BattleNext_SimulationConfig(Strategy.AGGRESSIVE, 12000f, 12000f, 500f, 3600f, 120f, 32, true, false, 0);
	}

	public static BattleNext_SimulationConfig quickBattle(float assaultAfterSeconds, int deploymentPointLimit) {
		return new BattleNext_SimulationConfig(Strategy.AGGRESSIVE, 12000f, 12000f, assaultAfterSeconds, 3600f, 120f, 32, true, false, deploymentPointLimit);
	}

	public JSONObject toJson() throws JSONException {
		return new JSONObject().put("strategy", strategy.name()).put("mapWidth", mapWidth).put("mapHeight", mapHeight)
				.put("assaultAfterSeconds", assaultAfterSeconds).put("simulationSeconds", simulationSeconds)
				.put("executionSeconds", executionSeconds).put("stepsPerFrame", stepsPerFrame)
				.put("playerAdmiral", playerAdmiral).put("enemyAdmiral", enemyAdmiral).put("deploymentPointLimit", deploymentPointLimit);
	}

	public static BattleNext_SimulationConfig fromJson(JSONObject json) throws JSONException {
		return new BattleNext_SimulationConfig(Strategy.valueOf(json.getString("strategy")), (float) json.getDouble("mapWidth"),
				(float) json.getDouble("mapHeight"), (float) json.getDouble("assaultAfterSeconds"),
				(float) json.getDouble("simulationSeconds"), (float) json.getDouble("executionSeconds"),
				json.getInt("stepsPerFrame"), json.getBoolean("playerAdmiral"), json.getBoolean("enemyAdmiral"), json.getInt("deploymentPointLimit"));
	}
}
