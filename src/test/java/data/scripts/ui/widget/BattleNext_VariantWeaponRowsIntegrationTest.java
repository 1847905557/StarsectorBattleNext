package data.scripts.ui.widget;

import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.page.BattleNext_BattleReportPage;
import java.util.ArrayList;
import java.util.Collections;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.scripts.ui.BattleNext_RefitPanelPlugin;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_VariantWeaponRowsIntegrationTest extends BattleNext_TestVariantFixture {

	private final ArrayList<String> imageAttempts = new ArrayList<>();

	@BeforeEach
	void configureWeaponAssets() {

		shortenLabels = true;
		paragraphLineHeight = 15f;
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getWeaponSpec")) {
				String id = (String) arguments[0];
				if (id.equals("unknown")) return null;
				return proxy(WeaponSpecAPI.class, (name, values) -> switch (name) {
					case "getWeaponName" -> "Weapon " + id + " with a deliberately long display name";
					case "getTurretSpriteName" -> id.equals("hardpoint") ? "" : "graphics/" + id + "_turret.png";
					case "getHardpointSpriteName" -> "graphics/" + id + "_hardpoint.png";
					default -> null;
				});
			}
			if (method.getName().equals("getSprite") && arguments.length == 1) {
				String path = (String) arguments[0];
				imageAttempts.add(path);
				if (path.contains("broken_turret") || path.contains("both_")) {
					throw new IllegalArgumentException("Missing image");
				}
			}
			try {
				return method.invoke(previous, arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	@ParameterizedTest
	@ValueSource(ints = {640, 1001, 1400})
	void weaponRowsHaveFixedGeometrySortedSlotsAndCompleteHoverText(int width) throws Exception {

		var details = details(width, List.of("turret", "hardpoint", "broken", "both", "unknown", ""));
		CustomPanelAPI body = groupBody(details);
		TooltipState state = tooltips.get(children.get(body).get(0));
		assertEquals(276f, body.getPosition().getHeight());
		assertEquals(6, state.getComponents().size());
		assertEquals(Set.of(0), getField(details, "expanded"));
		PositionAPI previous = null;
		for (int index = 0; index < state.getComponents().size(); index++) {
			CustomPanelAPI row = (CustomPanelAPI) state.getComponents().get(index);
			PositionAPI bounds = row.getPosition();
			assertEquals(40f, bounds.getHeight());
			assertEquals(8f, bounds.getX() - body.getPosition().getX());
			assertEquals(8f, body.getPosition().getX() + body.getPosition().getWidth()
					- bounds.getX() - bounds.getWidth());
			if (previous != null) assertEquals(4f, previous.getY() - bounds.getY() - bounds.getHeight());
			previous = bounds;
			TooltipState text = tooltips.get(children.get(row).get(children.get(row).size() - 1));
			LabelAPI label = (LabelAPI) text.getComponents().get(0);
			assertEquals(15f, label.getPosition().getHeight());
			assertEquals(bounds.getCenterY(), label.getPosition().getCenterY());
			assertEquals(bounds.getX() + 40f, label.getPosition().getX());
			int before = labels.size();
			TooltipMakerAPI hover = tooltip(600f, 300f, false);
			attachedTooltips.get(row).createTooltip(hover, false, null);
			String name = labels.get(before);
			String fullRowText = "slot_" + index + ": " + name;
			assertFalse(name.startsWith("slot_"));
			assertTrue(fullRowText.length() >= label.getText().length());
			if (fullRowText.length() * 7f > text.getViewport().getWidth() - 4f) {
				assertTrue(label.getText().endsWith("..."));
				assertTrue(fullRowText.length() > label.getText().length());
			} else {
				assertEquals(fullRowText, label.getText());
			}
			if (index < 5) assertEquals(List.of("turret", "hardpoint", "broken", "both", "unknown").get(index),
					labels.get(before + 1));
			if (index >= 3) {
				assertEquals(true, getField(plugins.get(row), "missingIcon"));
				spriteCenters.clear();
				plugins.get(row).render(1f);
				assertEquals(bounds.getX() + 16f, spriteCenters.get(0)[0]);
				assertEquals(bounds.getCenterY(), spriteCenters.get(0)[1]);
			} else {
				TooltipState icon = tooltips.get(children.get(row).get(0));
				var image = icon.getComponents().get(0);
				assertEquals(32f, image.getPosition().getWidth());
				assertEquals(32f, image.getPosition().getHeight());
				assertEquals(bounds.getCenterY(), image.getPosition().getCenterY());
				assertEquals(8f, label.getPosition().getX() - image.getPosition().getX() - 32f);
				assertEquals(index == 0 ? "graphics/turret_turret.png" : index == 1
						? "graphics/hardpoint_hardpoint.png" : "graphics/broken_hardpoint.png", imagePaths.get(image));
			}
		}
		assertTrue(imageAttempts.contains("graphics/broken_turret.png"));
		assertTrue(imageAttempts.contains("graphics/broken_hardpoint.png"));
		assertFalse(imageAttempts.contains("graphics/hardpoint_turret.png"));
		assertEquals(8f, body.getPosition().getY() + body.getPosition().getHeight()
				- state.getComponents().get(0).getPosition().getY() - 40f);
		assertEquals(8f, state.getComponents().get(5).getPosition().getY() - body.getPosition().getY());
		details.close();
		assertTrue(((List<?>) getField(details, "ownedPanels")).isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {"turret", "hardpoint", "broken", "both", "unknown"})
	void reportAndVariantSelectTheSameWorkingWeaponImage(String id) throws Exception {
		var details = details(1000, List.of(id));
		var variantRow = (CustomPanelAPI) tooltips.get(children.get(groupBody(details)).get(0)).getComponents().get(0);
		var report = new BattleNext_BattleReportPage(() -> {}, Runnable::run,
				(description, action) -> {});
		var body = panel(600f, 100f, new BaseCustomUIPanelPlugin());
		var list = body.createUIElement(600f, 100f, false);
		var method = report.getClass().getDeclaredMethod("addSourceRow", CustomPanelAPI.class,
				TooltipMakerAPI.class, BattleNext_DamageSourceReport.class, float.class);
		method.setAccessible(true);
		method.invoke(report, body, list, new BattleNext_DamageSourceReport("weapon", id), 600f);
		body.addUIElement(list).inTL(0f, 0f);
		var reportRow = (CustomPanelAPI) tooltips.get(list).getComponents().get(0);
		boolean missing = (Boolean) getField(plugins.get(variantRow), "missingIcon");
		assertEquals(missing, getField(plugins.get(reportRow), "missingIcon"));
		if (!missing) {
			var variantImage = tooltips.get(children.get(variantRow).get(0)).getComponents().get(0);
			var reportImage = tooltips.get(children.get(reportRow).get(0)).getComponents().get(0);
			assertEquals(imagePaths.get(variantImage), imagePaths.get(reportImage));
			assertEquals(32f, reportImage.getPosition().getHeight());
		}
		details.close();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 4})
	void expandedBodyHeightTracksFixedRowsAndEmptyGroupPrompt(int count) throws Exception {
		var ids = Collections.nCopies(count, "turret");
		var details = details(1000, ids);
		var body = groupBody(details);
		assertEquals(count == 0 ? 31f : 16f + count * 40f + (count - 1) * 4f,
				body.getPosition().getHeight());
		if (count == 0) assertTrue(labels.contains("BattleNext_variant_none"));
		details.close();
	}

	@Test
	void imageFailuresAndRepeatedRefreshKeepGroupStateAndScrollOffsets() throws Exception {
		var details = details(640, Collections.nCopies(12, "broken"));
		lists(details).get("groups").getExternalScroller().setYOffset(90f);
		for (int cycle = 0; cycle < 3; cycle++) {
			details.refreshContent();
			assertEquals(Set.of(0), getField(details, "expanded"));
			assertEquals(90f, lists(details).get("groups").getExternalScroller().getYOffset());
			var content = tooltips.get(children.get(groupBody(details)).get(0));
			assertEquals(12, content.getComponents().size());
			assertEquals(5, children.get(details.getPanel()).size());
		}
		((Set<Integer>) getField(details, "expanded")).clear();
		details.refreshContent();
		((Set<Integer>) getField(details, "expanded")).add(0);
		details.refreshContent();
		assertEquals(12, tooltips.get(children.get(groupBody(details)).get(0)).getComponents().size());
		details.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"catalog", "campaign", "refit"})
	void allVariantDetailEntrypointsDisplayTheSameWeaponRow(String entry) throws Exception {

		var variant = source();
		BattleNext_VariantDetails details;
		Runnable close;
		if (entry.equals("catalog")) {
			var store = new BattleNext_PinnedVariantStore();
			store.reload();
			var pin = store.saveVariant("Example", variant.getApi());
			var page = page(store, Runnable::run, (description, action) -> {}, () -> {});
			page.attach(panel(1000f, 700f, new BaseCustomUIPanelPlugin()));
			page.buttonPressed("pin:" + pin.getSourcePath());
			details = (BattleNext_VariantDetails) getField(page, "details");
			close = page::close;
		} else if (entry.equals("campaign")) {
			var store = new BattleNext_PinnedVariantStore();
			store.reload();
			var display = open(variant, store, 1000);
			details = (BattleNext_VariantDetails) getField(display, "details");
			close = () -> {
				try {
					var method = display.getClass().getDeclaredMethod("close");
					method.setAccessible(true);
					method.invoke(display);
				} catch (Exception exception) {
					throw new AssertionError(exception);
				}
			};
		} else {
			var plugin = new BattleNext_RefitPanelPlugin(() -> {});
			plugin.init(panel(1000f, 700f, plugin), 1000f, 700f);
			ShipVariantAPI current = (ShipVariantAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{ShipVariantAPI.class}, (instance, method, arguments) -> {
				if (method.getName().equals("clone")) return instance;
				if (method.getName().equals("getHullMods")) return List.of("ordinary", "sm", "built");
				try {
					return method.invoke(variant.getApi(), arguments);
				} catch (InvocationTargetException exception) {
					throw exception.getCause();
				}
			});
			FleetMemberAPI member = proxy(FleetMemberAPI.class, (method, arguments) ->
					method.equals("getVariant") ? current : null);
			plugin.open(member, new Refit(current));
			details = (BattleNext_VariantDetails) getField(plugin, "details");
			close = plugin::close;
		}
		((Set<Integer>) getField(details, "expanded")).add(0);
		details.refreshContent();
		var rows = tooltips.get(children.get(groupBody(details)).get(0)).getComponents();
		assertEquals(1, rows.size());
		var row = (CustomPanelAPI) rows.get(0);
		assertEquals(40f, row.getPosition().getHeight());
		var icon = tooltips.get(children.get(row).get(0)).getComponents().get(0);
		assertEquals("graphics/laser_turret.png", imagePaths.get(icon));
		for (String section : List.of("modules", "wings")) {
			var tileRow = tooltips.get(lists(details).get(section)).getComponents().stream()
					.filter(CustomPanelAPI.class::isInstance).map(CustomPanelAPI.class::cast).findFirst().orElseThrow();
			var tile = (CustomPanelAPI) children.get(tileRow).get(0);
			assertEquals(tile.getPosition().getWidth(), tile.getPosition().getHeight());
			assertEquals("SquareCardPlugin", plugins.get(tile).getClass().getSimpleName());
			var caption = (LabelAPI) tooltips.get(children.get(tile).get(0)).getComponents().get(1);
			assertEquals(section.equals("modules") ? "Child" : "Wing wing", caption.getText());
			assertEquals(Alignment.MID, labelAlignments.get(caption.getPosition()));
			var captionState = tooltips.get(children.get(tile).get(0));
			var hover = tooltip(360f, 300f, false);
			attachedTooltips.get(captionState.getComponents().get(0)).createTooltip(hover, false, null);
			var tooltipParts = tooltips.get(hover).getComponents();
			assertEquals(3, tooltipParts.size());
			var identifier = (LabelAPI) tooltipParts.get(1);
			assertEquals(section.equals("modules") ? "child" : "wing", identifier.getText());
			assertEquals(BattleNext_UiTheme.MUTED, labelColors.get(identifier.getPosition()));
		}
		JSONObject snapshot = (JSONObject) getField(details, "variantJson");
		for (String mode : List.of("LINKED", "ALTERNATING")) {
			snapshot.getJSONArray("weaponGroups").getJSONObject(0).put("mode", mode);
			details.refreshContent();
			var group = (CustomPanelAPI) tooltips.get(lists(details).get("groups")).getComponents().get(0);
			var top = (CustomPanelAPI) children.get(group).get(0);
			var sprite = (SpriteAPI) getField(plugins.get(top), "primarySprite");
			assertEquals(mode.equals("LINKED") ? "graphics/icons/jump-point.png" : "graphics/icons/jump-point2.png",
					spritePaths.get(sprite));
			assertEquals(BattleNext_UiTheme.ACCENT, getField(plugins.get(top), "primaryTint"));
			assertEquals(Set.of(0), getField(details, "expanded"));
		}
		details.close();
		close.run();
		assertNull(details.getPanel());
	}

	private BattleNext_VariantDetails details(int width, List<String> ids) throws Exception {
		var json = BattleNext_VariantBuilder.fromVariant(source().getApi()).toJson();
		var weapons = new JSONObject();
		for (int index = ids.size() - 1; index >= 0; index--) {
			weapons.put("slot_" + index, ids.get(index));
		}
		json.put("weaponGroups", new JSONArray().put(new JSONObject().put("mode", "LINKED")
				.put("autofire", true).put("weapons", weapons)));
		var details = new BattleNext_VariantDetails(() -> {});
		((Set<Integer>) getField(details, "expanded")).add(0);
		details.setVariantJson(json);
		details.attach(panel(width, 601f, new BaseCustomUIPanelPlugin()));
		return details;
	}

	private CustomPanelAPI groupBody(BattleNext_VariantDetails details) throws Exception {
		var root = (CustomPanelAPI) tooltips.get(lists(details).get("groups")).getComponents().get(0);
		var container = (CustomPanelAPI) children.get(root).get(1);
		return (CustomPanelAPI) children.get(container).get(0);
	}

	public static class Refit {
		private final ShipVariantAPI variant;

		public Refit(ShipVariantAPI variant) {
			this.variant = variant;
		}

		public Refit getShipDisplay() {
			return this;
		}

		public ShipVariantAPI getCurrentVariant() {
			return variant;
		}
	}
}
