package data.model.pinnedvariant;

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
		requireLoaded();
		BattleNext_PinnedVariantModel model = get(pinId);
		if (model == null) throw new IllegalArgumentException("Unknown pinned variant: " + pinId);
		BattleNext_PinnedVariantModel renamed = new BattleNext_PinnedVariantModel(pinId, displayName, model.getVariantJson());
		save(renamed);
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

	@Override
	protected BattleNext_PinnedVariantSaver saver() {
		return saver;
	}

	@Override
	protected List<BattleNext_BaseLoader<BattleNext_PinnedVariantModel>> sources() {
		return List.of(loader);
	}
}
