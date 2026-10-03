package data.model.pinnedvariant.io;

import data.scripts.tools.BattleNext_Logger;

import com.fs.starfarer.api.Global;
import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class BattleNext_PinnedVariantStandardLoader extends BattleNext_CatalogLoader<BattleNext_PinnedVariantModel> {

	private static final String RESOURCE_FOLDER = "tournament/_pinnedVariants";
	private final List<String> loadErrors = new ArrayList<>();
	private List<CatalogEntry> catalog = List.of();

	public record CatalogEntry(String path, String name, String pinId, String hullId,
			BattleNext_PinnedVariantModel model, String error) {

	}

	public List<CatalogEntry> getCatalog() {
		return catalog;
	}

	@Override
	public List<BattleNext_PinnedVariantModel> load() throws IOException, JSONException {

		List<String> errors = new ArrayList<>();
		List<BattleNext_PinnedVariantModel> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		List<CatalogEntry> entries = new ArrayList<>();
		for (String path : catalogPaths()) {
			String name = path.substring(path.lastIndexOf('/') + 1);
			String pinId = "";
			String hullId = "";
			try {
				JSONObject json = BattleNext_ModFileManager.readJson(BattleNext_PinnedVariantStandardLoader.class, path, MOD_ID);
				name = json.optString("displayName", name);
				if (name.isBlank()) {
					name = path.substring(path.lastIndexOf('/') + 1);
					BattleNext_Logger.warn(BattleNext_PinnedVariantStandardLoader.class, "variant.pin.name.fallback",
				BattleNext_Logger.fields().put("path", path).put("input", json).put("name", name).build());
				}
				pinId = json.optString("pinId", "");
				JSONObject variant = json.optJSONObject("variantJson");
				hullId = variant == null ? "" : variant.optString("hullId", "");
				BattleNext_PinnedVariantModel model = new BattleNext_PinnedVariantModel();
				model.fromJson(json);
				if (!path.equals(RESOURCE_FOLDER + "/" + model.getPinId() + ".json")) {
					throw new IllegalArgumentException("Pin ID does not match file name");
				}
				if (!ids.add(model.getId())) throw new IllegalArgumentException("Duplicate pin ID: " + model.getId());
				markSource(model, path);
				model.toBuilder().build();
				result.add(model);
				entries.add(new CatalogEntry(path, name, pinId, hullId, model, ""));
			} catch (IOException | JSONException | RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_PinnedVariantStandardLoader.class, "variant.pin.load.degraded",
				BattleNext_Logger.fields().put("path", path).put("pin_id", pinId).put("name", name).put("hull_id", hullId).build(), exception);
				String error = exception.toString();
				errors.add(path + ": " + error);
				entries.add(new CatalogEntry(path, name, pinId, hullId, null, error));
			}
		}
		catalog = List.copyOf(entries);
		loadErrors.clear();
		loadErrors.addAll(errors);
		return result;
	}

	public List<String> getLoadErrors() {
		return List.copyOf(loadErrors);
	}

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		return BattleNext_ModFileManager.listJson(RESOURCE_FOLDER);
	}

	@Override
	protected BattleNext_PinnedVariantModel parse(String path) throws IOException, JSONException {
		BattleNext_PinnedVariantModel model = new BattleNext_PinnedVariantModel();
		model.fromJson(BattleNext_ModFileManager.readJson(BattleNext_PinnedVariantStandardLoader.class, path, MOD_ID));
		return model;
	}

	@Override
	protected BattleNext_LoadSource source() {
		return BattleNext_LoadSource.STANDARD;
	}

	@Override
	protected String kind() {
		return "pinned variant";
	}
}
