package data.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_BaseStoreTest {

	@Test
	void failedReloadPreservesThePreviouslyPublishedCatalog() throws Exception {
		MemoryStore store = new MemoryStore();
		Entry original = new Entry("alpha");
		store.loaded = List.of(original);
		store.reload();
		store.loaded = List.of(new Entry("replacement"));
		store.failure = new IOException("second source failed");

		assertThrows(IOException.class, store::reload);

		assertSame(original, store.get("alpha"));
		assertEquals(1, store.getAll().size());
		assertFalse(store.isLoaded());
		assertEquals("second source failed", store.getLoadError());
	}

	@Test
	void messageLessLoadFailureRemainsAReadableFailureState() throws Exception {
		MemoryStore store = new MemoryStore();
		store.failure = new IOException();

		assertThrows(IOException.class, store::reload);

		assertFalse(store.isLoaded());
		assertFalse(store.getLoadError().isBlank());
		assertThrows(IllegalStateException.class, () -> store.save(new Entry("alpha")));
	}

	@Test
	void successfulSavePublishesTheReturnedSourceOnlyAfterPersistence() throws Exception {
		MemoryStore store = new MemoryStore();
		store.reload();
		Entry entry = new Entry("alpha");
		store.failSave = true;

		assertThrows(IOException.class, () -> store.save(entry));
		assertEquals(List.of(), store.getAll());
		assertEquals(BattleNext_LoadSource.CREATED, entry.getSourceType());

		store.failSave = false;
		store.save(entry);
		assertSame(entry, store.get("alpha"));
		assertEquals("saved/alpha.json", entry.getSourcePath());
		assertTrue(store.isLoaded());
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

	private static class MemoryStore extends BattleNext_BaseStore<Entry> {
		private List<Entry> loaded = List.of();
		private IOException failure;
		private boolean failSave;
		private final BattleNext_BaseLoader<Entry> loader = new BattleNext_BaseLoader<>() {
			@Override
			public List<Entry> load() throws IOException {
				if (failure != null) throw failure;
				return loaded;
			}

			@Override
			protected BattleNext_LoadSource source() {
				return BattleNext_LoadSource.STANDARD;
			}
		};
		private final BattleNext_BaseSaver<Entry> writer = new BattleNext_BaseSaver<>() {
			@Override
			public String save(Entry model) throws IOException {
				if (failSave) throw new IOException("save failed");
				return "saved/" + model.getId() + ".json";
			}

			@Override
			public void delete(Entry model) {
			}
		};

		private MemoryStore() {
			super("not loaded");
		}

		@Override
		protected BattleNext_BaseSaver<Entry> saver() {
			return writer;
		}

		@Override
		protected List<BattleNext_BaseLoader<Entry>> sources() {
			return List.of(loader);
		}
	}
}
