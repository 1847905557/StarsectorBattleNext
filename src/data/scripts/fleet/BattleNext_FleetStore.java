package data.scripts.fleet;

import com.fs.starfarer.api.Global;
import data.scripts.fleet.io.BattleNext_FleetCommonLoader;
import data.scripts.tools.BattleNext_LoadSource;
import data.scripts.fleet.io.BattleNext_FleetSaver;
import data.scripts.fleet.io.BattleNext_FleetStandardLoader;
import data.scripts.fleet.io.BattleNext_FleetLegacyLoader;
import org.json.JSONException;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_FleetStore {

	private static final String LEGACY_ROOT = "tournament";
	private static final String MOD_ID = "AI_Battles_Next";

	private final BattleNext_FleetStandardLoader standardLoader;
	private final BattleNext_FleetCommonLoader commonLoader;
	private final BattleNext_FleetLegacyLoader legacyLoader;
	private final BattleNext_FleetSaver saver;
	private final Map<String, BattleNext_FleetModel> fleets = new LinkedHashMap<>();

	public BattleNext_FleetStore() {
		standardLoader = new BattleNext_FleetStandardLoader();
		commonLoader = new BattleNext_FleetCommonLoader();
		legacyLoader = new BattleNext_FleetLegacyLoader();
		saver = new BattleNext_FleetSaver();
	}

	public void reload() {
		reload(loadLegacy());
	}

	private List<BattleNext_FleetModel> loadLegacy() {
		try {
			return legacyLoader.loadResourceDirectory(LEGACY_ROOT, MOD_ID);
		} catch (IOException | JSONException exception) {
			Global.getLogger(BattleNext_FleetStore.class).warn("Unable to load legacy fleets from " + LEGACY_ROOT, exception);
			return List.of();
		}
	}

	public void reload(List<BattleNext_FleetModel> legacy) {
		fleets.clear();
		putIfAbsent(legacy);
		putIfAbsent(standardLoader.load());
		putOverride(commonLoader.load());
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
		fleet.setSourceType(BattleNext_LoadSource.COMMON);
		fleet.setSourcePath(path);
		fleets.put(fleet.getId(), fleet);
	}

	public void delete(BattleNext_FleetModel fleet) throws IOException {
		if (fleet == null || !BattleNext_LoadSource.COMMON.equals(fleet.getSourceType())) return;
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
