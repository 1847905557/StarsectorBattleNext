package data.scripts.tournament;

import data.model.report.BattleNext_BattleReportModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import data.test.fixture.BattleNext_TestGameFixture;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

class BattleNext_StartedTournamentStoreTest extends BattleNext_TestGameFixture {

	@Test
	void independentStoresMergeInstancesActivationAndRoundProgressAndLockConfiguration() throws Exception {
		BattleNext_TournamentStore configurations = new BattleNext_TournamentStore();
		configurations.reload();
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel();
		configuration.setId("cup");
		configuration.setName("Cup");
		configurations.save(configuration);
		BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
		BattleNext_StartedTournament first = engine.start(configuration, List.of("a", "b"), BattleNext_StartedTournament.ExecutionKind.MANUAL);
		BattleNext_StartedTournament second = engine.start(configuration, List.of("b", "a"), BattleNext_StartedTournament.ExecutionKind.SIMULATION);
		assertNotEquals(first.getId(), second.getId());
		BattleNext_StartedTournamentStore left = new BattleNext_StartedTournamentStore();
		BattleNext_StartedTournamentStore right = new BattleNext_StartedTournamentStore();
		left.reload();
		right.reload();
		left.save(first);
		right.save(second);
		left.activate(first.getId());
		BattleNext_Match match = second.getNextPendingMatch();
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId(UUID.randomUUID().toString());
		report.setName("Round 中文 50%");
		engine.recordRound(configuration, second, match.getId(), match.getPlayer(), report.getId());
		right.save(second);
		left.reload();
		assertEquals(first.getId(), left.getActiveId());
		assertEquals(2, left.getAll().size());
		assertTrue(left.get(second.getId()).isCompleted());
		assertTrue(left.get(first.getId()).getMatches().get(0).getRounds().isEmpty());
		assertEquals(report.getId(), left.get(second.getId()).getMatches().get(0).getRounds().get(0).getReportId());
		assertThrows(IllegalArgumentException.class, () -> left.activate(second.getId()));
		assertThrows(IllegalStateException.class, () -> configurations.save(configuration));
		assertThrows(IllegalStateException.class, () -> configurations.delete("cup"));
		assertFalse(configuration.toJson().has("matches"));
	}

