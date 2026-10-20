package data.simulator;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import data.scripts.plugin.BattleNext_ScorekeeperPlugin;
import data.scripts.report.BattleNext_DamageSourceReport;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.opengl.Display;
import org.lwjgl.util.vector.Vector2f;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BattleNext_ProximityDamageSmoke {

	private static volatile int completed;
	private static Path reports;

	public static void run() throws Exception {

		Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
		Path output = Path.of("build/reports/proximity-damage").toAbsolutePath();
		Files.createDirectories(output);
		List<String> command = BattleNext_SimulatorLaunch.javaCommand(core);
		command.addAll(List.of("-Dcom.fs.starfarer.settings.paths.mods=" + core.getParent().resolve("mods"),
				"-Dcom.fs.starfarer.settings.paths.logs=" + output, "-cp", System.getProperty("java.class.path"),
				BattleNext_ProximityDamageSmoke.class.getName(), output.toString()));
		Process process = BattleNext_SimulatorLaunch.start(command, core);
		String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		Files.writeString(output.resolve("native-output.log"), result, StandardCharsets.UTF_8);
		if (process.waitFor() != 0 || !result.contains("Proximity damage verified: battles=2, explosions=6")) {
			List<String> lines = result.lines().toList();
			throw new AssertionError("Native proximity damage verification failed; output=" + output.resolve("native-output.log")
					+ "\n" + String.join("\n", lines.subList(Math.max(0, lines.size() - 60), lines.size())));
		}
		result.lines().filter(line -> line.startsWith("Proximity damage")).forEach(System.out::println);
		System.out.println("Proximity damage reports: " + output);
	}

	public static void main(String[] arguments) throws Throwable {
		reports = Path.of(arguments[0]);
		System.setProperty("launchDirect", "true");
		System.setProperty("startRes", "800x600");
		System.setProperty("startFS", "false");
		System.setProperty("startSound", "false");
		Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%p %c - %m%n")));
		Logger.getRootLogger().setLevel(Level.WARN);
		Display.setLocation(-20000, -20000);
		Thread driver = new Thread(BattleNext_ProximityDamageSmoke::drive, "Proximity damage verification");
		driver.setDaemon(true);
		driver.start();
		Class<?> launcher = Class.forName("com.fs.starfarer.StarfarerLauncher");
		MethodHandles.publicLookup().findStatic(launcher, "main", MethodType.methodType(void.class, String[].class))
				.invoke((Object) new String[0]);
		if (completed != 2) throw new AssertionError("Native proximity damage verification did not finish");
	}

	private static void drive() {

		try {
			long deadline = System.nanoTime() + 180000000000L;
			BattleNext_SimulatorNative access = new BattleNext_SimulatorNative(BattleNext_ProximityDamageSmoke.class.getClassLoader());
			for (int battle = 1; battle <= 2; battle++) {
				while (Global.getSettings() == null || Global.getCurrentState() != GameState.TITLE || Global.getCombatEngine() == null) {
					if (System.nanoTime() > deadline) throw new AssertionError("Native title startup timed out");
					Thread.sleep(20L);
				}
				Thread.sleep(1000L);
				Global.getSettings().setBoolean("idleWhileWindowNotVisible", false);
				Global.getSettings().setBoolean("devMode", false);
				access.configureGraphics();
				var mission = access.createMission();
				mission.initFleet(FleetSide.PLAYER, "", FleetGoal.ATTACK, true, 999);
				mission.initFleet(FleetSide.ENEMY, "", FleetGoal.ATTACK, true, 999);
				FleetMemberAPI player = mission.addToFleet(FleetSide.PLAYER, "wolf_CS", FleetMemberType.SHIP, "Proximity source", true);
				FleetMemberAPI enemy = mission.addToFleet(FleetSide.ENEMY, "wolf_CS", FleetMemberType.SHIP, "Proximity target", true);
				player.getRepairTracker().setCR(0.7f);
				enemy.getRepairTracker().setCR(0.7f);
				mission.initMap(-3000f, 3000f, -3000f, 3000f);
				BattleNext_ScorekeeperPlugin scorekeeper = new BattleNext_ScorekeeperPlugin(List.of(player), List.of(enemy));
				mission.addPlugin(scorekeeper);
				mission.addPlugin(new Verification(player, enemy, scorekeeper, battle));
				access.submitMission(mission);
				while (completed < battle) {
					if (System.nanoTime() > deadline) throw new AssertionError("Native proximity damage timed out in battle " + battle);
					Thread.sleep(20L);
				}
			}
			while (Global.getCurrentState() != GameState.TITLE) {
				if (System.nanoTime() > deadline) throw new AssertionError("Native title return timed out");
				Thread.sleep(20L);
			}
			System.out.println("Proximity damage verified: battles=2, explosions=6");
			access.exit();
		} catch (Throwable exception) {
			exception.printStackTrace();
			System.exit(1);
		}
	}

	public static class Verification extends BaseEveryFrameCombatPlugin implements DamageListener {

		private final FleetMemberAPI player;
		private final FleetMemberAPI enemy;
		private final BattleNext_ScorekeeperPlugin scorekeeper;
		private final int battle;
		private final double[] observed = new double[4];
		private final JSONArray probes = new JSONArray();
		private CombatEngineAPI engine;
		private ShipAPI attacker;
		private ShipAPI target;
		private WeaponAPI weapon;
		private DamagingProjectileAPI explosion;
		private int stage;
		private int hits;
		private float elapsed;
		private float firedAt;
		private boolean finished;

		public Verification(FleetMemberAPI player, FleetMemberAPI enemy, BattleNext_ScorekeeperPlugin scorekeeper, int battle) {
			this.player = player;
			this.enemy = enemy;
			this.scorekeeper = scorekeeper;
			this.battle = battle;
		}

		@Override
		public void init(CombatEngineAPI engine) {
			this.engine = engine;
			engine.getListenerManager().addListener(this);
		}

		@Override
		public void advance(float amount, List<InputEventAPI> events) {

			if (finished) return;
			try {
				if (attacker == null) {
					deploy();
				}
				elapsed += amount;
				if (elapsed < 1f) return;
				if (explosion != null) {
					if (elapsed - firedAt < 0.3f) return;
					if (hits != 1) throw new AssertionError("Proximity probe " + stage + " must damage its target exactly once: " + hits);
					explosion = null;
					stage++;
				}
				if (stage == 3) {
					verifyReport();
					return;
				}
				fire();
			} catch (Throwable exception) {
				exception.printStackTrace();
				System.exit(1);
			}
		}

		private void deploy() throws Throwable {

			new BattleNext_SimulatorNative(engine.getClass().getClassLoader()).prepareDeployment(engine, false);
			attacker = engine.getFleetManager(FleetSide.PLAYER).spawnFleetMember(player, new Vector2f(-1000f, 0f), 0f, 0f);
			target = engine.getFleetManager(FleetSide.ENEMY).spawnFleetMember(enemy, new Vector2f(0f, 0f), 0f, 0f);
			for (ShipAPI ship : List.of(attacker, target)) {
				ship.setShipAI(null);
				ship.setHoldFire(true);
				ship.setCurrentCR(0.7f);
				for (WeaponAPI fitted : ship.getAllWeapons()) {
					fitted.setForceDisabled(true);
				}
			}
			weapon = engine.createFakeWeapon(attacker, "devastator");
			target.setShield(ShieldAPI.ShieldType.OMNI, 0f, 1f, 360f);
			target.getShield().toggleOn();
			engine.setPaused(false);
		}

		private void fire() throws ReflectiveOperationException {

			hits = 0;
			Vector2f point = new Vector2f(target.getLocation());
			if (stage == 0) {
				target.getShield().toggleOn();
				target.getShield().setActiveArc(360f);
				point.x += target.getShield().getRadius() + 5f;
			} else {
				target.getShield().toggleOff();
			}
			if (stage == 2) {
				float[][] armor = target.getArmorGrid().getGrid();
				for (int x = 0; x < armor.length; x++) {
					for (int y = 0; y < armor[x].length; y++) {
						target.getArmorGrid().setArmorValue(x, y, 0f);
					}
				}
			}
			DamagingProjectileAPI original = (DamagingProjectileAPI) engine.spawnProjectile(attacker, weapon, "devastator", point, 180f, new Vector2f());
			Object fuse = original.getClass().getMethod("getAI").invoke(original);
			explosion = (DamagingProjectileAPI) fuse.getClass().getMethod("explode").invoke(fuse);
			if (explosion.getExplosionSpecIfExplosion() == null) throw new AssertionError("Devastator must create a damaging explosion");
			firedAt = elapsed;
		}

		@Override
		public void reportDamageApplied(Object source, CombatEntityAPI damaged, ApplyDamageResultAPI result) {

			if (damaged != target || target.getParamAboutToApplyDamage() != explosion) return;
			if (result.getDamageToHull() <= 0f && result.getTotalDamageToArmor() <= 0f
					&& result.getDamageToShields() <= 0f && result.getEmpDamage() <= 0f) return;
			try {
				double[] values = {result.getDamageToHull(), result.getTotalDamageToArmor(), result.getDamageToShields(), result.getEmpDamage()};
				int component = stage == 0 ? 2 : stage == 1 ? 1 : 0;
				if (values[component] <= 0d) throw new AssertionError("Proximity probe " + stage + " must apply component " + component
						+ ": hull=" + values[0] + ", armor=" + values[1] + ", shields=" + values[2] + ", shieldOn=" + target.getShield().isOn());
				for (int index = 0; index < values.length; index++) {
					observed[index] += values[index];
				}
				probes.put(new JSONObject().put("stage", stage).put("hull", values[0]).put("armor", values[1])
						.put("shields", values[2]).put("emp", values[3]));
				hits++;
			} catch (Throwable exception) {
				exception.printStackTrace();
				System.exit(1);
			}
		}

		private void verifyReport() throws Throwable {

			scorekeeper.freeze("draw");
			var report = scorekeeper.getFrozenReport();
			for (List<BattleNext_DamageSourceReport> sources : List.of(report.getPlayerMembers().get(0).getDealtBySource(),
					report.getEnemyMembers().get(0).getTakenBySource())) {
				if (sources.size() != 1) throw new AssertionError("All Devastator splash must share one weapon source: " + sources.size());
				var source = sources.get(0);
				if (!source.getKind().equals("weapon") || !source.getId().equals("devastator")) {
					throw new AssertionError("Devastator splash source: " + source.getKind() + "/" + source.getId());
				}
				double[] actual = {source.getHull(), source.getArmor(), source.getShields(), source.getEmp()};
				for (int index = 0; index < actual.length; index++) {
					if (Double.compare(observed[index], actual[index]) != 0) {
						throw new AssertionError("Proximity component " + index + ": observed=" + observed[index] + ", report=" + actual[index]);
					}
				}
			}
			JSONObject output = new JSONObject().put("battle", battle).put("probes", probes).put("report", report.toJson());
			Files.writeString(reports.resolve("battle-" + battle + ".json"), output.toString(2), StandardCharsets.UTF_8);
			System.out.println("Proximity damage battle=" + battle + ", explosions=" + probes.length() + ", hull=" + observed[0]
					+ ", armor=" + observed[1] + ", shields=" + observed[2] + ", emp=" + observed[3]);
			engine.getListenerManager().removeListener(this);
			scorekeeper.cleanup();
			finished = true;
			new BattleNext_SimulatorNative(engine.getClass().getClassLoader()).finish(engine);
			completed = battle;
		}
	}
}
