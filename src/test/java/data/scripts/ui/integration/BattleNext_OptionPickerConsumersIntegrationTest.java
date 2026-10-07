package data.scripts.ui.integration;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.ui.BattleNext_RefitPanelPlugin;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.editor.BattleNext_RuleEditor;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentTemplate;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_OptionPickerConsumersIntegrationTest extends BattleNext_TestVariantFixture {

	private static final String PARAGRAPH = "Complete description 20%. " + "Full first paragraph. ".repeat(30).strip();
	private Object previousRestrictions;
	private Object previousScriptFactory;
	private Object previousLoadErrors;
	private ShipVariantAPI variant;
	private BattleNext_RefitPanelPlugin refit;
	private BattleNext_FleetEditor fleetEditor;
	private BattleNext_TournamentTemplate template;
	private final List<Runnable> ownerClosers = new ArrayList<>();

	@BeforeEach
	void installChoiceData() throws Exception {

		var registry = BattleNext_RestrictionRegistry.getInstance();
		previousRestrictions = getField(registry, "entries");
		previousScriptFactory = getField(registry, "scriptFactory");
		previousLoadErrors = getField(registry, "loadErrors");
		registry.load(new JSONArray().put(new JSONObject().put("id", "limit").put("type", "number")
				.put("script", "restriction").put("desc", "Fleet limit 20%")),
				name -> (BattleNext_RestrictionScript) (fleet, restriction) -> List.of());
		var original = source().getApi();
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, arguments) -> switch (method) {
			case "getHullId" -> "parent";
			case "getHullName" -> "Parent";
			case "getSpriteName" -> "graphics/parent.png";
			case "getDescriptionId" -> "parent_text";
			case "getHullSize" -> ShipAPI.HullSize.FRIGATE;
			case "getSuppliesToRecover" -> 5f;
			case "getHints" -> EnumSet.noneOf(ShipHullSpecAPI.ShipTypeHints.class);
			case "getDefenseType" -> ShieldAPI.ShieldType.OMNI;
			case "getNoCRLossTime" -> 1000f;
			case "getBuiltInMods", "getBuiltInWings" -> List.of();
			default -> null;
		});
		variant = (ShipVariantAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipVariantAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("clone")) return instance;
			if (method.getName().equals("getHullSpec")) return hull;
			if (method.getName().equals("getHullSize")) return ShipAPI.HullSize.FRIGATE;
			if (method.getName().equals("getHullMods")) return List.of("ordinary");
			if (method.getName().equals("isFighter")) return false;
			try {
				return method.invoke(original, arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		});
		SkillSpecAPI skill = proxy(SkillSpecAPI.class, (method, arguments) -> switch (method) {
			case "getName" -> "Sensors";
			case "getSpriteName" -> "graphics/skill.png";
			case "getDescription" -> PARAGRAPH + "\nSecond paragraph";
			case "getScopeStr" -> "Ship";
			case "getScopeStr2" -> "Fleet";
			case "isCombatOfficerSkill" -> true;
			case "getEffectsAPI" -> List.of();
			default -> null;
		});
		FactionSpecAPI faction = proxy(FactionSpecAPI.class, (method, arguments) -> switch (method) {
			case "getId" -> "hegemony";
			case "getDisplayName" -> "Hegemony";
			case "getAllPortraits" -> List.of("graphics/portraits/hegemony.png");
			default -> null;
		});
		FactionSpecAPI player = proxy(FactionSpecAPI.class, (method, arguments) ->
				method.equals("getAllPortraits") ? List.of() : null);
		SettingsAPI settings = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			return switch (method.getName()) {
				case "getSortedSkillIds" -> List.of("sensors");
				case "getSkillSpec" -> skill;
				case "getAllVariantIds" -> List.of("parent_variant");
				case "getVariant" -> variant;
				case "getAllFactionSpecs" -> new ArrayList<>(List.of(faction, player));
				case "getFactionSpec" -> player;
				case "getAllHullModSpecs" -> List.of(hullmod("damaged"));
				case "getHullModSpec" -> hullmod((String) arguments[0]);
				case "getMergedSpreadsheetData" -> new JSONArray().put(new JSONObject()
						.put("id", "steady").put("name", "Steady").put("desc", PARAGRAPH + "\r\nSecond paragraph"));
				case "getDescription" -> {
					Description description = new Description((String) arguments[0], (Description.Type) arguments[1]);
					description.setText1(PARAGRAPH + "\nSecond paragraph");
					yield description;
				}
				default -> {
					try {
						yield method.invoke(settings, arguments);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				}
			};
		}));
	}

	@AfterEach
	void restoreChoiceRegistry() throws Exception {
		try {
			for (int index = ownerClosers.size() - 1; index >= 0; index--) {
				ownerClosers.get(index).run();
			}
		} finally {
			ownerClosers.clear();
			refit = null;
			fleetEditor = null;
			BattleNext_UiInputRouter.activate(null);
			var registry = BattleNext_RestrictionRegistry.getInstance();
			setField(registry, "entries", previousRestrictions);
			setField(registry, "scriptFactory", previousScriptFactory);
			setField(registry, "loadErrors", previousLoadErrors);
		}
	}

	@Test
	void allNineConsumersUseTheirNativeWidthIdentityAndCompleteText() throws Exception {
		CustomPanelAPI root = panel(1000f, 900f, new BaseCustomUIPanelPlugin());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		List<List<String>> consumers = List.of(
				List.of("commander", "520", "Sensors", "sensors"),
				List.of("officer", "520", "Sensors", "sensors"),
				List.of("variant", "360", "Parent - Unsubmitted", "parent_variant"),
				List.of("personality", "360", "Steady", "steady"),
				List.of("portrait", "520", "Hegemony", "graphics/portraits/hegemony.png"),
				List.of("tournament", "360", "Fleet name", "fleet_id"),
				List.of("smod", "360", "Hullmod ordinary", "ordinary"),
				List.of("dmod", "360", "Hullmod damaged", "damaged"),
				List.of("restriction", "360", "Fleet limit 20%", "limit"));
		for (List<String> consumer : consumers) {
			assertConsumer(root, router, consumer.get(0), Integer.parseInt(consumer.get(1)), consumer.get(2), consumer.get(3));
		}
	}

	private void assertConsumer(CustomPanelAPI root, BattleNext_UiInputRouter router, String kind, int width,
			String title, String id) throws Exception {

		BattleNext_OptionPicker picker = picker(kind, root, router);
		router = (BattleNext_UiInputRouter) getField(picker, "inputRouter");
		int registrations = ((Map<?, ?>) getField(router.getSession(), "registrations")).size()
				- (kind.equals("tournament") ? 1 : 0);
		var open = picker.getClass().getDeclaredMethod("open");
		open.setAccessible(true);
		if (!kind.equals("tournament")) {
			open.invoke(picker);
		}
		CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
		TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
		assertEquals(1, tooltips.get(grid).getComponents().size());
		UIComponentAPI card = tooltips.get(grid).getComponents().get(0);
		var creator = attachedTooltips.get(card);
		assertEquals(width, creator.getTooltipWidth(null));
		assertSame(tooltips.get(grid), tooltipBindings.get(card).owner());
		assertEquals(TooltipMakerAPI.TooltipLocation.RIGHT, tooltipBindings.get(card).location());
		assertFalse(tooltipBindings.get(card).recreateEveryFrame());
		TooltipMakerAPI content = tooltip(width, 0f, false);
		creator.createTooltip(content, false, null);
		List<UIComponentAPI> components = tooltips.get(content).getComponents();
		assertEquals(title, ((LabelAPI) components.get(0)).getText());
		assertEquals(id, ((LabelAPI) components.get(1)).getText());
		assertEquals("small-insignia", labelFonts.get(components.get(1).getPosition()));
		assertEquals(BattleNext_UiTheme.MUTED, labelColors.get(components.get(1).getPosition()));
		if (kind.equals("variant")) {
			assertTrue(((LabelAPI) components.get(2)).getText().contains(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_base_deployment_points")));
			assertEquals(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_flux_capacitors") + ": 0", ((LabelAPI) components.get(3)).getText());
			assertEquals(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_flux_vents") + ": 0", ((LabelAPI) components.get(4)).getText());
			List<String> values = components.stream().filter(LabelAPI.class::isInstance)
					.map(LabelAPI.class::cast).map(LabelAPI::getText).toList();
			assertTrue(values.contains(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_weapons") + ": 1"));
			assertTrue(values.contains("Weapon laser ×1"));
			assertTrue(values.contains(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_fighter_wings") + ": 1"));
			assertTrue(values.contains(BattleNext_TestTextCatalog.text("Variant", "BattleNext_variant_modules") + ": 1"));
			assertEquals("default", labelFonts.get(components.get(2).getPosition()));
			assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(components.get(2).getPosition()));
		} else if (List.of("smod", "dmod").contains(kind)) {
			assertEquals(PARAGRAPH + "\nSecond paragraph", ((LabelAPI) components.get(2)).getText());
			assertEquals("default", labelFonts.get(components.get(2).getPosition()));
			assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(components.get(2).getPosition()));
		} else if (kind.equals("tournament")) {
			assertEquals(6, components.size());
			assertEquals("small-orbitron", labelFonts.get(components.get(0).getPosition()));
			assertEquals(BattleNext_UiTheme.ACCENT, labelColors.get(components.get(0).getPosition()));
			var titlePosition = components.get(0).getPosition();
			var idPosition = components.get(1).getPosition();
			assertEquals(5f, titlePosition.getY() - idPosition.getY() - idPosition.getHeight());
			assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_ships") + ": 0 - " + BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_fighter_wings") + ": 0", ((LabelAPI) components.get(2)).getText());
			assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_base_dp") + ": 0 - " + BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_base_fp") + ": 0", ((LabelAPI) components.get(3)).getText());
			assertEquals(8f, components.get(5).getPosition().getHeight());
		} else if (kind.equals("restriction")) {
			assertEquals(2, components.size());
		} else {
			assertEquals(PARAGRAPH, ((LabelAPI) components.get(2)).getText());
			assertEquals("default", labelFonts.get(components.get(2).getPosition()));
			assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(components.get(2).getPosition()));
		}
		if (List.of("commander", "officer").contains(kind)) {
			assertEquals("Ship - Fleet", ((LabelAPI) components.get(3)).getText());
		}
		if (kind.equals("smod")) assertEquals("S-mod effect 50%.", ((LabelAPI) components.get(3)).getText());
		if (kind.equals("dmod")) {
			assertEquals(BattleNext_TestTextCatalog.text("Refit", "BattleNext_refit_d_mod_suitable"), ((LabelAPI) components.get(3)).getText());
		}
		if (List.of("commander", "officer", "variant", "personality", "portrait").contains(kind)) {
			Map<TextFieldAPI, String> siblings = new LinkedHashMap<>();
			BattleNext_OptionField selected = null;
			for (BattleNext_OptionField field : optionFields(fleetEditor)) {
				if (getField(field, "picker") == picker) {
					selected = field;
				} else {
					siblings.put(field.getField(), field.getField().getText());
				}
			}
			Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
			selection.clear();
			selection.put(id, 1);
			CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
			plugins.get(popup).buttonPressed(buttonIds.get("CONFIRM"));
			assertEquals(id, selected.getField().getText());
			for (var sibling : siblings.entrySet()) {
				assertEquals(sibling.getValue(), sibling.getKey().getText());
			}
		} else if (kind.equals("tournament")) {
			Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
			selection.put(id, 1);
			CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
			plugins.get(popup).buttonPressed(buttonIds.get("CONFIRM"));
			assertEquals(List.of(id, ""), template.getFleetIds());
		} else {
			picker.closeOverlays();
		}
		assertNull(getField(picker, "popup"));
		assertTrue(removedComponents.contains(gridPanel));
		assertTrue(router.isEmpty());
		assertEquals(registrations, ((Map<?, ?>) getField(router.getSession(), "registrations")).size());
		if (kind.equals("tournament")) {
			picker.close();
		}
	}

	private BattleNext_OptionPicker picker(String kind, CustomPanelAPI root, BattleNext_UiInputRouter router) throws Exception {

		BattleNext_OptionField field;
		if (kind.equals("tournament")) {
			BattleNext_FleetStore fleets = new BattleNext_FleetStore() {
				@Override
				public BattleNext_FleetModel get(String id) {
					return getAll().stream().filter(fleet -> fleet.getId().equals(id)).findFirst().orElse(null);
				}

				@Override
				public List<BattleNext_FleetModel> getAll() {
					return List.of(new BattleNext_FleetModel("fleet_id", "Fleet name", List.of(), List.of(), List.of()));
				}
			};
			template = new BattleNext_TournamentTemplate(2);
			var configuration = new BattleNext_TournamentModel("cup", "Cup",
					BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
			var display = new BattleNext_TournamentDisplay(router, configuration, null,
					BattleNext_TournamentDisplay.details(configuration, null, template.getFleetIds(), fleets,
							new BattleNext_RuleStore(), "", List.of()), List.of(), action -> {});
			ownerClosers.add(display::close);
			display.enableTemplateEditing(root, new BattleNext_FleetSourceProvider(fleets), template::getFleetIds, template::select);
			display.attach(root);
			var chart = (BattleNext_TournamentView) getField(display, "chart");
			var node = (BattleNext_TournamentView.Node) ((List<?>) getField(chart, "nodes")).get(0);
			float[] point = chart.project(node.x + 110f, node.y + 33f);
			for (boolean down : List.of(true, false)) {
				AtomicBoolean consumed = new AtomicBoolean();
				InputEventAPI click = proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
					case "isLMBDownEvent", "isMouseDownEvent" -> down;
					case "isLMBUpEvent" -> !down;
					case "isMouseEvent" -> true;
					case "getX" -> (int) point[0];
					case "getY" -> (int) point[1];
					case "isConsumed" -> consumed.get();
					case "consume" -> {
						consumed.set(true);
						yield null;
					}
					default -> null;
				});
				chart.handleInput(List.of(click));
				assertTrue(consumed.get());
			}
			return (BattleNext_OptionPicker) getField(display, "fleetPicker");
		} else if (kind.equals("restriction")) {
			var editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
			ownerClosers.add(editor::close);
			editor.open(null);
			editor.setInputRouter(router);
			attachEditor(editor, root);
			return (BattleNext_OptionPicker) getField(editor, "restrictionPicker");
		} else if (kind.equals("smod") || kind.equals("dmod")) {
			if (refit == null) {
				refit = new BattleNext_RefitPanelPlugin(() -> {});
				ownerClosers.add(refit::close);
				refit.init(root, 1000f, 900f);
				refit.open(proxy(FleetMemberAPI.class, (method, arguments) -> method.equals("getVariant") ? variant : null),
						new Refit(null, variant));
			}
			BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(refit,
					kind.equals("smod") ? "sModPicker" : "dModPicker");
			return picker;
		} else {
			if (fleetEditor == null) {
				fleetEditor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
				ownerClosers.add(fleetEditor::close);
				fleetEditor.open(null, false);
				fleetEditor.setInputRouter(router);
				fleetEditor.setOverlayHost(root);
				attachEditor(fleetEditor, root);
				fleetEditor.buttonPressed("add_member");
			}
			if (kind.equals("commander")) {
				field = optionFields(fleetEditor).get(0);
			} else {
				List<BattleNext_OptionField> members = optionFields(fleetEditor).subList(1, optionFields(fleetEditor).size());
				int index = List.of("variant", "personality", "officer", "portrait").indexOf(kind);
				field = members.get(index);
			}
		}
		return (BattleNext_OptionPicker) getField(field, "picker");
	}

	private HullModSpecAPI hullmod(String id) {
		return proxy(HullModSpecAPI.class, (method, arguments) -> switch (method) {
			case "getId" -> id;
			case "getDisplayName" -> "Hullmod " + id;
			case "getSpriteName" -> "graphics/mod.png";
			case "getDescriptionFormat" -> PARAGRAPH.replace("%", "%%") + "\nSecond paragraph";
			case "hasTag" -> id.equals("damaged") && arguments[0].equals("dmod");
			case "getEffect" -> proxy(HullModEffect.class, (name, values) -> switch (name) {
				case "shouldAddDescriptionToTooltip" -> true;
				case "hasSModEffect" -> !id.equals("damaged");
				case "addSModSection" -> {
					((TooltipMakerAPI) values[0]).addPara("S-mod effect 50%.", BattleNext_UiTheme.TEXT, 8f);
					yield null;
				}
				default -> null;
			});
			case "getSModEffectFormat" -> "Effect";
			case "getSModDescription" -> "S-mod effect 50%.";
			default -> null;
		});
	}
}
