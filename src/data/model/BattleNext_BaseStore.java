package data.model;

import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
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
		BattleNext_Logger.operationStarted(getClass(), "catalog.reload",
				BattleNext_Logger.fields().put("previous_entries", getAll()).build());
		try {
			Map<String, T> loaded = new LinkedHashMap<>();
			for (BattleNext_BaseLoader<T> loader : sources()) {
				BattleNext_Logger.operationStarted(loader.getClass(), "catalog.source.load",
						BattleNext_Logger.fields().put("store", getClass()).build());
				List<T> models;
				try {
					models = loader.load();
				} catch (IOException | JSONException | RuntimeException exception) {
					BattleNext_Logger.operationFailed(loader.getClass(), "catalog.source.load",
							BattleNext_Logger.fields().put("store", getClass()).build(), exception);
					throw exception;
				}
				BattleNext_Logger.operationSucceeded(loader.getClass(), "catalog.source.load",
						BattleNext_Logger.fields().put("store", getClass()).put("models", models).build());
				for (T model : models) {
					mergeLoaded(loaded, model);
				}
			}
			loadState();
			replaceEntries(loaded);
			succeedLoad();
			BattleNext_Logger.operationSucceeded(getClass(), "catalog.reload",
					BattleNext_Logger.fields().put("entries", getAll()).build());
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(getClass(), "catalog.reload",
					BattleNext_Logger.fields().put("published_entries", getAll()).build(), exception);
			recordFailure(exception);
			throw exception;
		}
	}

	public void save(T model) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("model", model).build();
		BattleNext_Logger.operationStarted(getClass(), "store.save", input);
		try {
			requireLoaded();
			if (model == null || !BattleNext_EntryId.isValidPathSegment(model.getId())) {
				throw new IllegalArgumentException("Entry id is required");
			}
			boolean created = !entries.containsKey(model.getId());
			register(model, saver().save(model));
			BattleNext_Logger.operationSucceeded(getClass(), "store.save",
					BattleNext_Logger.fields().put("action", created ? "created" : "updated").put("model", model).build());
		} catch (IOException | JSONException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(getClass(), "store.save.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(getClass(), "store.save", input, exception);
			}
			throw exception;
		}
	}

	public void delete(String id) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("id", id).put("model", get(id)).build();
		BattleNext_Logger.operationStarted(getClass(), "store.delete", input);
		try {
			requireLoaded();
			T model = get(id);
			if (!isManaged(model)) {
				throw new IllegalStateException("Entry is not managed: " + id);
			}
			saver().delete(model);
			removeEntry(id);
			onEntryRemoved(id);
			BattleNext_Logger.operationSucceeded(getClass(), "store.delete", input);
		} catch (IOException | JSONException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(getClass(), "store.delete.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(getClass(), "store.delete", input, exception);
			}
			throw exception;
		}
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
