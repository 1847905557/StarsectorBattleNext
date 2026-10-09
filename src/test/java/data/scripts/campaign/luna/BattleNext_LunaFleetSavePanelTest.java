package data.scripts.campaign.luna;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestCampaignFleetFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_LunaFleetSavePanelTest extends BattleNext_TestCampaignFleetFixture {

	@Test
	void nameAndFooterFitAndCorrectedNamesPublishDistinctFrozenSnapshots() throws Exception {

		int width = 560;
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		BattleNext_LunaFleetSavePanel display = open(store, width);
		CustomPanelAPI root = (CustomPanelAPI) getField(display, "panel");
		BattleNext_TextField name = (BattleNext_TextField) getField(display, "name");
		assertTrue(name.getText().matches("Player-\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
		assertEquals(((BattleNext_FleetModel) getField(display, "snapshot")).getName(), name.getText());
		assertTrue(labels.contains(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_commander"), "Player")));
		assertTrue(labels.contains(String.format(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_members"), 5)));
		assertEquals(1, children.get(root).size());
		assertTrue(name.getPosition().getX() >= root.getPosition().getX());
		assertTrue(name.getPosition().getX() + name.getPosition().getWidth() <= root.getPosition().getX() + width);
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_save").getY());
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_close").getY());
		assertTrue(buttonPositions.get("fleet_capture_save").getX() + buttonPositions.get("fleet_capture_save").getWidth()
				< buttonPositions.get("fleet_capture_close").getX());
		String id = ((BattleNext_FleetModel) getField(display, "snapshot")).getId();
		name.setText("   ");
		display.buttonPressed("fleet_capture_save");
		assertEquals("   ", ((BattleNext_TextField) getField(display, "name")).getText());
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_name_required"), getField(display, "status"));
		assertFalse(Files.exists(directory.resolve("tournament")));
		activeVariant.getApi().setNumFluxVents(99);
		String fullName = "舰队😀50% " + "Full name ".repeat(40).stripTrailing();
		((BattleNext_TextField) getField(display, "name")).setText("  " + fullName + "  ");
		display.buttonPressed("fleet_capture_save");
		display.buttonPressed("fleet_capture_save");
		assertEquals(1, store.getAll().size());
		assertEquals(id, store.getAll().get(0).getId());
		assertEquals(fullName, store.getAll().get(0).getName());
		assertEquals("Player", store.getAll().get(0).getAuthor());
		assertEquals(42, store.getAll().get(0).getVariants().get(0).toJson().getInt("fluxVents"));
		assertFalse(buttons.get("fleet_capture_save").get());
		assertEquals(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_saved"), buttonLabels.get("fleet_capture_save"));
		assertEquals("  " + fullName + "  ", ((BattleNext_TextField) getField(display, "name")).getText());
		assertTrue(((String) getField(display, "status")).contains("tournament/" + id + "/fleet_data.json"));
		assertEquals(1, children.get(root).size());
		assertEquals(1, registrations(display).size());
		display.close();
		assertTrue(children.get(root).isEmpty());
		assertTrue(registrations(display).isEmpty());
		BattleNext_LunaFleetSavePanel second = open(store, width);
		((BattleNext_TextField) getField(second, "name")).setText(fullName);
		second.buttonPressed("fleet_capture_save");
		assertEquals(2, store.getAll().size());
		assertTrue(store.getAll().stream().allMatch(snapshot -> fullName.equals(snapshot.getName())));
		assertNotEquals(id, ((BattleNext_FleetModel) getField(second, "snapshot")).getId());
		second.close();
	}

	@Test
	void narrowPanelKeepsNameAndFooterWithinBounds() throws Exception {
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		BattleNext_LunaFleetSavePanel display = open(store, 320);
		CustomPanelAPI root = (CustomPanelAPI) getField(display, "panel");
		BattleNext_TextField name = (BattleNext_TextField) getField(display, "name");
		assertTrue(name.getPosition().getX() >= root.getPosition().getX());
		assertTrue(name.getPosition().getX() + name.getPosition().getWidth() <= root.getPosition().getX() + 320f);
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_save").getY());
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_close").getY());
		assertTrue(buttonPositions.get("fleet_capture_save").getX() + buttonPositions.get("fleet_capture_save").getWidth()
				< buttonPositions.get("fleet_capture_close").getX());
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"load", "write"})
	void saveFailureRetainsNameSnapshotAndIdentityForRetry(String failure) throws Exception {
		fleetMembers.subList(1, fleetMembers.size()).clear();
		FailingFleetStore store = new FailingFleetStore();
		store.setFailLoad("load".equals(failure));
		store.setFailWrite("write".equals(failure));
		BattleNext_LunaFleetSavePanel display = open(store, 560);
		BattleNext_FleetModel snapshot = (BattleNext_FleetModel) getField(display, "snapshot");
		String id = snapshot.getId();
		((BattleNext_TextField) getField(display, "name")).setText("Retry fleet");
		display.buttonPressed("fleet_capture_save");
		assertTrue(store.getAll().isEmpty());
		assertSame(snapshot, getField(display, "snapshot"));
		assertEquals("Retry fleet", ((BattleNext_TextField) getField(display, "name")).getText());
		assertTrue(buttons.get("fleet_capture_save").get());
		assertTrue(((String) getField(display, "status")).contains("Injected fleet " + failure + " failure"));
		assertFalse(Files.exists(directory.resolve("tournament/" + id + "/fleet_data.json")));
		store.setFailLoad(false);
		store.setFailWrite(false);
		display.buttonPressed("fleet_capture_save");
		assertEquals(1, store.getAll().size());
		assertEquals(id, store.getAll().get(0).getId());
		assertEquals("Retry fleet", store.getAll().get(0).getName());
		assertEquals(42, store.getAll().get(0).getVariants().get(0).toJson().getInt("fluxVents"));
		assertTrue(Files.exists(directory.resolve("tournament/" + id + "/fleet_data.json")));
		assertJsonEquals(snapshot.toJson(), new JSONObject(Files.readString(directory.resolve(snapshot.getSourcePath()))));
		assertJsonEquals(snapshot.getVariants().get(0).toJson(), new JSONObject(Files.readString(
				directory.resolve("tournament/" + id + "/" + snapshot.getVariants().get(0).getId() + ".variant"))));
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"context", "cycle", "missing", "hull"})
	void captureFailureReportsErrorAndDisablesSaving(String failure) throws Exception {
		if ("cycle".equals(failure) || "missing".equals(failure)) {
			activeVariant.getApi().setModuleVariant("module", "cycle".equals(failure) ? activeVariant.getApi() : null);
		}
		failBuild = "hull".equals(failure);
		BattleNext_LunaFleetSavePanel display = new BattleNext_LunaFleetSavePanel(() -> {});
		display.open(selected, activeVariant.getApi(), "context".equals(failure) ? "Specific context error" : "");
		display.attach(panel(560f, 300f, display), 560f, 300f);
		assertNull(getField(display, "snapshot"));
		assertFalse(buttons.get("fleet_capture_save").get());
		assertTrue(((String) getField(display, "status")).contains("context".equals(failure)
				? "Specific context error" : BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_player_save_snapshot_error")));
		String reason = switch (failure) {
			case "context" -> "Specific context error";
			case "cycle" -> "Cyclic variant modules";
			case "missing" -> "Missing module: module";
			default -> "Unknown hull: parent";
		};
		assertTrue(((String) getField(display, "status")).contains(reason));
		display.buttonPressed("fleet_capture_save");
		assertFalse(Files.exists(directory.resolve("tournament")));
		display.close();
	}

	@Test
	void closingReleasesFocusRegistrationsAndMountedContentsAcrossReopenings() throws Exception {

		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_LunaFleetSavePanel[] holder = new BattleNext_LunaFleetSavePanel[1];
		holder[0] = new BattleNext_LunaFleetSavePanel(() -> {
			closed.set(true);
			holder[0].close();
		});
		BattleNext_LunaFleetSavePanel display = holder[0];
		CustomPanelAPI root = panel(560f, 300f, display);
		for (String path : List.of("button", "escape", "outside")) {
			closed.set(false);
			display.open(selected, activeVariant.getApi(), "");
			display.attach(root, 560f, 300f);
			BattleNext_TextField name = (BattleNext_TextField) getField(display, "name");
			name.grabFocus(false);
			assertTrue(name.hasFocus());
			assertEquals(1, registrations(display).size());
			if ("button".equals(path)) {
				display.buttonPressed("fleet_capture_close");
			} else {
				AtomicBoolean consumed = new AtomicBoolean();
				display.processInput(List.of(event(path, consumed)));
				assertTrue(consumed.get());
			}
			assertTrue(closed.get());
			assertFalse(name.hasFocus());
			assertTrue(registrations(display).isEmpty());
			assertTrue(((List<?>) getField(name, "ownedPanels")).isEmpty());
			assertTrue(children.get(root).isEmpty());
			assertNull(getField(display, "snapshot"));
			display.buttonPressed("fleet_capture_save");
			assertFalse(Files.exists(directory.resolve("tournament")));
		}
	}

	private BattleNext_LunaFleetSavePanel open(BattleNext_FleetStore store, int width) {
		BattleNext_LunaFleetSavePanel display = new BattleNext_LunaFleetSavePanel(() -> {}, store);
		display.open(selected, activeVariant.getApi(), "");
		display.attach(panel(width, 300f, display), width, 300f);
		return display;
	}

	private Map<?, ?> registrations(BattleNext_LunaFleetSavePanel display) throws Exception {
		BattleNext_UiInputRouter router = (BattleNext_UiInputRouter) getField(display, "inputRouter");
		return (Map<?, ?>) getField(router.getSession(), "registrations");
	}
}
