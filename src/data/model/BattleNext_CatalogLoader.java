package data.model;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class BattleNext_CatalogLoader<T extends BattleNext_BaseModel> extends BattleNext_BaseLoader<T> {

	@Override
	public List<T> load() throws IOException, JSONException {
		List<T> result = new ArrayList<>();
		for (String path : catalogPaths()) {
			T value;
			try {
				value = parse(path);
			} catch (IOException | JSONException | IllegalArgumentException exception) {
				throw new IllegalStateException("Unable to load " + kind() + ": " + path, exception);
			}
			if (value.getId().isEmpty()) continue;
			markSource(value, path);
			result.add(value);
		}
		return result;
	}

	protected abstract List<String> catalogPaths() throws IOException, JSONException;

	protected abstract T parse(String path) throws IOException, JSONException;

	protected abstract String kind();
}
