package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentModel.TournamentBracketType;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.view.BattleNext_TournamentTemplate;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import data.test.fixture.BattleNext_TestPageFixture;
import data.test.fixture.BattleNext_TestTextCatalog;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class BattleNext_FleetSourceProviderTest extends BattleNext_TestPageFixture {

	@Test
	void sourceReadsTheCurrentCatalogAndRetainsTextTilesAndSingleStackSelection() {

		List<BattleNext_FleetModel> fleets = new ArrayList<>();
		fleets.add(new BattleNext_FleetModel("zeta", "Second name", List.of(), List.of(), List.of()));
		fleets.add(new BattleNext_FleetModel("alpha", "First name", List.of(), List.of(), List.of()));
		BattleNext_FleetSourceProvider source = new BattleNext_FleetSourceProvider(store(fleets));
		assertEquals(BattleNext_BaseSourceProvider.class, source.getClass().getSuperclass());
		assertEquals(List.of("zeta", "alpha"), source.getOptions().stream().map(OptionChoice::getId).toList());
		assertEquals(List.of("Second name", "First name"), source.getOptions().stream().map(OptionChoice::getLabel).toList());
		assertEquals(1, source.getMaxStack("zeta"));
		assertEquals(360f, source.getTooltipWidth());
		assertFalse(source.expectsIcon());
		assertEquals("", source.getIconPath("zeta"));
		fleets.clear();
		assertTrue(source.getOptions().isEmpty());
		fleets.add(new BattleNext_FleetModel("new", "Updated", List.of(), List.of(), List.of()));
		assertEquals(List.of("new"), source.getOptions().stream().map(OptionChoice::getId).toList());
		assertEquals("Updated", source.getOptions().get(0).getLabel());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void tooltipSharesExactHeaderFontsColorsGapAndBottomInset(boolean deferred) {

		deferTooltipWidth = deferred;
		TooltipMakerAPI tooltip = tooltip(360f, 0f, false);
		String id = "fleet_50%_with_%s";
		String name = "风风雨雨 20% " + "Full fleet name ".repeat(20);
		BattleNext_FleetSourceProvider source = new BattleNext_FleetSourceProvider(store(List.of(
				new BattleNext_FleetModel(id, name, List.of(), List.of(), List.of()))));
		source.createTooltip(tooltip, new OptionChoice(id, name));
		var components = tooltips.get(tooltip).getComponents();
		assertEquals(6, components.size());
		assertEquals(name, ((LabelAPI) components.get(0)).getText());
		assertEquals(id, ((LabelAPI) components.get(1)).getText());
		assertEquals("small-orbitron", labelFonts.get(components.get(0).getPosition()));
		assertEquals(BattleNext_UiTheme.ACCENT, labelColors.get(components.get(0).getPosition()));
		assertEquals("small-insignia", labelFonts.get(components.get(1).getPosition()));
		assertEquals(BattleNext_UiTheme.MUTED, labelColors.get(components.get(1).getPosition()));
		var title = components.get(0).getPosition();
		var identifier = components.get(1).getPosition();
		assertEquals(5f, title.getY() - identifier.getY() - identifier.getHeight());
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_ships") + ": 0 - " + BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_fighter_wings") + ": 0", ((LabelAPI) components.get(2)).getText());
		assertEquals(BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_base_dp") + ": 0 - " + BattleNext_TestTextCatalog.text("Fleet", "BattleNext_fleet_base_fp") + ": 0", ((LabelAPI) components.get(3)).getText());
		assertEquals(8f, components.get(5).getPosition().getHeight());
		assertTrue(title.getHeight() > paragraphLineHeight);
		assertTrue(tooltips.get(tooltip).getUsedHeight() > 50f);
	}

	@Test
	void templateFleetRowsUseSharedPickerAndPreserveSlotsThroughCancelConfirmAndClear() throws Exception {

		List<BattleNext_FleetModel> fleets = List.of(
				new BattleNext_FleetModel("first_50%", "风风雨雨", List.of(), List.of(), List.of()),
				new BattleNext_FleetModel("second", "Raw fleet", List.of(), List.of(), List.of()));
		BattleNext_FleetStore store = store(fleets);
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 900f, new BaseCustomUIPanelPlugin());
		BattleNext_TournamentModel configuration = new BattleNext_TournamentModel("cup", "Cup", TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		BattleNext_TournamentTemplate template = new BattleNext_TournamentTemplate(2);
		template.select(0, "first_50%");
		BattleNext_RuleStore rules = new BattleNext_RuleStore();
		BattleNext_TournamentDisplay display = new BattleNext_TournamentDisplay(router, configuration, null,
				BattleNext_TournamentDisplay.details(configuration, null, template.getFleetIds(), store, rules, "", List.of()), List.of(), action -> {});
		display.enableTemplateEditing(root, new BattleNext_FleetSourceProvider(store), template::getFleetIds, (slot, id) -> {
			template.select(slot, id);
			display.update(configuration, null, BattleNext_TournamentDisplay.details(configuration, null,
					template.getFleetIds(), store, rules, "", List.of()), List.of());
		});
		display.attach(root);
		BattleNext_TournamentView chart = (BattleNext_TournamentView) getField(display, "chart");
		BattleNext_TournamentView.ViewState before = chart.captureState();
		for (String action : List.of("CANCEL", "CONFIRM", "CONFIRM")) {
			List<?> nodes = (List<?>) getField(chart, "nodes");
			BattleNext_TournamentView.Node node = (BattleNext_TournamentView.Node) nodes.get(0);
			float[] point = chart.project(node.x + 120f, node.y + 33f);
			chart.handleInput(List.of(click(point[0], point[1], true), click(point[0], point[1], false)));
			BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(display, "fleetPicker");
			assertInstanceOf(BattleNext_FleetSourceProvider.class, getField(picker, "source"));
			assertSame(root, getField(picker, "overlayHost"));
			CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
			assertNotNull(popup);
			assertTrue(children.get(root).contains(popup));
			BattleNext_TextField search = (BattleNext_TextField) getField(picker, "search");
			search.setText("second");
			plugins.get(popup).advance(0f);
			CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			CustomPanelAPI card = (CustomPanelAPI) tooltips.get(grid).getComponents().get(0);
			TooltipMakerAPI hover = tooltip(360f, 0f, false);
			attachedTooltips.get(card).createTooltip(hover, false, null);
			assertEquals("Raw fleet", ((LabelAPI) tooltips.get(hover).getComponents().get(0)).getText());
			plugins.get(card).processInput(List.of(click(card.getPosition().getCenterX(), card.getPosition().getCenterY(), true)));
			plugins.get(popup).buttonPressed(buttonIds.get(action));
			assertTrue(router.isEmpty());
			assertFalse(router.getSession().hasInputState());
			assertEquals(before, chart.captureState());
		}
		assertEquals(List.of("", ""), template.getFleetIds());
		template.select(0, "missing");
		display.update(configuration, null, BattleNext_TournamentDisplay.details(configuration, null,
				template.getFleetIds(), store, rules, "", List.of()), List.of());
		assertTrue(chart.errors().stream().anyMatch(error -> error.contains("missing")));
		BattleNext_TournamentView.Node node = (BattleNext_TournamentView.Node) ((List<?>) getField(chart, "nodes")).get(0);
		float[] point = chart.project(node.x + 120f, node.y + 33f);
		chart.handleInput(List.of(click(point[0], point[1], true), click(point[0], point[1], false)));
		BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(display, "fleetPicker");
		display.close();
		assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
		assertTrue(router.isEmpty());
		assertFalse(router.getSession().hasInputState());
	}

	private InputEventAPI click(float x, float y, boolean down) {
		AtomicBoolean consumed = new AtomicBoolean();
		return proxy(InputEventAPI.class, (method, arguments) -> switch (method) {
			case "isLMBDownEvent" -> down;
			case "isLMBUpEvent" -> !down;
			case "isMouseDownEvent" -> down;
			case "isMouseEvent" -> true;
			case "getX" -> (int) x;
			case "getY" -> (int) y;
			case "isConsumed" -> consumed.get();
			case "consume" -> {
				consumed.set(true);
				yield null;
			}
			default -> null;
		});
	}

	private BattleNext_FleetStore store(List<BattleNext_FleetModel> fleets) {
		return new BattleNext_FleetStore() {
			@Override
			public BattleNext_FleetModel get(String id) {
				return fleets.stream().filter(fleet -> fleet.getId().equals(id)).findFirst().orElse(null);
			}

			@Override
			public List<BattleNext_FleetModel> getAll() {
				return List.copyOf(fleets);
			}
		};
	}
}
