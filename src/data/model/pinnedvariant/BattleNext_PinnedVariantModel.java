package data.model.pinnedvariant;

import com.fs.starfarer.api.combat.ShipVariantAPI;
import data.model.BattleNext_JsonModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_EntryId;
import org.json.JSONException;
import org.json.JSONObject;

public class BattleNext_PinnedVariantModel extends BattleNext_JsonModel {

	public static final int FORMAT_VERSION = 1;

	private String pinId;
	private String displayName;
	private JSONObject variantJson;

	public BattleNext_PinnedVariantModel() {
		this("", "", new JSONObject());
	}

	public BattleNext_PinnedVariantModel(String pinId, String displayName, JSONObject variantJson) {
		if (!pinId.isEmpty() && !BattleNext_EntryId.isValidPathSegment(pinId)) throw new IllegalArgumentException("Invalid pin ID: " + pinId);
		this.pinId = pinId;
		this.displayName = normalize(displayName);
		this.variantJson = copyJson(variantJson);
	}

	public static BattleNext_PinnedVariantModel fromVariant(String pinId, String displayName,
			ShipVariantAPI variant) throws JSONException {
		if (variant == null) throw new IllegalArgumentException("Variant is required");
		return new BattleNext_PinnedVariantModel(pinId, displayName, BattleNext_VariantBuilder.fromVariant(variant).toJson());
	}

	@Override
	public void fromJson(JSONObject json) throws JSONException {
		if (!pinId.isEmpty()) throw new IllegalStateException("Pinned variant snapshot is already initialized");
		int version = json.getInt("formatVersion");
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported pinned variant formatVersion: " + version);
		String id = json.getString("pinId");
		String name = normalize(json.getString("displayName"));
		if (!BattleNext_EntryId.isValidPathSegment(id)) throw new IllegalArgumentException("Invalid pin ID: " + id);
		if (name.isEmpty()) throw new IllegalArgumentException("Pinned variant display name is required");
		JSONObject snapshot = copyJson(json.getJSONObject("variantJson"));
		pinId = id;
		displayName = name;
		variantJson = snapshot;
	}

	@Override
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("pinId", pinId);
		json.put("displayName", displayName);
		json.put("variantJson", copyJson(variantJson));
		return json;
	}

	public BattleNext_VariantBuilder toBuilder() {
		JSONObject json = copyJson(variantJson);
		try {
			json.put("variantId", pinId);
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Invalid pin ID", exception);
		}
		return BattleNext_VariantBuilder.fromJson(json, pinId);
	}

	@Override
	public String getId() {
		return pinId;
	}

	public String getPinId() {
		return pinId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public JSONObject getVariantJson() {
		return copyJson(variantJson);
	}

	private static JSONObject copyJson(JSONObject json) {
		try {
			return json == null ? new JSONObject() : new JSONObject(json.toString());
		} catch (JSONException exception) {
			throw new IllegalArgumentException("Invalid variant JSON", exception);
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
