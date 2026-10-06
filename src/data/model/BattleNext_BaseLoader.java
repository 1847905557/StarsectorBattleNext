package data.model;

import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BattleNext_BaseLoader<T extends BattleNext_BaseModel> {

	protected static final String MOD_ID = "AI_Battles_Next";
	private final List<LoadError> errors = new ArrayList<>();
	private final List<LoadError> inputs = new ArrayList<>();

	public record LoadError(String path, int position, String id, String raw, String diagnostic) { }

	public List<LoadError> getErrors() {
		return List.copyOf(errors);
	}

	protected void beginLoad() {
		errors.clear();
		inputs.clear();
	}

	protected void recordInput(String path, int position, String id, String raw) {
		inputs.add(new LoadError(path, position, id, raw, ""));
	}

	public List<LoadError> getInputs() {
		return List.copyOf(inputs);
	}

	protected void reject(String path, int position, String id, String raw, Exception exception) {
		errors.add(new LoadError(path, position, id, raw, exception.toString()));
		BattleNext_Logger.warn(getClass(), "catalog.item.rejected", BattleNext_Logger.fields()
				.put("path", path).put("position", position).put("id", id).put("raw", raw).build(), exception);
	}

	protected List<T> finishLoad(List<T> models) {
		Map<String, Integer> counts = new HashMap<>();
		for (T model : models) {
			counts.merge(model.getId(), 1, Integer::sum);
		}
		for (LoadError error : errors) {
			if (error.id().isEmpty()) continue;
			counts.merge(error.id(), 1, Integer::sum);
		}
		List<T> accepted = new ArrayList<>();
		for (T model : models) {
			if (counts.get(model.getId()) > 1) {
				LoadError input = inputs.stream().filter(value -> value.path().equals(model.getSourcePath())).findFirst()
						.orElseThrow();
				reject(input.path(), input.position(), model.getId(), input.raw(), new IllegalArgumentException("Duplicate ID: " + model.getId()));
				continue;
			}
			accepted.add(model);
		}
		return accepted;
	}

	protected BattleNext_BaseLoader() {}

	protected List<String> paths(String directory, boolean recursive) {
		try {
			return BattleNext_ModFileManager.list(directory, recursive);
		} catch (IOException exception) {
			reject(directory, -1, "", "", exception);
			return List.of();
		}
	}

	public abstract List<T> load() throws IOException, JSONException;

	protected abstract BattleNext_LoadSource source();

	protected void markSource(T model, String path) {
		model.setSourceType(source());
		model.setSourcePath(path);
	}
}
