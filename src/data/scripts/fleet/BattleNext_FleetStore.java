package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_FleetStore {

	private final BattleNext_FleetLoader loader;
	private final Map<String, BattleNext_FleetModel> fleets = new LinkedHashMap<>();

	public BattleNext_FleetStore() {
		loader = new BattleNext_FleetLoader();
	}

	public void reload() {
		reload(List.of());
	}

	public void reload(List<BattleNext_FleetModel> legacy) {
		fleets.clear();
		putIfAbsent(legacy);
		putIfAbsent(loader.loadBuiltIn());
		putOverride(loader.loadUser());
	}

	public Collection<BattleNext_FleetModel> getAll() {
		return List.copyOf(fleets.values());
	}

	public BattleNext_FleetModel get(String id) {
		return fleets.get(id);
	}

	public void save(BattleNext_FleetModel fleet) throws IOException, JSONException {
		if (fleet == null || fleet.getId().isEmpty()) throw new IllegalArgumentException("Fleet id is required");
		String directory = "AI_Battles_Next/" + fleet.getId() + "/";
		String path = directory + "fleet_data.json";
		if (Global.getSettings().fileExistsInCommon(path)) {
			JSONObject previous = Global.getSettings().readJSONFromCommon(path, false);
			Global.getSettings().writeJSONToCommon(directory + "fleet_data.json.bak", previous, false);
		}
		Global.getSettings().writeJSONToCommon(path, fleet.toJson(), false);
		for (BattleNext_VariantModel variant : fleet.getVariants()) {
			Global.getSettings().writeTextFileToCommon(directory + variant.getId() + ".variant", variant.toJson().toString(2));
		}
		fleet.setSource("user");
		fleet.setSourcePath(path);
		fleets.put(fleet.getId(), fleet);
	}

	public void delete(BattleNext_FleetModel fleet) throws IOException {
		if (fleet == null || !"user".equals(fleet.getSource())) return;
		String directory = "AI_Battles_Next/" + fleet.getId() + "/";
		Global.getSettings().deleteTextFileFromCommon(directory + "fleet_data.json");
		Global.getSettings().deleteTextFileFromCommon(directory + "fleet_data.json.bak");
		for (BattleNext_VariantModel variant : fleet.getVariants()) {
			Global.getSettings().deleteTextFileFromCommon(directory + variant.getId() + ".variant");
		}
		fleets.remove(fleet.getId());
	}

	private void putIfAbsent(List<BattleNext_FleetModel> values) {
		for (BattleNext_FleetModel fleet : values) {
			if (fleet.getId().isEmpty()) continue;
			fleets.putIfAbsent(fleet.getId(), fleet);
		}
	}

	private void putOverride(List<BattleNext_FleetModel> values) {
		for (BattleNext_FleetModel fleet : values) {
			if (!fleet.getId().isEmpty()) fleets.put(fleet.getId(), fleet);
		}
	}
}
