package data.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

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
		private boolean failDelete;
		private boolean failState;
		private String removedId = "";

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
			public void delete(Entry model) throws IOException {
				if (failDelete) throw new IOException("delete failed");
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

		@Override
		protected void loadState() throws IOException {
			if (failState) throw new IOException("state failed");
		}

		@Override
		protected void onEntryRemoved(String id) {
			removedId = id;
		}
	}

	@Test
	void stateFailureKeepsOldEntriesAndSuccessfulReloadClearsTheError() throws Exception {
		MemoryStore store = new MemoryStore();
		Entry original = new Entry("original");
		store.loaded = List.of(original);
		store.reload();
		store.loaded = List.of(new Entry("replacement"));
		store.failState = true;
		assertThrows(IOException.class, store::reload);
		assertSame(original, store.get("original"));
		assertNull(store.get("replacement"));
		store.failState = false;
		store.reload();
		assertNull(store.get("original"));
		assertEquals("replacement", store.getAll().get(0).getId());
		assertEquals("", store.getLoadError());
	}

	@Test
	void saveFailurePreservesExistingEntryAndInputSourceMetadata() throws Exception {
		MemoryStore store = new MemoryStore();
		Entry original = new Entry("entry");
		store.loaded = List.of(original);
		store.reload();
		Entry replacement = new Entry("entry");
		replacement.setSourceType(BattleNext_LoadSource.STANDARD);
		replacement.setSourcePath("original.json");
		store.failSave = true;
		assertThrows(IOException.class, () -> store.save(replacement));
		assertSame(original, store.get("entry"));
		assertEquals(BattleNext_LoadSource.STANDARD, replacement.getSourceType());
		assertEquals("original.json", replacement.getSourcePath());
		store.failSave = false;
		store.save(replacement);
		assertSame(replacement, store.get("entry"));
		assertEquals(BattleNext_LoadSource.CREATED, replacement.getSourceType());
		assertEquals("saved/entry.json", replacement.getSourcePath());
		assertTrue(store.isLoaded());
	}

	@Test
	void deletionPublishesAfterPersistenceAndInvokesTheRemovalHook() throws Exception {
		MemoryStore store = new MemoryStore();
		assertThrows(IllegalStateException.class, () -> store.delete("entry"));
		Entry entry = new Entry("entry");
		store.loaded = List.of(entry);
		store.reload();
		List<Entry> snapshot = store.getAll();
		assertThrows(UnsupportedOperationException.class, snapshot::clear);
		store.failDelete = true;
		assertThrows(IOException.class, () -> store.delete("entry"));
		assertSame(entry, store.get("entry"));
		assertEquals("", store.removedId);
		store.failDelete = false;
		store.delete("entry");
		assertEquals("entry", store.removedId);
		assertEquals(List.of(), store.getAll());
		assertEquals(List.of(entry), snapshot);
		assertThrows(IllegalStateException.class, () -> store.delete("unknown"));
	}

	@Test
	void sourceManagementAndInvalidSaveIdentityAreCheckedBeforePersistence() throws Exception {
		MemoryStore store = new MemoryStore();
		Entry standard = new Entry("standard");
		standard.setSourceType(BattleNext_LoadSource.STANDARD);
		store.loaded = List.of(standard);
		store.reload();
		assertThrows(IllegalStateException.class, () -> store.delete("standard"));
		assertThrows(IllegalArgumentException.class, () -> store.save(new Entry("../invalid")));
		assertSame(standard, store.get("standard"));
	}
}
