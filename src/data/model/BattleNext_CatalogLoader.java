package data.model;

import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public abstract class BattleNext_CatalogLoader<T extends BattleNext_BaseModel> extends BattleNext_BaseLoader<T> {

	@Override
	public List<T> load() throws IOException, JSONException {
		beginLoad();
		List<T> result = new ArrayList<>();
		for (String path : catalogPaths()) {
			BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("path", path).put("mod_id", MOD_ID).put("kind", kind()).build();
			BattleNext_Logger.operationStarted(getClass(), "resource.load", input);
			T value;
			String raw = "";
			String id = "";
			try {
				raw = new String(BattleNext_ModFileManager.readBytes(path), StandardCharsets.UTF_8);
				JSONObject json = BattleNext_ModFileManager.readJson(getClass(), path, MOD_ID);
				id = json.optString("id", "").trim();
				value = parse(json);
				if (value.getId().isEmpty()) throw new IllegalArgumentException("Entry ID is required");
			} catch (IOException | JSONException | IllegalArgumentException exception) {
				reject(path, -1, id, raw, exception);
				continue;
			}
			markSource(value, path);
			recordInput(path, -1, value.getId(), raw);
			result.add(value);
			BattleNext_Logger.operationSucceeded(getClass(), "resource.load",
					() -> BattleNext_Logger.fields().put("path", path).put("model", value).build());
		}
		return finishLoad(result);
	}

	protected abstract List<String> catalogPaths() throws IOException, JSONException;

	protected abstract T parse(JSONObject json) throws IOException, JSONException;

	protected abstract String kind();
}
