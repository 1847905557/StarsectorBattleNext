package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.rule.BattleNext_RuleStore;
import data.model.tournament.BattleNext_TournamentStore;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.editor.BattleNext_TournamentEditor;
import data.scripts.ui.widget.BattleNext_OptionField;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.ui.widget.BattleNext_TextField;
import data.test.fixture.BattleNext_TestPageFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
		assertEquals("BattleNext_fleet_ships: 0 - BattleNext_fleet_fighter_wings: 0", ((LabelAPI) components.get(2)).getText());
		assertEquals("BattleNext_fleet_base_dp: 0 - BattleNext_fleet_base_fp: 0", ((LabelAPI) components.get(3)).getText());
		assertEquals(8f, components.get(5).getPosition().getHeight());
		assertTrue(title.getHeight() > paragraphLineHeight);
		assertTrue(tooltips.get(tooltip).getUsedHeight() > 50f);
	}

	@Test
	void tournamentPickerUsesTheExtractedSourceAcrossSearchCancelConfirmAndRefresh() throws Exception {

		List<BattleNext_FleetModel> fleets = List.of(
				new BattleNext_FleetModel("first_50%", "风风雨雨", List.of(), List.of(), List.of()),
				new BattleNext_FleetModel("second", "Raw fleet", List.of(), List.of(), List.of()));
		BattleNext_FleetStore store = store(fleets);
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		CustomPanelAPI root = panel(1000f, 900f, new BaseCustomUIPanelPlugin());
		BattleNext_TournamentEditor editor = new BattleNext_TournamentEditor(
				new BattleNext_TournamentStore(), store, new BattleNext_RuleStore());
		editor.open(null);
		editor.setInputRouter(router);
		editor.attach(root);
		BattleNext_OptionField field = (BattleNext_OptionField) getField(editor, "fleetPicker");
		field.getField().setText("first_50%");
		for (int cycle = 0; cycle < 3; cycle++) {
			BattleNext_OptionPicker picker = (BattleNext_OptionPicker) getField(field, "picker");
			var source = getField(picker, "source");
			assertInstanceOf(BattleNext_FleetSourceProvider.class, source);
			assertSame(store, getField(source, "fleetStore"));
			int registrations = ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size();
			CustomPanelAPI row = (CustomPanelAPI) getField(picker, "row");
			plugins.get(row).buttonPressed(buttonIds.get("TOGGLE"));
			CustomPanelAPI popup = (CustomPanelAPI) getField(picker, "popup");
			BattleNext_TextField search = (BattleNext_TextField) getField(picker, "search");
			search.setText("second");
			plugins.get(popup).advance(0f);
			CustomPanelAPI gridPanel = (CustomPanelAPI) getField(picker, "gridPanel");
			TooltipMakerAPI grid = (TooltipMakerAPI) children.get(gridPanel).get(0);
			assertEquals(1, tooltips.get(grid).getComponents().size());
			var card = tooltips.get(grid).getComponents().get(0);
			assertEquals(TooltipMakerAPI.TooltipLocation.RIGHT, tooltipBindings.get(card).location());
			assertFalse(tooltipBindings.get(card).recreateEveryFrame());
			assertSame(tooltips.get(grid), tooltipBindings.get(card).owner());
			assertTrue(imagePaths.isEmpty());
			TooltipMakerAPI hover = tooltip(360f, 0f, false);
			attachedTooltips.get(card).createTooltip(hover, false, null);
			assertEquals("Raw fleet", ((LabelAPI) tooltips.get(hover).getComponents().get(0)).getText());
			assertEquals("second", ((LabelAPI) tooltips.get(hover).getComponents().get(1)).getText());
			Map<String, Integer> selection = (Map<String, Integer>) getField(picker, "selection");
			selection.clear();
			selection.put("second", 1);
			plugins.get(popup).buttonPressed(buttonIds.get(cycle == 1 ? "CONFIRM" : "CANCEL"));
			assertEquals(cycle == 0 ? "first_50%" : "second", field.getField().getText());
			assertTrue(router.isEmpty());
			assertEquals(registrations, ((Map<?, ?>) getField(router.getSession(), "REGISTERED")).size());
			editor.refreshContent();
			assertTrue(((List<?>) getField(picker, "ownedPanels")).isEmpty());
			field = (BattleNext_OptionField) getField(editor, "fleetPicker");
			assertEquals(cycle == 0 ? "first_50%" : "second", field.getField().getText());
		}
		field.close();
		router.clear();
		assertTrue(((List<?>) getField(field, "ownedPanels")).isEmpty());
		assertFalse(router.getSession().hasInputState());
	}

	@Test
	void tournamentEditorBindsTheStandaloneProviderAsItsFormalSource() throws Exception {
		JavaParser parser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17).setCharacterEncoding(StandardCharsets.UTF_8));
		var parsed = parser.parse(Path.of("src/data/scripts/ui/editor/BattleNext_TournamentEditor.java"));
		assertTrue(parsed.isSuccessful(), parsed.getProblems().toString());
		var unit = parsed.getResult().orElseThrow();
		assertEquals(List.of("BattleNext_TournamentEditor"), unit.findAll(ClassOrInterfaceDeclaration.class)
				.stream().map(ClassOrInterfaceDeclaration::getNameAsString).toList());
		assertEquals(1, unit.findAll(ObjectCreationExpr.class).stream()
				.filter(expression -> expression.getType().getNameAsString().equals("BattleNext_FleetSourceProvider")).count());
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
