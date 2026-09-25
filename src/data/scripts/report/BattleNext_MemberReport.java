package data.scripts.report;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;

public class BattleNext_MemberReport {

	private String variantId;
	private String name;
	private double damageDealt;
	private double damageTaken;
	private double hullDamageDealt;
	private double shieldDamageDealt;
	private double empDamageDealt;
	private double hullDamageTaken;
	private double shieldDamageTaken;
	private double empDamageTaken;
	private float finalHullFraction;
	private float finalCr;
	private String status;

	public BattleNext_MemberReport(String variantId, String name) {
		this.variantId = normalize(variantId);
		this.name = normalize(name);
		status = "alive";
	}

	public static BattleNext_MemberReport fromJson(JSONObject json) {
		BattleNext_MemberReport report = new BattleNext_MemberReport(json.optString("variantId", ""),
				json.optString("name", ""));
		report.damageDealt = json.optDouble("damageDealt", 0d);
		report.damageTaken = json.optDouble("damageTaken", 0d);
		report.hullDamageDealt = json.optDouble("hullDamageDealt", 0d);
		report.shieldDamageDealt = json.optDouble("shieldDamageDealt", 0d);
		report.empDamageDealt = json.optDouble("empDamageDealt", 0d);
		report.hullDamageTaken = json.optDouble("hullDamageTaken", 0d);
		report.shieldDamageTaken = json.optDouble("shieldDamageTaken", 0d);
		report.empDamageTaken = json.optDouble("empDamageTaken", 0d);
		report.finalHullFraction = (float)json.optDouble("finalHullFraction", 0d);
		report.finalCr = (float)json.optDouble("finalCr", 0d);
		report.status = normalizeStatus(json.optString("status", "alive"));
		return report;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("variantId", variantId);
		json.put("name", name);
		json.put("damageDealt", damageDealt);
		json.put("damageTaken", damageTaken);
		json.put("hullDamageDealt", hullDamageDealt);
		json.put("shieldDamageDealt", shieldDamageDealt);
		json.put("empDamageDealt", empDamageDealt);
		json.put("hullDamageTaken", hullDamageTaken);
		json.put("shieldDamageTaken", shieldDamageTaken);
		json.put("empDamageTaken", empDamageTaken);
		json.put("finalHullFraction", finalHullFraction);
		json.put("finalCr", finalCr);
		json.put("status", status);
		return json;
	}

	public String getVariantId() {
		return variantId;
	}

	public String getName() {
		return name;
	}

	public double getDamageDealt() {
		return damageDealt;
	}

	public double getDamageTaken() {
		return damageTaken;
	}

	public double getHullDamageDealt() {
		return hullDamageDealt;
	}

	public double getShieldDamageDealt() {
		return shieldDamageDealt;
	}

	public double getEmpDamageDealt() {
		return empDamageDealt;
	}

	public double getHullDamageTaken() {
		return hullDamageTaken;
	}

	public double getShieldDamageTaken() {
		return shieldDamageTaken;
	}

	public double getEmpDamageTaken() {
		return empDamageTaken;
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

	public void addDamageDealt(double hull, double shields, double emp) {
		hullDamageDealt += Math.max(0d, hull);
		shieldDamageDealt += Math.max(0d, shields);
		empDamageDealt += Math.max(0d, emp);
		damageDealt += Math.max(0d, hull) + Math.max(0d, shields);
	}

	public void addDamageTaken(double hull, double shields, double emp) {
		hullDamageTaken += Math.max(0d, hull);
		shieldDamageTaken += Math.max(0d, shields);
		empDamageTaken += Math.max(0d, emp);
		damageTaken += Math.max(0d, hull) + Math.max(0d, shields);
	}

	public void setFinalState(float finalHullFraction, float finalCr, String status) {
		this.finalHullFraction = Math.max(0f, Math.min(1f, finalHullFraction));
		this.finalCr = Math.max(0f, Math.min(1f, finalCr));
		this.status = normalizeStatus(status);
	}

	public static Comparator<BattleNext_MemberReport> damageOrder(boolean dealt) {
		return Comparator.comparingDouble(
				(BattleNext_MemberReport member) -> dealt ? member.damageDealt : member.damageTaken).reversed();
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private static String normalizeStatus(String value) {
		if ("destroyed".equals(value) || "retreated".equals(value)) return value;
		return "alive";
	}
}
