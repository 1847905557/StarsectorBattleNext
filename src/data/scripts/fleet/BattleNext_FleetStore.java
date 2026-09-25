package data.scripts.fleet;

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
	private String loadError = "Fleet catalog has not been loaded";

	public BattleNext_FleetStore() {
		standardLoader = new BattleNext_FleetStandardLoader();
		commonLoader = new BattleNext_FleetCommonLoader();
		legacyLoader = new BattleNext_FleetLegacyLoader();
		saver = new BattleNext_FleetSaver();
	}

	public void reload() {
		try {
			reload(loadLegacy());
		} catch (RuntimeException exception) {
			loadError = exception.getMessage();
			throw exception;
		}
	}

	private List<BattleNext_FleetModel> loadLegacy() {
		try {
			return legacyLoader.loadResourceDirectory(LEGACY_ROOT, MOD_ID);
		} catch (IOException | JSONException exception) {
			throw new IllegalStateException("Unable to load legacy fleets from " + LEGACY_ROOT + ": " + exception.getMessage(), exception);
		}
	}

	public void reload(List<BattleNext_FleetModel> legacy) {
		try {
			Map<String, BattleNext_FleetModel> loaded = new LinkedHashMap<>();
			putIfAbsent(loaded, legacy);
			putIfAbsent(loaded, standardLoader.load());
			putOverride(loaded, commonLoader.load());
			fleets.clear();
			fleets.putAll(loaded);
			loadError = "";
		} catch (RuntimeException exception) {
			loadError = exception.getMessage();
			throw exception;
		}
	}

	public String getLoadError() {
		return loadError;
	}

	public boolean isLoaded() {
		return loadError.isEmpty();
	}

	public Collection<BattleNext_FleetModel> getAll() {
		return List.copyOf(fleets.values());
	}

	public BattleNext_FleetModel get(String id) {
		return fleets.get(id);
	}

	public void save(BattleNext_FleetModel fleet) throws IOException, JSONException {
		requireLoaded();
		if (fleet == null || fleet.getId().isEmpty()) throw new IllegalArgumentException("Fleet id is required");
		String path = saver.save(fleet);
		fleet.setSourceType(BattleNext_LoadSource.COMMON);
		fleet.setSourcePath(path);
		fleets.put(fleet.getId(), fleet);
	}

	public void delete(BattleNext_FleetModel fleet) throws IOException {
		requireLoaded();
		if (fleet == null || !BattleNext_LoadSource.COMMON.equals(fleet.getSourceType())) return;
		saver.delete(fleet);
		fleets.remove(fleet.getId());
	}

	private void requireLoaded() {
		if (!isLoaded()) throw new IllegalStateException(loadError);
	}

	private void putIfAbsent(Map<String, BattleNext_FleetModel> target, List<BattleNext_FleetModel> values) {
		for (BattleNext_FleetModel fleet : values) {
			if (fleet.getId().isEmpty()) continue;
			target.putIfAbsent(fleet.getId(), fleet);
		}
	}

	private void putOverride(Map<String, BattleNext_FleetModel> target, List<BattleNext_FleetModel> values) {
		for (BattleNext_FleetModel fleet : values) {
			if (!fleet.getId().isEmpty()) target.put(fleet.getId(), fleet);
		}
	}
}
