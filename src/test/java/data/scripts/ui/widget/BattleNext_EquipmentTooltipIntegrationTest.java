package data.scripts.ui.widget;

import data.scripts.ui.widget.BattleNext_ReportDetails;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.model.report.BattleNext_ReportTarget;
import data.model.report.BattleNext_BattleReportModel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.report.BattleNext_DamageSourceReport;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.page.BattleNext_BattleReportPage;
import data.scripts.ui.widget.provider.BattleNext_RefitDModSourceProvider;
import data.scripts.ui.widget.provider.BattleNext_RefitSModSourceProvider;
import data.scripts.ui.widget.provider.BattleNext_VariantSourceProvider;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

class BattleNext_EquipmentTooltipIntegrationTest extends BattleNext_TestVariantFixture {

	private static final String MODULE_PARAGRAPH = "Module background by 20%. " + "Complete first paragraph. ".repeat(50).strip();
	private static final String WING_PARAGRAPH = "Fighter background 30%.";
	private static final String WEAPON_PARAGRAPH = "Weapon background 40%.";
	private static final String MOD_PARAGRAPH = "Hullmod background 50%.";
	private final List<String> descriptionReads = new ArrayList<>();
	private final List<ShipAPI.HullSize> hullSizes = new ArrayList<>();
	private ShipVariantAPI current;
	private JSONObject snapshot;
	private boolean failDescriptions;

