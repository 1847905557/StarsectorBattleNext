package data.scripts.report;

import data.model.BattleNext_JsonModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class BattleNext_MemberReport {

	private String variantId;
	private String hullId;
	private String name;
	private final List<BattleNext_DamageSourceReport> dealtBySource = new ArrayList<>();
	private final List<BattleNext_DamageSourceReport> takenBySource = new ArrayList<>();
	private float finalHullFraction;
	private float finalCr;
	private String status;
	private final List<String> loadErrors = new ArrayList<>();
	private JSONObject incompleteJson;

	public BattleNext_MemberReport(String variantId, String hullId, String name) {
		this.variantId = normalize(variantId);
		this.hullId = normalize(hullId);
		this.name = normalize(name);
		status = "alive";
	}

	public static BattleNext_MemberReport fromJson(JSONObject json) throws JSONException {
		BattleNext_MemberReport report = new BattleNext_MemberReport(json.optString("variantId", ""),
				json.optString("hullId", ""), json.optString("name", ""));
		report.dealtBySource.addAll(readSources("dealtBySource", json.getJSONArray("dealtBySource"), report.loadErrors));
		report.takenBySource.addAll(readSources("takenBySource", json.getJSONArray("takenBySource"), report.loadErrors));
		report.finalHullFraction = (float)json.optDouble("finalHullFraction", 0d);
		report.finalCr = (float)json.optDouble("finalCr", 0d);
		report.status = normalizeStatus(json.optString("status", "alive"));
		if (!report.loadErrors.isEmpty()) {
			report.incompleteJson = new JSONObject(json.toString());
		}
		return report;
	}

	public JSONObject toJson() throws JSONException {
		if (incompleteJson != null) return new JSONObject(incompleteJson.toString());
		JSONObject json = new JSONObject();
		json.put("variantId", variantId);
		json.put("hullId", hullId);
		json.put("name", name);
		json.put("dealtBySource", writeSources(dealtBySource));
		json.put("takenBySource", writeSources(takenBySource));
		json.put("finalHullFraction", finalHullFraction);
		json.put("finalCr", finalCr);
		json.put("status", status);
		return json;
	}

	public List<String> getLoadErrors() {
		return List.copyOf(loadErrors);
	}

	public String getVariantId() {
		return variantId;
	}

	public String getHullId() {
		return hullId;
	}

	public String getName() {
		return name;
	}

	public double getDamageDealt() {
		return total(dealtBySource);
	}

	public double getDamageTaken() {
		return total(takenBySource);
	}

	public double getHullDamageDealt() {
		return component(dealtBySource, 0);
	}

	public double getShieldDamageDealt() {
		return component(dealtBySource, 2);
	}

	public double getEmpDamageDealt() {
		return component(dealtBySource, 3);
	}

	public double getHullDamageTaken() {
		return component(takenBySource, 0);
	}

	public double getShieldDamageTaken() {
		return component(takenBySource, 2);
	}

	public double getEmpDamageTaken() {
		return component(takenBySource, 3);
	}

	public double getArmorDamageDealt() {
		return component(dealtBySource, 1);
	}

	public double getArmorDamageTaken() {
		return component(takenBySource, 1);
	}

	public List<BattleNext_DamageSourceReport> getDealtBySource() {
		return List.copyOf(dealtBySource);
	}

	public List<BattleNext_DamageSourceReport> getTakenBySource() {
		return List.copyOf(takenBySource);
	}

	public float getFinalHullFraction() {
		return finalHullFraction;
	}

	public float getFinalCr() {
		return finalCr;
	}

	public String getStatus() {
		return status;
	}

	public void addDamageDealt(String kind, String id, double hull, double armor, double shields, double emp) {
		addSource(dealtBySource, kind, id, hull, armor, shields, emp);
	}

	public void addDamageTaken(String kind, String id, double hull, double armor, double shields, double emp) {
		addSource(takenBySource, kind, id, hull, armor, shields, emp);
	}

	public void setFinalState(float finalHullFraction, float finalCr, String status) {
		this.finalHullFraction = Math.max(0f, Math.min(1f, finalHullFraction));
		this.finalCr = Math.max(0f, Math.min(1f, finalCr));
		this.status = normalizeStatus(status);
	}

	public static Comparator<BattleNext_MemberReport> damageOrder(boolean dealt) {
		return Comparator.comparingDouble(
				(BattleNext_MemberReport member) -> dealt ? member.getDamageDealt() : member.getDamageTaken()).reversed();
	}

	private static void addSource(List<BattleNext_DamageSourceReport> sources, String kind, String id,
			double hull, double armor, double shields, double emp) {
		for (BattleNext_DamageSourceReport source : sources) {
			if (source.getKind().equals(kind) && source.getId().equals(id)) {
				source.add(hull, armor, shields, emp);
				return;
			}
		}
		BattleNext_DamageSourceReport source = new BattleNext_DamageSourceReport(kind, id);
		source.add(hull, armor, shields, emp);
		sources.add(source);
	}

	private static double total(List<BattleNext_DamageSourceReport> sources) {
		double result = 0d;
		for (BattleNext_DamageSourceReport source : sources) {
			result += source.getTotal();
		}
		return result;
	}

	private static double component(List<BattleNext_DamageSourceReport> sources, int index) {
		double result = 0d;
		for (BattleNext_DamageSourceReport source : sources) {
			result += switch (index) {
				case 0 -> source.getHull();
				case 1 -> source.getArmor();
				case 2 -> source.getShields();
				default -> source.getEmp();
			};
		}
		return result;
	}

	private static JSONArray writeSources(List<BattleNext_DamageSourceReport> sources) throws JSONException {
		JSONArray array = new JSONArray();
		for (BattleNext_DamageSourceReport source : sources) {
			array.put(source.toJson());
		}
		return array;
	}

	private static List<BattleNext_DamageSourceReport> readSources(String key, JSONArray values, List<String> errors) {
		Map<List<String>, Integer> counts = new HashMap<>();
		for (int index = 0; index < values.length(); index++) {
			JSONObject item = values.optJSONObject(index);
			if (item == null) continue;
			counts.merge(List.of(item.optString("kind", ""), item.optString("id", "")), 1, Integer::sum);
		}
		return BattleNext_JsonModel.parseCollection(key, values, json -> {
			List<String> identity = List.of(json.getString("kind"), json.getString("id"));
			if (counts.get(identity) > 1) throw new IllegalArgumentException("Duplicate damage source: " + identity);
			return BattleNext_DamageSourceReport.fromJson(json);
		}, errors);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private static String normalizeStatus(String value) {
		if ("destroyed".equals(value) || "retreated".equals(value)) return value;
		return "alive";
	}
}
