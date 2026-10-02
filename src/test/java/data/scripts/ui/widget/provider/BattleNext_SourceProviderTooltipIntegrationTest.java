package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.HullModEffect;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_SourceProviderTooltipIntegrationTest {

	private SettingsAPI previousSettings;
	private SectorAPI previousSector;
	private ShipVariantAPI variant;
	private HullModSpecAPI hullmod;
	private SkillSpecAPI skill;
	private FactionSpecAPI factionSpec;
	private Description shipDescription;
	private Description factionDescription;

	@BeforeEach
	void installSourceData() throws Exception {
		previousSettings = Global.getSettings();
		previousSector = Global.getSector();
		ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class, (method, args) -> switch (method) {
			case "getDescriptionId" -> "wolf";
			case "getHullSize" -> ShipAPI.HullSize.FRIGATE;
			case "getSuppliesToRecover" -> 5f;
			case "getBuiltInMods" -> List.of("built");
			case "isBuiltInMod" -> "built".equals(args[0]);
			case "getHints" -> EnumSet.noneOf(ShipHullSpecAPI.ShipTypeHints.class);
			case "getDefenseType" -> ShieldAPI.ShieldType.OMNI;
			case "getNoCRLossTime" -> 1000f;
			case "isPhase", "hasTag" -> false;
			default -> null;
		});
		variant = proxy(ShipVariantAPI.class, (method, args) -> switch (method) {
			case "getHullSpec" -> hull;
			case "getHullSize" -> ShipAPI.HullSize.FRIGATE;
			case "getHullMods" -> List.of("built");
			case "hasHullMod", "hasTag" -> false;
			default -> null;
		});
		HullModEffect effect = proxy(HullModEffect.class, (method, args) ->
				"hasSModEffect".equals(method));
		hullmod = proxy(HullModSpecAPI.class, (method, args) -> switch (method) {
			case "getDisplayName" -> "Hardened Shields";
			case "getEffect" -> effect;
			case "getSModEffectFormat" -> "Elite shield effect";
			case "getSModDescription" -> "Elite shield effect, 50% stronger.";
			case "getDescription" -> "Reduces shield damage by 20%.";
			case "hasTag" -> "dmod".equals(args[0]);
			default -> null;
		});
		skill = proxy(SkillSpecAPI.class, (method, args) -> switch (method) {
			case "getDescription" -> "Improves combat coordination by 10%.";
			case "getScopeStr" -> "Ship";
			case "getScopeStr2" -> "Fleet";
			default -> null;
		});
		factionSpec = proxy(FactionSpecAPI.class, (method, args) -> switch (method) {
			case "getId" -> "hegemony";
			case "getDisplayName" -> "Hegemony";
			case "getAllPortraits" -> List.of("graphics/portraits/hegemony.png");
			default -> null;
		});
		shipDescription = new Description("wolf", Description.Type.SHIP);
		shipDescription.setText1("The Wolf is a fast combat frigate. It depends on agility.");
		factionDescription = new Description("hegemony", Description.Type.FACTION);
		factionDescription.setText1("The Hegemony descends from a Domain fleet. Its captains prize order.");
		Global.setSettings(proxy(SettingsAPI.class, (method, args) -> switch (method) {
			case "getColor" -> Color.GRAY;
			case "getFloat" -> 1f;
			case "getBoolean" -> false;
			case "getInt" -> 1;
			case "getString" -> args[1];
			case "getVariant" -> variant;
			case "getDescription" -> args[1] == Description.Type.SHIP ? shipDescription : factionDescription;
			case "getSkillSpec" -> skill;
			case "getHullModSpec" -> hullmod;
			case "getAllFactionSpecs" -> List.of(factionSpec);
			case "getMergedSpreadsheetData" -> new JSONArray().put(new JSONObject()
					.put("id", "steady").put("name", "Steady")
					.put("desc", "Balances aggression and defense."));
			default -> null;
		}));
		FactionAPI player = proxy(FactionAPI.class, (method, args) -> Color.GRAY);
		Global.setSector(proxy(SectorAPI.class, (method, args) ->
				"getPlayerFaction".equals(method) ? player : null));
	}

	@AfterEach
	void restoreGlobalState() {
		Global.setSettings(previousSettings);
		Global.setSector(previousSector);
	}

	@Test
	void variantTooltipQuotesShipBackgroundAndLabelsBaseDp() {
		RecordingTooltip view = new RecordingTooltip();
		new BattleNext_VariantSourceProvider().createTooltip(view.api,
				new OptionChoice("wolf_50%_custom", "Wolf - Custom"));

		assertEquals("Wolf - Custom", view.title);
		assertEquals("wolf_50%_custom", view.paragraphs.get(0));
		assertTrue(view.paragraphs.stream().anyMatch(text -> text.contains("The Wolf is a fast combat frigate")));
		assertTrue(view.paragraphs.stream().anyMatch(text -> text.contains("refit_base_dp: 5.0")));
	}

	@Test
	void personalityAndSkillTooltipsQuoteTheirSourceDescriptions() {
		RecordingTooltip personality = new RecordingTooltip();
		new BattleNext_PersonalitySourceProvider().createTooltip(personality.api,
				new OptionChoice("steady", "Steady"));
		RecordingTooltip skillView = new RecordingTooltip();
		new BattleNext_OfficerSkillSourceProvider().createTooltip(skillView.api,
				new OptionChoice("combat_skill", "Combat Skill"));

		assertEquals(List.of("steady", "Balances aggression and defense."), personality.paragraphs);
		assertTrue(skillView.paragraphs.contains("Improves combat coordination by 10%."));
		assertTrue(skillView.paragraphs.contains("Ship - Fleet"));
	}

	@Test
	void factionAndCustomPortraitsUseTheirRespectiveSourceText() {
		RecordingTooltip faction = new RecordingTooltip();
		new BattleNext_PortraitSourceProvider().createTooltip(faction.api,
				new OptionChoice("graphics/portraits/hegemony.png", "Hegemony"));
		RecordingTooltip custom = new RecordingTooltip();
		new BattleNext_PortraitSourceProvider().createTooltip(custom.api,
				new OptionChoice("graphics/portraits/custom.png", "custom"));

		assertTrue(faction.paragraphs.stream().anyMatch(text -> text.contains("Domain fleet")));
		assertEquals("graphics/portraits/custom.png", custom.paragraphs.get(0));
		assertTrue(custom.paragraphs.contains("BattleNext_ui_refit_custom_portrait"));
	}

	@Test
	void refitProvidersQuoteHullmodDescriptionsAndSModEffect() {
		RecordingTooltip sMod = new RecordingTooltip();
		new BattleNext_RefitSModSourceProvider(() -> variant).createTooltip(sMod.api,
				new OptionChoice("built", "Hardened Shields"));
		RecordingTooltip dMod = new RecordingTooltip();
		new BattleNext_RefitDModSourceProvider(() -> variant).createTooltip(dMod.api,
				new OptionChoice("faulty_grid", "Faulty Grid"));

		assertTrue(sMod.paragraphs.contains("Reduces shield damage by 20%."));
		assertTrue(sMod.paragraphs.contains("Elite shield effect, 50% stronger."));
		assertTrue(dMod.paragraphs.contains("Reduces shield damage by 20%."));
		assertTrue(dMod.paragraphs.contains("BattleNext_ui_refit_suitable"));
	}

	private interface Call {
		Object invoke(String method, Object[] args) throws Exception;
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> call.invoke(method.getName(), args)));
	}

	private static class RecordingTooltip {
		private final List<String> paragraphs = new ArrayList<>();
		private String title;
		private float height;
		private final PositionAPI position = (PositionAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{PositionAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getWidth" -> 450f;
					case "getHeight" -> 220f;
					default -> null;
				});
		private final TooltipMakerAPI api = (TooltipMakerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{TooltipMakerAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "addTitle" -> { title = (String) args[0]; height += 30f; yield null; }
					case "addPara" -> {
						paragraphs.add((String) args[0]);
						height += 22f;
						yield null;
					}
					case "getPosition" -> position;
					case "getHeightSoFar" -> height;
					case "createLabel" -> (LabelAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
							new Class<?>[]{LabelAPI.class}, (label, labelMethod, labelArgs) ->
									"autoSizeToWidth".equals(labelMethod.getName()) ? measured((String) args[0]) : null);
					default -> null;
				});

		private PositionAPI measured(String text) {
			float textHeight = 20f * Math.max(1, (text.length() + 54) / 55);
			return (PositionAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{PositionAPI.class}, (instance, method, args) ->
							"getHeight".equals(method.getName()) ? textHeight : null);
		}
	}
}
