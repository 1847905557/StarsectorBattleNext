package data.scripts.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.model.BattleNext_BaseModel;
import data.model.BattleNext_CsvModel;
import data.model.BattleNext_JsonModel;
import data.model.fleet.BattleNext_FleetModel;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.member.BattleNext_PersonSkills;
import data.scripts.report.BattleNext_MemberReport;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.function.Supplier;

public class BattleNext_Logger {

	private static final Logger LOGGER = Global.getLogger(BattleNext_Logger.class);

	static {
		LOGGER.setLevel(Level.INFO);
	}

	private BattleNext_Logger() {

	}

	public static Fields.Builder fields() {
		return new Fields.Builder();
	}

	public static void info(Class<?> source, String event, Fields fields) {
		if (!BattleNext_Settings.isInfoLogEnabled()) return;
		emit(Level.INFO, source, event, "observed", fields, null);
	}

	public static void info(Class<?> source, String event, Supplier<Fields> fields) {
		emitInfo(source, event, "observed", fields);
	}

	public static void warn(Class<?> source, String event, Fields fields) {
		emit(Level.WARN, source, event, "degraded", fields, null);
	}

	public static void warn(Class<?> source, String event, Fields fields, Throwable exception) {
		emit(Level.WARN, source, event, "degraded", fields, exception);
	}

	public static void error(Class<?> source, String event, Fields fields, Throwable exception) {
		emit(Level.ERROR, source, event, "failed", fields, exception);
	}

	public static void operationStarted(Class<?> source, String event, Fields fields) {
		if (!BattleNext_Settings.isInfoLogEnabled()) return;
		emit(Level.INFO, source, event, "started", fields, null);
	}

	public static void operationStarted(Class<?> source, String event, Supplier<Fields> fields) {
		emitInfo(source, event, "started", fields);
	}

	public static void operationSucceeded(Class<?> source, String event, Fields fields) {
		if (!BattleNext_Settings.isInfoLogEnabled()) return;
		emit(Level.INFO, source, event, "succeeded", fields, null);
	}

	public static void operationSucceeded(Class<?> source, String event, Supplier<Fields> fields) {
		emitInfo(source, event, "succeeded", fields);
	}

	public static void operationFailed(Class<?> source, String event, Fields fields, Throwable exception) {
		emit(Level.ERROR, source, event, "failed", fields, exception);
	}

	static String format(Class<?> source, String event, String phase, Fields fields, Throwable exception) throws JSONException {
		JSONObject entry = new JSONObject().put("source", source.getName()).put("event", event)
				.put("phase", phase).put("fields", new JSONObject(fields.json));
		if (exception != null) entry.put("exception", exceptionSummary(exception));
		return entry.toString();
	}

	private static void emitInfo(Class<?> source, String event, String phase, Supplier<Fields> fields) {
		if (!BattleNext_Settings.isInfoLogEnabled()) return;
		emit(Level.INFO, source, event, phase, fields.get(), null);
	}

	private static void emit(Level level, Class<?> source, String event, String phase, Fields fields, Throwable exception) {
		try {
			LOGGER.log(level, format(source, event, phase, fields, exception));
		} catch (Exception failure) {
			System.err.println("Business log output failed: source=" + source.getName() + ", event=" + event
					+ ", type=" + failure.getClass().getName() + ", message=" + failure.getMessage());
		}
	}

	private static JSONObject exceptionSummary(Throwable exception) throws JSONException {
		return new JSONObject().put("type", exception.getClass().getName())
				.put("message", exception.getMessage() == null ? JSONObject.NULL : exception.getMessage());
	}

