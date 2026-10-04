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
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.ui.BattleNext_RefitPanelPlugin;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.editor.BattleNext_RuleEditor;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

class BattleNext_OptionPickerConsumersIntegrationTest extends BattleNext_TestVariantFixture {

	private static final String PARAGRAPH = "Complete description 20%. " + "Full first paragraph. ".repeat(30).strip();
	private Object previousRestrictions;
	private Object previousScriptFactory;
	private ShipVariantAPI variant;
	private BattleNext_RefitPanelPlugin refit;

	@BeforeEach
	void installChoiceData() throws Exception {

		var registry = BattleNext_RestrictionRegistry.getInstance();
		previousRestrictions = getField(registry, "entries");
		previousScriptFactory = getField(registry, "scriptFactory");
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
		if (refit != null) refit.close();
		BattleNext_UiInputRouter.activate(null);
		var registry = BattleNext_RestrictionRegistry.getInstance();
		setField(registry, "entries", previousRestrictions);
		setField(registry, "scriptFactory", previousScriptFactory);
	}

	@ParameterizedTest
	@CsvSource({"commander,520,Sensors,sensors", "officer,520,Sensors,sensors",
			"variant,360,Parent - Unsubmitted,parent_variant", "personality,360,Steady,steady",
			"portrait,520,Hegemony,graphics/portraits/hegemony.png", "tournament,360,Fleet name,fleet_id",
			"smod,360,Hullmod ordinary,ordinary", "dmod,360,Hullmod damaged,damaged",
			"restriction,360,Fleet limit 20%,limit"})
	void allNineConsumersUseTheirNativeWidthIdentityAndCompleteText(String kind, int width,
			String title, String id) throws Exception {

		CustomPanelAPI root = panel(1000f, 900f, new BaseCustomUIPanelPlugin());
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		BattleNext_OptionPicker picker = picker(kind, root, router);
		router = (BattleNext_UiInputRouter) getField(picker, "inputRouter");
		int registrations = ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size();
		var open = picker.getClass().getDeclaredMethod("open");
		open.setAccessible(true);
		open.invoke(picker);
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
			assertTrue(((LabelAPI) components.get(2)).getText().contains("BattleNext_variant_base_deployment_points"));
			assertEquals("BattleNext_variant_flux_capacitors: 0", ((LabelAPI) components.get(3)).getText());
			assertEquals("BattleNext_variant_flux_vents: 0", ((LabelAPI) components.get(4)).getText());
			List<String> values = components.stream().filter(LabelAPI.class::isInstance)
					.map(LabelAPI.class::cast).map(LabelAPI::getText).toList();
			assertTrue(values.contains("BattleNext_variant_weapons: 1"));
			assertTrue(values.contains("Weapon laser ×1"));
			assertTrue(values.contains("BattleNext_variant_fighter_wings: 1"));
			assertTrue(values.contains("BattleNext_variant_modules: 1"));
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
			assertEquals("BattleNext_fleet_ships: 0 - BattleNext_fleet_fighter_wings: 0", ((LabelAPI) components.get(2)).getText());
			assertEquals("BattleNext_fleet_base_dp: 0 - BattleNext_fleet_base_fp: 0", ((LabelAPI) components.get(3)).getText());
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
			assertTrue(((LabelAPI) components.get(3)).getText().contains("BattleNext_refit_d_mod_"));
		}
		picker.closeOverlays();
		assertNull(getField(picker, "popup"));
		assertTrue(removedComponents.contains(gridPanel));
		assertTrue(router.isEmpty());
		assertEquals(registrations, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
		picker.close();
		router.clear();
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
			var editor = new BattleNext_TournamentEditor(new BattleNext_TournamentStore(), fleets, new BattleNext_RuleStore());
			editor.open(null);
			editor.setInputRouter(router);
			editor.attach(root);
			return (BattleNext_OptionPicker) getField(editor, "fleetPicker");
		} else if (kind.equals("restriction")) {
			var editor = new BattleNext_RuleEditor(new BattleNext_RuleStore());
			editor.open(null);
			editor.setInputRouter(router);
			editor.attach(root);
			return (BattleNext_OptionPicker) getField(editor, "restrictionPicker");
		} else if (kind.equals("smod") || kind.equals("dmod")) {
			var plugin = new BattleNext_RefitPanelPlugin(() -> {});
			refit = plugin;
			plugin.init(root, 1000f, 900f);
			plugin.open(proxy(FleetMemberAPI.class, (method, arguments) -> method.equals("getVariant") ? variant : null),
					new Refit(null, variant));
			BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(plugin,
					kind.equals("smod") ? "sModPicker" : "dModPicker");
			return picker;
		} else {
			var editor = new BattleNext_FleetEditor(new BattleNext_FleetStore());
			editor.open(null, false);
			editor.setInputRouter(router);
			editor.setOverlayHost(root);
			editor.attach(root);
			editor.buttonPressed("add_member");
			if (kind.equals("commander")) {
				field = (BattleNext_OptionField) getField(editor, "skillsPicker");
			} else {
				List<BattleNext_OptionField> members = (List<BattleNext_OptionField>) getField(editor, "memberPickers");
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
