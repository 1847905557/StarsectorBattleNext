package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleRestriction;
import data.model.rule.BattleNext_RuleStore;
import data.restriction.BattleNext_RestrictionRegistry;
import data.restriction.BattleNext_RestrictionScript;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

class BattleNext_RuleSourceProviderTest extends BattleNext_TestPageFixture {

	private Object previousEntries;
	private Object previousFactory;
	private Object previousLoadErrors;

	@BeforeEach
	void registerRestrictions() throws Exception {
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		previousEntries = getField(registry, "entries");
		previousFactory = getField(registry, "scriptFactory");
		previousLoadErrors = getField(registry, "loadErrors");
		registry.load(new JSONArray()
				.put(new JSONObject().put("id", "limit").put("name", "limit").put("type", "number").put("script", "limit_script").put("desc", "Fleet limit 50%"))
				.put(new JSONObject().put("id", "items").put("name", "items").put("type", "list").put("script", "items_script").put("desc", "Equipment list")),
				name -> (BattleNext_RestrictionScript) (fleet, restriction) -> List.of());
	}

	@AfterEach
	void restoreRestrictions() throws Exception {
		BattleNext_RestrictionRegistry registry = BattleNext_RestrictionRegistry.getInstance();
		Field entries = registry.getClass().getDeclaredField("entries");
		entries.setAccessible(true);
		entries.set(registry, previousEntries);
		Field factory = registry.getClass().getDeclaredField("scriptFactory");
		factory.setAccessible(true);
		factory.set(registry, previousFactory);
		Field loadErrors = registry.getClass().getDeclaredField("loadErrors");
		loadErrors.setAccessible(true);
		loadErrors.set(registry, previousLoadErrors);
	}

	@Test
	void optionsFollowCurrentStoreIdentityOrderAndContentAcrossSaveDeleteAndReload() throws Exception {
		BattleNext_RuleStore store = new BattleNext_RuleStore();
		store.reload();
		BattleNext_RuleSourceProvider source = new BattleNext_RuleSourceProvider(store);
		assertTrue(source.getOptions().isEmpty());
		store.save(new BattleNext_RuleModel("zeta", "同名 50%"));
		store.save(new BattleNext_RuleModel("alpha", "同名 50%"));
		assertEquals(List.of("zeta", "alpha"), source.getOptions().stream().map(OptionChoice::getId).toList());
		assertEquals(List.of("同名 50%", "同名 50%"), source.getOptions().stream().map(OptionChoice::getLabel).toList());
		store.save(new BattleNext_RuleModel("zeta", "Updated rule"));
		assertEquals("Updated rule", source.getOptions().get(0).getLabel());
		store.delete("alpha");
		store.reload();
		assertEquals(List.of("zeta"), source.getOptions().stream().map(OptionChoice::getId).toList());
		assertEquals("Updated rule", source.getOptions().get(0).getLabel());
	}

