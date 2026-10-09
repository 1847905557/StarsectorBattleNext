package data.scripts.ui.page;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.TextFieldAPI;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.test.fixture.BattleNext_TestFleetIoFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

class BattleNext_FleetCsvExportIntegrationTest extends BattleNext_TestFleetIoFixture {

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
