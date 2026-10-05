package data.simulator;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.StarfarerLauncher;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_Logger;
import org.json.JSONObject;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.Display;

import java.net.InetAddress;
import java.net.Socket;

public class BattleNext_SimulatorWorker {

	private static final float IDLE_FPS = 5f;
	private static BattleNext_SimulatorProtocol channel;
	private static volatile BattleNext_SimulationRequest pending;
	private static volatile String cancelled = "";
	private static volatile boolean shutdown;
	private static BattleNext_SimulationRequest current;
	private static boolean returning;
	private static boolean initialized;
	private static float previousFps;
	private static boolean idleFpsApplied;

	private BattleNext_SimulatorWorker() {
	}

	public static boolean isWorker() {
		return Boolean.getBoolean("BattleNext.simulator.worker");
	}

	public static void main(String[] arguments) throws Exception {

		System.setProperty("BattleNext.simulator.worker", "true");
		configureLogging();
		System.setProperty("launchDirect", "true");
		System.setProperty("startRes", "1024x768");
		System.setProperty("startFS", "false");
		System.setProperty("startSound", "false");
		Socket socket = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(arguments[0]));
		channel = new BattleNext_SimulatorProtocol(socket, arguments[1]);
		Thread receiver = new Thread(BattleNext_SimulatorWorker::receive, "BattleNext simulator worker channel");
		receiver.setDaemon(true);
		receiver.start();
		StarfarerLauncher.main(new String[0]);
	}

	private static void configureLogging() {
		LogManager.getLoggerRepository().setThreshold(Level.WARN);
		Logger.getRootLogger().setLevel(Level.WARN);
	}

	private static void receive() {

		try {
			while (!shutdown) {
				JSONObject message = channel.receive();
				JSONObject payload = message.getJSONObject("payload");
				switch (message.getString("type")) {
					case "REQUEST" -> {
						cancelled = "";
						pending = BattleNext_SimulationRequest.fromJson(payload);
					}
					case "CANCEL" -> cancelled = payload.getString("id");
					case "SHUTDOWN" -> shutdown = true;
					default -> throw new IllegalArgumentException("Unexpected simulator worker command: " + message.getString("type"));
				}
			}
		} catch (Exception exception) {
			shutdown = true;
			BattleNext_Logger.error(BattleNext_SimulatorWorker.class, "simulator.channel.failed", BattleNext_Logger.fields().build(), exception);
		}
	}

	public static void advance() {

		if (Global.getCurrentState() != GameState.TITLE) return;
		try {
			if (shutdown) {
				restoreFrameRate();
				new BattleNext_SimulatorNative(Global.getCombatEngine().getClass().getClassLoader()).exit();
				return;
			}
			if (!initialized || returning) {
				Display.setLocation(-20000, -20000);
				Display.setVSyncEnabled(false);
				initialized = true;
				returning = false;
				current = null;
				applyIdleFrameRate();
				channel.send("READY", new JSONObject().put("fps", IDLE_FPS).put("active_fps", previousFps));
			}
			if (current != null || pending == null) return;
			restoreFrameRate();
			current = pending;
			pending = null;
			status(current.id(), BattleNext_SimulationTask.State.PREPARING);
			BattleNext_SimulatorNative nativeAccess = new BattleNext_SimulatorNative(Global.getCombatEngine().getClass().getClassLoader());
			MissionDefinitionAPI mission = nativeAccess.createMission();
			BattleNext_ScorekeeperPlugin scorekeeper = BattleNext_SimulatorMission.define(mission, current.player(), current.enemy(), current.config());
			if (current.config().strategy() == BattleNext_SimulationConfig.Strategy.AGGRESSIVE) {
				mission.getContext().setInitialStepSize(1f / 60f);
				mission.getContext().setInitialNumSteps((float) Math.ceil(current.config().simulationSeconds() * 60d) + 2f);
			}
			mission.addPlugin(new BattleNext_SimulatorController(current, scorekeeper));
			nativeAccess.submitMission(mission);
		} catch (Throwable exception) {
			if (current == null) throw new IllegalStateException("Simulator idle channel failed", exception);
			BattleNext_Logger.error(BattleNext_SimulatorWorker.class, "simulator.task.prepare.failed",
					BattleNext_Logger.fields().put("id", current.id()).put("player", current.player()).put("enemy", current.enemy()).build(), exception);
			try {
				complete(new BattleNext_SimulationResult(current.id(), BattleNext_SimulationResult.Outcome.FAILED,
						"", 0f, 0L, 0L, 0L, exception.getClass().getName() + ": " + exception.getMessage()));
			} catch (Exception failure) {
				throw new IllegalStateException("Unable to publish simulator preparation failure", failure);
			}
		}
	}

	private static void applyIdleFrameRate() {
		previousFps = Global.getSettings().getFloat("fps");
		Global.getSettings().setFloat("fps", IDLE_FPS);
		idleFpsApplied = true;
		BattleNext_Logger.info(BattleNext_SimulatorWorker.class, "simulator.fps.idle",
				BattleNext_Logger.fields().put("fps", IDLE_FPS).put("previous_fps", previousFps).build());
	}

	private static void restoreFrameRate() {
		if (!idleFpsApplied) return;
		Global.getSettings().setFloat("fps", previousFps);
		idleFpsApplied = false;
		BattleNext_Logger.info(BattleNext_SimulatorWorker.class, "simulator.fps.restored",
				BattleNext_Logger.fields().put("fps", previousFps).build());
	}

	public static boolean isCancelled(String id) {
		return shutdown || id.equals(cancelled);
	}

	public static void status(String id, BattleNext_SimulationTask.State state) throws Exception {
		channel.send("STATUS", new JSONObject().put("id", id).put("state", state.name()).put("fps", Global.getSettings().getFloat("fps")));
	}

	public static void complete(BattleNext_SimulationResult result) throws Exception {
		channel.send("RESULT", result.toJson());
		returning = true;
	}
}
