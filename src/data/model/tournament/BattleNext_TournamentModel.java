package data.model.tournament;

import data.model.BattleNext_JsonModel;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import org.json.JSONException;
import org.json.JSONObject;

public class BattleNext_TournamentModel extends BattleNext_JsonModel {

	public enum TournamentBracketType { SINGLE_ELIMINATION, DOUBLE_ELIMINATION, ROUND_ROBIN, KING_OF_THE_HILL }

	public static final int FORMAT_VERSION = 1;
	public static final int MIN_FLEET_COUNT = 2;
	public static final int MAX_FLEET_COUNT = 32;

	private String id;
	private String name;
	private TournamentBracketType bracketType;
	private int boCount;
	private int maxFleet;
	private String ruleId = "";
	private int mapId = -1;

	public BattleNext_TournamentModel() {
		this("", "", TournamentBracketType.SINGLE_ELIMINATION, 1, MIN_FLEET_COUNT);
	}

	public BattleNext_TournamentModel(String id, String name, TournamentBracketType bracketType, int boCount, int maxFleet) {
		this.id = id == null ? "" : id.trim();
		this.name = name == null ? "" : name.trim();
		this.bracketType = bracketType;
		this.boCount = boCount;
		this.maxFleet = maxFleet;
	}

	@Override
	public void fromJson(JSONObject json) throws JSONException {

		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported tournament formatVersion: " + version);
		String parsedId = json.optString("id", "");
		if (BattleNext_StartedTournamentStore.FILE_NAME_WITHOUT_FORMAT.equalsIgnoreCase(parsedId)) throw new IllegalArgumentException("Reserved tournament ID: " + parsedId);
		String parsedName = json.optString("name", "");
		TournamentBracketType parsedBracket;
		try {
			parsedBracket = TournamentBracketType.valueOf(json.getString("bracketType"));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid tournament.bracketType: " + json.getString("bracketType"), exception);
		}
		int parsedBo = json.getInt("boCount");
		int parsedMaxFleet = json.getInt("maxFleet");
		if (parsedBo < 1 || parsedMaxFleet < MIN_FLEET_COUNT || parsedMaxFleet > MAX_FLEET_COUNT) {
			throw new IllegalArgumentException("Invalid tournament limits");
		}
		String parsedRule = json.optString("ruleId", "");
		int parsedMap = json.optInt("mapId", -1);

		setId(parsedId);
		setName(parsedName);
		bracketType = parsedBracket;
		boCount = parsedBo;
		maxFleet = parsedMaxFleet;
		ruleId = parsedRule;
		mapId = parsedMap;
	}

	@Override
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("id", id);
		json.put("name", name);
		json.put("bracketType", bracketType.name());
		json.put("boCount", boCount);
		json.put("maxFleet", maxFleet);
		json.put("ruleId", ruleId);
		json.put("mapId", mapId);
		return json;
	}

	@Override
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id == null ? "" : id.trim();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name == null ? "" : name.trim();
	}

	public TournamentBracketType getBracketType() {
		return bracketType;
	}

	public void setBracketType(TournamentBracketType bracketType) {
		this.bracketType = bracketType;
	}

	public int getBoCount() {
		return boCount;
	}

	public int getWinTarget() {
		return boCount / 2 + 1;
	}

	public void setBoCount(int boCount) {
		this.boCount = boCount;
	}

	public int getMaxFleet() {
		return maxFleet;
	}

	public void setMaxFleet(int maxFleet) {
		this.maxFleet = maxFleet;
	}

	public String getRuleId() {
		return ruleId;
	}

	public void setRuleId(String ruleId) {
		this.ruleId = ruleId == null ? "" : ruleId.trim();
	}

	public int getMapId() {
		return mapId;
	}

	public void setMapId(int mapId) {
		this.mapId = mapId;
	}

}
