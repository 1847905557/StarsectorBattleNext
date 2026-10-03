package data.scripts.tools;

import com.fs.starfarer.api.Global;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class BattleNext_LoggerTest {
	private Field settingsValues;
	private Object previousValues;
	private Logger logger;
	private Level previousLevel;
	private boolean previousAdditivity;
	private final RecordingAppender appender = new RecordingAppender();

	@BeforeEach
	void configureLogging() throws Exception {
		settingsValues = BattleNext_Settings.class.getDeclaredField("values");
		settingsValues.setAccessible(true);
		previousValues = settingsValues.get(null);
		settingsValues.set(null, new JSONObject());
		BattleNext_Logger.fields();
		logger = Global.getLogger(BattleNext_Logger.class);
		previousLevel = logger.getLevel();
		previousAdditivity = logger.getAdditivity();
		logger.setLevel(Level.INFO);
		logger.setAdditivity(false);
		logger.addAppender(appender);
	}

	@AfterEach
	void restoreLogging() throws Exception {
		logger.removeAppender(appender);
		logger.setLevel(previousLevel);
		logger.setAdditivity(previousAdditivity);
		settingsValues.set(null, previousValues);
	}

	@Test
	void disabledInfoLogKeepsWarningAndErrorEmission() {
		BattleNext_Logger.Fields fields = BattleNext_Logger.fields().put("id", "alpha").build();
		BattleNext_Logger.info(getClass(), "test.info", fields);
		BattleNext_Logger.operationStarted(getClass(), "test.operation", fields);
		BattleNext_Logger.operationSucceeded(getClass(), "test.operation", fields);
		assertTrue(appender.events.isEmpty());
		BattleNext_Logger.warn(getClass(), "test.warning", fields);
		BattleNext_Logger.warn(getClass(), "test.warning.failure", fields, new IllegalArgumentException("Rejected"));
		BattleNext_Logger.error(getClass(), "test.error", fields, new IllegalStateException("Failed"));
		BattleNext_Logger.operationFailed(getClass(), "test.operation", fields, new IllegalStateException("Failed"));
		assertEquals(List.of(Level.WARN, Level.WARN, Level.ERROR, Level.ERROR),
				appender.events.stream().map(LoggingEvent::getLevel).toList());
	}

	@Test
	void enabledInfoLogEmitsAllInfoEntrypointsAndStopsWhenDisabled() throws Exception {
		JSONObject values = (JSONObject) settingsValues.get(null);
		values.put("infoLog", true);
		BattleNext_Logger.Fields fields = BattleNext_Logger.fields().put("id", "alpha").build();
		BattleNext_Logger.info(getClass(), "test.info", fields);
		BattleNext_Logger.operationStarted(getClass(), "test.operation", fields);
		BattleNext_Logger.operationSucceeded(getClass(), "test.operation", fields);
		assertEquals(List.of(Level.INFO, Level.INFO, Level.INFO),
				appender.events.stream().map(LoggingEvent::getLevel).toList());
		assertEquals("observed", new JSONObject(appender.events.get(0).getRenderedMessage()).getString("phase"));
		assertEquals("started", new JSONObject(appender.events.get(1).getRenderedMessage()).getString("phase"));
		assertEquals("succeeded", new JSONObject(appender.events.get(2).getRenderedMessage()).getString("phase"));
		values.put("infoLog", false);
		BattleNext_Logger.info(getClass(), "test.info", fields);
		BattleNext_Logger.operationStarted(getClass(), "test.operation", fields);
		BattleNext_Logger.operationSucceeded(getClass(), "test.operation", fields);
		assertEquals(3, appender.events.size());
	}

	@Test
	void startupInfoUsesDefaultWithoutLoadingSettingsRecursively() throws Exception {
		settingsValues.set(null, null);
		BattleNext_Logger.info(getClass(), "test.startup", BattleNext_Logger.fields().build());
		assertFalse(BattleNext_Settings.isInfoLogEnabled());
		assertTrue(appender.events.isEmpty());
		assertEquals(null, settingsValues.get(null));
	}

	@Test
	void fieldsFreezeNestedValuesAndKeepStructuredData() throws Exception {
		Map<String, Object> nested = new LinkedHashMap<>();
		nested.put("value", "中文 / quote \" / percent %");
		BattleNext_Logger.Fields fields = BattleNext_Logger.fields()
				.put("items", List.of("alpha", nested))
				.put("empty", null)
				.build();
		JSONObject entry = new JSONObject(BattleNext_Logger.format(BattleNext_LoggerTest.class,
				"test.snapshot", "observed", fields, null));

		assertEquals("data.scripts.tools.BattleNext_LoggerTest", entry.getString("source"));
		assertEquals("test.snapshot", entry.getString("event"));
		assertEquals("中文 / quote \" / percent %",
				entry.getJSONObject("fields").getJSONArray("items").getJSONObject(1).getString("value"));
		assertTrue(entry.getJSONObject("fields").isNull("empty"));
	}

	@Test
	void exceptionSummaryContainsOnlyTypeAndMessage() throws Exception {
		JSONObject entry = new JSONObject(BattleNext_Logger.format(BattleNext_LoggerTest.class,
				"test.failed", "failed", BattleNext_Logger.fields().put("id", "alpha").build(),
				new IllegalStateException("中文 failure")));

		JSONObject exception = entry.getJSONObject("exception");
		assertEquals(IllegalStateException.class.getName(), exception.getString("type"));
		assertEquals("中文 failure", exception.getString("message"));
		assertEquals(2, exception.length());
	}

	private static class RecordingAppender extends AppenderSkeleton {
		private final List<LoggingEvent> events = new ArrayList<>();

		@Override
		protected void append(LoggingEvent event) {
			events.add(event);
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
