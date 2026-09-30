package data.scripts.tools;

import com.fs.starfarer.api.Global;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class BattleNext_Settings {

	private static final String PATH = "AI_Battles_Next/settings.json";
	private static final boolean DEFAULT_FULL_GRAPHIC_EFFECT = true;
	private static final boolean DEFAULT_RTT_IN_BATTLE = false;
	private static final float DEFAULT_BATTLE_TIME_LIMIT = 500f;
	private static final float DEFAULT_TIME_MULT = 1f;
	private static final float DEFAULT_BOOST_TIME_MULT = 3f;
	private static final int DEFAULT_REPORT_KEEP_COUNT = 50;

	private static JSONObject values;

	public static void load() {
		values = defaults();
		try {
			JSONObject stored = Global.getSettings().readJSONFromCommon(PATH, false);
			if (stored != null) merge(stored);
		} catch (Exception ignored) {
			try {
				JSONObject stored = Global.getSettings().readJSONFromCommon(PATH + ".data", false);
				if (stored != null) merge(stored);
			} catch (Exception absent) {
			}
		}
	}

	public static boolean isFullGraphicEffect() {
		ensureLoaded();
		return values.optBoolean("fullGraphicEffect", DEFAULT_FULL_GRAPHIC_EFFECT);
	}

	public static boolean isRttInBattle() {
		ensureLoaded();
		return values.optBoolean("rttInBattle", DEFAULT_RTT_IN_BATTLE);
	}

	public static float getBattleTimeLimit() {
		ensureLoaded();
		return (float) values.optDouble("battleTimeLimit", DEFAULT_BATTLE_TIME_LIMIT);
	}

	public static float getDefaultTimeMult() {
		ensureLoaded();
		return (float) values.optDouble("defaultTimeMult", DEFAULT_TIME_MULT);
	}

	public static float getBoostTimeMult() {
		ensureLoaded();
		return (float) values.optDouble("boostTimeMult", DEFAULT_BOOST_TIME_MULT);
	}

	public static int getReportKeepCount() {
		ensureLoaded();
		return values.optInt("reportKeepCount", DEFAULT_REPORT_KEEP_COUNT);
	}

	public static void save(boolean fullGraphicEffect, boolean rttInBattle, float battleTimeLimit, float defaultTimeMult,
			float boostTimeMult, int reportKeepCount) {
		if (battleTimeLimit < 0f || !Float.isFinite(battleTimeLimit)) throw new IllegalArgumentException("Battle time limit is invalid");
		if (defaultTimeMult <= 0f || !Float.isFinite(defaultTimeMult)) throw new IllegalArgumentException("Default time multiplier is invalid");
		if (boostTimeMult <= 0f || !Float.isFinite(boostTimeMult)) throw new IllegalArgumentException("Boost time multiplier is invalid");
		if (reportKeepCount < 0) throw new IllegalArgumentException("Report keep count is invalid");
		JSONObject next = defaults();
		put(next, "fullGraphicEffect", fullGraphicEffect);
		put(next, "rttInBattle", rttInBattle);
		put(next, "battleTimeLimit", battleTimeLimit);
		put(next, "defaultTimeMult", defaultTimeMult);
		put(next, "boostTimeMult", boostTimeMult);
		put(next, "reportKeepCount", reportKeepCount);
		try {
			Global.getSettings().writeJSONToCommon(PATH, next, false);
		} catch (IOException | JSONException exception) {
			throw new IllegalStateException("Unable to save settings", exception);
		}
		values = next;
	}

	private static JSONObject defaults() {
		JSONObject result = new JSONObject();
		put(result, "fullGraphicEffect", DEFAULT_FULL_GRAPHIC_EFFECT);
		put(result, "rttInBattle", DEFAULT_RTT_IN_BATTLE);
		put(result, "battleTimeLimit", DEFAULT_BATTLE_TIME_LIMIT);
		put(result, "defaultTimeMult", DEFAULT_TIME_MULT);
		put(result, "boostTimeMult", DEFAULT_BOOST_TIME_MULT);
		put(result, "reportKeepCount", DEFAULT_REPORT_KEEP_COUNT);
		return result;
	}

	private static void merge(JSONObject stored) {
		putIf(values, "fullGraphicEffect", stored.has("fullGraphicEffect") ? stored.optBoolean("fullGraphicEffect") : null);
		putIf(values, "rttInBattle", stored.has("rttInBattle") ? stored.optBoolean("rttInBattle") : null);
		putIf(values, "battleTimeLimit", validNumber(stored, "battleTimeLimit", false));
		putIf(values, "defaultTimeMult", validNumber(stored, "defaultTimeMult", true));
		putIf(values, "boostTimeMult", validNumber(stored, "boostTimeMult", true));
		putIf(values, "reportKeepCount", stored.has("reportKeepCount") && stored.optInt("reportKeepCount", -1) >= 0 ? stored.optInt("reportKeepCount") : null);
	}

	private static void put(JSONObject object, String key, Object value) {
		try {
			object.put(key, value);
		} catch (JSONException exception) {
			throw new IllegalStateException("Unable to create settings", exception);
		}
	}

	private static void putIf(JSONObject object, String key, Object value) {
		if (value != null) put(object, key, value);
	}

	private static Float validNumber(JSONObject object, String key, boolean positive) {
		if (!object.has(key)) return null;
		float value = (float) object.optDouble(key, Float.NaN);
		if (!Float.isFinite(value) || value < 0f || (positive && value == 0f)) return null;
		return value;
	}

	private static void ensureLoaded() {
		if (values == null) load();
	}
}
