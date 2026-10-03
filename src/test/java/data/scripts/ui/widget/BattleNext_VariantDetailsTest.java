package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.page.BattleNext_PinnedVariantPage;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BattleNext_VariantDetailsTest extends BattleNext_TestVariantFixture {

	@Test
	void moduleSummariesRetainIdentityAndIsolateErrors() throws Exception {
		JSONObject json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		json.getJSONObject("modules").put("broken", new JSONObject().put("hullId", "missing"));
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		details.setVariantJson(json);
		details.attach(panel(600f, 700f, new BaseCustomUIPanelPlugin()));
		assertTrue(labels.stream().anyMatch(value -> value.contains("Child")));
		TooltipState modules = tooltips.get(lists(details).get("modules"));
		for (var component : modules.getComponents()) {
			if (!(component instanceof CustomPanelAPI row)) continue;
			for (Object item : children.get(row)) {
				var card = (CustomPanelAPI) item;
				var caption = tooltips.get(children.get(card).get(0));
				attachedTooltips.get(caption.getComponents().get(0)).createTooltip(tooltip(600f, 400f, false), false, null);
			}
		}
		assertTrue(labels.contains("child"));
		assertTrue(labels.contains("broken"));
		assertTrue(labels.stream().anyMatch(value -> value.contains("Unknown module hull")));
		assertEquals(modules.getUsedHeight(), modules.getMountedHeight());
		assertEquals(2, modules.getComponents().stream().filter(CustomPanelAPI.class::isInstance).count());
	}

	@Test
	void weaponToggleKeepsEditedNameAndRespectsModalPriority() throws Exception {

		BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
		store.reload();
		var pin = store.saveVariant("Original", source().getApi());
		BattleNext_PinnedVariantPage page = page(store, Runnable::run, (description, action) -> {}, () -> {});
		page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		page.buttonPressed("pin:" + pin.getSourcePath());
		((TextFieldAPI) getField(page, "name")).setText("Unsaved module name");
		CustomUIPanelPlugin header = plugins.values().stream()
				.filter(plugin -> plugin.getClass().getSimpleName().equals("HeaderPlugin")).findFirst().orElseThrow();
		Runnable toggle = (Runnable) getField(header, "onToggle");
		toggle.run();
		assertEquals("Unsaved module name", ((TextFieldAPI) getField(page, "name")).getText());
		assertTrue(page.hasUnsavedChanges());
		var router = (BattleNext_UiInputRouter) getField(page, "inputRouter");
		var overlay = new BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return position(500f, 500f);
			}
			@Override
			public boolean handleGlobalInput(List<InputEventAPI> events) {
				return true;
			}
			@Override
			public void close() {

			}
		};
		router.push(overlay);
		toggle.run();
		assertTrue(router.isTop(overlay));
		assertTrue(page.hasUnsavedChanges());
		router.pop(overlay);
	}

	@Test
	void previewTitleUsesHullNameAndEmptySectionsHaveCompleteMountedContents() throws Exception {
		JSONObject json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		for (String key : List.of("hullMods", "permaMods", "sMods", "sModdedBuiltIns",
				"suppressedMods", "weaponGroups", "wings")) {
			json.put(key, new JSONArray());
		}
		json.put("modules", new JSONObject());
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		details.setVariantJson(json);
		details.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		assertTrue(labels.contains("Parent"));
		assertEquals(4, labels.stream().filter("BattleNext_variant_none"::equals).count());
		for (String key : List.of("plugins", "modules", "groups", "wings")) {
			TooltipState state = tooltips.get(lists(details).get(key));
			assertEquals(state.getUsedHeight(), state.getMountedHeight());
			assertTrue(state.getMountedHeight() <= state.getViewport().getHeight());
			assertEquals(0f, state.getScroller().getYOffset());
			assertEquipmentSpacing(state);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {640, 1001, 1400})
	void everyEquipmentListOverflowsWithinSymmetricInsets(int width) throws Exception {

		JSONObject json = equipmentJson();
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		CustomPanelAPI owner = panel(width, 601f, new BaseCustomUIPanelPlugin());
		details.setVariantJson(json);
		details.attach(owner);
			CustomPanelAPI root = details.getPanel();
			List<PositionAPI> frames = children.get(root).stream().map(CustomPanelAPI.class::cast)
				.map(CustomPanelAPI::getPosition).toList();
		assertEquals(5, frames.size());
		assertEquals(8f, frames.get(1).getX() - frames.get(0).getX() - frames.get(0).getWidth());
		assertEquals(8f, frames.get(3).getX() - frames.get(1).getX() - frames.get(1).getWidth());
		assertEquals(owner.getPosition().getX() + width, frames.get(3).getX() + frames.get(3).getWidth());
		assertEquals(owner.getPosition().getY(), frames.get(2).getY());
		assertEquals(owner.getPosition().getY(), frames.get(4).getY());

		for (String key : List.of("plugins", "modules", "groups", "wings")) {
			TooltipState state = tooltips.get(lists(details).get(key));
			assertTrue(state.isScrollable());
			assertEquals(state.getUsedHeight(), state.getMountedHeight());
			assertTrue(state.getMountedHeight() > state.getViewport().getHeight(), key);
			state.getScroller().setYOffset(Float.MAX_VALUE);
			assertEquals(state.getMountedHeight() - state.getViewport().getHeight(), state.getScroller().getYOffset());
			state.getScroller().setYOffset(0f);
			assertEquipmentSpacing(state);
			if ("wings".equals(key)) {
				for (UIComponentAPI component : state.getComponents()) {
					if (!(component instanceof CustomPanelAPI row)) continue;
					List<Object> cards = children.get(row);
					for (int index = 1; index < cards.size(); index++) {
						PositionAPI left = ((CustomPanelAPI) cards.get(index - 1)).getPosition();
						PositionAPI right = ((CustomPanelAPI) cards.get(index)).getPosition();
						assertEquals(4f, right.getX() - left.getX() - left.getWidth(), 0.001f);
					}
				}
			}
			PositionAPI frame = geometry.get(state.getViewport()).getParent();
			for (UIComponentAPI component : state.getComponents()) {
				if (!(component instanceof CustomPanelAPI)) continue;
				PositionAPI row = component.getPosition();
				assertEquals(8f, row.getX() - frame.getX(), 0.001f);
				assertEquals(8f, frame.getX() + frame.getWidth() - row.getX() - row.getWidth(), 0.001f);
			}
		}
		TooltipState modules = tooltips.get(lists(details).get("modules"));
		assertTrue(modules.getComponents().stream().filter(CustomPanelAPI.class::isInstance)
				.anyMatch(card -> card.getPosition().getHeight() > 48f));
	}

	@Test
	void expansionUsesFixedWeaponRowsAndRetainsIndependentScrollOffsets() throws Exception {

		JSONObject json = equipmentJson();
		BattleNext_VariantDetails[] details = new BattleNext_VariantDetails[1];
		Runnable refresh = () -> { details[0].setVariantJson(json); details[0].attach(panel(640f, 601f, new BaseCustomUIPanelPlugin())); };
		details[0] = new BattleNext_VariantDetails(refresh);
		refresh.run();
		Map<String, Float> offsets = new LinkedHashMap<>();
		for (String key : List.of("plugins", "modules", "groups", "wings")) {
			TooltipMakerAPI list = lists(details[0]).get(key);
			list.getExternalScroller().setYOffset(80f);
			offsets.put(key, list.getExternalScroller().getYOffset());
		}
		float before = lists(details[0]).get("groups").getHeightSoFar();
		CustomUIPanelPlugin header = plugins.values().stream()
				.filter(plugin -> plugin.getClass().getSimpleName().equals("HeaderPlugin")).findFirst().orElseThrow();
		((Runnable) getField(header, "onToggle")).run();
		Map<String, TooltipMakerAPI> current = lists(details[0]);
		assertTrue(current.get("groups").getHeightSoFar() > before + 42f);
		assertTrue(labels.stream().anyMatch(value -> value.contains("Weapon weapon_")));
		TooltipState groups = tooltips.get(current.get("groups"));
		assertEquipmentSpacing(groups);
		CustomPanelAPI root = groups.getComponents().stream().filter(CustomPanelAPI.class::isInstance)
				.map(CustomPanelAPI.class::cast).filter(row -> children.get(row).size() == 2).findFirst().orElseThrow();
		CustomPanelAPI container = (CustomPanelAPI) children.get(root).get(1);
		CustomPanelAPI body = (CustomPanelAPI) children.get(container).get(0);
		TooltipState text = tooltips.get(children.get(body).get(0));
		assertEquals(188f, body.getPosition().getHeight());
		PositionAPI bodyBounds = body.getPosition();
		PositionAPI first = text.getComponents().get(0).getPosition();
		assertEquals(8f, bodyBounds.getY() + bodyBounds.getHeight() - first.getY() - first.getHeight());
		for (int index = 0; index < text.getComponents().size(); index++) {
			PositionAPI row = text.getComponents().get(index).getPosition();
			assertEquals(40f, row.getHeight());
			assertEquals(8f, row.getX() - bodyBounds.getX());
			assertEquals(8f, bodyBounds.getX() + bodyBounds.getWidth() - row.getX() - row.getWidth());
			if (index > 0) {
				PositionAPI previous = text.getComponents().get(index - 1).getPosition();
				assertEquals(4f, previous.getY() - row.getY() - row.getHeight());
			}
		}
		PositionAPI last = text.getComponents().get(text.getComponents().size() - 1).getPosition();
		assertEquals(8f, last.getY() - bodyBounds.getY());
		for (String key : offsets.keySet()) {
			assertEquals(offsets.get(key), current.get(key).getExternalScroller().getYOffset());
		}
		details[0].clear();
		refresh.run();
		assertEquals(before, lists(details[0]).get("groups").getHeightSoFar());
		for (String key : offsets.keySet()) {
			assertEquals(0f, lists(details[0]).get(key).getExternalScroller().getYOffset());
		}
	}

	@Test
	void mixedErrorsAndCardsShareTheSameSectionSpacing() throws Exception {

		JSONObject json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		json.put("hullMods", new JSONArray().put("aaa_missing_hullmod").put("ordinary").put("zzz_missing_hullmod"));
		json.put("wings", new JSONArray().put("aaa_missing_wing").put("wing").put("zzz_missing_wing"));
		JSONObject modules = json.getJSONObject("modules");
		JSONObject valid = new JSONObject(modules.getJSONObject("module").toString());
		modules.put("a_error", new JSONObject().put("hullId", "missing"));
		modules.put("b_valid", valid);
		modules.put("z_error", "missing_registered_variant");
		json.put("weaponGroups", new JSONArray().put("invalid")
				.put(new JSONObject().put("mode", "LINKED").put("weapons", new JSONObject()))
				.put("invalid"));
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		details.setVariantJson(json);
		details.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
		for (String key : List.of("plugins", "modules", "groups", "wings")) {
			assertEquipmentSpacing(tooltips.get(lists(details).get(key)));
		}
	}
}
