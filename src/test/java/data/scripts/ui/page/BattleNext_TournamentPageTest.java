package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournamentStore;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_TournamentPageTest extends BattleNext_TestPageFixture {

	@Test
	void existingManualInstancesKeepTheConfigurationEditorReadOnly() throws Exception {
		for (BattleNext_StartedTournament.ExecutionKind kind : List.of(BattleNext_StartedTournament.ExecutionKind.MANUAL)) {
			String id = kind.name().toLowerCase();
			BattleNext_TournamentModel configuration = new BattleNext_TournamentModel(id, "Cup", TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
			BattleNext_TournamentStore configurations = new BattleNext_TournamentStore();
			configurations.reload();
			configurations.save(configuration);
			BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
			BattleNext_StartedTournament started = new BattleNext_TournamentEngine().start(configuration, List.of("first", "second"), kind);
			instances.save(started);
			String stored = Files.readString(directory.resolve(configuration.getSourcePath()), StandardCharsets.UTF_8);
			BattleNext_TournamentPage page = page();
			page.attach(panel(1200f, 900f, new BaseCustomUIPanelPlugin()));
			page.buttonPressed("tournament:select:" + id);
			BattleNext_TournamentEditor editor = (BattleNext_TournamentEditor) getField(page, "editor");
			assertTrue(editor.isReadOnly());
			assertFalse(buttons.get("save").get());
			assertFalse(buttons.get("delete").get());
			assertFalse(page.hasUnsavedChanges());
			page.close();
			assertEquals(stored, Files.readString(directory.resolve(configuration.getSourcePath()), StandardCharsets.UTF_8));
		}
	}

	@Test
	void newAndUnstartedEditorsKeepSaveDeleteAndNavigationConfirmationFlows() throws Exception {

		saveFleets(List.of("first", "second", "third"));
		List<Runnable> navigation = new ArrayList<>();
		List<Runnable> deletion = new ArrayList<>();
		AtomicBoolean back = new AtomicBoolean();
		BattleNext_TournamentPage page = new BattleNext_TournamentPage(() -> back.set(true), navigation::add,
				(description, action) -> deletion.add(action));
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.attach(root);
		page.buttonPressed("add");
		navigation.remove(0).run();
		assertFalse(page.hasUnsavedChanges());
		BattleNext_TournamentEditor editor = (BattleNext_TournamentEditor) getField(page, "editor");
		((TextFieldAPI) getField(editor, "idField")).setText("created");
		((TextFieldAPI) getField(editor, "nameField")).setText("Created cup");
		((TextFieldAPI) getField(editor, "maxFleetsField")).setText("3");
		assertTrue(page.hasUnsavedChanges());
		page.buttonPressed("save");
		assertFalse(page.hasUnsavedChanges());
		assertEquals("Created cup", ((BattleNext_TournamentStore) getField(page, "tournamentStore")).get("created").getName());
		((TextFieldAPI) getField(editor, "nameField")).setText("Edited cup");
		page.buttonPressed("back");
		Runnable leave = navigation.remove(0);
		BattleNext_ConfirmationDialog dialog = confirmation(page, (BattleNext_UiInputRouter) getField(page, "inputRouter"));
		dialog.attach(root);
		dialog.request(leave);
		dialog.handle("confirm_cancel");
		assertTrue(page.hasUnsavedChanges());
		assertEquals("Edited cup", ((TextFieldAPI) getField(editor, "nameField")).getText());
		dialog.request(leave);
		dialog.handle("confirm_save");
		assertFalse(page.hasUnsavedChanges());
		page.buttonPressed("tournament:select:created");
		navigation.remove(0).run();
		assertFalse(page.hasUnsavedChanges());
		assertEquals("Edited cup", ((TextFieldAPI) getField(editor, "nameField")).getText());
		page.buttonPressed("delete");
		assertEquals(1, deletion.size());
		deletion.remove(0).run();
		assertNull(((BattleNext_TournamentStore) getField(page, "tournamentStore")).get("created"));
		page.buttonPressed("back");
		assertTrue(back.get());
		dialog.close();
		page.close();
	}

	private BattleNext_TournamentPage page() {
		return new BattleNext_TournamentPage(() -> {}, Runnable::run, (description, action) -> action.run());
	}

	@Test
	void expandedInstanceDeletionRequiresConfirmationAndReleasesConfigurationLock() throws Exception {
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		BattleNext_TournamentStore configurations = new BattleNext_TournamentStore();
		configurations.reload();
		configurations.save(configuration);
		BattleNext_StartedTournamentStore instances = new BattleNext_StartedTournamentStore();
		BattleNext_StartedTournament started = new BattleNext_TournamentEngine().start(configuration, List.of("first", "second"), BattleNext_StartedTournament.ExecutionKind.MANUAL);
		instances.save(started);
		instances.activate(started.getId());
		List<Runnable> confirmed = new ArrayList<>();
		BattleNext_TournamentPage page = new BattleNext_TournamentPage(() -> {}, Runnable::run, (name, action) -> confirmed.add(action));
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("tournament:select:cup");
		BattleNext_TournamentEditor editor = (BattleNext_TournamentEditor) getField(page, "editor");
		editor.buttonPressed("instance:toggle:" + started.getId());
		assertEquals(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_delete_instance"), buttonLabels.get("instance:delete:" + started.getId()));
		editor.buttonPressed("instance:delete:" + started.getId());
		instances.reload();
		assertNotNull(instances.get(started.getId()));
		assertTrue(editor.isReadOnly());
		confirmed.remove(0).run();
		instances.reload();
		assertNull(instances.get(started.getId()));
		assertEquals("", instances.getActiveId());
		assertFalse(editor.isReadOnly());
		assertFalse(page.hasUnsavedChanges());
		((TextFieldAPI) getField(editor, "nameField")).setText("Unlocked cup");
		assertTrue(page.save(), page.getStatusText());
		configurations.reload();
		assertEquals("Unlocked cup", configurations.get("cup").getName());
		page.close();
	}

	private void saveFleets(List<String> ids) throws Exception {
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		store.reload();
		for (String id : ids) {
			store.save(new BattleNext_FleetModel(id, "Fleet " + id + " with a full descriptive name", List.of(), List.of(), List.of()));
		}
	}

	@Test
	void malformedOwnedInstanceDisplaysDiagnosticAndDeletesAfterConfirmation() throws Exception {
		var configurations = new BattleNext_TournamentStore();
		configurations.reload();
		configurations.save(new BattleNext_TournamentModel("cup", "Cup", TournamentBracketType.SINGLE_ELIMINATION, 1, 2));
		String id = UUID.randomUUID().toString();
		var broken = new BattleNext_StartedTournament(id, "cup", BattleNext_StartedTournament.ExecutionKind.MANUAL, List.of("a", "b")).toJson();
		broken.remove("fleetIds");
		var other = new JSONObject().put("sourceTournamentId", "other").put("id", "other");
		var path = directory.resolve(BattleNext_StartedTournamentStore.PATH);
		Files.writeString(path, new JSONObject().put("formatVersion", 1).put("activeTournamentId", id)
				.put("instances", new JSONArray().put(broken).put(other)).toString(), StandardCharsets.UTF_8);
		List<Runnable> confirmation = new ArrayList<>();
		var page = new BattleNext_TournamentPage(() -> {}, Runnable::run, (label, action) -> confirmation.add(action));
		page.attach(panel(1400f, 900f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("tournament:select:cup");
		var editor = (BattleNext_TournamentEditor) getField(page, "editor");
		assertTrue(editor.isReadOnly());
		editor.buttonPressed("instance:toggle:error:0");
		assertTrue(labels.stream().anyMatch(label -> label.contains("fleetIds")));
		assertTrue(buttons.get("instance:delete_error:0").get());
		editor.buttonPressed("instance:delete_error:0");
		assertEquals(1, confirmation.size());
		assertEquals(2, new JSONObject(Files.readString(path, StandardCharsets.UTF_8)).getJSONArray("instances").length());
		confirmation.remove(0).run();
		assertEquals(new JSONArray().put(other).toString(), new JSONObject(Files.readString(path, StandardCharsets.UTF_8)).getJSONArray("instances").toString());
		assertFalse(editor.isReadOnly());
		assertTrue(buttons.get("save").get());
		page.close();
		assertFalse(((BattleNext_UiInputRouter) getField(page, "inputRouter")).getSession().hasInputState());
	}

	private BattleNext_ConfirmationDialog confirmation(BattleNext_TournamentPage page, BattleNext_UiInputRouter router) {
		return new BattleNext_ConfirmationDialog(new BattleNext_ConfirmationDialog.ConfirmationDialogHost() {
			@Override
			public boolean saveCurrentPage() {
				return page.save();
			}

			@Override
			public String getSaveError() {
				return page.getStatusText();
			}

			@Override
			public void discardCurrentPage() {
				page.discard();
			}
		}, router);
	}

	private void setField(Object target, String name, Object value) throws Exception {
		Class<?> owner = target.getClass();
		while (Arrays.stream(owner.getDeclaredFields()).noneMatch(field -> field.getName().equals(name))) {
			owner = owner.getSuperclass();
		}
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
