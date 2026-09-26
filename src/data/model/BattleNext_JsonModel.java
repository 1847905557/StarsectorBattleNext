package data.model;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class BattleNext_JsonModel extends BattleNext_BaseModel {

	public abstract void fromJson(JSONObject json) throws JSONException;

	public abstract JSONObject toJson() throws JSONException;
}
