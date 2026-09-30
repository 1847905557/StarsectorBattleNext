package data.model.pinnedvariant.io;

import data.model.BattleNext_BaseSaver;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;

public class BattleNext_PinnedVariantSaver extends BattleNext_BaseSaver<BattleNext_PinnedVariantModel> {

	private static final String RESOURCE_ROOT = "tournament/_pinnedVariants/";

	@Override
	public String save(BattleNext_PinnedVariantModel model) throws IOException, JSONException {
		if (!BattleNext_EntryId.isValidPathSegment(model.getPinId())) {
			throw new IOException("Invalid pinned variant ID: " + model.getPinId());
		}
		String path = RESOURCE_ROOT + model.getPinId() + ".json";
		BattleNext_ModFileManager.writeText(path, model.toJson().toString(2));
		return path;
	}

	@Override
	public void delete(BattleNext_PinnedVariantModel model) throws IOException {
		if (!BattleNext_EntryId.isValidPathSegment(model.getPinId())) {
			throw new IOException("Invalid pinned variant ID: " + model.getPinId());
		}
		BattleNext_ModFileManager.delete(RESOURCE_ROOT + model.getPinId() + ".json");
	}
}
