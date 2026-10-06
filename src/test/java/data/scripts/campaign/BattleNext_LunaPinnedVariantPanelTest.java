package data.scripts.campaign;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
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
		assertEquals(120f, list.getExternalScroller().getYOffset());
		display.buttonPressed("pin_save");
		assertFalse(buttons.get("pin_save").get());
		assertEquals(fullName, name(display).getText());
		assertEquals(fullName, store.getAll().get(0).getDisplayName());
		assertEquals(120f, lists(display).get("groups").getExternalScroller().getYOffset());
		assertAligned(display);
		display.close();
	}

	@Test
	void savingReplacesTheManagedContentsTreeWithoutDuplicateRootPanels() throws Exception {

		BattleNext_LunaPinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), 1000);
		CustomPanelAPI rightPanel = (CustomPanelAPI) getField(display, "rightPanel");
		CustomPanelAPI firstContentsPanel = (CustomPanelAPI) getField(display, "contentsPanel");
		assertEquals(1, children.getOrDefault(rightPanel, List.of()).size());

		display.buttonPressed("pin_save");

		CustomPanelAPI secondContentsPanel = (CustomPanelAPI) getField(display, "contentsPanel");
		assertNotSame(firstContentsPanel, secondContentsPanel);
		assertEquals(List.of(secondContentsPanel), children.get(rightPanel));
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
	void staticMotherPreviewCentersAndScalesWithoutChangingNativeSpriteDimensions() throws Exception {
		spriteWidth = 600f;
		spriteHeight = 1200f;
		BattleNext_LunaPinnedVariantPanel display = open(source(), new BattleNext_PinnedVariantStore(), 1000);
		CustomUIPanelPlugin preview = plugins.values().stream()
				.filter(plugin -> plugin.getClass().getSimpleName().equals("PreviewPlugin")).findFirst().orElseThrow();
		Object model = getField(preview, "preview");
		List<?> parts = (List<?>) getField(model, "parts");
		SpriteAPI sprite = (SpriteAPI) getField(parts.get(0), "sprite");
		float[] original = sprites.get(sprite).clone();
		PositionAPI position = (PositionAPI) getField(preview, "position");
		preview.render(0.5f);
		assertEquals(2, spriteCenters.size());
		assertArrayEquals(new float[]{position.getCenterX(), position.getCenterY()}, spriteCenters.get(0));
		assertArrayEquals(original, sprites.get(sprite));
		assertTrue(spriteSizes.get(0)[0] <= position.getWidth());
		assertTrue(spriteSizes.get(0)[1] <= (Float) getField(preview, "imageHeight"));
		assertEquals(0.5f, spriteSizes.get(0)[0] / spriteSizes.get(0)[1]);
		PositionAPI frame = geometry.get(position).getParent();
		assertEquals(frame.getCenterX(), position.getCenterX());
		assertEquals(frame.getY() + (frame.getHeight() - 18f) * 0.5f, position.getCenterY());
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
		assertTrue(last.getY() - position.getCenterY() - spriteSizes.get(0)[1] * 0.5f >= 8f - 0.001f);
		display.close();
	}

	@Test
	void openingFreezesFullParentSnapshotAndCollectionIsConsumable() throws Exception {

		Variant source = source();
		String before = BattleNext_VariantBuilder.fromVariant(source.getApi()).toJson().toString();
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source, store, 640);
		assertEquals(before, BattleNext_VariantBuilder.fromVariant(source.getApi()).toJson().toString());
		source.getJson().put("displayName", "Later change");
		source.getModules().get("module").setNumFluxVents(99);
		String changed = BattleNext_VariantBuilder.fromVariant(source.getApi()).toJson().toString();
		display.buttonPressed("pin_save");
		display.buttonPressed("pin_save");
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
		BattleNext_VariantSourceProvider provider = new BattleNext_VariantSourceProvider();
		assertTrue(provider.getOptions().stream().anyMatch(option -> option.getId().equals("pinned:" + pin.getPinId())));
		BattleNext_VariantBuilder local = BattleNext_VariantBuilder.fromVariant(provider.resolve("pinned:" + pin.getPinId()));
		BattleNext_FleetModel fleet = new BattleNext_FleetModel();
		fleet.putVariant(local);
		store.delete(pin.getPinId());
		assertEquals("Unsubmitted", fleet.getVariant(pin.getPinId()).build().getDisplayName());
		assertNotNull(fleet.getVariant(pin.getPinId()).build().getModuleVariant("module"));
		display.close();
	}

	@Test
	void separateOpeningsPermitIdenticalNamesAndSnapshots() throws Exception {
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		Variant source = source();
		BattleNext_LunaPinnedVariantPanel first = open(source, store, 640);
		first.buttonPressed("pin_save");
		first.close();
		BattleNext_LunaPinnedVariantPanel second = open(source, store, 640);
		second.buttonPressed("pin_save");
		assertEquals(2, store.getAll().size());
		assertNotEquals(store.getAll().get(0).getPinId(), store.getAll().get(1).getPinId());
		assertEquals(store.getAll().get(0).getDisplayName(), store.getAll().get(1).getDisplayName());
		second.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "\t\r\n", "\u3000", "\u2003"})
	void blankNameIsRejectedAndCanBeCorrected(String blank) throws Exception {
		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source(), store, 640);
		name(display).setText(blank);
		display.buttonPressed("pin_save");
		assertTrue(labels.contains("BattleNext_variant_pin_name_required"));
		assertEquals(blank, name(display).getText());
		assertFalse(Files.exists(directory.resolve("tournament/_pinnedVariants")));
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
		assertTrue(labels.stream().anyMatch(value -> value.contains("Unknown hull")));
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
		assertTrue(labels.stream().anyMatch(value -> value.contains("cycle".equals(failure) ? "Cyclic variant" : "Missing module")));
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
	void loadFailurePreservesNameAndAllowsRetry() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		BattleNext_LunaPinnedVariantPanel display = open(source(), store, 640);
		name(display).setText("Keep this name");
		Files.createDirectory(directory.resolve("tournament"));
		Files.writeString(directory.resolve("tournament/_pinnedVariants"), "blocked");
		display.buttonPressed("pin_save");
		assertEquals("Keep this name", name(display).getText());
		assertTrue(buttons.get("pin_save").get());
		assertTrue(labels.stream().anyMatch(value -> value.contains("Not a directory")));
		assertTrue(store.getAll().isEmpty());
		Files.delete(directory.resolve("tournament/_pinnedVariants"));
		display.buttonPressed("pin_save");
		assertEquals(1, store.getAll().size());
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
	void malformedExistingPinIsReportedWhileNewPinIsSaved() throws Exception {
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
