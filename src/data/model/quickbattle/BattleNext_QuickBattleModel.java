package data.model.quickbattle;

import data.model.BattleNext_CsvModel;
import data.scripts.tools.BattleNext_CsvLine;

import java.util.List;

public class BattleNext_QuickBattleModel extends BattleNext_CsvModel {

	private String id = "";
	private String player;
	private String enemy;
	private int map;
	private float size;

	public BattleNext_QuickBattleModel(String player, String enemy, int map, float size) {
		this.player = player == null ? "" : player.trim();
		this.enemy = enemy == null ? "" : enemy.trim();
		this.map = map;
		this.size = size;
	}

	public static BattleNext_QuickBattleModel fromCsvRow(String line) {
		String trimmed = line.trim();
		if (trimmed.isEmpty() || trimmed.equals("player,enemy,map,size") || trimmed.startsWith("#")) return null;
		BattleNext_QuickBattleModel model = new BattleNext_QuickBattleModel("", "", -1, 1f);
		try {
			model.fromCsvLine(BattleNext_CsvLine.parse(line));
		} catch (IllegalArgumentException exception) {
			return null;
		}
		return model;
	}

	@Override
	public void fromCsvLine(List<String> fields) {
		if (fields.size() != 4) throw new IllegalArgumentException("Quick battle row must have 4 columns");
		int parsedMap;
		float parsedSize;
		try {
			parsedMap = Integer.parseInt(fields.get(2));
			parsedSize = Float.parseFloat(fields.get(3));
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Invalid quick battle row numbers", exception);
		}
		if (!Float.isFinite(parsedSize) || parsedSize <= 0f) {
			throw new IllegalArgumentException("Invalid quick battle row size");
		}
		player = fields.get(0).trim();
		enemy = fields.get(1).trim();
		map = parsedMap;
		size = parsedSize;
	}

	@Override
	public String toCsvLine() {
		return BattleNext_CsvLine.escape(player) + "," + BattleNext_CsvLine.escape(enemy) + "," + map + "," + size;
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

	public float getSize() {
		return size;
	}

	public void setSize(float size) {
		this.size = size;
	}
}
