package data.simulator;

import com.fs.starfarer.StarfarerLauncher;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.tools.BattleNext_Logger;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.boxutil.config.BoxConfigs;
import org.dark.shaders.util.GraphicsLibSettings;
import org.dark.shaders.util.TextureData;
import org.json.JSONObject;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

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
	private static BattleNext_SimulatorMouse mouse;

	private BattleNext_SimulatorWorker() {
	}

	public static boolean isWorker() {
		return Boolean.getBoolean("BattleNext.simulator.worker");
	}

	public static void main(String[] arguments) throws Exception {

		System.setProperty("BattleNext.simulator.worker", "true");
		configureLogging();
		System.setProperty("launchDirect", "true");
		System.setProperty("startRes", System.getProperty("startRes", "800x600"));
		System.setProperty("startFS", "false");
		System.setProperty("startSound", "false");
		Socket socket = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(arguments[0]));
		channel = new BattleNext_SimulatorProtocol(socket, arguments[1]);
		Thread receiver = new Thread(BattleNext_SimulatorWorker::receive, "BattleNext simulator worker channel");
		receiver.setDaemon(true);
		receiver.start();
		try {
			StarfarerLauncher.main(new String[0]);
		} finally {
			if (mouse != null) {
				mouse.cleanup();
			}
		}
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
				new BattleNext_SimulatorNative(BattleNext_SimulatorWorker.class.getClassLoader()).configureGraphics();
				mouse = BattleNext_SimulatorMouse.getInstance();
				Display.setLocation(-20000, -20000);
				Display.setVSyncEnabled(false);
				initialized = true;
				returning = false;
				current = null;
				applyIdleFrameRate();
				JSONObject graphics = new JSONObject().put("lights", GraphicsLibSettings.enableLights()).put("bloom", GraphicsLibSettings.enableBloom())
						.put("normal", GraphicsLibSettings.enableNormal()).put("auto_normals", GraphicsLibSettings.autoGenNormals())
						.put("material", TextureData.isLoadMaterial()).put("surface", TextureData.isLoadSurface())
						.put("distortion", GraphicsLibSettings.enableDistortion()).put("post_process", GraphicsLibSettings.enablePostProcess())
						.put("preload_all_maps", GraphicsLibSettings.preloadAllMaps()).put("explosion_effects", GraphicsLibSettings.enableExplosionEffects())
						.put("explosion_shockwave", GraphicsLibSettings.enableExplosionShockwave()).put("explosion_trails", GraphicsLibSettings.enableExplosionTrails())
						.put("box_shader", BoxConfigs.isShaderEnable()).put("box_distortion", BoxConfigs.isDistortionEnable())
						.put("box_debug", BoxConfigs.isGLDebugEnable()).put("box_opencl", BoxConfigs.isCLEnable());
				JSONObject display = new JSONObject().put("width", Display.getWidth()).put("height", Display.getHeight())
						.put("aa_samples", Global.getSettings().getAASamples()).put("gl_samples", GL11.glGetInteger(GL13.GL_SAMPLES))
						.put("shield_jitter", Global.getSettings().getBoolean("renderShieldJitterEffect"))
						.put("version_overlay", Global.getSettings().getBoolean("renderVersionAndIdle"))
						.put("planet_backgrounds", Global.getSettings().getBoolean("3dPlanetBackgrounds")).put("mouse_isolated", true);
				channel.send("READY", new JSONObject().put("fps", IDLE_FPS).put("active_fps", previousFps).put("graphics", graphics).put("display", display));
			}
			if (current != null || pending == null) return;
			restoreFrameRate();
			current = pending;
			pending = null;
			status(current.id(), BattleNext_SimulationTask.State.PREPARING);
			BattleNext_SimulatorNative nativeAccess = new BattleNext_SimulatorNative(Global.getCombatEngine().getClass().getClassLoader());
			MissionDefinitionAPI mission = nativeAccess.createMission();
			BattleNext_ScorekeeperPlugin scorekeeper = BattleNext_SimulatorMission.define(mission, current);
			if (current.config().strategy() == BattleNext_SimulationConfig.Strategy.AGGRESSIVE) {
				mission.getContext().setInitialStepSize(1f / 60f);
				mission.getContext().setInitialNumSteps((float) Math.ceil(current.config().simulationSeconds() * 60d) + 2f);
			}
			mission.addPlugin(new BattleNext_SimulatorController(current, scorekeeper));
			nativeAccess.submitMission(mission);
		} catch (Throwable exception) {
			if (current == null) throw new IllegalStateException("Simulator idle channel failed", exception);
			BattleNext_Logger.error(BattleNext_SimulatorWorker.class, "simulator.task.prepare.failed",
					BattleNext_Logger.fields().put("request", current.toJson()).build(), exception);
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
