package data.scripts.fleet;

import data.scripts.fleet.io.BattleNext_FleetLoader;
import data.scripts.fleet.io.BattleNext_FleetSaver;
import org.json.JSONException;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_FleetStore {

	private final BattleNext_FleetLoader loader;
	private final BattleNext_FleetSaver saver;
	private final Map<String, BattleNext_FleetModel> fleets = new LinkedHashMap<>();

	public BattleNext_FleetStore() {
		loader = new BattleNext_FleetLoader();
		saver = new BattleNext_FleetSaver();
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
		String path = saver.save(fleet);
		fleet.setSource("user");
		fleet.setSourcePath(path);
		fleets.put(fleet.getId(), fleet);
	}

	public void delete(BattleNext_FleetModel fleet) throws IOException {
		if (fleet == null || !"user".equals(fleet.getSource())) return;
		saver.delete(fleet);
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
