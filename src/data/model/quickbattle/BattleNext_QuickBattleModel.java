package data.model.quickbattle;


import data.model.BattleNext_CsvModel;
import data.scripts.tools.BattleNext_CsvLine;

import java.util.List;

public class BattleNext_QuickBattleModel extends BattleNext_CsvModel {

	public enum QuickBattleRowType {
		BATTLE,
		COMMENT_BATTLE,
		RAW
	}

	private static final String HEADER = "player,enemy,map,size";

	private String id = "";
	private String player;
	private String enemy;
	private int map;
	private float mapScale;
	private String rawContent = "";
	private QuickBattleRowType rowType = QuickBattleRowType.BATTLE;
	private boolean enabled = true;
	private boolean legacyIds;

	public BattleNext_QuickBattleModel(String player, String enemy, int map, float mapScale) {
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.map = map;
		this.mapScale = mapScale;
	}

	public static BattleNext_QuickBattleModel fromCsvRow(String line) {
		BattleNext_QuickBattleModel model = parseLine(line, false);
		return model != null && model.rowType == QuickBattleRowType.BATTLE ? model : null;
	}

	public static BattleNext_QuickBattleModel parseLine(String line, boolean legacy) {
		String original = line == null ? "" : line;
		String trimmed = original.trim();
		if (trimmed.equalsIgnoreCase(HEADER)) return null;
		boolean commented = trimmed.startsWith("#");
		String candidate = commented ? trimmed.substring(1).trim() : original;
		BattleNext_QuickBattleModel model = new BattleNext_QuickBattleModel("", "", -1, 1f);
		try {
			model.fromCsvLine(BattleNext_CsvLine.parse(candidate));
			if (legacy) model.convertLegacyIds();
			model.rowType = commented ? QuickBattleRowType.COMMENT_BATTLE : QuickBattleRowType.BATTLE;
			model.enabled = !commented;
			model.legacyIds = legacy;
			model.rawContent = commented ? normalizeComment(candidate) : "";
			return model;
		} catch (IllegalArgumentException exception) {
			model.rowType = QuickBattleRowType.RAW;
			model.enabled = false;
			model.legacyIds = legacy;
			model.rawContent = normalizeComment(candidate);
			return model;
		}
	}

	private static String normalizeComment(String value) {
		String trimmed = value == null ? "" : value.trim();
		while (trimmed.startsWith("#")) {
			trimmed = trimmed.substring(1).trim();
		}
		return trimmed.isEmpty() ? "#" : "# " + trimmed;
	}

	private void convertLegacyIds() {
		player = "ab_player" + player;
		enemy = "ab_player" + enemy;
	}

	public BattleNext_QuickBattleModel copy() {
		BattleNext_QuickBattleModel copy = new BattleNext_QuickBattleModel(player, enemy, map, mapScale);
		copy.id = id;
		copy.rawContent = rawContent;
		copy.rowType = rowType;
		copy.enabled = enabled;
		copy.legacyIds = legacyIds;
		copy.setSourceType(getSourceType());
		copy.setSourcePath(getSourcePath());
		return copy;
	}

	public void promoteRaw(String value) {
		BattleNext_QuickBattleModel parsed = parseLine(value, legacyIds);
		if (parsed != null && parsed.rowType != QuickBattleRowType.RAW) {
			player = parsed.player;
			enemy = parsed.enemy;
			map = parsed.map;
			mapScale = parsed.mapScale;
			rowType = parsed.rowType;
			enabled = false;
			rawContent = parsed.rawContent;
		} else {
			rowType = QuickBattleRowType.RAW;
			enabled = false;
			rawContent = normalizeComment(value);
		}
	}

	@Override
	public void fromCsvLine(List<String> fields) {
		if (fields.size() != 4) throw new IllegalArgumentException("Quick battle row must have 4 columns");
		int parsedMap;
		float parsedSize;
		try {
			parsedMap = Integer.parseInt(fields.get(2).trim());
			parsedSize = Float.parseFloat(fields.get(3).trim());
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Invalid quick battle row numbers", exception);
		}
		if (fields.get(0).trim().isEmpty() || fields.get(1).trim().isEmpty()
				|| parsedMap < -1 || parsedMap > 11 || !Float.isFinite(parsedSize) || parsedSize <= 0f) {
			throw new IllegalArgumentException("Invalid quick battle row");
		}
		player = fields.get(0).trim();
		enemy = fields.get(1).trim();
		map = parsedMap;
		mapScale = parsedSize;
	}

	@Override
	public String toCsvLine() {
		return BattleNext_CsvLine.escape(player) + "," + BattleNext_CsvLine.escape(enemy) + "," + map + "," + mapScale;
	}

	public String toSourceLine() {
		if (rowType == QuickBattleRowType.RAW) return rawContent.isEmpty() ? "#" : rawContent;
		String line = toCsvLine();
		return enabled ? line : "# " + line;
	}

	@Override
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id == null ? "" : id.trim();
	}

	public String getPlayer() {
		return player;
	}

	public void setPlayer(String player) {
		this.player = player == null ? "" : player.trim();
	}

	public String getEnemy() {
		return enemy;
	}

	public void setEnemy(String enemy) {
		this.enemy = enemy == null ? "" : enemy.trim();
	}

	public int getMap() {
		return map;
	}

	public void setMap(int map) {
		this.map = map;
	}

	public float getMapScale() {
		return mapScale;
	}

	public void setMapScale(float mapScale) {
		this.mapScale = mapScale;
	}

	public String getRawContent() {
		return rawContent;
	}

	public void setRawContent(String rawContent) {
		this.rawContent = normalizeComment(rawContent);
	}

	public QuickBattleRowType getRowType() {
		return rowType;
	}

	public void setRowType(QuickBattleRowType rowType) {
		this.rowType = rowType == null ? QuickBattleRowType.RAW : rowType;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean usesLegacyIds() {
		return legacyIds;
	}

	public void setLegacyIds(boolean legacyIds) {
		this.legacyIds = legacyIds;
	}
}