	@Test
	void instanceParsingAndPendingQueriesPreserveIndependentOrderedReports() throws Exception {
		BattleNext_StartedTournament started = new BattleNext_StartedTournament(UUID.randomUUID().toString(), "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("a", "", "b", "c"));
		BattleNext_Match bye = new BattleNext_Match("bye", "a", "", -1, 1f);
		BattleNext_Match first = new BattleNext_Match("first", "a", "b", -1, 1f);
		BattleNext_Match selected = new BattleNext_Match("selected", "b", "c", -1, 1f);
		started.addGeneratedMatch(bye);
		started.addGeneratedMatch(first);
		started.addGeneratedMatch(selected);
		started.setPendingMatchId(selected.getId());
		String before = started.toJson().toString();
		assertSame(selected, started.getNextPendingMatch());
		assertEquals(before, started.toJson().toString());
		selected.setStatus(BattleNext_Match.MatchStatus.COMPLETE);
		BattleNext_Round completedRound = new BattleNext_Round("b", "c");
		completedRound.setReportId("report-50%");
		completedRound.setStatus(BattleNext_Round.RoundStatus.PLAYER_WIN);
		selected.addRound(completedRound);
		assertSame(first, started.getNextPendingMatch());
		BattleNext_StartedTournament restored = started.copy();
		assertEquals("report-50%", restored.findMatch("selected").getRounds().get(0).getReportId());
		assertEquals("b", restored.findMatch("selected").getRounds().get(0).getWinnerFleetId());
		assertEquals(List.of("a", "", "b", "c"), restored.getFleetIds());
		assertThrows(UnsupportedOperationException.class, () -> restored.getFleetIds().clear());
		first.addRound(new BattleNext_Round("a", "b"));
		assertTrue(restored.findMatch("first").getRounds().isEmpty());
		JSONObject broken = restored.toJson().put("executionKind", "UNKNOWN");
		String state = restored.toJson().toString();
		assertThrows(IllegalArgumentException.class, () -> restored.fromJson(broken));
		assertEquals(state, restored.toJson().toString());
	}

	@Test
	void failedAtomicReplacementPreservesPublishedSelectionAndReleasesStaging() throws Exception {
		BattleNext_StartedTournamentStore store = new BattleNext_StartedTournamentStore();
		BattleNext_StartedTournament started = new BattleNext_StartedTournament(UUID.randomUUID().toString(), "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("a", "b"));
		store.save(started);
		store.activate(started.getId());
		var path = directory.resolve(BattleNext_StartedTournamentStore.PATH);
		Files.delete(path);
		Files.createDirectory(path);
		Files.writeString(path.resolve("owned"), "Keep", StandardCharsets.UTF_8);
		assertThrows(IOException.class, () -> store.activate(""));
		assertThrows(IOException.class, () -> store.delete(started.getId()));
		assertEquals(started.getId(), store.getActiveId());
		assertEquals("Keep", Files.readString(path.resolve("owned"), StandardCharsets.UTF_8));
		assertFalse(Files.exists(directory.resolve(BattleNext_StartedTournamentStore.PATH + ".pending")));
	}

	@Test
	void malformedInstanceSourcePublishesEmptyContentsAndAllowsConfigurationSave() throws Exception {
		BattleNext_StartedTournamentStore store = new BattleNext_StartedTournamentStore();
		store.reload();
		BattleNext_StartedTournament started = new BattleNext_StartedTournament(UUID.randomUUID().toString(), "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("a", "b"));
		store.save(started);
		Files.writeString(directory.resolve(BattleNext_StartedTournamentStore.PATH), "{broken", StandardCharsets.UTF_8);
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
		assertTrue(store.getLoadErrors().get(0).contains(BattleNext_StartedTournamentStore.PATH));
		assertThrows(JSONException.class, () -> store.save(started));
		assertEquals("{broken", Files.readString(directory.resolve(BattleNext_StartedTournamentStore.PATH), StandardCharsets.UTF_8));
		BattleNext_TournamentStore configurations = new BattleNext_TournamentStore();
		configurations.reload();
		configurations.save(new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2));
		assertEquals("Cup", configurations.get("cup").getName());
	}

	@Test
	void deletingInstancesMergesLatestDataClearsActivityAndUnlocksTheirConfiguration() throws Exception {
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		BattleNext_TournamentStore configurations = new BattleNext_TournamentStore();
		configurations.reload();
		configurations.save(configuration);
		BattleNext_StartedTournamentStore firstStore = new BattleNext_StartedTournamentStore();
		BattleNext_StartedTournamentStore secondStore = new BattleNext_StartedTournamentStore();
		BattleNext_StartedTournament first = new BattleNext_TournamentEngine().start(configuration, List.of("a", "b"), BattleNext_StartedTournament.ExecutionKind.MANUAL);
		BattleNext_StartedTournament second = new BattleNext_TournamentEngine().start(configuration, List.of("b", "a"), BattleNext_StartedTournament.ExecutionKind.SIMULATION);
		firstStore.save(first);
		firstStore.activate(first.getId());
		secondStore.save(second);
		firstStore.delete(first.getId());
		assertEquals(List.of(second.getId()), firstStore.getAll().stream().map(BattleNext_StartedTournament::getId).toList());
		assertEquals("", firstStore.getActiveId());
		assertTrue(configurations.isLocked("cup"));
		secondStore.delete(second.getId());
		assertFalse(configurations.isLocked("cup"));
		configurations.save(configuration);
		assertThrows(IllegalArgumentException.class, () -> secondStore.delete(first.getId()));
		assertTrue(secondStore.getAll().isEmpty());
	}

	@Test
	void mixedInstancesKeepHealthyProgressAndDeleteTheSelectedMalformedPosition() throws Exception {
		var valid = new BattleNext_StartedTournament(UUID.randomUUID().toString(), "healthy", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("a", "b"));
		var broken = new BattleNext_StartedTournament(UUID.randomUUID().toString(), "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("a", "b")).toJson();
		broken.remove("fleetIds");
		JSONObject unowned = new JSONObject().put("unreadable", "保留 50%");
		JSONArray inputs = new JSONArray().put(valid.toJson()).put(broken).put(unowned);
		JSONObject envelope = new JSONObject().put("formatVersion", 1).put("activeTournamentId", broken.getString("id")).put("instances", inputs);
		var path = directory.resolve(BattleNext_StartedTournamentStore.PATH);
		Files.createDirectories(path.getParent());
		Files.writeString(path, envelope.toString(), StandardCharsets.UTF_8);
		var store = new BattleNext_StartedTournamentStore();
		store.reload();
		assertEquals(List.of(valid.getId()), store.getAll().stream().map(BattleNext_StartedTournament::getId).toList());
		assertTrue(store.hasInstances("cup"));
		assertFalse(store.hasInstances(""));
		assertEquals("", store.getActiveId());
		var selected = store.getCatalog().get(1);
		assertEquals("cup", selected.sourceTournamentId());
		assertTrue(selected.error().contains("fleetIds"));
		assertEquals(broken.toString(), selected.raw());
		store.delete(selected);
		JSONObject remaining = new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
		assertEquals("", remaining.getString("activeTournamentId"));
		assertEquals(new JSONArray().put(valid.toJson()).put(unowned).toString(), remaining.getJSONArray("instances").toString());
		assertFalse(store.hasInstances("cup"));
		assertEquals(1, store.getLoadErrors().size());
	}

	@Test
	void conflictingInstancesAreAllRejectedAndDeletionPreservesSharedReferencesUntilTheLastItem() throws Exception {
		var instance = new BattleNext_StartedTournament(UUID.randomUUID().toString(), "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("a", "b"));
		var path = directory.resolve(BattleNext_StartedTournamentStore.PATH);
		Files.createDirectories(path.getParent());
		var duplicate = instance.toJson().put("startedAt", 123);
		var envelope = new JSONObject().put("formatVersion", 1).put("activeTournamentId", instance.getId())
				.put("instances", new JSONArray().put(instance.toJson()).put(duplicate));
		Files.writeString(path, envelope.toString(), StandardCharsets.UTF_8);
		var pending = directory.resolve(BattleNext_StartedTournamentStore.pendingRoundPath(instance.getId()));
		Files.createDirectories(pending.getParent());
		Files.writeString(pending, "owned pending", StandardCharsets.UTF_8);
		var store = new BattleNext_StartedTournamentStore();
		store.reload();
		assertTrue(store.getAll().isEmpty());
		assertEquals(2, store.getLoadErrors().size());
		assertThrows(IllegalArgumentException.class, () -> store.delete(instance.getId()));
		store.delete(store.getCatalog().get(0));
		assertEquals(duplicate.toString(), new JSONObject(Files.readString(path, StandardCharsets.UTF_8)).getJSONArray("instances").getJSONObject(0).toString());
		assertEquals(instance.getId(), store.getActiveId());
		assertTrue(Files.exists(pending));
		store.delete(store.getCatalog().get(0));
		assertTrue(store.getAll().isEmpty());
		assertFalse(Files.exists(pending));
		assertEquals("", store.getActiveId());
	}

	@Test
	void deletionRereadsTheSelectedRawEntryAndRejectsAChangedPosition() throws Exception {
		var store = new BattleNext_StartedTournamentStore();
		var instance = new BattleNext_StartedTournament(UUID.randomUUID().toString(), "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("a", "b"));
		store.save(instance);
		var selected = store.getCatalog().get(0);
		var changed = instance.toJson().put("version", 9);
		var path = directory.resolve(BattleNext_StartedTournamentStore.PATH);
		var envelope = new JSONObject(Files.readString(path, StandardCharsets.UTF_8)).put("instances", new JSONArray().put(changed));
		Files.writeString(path, envelope.toString(), StandardCharsets.UTF_8);
		assertThrows(IllegalStateException.class, () -> store.delete(selected));
		assertEquals(envelope.toString(), Files.readString(path, StandardCharsets.UTF_8));
	}
}
