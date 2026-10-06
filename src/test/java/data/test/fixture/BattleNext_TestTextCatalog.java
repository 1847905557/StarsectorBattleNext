package data.test.fixture;

import data.scripts.tools.BattleNext_Settings;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BattleNext_TestTextCatalog {

	public record TextKey(String category, String id) { }

	private static final Path PATH = Path.of("data/strings/strings.json");
	private static final Map<TextKey, String> ENTRIES = load();

	private BattleNext_TestTextCatalog() {

	}

	public static String text(Object category, Object id) {
		String domain = category.toString();
		if (domain.endsWith("_CN")) {
			domain = domain.substring(0, domain.length() - 3);
		}
		TextKey key = new TextKey(BattleNext_Settings.isCnTextEnabled() ? domain + "_CN" : domain, id.toString());
		assertTrue(ENTRIES.containsKey(key), "Missing text: " + key);
		return ENTRIES.get(key);
	}

	public static Map<TextKey, String> entries() {
		return ENTRIES;
	}

	public static String source() throws IOException {
		byte[] bytes = Files.readAllBytes(PATH);
		assertFalse(bytes.length >= 3 && bytes[0] == (byte) 0xef
				&& bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf);
		return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
	}

	private static Map<TextKey, String> load() {
		try {
			JSONObject json = new JSONObject(source());
			Map<TextKey, String> entries = new LinkedHashMap<>();
			for (String category : keys(json)) {
				JSONObject texts = json.getJSONObject(category);
				for (String id : keys(texts)) {
					TextKey key = new TextKey(category, id);
					assertNull(entries.put(key, texts.getString(id)), key.toString());
				}
			}
			return Collections.unmodifiableMap(entries);
		} catch (Exception exception) {
			throw new AssertionError("Unable to load text catalog", exception);
		}
	}

	private static List<String> keys(JSONObject json) {
		String[] keys = JSONObject.getNames(json);
		assertNotNull(keys);
		return Arrays.stream(keys).sorted().toList();
	}
}
