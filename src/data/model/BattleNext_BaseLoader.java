package data.model;

import org.json.JSONException;

import java.io.IOException;
import java.util.List;

public abstract class BattleNext_BaseLoader<T extends BattleNext_BaseModel> {

	protected static final String MOD_ID = "AI_Battles_Next";

	protected BattleNext_BaseLoader() {}

	public abstract List<T> load() throws IOException, JSONException;

	protected abstract BattleNext_LoadSource source();

	protected void markSource(T model, String path) {
		model.setSourceType(source());
		model.setSourcePath(path);
	}
}
