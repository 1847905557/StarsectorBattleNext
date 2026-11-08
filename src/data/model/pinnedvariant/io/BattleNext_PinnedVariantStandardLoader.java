package data.model.pinnedvariant.io;

import data.model.BattleNext_CatalogLoader;
import data.model.BattleNext_LoadSource;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class BattleNext_PinnedVariantStandardLoader extends BattleNext_CatalogLoader<BattleNext_PinnedVariantModel> {

	private static final String RESOURCE_FOLDER = "tournament/_pinnedVariants";
	private final List<String> loadErrors = new ArrayList<>();
	private List<CatalogEntry> catalog = List.of();
	private final Consumer<BattleNext_PinnedVariantModel> validate;

	public BattleNext_PinnedVariantStandardLoader() {
		this(model -> model.toBuilder().build());
	}

	public BattleNext_PinnedVariantStandardLoader(Consumer<BattleNext_PinnedVariantModel> validate) {
		this.validate = validate;
	}

	public record CatalogEntry(String path, String name, String pinId, String hullId,
			BattleNext_PinnedVariantModel model, String error, String raw) {

	}

	public List<CatalogEntry> getCatalog() {
		return catalog;
	}

	@Override
	public void removeSource(String path) {
		super.removeSource(path);
		catalog = catalog.stream().filter(entry -> !entry.path().equals(path)).toList();
		loadErrors.removeIf(error -> error.startsWith(path + ":"));
	}

	public void rejectModel(String id, RuntimeException failure) {
		List<CatalogEntry> updated = new ArrayList<>();
		for (CatalogEntry entry : catalog) {
			if (entry.pinId().equals(id)) {
				updated.add(new CatalogEntry(entry.path(), entry.name(), entry.pinId(), entry.hullId(), null, failure.toString(), entry.raw()));
				loadErrors.add(entry.path() + ": " + failure);
			} else updated.add(entry);
		}
		catalog = List.copyOf(updated);
	}

	@Override
	public List<BattleNext_PinnedVariantModel> load() throws IOException, JSONException {

		beginLoad();
		List<String> errors = new ArrayList<>();
		List<BattleNext_PinnedVariantModel> result = new ArrayList<>();
		List<CatalogEntry> entries = new ArrayList<>();
		List<String> paths;
		try {
			paths = catalogPaths();
		} catch (IOException | JSONException exception) {
			reject(RESOURCE_FOLDER, -1, "", "", exception);
			paths = List.of();
			errors.add(RESOURCE_FOLDER + ": " + exception);
		}
		for (LoadError error : getErrorsCopy()) errors.add(error.path() + ": " + error.diagnostic());
		for (String path : paths) {
			String name = path.substring(path.lastIndexOf('/') + 1);
			String pinId = "";
			String hullId = "";
			String raw = "";
			try {
				raw = new String(BattleNext_ModFileManager.readBytes(path), StandardCharsets.UTF_8);
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
				markSource(model, path);
				validate.accept(model);
				result.add(model);
				recordInput(path, -1, pinId, raw);
				entries.add(new CatalogEntry(path, name, pinId, hullId, model, "", raw));
			} catch (IOException | JSONException | RuntimeException exception) {
				BattleNext_Logger.warn(BattleNext_PinnedVariantStandardLoader.class, "variant.pin.load.degraded",
				BattleNext_Logger.fields().put("path", path).put("pin_id", pinId).put("name", name).put("hull_id", hullId).build(), exception);
				String error = exception.toString();
				errors.add(path + ": " + error);
				reject(path, -1, pinId, raw, exception);
				entries.add(new CatalogEntry(path, name, pinId, hullId, null, error, raw));
			}
		}

		Map<String, Integer> counts = new HashMap<>();
		for (CatalogEntry entry : entries) {
			if (entry.pinId().isEmpty()) continue;
			counts.merge(entry.pinId(), 1, Integer::sum);
		}
		for (int index = 0; index < entries.size(); index++) {
			CatalogEntry entry = entries.get(index);
			if (counts.getOrDefault(entry.pinId(), 0) < 2) continue;
			String error = "Duplicate pin ID: " + entry.pinId();
			entries.set(index, new CatalogEntry(entry.path(), entry.name(), entry.pinId(), entry.hullId(), null, error, entry.raw()));
			errors.add(entry.path() + ": " + error);
		}
		result.removeIf(model -> counts.get(model.getId()) > 1);
		catalog = List.copyOf(entries);
		loadErrors.clear();
		loadErrors.addAll(errors);
		return result;
	}

	public List<String> getLoadErrorsCopy() {
		return List.copyOf(loadErrors);
	}

	@Override
	protected List<String> catalogPaths() throws IOException, JSONException {
		List<String> result = new ArrayList<>();
		for (String path : paths(RESOURCE_FOLDER, false)) {
			if (path.endsWith(".json")) result.add(path);
		}
		return List.copyOf(result);
	}

	@Override
	protected BattleNext_PinnedVariantModel parse(JSONObject json) throws IOException, JSONException {
		BattleNext_PinnedVariantModel model = new BattleNext_PinnedVariantModel();
		model.fromJson(json);
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
