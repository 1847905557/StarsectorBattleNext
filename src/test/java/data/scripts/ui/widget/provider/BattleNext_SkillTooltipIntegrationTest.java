package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.characters.CustomSkillDescription;
import com.fs.starfarer.api.characters.LevelBasedEffect;
import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.SkillEffectType;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.skills.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestSkillFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class BattleNext_SkillTooltipIntegrationTest extends BattleNext_TestSkillFixture {

	@Test
	void catalogsApplyRoleAndTagFiltersToTheCurrentOrderedSpecs() {

		Map<String, SkillSpecAPI> catalog = new LinkedHashMap<>();
		catalog.put("combat", catalogSkill("combat", false, true, false));
		catalog.put("admin", catalogSkill("admin", true, false, false));
		catalog.put("fleet", catalogSkill("fleet", false, false, false));
		catalog.put("forced", catalogSkill("forced", true, false, false,
				"BattleNext_forceAllowed", Skills.TAG_DEPRECATED, Skills.TAG_NPC_ONLY, Skills.TAG_PLAYER_ONLY));
		catalog.put("deprecated", catalogSkill("deprecated", false, true, false, Skills.TAG_DEPRECATED));
		catalog.put("restricted", catalogSkill("restricted", false, true, false, Skills.TAG_NPC_ONLY, Skills.TAG_PLAYER_ONLY));
		catalog.put("npc", catalogSkill("npc", false, true, false, Skills.TAG_NPC_ONLY));
		catalog.put("player", catalogSkill("player", false, true, false, Skills.TAG_PLAYER_ONLY));
		catalog.put("aptitude", catalogSkill("aptitude", false, true, true, "BattleNext_forceAllowed"));
		List<String> identities = new ArrayList<>(catalog.keySet());
		identities.add("missing");
		SettingsAPI delegate = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			if (method.getName().equals("getSortedSkillIds")) return identities;
			if (method.getName().equals("getSkillSpec")) return catalog.get(args[0]);
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		var commander = provider("commander");
		var officer = provider("officer");
		assertEquals(List.of("combat", "fleet", "forced", "npc", "player"),
				commander.getOptions().stream().map(option -> option.getId()).toList());
		assertEquals(List.of("combat", "forced", "npc", "player"),
				officer.getOptions().stream().map(option -> option.getId()).toList());
		assertEquals(List.of("Name combat", "Name forced", "Name npc", "Name player"),
				officer.getOptions().stream().map(option -> option.getLabel()).toList());
		assertEquals("graphics/combat.png", officer.getIconPath("combat"));
		assertEquals("", officer.getIconPath("missing"));
		catalog.remove("combat");
		catalog.put("fleet", catalogSkill("fleet", false, true, false));
		assertEquals(List.of("fleet", "forced", "npc", "player"),
				officer.getOptions().stream().map(option -> option.getId()).toList());
	}

	@Test
	void stackLimitUsesDisplayEffectLevelsAndCurrentSpecAvailability() {
		var source = provider("officer");
		assertEquals(0, source.getMaxStack("missing"));
		assertEquals(1, source.getMaxStack(SKILL_ID));
		effects.add(effect(SkillEffectType.DESCRIPTION, 3f, "Text", false, null));
		assertEquals(1, source.getMaxStack(SKILL_ID));
		effects.add(effect(SkillEffectType.SHIP, 1f, "Normal", false, new Helmsmanship.Level1()));
		assertEquals(1, source.getMaxStack(SKILL_ID));
		effects.add(effect(SkillEffectType.SHIP, 2f, "Elite", false, new Helmsmanship.Level4()));
		assertEquals(2, source.getMaxStack(SKILL_ID));
	}

	@Test
	void fieldModulationUsesYellowEffectsGreenEliteAndUnchangedAuthorColor() {

		List<LevelBasedEffect> scripts = List.of(new FieldModulation.Level1(), new FieldModulation.Level2(),
				new FieldModulation.Level3(), new FieldModulation.Level4(), new FieldModulation.Level6());
		for (int index = 0; index < scripts.size(); index++) {
			effects.add(effect(SkillEffectType.SHIP, index < 2 ? 1f : 2f, index < 2 ? null : "Elite", false, scripts.get(index)));
		}
		TooltipMakerAPI content = renderSkill(provider("officer"));
		List<LabelAPI> labels = textLabels(content);
		for (LevelBasedEffect script : scripts) {
			LabelAPI label = labels.stream().filter(value -> value.getText().equals(script.getEffectDescription(2f)))
					.findFirst().orElseThrow();
			assertEquals(Misc.getHighlightColor(), labelColors.get(label.getPosition()));
			assertNotEquals(BattleNext_UiTheme.TEXT, labelColors.get(label.getPosition()));
		}
		for (String heading : List.of(String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_skill_effect_level"), "1"), "Elite")) {
			LabelAPI label = labels.stream().filter(value -> value.getText().equals(heading)).findFirst().orElseThrow();
			assertEquals(heading.equals("Elite") ? Misc.getStoryOptionColor() : BattleNext_UiTheme.ACCENT,
					labelColors.get(label.getPosition()));
		}
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(labels.get(3).getPosition()));
		assertEquals(Alignment.RMID, labelAlignments.get(labels.get(3).getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(labels.get(2).getPosition()));
	}

	@ParameterizedTest
	@CsvSource(value = {"<null>,1", "'',2", "Normal,1", "Mastery,2", "Elite,1", "Elite,2"}, nullValues = "<null>")
	void onlyEliteGroupNameUsesGreenRegardlessOfLevel(String group, float level) {
		effects.add(effect(SkillEffectType.SHIP, level, group, false, new Helmsmanship.Level1()));
		List<LabelAPI> labels = textLabels(renderSkill(provider("officer")));
		String heading = group == null || group.isEmpty() ? String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_skill_effect_level"), Integer.toString((int) level)) : group;
		assertEquals(heading, labels.get(5).getText());
		Color expected = "Elite".equals(group) ? Misc.getStoryOptionColor() : BattleNext_UiTheme.ACCENT;
		assertEquals(expected, labelColors.get(labels.get(5).getPosition()));
		assertEquals(Misc.getHighlightColor(), labelColors.get(labels.get(6).getPosition()));
	}

	@Test
	void declaredShipEffectScopesUseNativeHighlightColor() {
		LevelBasedEffect script = proxy(LevelBasedEffect.class, (method, args) -> switch (method) {
			case "getScopeDescription" -> ScopeDescription.PILOTED_SHIP;
			case "getEffectDescription" -> "Ship effect 50%";
			default -> null;
		});
		effects.add(effect(SkillEffectType.SHIP, 1f, "Normal", false, script));
		List<LabelAPI> labels = textLabels(renderSkill(provider("officer")));
		assertEquals(Misc.getHighlightColor(), labelColors.get(labels.get(labels.size() - 1).getPosition()));
	}

	@ParameterizedTest
	@EnumSource(value = SkillEffectType.class, names = {"SHIP", "CHARACTER_STATS"})
	void absentScopeUsesNativeTypeSpecificColor(SkillEffectType type) {
		effects.add(effect(type, 1f, "Normal", false, textEffect("Effect without scope", new ArrayList<>())));
		List<LabelAPI> labels = textLabels(renderSkill(provider("commander")));
		boolean shipEffect = List.of(SkillEffectType.SHIP, SkillEffectType.ALL_SHIPS_IN_FLEET,
				SkillEffectType.ALL_FIGHTERS_IN_FLEET, SkillEffectType.SHIP_FIGHTERS).contains(type);
		assertEquals(shipEffect ? Misc.getTextColor() : Misc.getHighlightColor(),
				labelColors.get(labels.get(labels.size() - 1).getPosition()));
	}

	@Test
	void authorUsesNormalFullWidthRightAlignedParagraphBetweenDescriptionAndScope() {

		author = "  Full source author 50% - " + "Long attribution. ".repeat(80);
		deferTooltipWidth = true;
		TooltipMakerAPI content = renderSkill(provider("commander"));
		List<LabelAPI> labels = textLabels(content);
		assertEquals(List.of("Preview Skill", SKILL_ID, "First paragraph 50%.", author, "Ship - Fleet"), textValues(content));
		LabelAPI attribution = labels.get(3);
		assertEquals(Alignment.RMID, labelAlignments.get(attribution.getPosition()));
		assertEquals("default", labelFonts.get(attribution.getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(attribution.getPosition()));
		assertEquals(labels.get(2).getPosition().getWidth(), attribution.getPosition().getWidth());
		assertEquals(labels.get(2).getPosition().getX(), attribution.getPosition().getX());
		assertTrue(attribution.getPosition().getHeight() > paragraphLineHeight);
		assertGap(labels.get(2), attribution, 5f);
		assertGap(attribution, labels.get(4), 5f);
		assertEquals(520f, attribution.getPosition().getWidth());
		assertEquals(0f, content.getPosition().getWidth());
		assertTrue(previews.isEmpty());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"\t\r\n"})
	void emptyAuthorsKeepDescriptionScopeAndEffectsInTheirOwnOrder(String value) {
		author = value;
		effects.add(effect(SkillEffectType.SHIP, 1f, "Normal", false, new Helmsmanship.Level1()));
		TooltipMakerAPI content = renderSkill(provider("officer"));
		assertEquals(List.of("Preview Skill", SKILL_ID, "First paragraph 50%.", "Ship - Fleet", "Normal",
				new Helmsmanship.Level1().getEffectDescription(1f)), textValues(content));
		assertTrue(textLabels(content).stream().noneMatch(label -> labelAlignments.get(label.getPosition()) == Alignment.RMID));
	}

	@Test
	void nativeHelmsmanshipKeepsBothGroupsAndEveryEffectInOriginalOrder() {

		List<LevelBasedEffect> scripts = List.of(new Helmsmanship.Level1(), new Helmsmanship.Level2(),
				new Helmsmanship.Level4(), new Helmsmanship.Level3());
		for (int index = 0; index < scripts.size(); index++) {
			effects.add(effect(SkillEffectType.SHIP, index < 2 ? 1f : 2f, index < 2 ? null : "Elite", false, scripts.get(index)));
		}
		var source = provider("officer");
		TooltipMakerAPI content = renderSkill(source);
		List<String> values = textValues(content);
		assertEquals(List.of("Preview Skill", SKILL_ID, "First paragraph 50%.", author, "Ship - Fleet", String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_skill_effect_level"), "1"),
				scripts.get(0).getEffectDescription(1f), scripts.get(1).getEffectDescription(1f), "Elite",
				scripts.get(2).getEffectDescription(2f), scripts.get(3).getEffectDescription(2f)), values);
		assertEquals(2, source.getMaxStack(SKILL_ID));
		List<LabelAPI> labels = textLabels(content);
		assertGap(labels.get(4), labels.get(5), 8f);
		assertGap(labels.get(5), labels.get(6), 5f);
		assertGap(labels.get(6), labels.get(7), 5f);
		assertGap(labels.get(7), labels.get(8), 8f);
		assertEquals(BattleNext_UiTheme.ACCENT, labelColors.get(labels.get(5).getPosition()));
		assertEquals(Misc.getStoryOptionColor(), labelColors.get(labels.get(8).getPosition()));
		for (int index : List.of(6, 7, 9, 10)) {
			assertEquals("default", labelFonts.get(labels.get(index).getPosition()));
			assertEquals(Misc.getHighlightColor(), labelColors.get(labels.get(index).getPosition()));
		}
		assertEquals(List.of("skip:true", SKILL_ID + ":1.0", SKILL_ID + ":1.0", SKILL_ID + ":2.0", SKILL_ID + ":2.0"),
				previews.get(0).getCalls());
	}

	@Test
	void fixedAndLevelBasedEffectsUseTheirOwnLevelsAndGroupNames() {

		List<Float> levels = new ArrayList<>();
		effects.add(effect(SkillEffectType.SHIP, 0f, "Zero", false, textEffect("Fixed zero 20%", levels)));
		effects.add(effect(SkillEffectType.SHIP, 0f, "Zero", true, textEffect("Scaled zero 30%", levels)));
		effects.add(effect(SkillEffectType.FLEET, 1f, null, true, textEffect("First", levels)));
		effects.add(effect(SkillEffectType.FLEET, 1f, "", false, textEffect("Second", levels)));
		effects.add(effect(SkillEffectType.CHARACTER_STATS, 1f, "Other", false, textEffect("Other", levels)));
		effects.add(effect(SkillEffectType.SHIP, 3f, "", true, textEffect("Third", levels)));
		TooltipMakerAPI content = renderSkill(provider("commander"));
		assertEquals(List.of(0f, 1f, 1f, 1f, 1f, 3f), levels);
		assertEquals(List.of("Zero", "Fixed zero 20%", "Scaled zero 30%", String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_skill_effect_level"), "1"), "First", "Second", "Other", "Other", String.format(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_skill_effect_level"), "3"), "Third"),
				textValues(content).subList(5, textValues(content).size()));
	}

	@Test
	void nativeCustomEffectsUseTheoryThresholdsAndPreserveHighlightedParagraphs() {

		effects.add(effect(SkillEffectType.ALL_SHIPS_IN_FLEET, 1f, "Crew", false, new CrewTraining.Level1()));
		effects.add(effect(SkillEffectType.SHIP, 1f, "Weapons", false, new EnergyWeaponMastery.Level1()));
		effects.add(effect(SkillEffectType.SHIP, 1f, "Weapons", false, new EnergyWeaponMastery.Level2()));
		TooltipMakerAPI content = renderSkill(provider("commander"));
		List<LabelAPI> labels = textLabels(content);
		assertTrue(textValues(content).contains("+" + (int) CrewTraining.CR_PERCENT
				+ "% maximum combat readiness for combat ships (maximum: " + (int) CrewTraining.CR_PERCENT + "%)"));
		String threshold = "Maximum at " + (int) BaseSkillEffectDescription.OP_THRESHOLD;
		assertTrue(textValues(content).stream().anyMatch(value -> value.contains(threshold)));
		assertTrue(textValues(content).stream().noneMatch(value -> value.contains("your fleet")));
		LabelAPI cr = labels.stream().filter(label -> label.getText().contains("maximum combat readiness")).findFirst().orElseThrow();
		assertEquals(List.of((int) CrewTraining.CR_PERCENT + "%", (int) CrewTraining.CR_PERCENT + "%"), labelHighlights.get(cr.getPosition()));
		assertEquals(List.of(Misc.getHighlightColor()), labelHighlightColors.get(cr.getPosition()));
		LabelAPI weapon = labels.stream().filter(label -> label.getText().startsWith("Energy weapons deal")).findFirst().orElseThrow();
		assertEquals(List.of("+" + (int) EnergyWeaponMastery.ENERGY_DAMAGE_PERCENT + "%"), labelHighlights.get(weapon.getPosition()));
		assertEquals(List.of(Misc.getHighlightColor()), labelHighlightColors.get(weapon.getPosition()));
		assertEquals(new EnergyWeaponMastery.Level2().getEffectDescription(1f), labels.get(labels.size() - 1).getText());
		assertEquals("default", labelFonts.get(labels.get(labels.size() - 1).getPosition()));
		assertEquals(Misc.getHighlightColor(), labelColors.get(labels.get(labels.size() - 1).getPosition()));
	}

	@Test
	void customCallbacksReceiveFreshPreviewStatsFullWidthAndRestoreTextStyle() {

		List<MutableCharacterStatsAPI> receivedStats = new ArrayList<>();
		CustomSkillDescription custom = proxy(CustomSkillDescription.class, (method, args) -> switch (method) {
			case "hasCustomDescription" -> true;
			case "createCustomDescription" -> {
				MutableCharacterStatsAPI stats = (MutableCharacterStatsAPI) args[0];
				assertTrue(stats.isSkipRefresh());
				assertEquals(2f, stats.getSkillLevel(SKILL_ID));
				assertSame(skill, args[1]);
				assertEquals(((TooltipMakerAPI) args[2]).getWidthSoFar(), args[3]);
				receivedStats.add(stats);
				TooltipMakerAPI info = (TooltipMakerAPI) args[2];
				info.setParaSmallInsignia();
				info.setParaFontColor(Color.MAGENTA);
				info.addPara("Custom first 20%", 0f);
				info.addPara("Custom second 30%", 0f);
				yield null;
			}
			case "getEffectDescription" -> throw new AssertionError("Custom callback owns the effect text");
			default -> null;
		});
		effects.add(effect(SkillEffectType.FLEET, 2f, "Elite", false, custom));
		effects.add(effect(SkillEffectType.SHIP, 2f, "Elite", false, new Helmsmanship.Level4()));
		for (int iteration = 0; iteration < 2; iteration++) {
			TooltipMakerAPI content = renderSkill(provider("commander"));
			List<LabelAPI> labels = textLabels(content);
			LabelAPI after = labels.get(labels.size() - 1);
			assertEquals("default", labelFonts.get(after.getPosition()));
			assertEquals(Misc.getHighlightColor(), labelColors.get(after.getPosition()));
			assertGap(labels.get(5), labels.get(6), 5f);
			assertEquals(List.of("Custom first 20%", "Custom second 30%"), textValues(content).subList(6, 8));
		}
		assertNotSame(receivedStats.get(0), receivedStats.get(1));
		assertSame(previews.get(0).getApi(), receivedStats.get(0));
		assertSame(previews.get(1).getApi(), receivedStats.get(1));
	}

	@Test
	void onlyNumericAndCustomEffectTypesEnterTheDescriptionChain() {

		List<Float> levels = new ArrayList<>();
		List<SkillEffectType> included = new ArrayList<>();
		for (SkillEffectType type : SkillEffectType.values()) {
			boolean excluded = List.of(SkillEffectType.ABILITY_UNLOCK, SkillEffectType.HULLMOD_UNLOCK, SkillEffectType.DESCRIPTION).contains(type);
			LevelBasedEffect script = excluded ? proxy(LevelBasedEffect.class, (method, args) -> {
				throw new AssertionError("Excluded type has its own display contract");
			}) : textEffect(type.name(), levels);
			effects.add(effect(type, 1f, "Normal", false, script));
			if (!excluded) included.add(type);
		}
		TooltipMakerAPI content = renderSkill(provider("commander"));
		assertEquals(included.stream().map(Enum::name).toList(), textValues(content).subList(6, textValues(content).size()));
		assertEquals(included.size(), levels.size());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"\t\n"})
	void emptyDescriptionAndEmptyEffectTextRetainAuthorAndOtherEffects(String value) {
		description = value;
		effects.add(effect(SkillEffectType.SHIP, 1f, "Normal", false, textEffect(value, new ArrayList<>())));
		effects.add(effect(SkillEffectType.SHIP, 1f, "Normal", false, new Helmsmanship.Level1()));
		TooltipMakerAPI content = renderSkill(provider("officer"));
		assertEquals(List.of("Preview Skill", SKILL_ID, author, "Ship - Fleet", "Normal",
				new Helmsmanship.Level1().getEffectDescription(1f)), textValues(content));
	}

	@Test
	void customOptOutUsesItsPlainDescriptionWithPercentCharactersIntact() {
		CustomSkillDescription script = proxy(CustomSkillDescription.class, (method, args) -> switch (method) {
			case "hasCustomDescription" -> false;
			case "getEffectDescription" -> "Literal 50%, %s and %d text";
			case "createCustomDescription" -> throw new AssertionError("Custom opt-out uses ordinary description");
			default -> null;
		});
		effects.add(effect(SkillEffectType.SHIP, 1f, "Normal", false, script));
		assertEquals("Literal 50%, %s and %d text", textValues(renderSkill(provider("officer"))).get(6));
	}

	@Test
	void descriptionFailureLogsCompleteIdentityRestoresStyleAndContinues() throws Exception {

		CustomSkillDescription failed = proxy(CustomSkillDescription.class, (method, args) -> switch (method) {
			case "hasCustomDescription" -> true;
			case "createCustomDescription" -> {
				TooltipMakerAPI info = (TooltipMakerAPI) args[2];
				info.setParaSmallInsignia();
				info.setParaFontColor(Color.MAGENTA);
				throw new IllegalStateException("Broken mod description 50%");
			}
			default -> null;
		});
		effects.add(effect(SkillEffectType.SHIP, 2f, "Elite", false, failed));
		effects.add(effect(SkillEffectType.SHIP, 2f, "Elite", false, new Helmsmanship.Level4()));
		List<LoggingEvent> events = new ArrayList<>();
		AppenderSkeleton appender = new AppenderSkeleton() {
			@Override
			protected void append(LoggingEvent event) {
				events.add(event);
			}
			@Override
			public void close() {

			}
			@Override
			public boolean requiresLayout() {
				return false;
			}
		};
		var logger = Global.getLogger(BattleNext_Logger.class);
		logger.addAppender(appender);
		try {
			TooltipMakerAPI content = renderSkill(provider("officer"));
			List<LabelAPI> labels = textLabels(content);
			LabelAPI diagnostic = labels.stream().filter(label -> label.getText().startsWith("Load failed:")).findFirst().orElseThrow();
			assertTrue(diagnostic.getText().contains("Broken mod description 50%"));
			assertEquals("default", labelFonts.get(diagnostic.getPosition()));
			assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(diagnostic.getPosition()));
			assertEquals(new Helmsmanship.Level4().getEffectDescription(2f), labels.get(labels.size() - 1).getText());
			assertEquals(1, events.size());
			assertEquals(Level.WARN, events.get(0).getLevel());
			JSONObject entry = new JSONObject(events.get(0).getRenderedMessage());
			assertEquals("skill.effect.description.degraded", entry.getString("event"));
			JSONObject fields = entry.getJSONObject("fields");
			assertEquals(SKILL_ID, fields.getString("skill_id"));
			assertEquals(failed.getClass().getName(), fields.getString("effect_class"));
			assertEquals("SHIP", fields.getString("effect_type"));
			assertEquals(2f, fields.getDouble("required_level"));
			assertEquals(2f, fields.getDouble("preview_level"));
			assertEquals("Elite", fields.getString("group"));
		} finally {
			logger.removeAppender(appender);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"commander", "officer"})
	void nativeSkillHoverKeepsAuthorAndEffectsAcrossFilteringAndReleasesSearchInput(String kind) throws Exception {

		effects.add(effect(SkillEffectType.SHIP, 1f, "Normal", false, new Helmsmanship.Level1()));
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(800f, 700f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = root.createUIElement(32f, 28f, false);
		BattleNext_OptionPicker picker = new BattleNext_OptionPicker(router, root, 32f, 28f, "...", 0f, provider(kind),
				BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, ids -> {});
		picker.attach(host, root);
		root.addUIElement(host).inTL(20f, 20f);
		var open = picker.getClass().getDeclaredMethod("open");
		open.setAccessible(true);
		open.invoke(picker);
		CustomPanelAPI popup = (CustomPanelAPI) pickerField(picker, "popup");
		for (int rebuild = 0; rebuild < 2; rebuild++) {
			CustomPanelAPI gridPanel = (CustomPanelAPI) pickerField(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			var tile = tooltips.get(grid).getComponents().get(0);
			var creator = attachedTooltips.get(tile);
			assertEquals(520f, creator.getTooltipWidth(null));
			assertFalse(tooltipBindings.get(tile).recreateEveryFrame());
			assertSame(tooltips.get(grid), tooltipBindings.get(tile).owner());
			TooltipMakerAPI content = tooltip(creator.getTooltipWidth(null), 0f, false);
			creator.createTooltip(content, false, null);
			assertEquals(SKILL_ID, textValues(content).get(1));
			assertEquals(author, textValues(content).get(3));
			assertEquals(new Helmsmanship.Level1().getEffectDescription(1f), textValues(content).get(6));
			BattleNext_TextField search = (BattleNext_TextField) pickerField(picker, "search");
			search.setText("unmatched");
			plugins.get(popup).advance(0f);
			assertTrue(removedComponents.contains(gridPanel));
			search.setText("");
			plugins.get(popup).advance(0f);
		}
		BattleNext_TextField search = (BattleNext_TextField) pickerField(picker, "search");
		search.grabFocus(false);
		assertTrue(search.hasFocus());
		plugins.get(popup).buttonPressed(buttonIds.get("CANCEL"));
		assertFalse(search.hasFocus());
		assertTrue(router.isEmpty());
		assertFalse(router.getSession().hasInputState());
		picker.close();
		assertTrue(tooltips.get(host).getComponents().isEmpty());
	}

	private SkillSpecAPI catalogSkill(String id, boolean admin, boolean combat, boolean aptitude, String... tags) {
		return proxy(SkillSpecAPI.class, (method, args) -> switch (method) {
			case "getId" -> id;
			case "getName" -> "Name " + id;
			case "getSpriteName" -> "graphics/" + id + ".png";
			case "isAdminSkill" -> admin;
			case "isCombatOfficerSkill" -> combat;
			case "isAptitudeEffect" -> aptitude;
			case "hasTag" -> List.of(tags).contains(args[0]);
			default -> null;
		});
	}

	private Object pickerField(BattleNext_OptionPicker picker, String name) throws Exception {
		Field field = BattleNext_OptionPicker.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(picker);
	}

	private void assertGap(LabelAPI previous, LabelAPI next, float gap) {
		assertEquals(gap, previous.getPosition().getY() - next.getPosition().getY() - next.getPosition().getHeight(), 0.001f);
	}
}
