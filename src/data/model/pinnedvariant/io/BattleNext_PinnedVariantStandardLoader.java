package data.model.pinnedvariant.io;

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

	@Override
	public List<BattleNext_PinnedVariantModel> load() throws IOException, JSONException {
		loadErrors.clear();
		List<BattleNext_PinnedVariantModel> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (String path : catalogPaths()) {
			try {
				BattleNext_PinnedVariantModel model = parse(path);
				if (!path.equals(RESOURCE_FOLDER + "/" + model.getPinId() + ".json")) {
					throw new IllegalArgumentException("Pin ID does not match file name");
				}
				if (!ids.add(model.getId())) throw new IllegalArgumentException("Duplicate pin ID: " + model.getId());
				markSource(model, path);
				model.toBuilder().build();
				result.add(model);
			} catch (IOException | JSONException | RuntimeException exception) {
				loadErrors.add(path + ": " + exception.getMessage());
			}
		}
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
		model.fromJson(new JSONObject(BattleNext_ModFileManager.readText(path)));
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
