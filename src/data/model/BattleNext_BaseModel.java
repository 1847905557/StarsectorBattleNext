package data.model;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class BattleNext_BaseModel {

	private BattleNext_LoadSource sourceType = BattleNext_LoadSource.NATIVE;
	private String sourcePath = "";

	public abstract String getId();

	public abstract void fromJson(JSONObject json) throws JSONException;

	public abstract JSONObject toJson() throws JSONException;

	public BattleNext_LoadSource getSourceType() {
		return sourceType;
	}

	public void setSourceType(BattleNext_LoadSource sourceType) {
		this.sourceType = sourceType;
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}
}
