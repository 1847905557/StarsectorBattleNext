package data.model;

import data.test.fixture.BattleNext_TestGameFixture;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

class BattleNext_CatalogLoaderTest extends BattleNext_TestGameFixture {

	@Test
	void catalogPreservesOrderMarksSourcesAndFiltersEmptyIdentities() throws Exception {
		MemoryLoader loader = new MemoryLoader();
		loader.paths = List.of("first", "empty", "last");
		writeEntries(loader.paths);
		List<Entry> entries = loader.load();
		assertEquals(List.of("first", "empty", "last"), loader.parsed);
		assertEquals(List.of("first", "last"), entries.stream().map(Entry::getId).toList());
		assertEquals(BattleNext_LoadSource.STANDARD, entries.get(0).getSourceType());
		assertEquals("last", entries.get(1).getSourcePath());
		loader.paths = List.of();
		assertEquals(List.of(), loader.load());
	}

	@ParameterizedTest
	@ValueSource(strings = {"io", "json", "argument"})
	void parseFailureRetainsCauseAndIdentifiesTheResource(String kind) throws Exception {
		MemoryLoader loader = new MemoryLoader();
		loader.paths = List.of("first", "broken", "last");
		loader.failure = kind;
		writeEntries(loader.paths);
		assertEquals(List.of("first", "last"), loader.load().stream().map(Entry::getId).toList());
		assertEquals("broken", loader.getErrors().get(0).path());
		assertEquals("broken", loader.getErrors().get(0).id());
		assertTrue(loader.getErrors().get(0).diagnostic().contains(kind));
		assertEquals(List.of("first", "broken", "last"), loader.parsed);
	}

	@Test
	void directoryFailurePropagatesBeforeParsing() {
		MemoryLoader loader = new MemoryLoader();
		loader.failDirectory = true;
		assertThrows(IOException.class, loader::load);
		assertEquals(List.of(), loader.parsed);
	}

	@Test
	void sourceMetadataIsExplicitAndIndependentOfIdentity() {
		Entry entry = new Entry("identity");
		assertEquals(BattleNext_LoadSource.CREATED, entry.getSourceType());
		assertEquals("", entry.getSourcePath());
		entry.setSourceType(BattleNext_LoadSource.LEGACY);
		entry.setSourcePath("path.csv");
		assertEquals("identity", entry.getId());
		assertEquals(BattleNext_LoadSource.LEGACY, entry.getSourceType());
		assertEquals("path.csv", entry.getSourcePath());
	}

	private static class Entry extends BattleNext_BaseModel {
		private final String id;

		private Entry(String id) {
			this.id = id;
		}

		@Override
		public String getId() {
			return id;
		}
	}

	private static class MemoryLoader extends BattleNext_CatalogLoader<Entry> {
		private List<String> paths = List.of();
		private final List<String> parsed = new ArrayList<>();
		private String failure = "";
		private boolean failDirectory;

		@Override
		protected List<String> catalogPaths() throws IOException {
			if (failDirectory) throw new IOException("directory");
			return paths;
		}

		@Override
		protected Entry parse(JSONObject json) throws IOException, JSONException {
			String path = json.getString("id");
			parsed.add(path);
			if (path.equals("broken")) {
				if (failure.equals("io")) throw new IOException(failure);
				if (failure.equals("json")) throw new JSONException(failure);
				throw new IllegalArgumentException(failure);
			}
			return new Entry(path.equals("empty") ? "" : path);
		}

		@Override
		protected BattleNext_LoadSource source() {
			return BattleNext_LoadSource.STANDARD;
		}

		@Override
		protected String kind() {
			return "test catalog";
		}
	}

	private void writeEntries(List<String> paths) throws Exception {
		for (String path : paths) {
			Files.writeString(directory.resolve(path), new JSONObject().put("id", path).toString(), StandardCharsets.UTF_8);
		}
	}
}
