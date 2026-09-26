package data.model;

import com.fs.starfarer.api.Global;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public abstract class BattleNext_BaseSaver<T extends BattleNext_BaseModel> {

	protected BattleNext_BaseSaver() {}

	public abstract String save(T model) throws IOException, JSONException;

	public abstract void delete(T model) throws IOException;

	protected void writeCommonJson(String path, JSONObject json) throws IOException, JSONException {
		Global.getSettings().writeJSONToCommon(path, json, false);
	}
}
