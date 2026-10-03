package data.model;

import data.scripts.tools.BattleNext_EntryId;

import org.json.JSONException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class BattleNext_BaseStore<T extends BattleNext_BaseModel> {

	private final Map<String, T> entries = new LinkedHashMap<>();
	private String loadError;

	protected BattleNext_BaseStore(String notLoadedMessage) {
		loadError = notLoadedMessage;
	}

	public void reload() throws IOException, JSONException {
		try {
			Map<String, T> loaded = new LinkedHashMap<>();
			for (BattleNext_BaseLoader<T> loader : sources()) {
				for (T model : loader.load()) {
					mergeLoaded(loaded, model);
				}
			}
			loadState();
			replaceEntries(loaded);
			succeedLoad();
		} catch (IOException | JSONException | RuntimeException exception) {
			recordFailure(exception);
			throw exception;
		}
	}

	public void save(T model) throws IOException, JSONException {
		requireLoaded();
		if (model == null || !BattleNext_EntryId.isValidPathSegment(model.getId())) {
			throw new IllegalArgumentException("Entry id is required");
		}
		register(model, saver().save(model));
	}

	public void delete(String id) throws IOException, JSONException {
		requireLoaded();
		T model = get(id);
		if (!isManaged(model)) {
			throw new IllegalStateException("Entry is not managed: " + id);
		}
		saver().delete(model);
		removeEntry(id);
		onEntryRemoved(id);
	}

	public boolean isLoaded() {
		return loadError.isEmpty();
	}

	public String getLoadError() {
		return loadError;
	}

	public T get(String id) {
		return entries.get(id);
	}

	public List<T> getAll() {
		return List.copyOf(entries.values());
	}

	protected void requireLoaded() {
		if (!isLoaded()) throw new IllegalStateException(loadError);
	}

	protected abstract BattleNext_BaseSaver<T> saver();

	protected abstract List<BattleNext_BaseLoader<T>> sources();

	protected void mergeLoaded(Map<String, T> loaded, T model) throws IOException {
		loaded.put(model.getId(), model);
	}

	protected boolean isManaged(T model) {
		return model != null && model.getSourceType() == BattleNext_LoadSource.CREATED;
	}

	protected void loadState() throws IOException, JSONException {

	}

	protected void onEntryRemoved(String id) throws IOException, JSONException {

	}

	private void replaceEntries(Map<String, T> loaded) {
		entries.clear();
		entries.putAll(loaded);
	}

	private void removeEntry(String id) {
		entries.remove(id);
	}

	private void succeedLoad() {
		loadError = "";
	}

	private void recordFailure(Exception exception) {
		String message = exception.getMessage();
		loadError = message == null || message.isBlank() ? exception.toString() : message;
	}

	private void register(T model, String path) {
		model.setSourceType(BattleNext_LoadSource.CREATED);
		model.setSourcePath(path);
		entries.put(model.getId(), model);
	}
}
