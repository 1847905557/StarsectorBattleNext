package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponGroupType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BattleNext_VariantBuilder {

	private final String id;
	private final JSONObject json;

	private BattleNext_VariantBuilder(String id, JSONObject json) {
		this.id = id;
		this.json = json;
	}

	public static BattleNext_VariantBuilder fromJson(JSONObject json, String fallbackId) {
		String id = json.optString("variantId", fallbackId);
		return new BattleNext_VariantBuilder(id, json);
	}

	public static BattleNext_VariantBuilder fromRegistered(String id) throws JSONException {
		ShipVariantAPI variant = Global.getSettings().getVariant(id);
		if (variant == null) {
			throw new IllegalArgumentException("Unknown variant: " + id);
		}
		return fromJson(variant.toJSONObject(), id);
	}

	public String getId() {
		return id;
	}

	public JSONObject toJson() {
		return new JSONObject(json, JSONObject.getNames(json));
	}

	public ShipVariantAPI build() {
		String hullId = json.optString("hullId", "");
		ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
		if (hull == null) {
			throw new IllegalArgumentException("Unknown hull: " + hullId);
		}
		ShipVariantAPI variant = Global.getSettings().createEmptyVariant(id, hull);
		variant.setVariantDisplayName(json.optString("displayName", id));
		variant.setNumFluxCapacitors(json.optInt("fluxCapacitors", 0));
		variant.setNumFluxVents(json.optInt("fluxVents", 0));
		addStrings(json.optJSONArray("hullMods"), variant, true, false);
		addStrings(json.optJSONArray("permaMods"), variant, false, true);
		addStrings(json.optJSONArray("sMods"), variant, false, false);
		JSONArray wings = json.optJSONArray("wings");
		if (wings != null) {
			for (int index = 0; index < wings.length(); index++) {
				String wing = wings.optString(index, "");
				if (!wing.isEmpty()) variant.setWingId(index, wing);
			}
		}
		JSONArray groups = json.optJSONArray("weaponGroups");
		if (groups != null) {
			for (int index = 0; index < groups.length(); index++) {
				JSONObject groupJson = groups.optJSONObject(index);
				if (groupJson == null) continue;
				WeaponGroupType type = WeaponGroupType.valueOf(groupJson.optString("mode", "LINKED"));
				WeaponGroupSpec group = new WeaponGroupSpec(type);
				group.setAutofireOnByDefault(groupJson.optBoolean("autofire", false));
				JSONObject weapons = groupJson.optJSONObject("weapons");
				if (weapons != null) {
					for (String slot : JSONObject.getNames(weapons)) {
						variant.addWeapon(slot, weapons.optString(slot, ""));
						group.addSlot(slot);
					}
				}
				variant.addWeaponGroup(group);
			}
		}
		return variant;
	}

	private void addStrings(JSONArray values, ShipVariantAPI variant, boolean hullMods, boolean permaMods) {
		if (values == null) return;
		for (int index = 0; index < values.length(); index++) {
			String value = values.optString(index, "");
			if (value.isEmpty()) continue;
			if (hullMods) variant.addMod(value);
			if (permaMods) variant.addPermaMod(value);
			if (!hullMods && !permaMods) variant.addMod(value);
		}
	}
}
