package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_ConfirmationDialog;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.List;

class BattleNext_FleetCsvExportIntegrationTest extends BattleNext_TestFleetIoFixture {

	@ParameterizedTest
	@ValueSource(strings = {"", "package", "shared"})
	void numberedCsvDeletionShowsItsScopeAndCommitsOnlyAfterConfirmation(String folder) throws Exception {

		csvFleet(folder, "01", "1,parent_variant,,,,,,\n");
		if (folder.equals("shared")) {
			csvFleet(folder, "1", "1,parent_variant,,,,,,\n");
		}
		String prefix = folder.isEmpty() ? "tournament" : "tournament/" + folder;
		byte[] metadata = Files.readAllBytes(directory.resolve(prefix + "/player01_data.csv"));
		byte[] members = Files.readAllBytes(directory.resolve(prefix + "/player01_fleet.csv"));
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		BattleNext_ConfirmationDialog dialog = new BattleNext_ConfirmationDialog(
				new BattleNext_ConfirmationDialog.ConfirmationDialogHost() {
					@Override
					public boolean saveCurrentPage() {
						return true;
					}

					@Override
					public String getSaveError() {
						return "";
					}

					@Override
					public void discardCurrentPage() {

					}
				}, router);
		var root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		BattleNext_FleetLibraryPage page = new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, dialog::requestDelete);
		page.setInputRouter(router);
		page.setOverlayHost(root);
		page.attach(root);
		dialog.attach(root);
		try {
			page.buttonPressed("fleet:01");
			assertTrue(buttons.get("delete").get());
			page.buttonPressed("delete");
			assertTrue(dialog.isActive());
			String scope = folder.equals("package")
					? String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_delete_directory"), prefix)
					: String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_delete_files"),
							prefix + "/player01_data.csv\n" + prefix + "/player01_fleet.csv");
			CustomPanelAPI popup = (CustomPanelAPI) getField(dialog, "confirmation");
			List<LabelAPI> labels = children.get(popup).stream().flatMap(child -> tooltips.get(child).getComponents().stream())
					.filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).toList();
			assertTrue(labels.stream().anyMatch(label -> label.getText().equals("Team\n" + scope)));
			assertTrue(labels.stream().allMatch(label -> label.getPosition().getY() >=
					buttonPositions.get("delete_confirm").getY() + buttonPositions.get("delete_confirm").getHeight()));
			dialog.handle("delete_cancel");
			assertArrayEquals(metadata, Files.readAllBytes(directory.resolve(prefix + "/player01_data.csv")));
			assertArrayEquals(members, Files.readAllBytes(directory.resolve(prefix + "/player01_fleet.csv")));
			page.buttonPressed("delete");
			dialog.handle("delete_confirm");
			BattleNext_FleetStore store = (BattleNext_FleetStore) getField(page, "store");
			assertNull(store.get("01"));
			assertEquals("", getField(page, "selectedId"));
			assertEquals(false, getField(page, "draftOpen"));
			assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_deleted"), page.getStatusText());
			assertFalse(Files.exists(directory.resolve(prefix + "/player01_data.csv")));
			assertFalse(Files.exists(directory.resolve(prefix + "/player01_fleet.csv")));
			if (folder.equals("shared")) {
				assertNotNull(store.get("1"));
			}
			store.reload();
			assertNull(store.get("01"));
		} finally {
			dialog.close();
			page.close();
		}
	}

	@Test
	void editorExportsToANewDirectoryThenRefreshesTheFormalCatalog() throws Exception {

		csvFleet("package", "01", "1,parent_local,,,,,,\n");
		variant("package", "parent_local", "parent", 12, new JSONObject().put("module", "child_local"));
		variant("package", "child_local", "child", 19, null);
		var original = Files.readAllBytes(directory.resolve("tournament/package/parent_local.variant"));
		BattleNext_FleetLibraryPage page = new BattleNext_FleetLibraryPage(() -> {}, Runnable::run, (description, action) -> {});
		var root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());
		page.setOverlayHost(root);
		page.attach(root);
		page.buttonPressed("fleet:01");
		BattleNext_FleetEditor editor = (BattleNext_FleetEditor) getField(page, "editor");
		assertFalse(page.hasUnsavedChanges());
		assertFalse(page.save());
		((TextFieldAPI) getField(editor, "idField")).setText("exported");
		assertFalse(page.save());
		((TextFieldAPI) getField(editor, "folderNameField")).setText("exported");
		assertTrue(page.save(), page.getStatusText());
		BattleNext_FleetStore store = (BattleNext_FleetStore) getField(page, "store");
		assertEquals(2, store.getAll().size());
		assertEquals("exported", getField(page, "selectedId"));
		assertFalse(page.hasUnsavedChanges());
		assertEquals(19, store.get("exported").getVariant("parent_local").build()
				.getModuleVariant("module").toJSONObject().getInt("fluxVents"));
		assertArrayEquals(original, Files.readAllBytes(directory.resolve("tournament/package/parent_local.variant")));
		page.close();
		page.attach(root);
		page.buttonPressed("fleet:exported");
		assertEquals("exported", getField(page, "selectedId"));
		page.close();
	}
}