	@BeforeEach
	void configureEquipment() throws Exception {

		shortenLabels = true;
		paragraphLineHeight = 15f;
		var original = source();
		snapshot = BattleNext_VariantBuilder.fromVariant(original.getApi()).toJson();
		snapshot.put("hullMods", new JSONArray().put("ordinary")).put("permaMods", new JSONArray())
				.put("sMods", new JSONArray()).put("sModdedBuiltIns", new JSONArray()).put("suppressedMods", new JSONArray());
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getHullSpec")) {
				ShipHullSpecAPI hull = hulls.get(arguments[0]);
				return hull == null ? null : decorateHull(hull);
			}
			if (method.getName().equals("getHullModSpec")) return hullmod((String) arguments[0]);
			if (method.getName().equals("getWeaponSpec")) {
				return proxy(WeaponSpecAPI.class, (name, values) -> switch (name) {
					case "getWeaponName" -> "Ion Pulser";
					case "getTurretSpriteName" -> "graphics/ion.png";
					default -> null;
				});
			}
			if (method.getName().equals("getFighterWingSpec")) {
				var fighterHull = decorateHull(hull("fighter", "Broadsword", List.of()));
				var fighter = proxy(ShipVariantAPI.class, (name, values) -> switch (name) {
					case "getHullSpec" -> fighterHull;
					default -> null;
				});
				return proxy(FighterWingSpecAPI.class, (name, values) -> switch (name) {
					case "getWingName" -> "Broadsword Wing";
					case "getVariant" -> fighter;
					default -> null;
				});
			}
			if (method.getName().equals("getVariant")) return current;
			if (method.getName().equals("getDescription")) {
				String key = arguments[1] + "/" + arguments[0];
				descriptionReads.add(key);
				if (failDescriptions) throw new IllegalArgumentException("Missing description resource");
				String text = switch (key) {
					case "SHIP/child_text", "SHIP/parent_text" -> MODULE_PARAGRAPH;
					case "SHIP/fighter_text" -> WING_PARAGRAPH;
					case "WEAPON/laser" -> WEAPON_PARAGRAPH;
					default -> "";
				};
				if (text.isEmpty()) return null;
				var description = new Description(arguments[0].toString(), (Description.Type) arguments[1]);
				description.setText1(text + "\nSECOND PARAGRAPH");
				return description;
			}
			try {
				return method.invoke(previous, arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		current = (ShipVariantAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipVariantAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getHullSpec")) return decorateHull(original.getApi().getHullSpec());
			if (method.getName().equals("getHullSize")) return ShipAPI.HullSize.FRIGATE;
			if (method.getName().equals("getHullMods")) return List.of("ordinary");
			try {
				return method.invoke(original.getApi(), arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		});
	}

	@ParameterizedTest
	@ValueSource(strings = {"hullmod", "module", "wing", "weapon"})
	void nativeTooltipsShareOneIdentifierFontsColorsAndSpacing(String kind) throws Exception {

		var details = new BattleNext_VariantDetails(() -> {});
		((Set<Integer>) getField(details, "expanded")).add(0);
		details.setVariantJson(snapshot);
		details.attach(panel(1000f, 601f, new BaseCustomUIPanelPlugin()));
		var hover = tooltip(360f, 80f, false);
		if (kind.equals("hullmod")) {
			var card = tooltips.get(lists(details).get("plugins")).getComponents().get(0);
			assertEquals(360f, attachedTooltips.get(card).getTooltipWidth(null));
			attachedTooltips.get(card).createTooltip(hover, false, null);
		} else if (kind.equals("weapon")) {
			var root = (CustomPanelAPI) tooltips.get(lists(details).get("groups")).getComponents().get(0);
			var container = (CustomPanelAPI) children.get(root).get(1);
			var body = (CustomPanelAPI) children.get(container).get(0);
			var row = tooltips.get(children.get(body).get(0)).getComponents().get(0);
			attachedTooltips.get(row).createTooltip(hover, false, null);
		} else {
			var section = kind.equals("module") ? "modules" : "wings";
			var row = (CustomPanelAPI) tooltips.get(lists(details).get(section)).getComponents().get(0);
			var card = (CustomPanelAPI) children.get(row).get(0);
			var surface = tooltips.get(children.get(card).get(0)).getComponents().get(0);
			attachedTooltips.get(surface).createTooltip(hover, false, null);
		}
		String title = switch (kind) {
			case "hullmod" -> "Expanded Magazines";
			case "module" -> "Child";
			case "wing" -> "Broadsword Wing";
			default -> "Ion Pulser";
		};
		String id = switch (kind) {
			case "hullmod" -> "ordinary";
			case "module" -> "child";
			case "wing" -> "wing";
			default -> "laser";
		};
		String paragraph = switch (kind) {
			case "hullmod" -> MOD_PARAGRAPH;
			case "module" -> MODULE_PARAGRAPH;
			case "wing" -> WING_PARAGRAPH;
			default -> WEAPON_PARAGRAPH;
		};
		assertLayout(hover, kind.equals("hullmod")
				? List.of(title, id, paragraph + "\nSECOND PARAGRAPH", "S-mod effect 60%.", "Second effect paragraph")
				: List.of(title, id, paragraph));
		if (kind.equals("module")) {
			assertTrue(tooltips.get(hover).getUsedHeight() > 80f);
			assertEquals(List.of("SHIP/child_text"), descriptionReads);
		}
		if (kind.equals("wing")) assertEquals(List.of("SHIP/fighter_text"), descriptionReads);
		if (kind.equals("weapon")) assertEquals(List.of("WEAPON/laser"), descriptionReads);
		details.close();
	}

	@ParameterizedTest
	@ValueSource(strings = {"weapon", "fighter_wing", "ship"})
	void reportEquipmentSourcesUseTheSameDescriptionsAndRawIdentifiers(String kind) throws Exception {

		String id = kind.equals("ship") ? "child" : kind.equals("weapon") ? "laser" : "wing";
		var page = new BattleNext_ReportDetails(new BattleNext_UiInputRouter(), new BattleNext_ReportTarget(new BattleNext_BattleReportModel(), "", null, "", -1), () -> {});
		var body = panel(600f, 100f, new BaseCustomUIPanelPlugin());
		var list = body.createUIElement(600f, 100f, false);
		var method = page.getClass().getDeclaredMethod("addSourceRow", CustomPanelAPI.class,
				TooltipMakerAPI.class, BattleNext_DamageSourceReport.class, float.class);
		method.setAccessible(true);
		method.invoke(page, body, list, new BattleNext_DamageSourceReport(kind, id), 600f);
		var row = tooltips.get(list).getComponents().get(0);
		var hover = tooltip(360f, 80f, false);
		attachedTooltips.get(row).createTooltip(hover, false, null);
		String title = kind.equals("ship") ? "Child" : kind.equals("weapon") ? "Ion Pulser" : "Broadsword Wing";
		String text = kind.equals("ship") ? MODULE_PARAGRAPH : kind.equals("weapon") ? WEAPON_PARAGRAPH : WING_PARAGRAPH;
		assertLayout(hover, List.of(title, id, text));
	}

	@ParameterizedTest
	@ValueSource(strings = {"smod", "dmod"})
	void selectorsRetainNotesAndTheirFormalIdentifierWithCompleteText(String kind) {

		BattleNext_OptionPicker.OptionChoiceSource source = kind.equals("smod")
				? new BattleNext_RefitSModSourceProvider(() -> current)
				: new BattleNext_RefitDModSourceProvider(() -> current);
		var hover = tooltip(360f, 60f, false);
		String id = kind.equals("smod") ? "ordinary" : "damaged";
		source.createTooltip(hover, new BattleNext_OptionPicker.OptionChoice(id, "Choice"));
		assertEquals(360f, source.getTooltipWidth());
		var content = textValues(hover);
		assertEquals(id, content.get(1));
		assertEquals(MOD_PARAGRAPH + "\nSECOND PARAGRAPH", content.get(2));
		assertEquals(kind.equals("smod") ? 5 : 4, content.size());
		assertTrue(tooltips.get(hover).getUsedHeight() > 60f);
		for (int index = 2; index < content.size(); index++) {
			var position = tooltips.get(hover).getComponents().get(index).getPosition();
			assertEquals("default", labelFonts.get(position));
			assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(position));
		}
		if (kind.equals("smod")) {
			assertEquals(List.of("S-mod effect 60%.", "Second effect paragraph"), content.subList(3, 5));
		}
		if (kind.equals("dmod")) assertTrue(content.get(3).contains("d_mod_"));
		assertTrue(hullSizes.stream().allMatch(size -> size == ShipAPI.HullSize.FRIGATE));
	}

	@ParameterizedTest
	@ValueSource(strings = {"variant_50%", "registered_variant"})
	void variantSelectorRendersCurrentPropertiesWithSharedHeaderAndSectionLayout(String id) {

		var source = new BattleNext_VariantSourceProvider();
		var hover = tooltip(360f, 0f, false);
		source.createTooltip(hover, new BattleNext_OptionPicker.OptionChoice(id, "Choice"));
		assertEquals(360f, source.getTooltipWidth());
		List<String> values = textValues(hover);
		assertEquals(List.of("Choice", id), values.subList(0, 2));
		assertTrue(values.contains("BattleNext_variant_weapons: 1"));
		assertTrue(values.contains("Ion Pulser ×1"));
		assertTrue(values.contains("BattleNext_variant_fighter_wings: 1"));
		assertTrue(values.contains("Broadsword Wing ×1"));
		assertTrue(values.contains("BattleNext_variant_modules: 1"));
		assertTrue(values.contains("Child ×1"));
		assertEquals(List.of(), descriptionReads);
		var components = tooltips.get(hover).getComponents();
		for (int index = 0; index < values.size(); index++) {
			var position = components.get(index).getPosition();
			String text = values.get(index);
			boolean heading = Set.of("BattleNext_variant_weapons:", "BattleNext_variant_fighter_wings:",
					"BattleNext_variant_modules:", "BattleNext_variant_hullmods:").stream().anyMatch(text::startsWith);
			assertEquals(index == 0 ? "small-orbitron" : index == 1 ? "small-insignia" : "default",
					labelFonts.get(position));
			assertEquals(index == 0 || heading ? BattleNext_UiTheme.ACCENT : index == 1
					? BattleNext_UiTheme.MUTED : BattleNext_UiTheme.TEXT, labelColors.get(position));
			if (index == 0) continue;
			var previous = components.get(index - 1).getPosition();
			boolean sectionRow = index > 4 && !heading;
			assertEquals(index == 1 ? 5f : index == 4 || sectionRow ? 2f : 8f,
					previous.getY() - position.getY() - position.getHeight());
		}
		assertEquals(8f, components.get(components.size() - 1).getPosition().getHeight());
		assertTrue(tooltips.get(hover).getUsedHeight() > 100f);
	}

	@ParameterizedTest
	@ValueSource(strings = {"\n", "\r\n", "\r", "\u2028"})
	void firstParagraphNormalizesWhitespaceAndKeepsPercentCharacters(String separator) {
		String text = " \tFirst   paragraph 20% with %s. " + separator + "Second paragraph";
		assertEquals("First paragraph 20% with %s.", BattleNext_EquipmentTooltip.firstParagraph(text));
		assertEquals("", BattleNext_EquipmentTooltip.firstParagraph(null));
		assertEquals("", BattleNext_EquipmentTooltip.firstParagraph("  "));
	}

	@ParameterizedTest
	@ValueSource(strings = {"missing", "empty", "failure"})
	void absentOrFailedDescriptionsKeepHeaderAndNormalErrorNotes(String mode) {
		failDescriptions = mode.equals("failure");
		Description.Type type = Description.Type.WEAPON;
		String descriptionId = mode.equals("empty") ? "" : mode.equals("failure") ? "laser" : "missing";
		var hover = tooltip(360f, 100f, false);
		var data = BattleNext_EquipmentTooltip.description("Name", "id", descriptionId, type,
				Global.getSettings(), List.of());
		BattleNext_EquipmentTooltip.render(hover, data);
		var values = textValues(hover);
		assertEquals(List.of("Name", "id"), values.subList(0, 2));
		assertEquals(mode.equals("failure") ? 3 : 2, values.size());
		if (mode.equals("failure")) {
			assertTrue(values.get(2).contains("Missing description resource"));
			var position = tooltips.get(hover).getComponents().get(2).getPosition();
			assertEquals("default", labelFonts.get(position));
			assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(position));
		}
	}

