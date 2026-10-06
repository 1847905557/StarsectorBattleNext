package data.scripts.report;

import org.json.JSONException;
import org.json.JSONObject;

public class BattleNext_DamageSourceReport {

	private final String kind;
	private final String id;
	private double hull;
	private double armor;
	private double shields;
	private double emp;

	public BattleNext_DamageSourceReport(String kind, String id) {
		this.kind = kind;
		this.id = id;
	}

	public static BattleNext_DamageSourceReport fromJson(JSONObject json) throws JSONException {
		BattleNext_DamageSourceReport result = new BattleNext_DamageSourceReport(
				json.getString("kind"), json.getString("id"));
		result.add(json.getDouble("hull"), json.getDouble("armor"), json.getDouble("shields"), json.getDouble("emp"));
		return result;
	}

	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("kind", kind);
		json.put("id", id);
		json.put("hull", hull);
		json.put("armor", armor);
		json.put("shields", shields);
		json.put("emp", emp);
		return json;
	}

	public void add(double hull, double armor, double shields, double emp) {
		this.hull += Math.max(0d, hull);
		this.armor += Math.max(0d, armor);
		this.shields += Math.max(0d, shields);
		this.emp += Math.max(0d, emp);
	}

	public String getKind() {
		return kind;
	}

	public String getId() {
		return id;
	}

	public double getHull() {
		return hull;
	}

	public double getArmor() {
		return armor;
	}

	public double getShields() {
		return shields;
	}

	public double getEmp() {
		return emp;
	}

	public double getTotal() {
		return hull + armor + shields;
	}
}
