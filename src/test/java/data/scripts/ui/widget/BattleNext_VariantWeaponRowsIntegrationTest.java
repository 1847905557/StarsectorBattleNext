package data.scripts.ui.widget;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.*;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_ReportTarget;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
	@ValueSource(ints = {640, 1001})
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
	@ValueSource(strings = {"broken", "unknown"})
	void reportAndVariantSelectTheSameWorkingWeaponImage(String id) throws Exception {
		var details = details(1000, List.of(id));
		var variantRow = (CustomPanelAPI) tooltips.get(children.get(groupBody(details)).get(0)).getComponents().get(0);
		var model = new BattleNext_BattleReportModel();
		var member = new BattleNext_MemberReport("parent_variant", "parent", "Parent");
		member.addDamageDealt("weapon", id, 1d, 2d, 3d, 4d);
		model.addPlayerMember(member);
		AtomicReference<BattleNext_ReportDetails> owner = new AtomicReference<>();
		var report = new BattleNext_ReportDetails(new BattleNext_UiInputRouter(), new BattleNext_ReportTarget(model),
				() -> owner.get().refreshContent());
		owner.set(report);
		var body = panel(600f, 400f, new BaseCustomUIPanelPlugin());
		report.attach(body);
		report.buttonPressed("member:0");
		var reportRow = plugins.keySet().stream().filter(panel -> plugins.get(panel).getClass().getSimpleName().equals("SourceRowPlugin"))
				.findFirst().orElseThrow();
		boolean missing = id.equals("unknown");
		assertEquals(missing, getField(plugins.get(variantRow), "missingIcon"));
		assertEquals(missing, getField(plugins.get(reportRow), "missingIcon"));
		if (!missing) {
			var variantImage = tooltips.get(children.get(variantRow).get(0)).getComponents().get(0);
			var reportImage = tooltips.get(children.get(reportRow).get(0)).getComponents().get(0);
			assertEquals("graphics/broken_hardpoint.png", imagePaths.get(variantImage));
			assertEquals("graphics/broken_hardpoint.png", imagePaths.get(reportImage));
			assertEquals(32f, reportImage.getPosition().getHeight());
		}
		details.close();
		report.close();
		assertNull(report.getPanel());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 4})
	void expandedBodyHeightTracksFixedRowsAndEmptyGroupPrompt(int count) throws Exception {
		var ids = Collections.nCopies(count, "turret");
		var details = details(1000, ids);
		var body = groupBody(details);
		assertEquals(count == 0 ? 31f : 16f + count * 40f + (count - 1) * 4f,
				body.getPosition().getHeight());
		if (count == 0) assertTrue(labels.contains(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_none")));
		details.close();
	}

	@Test
	void imageFailuresAndRepeatedRefreshKeepGroupStateAndScrollOffsets() throws Exception {
		List<String> ids = new ArrayList<>(Collections.nCopies(12, "turret"));
		ids.set(0, "broken");
		var details = details(640, ids);
		int frameCount = children.get(details.getPanel()).size();
		lists(details).get("groups").getExternalScroller().setYOffset(90f);
		for (int cycle = 0; cycle < 3; cycle++) {
			details.refreshContent();
			assertEquals(Set.of(0), getField(details, "expanded"));
			assertEquals(90f, lists(details).get("groups").getExternalScroller().getYOffset());
			var content = tooltips.get(children.get(groupBody(details)).get(0));
			assertEquals(12, content.getComponents().size());
			assertEquals(frameCount, children.get(details.getPanel()).size());
		}
		((Set<Integer>) getField(details, "expanded")).clear();
		details.refreshContent();
		((Set<Integer>) getField(details, "expanded")).add(0);
		details.refreshContent();
		assertEquals(12, tooltips.get(children.get(groupBody(details)).get(0)).getComponents().size());
		details.close();
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
