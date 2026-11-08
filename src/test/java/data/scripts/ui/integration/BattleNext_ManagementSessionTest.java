package data.scripts.ui.integration;

import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_ManagementSession;
import data.scripts.ui.BattleNext_UiManager;
import data.scripts.ui.page.BattleNext_PageView;
import data.scripts.ui.page.BattleNext_BattleSelectorPage.BattleSourceSelectionRequest;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.model.report.BattleNext_BattleReportModel;
import data.scripts.tools.BattleNext_ModFileManager;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_ManagementSessionTest extends BattleNext_TestPageFixture {

	@Test
	void switchingFromAnActiveTournamentToQuickBattlePublishesTheSharedSelectionWithoutAnEditor() throws Exception {

		BattleNext_TournamentStore configurations = new BattleNext_TournamentStore();
		configurations.reload();
		configurations.save(new BattleNext_TournamentModel("cup", "Cup", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2));
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		String instanceId = UUID.randomUUID().toString();
		instances.save(new BattleNext_StartedTournament(instanceId, "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("alpha", "beta")));
		instances.activate(instanceId);

		BattleNext_UiManager manager = new BattleNext_UiManager(() -> { });
		manager.attach(panel(1400f, 800f, manager));
		manager.open();
		settleUi(manager);
		BattleNext_ManagementSession session = (BattleNext_ManagementSession) getField(manager, "catalogs");
		assertEquals(instanceId, manager.getActiveTournamentId());
		assertNull(getField(getField(manager, "battleSelectorPage"), "quickBattleEditor"));

		manager.buttonPressed(new BattleSourceSelectionRequest(""));
		assertTrue(manager.isSaving());
		settleSave(manager);
		assertEquals("", session.data().instances().getActiveId());
		assertEquals("", manager.getActiveTournamentId());
		assertEquals("", getField(getField(manager, "battleSelectorPage"), "selectedId"));

		manager.buttonPressed("close");
	}

	@Test
	void reportRefreshRetainsAChangeArrivingWhileThePreviousRefreshIsWaitingForPublication() throws Exception {

		BattleNext_UiManager manager = new BattleNext_UiManager(() -> { });
		manager.attach(panel(1400f, 800f, manager));
		manager.open();
		settleUi(manager);
		BattleNext_ManagementSession session = (BattleNext_ManagementSession) getField(manager, "catalogs");
		long revision = session.data().reports().getRevision();

		session.refreshReports();
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("late");
		report.setName("Late report");
		session.persist(() -> {
			BattleNext_ModFileManager.writeText("tournament/_reports/late.json", report.toJson().toString());
			return true;
		}).get();
		session.refreshReports();
		long deadline = System.nanoTime() + 10_000_000_000L;
		while (session.data().reports().get("late") == null) {
			if (System.nanoTime() > deadline) throw new AssertionError("Coalesced report refresh timed out");
			session.advance();
			Thread.sleep(1L);
		}

		assertEquals(revision + 2, session.data().reports().getRevision());
		manager.buttonPressed("close");
	}

	@Test
	void reportRefreshReadsAnUpdatedReportWrittenByAnotherSession() throws Exception {

		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("report");
		report.setName("Initial");
		Files.createDirectories(directory.resolve("tournament/_reports"));
		Files.writeString(directory.resolve("tournament/_reports/report.json"), report.toJson().toString(), StandardCharsets.UTF_8);

		BattleNext_UiManager manager = new BattleNext_UiManager(() -> { });
		manager.attach(panel(1400f, 800f, manager));
		manager.open();
		settleUi(manager);
		BattleNext_ManagementSession session = (BattleNext_ManagementSession) getField(manager, "catalogs");
		BattleNext_BattleReportModel initial = session.data().reports().get("report");

		report.setName("Updated by simulator");
		BattleNext_ModFileManager.ReadSession other = new BattleNext_ModFileManager.ReadSession(directory.toString());
		other.submit(() -> {
			BattleNext_ModFileManager.writeText("tournament/_reports/report.json", report.toJson().toString());
			return null;
		}).get();
		session.refreshReports();
		long deadline = System.nanoTime() + 10_000_000_000L;
		while (session.data().reports().get("report") == initial) {
			if (System.nanoTime() > deadline) throw new AssertionError("Report update timed out");
			session.advance();
			Thread.sleep(1L);
		}
		assertEquals("Updated by simulator", session.data().reports().get("report").getName());
		initial.prepareDetails();
		assertEquals("Initial", initial.getName());

		manager.buttonPressed("close");
	}

	@Test
	void pageOperationsPublishWithoutAnEditorKeepNestedWorkAndDropClosedViewCallbacks() throws Exception {
		BattleNext_PageView page = new BattleNext_PageView() {
			@Override
			protected void loadPageState() {

			}

			@Override
			protected void buildPage() {

			}

			@Override
			protected void refreshPage() {

			}
		};
		CompletableFuture<Boolean> first = new CompletableFuture<>();
		CompletableFuture<Boolean> second = new CompletableFuture<>();
		AtomicInteger submitted = new AtomicInteger();
		AtomicInteger published = new AtomicInteger();

		page.setPersistence(write -> submitted.getAndIncrement() == 0 ? first : second);
		page.persistMutation(() -> true, () -> {
			published.incrementAndGet();
			page.persistMutation(() -> true, published::incrementAndGet, "Next", "Failure");
		}, "Loading", "Failure");
		first.complete(true);
		page.completeSave();
		assertTrue(page.isSaving());
		assertEquals("Next", page.getStatusText());

		second.completeExceptionally(new IllegalStateException("Injected operation failure"));
		page.completeSave();
		assertFalse(page.isSaving());
		assertTrue(page.getStatusText().contains("Injected operation failure"));

		CompletableFuture<Boolean> closing = new CompletableFuture<>();
		page.setPersistence(write -> closing);
		page.persistMutation(() -> true, published::incrementAndGet, "Loading", "Failure");
		page.close();
		closing.complete(true);
		page.completeSave();
		assertEquals(1, published.get());
	}

	@Test
	void sharedCatalogPublishesAsyncSavesAndRefreshesExternalEditsWhileRetainingSelection() throws Exception {

		BattleNext_FleetStore source = new BattleNext_FleetStore();
		source.reload();
		BattleNext_FleetModel fleet = new BattleNext_FleetModel("fleet", "Original", List.of(), List.of(), List.of());
		source.save(fleet);

		BattleNext_UiManager manager = new BattleNext_UiManager(() -> { });
		manager.attach(panel(1400f, 800f, manager));
		manager.open();
		assertTrue(manager.isLoading());
		settleUi(manager);
		manager.buttonPressed(tab("FLEETS"));
		manager.buttonPressed("fleet:fleet");
		Object page = getField(manager, "fleetPage");
		Object editor = getField(page, "editor");
		((TextFieldAPI) getField(editor, "nameField")).setText("Saved");
		manager.buttonPressed("save");
		assertTrue(manager.isSaving());
		settleSave(manager);
		BattleNext_ManagementSession session = (BattleNext_ManagementSession) getField(manager, "catalogs");
		assertEquals("Saved", session.data().fleets().get("fleet").getName());
		assertSame(session.data().fleets(), getField(page, "store"));

		long tournamentRevision = session.data().tournaments().getRevision();
		long instancesRevision = session.data().instances().getRevision();
		fleet.setName("External");
		Files.writeString(directory.resolve(fleet.getSourcePath()), fleet.toJson().toString(), StandardCharsets.UTF_8);
		manager.buttonPressed(tab("RULES"));
		manager.buttonPressed(tab("FLEETS"));
		assertEquals("Saved", session.data().fleets().get("fleet").getName());
		assertEquals(tournamentRevision, session.data().tournaments().getRevision());
		assertEquals(instancesRevision, session.data().instances().getRevision());

		manager.buttonPressed("fleet:fleet");
		manager.buttonPressed("refresh");
		settleUi(manager);
		Object refreshed = getField(getField(manager, "fleetPage"), "editor");
		assertEquals("External", ((TextFieldAPI) getField(refreshed, "nameField")).getText());

		manager.buttonPressed("close");
	}

	@Test
	void openingStartsPreparationImmediatelyAndEntersTheSelectedPageOnlyAfterCompletion() throws Exception {

		CompletableFuture<Void> release = new CompletableFuture<>();
		BattleNext_ModFileManager.ReadSession files = new BattleNext_ModFileManager.ReadSession(directory.toString());
		CompletableFuture<Void> blocked = files.submit(() -> {
			release.get();
			return null;
		});

		BattleNext_UiManager manager = new BattleNext_UiManager(() -> { });
		manager.attach(panel(1400f, 800f, manager));
		manager.open();
		BattleNext_ManagementSession session = (BattleNext_ManagementSession) getField(manager, "catalogs");
		CompletableFuture<?> started = (CompletableFuture<?>) getField(session, "preparation");
		try {
			assertFalse(started.isDone());
			assertNull(session.data());
			assertNotNull(getField(manager, "loadingLabel"));
			manager.buttonPressed(tab("RULES"));
			assertNull(getField(manager, "attachedView"));
			manager.advance(1f / 60f);
			assertNull(getField(manager, "rulesPage"));
		} finally {
			release.complete(null);
		}
		blocked.get();
		started.get();

		CompletableFuture<Void> finishing = new CompletableFuture<>();
		Field preparation = BattleNext_ManagementSession.class.getDeclaredField("preparation");
		preparation.setAccessible(true);
		preparation.set(session, finishing);
		manager.buttonPressed(tab("RULES"));
		manager.advance(1f / 60f);
		assertTrue(manager.isLoading());
		assertNull(getField(manager, "attachedView"));
		finishing.complete(null);
		settleUi(manager);
		assertNotNull(getField(manager, "attachedView"));

		manager.buttonPressed("add");
		Object page = getField(manager, "rulesPage");
		Object editor = getField(page, "editor");
		TextFieldAPI name = (TextFieldAPI) getField(editor, "nameField");
		name.setText("Loaded page draft");
		manager.buttonPressed(tab("FLEETS"));
		manager.buttonPressed("confirm_cancel");
		assertSame(page, getField(manager, "attachedView"));
		manager.advance(1f / 60f);
		assertSame(name, getField(editor, "nameField"));
		assertEquals("Loaded page draft", name.getText());

		manager.buttonPressed("close");
		manager.buttonPressed("confirm_discard");
	}

	@Test
	void refreshCancellationKeepsTheDraftAndConfirmedSaveCompletesBeforeReload() throws Exception {

		BattleNext_FleetStore source = new BattleNext_FleetStore();
		source.reload();
		source.save(new BattleNext_FleetModel("fleet", "Original", List.of(), List.of(), List.of()));

		BattleNext_UiManager manager = new BattleNext_UiManager(() -> { });
		manager.attach(panel(1400f, 800f, manager));
		manager.open();
		settleUi(manager);
		manager.buttonPressed(tab("FLEETS"));
		manager.buttonPressed("fleet:fleet");
		Object editor = getField(getField(manager, "fleetPage"), "editor");
		TextFieldAPI name = (TextFieldAPI) getField(editor, "nameField");
		name.setText("Draft");
		manager.buttonPressed("refresh");
		manager.buttonPressed("confirm_cancel");
		assertEquals("Draft", name.getText());
		assertFalse(manager.isLoading());

		manager.buttonPressed("refresh");
		Object previousSession = getField(manager, "catalogs");
		manager.buttonPressed("confirm_save");
		assertTrue(manager.isSaving());
		settleSave(manager);
		settleUi(manager);
		assertNotSame(previousSession, getField(manager, "catalogs"));
		Object refreshed = getField(getField(manager, "fleetPage"), "editor");
		assertEquals("Draft", ((TextFieldAPI) getField(refreshed, "nameField")).getText());

		manager.buttonPressed("close");
	}

	private void settleSave(BattleNext_UiManager manager) throws InterruptedException {
		long deadline = System.nanoTime() + 10_000_000_000L;
		while (manager.isSaving()) {
			if (System.nanoTime() > deadline) throw new AssertionError("Save timed out");
			manager.advance(1f / 60f);
			Thread.sleep(1L);
		}
	}

	private Object tab(String name) throws ClassNotFoundException {
		return Arrays.stream(Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants())
				.filter(value -> value.toString().equals(name)).findFirst().orElseThrow();
	}
}
