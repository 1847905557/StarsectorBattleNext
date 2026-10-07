package data.scripts.ui.page;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.report.BattleNext_ReportTarget;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.scripts.report.BattleNext_MemberReport;
import data.scripts.tools.BattleNext_Settings;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.widget.BattleNext_ReportDetails;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_BattleReportPageTest extends BattleNext_TestPageFixture {

	private boolean mixedLabelHeights;

	@Test
	void persistedLocalVariantReportsResolveHullNamesAndThumbnailsInBothDamageSections() throws Exception {

		hull("wolf", "Wolf", List.of());
		hull("lasher", "Lasher", List.of());
		List<String> requestedSprites = new ArrayList<>();
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getVariant")) throw new AssertionError("Report thumbnails must resolve the recorded hull identity");
			if (method.getName().equals("getSprite") && args.length == 1) {
				requestedSprites.add((String) args[0]);
			}
			try {
				return method.invoke(settings, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("local_report");
		report.setName("Local fleet report");
		BattleNext_MemberReport player = new BattleNext_MemberReport("local_wolf_CS", "wolf", "Named Wolf");
		BattleNext_MemberReport enemy = new BattleNext_MemberReport("pinned:custom_lasher", "lasher", "Named Lasher");
		player.addDamageDealt("weapon", "gun", 200d, 0d, 0d, 0d);
		enemy.addDamageTaken("weapon", "gun", 200d, 0d, 0d, 0d);
		report.addPlayerMember(player);
		report.addEnemyMember(enemy);
		BattleNext_BattleReportStore store = new BattleNext_BattleReportStore();
		store.reload();
		store.save(report);
		store.reload();
		BattleNext_ReportDetails details = new BattleNext_ReportDetails(new BattleNext_UiInputRouter(),
				new BattleNext_ReportTarget(store.get("local_report")), () -> {});
		try {
			details.attach(panel(1400f, 500f, new BaseCustomUIPanelPlugin()));
			List<CustomPanelAPI> rows = reportRows(details);
			List<String> names = List.of("Wolf", "Lasher", "Lasher", "Wolf");
			assertEquals(names.size(), rows.size());
			for (int index = 0; index < rows.size(); index++) {
				CustomPanelAPI top = (CustomPanelAPI) children.get(rows.get(index)).get(0);
				List<UIComponentAPI> labels = tooltips.get(children.get(top).get(0)).getComponents();
				assertEquals(names.get(index), ((LabelAPI) labels.get(1)).getText());
				assertNotNull(getField(top.getPlugin(), "primarySprite"));
				top.getPlugin().render(1f);
			}
			assertEquals(List.of("graphics/wolf.png", "graphics/lasher.png", "graphics/lasher.png", "graphics/wolf.png"), requestedSprites);
			assertEquals(rows.size(), spriteCenters.size());
		} finally {
			details.close();
			Global.setSettings(settings);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void reportSidesKeepTheirColorsAndMembershipAcrossSortingExpansionAndLanguageRefresh(
			boolean cnText) throws Exception {

		configureRowAssets();
		useReportTextAndSideColors();
		BattleNext_Settings.save(cnText, true, false, false, 500f, 1f, 3f, 50);
		String name = "Player Enemy 我方 敌方 50%";
		BattleNext_MemberReport firstPlayer = reportMember(name, 100d, 300d, 0.75f, 0.7f, "alive");
		BattleNext_MemberReport secondPlayer = reportMember(name, 200d, 100d, 0.25f, 0.6f, "retreated");
		BattleNext_MemberReport enemy = reportMember(name, 300d, 200d, 0f, 0.5f, "destroyed");
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.addPlayerMember(firstPlayer);
		report.addPlayerMember(secondPlayer);
		report.addEnemyMember(enemy);
		String saved = report.toJson().toString();
		BattleNext_ReportTarget target = new BattleNext_ReportTarget(report);
		AtomicReference<BattleNext_ReportDetails> owner = new AtomicReference<>();
		BattleNext_ReportDetails details = new BattleNext_ReportDetails(new BattleNext_UiInputRouter(), target,
				() -> owner.get().refreshContent());
		owner.set(details);
		details.attach(panel(1400f, 160f, new BaseCustomUIPanelPlugin()));
		List<BattleNext_MemberReport> ordered = List.of(enemy, secondPlayer, firstPlayer, firstPlayer, enemy, secondPlayer);
		List<Boolean> playerSides = List.of(false, true, true, true, false, true);
		assertReportHeaders(details, ordered, playerSides, cnText);
		details.getContent().getExternalScroller().setYOffset(40f);
		details.buttonPressed("member:0");
		assertEquals(2, children.get(reportRows(details).get(0)).size());
		assertEquals(1, children.get(reportRows(details).get(4)).size());
		for (boolean language : List.of(!cnText, cnText)) {
			BattleNext_Settings.save(language, true, false, false, 500f, 1f, 3f, 50);
			details.refreshContent();
			assertReportHeaders(details, ordered, playerSides, language);
			assertEquals(2, children.get(reportRows(details).get(0)).size());
			assertEquals(40f, details.getContent().getExternalScroller().getYOffset());
		}
		details.buttonPressed("member:0");
		assertEquals(1, children.get(reportRows(details).get(0)).size());
		assertReportHeaders(details, ordered, playerSides, cnText);
		assertEquals(saved, report.toJson().toString());
		details.close();
	}

	private BattleNext_MemberReport reportMember(String name, double dealt, double taken,
			float hull, float cr, String status) {
		BattleNext_MemberReport member = new BattleNext_MemberReport("wolf_CS", "wolf", name);
		member.addDamageDealt("ship", "wolf", dealt, 0d, 0d, 20d);
		member.addDamageTaken("ship", "wolf", taken, 0d, 0d, 10d);
		member.setFinalState(hull, cr, status);
		return member;
	}

	private List<CustomPanelAPI> reportRows(BattleNext_ReportDetails details) {
		return tooltips.get(details.getContent()).getComponents().stream()
				.filter(CustomPanelAPI.class::isInstance).map(CustomPanelAPI.class::cast).toList();
	}

	private void assertReportHeaders(BattleNext_ReportDetails details, List<BattleNext_MemberReport> members,
			List<Boolean> playerSides, boolean cnText) {

		List<CustomPanelAPI> rows = reportRows(details);
		assertEquals(members.size(), rows.size());
		String category = "Report";
		for (int index = 0; index < rows.size(); index++) {
			BattleNext_MemberReport member = members.get(index);
			boolean playerSide = playerSides.get(index);
			String side = BattleNext_TestTextCatalog.text(category, "BattleNext_report_side_" + (playerSide ? "player" : "enemy"));
			CustomPanelAPI top = (CustomPanelAPI) children.get(rows.get(index)).get(0);
			List<UIComponentAPI> names = tooltips.get(children.get(top).get(0)).getComponents();
			LabelAPI firstLine = (LabelAPI) names.get(0);
			assertEquals(member.getName() + " - " + side, firstLine.getText());
			assertEquals("Wolf", ((LabelAPI) names.get(1)).getText());
			assertEquals(List.of(side), labelHighlights.get(firstLine.getPosition()));
			assertEquals(List.of(firstLine.getText().length() - side.length(), firstLine.getText().length()),
					labelHighlightRanges.get(firstLine.getPosition()));
			assertEquals(List.of(playerSide ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor()),
					labelHighlightColors.get(firstLine.getPosition()));
			assertEquals(Color.WHITE, labelColors.get(firstLine.getPosition()));
			List<UIComponentAPI> metrics = tooltips.get(children.get(top).get(1)).getComponents();
			String damage = BattleNext_TestTextCatalog.text(category, "BattleNext_report_damage_" + (index < members.size() / 2 ? "dealt" : "taken"));
			double total = index < members.size() / 2 ? member.getDamageDealt() : member.getDamageTaken();
			assertEquals(damage + ": " + String.format(Locale.ROOT, "%,.1f", total) + "  "
					+ BattleNext_TestTextCatalog.text(category, "BattleNext_report_status_" + member.getStatus()),
					((LabelAPI) metrics.get(0)).getText());
			assertEquals(BattleNext_TestTextCatalog.text(category, "BattleNext_report_final_hull") + ": "
					+ String.format(Locale.ROOT, "%.1f%%", member.getFinalHullFraction() * 100f) + "  "
					+ BattleNext_TestTextCatalog.text(category, "BattleNext_report_final_cr") + ": "
					+ String.format(Locale.ROOT, "%.1f%%", member.getFinalCr() * 100f),
					((LabelAPI) metrics.get(1)).getText());
		}
	}

	private void useReportTextAndSideColors() {
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getString")) return BattleNext_TestTextCatalog.lookup(args[0], args[1]);
			if (method.getName().equals("getColor") && args[0].equals("textFriendColor")) return Color.GREEN;
			if (method.getName().equals("getColor") && args[0].equals("textEnemyColor")) return Color.RED;
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
	}

	@ParameterizedTest
	@ValueSource(ints = {12, 15, 17, 20})
	void sourceTextAndImagesRemainCenteredAcrossFontHeightsAndScrolling(int lineHeight) throws Exception {

		paragraphLineHeight = lineHeight;
		configureRowAssets();
		CustomPanelAPI body = panel(1400f, 80f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI content = body.createUIElement(1300f, 80f, true);
		BattleNext_ReportDetails details = new BattleNext_ReportDetails(new BattleNext_UiInputRouter(), new BattleNext_ReportTarget(new BattleNext_BattleReportModel()), () -> {});
		for (String kind : List.of("weapon", "ship", "fighter_wing")) {
			appendSource(details, body, content, kind);
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
		BattleNext_ReportDetails details = new BattleNext_ReportDetails(new BattleNext_UiInputRouter(), new BattleNext_ReportTarget(new BattleNext_BattleReportModel()), () -> {});
		appendSource(details, body, content, kind);
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
		BattleNext_ReportDetails details = new BattleNext_ReportDetails(new BattleNext_UiInputRouter(), new BattleNext_ReportTarget(new BattleNext_BattleReportModel()), () -> {});
		failSprites = true;
		appendSource(details, body, content, "damage_type");
		appendSource(details, body, content, "weapon");
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
		if (mixedLabelHeights && text.startsWith(BattleNext_TestTextCatalog.text("Report", "BattleNext_report_armor") + ":")) {
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

	private void appendSource(BattleNext_ReportDetails details, CustomPanelAPI body,
			TooltipMakerAPI content, String kind) throws Exception {
		Method method = BattleNext_ReportDetails.class.getDeclaredMethod("addSourceRow",
				CustomPanelAPI.class, TooltipMakerAPI.class, BattleNext_DamageSourceReport.class, float.class);
		method.setAccessible(true);
		BattleNext_DamageSourceReport source = new BattleNext_DamageSourceReport(kind, kind.equals("damage_type") ? "ENERGY" : "source");
		source.add(490.4, 76.5, 12.3, 0d);
		method.invoke(details, body, content, source, 1300f);
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
				case "getVariant":
					return variant;
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
