package data.scripts.ui.widget.view;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.model.BattleNext_BaseStore;
import data.model.fleet.BattleNext_FleetModel;
import data.model.fleet.BattleNext_FleetStore;
import data.model.report.BattleNext_BattleReportModel;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournament.ExecutionKind;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.page.BattleNext_BattleSelectorPage;
import data.scripts.ui.widget.BattleNext_TournamentDisplay;
import data.scripts.ui.widget.provider.BattleNext_FleetSourceProvider;
import data.test.fixture.BattleNext_TestTextCatalog;
import data.test.fixture.BattleNext_TestTournamentViewFixture;
import data.test.fixture.BattleNext_TestTournamentViewFixture.*;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;

class BattleNext_TournamentViewTest {
	private final BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
	private final Map<BattleNext_TournamentModel, BattleNext_StartedTournament> instances = new HashMap<>();
	private final Map<BattleNext_TournamentModel, List<String>> participants = new HashMap<>();
	private SettingsAPI previousSettings;

	@BeforeEach
	void configureSettings() throws Exception {
		previousSettings = Global.getSettings();
		Global.setSettings(BattleNext_TestTournamentViewFixture.settings());
	}

	@AfterEach
	void restoreSettings() {
		Global.setSettings(previousSettings);
	}

	@Test
	void allFormatsMapInitialProgressAndCompletedResultsWithoutMutatingTheModel() throws Exception {

		for (int count : new int[]{2, 3, 5, 32}) {
			for (BattleNext_TournamentModel.TournamentBracketType type
					: BattleNext_TournamentModel.TournamentBracketType.values()) {
				BattleNext_TournamentModel tournament = tournament(count, type, 1);
				BattleNext_TournamentView before = view(tournament);
				String initial = tournament.toJson().toString();
				start(tournament);
				for (BattleNext_Match match : instances.get(tournament).getMatches()) {
					BattleNext_TournamentView.Node predicted = node(before, match.getId());
					assertEquals(match.getPlayer(), predicted.player.fleetId, type + " " + count);
					assertEquals(match.getEnemy(), predicted.enemy.fleetId, type + " " + count);
					assertEquals(match.getStage(), predicted.stage);
				}
				int played = 0;
				while (!instances.get(tournament).isCompleted() && (count <= 5 || played < 2)) {
					if (played < 2) {
						assertRecordMapping(tournament, view(tournament));
					}
					BattleNext_Match next = engine.getNextMatch(instances.get(tournament));
					assertNotNull(next);
					engine.recordRound(tournament, instances.get(tournament), next.getId(), played % 2 == 0
							? next.getPlayer() : next.getEnemy(), "");
					played++;
					assertTrue(played <= count * (count - 1) / 2 + 2 * count);
				}
				assertRecordMapping(tournament, view(tournament));
				assertEquals(initial, tournament.toJson().toString());
				if (count <= 5) {
					assertEquals(instances.get(tournament).getPlayableMatchTotal(tournament),
							BattleNext_TournamentDisplay.completedMatches(instances.get(tournament)));
				}
			}
		}
	}

	private void assertRecordMapping(BattleNext_TournamentModel tournament,
			BattleNext_TournamentView chart) throws Exception {

		String json = tournament.toJson().toString();
		Set<String> ids = new HashSet<>();
		for (BattleNext_TournamentView.Node node : chart.nodes) {
			if (node.id.isEmpty()) continue;

			ids.add(node.id);
			if (node.match == null) continue;

			BattleNext_Match match = instances.get(tournament).findMatch(node.id);
			assertSame(match, node.match);
			assertEquals(match.getStage(), node.stage);
			assertEquals(new HashSet<>(List.of(match.getPlayer(), match.getEnemy())),
					new HashSet<>(List.of(node.player.fleetId, node.enemy.fleetId)));
			Set<String> dependencies = new HashSet<>();
			if (!node.player.sourceId.isEmpty()) {
				dependencies.add(node.player.sourceId);
			}
			if (!node.enemy.sourceId.isEmpty()) {
				dependencies.add(node.enemy.sourceId);
			}
			assertEquals(new HashSet<>(match.getDependencies()), dependencies, node.id);
			for (BattleNext_TournamentView.Participant participant : List.of(node.player, node.enemy)) {
				if (participant.sourceId.isEmpty()) continue;

				BattleNext_Match previous = instances.get(tournament).findMatch(participant.sourceId);
				assertNotNull(previous);
				assertEquals(previous.getStatus(), BattleNext_Match.MatchStatus.COMPLETE);
				assertEquals(participant.loser ? previous.getLoserFleetId() : previous.getWinnerFleetId(),
						participant.fleetId, node.id + " from " + participant.sourceId);
			}
			assertFalse(chart.tooltipText(node).contains("[NULL]"));
		}
		for (BattleNext_Match match : instances.get(tournament).getMatches()) {
			assertTrue(ids.contains(match.getId()));
		}
		for (BattleNext_TournamentView.Edge edge : chart.edges) {
			assertTrue(ids.contains(edge.from), edge.from);
			assertTrue(ids.contains(edge.to), edge.to);
		}
		assertTrue(chart.errors().isEmpty(), chart.errors().toString());
		assertEquals(json, tournament.toJson().toString());
	}

