package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.test.fixture.BattleNext_TestPageFixture;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

class BattleNext_BattleReportPageTest extends BattleNext_TestPageFixture {

	private boolean mixedLabelHeights;

	@Test
	void attachRefreshAndCloseOwnThePagePanel() throws Exception {
		BattleNext_BattleReportPage page = new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
		CustomPanelAPI root = panel(1400f, 900f, new BaseCustomUIPanelPlugin());

		page.attach(root);
		assertNotNull(getField(page, "panel"));
		assertEquals(1, children.get(root).size());
		page.refreshContent();
		assertEquals(1, children.get(root).size());
		page.close();
		assertEquals(0, children.get(root).size());
	}

	@ParameterizedTest
	@ValueSource(ints = {12, 15, 17, 20})
	void sourceTextAndImagesRemainCenteredAcrossFontHeightsAndScrolling(int lineHeight) throws Exception {

		paragraphLineHeight = lineHeight;
		configureRowAssets();
		CustomPanelAPI body = panel(1400f, 80f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = body.createUIElement(1300f, 80f, true);
		BattleNext_BattleReportPage page = new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
		for (String kind : List.of("weapon", "ship", "fighter_wing")) {
			appendSource(page, body, content, kind);
		}
		body.addUIElement(content).inTL(8f, 8f);
		List<CustomPanelAPI> rows = tooltips.get(content).getComponents().stream()
				.map(CustomPanelAPI.class::cast).toList();
		assertEquals(132f, content.getHeightSoFar());
		for (float offset : List.of(0f, 40f)) {
			content.getExternalScroller().setYOffset(offset);
			for (int index = 0; index < rows.size(); index++) {
				CustomPanelAPI row = rows.get(index);
				assertEquals(40f, row.getPosition().getHeight());
				assertTextCentered(row);
				TooltipState icon = tooltips.get(children.get(row).get(0));
				assertEquals(32f, icon.getViewport().getHeight());
				PositionAPI image = icon.getComponents().get(0).getPosition();
				assertEquals(32f, image.getHeight());
				assertEquals(row.getPosition().getCenterY(), image.getCenterY());
				assertEquals(4f, row.getPosition().getY() + 40f - image.getY() - image.getHeight());
				if (index > 0) {
					assertEquals(4f, rows.get(index - 1).getPosition().getY()
							- row.getPosition().getY() - row.getPosition().getHeight());
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"weapon", "ship", "fighter_wing", "arc", "source_type", "damage_type", "unknown"})
	void allSourceKindsUseTheSameRowAndTextCenter(String kind) throws Exception {
		configureRowAssets();
		paragraphLineHeight = 15f;
		mixedLabelHeights = true;
		CustomPanelAPI body = panel(1400f, 100f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = body.createUIElement(1300f, 100f, false);
		BattleNext_BattleReportPage page = new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
		appendSource(page, body, content, kind);
		body.addUIElement(content).inTL(8f, 8f);
		CustomPanelAPI row = (CustomPanelAPI) tooltips.get(content).getComponents().get(0);
		assertTextCentered(row);
		if (kind.equals("arc")) {
			plugins.get(row).render(1f);
			assertEquals(1, spriteCenters.size());
			assertEquals(row.getPosition().getCenterY(), spriteCenters.get(0)[1]);
		}
	}

	@Test
	void placeholderAndMissingImagePathsKeepTheirRowCenter() throws Exception {

		configureRowAssets();
		paragraphLineHeight = 15f;
		CustomPanelAPI body = panel(1400f, 100f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = body.createUIElement(1300f, 100f, false);
		BattleNext_BattleReportPage page = new BattleNext_BattleReportPage(() -> {}, Runnable::run, (description, action) -> {});
		failSprites = true;
		appendSource(page, body, content, "damage_type");
		appendSource(page, body, content, "weapon");
		body.addUIElement(content).inTL(8f, 8f);
		CustomPanelAPI placeholder = (CustomPanelAPI) tooltips.get(content).getComponents().get(0);
		TooltipState icon = tooltips.get(children.get(placeholder).get(0));
		PositionAPI letter = icon.getComponents().get(0).getPosition();
		assertEquals(32f, icon.getViewport().getHeight());
		assertEquals(placeholder.getPosition().getCenterY(), letter.getCenterY());
		CustomPanelAPI missing = (CustomPanelAPI) tooltips.get(content).getComponents().get(1);
		assertEquals(true, getField(plugins.get(missing), "missingIcon"));
		assertTextCentered(placeholder);
		assertTextCentered(missing);
	}

	@Override
	protected LabelAPI label(String text, float width) {
		LabelAPI label = super.label(text, width);
		if (mixedLabelHeights && text.startsWith("BattleNext_report_armor:")) {
			label.getPosition().setSize(width, 19f);
		}
		return label;
	}

	private void assertTextCentered(CustomPanelAPI row) {

		TooltipMakerAPI labels = (TooltipMakerAPI) children.get(row).get(children.get(row).size() - 1);
		var components = tooltips.get(labels).getComponents();
		assertEquals(5, components.size());
		PositionAPI name = components.get(0).getPosition();
		float bottom = Float.POSITIVE_INFINITY;
		float damageTop = components.get(1).getPosition().getY() + components.get(1).getPosition().getHeight();
		for (int index = 1; index < components.size(); index++) {
			PositionAPI metric = components.get(index).getPosition();
			assertEquals(damageTop, metric.getY() + metric.getHeight());
			assertTrue(metric.getY() >= row.getPosition().getY());
			bottom = Math.min(bottom, metric.getY());
		}
		float top = name.getY() + name.getHeight();
		assertEquals(row.getPosition().getCenterY(), (top + bottom) * 0.5f);
		assertEquals(row.getPosition().getY() + row.getPosition().getHeight() - top,
				bottom - row.getPosition().getY());
		assertTrue(name.getY() >= damageTop);
		assertTrue(top <= row.getPosition().getY() + row.getPosition().getHeight());
	}

	private void appendSource(BattleNext_BattleReportPage page, CustomPanelAPI body,
			TooltipMakerAPI content, String kind) throws Exception {
		Method method = BattleNext_BattleReportPage.class.getDeclaredMethod("addSourceRow",
				CustomPanelAPI.class, TooltipMakerAPI.class, BattleNext_DamageSourceReport.class, float.class);
		method.setAccessible(true);
		BattleNext_DamageSourceReport source = new BattleNext_DamageSourceReport(kind, kind.equals("damage_type") ? "ENERGY" : "source");
		source.add(490.4, 76.5, 12.3, 0d);
		method.invoke(page, body, content, source, 1300f);
	}

	private void configureRowAssets() {

		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getHullName" -> "Wolf";
			case "getSpriteName" -> "graphics/wolf.png";
			default -> null;
		});
		ShipVariantAPI variant = proxy(ShipVariantAPI.class, (method, args) -> method.equals("getHullSpec") ? hull : null);
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			switch (method.getName()) {
				case "getWeaponSpec":
					return proxy(WeaponSpecAPI.class, (name, values) -> switch (name) {
						case "getWeaponName" -> "Sabot SRM";
						case "getTurretSpriteName" -> "graphics/sabot.png";
						default -> null;
					});
				case "getFighterWingSpec":
					return proxy(FighterWingSpecAPI.class, (name, values) -> switch (name) {
						case "getWingName" -> "Talon";
						case "getVariant" -> variant;
						default -> null;
					});
				case "getHullSpec":
					return hull;
				case "getSpriteName":
					return "graphics/icon.png";
				default:
					try {
						return method.invoke(previous, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
			}
		}));
	}
}
