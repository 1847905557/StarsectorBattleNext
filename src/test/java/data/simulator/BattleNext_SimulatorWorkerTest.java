package data.simulator;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import data.test.fixture.BattleNext_TestGameFixture;
import data.scripts.BattleNextModPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorWorkerTest extends BattleNext_TestGameFixture {

	private Field previousFpsField;
	private Field idleFpsAppliedField;
	private Object previousFps;
	private Object previousIdleFpsApplied;
	private float fps;
	private final List<Float> writes = new ArrayList<>();

	@BeforeEach
	void bindFrameSettingsAndCaptureWorkerOwnership() throws Exception {

		previousFpsField = BattleNext_SimulatorWorker.class.getDeclaredField("previousFps");
		idleFpsAppliedField = BattleNext_SimulatorWorker.class.getDeclaredField("idleFpsApplied");
		previousFpsField.setAccessible(true);
		idleFpsAppliedField.setAccessible(true);
		previousFps = previousFpsField.get(null);
		previousIdleFpsApplied = idleFpsAppliedField.get(null);
		idleFpsAppliedField.set(null, false);
		fps = 60f;
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getCurrentState")) return GameState.COMBAT;
			if (method.getName().equals("getFloat") && args[0].equals("fps")) return fps;
			if (method.getName().equals("setFloat") && args[0].equals("fps")) {
				fps = (Float) args[1];
				writes.add(fps);
				return null;
			}
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	@AfterEach
	void restoreWorkerOwnership() throws Exception {
		previousFpsField.set(null, previousFps);
		idleFpsAppliedField.set(null, previousIdleFpsApplied);
	}

	@Test
	void consecutiveIdleCyclesRestoreCurrentFrameRatesAndReleaseOwnershipIdempotently() throws Exception {
		for (float active : List.of(60f, 90f, 144f)) {
			fps = active;
			invokeLifecycle("applyIdleFrameRate");
			assertEquals(5f, fps);
			assertEquals(active, previousFpsField.getFloat(null));
			assertTrue(idleFpsAppliedField.getBoolean(null));
			invokeLifecycle("restoreFrameRate");
			assertEquals(active, fps);
			assertFalse(idleFpsAppliedField.getBoolean(null));
			invokeLifecycle("restoreFrameRate");
		}
		assertEquals(List.of(5f, 60f, 5f, 90f, 5f, 144f), writes);
	}

	@Test
	void combatCallbacksLeaveTheSimulationFrameRateUnderItsNormalOwner() {
		Level threshold = LogManager.getLoggerRepository().getThreshold();
		Level rootLevel = Logger.getRootLogger().getLevel();
		BattleNext_SimulatorWorker.advance();
		assertEquals(60f, fps);
		assertTrue(writes.isEmpty());
		assertEquals(threshold, LogManager.getLoggerRepository().getThreshold());
		assertEquals(rootLevel, Logger.getRootLogger().getLevel());
	}

	@Test
	void workerThresholdFiltersExistingAndNewIndependentAppendersWhileRetainingWarningsAndFailures() throws Exception {

		Level threshold = LogManager.getLoggerRepository().getThreshold();
		Level rootLevel = Logger.getRootLogger().getLevel();
		List<Logger> loggers = new ArrayList<>();
		List<RecordingAppender> appenders = new ArrayList<>();
		try {
			for (boolean afterConfiguration : List.of(false, true)) {
				if (afterConfiguration) {
					invokeLifecycle("configureLogging");
				}
				Logger logger = Logger.getLogger("simulator.worker.logging." + UUID.randomUUID());
				RecordingAppender appender = new RecordingAppender();
				logger.setAdditivity(false);
				logger.setLevel(Level.ALL);
				appender.setThreshold(Level.ALL);
				logger.addAppender(appender);
				loggers.add(logger);
				appenders.add(appender);
			}
			assertEquals(Level.WARN, LogManager.getLoggerRepository().getThreshold());
			assertEquals(Level.WARN, Logger.getRootLogger().getLevel());
			for (Logger logger : loggers) {
				logger.setLevel(Level.ALL);
				for (Level level : List.of(Level.ALL, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.FATAL)) {
					logger.log(level, "Worker level " + level);
				}
			}
			for (RecordingAppender appender : appenders) {
				assertEquals(List.of(Level.WARN, Level.ERROR, Level.FATAL), appender.levels);
			}
		} finally {
			for (Logger logger : loggers) {
				logger.removeAllAppenders();
				logger.setLevel(null);
				logger.setAdditivity(true);
			}
			LogManager.getLoggerRepository().setThreshold(threshold);
			Logger.getRootLogger().setLevel(rootLevel);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void vanillaGraphicsSettingsBelongToTheWorkerProcess(boolean worker) throws Exception {

		String previousIdentity = System.getProperty("BattleNext.simulator.worker");
		Map<String, Boolean> changes = new LinkedHashMap<>();
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("setBoolean")) {
				changes.put((String) args[0], (Boolean) args[1]);
				return null;
			}
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		try {
			System.setProperty("BattleNext.simulator.worker", Boolean.toString(worker));
			new BattleNextModPlugin() {
				@Override
				public void onDevModeF8Reload() {

				}
			}.onApplicationLoad();
			if (worker) {
				assertEquals(Map.of("devMode", false, "allowJVMRestartFromLauncher", false, "doRAMandVRAMChecksWhenRunningWithMods", false,
						"idleWhileWindowNotVisible", false, "renderShieldJitterEffect", false, "renderVersionAndIdle", false, "3dPlanetBackgrounds", false), changes);
			} else {
				assertTrue(changes.isEmpty());
			}
		} finally {
			if (previousIdentity == null) {
				System.clearProperty("BattleNext.simulator.worker");
			} else {
				System.setProperty("BattleNext.simulator.worker", previousIdentity);
			}
		}
	}

	private void invokeLifecycle(String name) throws Exception {
		Method method = BattleNext_SimulatorWorker.class.getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(null);
	}

	private static class RecordingAppender extends AppenderSkeleton {
		private final List<Level> levels = new ArrayList<>();

		@Override
		protected void append(LoggingEvent event) {
			levels.add(event.getLevel());
		}

		@Override
		public void close() {

		}

		@Override
		public boolean requiresLayout() {
			return false;
		}
	}
}
