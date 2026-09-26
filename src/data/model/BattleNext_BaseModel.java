package data.model;

public abstract class BattleNext_BaseModel {

	private BattleNext_LoadSource sourceType = BattleNext_LoadSource.NATIVE;
	private String sourcePath = "";

	protected BattleNext_BaseModel() {}

	public abstract String getId();

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