	@Test
	void predictionsAreReadOnlyAndWinnersAreCentered() throws Exception {
		for (int count : new int[]{2, 3, 5, 32}) {
			BattleNext_TournamentModel tournament = tournament(count,
					BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3);
			String json = tournament.toJson().toString();
			BattleNext_TournamentView chart = view(tournament);
			for (BattleNext_TournamentView.Node node : chart.nodes) {
				assertNull(node.match);
				assertTrue(chart.tooltipText(node).contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_predicted")));
				if (node.player.sourceId.isEmpty()) continue;

				float first = node(chart, node.player.sourceId).y;
				float second = node(chart, node.enemy.sourceId).y;
				assertEquals((first + second) * 0.5f, node.y, 0.001f);
			}
			assertEquals(json, tournament.toJson().toString());
		}
	}

	@Test
	void roundRobinMatrixMirrorsEveryUnorderedPairAndItsSwappedScores() {
		BattleNext_TournamentModel large = tournament(32,
				BattleNext_TournamentModel.TournamentBracketType.ROUND_ROBIN, 3);
		BattleNext_TournamentView matrix = view(large);
		int count = participants.get(large).size();
		assertEquals(count * (count - 1), matrix.nodes.stream().filter(node -> !node.id.isEmpty()).count());
		assertEquals(count * (count - 1) / 2, matrix.nodes.stream().filter(node -> !node.id.isEmpty())
				.map(node -> node.id).distinct().count());
		BattleNext_TournamentModel cup = tournament(2,
				BattleNext_TournamentModel.TournamentBracketType.ROUND_ROBIN, 3);
		start(cup);
		engine.recordRound(cup, instances.get(cup), "RR-0-1", "F1", "");
		BattleNext_TournamentView chart = view(cup);
		List<BattleNext_TournamentView.Node> cells = chart.nodes.stream()
				.filter(node -> node.id.equals("RR-0-1")).toList();
		assertEquals("F1", cells.get(0).player.fleetId);
		assertEquals("F2", cells.get(1).player.fleetId);
		assertEquals("1 - 0", chart.scoreText(cells.get(0)));
		assertEquals("0 - 1", chart.scoreText(cells.get(1)));
	}

	@Test
	void doubleEliminationResetAndByeSourcesAgreeWithEngine() {
		BattleNext_TournamentModel cup = tournament(2,
				BattleNext_TournamentModel.TournamentBracketType.DOUBLE_ELIMINATION, 1);
		BattleNext_TournamentView predicted = view(cup);
		assertEquals(BattleNext_Match.MatchStage.BYE, node(predicted, "L1-1").stage);
		assertTrue(predicted.tooltipText(node(predicted, "GF_RESET")).contains("Required if"));
		start(cup);
		engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
		engine.recordRound(cup, instances.get(cup), "GF", "F2", "");
		BattleNext_TournamentView chart = view(cup);
		assertEquals("F1", node(chart, "GF_RESET").player.fleetId);
		assertEquals("F2", node(chart, "GF_RESET").enemy.fleetId);
		engine.recordRound(cup, instances.get(cup), "GF_RESET", "F1", "");
		assertEquals(3, BattleNext_TournamentDisplay.completedMatches(instances.get(cup)));
		assertEquals("F1", instances.get(cup).getChampionFleetId());
	}

	@Test
	void tooltipIncludesFullNamesDrawsReportsMapAndSources() {
		BattleNext_TournamentModel cup = tournament(2,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3);
		start(cup);
		BattleNext_BattleReportModel report = new BattleNext_BattleReportModel();
		report.setId("report-1");
		engine.recordRound(cup, instances.get(cup), "W1-1", "draw", report.getId());
		engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
		String longName = "A long fleet name with full text and symbols";
		BattleNext_TournamentView chart = BattleNext_TournamentView.createView(cup, instances.get(cup), participants.get(cup),
				Map.of("F1", longName, "F2", "Second fleet"));
		String tooltip = chart.tooltipText(node(chart, "W1-1"));
		chart.setReportIds(Set.of("report-1"));
		tooltip = chart.tooltipText(node(chart, "W1-1"));
		assertTrue(tooltip.contains(longName));
		assertTrue(tooltip.contains("[F1]"));
		assertTrue(tooltip.contains("Round 1: Draw / Reports: Available"));
		assertTrue(tooltip.contains("Round 2: " + longName + " [F1] / Reports: Unavailable"));
		assertTrue(tooltip.contains(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_scale_label") + ": 1"));
		assertTrue(tooltip.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_score") + ": 0 - 1"));
		assertEquals(22, BattleNext_TournamentView.shorten(longName, 22).length());
		assertEquals("abc", BattleNext_TournamentView.shorten("abc", 22));
		assertFalse(BattleNext_TournamentView.shorten("abcdefghij\uD83D\uDE00abcdefghij", 14)
				.endsWith("\uD83D..."));
	}

	@Test
	void missingFleetAndInvalidConfigurationProduceLocalizedVisibleErrors() {
		BattleNext_TournamentModel cup = tournament(4,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1);
		BattleNext_TournamentView chart = BattleNext_TournamentView.createView(cup, instances.get(cup), participants.get(cup), Map.of("F1", "First"));
		assertFalse(chart.errors().isEmpty());
		assertTrue(chart.tooltipText(node(chart, "W1-1")).contains(String.format(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_missing_fleet"), "F4")));
		assertEquals(3, chart.nodes.size());
		cup.setBracketType(null);
		assertTrue(view(cup).errors().get(0).contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_missing_bracket")));
	}

	@Test
	void invalidLayoutRetainsRawRecordsAndSingleNodeErrorsRemainLocal() {
		BattleNext_TournamentModel cup = tournament(4,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1);
		BattleNext_Match raw = new BattleNext_Match("unmapped", "F1", "F2", 3, 2.5f);
		instances.put(cup, new BattleNext_StartedTournament(UUID.randomUUID().toString(), cup.getId(), ExecutionKind.MANUAL, participants.get(cup)));
		instances.get(cup).addGeneratedMatch(raw);
		BattleNext_TournamentView chart = view(cup);
		assertEquals(4, chart.nodes.size());
		assertSame(raw, node(chart, "unmapped").match);
		assertTrue(chart.errors().get(0).contains("Unexpected match ID"));
		assertTrue(chart.tooltipText(node(chart, "unmapped")).contains(BattleNext_TestTextCatalog.text("Battle", "BattleNext_battle_map_scale_label") + ": 2.5"));
		cup.setBracketType(null);
		BattleNext_TournamentView invalid = view(cup);
		assertSame(raw, node(invalid, "unmapped").match);
		assertTrue(invalid.errors().size() >= 2);
	}

	@Test
	@SuppressWarnings("unchecked")
	void selectedTournamentBuildsChartAndSummaryEvenWhenLayoutIsInvalid() throws Exception {

		Harness harness = new Harness();
		harness.getChartBounds().getApi().setSize(700f, 700f);
		BattleNext_BattleSelectorPage page = new BattleNext_BattleSelectorPage(() -> {

		}, new BaseCustomUIPanelPlugin());
		BattleNext_TournamentModel cup = tournament(4,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3);
		Field selected = page.getClass().getDeclaredField("selectedId");
		selected.setAccessible(true);
		selected.set(page, "cup");
		for (String storeName : List.of("tournamentStore", "fleetStore", "ruleStore")) {
			Field field = page.getClass().getDeclaredField(storeName);
			field.setAccessible(true);
			Object store = field.get(page);
			Field entries = BattleNext_BaseStore.class.getDeclaredField("entries");
			entries.setAccessible(true);
			Map<String, Object> values = (Map<String, Object>) entries.get(store);
			if (storeName.equals("tournamentStore")) {
				values.put("cup", cup);
			} else if (storeName.equals("fleetStore")) {
				for (String id : participants.get(cup)) {
					values.put(id, new BattleNext_FleetModel(id, "Fleet " + id,
							List.of(), List.of(), List.of()));
				}
			}
			Field error = BattleNext_BaseStore.class.getDeclaredField("loadError");
			error.setAccessible(true);
			error.set(store, "");
		}
		Method build = page.getClass().getDeclaredMethod("buildTournamentDetails", CustomPanelAPI.class);
		build.setAccessible(true);
		CustomPanelAPI detailHost = harness.detailHost(700f, 700f);
		String initial = cup.toJson().toString();
		build.invoke(page, detailHost);
		assertTrue(harness.getParagraphs().contains(String.format(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_finished_matches"), 0, 0)));
		assertFalse(harness.getParagraphs().contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_select_fleet")));
		assertTrue(harness.isScrollingHostCreated());
		assertTrue(harness.isCustomChartAdded());
		assertTrue(harness.getReservedChartHeight() > 0f);
		assertTrue(harness.isSummaryAdded());
		assertTrue(harness.getButtonIds().contains("tournament:create"));
		assertFalse(harness.getButtonIds().contains("tournament:active"));
		assertFalse(harness.getButtons().get(harness.getButtonIds().indexOf("tournament:create")).isEnabled());
		assertTrue(harness.getButtons().get(harness.getButtonIds().indexOf("tournament:check")).isEnabled());
		assertEquals(initial, cup.toJson().toString());
		cup.setBracketType(null);
		harness.getChartBounds().getApi().setSize(700f, 700f);
		harness.getParagraphs().clear();
		build.invoke(page, detailHost);
		assertTrue(harness.getParagraphs().stream().anyMatch(text -> text.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_error"))));
		assertTrue(harness.getParagraphs().stream().anyMatch(text -> text.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_unknown_bracket"))));
		assertFalse(harness.getParagraphs().contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_select_fleet")));
		Field chart = page.getClass().getDeclaredField("tournamentDisplay");
		chart.setAccessible(true);
		assertNotNull(chart.get(page));
		page.closeChart();
		assertNull(chart.get(page));
	}

	@Test
	void fitCameraPointerZoomPanAndClipUseOneCoordinateSystem() {
		BattleNext_TournamentView chart = view(tournament(32,
				BattleNext_TournamentModel.TournamentBracketType.KING_OF_THE_HILL, 1));
		for (BattleNext_TournamentView.Node node : chart.nodes) {
			float[] start = chart.project(node.x, node.y);
			float[] end = chart.project(node.x + node.width, node.y + node.height);
			assertTrue(start[0] >= 0f);
			assertTrue(end[0] <= BattleNext_TournamentView.CANVAS_WIDTH);
			assertTrue(start[1] <= BattleNext_TournamentView.CANVAS_HEIGHT - 40f);
			assertTrue(end[1] >= 0f);
		}
		chart.resetZoom();
		float[] anchor = chart.project(100f, 100f);
		chart.zoomAt(anchor[0], BattleNext_TournamentView.CANVAS_HEIGHT - anchor[1], 3);
		assertArrayEquals(anchor, chart.project(100f, 100f), 0.001f);
		chart.panBy(30f, 40f);
		assertArrayEquals(new float[]{anchor[0] + 30f, anchor[1] - 40f}, chart.project(100f, 100f), 0.001f);
		chart.resetZoom();
		BattleNext_TournamentView.Node node = chart.nodes.get(0);
		float[] center = chart.project(node.x + node.width * 0.5f, node.y + node.height * 0.5f);
		assertSame(node, chart.hitTest(center[0], center[1]));
		assertNull(chart.hitTest(-1f, -1f));
		assertArrayEquals(new float[]{10f, 30f, 90f, 70f},
				BattleNext_TournamentView.intersection(0f, 0f, 100f, 100f, 10f, 30f, 200f, 90f));
		assertArrayEquals(new float[]{200f, 0f, 0f, 100f},
				BattleNext_TournamentView.intersection(0f, 0f, 100f, 100f, 200f, 0f, 100f, 100f));
	}

	@Test
	void attachedChartRoutesWheelDragTooltipButtonsAndModalScope() {

		Harness harness = new Harness();
		BattleNext_TournamentView chart = view(tournament(4,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1));
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), router, 700f);
		assertEquals(700f, harness.getChartBounds().getWidth());
		assertEquals(700f * BattleNext_TournamentView.CANVAS_HEIGHT / BattleNext_TournamentView.CANVAS_WIDTH,
				harness.getChartBounds().getHeight());
		chart.resetZoom();
		BattleNext_TournamentView.Node node = chart.nodes.get(0);
		float[] center = chart.project(node.x + node.width * 0.5f, node.y + node.height * 0.5f);
		Event hover = new Event("move", center[0], center[1], 0);
		chart.handleInput(List.of(hover.api));
		assertTrue(harness.getTarget().getHeight() > 0f);
		assertNotNull(harness.getTooltip());
		harness.getTooltip().createTooltip(harness.getHost(), false, null);
		assertTrue(harness.getParagraphs().stream().anyMatch(text -> text.contains(node.id)
				&& text.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_predicted"))));
		chart.zoomAt(300f, 300f, 2);
		assertEquals(0f, harness.getTarget().getHeight());
		Event wheel = new Event("wheel", 400f, 100f, 1);
		chart.handleInput(List.of(wheel.api));
		assertTrue(wheel.consumed);
		Event outside = new Event("wheel", 900f, 220f, 1);
		chart.handleInput(List.of(outside.api));
		assertFalse(outside.consumed);
		Event toolbar = new Event("down", 630f, harness.getChartBounds().getHeight() - 10f, 0);
		chart.handleInput(List.of(toolbar.api));
		assertFalse(toolbar.consumed);
		float[] before = chart.project(100f, 100f);
		Event down = new Event("down", 300f, 100f, 0);
		Event move = new Event("move", 320f, 120f, 0);
		Event up = new Event("up", 320f, 120f, 0);
		chart.handleInput(List.of(down.api, move.api, up.api));
		assertTrue(down.consumed && move.consumed && up.consumed);
		assertArrayEquals(new float[]{before[0] + 20f, before[1] + 20f}, chart.project(100f, 100f), 0.001f);
		harness.getButtons().get(1).getApi().setChecked(true);
		chart.advance(0f);
		assertFalse(harness.getButtons().get(1).isChecked());
		assertEquals(124f * 700f / BattleNext_TournamentView.CANVAS_WIDTH,
				chart.project(100f, 100f)[0], 0.001f);
		router.push(new BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return harness.getViewport().getApi();
			}

			@Override
			public boolean handleGlobalInput(List<InputEventAPI> events) {
				return false;
			}

			@Override
			public void close() {

			}
		});
		Event blocked = new Event("wheel", 400f, 220f, 1);
		chart.handleInput(List.of(blocked.api));
		assertFalse(blocked.consumed);
		chart.advance(0f);
		assertFalse(harness.getButtons().get(0).isEnabled());
		assertEquals(0f, harness.getTarget().getHeight());
	}

	@Test
	void rawWheelValuesUseGentleDirectionalZoomAndKeepPointerAnchor() {

		Harness harness = new Harness();
		BattleNext_TournamentView chart = view(tournament(4,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1));
		chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), new BattleNext_UiInputRouter(), 700f);
		for (int value : new int[]{-240, -120, -1, 0, 1, 120, 240}) {
			chart.resetZoom();
			float[] anchor = chart.project(400f, 200f);
			float[] before = chart.project(500f, 200f);
			Event wheel = new Event("wheel", anchor[0], anchor[1], value);
			chart.handleInput(List.of(wheel.api));
			float[] after = chart.project(500f, 200f);
			float factor = (float) Math.pow(1.05f, Integer.signum(value));

			assertTrue(wheel.consumed);
			float[] movedAnchor = chart.project(400f, 200f);

			assertEquals((int) anchor[0] + (anchor[0] - (int) anchor[0]) * factor, movedAnchor[0], 0.001f);
			assertEquals((int) anchor[1] + (anchor[1] - (int) anchor[1]) * factor, movedAnchor[1], 0.001f);
			assertEquals((before[0] - (int) anchor[0]) * factor, after[0] - (int) anchor[0], 0.001f);
		}
	}

	@Test
	void frameIsCenteredAcrossWidthsAndOffsetInputKeepsCameraCoordinates() {

		for (float availableWidth : new float[]{600f, 1000f, 1600f}) {
			Harness harness = new Harness();
			harness.getViewport().getApi().setLocation(100f, 80f).setSize(availableWidth, 600f);
			harness.getChartBounds().getApi().setLocation(100f, 80f);
			BattleNext_TournamentView chart = view(tournament(2,
					BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1));
			chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), new BattleNext_UiInputRouter(), availableWidth);
			float width = Math.min(availableWidth, BattleNext_TournamentView.CANVAS_WIDTH);
			float left = harness.getChartBounds().getX() - harness.getViewport().getX();
			float right = harness.getViewport().getX() + availableWidth - harness.getChartBounds().getX() - width;

			assertEquals(width, harness.getChartBounds().getWidth());
			assertEquals(availableWidth, harness.getRowBounds().getWidth());
			assertEquals(width * BattleNext_TournamentView.CANVAS_HEIGHT / BattleNext_TournamentView.CANVAS_WIDTH,
					harness.getChartBounds().getHeight(), 0.001f);
			assertEquals(left, right, 0.001f);
			assertEquals(80f, harness.getChartBounds().getY());
			assertEquals(harness.getChartBounds().getHeight() + 10f, harness.getReservedChartHeight());
			BattleNext_TournamentView.Node node = chart.nodes.get(0);
			float logicalX = node.x + node.width * 0.5f;
			float logicalY = node.y + node.height * 0.5f;
			float[] center = chart.project(logicalX, logicalY);
			chart.handleInput(List.of(new Event("move", center[0], center[1], 0).api));
			assertSame(node, chart.hitTest(center[0], center[1]));
			assertTrue(harness.getTarget().getHeight() > 0f);
			Event wheel = new Event("wheel", center[0], center[1], -120);
			float scale = width / BattleNext_TournamentView.CANVAS_WIDTH;
			float anchorX = logicalX + ((int) center[0] - center[0]) / scale;
			float anchorY = logicalY - ((int) center[1] - center[1]) / scale;

			chart.handleInput(List.of(wheel.api));
			assertTrue(wheel.consumed);
			assertArrayEquals(new float[]{(int) center[0], (int) center[1]},
					chart.project(anchorX, anchorY), 0.001f);
			float[] movedCenter = chart.project(logicalX, logicalY);
			Event down = new Event("down", center[0], center[1], 0);
			Event move = new Event("move", center[0] + 20f, center[1] + 20f, 0);
			Event up = new Event("up", center[0] + 20f, center[1] + 20f, 0);
			chart.handleInput(List.of(down.api, move.api, up.api));
			assertTrue(down.consumed && move.consumed && up.consumed);
			assertArrayEquals(new float[]{movedCenter[0] + 20f, movedCenter[1] + 20f},
					chart.project(logicalX, logicalY), 0.001f);
			assertEquals(left, harness.getChartBounds().getX() - harness.getViewport().getX(), 0.001f);
			Event outside = new Event("wheel", harness.getChartBounds().getX() - 1f, center[1], -120);
			chart.handleInput(List.of(outside.api));
			assertFalse(outside.consumed);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	void summaryColumnsAlignWithChartAndReserveTallestContent(int state) throws Exception {

		for (int[] scenario : new int[][]{{400, 32}, {700, 2}, {1600, 32}}) {
			float availableWidth = scenario[0];
			int count = scenario[1];
			Harness harness = new Harness();
			harness.setCustomChartAdded(true);
			BattleNext_TournamentModel cup = tournament(count,
					BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 3);
			if (state > 0) {
				start(cup);
			}
			if (state == 2) {
				while (!instances.get(cup).isCompleted()) {
					BattleNext_Match match = engine.getNextMatch(instances.get(cup));
					engine.recordRound(cup, instances.get(cup), match.getId(), match.getPlayer(), "");
				}
			}
			Map<String, String> fleets = new HashMap<>();
			String longName = "A full fleet name that needs multiple lines in a narrow column ".repeat(3).trim();
			for (String id : participants.get(cup)) {
				fleets.put(id, longName);
			}
			float width = Math.min(availableWidth, BattleNext_TournamentView.CANVAS_WIDTH);
			BattleNext_TournamentDisplay.Details data = new BattleNext_TournamentDisplay.Details(fleets, true,
					state == 1 ? engine.getNextMatch(instances.get(cup)) : null,
					"A detailed warning about tournament configuration. ".repeat(count), List.of());
			BattleNext_TournamentDisplay display = new BattleNext_TournamentDisplay(new BattleNext_UiInputRouter(),
					cup, instances.get(cup), data, state == 0 ? List.of(
							new BattleNext_TournamentDisplay.Action(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_create"), "tournament:create", true, List.of(), "create"),
							new BattleNext_TournamentDisplay.Action(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_check_fleets"), "tournament:check", true, List.of(), "create")) : List.of(), ignored -> {

					});
			if (state == 0) {
				display.enableTemplateEditing(harness.getPanel(), new BattleNext_FleetSourceProvider(new BattleNext_FleetStore()),
						() -> this.participants.get(cup), (slot, id) -> {});
			}
			Field chart = display.getClass().getDeclaredField("chart");
			chart.setAccessible(true);
			chart.set(display, view(cup));
			Method build = display.getClass().getDeclaredMethod("addSummary",
					CustomPanelAPI.class, TooltipMakerAPI.class, float.class, float.class);
			build.setAccessible(true);
			String json = cup.toJson().toString();
			build.invoke(display, harness.getPanel(), harness.getHost(), availableWidth, width);
			Harness.TextBlock title = harness.getSummaryBlocks().get(0);
			Harness.TextBlock details = harness.getSummaryBlocks().get(1);
			Harness.TextBlock participants = harness.getSummaryBlocks().get(2);
			Harness.TextBlock fleetTitle = harness.getSummaryBlocks().get(3);

			assertEquals(width, harness.getSummaryBounds().getWidth());
			assertEquals((availableWidth - width) * 0.5f, harness.getSummaryBounds().getLeftPad());
			assertEquals(0f, title.getBounds().getLeftPad());
			assertEquals(details.getBounds().getWidth(), title.getBounds().getWidth());
			assertEquals(participants.getBounds().getWidth(), fleetTitle.getBounds().getWidth());
			assertEquals(title.getBounds().getTopPad(), fleetTitle.getBounds().getTopPad());
			assertEquals(participants.getBounds().getLeftPad(), fleetTitle.getBounds().getLeftPad());
			assertEquals(List.of(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_summary")), title.getTitles());
			assertEquals(List.of(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_participant_fleets")), fleetTitle.getTitles());
			assertEquals(details.getBounds().getWidth(), participants.getBounds().getWidth());
			assertEquals(10f, participants.getBounds().getLeftPad() - details.getBounds().getWidth());
			assertEquals(details.getBounds().getTopPad(), participants.getBounds().getTopPad());
			assertEquals(Math.max(title.getHeight(), fleetTitle.getHeight()) + 10f, details.getBounds().getTopPad());
			assertEquals(details.getBounds().getTopPad() + Math.max(details.getHeight(), participants.getHeight()), harness.getSummaryBounds().getHeight());
			assertEquals(harness.getSummaryBounds().getHeight() + 10f, harness.getContentHeight());
			assertEquals(count, participants.getText().size());
			assertTrue(participants.getText().contains(longName));
			assertTrue(details.getText().stream().anyMatch(value -> value.startsWith(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_status") + ":")));
			assertTrue(details.getText().stream().anyMatch(value -> value.startsWith("A detailed warning")));
			assertEquals(state == 0, harness.getButtonIds().contains("tournament:create"));
			for (TestButton button : harness.getButtons()) {
				assertEquals(Math.min(180f, (details.getBounds().getWidth() - 4f) * 0.5f), button.getBounds().getWidth());
				assertEquals(30f, button.getBounds().getHeight());
			}
			if (state == 0) {
				assertEquals(harness.getButtons().get(0).getBounds().getTopPad(), harness.getButtons().get(1).getBounds().getTopPad());
				assertEquals(harness.getButtons().get(0).getBounds().getLeftPad() + harness.getButtons().get(0).getBounds().getWidth() + 4f, harness.getButtons().get(1).getBounds().getLeftPad());
			}
			if (state == 2) {
				assertTrue(details.getText().stream().anyMatch(value -> value.startsWith(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_winner") + ":")));
			}
			assertEquals(json, cup.toJson().toString());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void displayRoutesActionsAndReleasesInputOnClose(boolean started) throws Exception {

		Harness harness = new Harness();
		harness.getChartBounds().getApi().setSize(700f, 700f);
		BattleNext_TournamentModel cup = tournament(2,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1);
		if (started) {
			start(cup);
		}
		List<Object> actions = new ArrayList<>();
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		BattleNext_TournamentDisplay.Details data = new BattleNext_TournamentDisplay.Details(
				Map.of("F1", "First", "F2", "Second"), true, started ? engine.getNextMatch(instances.get(cup)) : null,
				"", List.of("Visible warning"));
		BattleNext_TournamentDisplay.Action operation = new BattleNext_TournamentDisplay.Action("Action",
				started ? "tournament:authorize" : "tournament:start", true, List.of());
		BattleNext_TournamentDisplay display = new BattleNext_TournamentDisplay(router, cup, instances.get(cup), data, List.of(operation), actions::add);
		String json = cup.toJson().toString();
		display.attach(harness.getPanel());
		CustomUIPanelPlugin plugin = harness.getDisplayPlugin();
		String action = started ? "tournament:authorize" : "tournament:start";

		assertTrue(harness.isScrollingHostCreated());
		assertTrue(harness.isCustomChartAdded() && harness.isSummaryAdded());
		assertTrue(harness.getParagraphs().contains("Visible warning"));
		assertTrue(harness.getButtonIds().contains(action));
		plugin.buttonPressed("unrelated");
		assertTrue(actions.isEmpty());
		plugin.buttonPressed(action);
		assertEquals(List.of(action), actions);
		Field chartField = display.getClass().getDeclaredField("chart");
		chartField.setAccessible(true);
		BattleNext_TournamentView chart = (BattleNext_TournamentView) chartField.get(display);
		BattleNext_TournamentView.Node node = chart.nodes.get(0);
		float[] center = chart.project(node.x + node.width * 0.5f, node.y + node.height * 0.5f);
		Event hover = new Event("move", center[0], center[1], 0);
		display.handleGlobalInput(List.of(hover.api));
		assertTrue(harness.getTarget().getHeight() > 0f);
		Event down = new Event("down", center[0], center[1], 0);
		display.handleGlobalInput(List.of(down.api));
		assertTrue(down.consumed);
		BattleNext_UiInputRouter.Overlay overlay = new BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return harness.getViewport().getApi();
			}

			@Override
			public boolean handleGlobalInput(List<InputEventAPI> events) {
				return false;
			}

			@Override
			public void close() {

			}
		};
		router.push(overlay);
		plugin.buttonPressed(action);
		assertEquals(1, actions.size());
		Event blocked = new Event("wheel", center[0], center[1], -120);
		display.handleGlobalInput(List.of(blocked.api));
		assertFalse(blocked.consumed);
		assertEquals(0f, harness.getTarget().getHeight());
		router.pop(overlay);
		display.close();
		assertNull(chartField.get(display));
		plugin.buttonPressed(action);
		assertEquals(1, actions.size());
		Event afterClose = new Event("wheel", center[0], center[1], -120);
		display.handleGlobalInput(List.of(afterClose.api));
		assertFalse(afterClose.consumed);
		assertEquals(json, cup.toJson().toString());
	}

	@Test
	void displayCopiesPresentationInputsAndKeepsDisabledActionsInactive() {

		Map<String, String> names = new HashMap<>(Map.of("F1", "First", "F2", "Second"));
		List<String> warnings = new ArrayList<>(List.of("Warning"));
		BattleNext_TournamentDisplay.Details data = new BattleNext_TournamentDisplay.Details(
				names, false, null, "", warnings);
		names.clear();
		warnings.clear();
		assertEquals(2, data.fleetNames().size());
		assertEquals(List.of("Warning"), data.warnings());
		Harness harness = new Harness();
		harness.getChartBounds().getApi().setSize(700f, 700f);
		List<Object> actions = new ArrayList<>();
		BattleNext_TournamentDisplay display = new BattleNext_TournamentDisplay(new BattleNext_UiInputRouter(),
				tournament(2, BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1), null, data,
				List.of(new BattleNext_TournamentDisplay.Action("Start", "tournament:start", false, List.of())), actions::add);
		display.attach(harness.getPanel());
		assertFalse(harness.getButtons().get(harness.getButtons().size() - 1).isEnabled());
		harness.getDisplayPlugin().buttonPressed("tournament:start");
		harness.getDisplayPlugin().buttonPressed("tournament:authorize");
		assertTrue(actions.isEmpty());
	}

	private BattleNext_TournamentView.Node node(BattleNext_TournamentView view, String id) {
		return view.nodes.stream().filter(node -> node.id.equals(id)).findFirst().orElseThrow();
	}

	private BattleNext_TournamentView view(BattleNext_TournamentModel tournament) {
		Map<String, String> names = new HashMap<>();
		for (String id : participants.get(tournament)) {
			names.put(id, "Fleet " + id);
		}
		return BattleNext_TournamentView.createView(tournament, instances.get(tournament), participants.get(tournament), names);
	}

		private void start(BattleNext_TournamentModel configuration) {
		instances.put(configuration, engine.start(configuration, participants.get(configuration), ExecutionKind.MANUAL));
	}

	private BattleNext_TournamentModel tournament(int count,
			BattleNext_TournamentModel.TournamentBracketType type, int bo) {
		BattleNext_TournamentModel cup = new BattleNext_TournamentModel("cup", "Cup", type, bo, count);
		List<String> fleets = new ArrayList<>();
		for (int index = 1; index <= count; index++) {
			fleets.add("F" + index);
		}
		participants.put(cup, fleets);
		return cup;
	}

	private <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> callback) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> callback.apply(method.getName(), args)));
	}

	@Test
	void chartRestoresCanvasAndReservesSummarySpaceAfterChildLayout() {
		Harness harness = new Harness();
		harness.setShrinkChildren(true);
		BattleNext_TournamentView chart = view(tournament(2,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1));
		chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), new BattleNext_UiInputRouter(), 700f);
		assertEquals(700f, harness.getChartBounds().getWidth());
		float height = 700f * BattleNext_TournamentView.CANVAS_HEIGHT / BattleNext_TournamentView.CANVAS_WIDTH;

		assertEquals(height, harness.getChartBounds().getHeight());
		assertTrue(harness.getContentHeight() >= height + 10f);
		assertEquals(height + 10f, harness.getReservedChartHeight());
		BattleNext_TournamentView.Node node = chart.nodes.get(0);
		float[] center = chart.project(node.x + node.width * 0.5f, node.y + node.height * 0.5f);
		assertTrue(center[0] > 100f && center[0] < 600f);
		assertTrue(center[1] > 40f && center[1] < 340f);
		assertSame(node, chart.hitTest(center[0], center[1]));
	}

	@Test
	void threeLineNodesMeasureScoresPreserveNamesAndReuseLabelsDuringProgressUpdates() {
		BattleNext_TournamentModel cup = tournament(2, BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 25);
		String longName = "舰队 50% " + "很长的舰队名称".repeat(30);
		Map<String, String> names = Map.of("F1", longName, "F2", "Other fleet");
		BattleNext_TournamentView chart = BattleNext_TournamentView.createView(cup, null, participants.get(cup), names);
		Harness harness = new Harness();
		harness.setShortenNodeText(true);
		chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), new BattleNext_UiInputRouter(), 700f);
		BattleNext_TournamentView.Node node = node(chart, "W1-1");
		List<TestLabel> labels = List.copyOf(harness.getNodeLabels());
		assertEquals(220f, node.width);
		assertEquals(66f, node.height);
		assertEquals("W1-1", labels.get(0).getText());
		assertEquals(BattleNext_UiTheme.MUTED, labels.get(0).getColor());
		assertEquals("-", labels.get(1).getText());
		assertEquals("-", labels.get(3).getText());
		assertTrue(labels.get(2).getText().endsWith("..."));
		assertTrue(labels.get(2).getText().startsWith("舰队 50%"));
		assertTrue(chart.tooltipText(node).contains(longName));
		assertEquals(Alignment.RMID, labels.get(1).getAlignment());
		assertEquals(Alignment.RMID, labels.get(3).getAlignment());
		assertEquals(6f, labels.get(0).getBounds().getX());
		assertEquals(-24f, labels.get(0).getBounds().getY());
		assertEquals(-42f, labels.get(1).getBounds().getY());
		assertEquals(-60f, labels.get(3).getBounds().getY());
		for (TestLabel label : labels) {
			assertEquals(18f, label.getBounds().getHeight());
			assertTrue(label.getBounds().getX() + label.getBounds().getWidth() <= node.width - 6f);
			assertFalse(label.getText().contains("\n"));
		}
		start(cup);
		chart.zoomAt(90f, 90f, 2);
		chart.panBy(8f, 12f);
		BattleNext_TournamentView.ViewState browse = chart.captureState();
		chart.update(cup, instances.get(cup), participants.get(cup), names);
		assertEquals(Misc.getHighlightColor(), labels.get(0).getColor());
		assertEquals("0", labels.get(1).getText());
		assertEquals("0", labels.get(3).getText());
		engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
		chart.update(cup, instances.get(cup), participants.get(cup), names);
		assertEquals("0", labels.get(1).getText());
		assertEquals("1", labels.get(3).getText());
		assertEquals("Other fleet", labels.get(2).getText());
		assertTrue(labels.get(4).getText().startsWith("舰队 50%"));
		for (int round = 1; round < 12; round++) {
			engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
		}
		chart.update(cup, instances.get(cup), participants.get(cup), names);
		assertEquals("12", labels.get(1).getText());
		assertEquals("0", labels.get(3).getText());
		assertEquals(12f, labels.get(1).getBounds().getWidth());
		assertEquals(labels.get(1).getBounds().getWidth(), labels.get(3).getBounds().getWidth());
		assertEquals(24f, labels.get(2).getBounds().getX());
		assertEquals(labels.get(2).getBounds().getX(), labels.get(4).getBounds().getX());
		assertEquals(BattleNext_UiTheme.TEXT, labels.get(2).getColor());
		engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
		chart.update(cup, instances.get(cup), participants.get(cup), names);
		assertEquals("13", labels.get(1).getText());
		assertEquals(BattleNext_UiTheme.ACCENT, labels.get(0).getColor());
		assertEquals(Misc.getPositiveHighlightColor(), labels.get(1).getColor());
		assertEquals(Misc.getPositiveHighlightColor(), labels.get(2).getColor());
		assertEquals(Misc.getNegativeHighlightColor(), labels.get(3).getColor());
		assertEquals(Misc.getNegativeHighlightColor(), labels.get(4).getColor());
		assertEquals(browse, chart.captureState());
		assertEquals(labels, harness.getNodeLabels());
		chart.close();
	}

	@Test
	void mirroredCompletedRowsByeConditionalNodesAndDiagnosticsUseTheirOwnColors() {
		BattleNext_TournamentModel cup = tournament(3, BattleNext_TournamentModel.TournamentBracketType.ROUND_ROBIN, 1);
		start(cup);
		BattleNext_TournamentView chart = view(cup);
		Harness harness = new Harness();
		chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), new BattleNext_UiInputRouter(), 700f);
		BattleNext_TournamentView.Node waiting = node(chart, "RR-0-2");
		int waitingIndex = chart.nodes.indexOf(waiting) * 5;
		assertEquals(BattleNext_UiTheme.TEXT, harness.getNodeLabels().get(waitingIndex).getColor());
		engine.recordRound(cup, instances.get(cup), "RR-0-1", "F1", "");
		chart.update(cup, instances.get(cup), participants.get(cup), Map.of("F1", "First", "F2", "Second", "F3", "Third"));
		for (BattleNext_TournamentView.Node cell : chart.nodes.stream().filter(item -> item.id.equals("RR-0-1")).toList()) {
			int offset = chart.nodes.indexOf(cell) * 5;
			boolean firstWins = cell.player.fleetId.equals("F1");
			assertEquals(firstWins ? "1" : "0", harness.getNodeLabels().get(offset + 1).getText());
			assertEquals(firstWins ? "0" : "1", harness.getNodeLabels().get(offset + 3).getText());
			assertEquals(BattleNext_UiTheme.ACCENT, harness.getNodeLabels().get(offset).getColor());
			assertEquals(firstWins ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), harness.getNodeLabels().get(offset + 2).getColor());
		}
		chart.close();
		cup = tournament(2, BattleNext_TournamentModel.TournamentBracketType.DOUBLE_ELIMINATION, 1);
		chart = view(cup);
		harness = new Harness();
		chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), new BattleNext_UiInputRouter(), 700f);
		for (String id : List.of("L1-1", "GF_RESET")) {
			int offset = chart.nodes.indexOf(node(chart, id)) * 5;
			assertEquals(BattleNext_UiTheme.MUTED, harness.getNodeLabels().get(offset).getColor());
			assertEquals("-", harness.getNodeLabels().get(offset + 1).getText());
			assertEquals("-", harness.getNodeLabels().get(offset + 3).getText());
		}
		int reset = chart.nodes.indexOf(node(chart, "GF_RESET")) * 5;
		assertEquals(BattleNext_UiTheme.MUTED, harness.getNodeLabels().get(reset + 2).getColor());
		assertTrue(chart.tooltipText(node(chart, "GF_RESET")).contains("Required if"));
		chart.update(cup, null, participants.get(cup), Map.of("F1", "First"));
		int error = chart.nodes.indexOf(node(chart, "W1-1")) * 5;
		assertEquals(Misc.getNegativeHighlightColor(), harness.getNodeLabels().get(error).getColor());
		assertTrue(chart.tooltipText(node(chart, "W1-1")).contains("Missing fleet"));
		chart.close();
	}

	private class Event {

		private boolean consumed;
		private final InputEventAPI api;

		private Event(String kind, float x, float y, int value) {
			api = proxy(InputEventAPI.class, (name, args) -> switch (name) {
				case "getX" -> (int) x;
				case "getY" -> (int) y;
				case "getEventValue" -> value;
				case "isConsumed" -> consumed;
				case "consume" -> {
					consumed = true;
					yield null;
				}
				case "isMouseEvent" -> true;
				case "isMouseScrollEvent" -> kind.equals("wheel");
				case "isLMBDownEvent" -> kind.equals("down");
				case "isLMBUpEvent" -> kind.equals("up");
				case "isMouseMoveEvent" -> kind.equals("move");
				default -> false;
			});
		}
	}

	@Test
	void templateSeedRowsUseStablePositionsAcrossMirrorsAndSeparateClickFromDragging() {
		BattleNext_TournamentModel configuration = tournament(3, BattleNext_TournamentModel.TournamentBracketType.ROUND_ROBIN, 1);
		participants.put(configuration, List.of("F1", "F1", ""));
		Harness harness = new Harness();
		BattleNext_TournamentView chart = view(configuration);
		List<BattleNext_TournamentView.SlotHit> selected = new ArrayList<>();
		chart.setEditable(true, selected::add);
		chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), new BattleNext_UiInputRouter(), 700f);
		List<BattleNext_TournamentView.Node> mirrors = chart.nodes.stream().filter(node -> node.id.equals("RR-0-1")).toList();
		assertEquals(0, mirrors.get(0).player.seedIndex);
		assertEquals(1, mirrors.get(1).player.seedIndex);
		for (BattleNext_TournamentView.Node node : mirrors) {
			float[] point = chart.project(node.x + 110f, node.y + 33f);
			chart.handleInput(List.of(new Event("down", point[0], point[1], 0).api,
					new Event("up", point[0] + 3f, point[1], 0).api));
		}
		assertEquals(List.of(0, 1), selected.stream().map(BattleNext_TournamentView.SlotHit::slot).toList());
		assertNotEquals(chart.slotAnchor(selected.get(0)), chart.slotAnchor(selected.get(1)));
		BattleNext_TournamentView.Node node = mirrors.get(0);
		float[] point = chart.project(node.x + 110f, node.y + 33f);
		BattleNext_TournamentView.ViewState state = chart.captureState();
		chart.handleInput(List.of(new Event("down", point[0], point[1], 0).api,
				new Event("move", point[0] + 8f, point[1], 0).api, new Event("up", point[0] + 8f, point[1], 0).api));
		assertEquals(2, selected.size());
		assertNotEquals(state.panX(), chart.captureState().panX());
		chart.setEditable(false, selected::add);
		assertNull(chart.hitTestSlot(point[0], point[1]));
		assertNull(chart.hitTestSlot(-1f, -1f));
	}

	@Test
	void everyFormatExposesAllInitialSlotsAndDerivesLaterParticipantsAutomatically() {
		for (BattleNext_TournamentModel.TournamentBracketType type : BattleNext_TournamentModel.TournamentBracketType.values()) {
			BattleNext_TournamentModel configuration = tournament(5, type, 1);
			participants.put(configuration, Collections.nCopies(5, ""));
			Harness harness = new Harness();
			BattleNext_TournamentView chart = view(configuration);
			chart.setEditable(true, hit -> {});
			chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), new BattleNext_UiInputRouter(), 700f);
			Set<Integer> slots = new HashSet<>();
			for (BattleNext_TournamentView.Node node : chart.nodes) {
				if (node.id.isEmpty()) continue;
				for (int row : new int[]{1, 2}) {
					BattleNext_TournamentView.Participant participant = row == 1 ? node.player : node.enemy;
					float[] point = chart.project(node.x + 110f, node.y + 6f + row * 18f + 9f);
					BattleNext_TournamentView.SlotHit hit = chart.hitTestSlot(point[0], point[1]);
					if (participant.seedIndex >= 0) {
						assertNotNull(hit, type + " " + node.id + " row " + row);
						assertEquals(participant.seedIndex, hit.slot());
						assertEquals(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_select_fleet"), chart.participantText(participant));
						slots.add(hit.slot());
					} else {
						assertNull(hit);
					}
				}
			}
			assertEquals(Set.of(0, 1, 2, 3, 4), slots);
			assertTrue(chart.errors().isEmpty());
			chart.close();
		}
	}
}
