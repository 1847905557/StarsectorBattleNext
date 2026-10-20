package data.scripts.campaign.luna;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.widget.provider.BattleNext_VariantSourceProvider;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_LunaPinnedVariantPanelTest extends BattleNext_TestVariantFixture {

	@ParameterizedTest
	@ValueSource(ints = {320, 640})
	void panelUsesSameOwnerAndFitsNarrowAndWideWidths(int width) throws Exception {
		BattleNext_LunaPinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), width);
		assertTrue(buttons.get("pin_save").get());
		assertTrue(labels.stream().anyMatch(value -> value.equals("Parent")));
		assertTrue(labels.stream().anyMatch(value -> value.contains("Unsubmitted")));
		assertEquals("Parent - Unsubmitted", name(display).getText());
		assertAligned(display);
		PositionAPI grid = detailBounds(display);
		assertEquals(width - 40f, grid.getWidth());
		for (String key : List.of("plugins", "modules", "groups", "wings")) {
			TooltipState state = tooltips.get(lists(display).get(key));
			assertTrue(state.isScrollable());
			assertTrue(state.isMounted());
			assertEquals(state.getUsedHeight(), state.getMountedHeight());
			PositionAPI frame = geometry.get(state.getViewport()).getParent();
			assertTrue(frame.getX() >= grid.getX());
			assertTrue(frame.getX() + frame.getWidth() <= grid.getX() + grid.getWidth());
		}
		display.close();
		assertFalse(buttons.get("pin_save").get());
		assertTrue(lists(display).isEmpty());
	}

	@Test
	void lunaPanelSharesDetailsAndKeepsNameExpansionAndScrollAfterSaving() throws Exception {
		Variant source = source();
		JSONArray groups = new JSONArray();
		for (int index = 0; index < 20; index++) {
			groups.put(new JSONObject(source.getJson().getJSONArray("weaponGroups").getJSONObject(0).toString()));
		}
		source.getJson().put("weaponGroups", groups);
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source, store, 1000);
		String fullName = "装配😀50% " + "Full name ".repeat(40).stripTrailing();
		name(display).setText(fullName);
		TooltipMakerAPI list = lists(display).get("groups");
		list.getExternalScroller().setYOffset(120f);
		display.buttonPressed("pin_save");
		assertFalse(buttons.get("pin_save").get());
		assertEquals(fullName, name(display).getText());
		assertEquals(fullName, store.getAll().get(0).getDisplayName());
		assertEquals(120f, lists(display).get("groups").getExternalScroller().getYOffset());
		assertAligned(display);
		display.close();
	}

	@Test
	void missingPreviewIsLocalizedAndValidSnapshotCanStillBePinned() throws Exception {
		failSprites = true;
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source(), store, 1000);
		assertTrue(labels.stream().anyMatch(value -> value.contains("Missing sprite")));
		assertTrue(buttons.get("pin_save").get());
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
		display.close();
	}

	@Test
	void previewBindsTheCapturedHullAndModuleToItsParentFrame() throws Exception {

		spriteWidth = 600f;
		spriteHeight = 1200f;
		BattleNext_LunaPinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), 1000);
		Object details = getField(display, "details");
		CustomUIPanelPlugin preview = (CustomUIPanelPlugin) getField(details, "preview");
		PositionAPI position = (PositionAPI) getField(preview, "position");
		PositionAPI frame = geometry.get(position).getParent();
		assertEquals(frame.getCenterX(), position.getCenterX());
		assertEquals(frame.getY() + (frame.getHeight() - 18f) * 0.5f, position.getCenterY());
		assertEquals("Parent - Unsubmitted", name(display).getText());
		preview.render(0.5f);
		assertEquals(List.of("graphics/parent.png", "graphics/child.png"),
				renderedSprites.stream().map(spritePaths::get).toList());
		TooltipState identity = tooltips.get(lists(display).get("identity"));
		PositionAPI first = identity.getComponents().get(0).getPosition();
		assertEquals(4f, frame.getY() + frame.getHeight() - 18f - first.getY() - first.getHeight());
		for (int index = 0; index < identity.getComponents().size(); index++) {
			PositionAPI row = identity.getComponents().get(index).getPosition();
			assertEquals(8f, row.getX() - frame.getX());
			assertEquals(8f, frame.getX() + frame.getWidth() - row.getX() - row.getWidth());
			if (index > 0) {
				PositionAPI previous = identity.getComponents().get(index - 1).getPosition();
				assertEquals(4f, previous.getY() - row.getY() - row.getHeight());
			}
		}
		PositionAPI last = identity.getComponents().get(identity.getComponents().size() - 1).getPosition();
		assertTrue(last.getY() - spriteCenters.get(0)[1] - spriteRenderStates.get(0)[1] * 0.5f >= 8f - 0.001f);
		display.close();
		assertNull(getField(preview, "preview"));
	}

	@Test
	void openingFreezesFullSnapshotAndDistinctPinsReplaceContentsAndRemainConsumable() throws Exception {

		Variant source = source();
		String before = BattleNext_VariantBuilder.fromVariant(source.getApi()).toJson().toString();
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source, store, 640);
		CustomPanelAPI rightPanel = (CustomPanelAPI) getField(display, "rightPanel");
		CustomPanelAPI firstContentsPanel = (CustomPanelAPI) getField(display, "contentsPanel");
		assertEquals(List.of(firstContentsPanel), children.get(rightPanel));
		assertEquals(before, BattleNext_VariantBuilder.fromVariant(source.getApi()).toJson().toString());
		source.getJson().put("displayName", "Later change");
		source.getModules().get("module").setNumFluxVents(99);
		String changed = BattleNext_VariantBuilder.fromVariant(source.getApi()).toJson().toString();
		display.buttonPressed("pin_save");
		display.buttonPressed("pin_save");
		CustomPanelAPI secondContentsPanel = (CustomPanelAPI) getField(display, "contentsPanel");
		assertNotSame(firstContentsPanel, secondContentsPanel);
		assertEquals(List.of(secondContentsPanel), children.get(rightPanel));
		assertEquals(1, store.getAll().size());
		BattleNext_PinnedVariantModel pin = store.getAll().get(0);
		JSONObject json = pin.getVariantJson();
		assertEquals("Unsubmitted", json.getString("displayName"));
		assertEquals(7, json.getJSONObject("modules").getJSONObject("module").getInt("fluxVents"));
		assertEquals("built", json.getJSONArray("sModdedBuiltIns").getString(0));
		assertEquals("d", json.getJSONArray("suppressedMods").getString(0));
		assertEquals("sm", json.getJSONArray("sMods").getString(0));
		assertEquals("wing", json.getJSONArray("wings").getString(0));
		assertEquals("laser", json.getJSONArray("weaponGroups").getJSONObject(0).getJSONObject("weapons").getString("weapon"));
		assertFalse(buttons.get("pin_save").get());
		assertEquals(changed, BattleNext_VariantBuilder.fromVariant(source.getApi()).toJson().toString());
		Path path = directory.resolve("tournament/_pinnedVariants/" + pin.getPinId() + ".json");
		assertTrue(Files.exists(path));
		assertEquals(pin.getPinId(), new JSONObject(Files.readString(path)).getString("pinId"));
		display.close();
		BattleNext_LunaPinnedVariantPanel second = open(source(), store, 640);
		second.buttonPressed("pin_save");
		assertEquals(2, store.getAll().size());
		BattleNext_PinnedVariantModel secondPin = store.getAll().get(1);
		assertNotEquals(pin.getPinId(), secondPin.getPinId());
		assertEquals(pin.getDisplayName(), secondPin.getDisplayName());
		assertJsonEquals(pin.getVariantJson(), secondPin.getVariantJson());
		second.close();
		BattleNext_VariantSourceProvider provider = new BattleNext_VariantSourceProvider();
		assertTrue(provider.getOptions().stream().anyMatch(option -> option.getId().equals("pinned:" + pin.getPinId())));
		BattleNext_VariantBuilder local = BattleNext_VariantBuilder.fromVariant(provider.resolve("pinned:" + pin.getPinId()));
		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		fleet.putVariant(local);
		store.delete(pin.getPinId());
		assertEquals("Unsubmitted", fleet.getVariant(pin.getPinId()).build().getDisplayName());
		assertNotNull(fleet.getVariant(pin.getPinId()).build().getModuleVariant("module"));
	}

	@Test
	void blankNamesAreRejectedAndCanBeCorrected() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source(), store, 640);
		for (String blank : List.of("", " \t\r\n", "\u3000")) {
			labels.clear();
			name(display).setText(blank);
			display.buttonPressed("pin_save");
			assertEquals(1, display.getSaveErrors().size());
			assertTrue(display.getSaveErrors().get(0).endsWith(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_pin_name_required")));
			assertTrue(labels.contains(String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_errors_summary"),
					1, display.getSaveErrors().get(0))));
			assertEquals(blank, name(display).getText());
			assertTrue(buttons.get("pin_save").get());
			assertTrue(store.getAll().isEmpty());
			assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		}
		name(display).setText("Named snapshot");
		display.buttonPressed("pin_save");
		assertEquals("Named snapshot", store.getAll().get(0).getDisplayName());
		display.close();
	}

	@Test
	void invalidContextAndRebuildFailureDisableSubmission() throws Exception {

		BattleNext_LunaPinnedVariantPanel display = new BattleNext_LunaPinnedVariantPanel(() -> {});
		display.open(null, "Specific context error");
		display.attach(panel(640f, 560f, display), 640f, 560f);
		assertTrue(labels.contains("Specific context error"));
		assertFalse(buttons.get("pin_save").get());
		display.buttonPressed("pin_save");
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		Variant source = source();
		failBuild = true;
		display.open(source.getApi(), "");
		display.attach(panel(640f, 560f, display), 640f, 560f);
		assertTrue(labels.stream().anyMatch(value -> value.contains("Unknown hull: parent")));
		assertFalse(buttons.get("pin_save").get());
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"cycle", "missing"})
	void invalidModuleGraphIsLocalizedAndCannotBeSaved(String failure) throws Exception {
		Variant source = source();
		source.getModules().put("module", "cycle".equals(failure) ? source.getApi() : null);
		BattleNext_LunaPinnedVariantPanel display = open(source, new BattleNext_PinnedVariantStore(), 640);
		assertFalse(buttons.get("pin_save").get());
		assertTrue(labels.stream().anyMatch(value -> value.contains("cycle".equals(failure) ? "Cyclic variant modules" : "Missing module: module")));
		display.buttonPressed("pin_save");
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
		display.close();
	}

	@Test
	void textInputUsesItsOwnSessionAndConsumesKeyboardEvents() throws Exception {

		BattleNext_LunaPinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), 640);
		TextFieldAPI field = name(display);
		field.grabFocus(false);
		AtomicBoolean consumed = new AtomicBoolean();
		InputEventAPI selectAll = proxy(InputEventAPI.class, (method, args) -> switch (method) {
			case "isConsumed" -> consumed.get();
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			case "isKeyboardEvent", "isKeyDownEvent", "isCtrlDown" -> true;
			case "getEventValue" -> Keyboard.KEY_A;
			default -> null;
		});
		display.processInput(List.of(selectAll));
		assertTrue(consumed.get());
		assertTrue(field.hasFocus());
		assertEquals("Parent - Unsubmitted", field.getText());
		display.close();
		assertFalse(field.hasFocus());
	}

	@Test
	void unavailableDirectoryPreservesDraftAndAllowsCommitAfterRepair() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source(), store, 640);
		name(display).setText("Keep this name");
		Files.createDirectory(directory.resolve("tournament"));
		Files.writeString(directory.resolve("tournament/_pinnedVariants"), "blocked");
		display.buttonPressed("pin_save");
		assertEquals("Keep this name", name(display).getText());
		assertTrue(buttons.get("pin_save").get());
		assertTrue(labels.stream().anyMatch(value -> value.contains("_pinnedVariants")));
		assertTrue(store.getAll().isEmpty());
		assertEquals("blocked", Files.readString(directory.resolve("tournament/_pinnedVariants")));
		Files.delete(directory.resolve("tournament/_pinnedVariants"));
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
		assertEquals("Keep this name", store.getAll().get(0).getDisplayName());
		assertTrue(Files.exists(directory.resolve(store.getAll().get(0).getSourcePath())));
		display.close();
	}

	@Test
	void saverFailureDoesNotPublishOrDisableTheDraft() throws Exception {

		FailingStore store = new FailingStore();
		BattleNext_LunaPinnedVariantPanel display = open(source(), store, 640);
		name(display).setText("Retry after write failure");
		display.buttonPressed("pin_save");
		assertTrue(store.getAll().isEmpty());
		assertEquals("Retry after write failure", name(display).getText());
		assertTrue(buttons.get("pin_save").get());
		assertTrue(labels.stream().anyMatch(value -> value.contains("Injected write failure")));
		store.getWriter().setFail(false);
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
		display.close();
	}

	@Test
	void malformedCatalogPinIsReportedWhileNewPinIsSaved() throws Exception {
		Files.createDirectories(directory.resolve("tournament/_pinnedVariants"));
		Files.writeString(directory.resolve("tournament/_pinnedVariants/broken.json"), "{}");
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source(), store, 640);
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
		assertEquals(1, store.getLoadErrors().size());
		assertTrue(labels.stream().anyMatch(value -> value.contains("broken.json")));
		display.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"button", "escape", "outside"})
	void closePathsReleaseFocusAndNeverPersist(String path) throws Exception {

		AtomicBoolean closed = new AtomicBoolean();
		BattleNext_LunaPinnedVariantPanel[] holder = new BattleNext_LunaPinnedVariantPanel[1];
		holder[0] = new BattleNext_LunaPinnedVariantPanel(() -> {
			closed.set(true);
			holder[0].close();
		});
		BattleNext_LunaPinnedVariantPanel display = holder[0];
		display.open(source().getApi(), "");
		display.attach(panel(640f, 560f, display), 640f, 560f);
		TextFieldAPI field = name(display);
		field.grabFocus(false);
		assertTrue(field.hasFocus());
		AtomicBoolean consumed = new AtomicBoolean();
		if ("button".equals(path)) {
			display.buttonPressed("pin_close");
		} else {
			display.processInput(List.of(event(path, consumed)));
			assertTrue(consumed.get());
		}
		assertTrue(closed.get());
		assertFalse(field.hasFocus());
		assertFalse(buttons.get("pin_save").get());
		display.buttonPressed("pin_save");
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
	}
}