	@Test
	void tooltipShowsCompleteOrderedRestrictionsAndReadonlyEnabledStatesBeforeBoundsExist() throws Exception {

		deferTooltipWidth = true;
		BattleNext_RuleRestriction number = new BattleNext_RuleRestriction("limit", "number");
		number.setEnabled(true);
		number.setValue(25.5);
		BattleNext_RuleRestriction items = new BattleNext_RuleRestriction("items", "list");
		List<String> values = List.of("中文 50%", "item_" + "long".repeat(80));
		items.setItems(values);
		BattleNext_RuleRestriction missing = new BattleNext_RuleRestriction("missing", "number");
		missing.setEnabled(true);
		missing.setValue(9);
		BattleNext_RuleModel rule = new BattleNext_RuleModel("rule_50%", "完整规则 " + "名称".repeat(100));
		rule.setRestrictions(List.of(number, items, missing));
		BattleNext_RuleStore store = new BattleNext_RuleStore();
		store.reload();
		store.save(rule);
		BattleNext_RuleSourceProvider source = new BattleNext_RuleSourceProvider(store);
		OptionChoice option = source.getOptions().get(0);
		assertFalse(source.expectsIcon());
		assertEquals("", source.getIconPath(option.getId()));
		TooltipMakerAPI tooltip = tooltip(source.getTooltipWidth(), 0f, false);
		assertEquals(0f, tooltip.getPosition().getWidth());
		source.createTooltip(tooltip, option);
		List<UIComponentAPI> components = tooltips.get(tooltip).getComponents();
		List<LabelAPI> text = components.stream().filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).toList();
		assertEquals(List.of(rule.getName(), rule.getId(), BattleNext_TestTextCatalog.text("Rule", "BattleNext_rule_restrictions") + ": 3",
				"Fleet limit 50%", "limit  [number]", BattleNext_TestTextCatalog.text("Rule", "BattleNext_rule_value_or_items") + ": 25.5",
				"Equipment list", "items  [list]", BattleNext_TestTextCatalog.text("Rule", "BattleNext_rule_value_or_items") + ": " + String.join(",", values),
				BattleNext_TestTextCatalog.text("Rule", "BattleNext_rule_missing_restriction"), "missing  [number]", BattleNext_TestTextCatalog.text("Rule", "BattleNext_rule_value_or_items") + ": 9.0"),
				text.stream().map(LabelAPI::getText).toList());
		assertEquals(BattleNext_UiTheme.MUTED, labelColors.get(text.get(1).getPosition()));
		assertEquals(BattleNext_UiTheme.TEXT, labelColors.get(text.get(5).getPosition()));
		List<ButtonAPI> checks = components.stream().filter(ButtonAPI.class::isInstance).map(ButtonAPI.class::cast).toList();
		assertEquals(List.of(true, false, true), checks.stream().map(ButtonAPI::isChecked).toList());
		assertTrue(checks.stream().noneMatch(ButtonAPI::isEnabled));
		assertEquals(9, rule.getRestriction("missing").getValue());
		assertFalse(rule.getRestriction("items").isEnabled());
	}

	@Test
	void emptyRulesAndRemovedChoicesKeepTheirIdentityAndReportExactUnavailability() throws Exception {

		BattleNext_RuleStore store = new BattleNext_RuleStore();
		store.reload();
		store.save(new BattleNext_RuleModel("empty", "Empty rule"));
		BattleNext_RuleSourceProvider source = new BattleNext_RuleSourceProvider(store);
		OptionChoice option = source.getOptions().get(0);
		TooltipMakerAPI empty = tooltip(source.getTooltipWidth(), 0f, false);
		source.createTooltip(empty, option);
		assertTrue(tooltips.get(empty).getComponents().stream().filter(LabelAPI.class::isInstance)
				.map(LabelAPI.class::cast).map(LabelAPI::getText).anyMatch((BattleNext_TestTextCatalog.text("Rule", "BattleNext_rule_restrictions") + ": 0")::equals));
		store.delete("empty");
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
		Logger logger = Global.getLogger(BattleNext_Logger.class);
		Level previousLevel = logger.getLevel();
		logger.setLevel(Level.WARN);
		logger.addAppender(appender);
		try {
			TooltipMakerAPI unavailable = tooltip(source.getTooltipWidth(), 0f, false);
			source.createTooltip(unavailable, option);
			assertEquals(List.of("Empty rule", "empty", BattleNext_TestTextCatalog.text("Common", "BattleNext_common_unavailable")),
					tooltips.get(unavailable).getComponents().stream().filter(LabelAPI.class::isInstance)
							.map(LabelAPI.class::cast).map(LabelAPI::getText).toList());
			assertEquals(1, events.size());
			JSONObject event = new JSONObject(events.get(0).getRenderedMessage());
			assertEquals(Level.WARN, events.get(0).getLevel());
			assertEquals("rule.tooltip.unavailable", event.getString("event"));
			assertEquals("empty", event.getJSONObject("fields").getString("rule_id"));
		} finally {
			logger.removeAppender(appender);
			logger.setLevel(previousLevel);
		}
	}
}
