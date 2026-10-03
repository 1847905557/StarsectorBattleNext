package data.model;

import data.scripts.tools.BattleNext_Logger;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class BattleNext_CatalogLoader<T extends BattleNext_BaseModel> extends BattleNext_BaseLoader<T> {

	@Override
	public List<T> load() throws IOException, JSONException {
		List<T> result = new ArrayList<>();
		for (String path : catalogPaths()) {
			BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("path", path).put("mod_id", MOD_ID).put("kind", kind()).build();
			BattleNext_Logger.operationStarted(getClass(), "resource.load", input);
			T value;
			try {
				value = parse(path);
			} catch (IOException | JSONException | IllegalArgumentException exception) {
				BattleNext_Logger.operationFailed(getClass(), "resource.load", input, exception);
				throw new IllegalStateException("Unable to load " + kind() + ": " + path, exception);
			}
			if (value.getId().isEmpty()) {
				BattleNext_Logger.warn(getClass(), "resource.identity.rejected",
						BattleNext_Logger.fields().put("path", path).put("model", value).build());
				continue;
			}
			markSource(value, path);
			result.add(value);
			BattleNext_Logger.operationSucceeded(getClass(), "resource.load",
					BattleNext_Logger.fields().put("path", path).put("model", value).build());
		}
		return result;
	}

	protected abstract List<String> catalogPaths() throws IOException, JSONException;

	protected abstract T parse(String path) throws IOException, JSONException;

	protected abstract String kind();
}