	private static Object snapshot(Object value) throws JSONException {

		if (value == null || value == JSONObject.NULL) return JSONObject.NULL;
		if (value instanceof JSONObject json) return new JSONObject(json.toString());
		if (value instanceof JSONArray json) return new JSONArray(json.toString());
		if (value instanceof Fields fields) return new JSONObject(fields.json);
		if (value instanceof String || value instanceof Boolean) return value;
		if (value instanceof Number number) {
			return Double.isFinite(number.doubleValue()) ? number : number.toString();
		}
		if (value instanceof Enum<?> enumeration) return enumeration.name();
		if (value instanceof Class<?> type) return type.getName();
		if (value instanceof BattleNext_JsonModel model) {
			JSONObject json = new JSONObject(model.toJson().toString());
			json.put("model_type", model.getClass().getName()).put("source_type", model.getSourceType().name())
					.put("source_path", model.getSourcePath());
			if (model instanceof BattleNext_FleetModel fleet) {
				json.put("folder_name", fleet.getFolderName()).put("variants", snapshot(fleet.getVariants()))
						.put("load_errors", snapshot(fleet.getLoadErrors()));
			}
			return json;
		}
		if (value instanceof BattleNext_QuickBattleModel battle) {
			return new JSONObject().put("id", battle.getId()).put("player", snapshot(battle.getPlayer()))
					.put("enemy", snapshot(battle.getEnemy())).put("map", battle.getMap())
					.put("map_scale", snapshot(battle.getMapScale())).put("enabled", battle.isEnabled())
					.put("row_type", battle.getRowType().name()).put("raw_content", battle.getRawContent())
					.put("legacy_ids", battle.usesLegacyIds()).put("source_type", battle.getSourceType().name())
					.put("source_path", battle.getSourcePath());
		}
		if (value instanceof BattleNext_CsvModel model) {
			return new JSONObject().put("id", model.getId()).put("row", model.toCsvLine());
		}
		if (value instanceof BattleNext_BaseModel model) {
			return new JSONObject().put("id", model.getId()).put("source_type", model.getSourceType().name())
					.put("source_path", model.getSourcePath()).put("model_type", model.getClass().getName());
		}
		if (value instanceof BattleNext_VariantBuilder variant) return snapshot(variant.toJson());
		if (value instanceof BattleNext_MemberModel member) return snapshot(member.toJson());
		if (value instanceof BattleNext_MemberReport member) return snapshot(member.toJson());
		if (value instanceof BattleNext_Match match) return snapshot(match.toJson());
		if (value instanceof BattleNext_Round round) return snapshot(round.toJson());
		if (value instanceof ShipVariantAPI variant) return snapshot(BattleNext_VariantBuilder.fromVariant(variant).toJson());
		if (value instanceof ShipAPI ship) {
			return new JSONObject().put("id", snapshot(ship.getId())).put("owner", ship.getOwner())
					.put("original_owner", ship.getOriginalOwner()).put("ally", ship.isAlly())
					.put("alive", ship.isAlive()).put("member", snapshot(ship.getFleetMember()))
					.put("hull_id", ship.getHullSpec() == null ? JSONObject.NULL : snapshot(ship.getHullSpec().getHullId()));
		}
		if (value instanceof FleetMemberAPI member) {
			return new JSONObject().put("id", snapshot(member.getId())).put("name", snapshot(member.getShipName()))
					.put("owner", member.getOwner()).put("variant", snapshot(member.getVariant()))
					.put("captain", snapshot(member.getCaptain()));
		}
		if (value instanceof PersonAPI person) {
			return new JSONObject().put("id", snapshot(person.getId()))
					.put("name", person.getName() == null ? JSONObject.NULL : person.getName().getFullName())
					.put("skills", snapshot(BattleNext_PersonSkills.capture(person)))
					.put("portrait", snapshot(person.getPortraitSprite())).put("personality", snapshot(person.getPersonalityAPI() == null
							? null : person.getPersonalityAPI().getId()));
		}
		if (value instanceof Vector2f vector) return new JSONObject().put("x", snapshot(vector.x)).put("y", snapshot(vector.y));
		if (value instanceof Map<?, ?> map) {
			JSONObject json = new JSONObject();
			for (var entry : map.entrySet()) {
				json.put(String.valueOf(entry.getKey()), snapshot(entry.getValue()));
			}
			return json;
		}
		if (value instanceof Iterable<?> iterable) {
			JSONArray json = new JSONArray();
			for (Object item : iterable) {
				json.put(snapshot(item));
			}
			return json;
		}
		if (value.getClass().isArray()) {
			JSONArray json = new JSONArray();
			for (int index = 0; index < Array.getLength(value); index++) {
				json.put(snapshot(Array.get(value, index)));
			}
			return json;
		}
		return String.valueOf(value);
	}

	public static class Fields {
		private final String json;

		private Fields(JSONObject values) {
			json = values.toString();
		}

		public static class Builder {
			private final JSONObject values = new JSONObject();

			public Builder put(String key, Object value) {
				try {
					values.put(key, snapshot(value));
				} catch (Exception exception) {
					try {
						values.put(key, new JSONObject().put("snapshot_error", exceptionSummary(exception)));
					} catch (JSONException failure) {
						System.err.println("Business log snapshot failed: field=" + key + ", type=" + failure.getClass().getName()
								+ ", message=" + failure.getMessage());
					}
				}
				return this;
			}

			public Fields build() {
				return new Fields(values);
			}
		}
	}
}
