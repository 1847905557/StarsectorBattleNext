package data.test.fixture;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class BattleNext_TestSpriteCatalog {

	private static final Map<String, String> SPRITES = load();

	private BattleNext_TestSpriteCatalog() {

	}

	public static String path(Object category, Object key) {
		String id = category + "/" + key;
		assertTrue(SPRITES.containsKey(id), "Missing sprite registration: " + id);
		return SPRITES.get(id);
	}

	public static JSONObject read(Path path) throws Exception {
		return new JSONObject(withoutHashComments(Files.readString(path, StandardCharsets.UTF_8)));
	}

	public static String withoutHashComments(String text) {
		StringBuilder result = new StringBuilder();
		boolean quoted = false;
		boolean escaped = false;
		boolean comment = false;
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (comment) {
				if (character == '\n' || character == '\r') {
					comment = false;
					result.append(character);
				}
				continue;
			}
			if (quoted) {
				result.append(character);
				if (escaped) {
					escaped = false;
				} else if (character == '\\') {
					escaped = true;
				} else if (character == '"') {
					quoted = false;
				}
				continue;
			}
			if (character == '#') {
				comment = true;
			} else {
				result.append(character);
				if (character == '"') {
					quoted = true;
				}
			}
		}
		return result.toString();
	}

	private static Map<String, String> load() {
		try {
			Map<String, String> result = new LinkedHashMap<>();
			for (Path path : java.util.List.of(Path.of("..", "..", "starsector-core", "data/config/settings.json"),
					Path.of("data/config/settings.json"))) {
				JSONObject graphics = read(path).getJSONObject("graphics");
				for (java.util.Iterator<?> categories = graphics.keys(); categories.hasNext();) {
					String category = categories.next().toString();
					JSONObject entries = graphics.getJSONObject(category);
					for (java.util.Iterator<?> keys = entries.keys(); keys.hasNext();) {
						String key = keys.next().toString();
						result.put(category + "/" + key, entries.getString(key));
					}
				}
			}
			return Map.copyOf(result);
		} catch (Exception exception) {
			throw new AssertionError("Unable to load sprite registrations", exception);
		}
	}
}
