package data.simulator;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import data.scripts.plugin.BattleNext_DeploymentPlugin;
import data.scripts.tools.BattleNext_AppContext;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.lwjgl.opengl.Display;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public class BattleNext_DeploymentOpeningSmoke {

	private static volatile boolean verified;
	private static volatile Throwable failure;

	public static void run() throws Exception {
		Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
		List<String> command = BattleNext_SimulatorLaunch.javaCommand(core);
		command.addAll(List.of("-Dcom.fs.starfarer.settings.paths.mods=" + core.getParent().resolve("mods"),
				"-cp", System.getProperty("java.class.path"), BattleNext_DeploymentOpeningSmoke.class.getName()));
		Process process = BattleNext_SimulatorLaunch.start(command, core);
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.waitFor() != 0 || !output.contains("Opening deployment dialog: player=80 DP, enemy=80 DP, bars=80 DP")) {
			throw new AssertionError("Opening deployment verification failed:\n" + output);
		}
		System.out.println("Opening deployment dialog: player=80 DP, enemy=80 DP, bars=80 DP");
	}

	public static void main(String[] arguments) throws Throwable {
		System.setProperty("launchDirect", "true");
		System.setProperty("startRes", "800x600");
		System.setProperty("startFS", "false");
		System.setProperty("startSound", "false");
		Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%p %c - %m%n")));
		Logger.getRootLogger().setLevel(Level.WARN);
		Display.setLocation(-20000, -20000);
		Thread driver = new Thread(BattleNext_DeploymentOpeningSmoke::drive, "Opening deployment verification");
		driver.setDaemon(true);
		driver.start();
		Class<?> launcher = Class.forName("com.fs.starfarer.StarfarerLauncher");
		MethodHandles.publicLookup().findStatic(launcher, "main", MethodType.methodType(void.class, String[].class))
				.invoke((Object) new String[0]);
		if (failure != null) throw new AssertionError("Opening deployment verification failed", failure);
		if (!verified) throw new AssertionError("Opening deployment verification did not finish");
	}

	private static void drive() {
		try {
			long deadline = System.nanoTime() + 120000000000L;
			while (Global.getSettings() == null || Global.getCurrentState() != GameState.TITLE || Global.getCombatEngine() == null) {
				if (System.nanoTime() > deadline) throw new AssertionError("Native title startup timed out");
				Thread.sleep(20L);
			}
			Thread.sleep(1000L);
			Global.getSettings().setBoolean("idleWhileWindowNotVisible", false);
			Global.getSettings().setBoolean("devMode", false);
			BattleNext_SimulatorNative access = new BattleNext_SimulatorNative(BattleNext_DeploymentOpeningSmoke.class.getClassLoader());
			access.configureGraphics();
			var mission = access.createMission();
			for (FleetSide side : FleetSide.values()) {
				mission.initFleet(side, "", FleetGoal.ATTACK, true, 999);
				for (int index = 0; index < 2; index++) {
					var member = mission.addToFleet(side, "wolf_CS", FleetMemberType.SHIP, "Opening " + side + " " + index, index == 0);
					member.getRepairTracker().setCR(0.7f);
				}
			}
			mission.initMap(-6000f, 6000f, -6000f, 6000f);
			mission.addPlugin(new BattleNext_DeploymentPlugin(80, true, true));
			mission.addPlugin(new OpeningVerifier());
			access.submitMission(mission);
			while (!verified && failure == null) {
				if (System.nanoTime() > deadline) throw new AssertionError("Native opening deployment timed out");
				Thread.sleep(20L);
			}
			while (Global.getCurrentState() != GameState.TITLE) {
				if (System.nanoTime() > deadline) throw new AssertionError("Native title return timed out");
				Thread.sleep(20L);
			}
			access.exit();
		} catch (Throwable exception) {
			failure = exception;
			exception.printStackTrace();
			System.exit(1);
		}
	}

	public static class OpeningVerifier extends BaseEveryFrameCombatPlugin {

		private CombatEngineAPI engine;
		private boolean finished;

		@Override
		public void init(CombatEngineAPI engine) {
			this.engine = engine;
		}

		@Override
		public void advance(float amount, List<InputEventAPI> events) {
			if (finished) return;
			try {
				Object state = BattleNext_AppContext.currentState();
				Field dialogField = state.getClass().getDeclaredField("fdd");
				dialogField.setAccessible(true);
				Object dialog = dialogField.get(state);
				if (dialog == null) return;
				for (FleetSide side : FleetSide.values()) {
					if (engine.getFleetManager(side).getMaxStrength() != 80) throw new AssertionError("Opening side " + side + " must have 80 DP");
				}
				int bars = 0;
				for (Field field : dialog.getClass().getDeclaredFields()) {
					if (!field.getType().getName().equals("com.fs.starfarer.campaign.ui.N")) continue;
					field.setAccessible(true);
					Object bar = field.get(dialog);
					if (!bar.getClass().getMethod("getRangeMax").invoke(bar).equals(80f)) throw new AssertionError("Opening deployment progress bar must have 80 DP");
					bars++;
				}
				if (bars != 2) throw new AssertionError("Opening deployment must expose both fleet progress bars");
				System.out.println("Opening deployment dialog: player=80 DP, enemy=80 DP, bars=80 DP");
				verified = true;
				finished = true;
				new BattleNext_SimulatorNative(engine.getClass().getClassLoader()).finish(engine);
			} catch (Throwable exception) {
				failure = exception;
				exception.printStackTrace();
				System.exit(1);
			}
		}
	}
}
