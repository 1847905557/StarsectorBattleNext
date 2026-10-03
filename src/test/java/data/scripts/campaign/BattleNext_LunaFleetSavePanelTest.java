package data.scripts.campaign;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestCampaignFleetFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_LunaFleetSavePanelTest extends BattleNext_TestCampaignFleetFixture {

	@ParameterizedTest
	@ValueSource(ints = {320, 560})
	void nameAndFooterFitAndSavingPublishesOneFrozenSnapshot(int width) throws Exception {

		BattleNext_FleetStore store = new BattleNext_FleetStore();
		BattleNext_LunaFleetSavePanel display = open(store, width);
		CustomPanelAPI root = (CustomPanelAPI) getField(display, "panel");
		BattleNext_TextField name = (BattleNext_TextField) getField(display, "name");
		assertTrue(name.getText().matches("Player-\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
		assertEquals(((BattleNext_FleetModel) getField(display, "snapshot")).getName(), name.getText());
		assertTrue(labels.contains("Commander: Player"));
		assertTrue(labels.contains("Members: 5"));
		assertEquals(1, children.get(root).size());
		assertTrue(name.getPosition().getX() >= root.getPosition().getX());
		assertTrue(name.getPosition().getX() + name.getPosition().getWidth() <= root.getPosition().getX() + width);
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_save").getY());
		assertEquals(root.getPosition().getY() + 10f, buttonPositions.get("fleet_capture_close").getY());
		assertTrue(buttonPositions.get("fleet_capture_save").getX() + buttonPositions.get("fleet_capture_save").getWidth()
				< buttonPositions.get("fleet_capture_close").getX());
		String id = ((BattleNext_FleetModel) getField(display, "snapshot")).getId();
		activeVariant.getApi().setNumFluxVents(99);
		name.setText("  Named fleet  ");
		display.buttonPressed("fleet_capture_save");
		display.buttonPressed("fleet_capture_save");
		assertEquals(1, store.getAll().size());
		assertEquals(id, store.getAll().get(0).getId());
		assertEquals("Named fleet", store.getAll().get(0).getName());
		assertEquals("Player", store.getAll().get(0).getAuthor());
		assertEquals(42, store.getAll().get(0).getVariants().get(0).toJson().getInt("fluxVents"));
		assertFalse(buttons.get("fleet_capture_save").get());
		assertEquals("BattleNext_common_saved", buttonLabels.get("fleet_capture_save"));
		assertEquals("  Named fleet  ", ((BattleNext_TextField) getField(display, "name")).getText());
		assertTrue(((String) getField(display, "status")).contains("tournament/" + id + "/fleet_data.json"));
		assertEquals(1, children.get(root).size());
		assertEquals(1, registrations(display).size());
		display.close();
		assertTrue(children.get(root).isEmpty());
		assertTrue(registrations(display).isEmpty());
	}

	@Test
	void blankNameCanBeCorrectedAndSeparateOpeningsPermitSameName() throws Exception {
		BattleNext_FleetStore store = new BattleNext_FleetStore();
		BattleNext_LunaFleetSavePanel first = open(store, 560);
		((BattleNext_TextField) getField(first, "name")).setText("   ");
		first.buttonPressed("fleet_capture_save");
		assertEquals("   ", ((BattleNext_TextField) getField(first, "name")).getText());
		assertEquals("BattleNext_fleet_player_save_name_required", getField(first, "status"));
		assertFalse(Files.exists(directory.resolve("tournament")));
		((BattleNext_TextField) getField(first, "name")).setText("Same name");
		first.buttonPressed("fleet_capture_save");
		String firstId = store.getAll().get(0).getId();
		first.close();
		BattleNext_LunaFleetSavePanel second = open(store, 560);
		((BattleNext_TextField) getField(second, "name")).setText("Same name");
		second.buttonPressed("fleet_capture_save");
		assertEquals(2, store.getAll().size());
		assertTrue(store.getAll().stream().allMatch(snapshot -> "Same name".equals(snapshot.getName())));
		assertNotEquals(firstId, ((BattleNext_FleetModel) getField(second, "snapshot")).getId());
		second.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"load", "write"})
	void saveFailureRetainsNameSnapshotAndIdentityForRetry(String failure) throws Exception {
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
		store.setFailLoad(false);
		store.setFailWrite(false);
		display.buttonPressed("fleet_capture_save");
		assertEquals(1, store.getAll().size());
		assertEquals(id, store.getAll().get(0).getId());
		assertTrue(Files.exists(directory.resolve("tournament/" + id + "/fleet_data.json")));
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
				? "Specific context error" : "BattleNext_fleet_player_save_snapshot_error"));
		display.buttonPressed("fleet_capture_save");
		assertFalse(Files.exists(directory.resolve("tournament")));
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"button", "escape", "outside"})
	void closingReleasesFocusRegistrationsAndMountedContents(String path) throws Exception {

		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_LunaFleetSavePanel[] holder = new BattleNext_LunaFleetSavePanel[1];
		holder[0] = new BattleNext_LunaFleetSavePanel(() -> {
			closed.set(true);
			holder[0].close();
		});
		BattleNext_LunaFleetSavePanel display = holder[0];
		CustomPanelAPI root = panel(560f, 300f, display);
		for (int iteration = 0; iteration < 3; iteration++) {
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
		return (Map<?, ?>) getField(router.getSession(), "REGISTERED");
	}
}
