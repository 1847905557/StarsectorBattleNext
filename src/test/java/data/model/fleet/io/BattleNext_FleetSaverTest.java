package data.model.fleet.io;

import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BattleNext_FleetSaverTest {

	@Test
	void saveCreatesCompleteBackupAndPreservesOtherFiles() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/alpha/fleet_data.json", "{\"id\":\"persisted\"}");
		files.put("tournament/alpha/fleet_data.json.bak", "unrelated backup");
		files.put("tournament/alpha/stale.variant", "stale");
		files.put("tournament/alpha/keep.txt", "keep");
		BattleNext_FleetModel fleet = fleet("alpha", "alpha", "current");

		new BattleNext_FleetSaver(files, () -> LocalDateTime.of(2026, 9, 30, 1, 2, 3, 4_000_000)).save(fleet);

		assertFalse(files.contains("tournament/alpha/stale.variant"));
		assertTrue(files.contains("tournament/alpha/keep.txt"));
		assertEquals("{\"id\":\"persisted\"}", files.text("tournament/alpha/backup/20260930-010203-004/fleet_data.json"));
		assertEquals("stale", files.text("tournament/alpha/backup/20260930-010203-004/stale.variant"));
		assertEquals("unrelated backup", files.text("tournament/alpha/fleet_data.json.bak"));
		assertTrue(files.contains("tournament/alpha/current.variant"));
	}

	@Test
	void repeatedSavesKeepHistoryAndDisambiguateSameTimestamp() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/alpha/fleet_data.json", "persisted fleet");
		files.put("tournament/alpha/persisted.variant", "persisted variant");
		BattleNext_FleetSaver saver = new BattleNext_FleetSaver(files,
				() -> LocalDateTime.of(2026, 9, 30, 1, 2, 3, 4_000_000));
		BattleNext_FleetModel fleet = fleet("alpha", "alpha", "current");

		saver.save(fleet);
		String firstVersion = files.text("tournament/alpha/fleet_data.json");
		fleet.setSourcePath("tournament/alpha/fleet_data.json");
		saver.save(fleet);

		assertEquals("persisted fleet", files.text("tournament/alpha/backup/20260930-010203-004/fleet_data.json"));
		assertEquals("persisted variant", files.text("tournament/alpha/backup/20260930-010203-004/persisted.variant"));
		assertEquals(firstVersion,
				files.text("tournament/alpha/backup/20260930-010203-004-1/fleet_data.json"));
	}

	@Test
	void saveMovesRenamedFleetAndRemovesSourceDirectory() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/source/fleet_data.json", "persisted fleet");
		files.put("tournament/source/persisted.variant", "persisted variant");
		files.put("tournament/source/backup/20260929-010203-004/fleet_data.json", "previous backup");
		BattleNext_FleetModel fleet = fleet("renamed", "new", "current");
		fleet.setSourcePath("tournament/source/fleet_data.json");

		new BattleNext_FleetSaver(files, () -> LocalDateTime.of(2026, 9, 30, 1, 2, 3, 4_000_000)).save(fleet);

		assertFalse(files.contains("tournament/source/fleet_data.json"));
		assertFalse(files.contains("tournament/source/persisted.variant"));
		assertTrue(files.contains("tournament/new/fleet_data.json"));
		assertEquals("previous backup", files.text("tournament/new/backup/20260929-010203-004/fleet_data.json"));
		assertEquals("persisted fleet", files.text("tournament/new/backup/20260930-010203-004/fleet_data.json"));
		assertEquals("persisted variant", files.text("tournament/new/backup/20260930-010203-004/persisted.variant"));
	}

	@Test
	void renameRejectsOccupiedTargetFleet() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/source/fleet_data.json", "persisted fleet");
		files.put("tournament/new/fleet_data.json", "new");
		BattleNext_FleetModel fleet = fleet("renamed", "new", "current");
		fleet.setSourcePath("tournament/source/fleet_data.json");

		IOException failure = assertThrows(IOException.class, () -> new BattleNext_FleetSaver(files).save(fleet));
		assertEquals("Fleet target already exists: tournament/new/fleet_data.json", failure.getMessage());
		assertEquals("persisted fleet", files.text("tournament/source/fleet_data.json"));
		assertEquals("new", files.text("tournament/new/fleet_data.json"));
	}

	@Test
	void deleteRemovesBackupHistory() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/alpha/fleet_data.json", "current");
		files.put("tournament/alpha/backup/20260930-010203-004/fleet_data.json", "previous backup");

		new BattleNext_FleetSaver(files).delete(fleet("alpha", "alpha", "current"));

		assertFalse(files.contains("tournament/alpha/fleet_data.json"));
		assertFalse(files.contains("tournament/alpha/backup/20260930-010203-004/fleet_data.json"));
	}

	@Test
	void writeFailureRestoresTargetAndSourceDirectory() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/source/fleet_data.json", "{\"id\":\"persisted\"}");
		files.put("tournament/source/persisted.variant", "persisted variant");
		files.put("tournament/new/stale.variant", "stale");
		BattleNext_FleetModel fleet = fleet("renamed", "new", "current");
		fleet.setSourcePath("tournament/source/fleet_data.json");
		files.failWritePath = "tournament/new/current.variant";

		IOException failure = assertThrows(IOException.class, () -> new BattleNext_FleetSaver(files).save(fleet));
		assertEquals("Injected write failure: tournament/new/current.variant", failure.getMessage());
		assertEquals(0, failure.getSuppressed().length);

		assertEquals(List.of("tournament/new/stale.variant"), files.list("tournament/new", true));
		assertEquals(List.of("tournament/source/fleet_data.json", "tournament/source/persisted.variant"), files.list("tournament/source", true));
		assertFalse(files.contains("tournament/new/fleet_data.json"));
		assertEquals("stale", files.text("tournament/new/stale.variant"));
		assertEquals("{\"id\":\"persisted\"}", files.text("tournament/source/fleet_data.json"));
		assertEquals("persisted variant", files.text("tournament/source/persisted.variant"));
	}

	@Test
	void staleVariantDeleteFailureRestoresTarget() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/alpha/fleet_data.json", "{\"id\":\"persisted\"}");
		files.put("tournament/alpha/stale.variant", "stale");
		BattleNext_FleetModel fleet = fleet("alpha", "alpha", "current");
		files.failDeletePath = "tournament/alpha/stale.variant";

		IOException failure = assertThrows(IOException.class, () -> new BattleNext_FleetSaver(files).save(fleet));
		assertEquals("Injected delete failure: tournament/alpha/stale.variant", failure.getMessage());

		assertEquals("{\"id\":\"persisted\"}", files.text("tournament/alpha/fleet_data.json"));
		assertEquals("stale", files.text("tournament/alpha/stale.variant"));
		assertFalse(files.contains("tournament/alpha/current.variant"));
	}

	@Test
	void invalidVariantIsRejectedBeforeAnyMutation() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/alpha/fleet_data.json", "{\"id\":\"persisted\"}");
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("id", "alpha", "name", List.of(), List.of(), List.of());
		fleet.putVariant(BattleNext_VariantBuilder.fromJson(new JSONObject("{\"variantId\":\"bad/id\"}"), "bad/id"));

		assertThrows(IOException.class, () -> new BattleNext_FleetSaver(files).save(fleet));

		assertEquals(1, files.size());
		assertEquals("{\"id\":\"persisted\"}", files.text("tournament/alpha/fleet_data.json"));
	}

	@Test
	void rollbackFailureIsSuppressedOnOriginalFailure() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/alpha/fleet_data.json", "{\"id\":\"persisted\"}");
		BattleNext_FleetModel fleet = fleet("alpha", "alpha", "current");
		files.failWritePath = "tournament/alpha/current.variant";
		files.failDeleteDirectory = "tournament/alpha";

		IOException failure = assertThrows(IOException.class, () -> new BattleNext_FleetSaver(files).save(fleet));

		assertEquals("Injected write failure: tournament/alpha/current.variant", failure.getMessage());
		assertEquals(1, failure.getSuppressed().length);
		assertInstanceOf(IOException.class, failure.getSuppressed()[0]);
		assertEquals("Injected delete failure: tournament/alpha", failure.getSuppressed()[0].getMessage());
	}

	private BattleNext_FleetModel fleet(String id, String folder, String variantId) throws Exception {
		BattleNext_FleetModel fleet = new BattleNext_FleetModel(id, folder, "name", List.of(), List.of(), List.of());
		fleet.putVariant(BattleNext_VariantBuilder.fromJson(
				new JSONObject("{\"variantId\":\"" + variantId + "\",\"hullId\":\"hull\"}"), variantId));
		return fleet;
	}

	@Test
	void directNumberedCsvExportRequiresNewIdentityAndProtectsTheSourceDirectory() throws Exception {

		MemoryFiles files = new MemoryFiles();
		files.put("tournament/package/player01_data.csv", "metadata");
		files.put("tournament/package/player01_fleet.csv", "members");
		files.put("tournament/package/source.variant", "source variant");
		BattleNext_FleetModel fleet = fleet("ab_player01", "exported", "current");
		fleet.setSourceType(BattleNext_LoadSource.LEGACY);
		fleet.setSourcePath("tournament/package/player01_data.csv");
		BattleNext_FleetSaver saver = new BattleNext_FleetSaver(files);
		assertThrows(IOException.class, () -> saver.save(fleet));
		fleet.setId("exported");
		fleet.setFolderName("PACKAGE");
		assertThrows(IOException.class, () -> saver.save(fleet));
		assertEquals(3, files.size());
		fleet.setFolderName("exported");
		saver.save(fleet);
		assertEquals("source variant", files.text("tournament/package/source.variant"));
		assertEquals("metadata", files.text("tournament/package/player01_data.csv"));
		assertTrue(files.contains("tournament/exported/current.variant"));
	}

	@Test
	void createdFleetCannotClaimANumberedCsvPackage() throws Exception {
		MemoryFiles files = new MemoryFiles();
		files.put("tournament/package/player1_data.csv", "metadata");
		files.put("tournament/package/player1_fleet.csv", "members");
		BattleNext_FleetModel fleet = fleet("created", "package", "current");
		assertThrows(IOException.class, () -> new BattleNext_FleetSaver(files).save(fleet));
		assertEquals(2, files.size());
	}

	private static class MemoryFiles implements BattleNext_FleetSaver.FileOperations {

		private final Map<String, byte[]> files = new LinkedHashMap<>();
		private String failWritePath;
		private String failDeletePath;
		private String failDeleteDirectory;

		void put(String path, String text) {
			files.put(path, text.getBytes(StandardCharsets.UTF_8));
		}

		String text(String path) {
			return new String(files.get(path), StandardCharsets.UTF_8);
		}

		boolean contains(String path) {
			return files.containsKey(path);
		}

		int size() {
			return files.size();
		}

		@Override
		public boolean exists(String path) {
			if (files.containsKey(path)) return true;
			String prefix = path.endsWith("/") ? path : path + "/";
			return files.keySet().stream().anyMatch(item -> item.startsWith(prefix));
		}

		@Override
		public List<String> list(String directory, boolean recursive) {
			String prefix = directory.endsWith("/") ? directory : directory + "/";
			List<String> result = new ArrayList<>();
			for (String path : files.keySet()) {
				if (!path.startsWith(prefix)) continue;
				String relative = path.substring(prefix.length());
				if (recursive || !relative.contains("/")) result.add(path);
			}
			return result;
		}

		@Override
		public byte[] readBytes(String path) throws IOException {
			if (!files.containsKey(path)) throw new IOException("Missing: " + path);
			return files.get(path).clone();
		}

		@Override
		public void writeBytes(String path, byte[] data) throws IOException {
			if (path.equals(failWritePath)) throw new IOException("Injected write failure: " + path);
			files.put(path, data.clone());
		}

		@Override
		public void delete(String path) throws IOException {
			if (path.equals(failDeletePath)) throw new IOException("Injected delete failure: " + path);
			files.remove(path);
		}

		@Override
		public void deleteRecursively(String path) throws IOException {
			if (path.equals(failDeleteDirectory)) throw new IOException("Injected delete failure: " + path);
			String prefix = path.endsWith("/") ? path : path + "/";
			files.keySet().removeIf(item -> item.equals(path) || item.startsWith(prefix));
		}
	}
}
