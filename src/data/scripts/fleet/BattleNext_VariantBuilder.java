package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponGroupType;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

public class BattleNext_VariantBuilder {

	private final String id;
	private final JSONObject json;

	private BattleNext_VariantBuilder(String id, JSONObject json) {
		this.id = id;
		this.json = copy(json);
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
		return fromVariant(variant);
	}

	public static BattleNext_VariantBuilder fromVariant(ShipVariantAPI variant) throws JSONException {
		return fromJson(snapshot(variant, Collections.newSetFromMap(new IdentityHashMap<>())), variant.getHullVariantId());
	}

	private static JSONObject snapshot(ShipVariantAPI variant, Set<ShipVariantAPI> visiting) throws JSONException {
		if (!visiting.add(variant)) throw new IllegalArgumentException("Cyclic variant modules");
		JSONObject result = variant.clone().toJSONObject();
		result.put("sModdedBuiltIns", new JSONArray(variant.getSModdedBuiltIns()));
		result.put("suppressedMods", new JSONArray(variant.getSuppressedMods()));
		JSONObject modules = new JSONObject();
		for (String slot : variant.getStationModules().keySet()) {
			ShipVariantAPI module = variant.getModuleVariant(slot);
			if (module == null) throw new IllegalArgumentException("Missing module: " + slot);
			modules.put(slot, snapshot(module, visiting));
		}
		if (modules.length() > 0) {
			result.put("modules", modules);
		}
		visiting.remove(variant);
		return result;
	}

	public String getId() {
		return id;
	}

	public JSONObject toJson() {
		return copy(json);
	}

	public ShipVariantAPI build() {
		return build(new HashSet<>());
	}

	private ShipVariantAPI build(Set<String> moduleIds) {
		try {
			return buildJson(moduleIds);
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Invalid variant " + id + ": " + exception.getMessage(), exception);
		}
	}

	private ShipVariantAPI buildJson(Set<String> moduleIds) throws JSONException {
		String hullId = json.getString("hullId");
		ShipHullSpecAPI hull = Global.getSettings().getHullSpec(hullId);
		if (hull == null) {
			throw new IllegalArgumentException("Unknown hull: " + hullId);
		}
		ShipVariantAPI variant = Global.getSettings().createEmptyVariant(id, hull);
		variant.setVariantDisplayName(json.optString("displayName", id));
		variant.setNumFluxCapacitors(json.optInt("fluxCapacitors", 0));
		variant.setNumFluxVents(json.optInt("fluxVents", 0));
		variant.setGoalVariant(json.optBoolean("goalVariant", false));
		variant.setSource(VariantSource.REFIT);
		addMods("hullMods", variant::addMod);
		addMods("permaMods", variant::addPermaMod);
		addMods("sMods", mod -> variant.addPermaMod(mod, true));
		addMods("sModdedBuiltIns", mod -> variant.getSModdedBuiltIns().add(mod));
		addMods("suppressedMods", variant::addSuppressedMod);
		JSONArray tags = json.optJSONArray("tags");
		if (tags != null) {
			for (int index = 0; index < tags.length(); index++) {
				variant.addTag(tags.getString(index));
			}
		}
		JSONArray wings = json.optJSONArray("wings");
		if (wings != null) {
			for (int index = 0; index < wings.length(); index++) {
				String wing = wings.getString(index);
				if (!wing.isEmpty() && Global.getSettings().getFighterWingSpec(wing) == null) {
					throw new IllegalArgumentException("Unknown wing: " + wing);
				}
				variant.setWingId(index + hull.getBuiltInWings().size(), wing);
			}
		}
		JSONArray groups = json.optJSONArray("weaponGroups");
		if (groups != null) {
			for (int index = 0; index < groups.length(); index++) {
				JSONObject groupJson = groups.getJSONObject(index);
				WeaponGroupType type = WeaponGroupType.valueOf(groupJson.getString("mode"));
				WeaponGroupSpec group = new WeaponGroupSpec(type);
				group.setAutofireOnByDefault(groupJson.optBoolean("autofire", false));
				JSONObject weapons = groupJson.getJSONObject("weapons");
				if (weapons.length() > 0) {
					for (String slot : JSONObject.getNames(weapons)) {
						String weapon = weapons.getString(slot);
						if (hull.getWeaponSlot(slot) == null) throw new IllegalArgumentException("Unknown weapon slot: " + slot);
						if (Global.getSettings().getWeaponSpec(weapon) == null) throw new IllegalArgumentException("Unknown weapon: " + weapon);
						variant.addWeapon(slot, weapon);
						group.addSlot(slot);
					}
				}
				variant.addWeaponGroup(group);
			}
		}
		buildModules(variant, moduleIds);
		return variant;
	}

	private void addMods(String field, Consumer<String> consumer) throws JSONException {
		JSONArray values = json.optJSONArray(field);
		if (values == null) return;
		for (int index = 0; index < values.length(); index++) {
			String value = values.getString(index);
			if (Global.getSettings().getHullModSpec(value) == null) throw new IllegalArgumentException("Unknown hullmod: " + value);
			consumer.accept(value);
		}
	}

	private void buildModules(ShipVariantAPI variant, Set<String> moduleIds) throws JSONException {
		Object modules = json.opt("modules");
		if (modules == null) return;
		if (modules instanceof JSONObject object) {
			addModules(variant, object, moduleIds);
		} else if (modules instanceof JSONArray array) {
			for (int index = 0; index < array.length(); index++) {
				addModules(variant, array.getJSONObject(index), moduleIds);
			}
		} else {
			throw new IllegalArgumentException("Invalid modules for " + id);
		}
	}

	private void addModules(ShipVariantAPI variant, JSONObject modules, Set<String> moduleIds) throws JSONException {
		if (modules.length() == 0) return;
		for (String slot : JSONObject.getNames(modules)) {
			if (!variant.getModuleSlots().contains(slot)) throw new IllegalArgumentException("Unknown module slot: " + slot);
			Object value = modules.get(slot);
			JSONObject moduleJson;
			String moduleId;
			if (value instanceof JSONObject object) {
				moduleJson = object;
				moduleId = object.getString("variantId");
			} else if (value instanceof String reference) {
				moduleId = reference;
				ShipVariantAPI module = Global.getSettings().getVariant(reference);
				if (module == null) throw new IllegalArgumentException("Unknown module variant: " + reference);
				moduleJson = snapshot(module, Collections.newSetFromMap(new IdentityHashMap<>()));
			} else {
				throw new IllegalArgumentException("Invalid module: " + slot);
			}
			if (!moduleIds.add(moduleId)) throw new IllegalArgumentException("Cyclic module variant: " + moduleId);
			variant.setModuleVariant(slot, fromJson(moduleJson, moduleId).build(moduleIds));
			moduleIds.remove(moduleId);
		}
	}

	private static JSONObject copy(JSONObject json) {
		try {
			return new JSONObject(json.toString());
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Invalid variant JSON", exception);
		}
	}
}
