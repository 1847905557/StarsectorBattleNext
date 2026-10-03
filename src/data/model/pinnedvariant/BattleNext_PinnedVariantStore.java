package data.model.pinnedvariant;

import data.scripts.tools.BattleNext_Logger;

import com.fs.starfarer.api.combat.ShipVariantAPI;
import data.model.BattleNext_BaseLoader;
import data.model.BattleNext_BaseStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantSaver;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantStandardLoader;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class BattleNext_PinnedVariantStore extends BattleNext_BaseStore<BattleNext_PinnedVariantModel> {

	private final BattleNext_PinnedVariantStandardLoader loader = new BattleNext_PinnedVariantStandardLoader();
	private final BattleNext_PinnedVariantSaver saver = new BattleNext_PinnedVariantSaver();

	public BattleNext_PinnedVariantStore() {
		super("Pinned variant catalog has not been loaded");
	}

	@Override
	public void save(BattleNext_PinnedVariantModel model) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("model", model).build();
		BattleNext_Logger.operationStarted(BattleNext_PinnedVariantStore.class, "variant.pin.commit", input);
		try {
			requireLoaded();
			if (model == null || !BattleNext_EntryId.isValidPathSegment(model.getPinId())) throw new IllegalArgumentException("Invalid pin ID");
			if (model.getDisplayName().isEmpty()) throw new IllegalArgumentException("Pinned variant display name is required");
			BattleNext_PinnedVariantModel existing = get(model.getPinId());
			if (existing != null && !existing.getVariantJson().toString().equals(model.getVariantJson().toString())) {
				throw new IllegalArgumentException("Pinned variant snapshot cannot be replaced: " + model.getPinId());
			}
			if (existing == null && BattleNext_ModFileManager.exists("tournament/_pinnedVariants/" + model.getPinId() + ".json")) {
				throw new IllegalArgumentException("Pinned variant file already exists: " + model.getPinId());
			}
			model.toBuilder().build();
			super.save(model);
			BattleNext_Logger.operationSucceeded(BattleNext_PinnedVariantStore.class, "variant.pin.commit",
					BattleNext_Logger.fields().put("model", model).build());
		} catch (IOException | JSONException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(BattleNext_PinnedVariantStore.class, "variant.pin.commit.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(BattleNext_PinnedVariantStore.class, "variant.pin.commit", input, exception);
			}
			throw exception;
		}
	}

	public BattleNext_PinnedVariantModel saveVariant(String displayName, ShipVariantAPI variant)
			throws IOException, JSONException {
		requireLoaded();
		String pinId = "pin_" + UUID.randomUUID().toString().replace("-", "");
		BattleNext_PinnedVariantModel model = BattleNext_PinnedVariantModel.fromVariant(pinId, displayName, variant);
		save(model);
		return model;
	}

	public void rename(String pinId, String displayName) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("pin_id", pinId).put("display_name", displayName).build();
		BattleNext_Logger.operationStarted(BattleNext_PinnedVariantStore.class, "variant.pin.rename", input);
		try {
			requireLoaded();
			BattleNext_PinnedVariantModel model = get(pinId);
			if (model == null) throw new IllegalArgumentException("Unknown pinned variant: " + pinId);
			BattleNext_PinnedVariantModel renamed = new BattleNext_PinnedVariantModel(pinId, displayName, model.getVariantJson());
			save(renamed);
			BattleNext_Logger.operationSucceeded(BattleNext_PinnedVariantStore.class, "variant.pin.rename",
					BattleNext_Logger.fields().put("pin_id", pinId).put("display_name", displayName).build());
		} catch (IOException | JSONException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(BattleNext_PinnedVariantStore.class, "variant.pin.rename.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(BattleNext_PinnedVariantStore.class, "variant.pin.rename", input, exception);
			}
			throw exception;
		}
	}

	public ShipVariantAPI resolve(String pinId) {
		requireLoaded();
		BattleNext_PinnedVariantModel model = get(pinId);
		if (model == null) throw new IllegalArgumentException("Unknown pinned variant: " + pinId);
		return model.toBuilder().build();
	}

	@Override
	protected boolean isManaged(BattleNext_PinnedVariantModel model) {
		return model != null && model.getSourcePath().equals("tournament/_pinnedVariants/" + model.getPinId() + ".json");
	}

	public List<String> getLoadErrors() {
		return loader.getLoadErrors();
	}

	public List<BattleNext_PinnedVariantStandardLoader.CatalogEntry> getCatalog() {
		return loader.getCatalog();
	}

	public void deleteFile(String path) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("path", path).build();
		BattleNext_Logger.operationStarted(BattleNext_PinnedVariantStore.class, "variant.pin.file.delete", input);
		try {
			requireLoaded();
			String root = "tournament/_pinnedVariants/";
			if (!path.startsWith(root) || !path.endsWith(".json")) throw new IllegalArgumentException("Invalid pin file: " + path);
			String file = path.substring(root.length());
			if (file.contains("/") || file.contains("\\") || file.equals(".json")) throw new IllegalArgumentException("Invalid pin file: " + path);
			if (loader.getCatalog().stream().noneMatch(entry -> entry.path().equals(path))) {
				throw new IllegalArgumentException("Pin file is outside the loaded catalog: " + path);
			}
			BattleNext_ModFileManager.delete(path);
			reload();
			BattleNext_Logger.operationSucceeded(BattleNext_PinnedVariantStore.class, "variant.pin.file.delete",
					BattleNext_Logger.fields().put("path", path).build());
		} catch (IOException | JSONException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(BattleNext_PinnedVariantStore.class, "variant.pin.file.delete.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(BattleNext_PinnedVariantStore.class, "variant.pin.file.delete", input, exception);
			}
			throw exception;
		}
	}

	@Override
	protected BattleNext_PinnedVariantSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_PinnedVariantModel>> sources() {
		return List.of(loader);
	}
}
