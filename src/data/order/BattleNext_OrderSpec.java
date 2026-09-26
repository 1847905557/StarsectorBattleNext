package data.order;

public class BattleNext_OrderSpec {

	private final String id;
	private final String type;
	private final String script;
	private final String description;

	public BattleNext_OrderSpec(String id, String type, String script, String description) {
		this.id = id;
		this.type = type;
		this.script = script;
		this.description = description;
	}
	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getScript() {
		return script;
	}

	public String getDescription() {
		return description;
	}

}
