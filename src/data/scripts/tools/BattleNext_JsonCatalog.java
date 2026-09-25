package data.scripts.tools;

import java.util.List;

public class BattleNext_JsonCatalog {

	private BattleNext_JsonCatalog() {
	}

	public static List<String> resourceFiles(String directory) {
		return BattleNext_FileCatalog.resources(directory, false).stream()
				.filter(path -> path.endsWith(".json")).toList();
	}

	public static List<String> commonFiles(String directory) {
		return BattleNext_FileCatalog.common(directory, false).stream()
				.filter(path -> path.endsWith(".json.data"))
				.map(path -> path.substring(0, path.length() - 5)).toList();
	}
}