	private void assertLayout(TooltipMakerAPI hover, List<String> expected) {

		TooltipState state = tooltips.get(hover);
		assertEquals(expected, textValues(hover));
		var components = state.getComponents();
		assertEquals(expected.size() + 1, components.size());
		for (int index = 0; index < expected.size(); index++) {
			var position = components.get(index).getPosition();
			assertEquals(index == 0 ? "small-orbitron" : index == 1 ? "small-insignia" : "default",
					labelFonts.get(position));
			assertEquals(index == 0 ? BattleNext_UiTheme.ACCENT : index == 1
					? BattleNext_UiTheme.MUTED : BattleNext_UiTheme.TEXT, labelColors.get(position));
			if (index > 0) {
				var previous = components.get(index - 1).getPosition();
				assertEquals(index == 1 ? 5f : 8f, previous.getY() - position.getY() - position.getHeight());
			}
		}
		assertEquals(8f, components.get(components.size() - 1).getPosition().getHeight());
	}

	private List<String> textValues(TooltipMakerAPI hover) {
		var result = new ArrayList<String>();
		for (var component : tooltips.get(hover).getComponents()) {
			if (component instanceof LabelAPI label) result.add(label.getText());
		}
		return result;
	}

	private HullModSpecAPI hullmod(String id) {
		return proxy(HullModSpecAPI.class, (method, arguments) -> switch (method) {
			case "getId" -> id;
			case "hasTag" -> id.equals("damaged") && arguments[0].equals("dmod");
			case "getDisplayName" -> "Expanded Magazines";
			case "getSpriteName" -> "graphics/mod.png";
			case "getDescriptionFormat" -> MOD_PARAGRAPH.replace("%", "%%") + "\nSECOND PARAGRAPH";
			case "getEffect" -> proxy(HullModEffect.class, (name, values) -> switch (name) {
				case "shouldAddDescriptionToTooltip" -> true;
				case "getDescriptionParam" -> {
					hullSizes.add((ShipAPI.HullSize) values[1]);
					yield null;
				}
				case "hasSModEffect" -> !id.equals("damaged");
				case "addSModSection" -> {
					TooltipMakerAPI tooltip = (TooltipMakerAPI) values[0];
					tooltip.addPara("S-mod effect 60%.", BattleNext_UiTheme.TEXT, 8f);
					tooltip.addPara("Second effect paragraph", BattleNext_UiTheme.TEXT, 8f);
					yield null;
				}
				default -> null;
			});
			case "getSModEffectFormat" -> "Effect";
			case "getSModDescription" -> "S-mod effect 60%.\nSecond effect paragraph";
			default -> null;
		});
	}

	private ShipHullSpecAPI decorateHull(ShipHullSpecAPI original) {
		return (ShipHullSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ShipHullSpecAPI.class},
				(instance, method, arguments) -> switch (method.getName()) {
					case "getDescriptionId" -> original.getHullId() + "_text";
					case "getHullSize" -> ShipAPI.HullSize.FRIGATE;
					case "getSuppliesToRecover" -> 5f;
					case "getHints" -> EnumSet.noneOf(ShipHullSpecAPI.ShipTypeHints.class);
					case "getDefenseType" -> ShieldAPI.ShieldType.OMNI;
					case "getNoCRLossTime" -> 1000f;
					default -> {
						try {
							yield method.invoke(original, arguments);
						} catch (InvocationTargetException exception) {
							throw exception.getCause();
						}
					}
				});
	}
}
