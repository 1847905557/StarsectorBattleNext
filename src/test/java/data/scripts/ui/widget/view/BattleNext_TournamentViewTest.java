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
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;

class BattleNext_TournamentViewTest {
	private static final Color SEMITRANSPARENT_GRAY = new Color(175, 175, 175, 180);

	private final BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
	private final Map<BattleNext_TournamentModel, BattleNext_StartedTournament> instances = new HashMap<>();
	private final Map<BattleNext_TournamentModel, List<String>> participants = new HashMap<>();
	private SettingsAPI previousSettings;

	@BeforeEach
	void configureSettings() throws Exception {
		previousSettings = Global.getSettings();
		Global.setSettings(proxy(SettingsAPI.class, (name, args) -> switch (name) {
			case "getString" -> BattleNext_TestTextCatalog.text(args[0], args[1]);
			case "getColor" -> switch (args[0].toString()) {
				case "textGrayColor" -> SEMITRANSPARENT_GRAY;
				case "standardTextColor" -> Color.WHITE;
				case "textFriendColor" -> Color.GREEN;
				case "textEnemyColor" -> Color.RED;
				case "buttonShortcut" -> Color.YELLOW;
				default -> Color.CYAN;
			};
			case "getScreenScaleMult" -> 1f;
			case "getFloat" -> 1f;
			case "getInt" -> 0;
			case "getBoolean" -> false;
			case "getScreenWidth", "getScreenHeight" -> 800f;
			case "getScreenWidthPixels", "getScreenHeightPixels" -> 800f;
			default -> null;
		}));
	}

	@AfterEach
	void restoreSettings() {
		Global.setSettings(previousSettings);
	}

	@Test
	void allFormatsMapInitialProgressAndCompletedResultsWithoutMutatingTheModel() throws Exception {

		for (int count : new int[]{2, 3, 4, 5, 8, 16, 32}) {
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
		for (int count : new int[]{2, 3, 4, 5, 8, 16, 32}) {
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
		harness.chartBounds.width = 700f;
		harness.chartBounds.height = 700f;
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
		assertTrue(harness.paragraphs.contains(String.format(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_finished_matches"), 0, 0)));
		assertFalse(harness.paragraphs.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_select_fleet")));
		assertTrue(harness.scrollingHostCreated);
		assertTrue(harness.customChartAdded);
		assertTrue(harness.reservedChartHeight > 0f);
		assertTrue(harness.summaryAdded);
		assertTrue(harness.buttonIds.contains("tournament:create"));
		assertFalse(harness.buttonIds.contains("tournament:active"));
		assertFalse(harness.buttons.get(harness.buttonIds.indexOf("tournament:create")).enabled);
		assertTrue(harness.buttons.get(harness.buttonIds.indexOf("tournament:check")).enabled);
		assertEquals(initial, cup.toJson().toString());
		cup.setBracketType(null);
		harness.chartBounds.width = 700f;
		harness.chartBounds.height = 700f;
		harness.paragraphs.clear();
		build.invoke(page, detailHost);
		assertTrue(harness.paragraphs.stream().anyMatch(text -> text.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_error"))));
		assertTrue(harness.paragraphs.stream().anyMatch(text -> text.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_unknown_bracket"))));
		assertFalse(harness.paragraphs.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_select_fleet")));
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
		chart.attach(harness.panel, harness.host, harness.viewport.api, router, 700f);
		assertEquals(700f, harness.chartBounds.width);
		assertEquals(700f * BattleNext_TournamentView.CANVAS_HEIGHT / BattleNext_TournamentView.CANVAS_WIDTH,
				harness.chartBounds.height);
		chart.resetZoom();
		BattleNext_TournamentView.Node node = chart.nodes.get(0);
		float[] center = chart.project(node.x + node.width * 0.5f, node.y + node.height * 0.5f);
		Event hover = new Event("move", center[0], center[1], 0);
		chart.handleInput(List.of(hover.api));
		assertTrue(harness.target.height > 0f);
		assertNotNull(harness.tooltip);
		harness.tooltip.createTooltip(harness.host, false, null);
		assertTrue(harness.paragraphs.stream().anyMatch(text -> text.contains(node.id)
				&& text.contains(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_chart_predicted"))));
		chart.zoomAt(300f, 300f, 2);
		assertEquals(0f, harness.target.height);
		Event wheel = new Event("wheel", 400f, 100f, 1);
		chart.handleInput(List.of(wheel.api));
		assertTrue(wheel.consumed);
		Event outside = new Event("wheel", 900f, 220f, 1);
		chart.handleInput(List.of(outside.api));
		assertFalse(outside.consumed);
		Event toolbar = new Event("down", 630f, harness.chartBounds.height - 10f, 0);
		chart.handleInput(List.of(toolbar.api));
		assertFalse(toolbar.consumed);
		float[] before = chart.project(100f, 100f);
		Event down = new Event("down", 300f, 100f, 0);
		Event move = new Event("move", 320f, 120f, 0);
		Event up = new Event("up", 320f, 120f, 0);
		chart.handleInput(List.of(down.api, move.api, up.api));
		assertTrue(down.consumed && move.consumed && up.consumed);
		assertArrayEquals(new float[]{before[0] + 20f, before[1] + 20f}, chart.project(100f, 100f), 0.001f);
		harness.buttons.get(1).checked = true;
		chart.advance(0f);
		assertFalse(harness.buttons.get(1).checked);
		assertEquals(124f * 700f / BattleNext_TournamentView.CANVAS_WIDTH,
				chart.project(100f, 100f)[0], 0.001f);
		router.push(new BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return harness.viewport.api;
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
		assertFalse(harness.buttons.get(0).enabled);
		assertEquals(0f, harness.target.height);
	}

	@Test
	void rawWheelValuesUseGentleDirectionalZoomAndKeepPointerAnchor() {

		Harness harness = new Harness();
		BattleNext_TournamentView chart = view(tournament(4,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1));
		chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), 700f);
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
			harness.viewport.api.setLocation(100f, 80f).setSize(availableWidth, 600f);
			harness.chartBounds.api.setLocation(100f, 80f);
			BattleNext_TournamentView chart = view(tournament(2,
					BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1));
			chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), availableWidth);
			float width = Math.min(availableWidth, BattleNext_TournamentView.CANVAS_WIDTH);
			float left = harness.chartBounds.x - harness.viewport.x;
			float right = harness.viewport.x + availableWidth - harness.chartBounds.x - width;

			assertEquals(width, harness.chartBounds.width);
			assertEquals(availableWidth, harness.rowBounds.width);
			assertEquals(width * BattleNext_TournamentView.CANVAS_HEIGHT / BattleNext_TournamentView.CANVAS_WIDTH,
					harness.chartBounds.height, 0.001f);
			assertEquals(left, right, 0.001f);
			assertEquals(80f, harness.chartBounds.y);
			assertEquals(harness.chartBounds.height + 10f, harness.reservedChartHeight);
			BattleNext_TournamentView.Node node = chart.nodes.get(0);
			float logicalX = node.x + node.width * 0.5f;
			float logicalY = node.y + node.height * 0.5f;
			float[] center = chart.project(logicalX, logicalY);
			chart.handleInput(List.of(new Event("move", center[0], center[1], 0).api));
			assertSame(node, chart.hitTest(center[0], center[1]));
			assertTrue(harness.target.height > 0f);
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
			assertEquals(left, harness.chartBounds.x - harness.viewport.x, 0.001f);
			Event outside = new Event("wheel", harness.chartBounds.x - 1f, center[1], -120);
			chart.handleInput(List.of(outside.api));
			assertFalse(outside.consumed);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2})
	void summaryColumnsAlignWithChartAndReserveTallestContent(int state) throws Exception {

		for (float availableWidth : new float[]{400f, 700f, 1600f}) {
			for (int count : new int[]{2, 32}) {
				Harness harness = new Harness();
				harness.customChartAdded = true;
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
					display.enableTemplateEditing(harness.panel, new BattleNext_FleetSourceProvider(new BattleNext_FleetStore()),
							() -> this.participants.get(cup), (slot, id) -> {});
				}
				Field chart = display.getClass().getDeclaredField("chart");
				chart.setAccessible(true);
				chart.set(display, view(cup));
				Method build = display.getClass().getDeclaredMethod("addSummary",
						CustomPanelAPI.class, TooltipMakerAPI.class, float.class, float.class);
				build.setAccessible(true);
				String json = cup.toJson().toString();
				build.invoke(display, harness.panel, harness.host, availableWidth, width);
				Harness.TextBlock title = harness.summaryBlocks.get(0);
				Harness.TextBlock details = harness.summaryBlocks.get(1);
				Harness.TextBlock participants = harness.summaryBlocks.get(2);
				Harness.TextBlock fleetTitle = harness.summaryBlocks.get(3);

				assertEquals(width, harness.summaryBounds.width);
				assertEquals((availableWidth - width) * 0.5f, harness.summaryBounds.leftPad);
				assertEquals(0f, title.bounds.leftPad);
				assertEquals(details.bounds.width, title.bounds.width);
				assertEquals(participants.bounds.width, fleetTitle.bounds.width);
				assertEquals(title.bounds.topPad, fleetTitle.bounds.topPad);
				assertEquals(participants.bounds.leftPad, fleetTitle.bounds.leftPad);
				assertEquals(List.of(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_summary")), title.titles);
				assertEquals(List.of(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_participant_fleets")), fleetTitle.titles);
				assertEquals(details.bounds.width, participants.bounds.width);
				assertEquals(10f, participants.bounds.leftPad - details.bounds.width);
				assertEquals(details.bounds.topPad, participants.bounds.topPad);
				assertEquals(Math.max(title.height, fleetTitle.height) + 10f, details.bounds.topPad);
				assertEquals(details.bounds.topPad + Math.max(details.height, participants.height), harness.summaryBounds.height);
				assertEquals(harness.summaryBounds.height + 10f, harness.contentHeight);
				assertEquals(count, participants.text.size());
				assertTrue(participants.text.contains(longName));
				assertTrue(details.text.stream().anyMatch(value -> value.startsWith(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_status") + ":")));
				assertTrue(details.text.stream().anyMatch(value -> value.startsWith("A detailed warning")));
				assertEquals(state == 0, harness.buttonIds.contains("tournament:create"));
				for (TestButton button : harness.buttons) {
					assertEquals(Math.min(180f, (details.bounds.width - 4f) * 0.5f), button.bounds.width);
					assertEquals(30f, button.bounds.height);
				}
				if (state == 0) {
					assertEquals(harness.buttons.get(0).bounds.topPad, harness.buttons.get(1).bounds.topPad);
					assertEquals(harness.buttons.get(0).bounds.leftPad + harness.buttons.get(0).bounds.width + 4f, harness.buttons.get(1).bounds.leftPad);
				}
				if (state == 2) {
					assertTrue(details.text.stream().anyMatch(value -> value.startsWith(BattleNext_TestTextCatalog.text("Common", "BattleNext_common_winner") + ":")));
				}
				assertEquals(json, cup.toJson().toString());
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void displayRoutesActionsAndReleasesInputOnClose(boolean started) throws Exception {

		Harness harness = new Harness();
		harness.chartBounds.api.setSize(700f, 700f);
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
		display.attach(harness.panel);
		CustomUIPanelPlugin plugin = harness.displayPlugin;
		String action = started ? "tournament:authorize" : "tournament:start";

		assertTrue(harness.scrollingHostCreated);
		assertTrue(harness.customChartAdded && harness.summaryAdded);
		assertTrue(harness.paragraphs.contains("Visible warning"));
		assertTrue(harness.buttonIds.contains(action));
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
		assertTrue(harness.target.height > 0f);
		Event down = new Event("down", center[0], center[1], 0);
		display.handleGlobalInput(List.of(down.api));
		assertTrue(down.consumed);
		BattleNext_UiInputRouter.Overlay overlay = new BattleNext_UiInputRouter.Overlay() {
			@Override
			public PositionAPI getBounds() {
				return harness.viewport.api;
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
		assertEquals(0f, harness.target.height);
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
		harness.chartBounds.api.setSize(700f, 700f);
		List<Object> actions = new ArrayList<>();
		BattleNext_TournamentDisplay display = new BattleNext_TournamentDisplay(new BattleNext_UiInputRouter(),
				tournament(2, BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1), null, data,
				List.of(new BattleNext_TournamentDisplay.Action("Start", "tournament:start", false, List.of())), actions::add);
		display.attach(harness.panel);
		assertFalse(harness.buttons.get(harness.buttons.size() - 1).enabled);
		harness.displayPlugin.buttonPressed("tournament:start");
		harness.displayPlugin.buttonPressed("tournament:authorize");
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

	private class Bounds {
		private float x;
		private float y;
		private float width;
		private float height;
		private Bounds parent;
		private float leftPad;
		private float topPad;
		private final PositionAPI api = proxy(PositionAPI.class, this::call);

		private Object call(String name, Object[] args) {
			return switch (name) {
				case "getX" -> x;
				case "getY" -> y;
				case "getWidth" -> width;
				case "getHeight" -> height;
				case "setLocation" -> {
					x = (Float) args[0];
					y = (Float) args[1];
					yield api;
				}
				case "setSize" -> {
					width = (Float) args[0];
					height = (Float) args[1];
					yield api;
				}
				case "inTL" -> {
					leftPad = (Float) args[0];
					topPad = (Float) args[1];
					if (parent != null) {
						x = parent.x + (Float) args[0];
						y = parent.y + parent.height - height - (Float) args[1];
					}
					yield api;
				}
				case "inLMid" -> api;
				default -> null;
			};
		}
	}

	private class TestButton {
		private boolean checked;
		private boolean enabled = true;
		private final Bounds bounds = new Bounds();
		private final ButtonAPI api = proxy(ButtonAPI.class, (name, args) -> switch (name) {
			case "getPosition" -> bounds.api;
			case "isChecked" -> checked;
			case "setChecked" -> {
				checked = (Boolean) args[0];
				yield null;
			}
			case "setEnabled" -> {
				enabled = (Boolean) args[0];
				yield null;
			}
			default -> null;
		});
	}

	private class TestLabel {
		private final Bounds bounds = new Bounds();
		private String text;
		private Color color;
		private Alignment alignment;
		private final LabelAPI api = proxy(LabelAPI.class, (name, args) -> switch (name) {
			case "getPosition" -> bounds.api;
			case "getText" -> text;
			case "setText" -> {
				text = (String) args[0];
				yield null;
			}
			case "setColor" -> {
				color = (Color) args[0];
				yield null;
			}
			case "setAlignment" -> {
				alignment = (Alignment) args[0];
				yield null;
			}
			case "computeTextWidth" -> textWidth((String) args[0]);
			default -> null;
		});

		private float textWidth(String value) {
			float width = 0f;
			for (int point : value.codePoints().toArray()) {
				width += point == '1' ? 4f : 8f;
			}
			return width;
		}
	}

	private class Harness {
		private final List<TestLabel> nodeLabels = new ArrayList<>();
		private boolean shortenNodeText;
		private final Bounds chartBounds = new Bounds();
		private final Bounds rowBounds = new Bounds();
		private final Bounds summaryBounds = new Bounds();
		private final List<TextBlock> summaryBlocks = new ArrayList<>();
		private final Bounds viewport = new Bounds();
		private final Bounds target = new Bounds();
		private final List<TestButton> buttons = new ArrayList<>();
		private final List<String> paragraphs = new ArrayList<>();
		private final List<Object> buttonIds = new ArrayList<>();
		private boolean scrollingHostCreated;
		private boolean customChartAdded;
		private float contentHeight;
		private float reservedChartHeight;
		private boolean summaryScope;
		private boolean summaryAdded;
		private boolean shrinkChildren;
		private CustomUIPanelPlugin displayPlugin;
		private final Bounds hostBounds = new Bounds();
		private TooltipMakerAPI.TooltipCreator tooltip;
		private final UIComponentAPI targetApi = proxy(UIComponentAPI.class, (name, args) ->
				name.equals("getPosition") ? target.api : null);
		private final TooltipMakerAPI host = proxy(TooltipMakerAPI.class, this::hostCall);
		private final CustomPanelAPI panel = proxy(CustomPanelAPI.class, this::panelCall);
		private final CustomPanelAPI row = proxy(CustomPanelAPI.class, (name, args) -> switch (name) {
			case "getPosition", "addUIElement" -> rowBounds.api;
			case "getPlugin" -> displayPlugin;
			case "createUIElement" -> panelCall(name, args);
			case "createCustomPanel" -> {
				summaryBounds.api.setSize((Float) args[0], (Float) args[1]);
				yield this.summary;
			}
			case "addComponent" -> {
				Bounds child = args[0] == panel ? chartBounds : summaryBounds;
				child.parent = rowBounds;
				yield child.api;
			}
			default -> null;
		});
		private final CustomPanelAPI summary = proxy(CustomPanelAPI.class, (name, args) -> switch (name) {
			case "getPosition" -> summaryBounds.api;
			case "createUIElement" -> {
				assertFalse((Boolean) args[2]);
				TextBlock block = new TextBlock();
				block.owner = this.summary;
				block.layoutWidth = (Float) args[0];
				summaryBlocks.add(block);
				yield block.api;
			}
			case "addUIElement" -> {
				TextBlock block = summaryBlocks.stream().filter(item -> item.api == args[0]).findFirst().orElseThrow();
				assertSame(this.summary, block.owner, "The target panel must create its UI element");
				block.bounds.parent = summaryBounds;
				yield block.bounds.api;
			}
			default -> null;
		});

		private class TextBlock {
			private CustomPanelAPI owner;
			private float layoutWidth;
			private final Bounds bounds = new Bounds();
			private final List<String> text = new ArrayList<>();
			private final List<String> titles = new ArrayList<>();
			private float height;
			private final TooltipMakerAPI api = proxy(TooltipMakerAPI.class, (name, args) -> {
				if (name.equals("getPosition")) return bounds.api;
				if (name.equals("getHeightSoFar")) return height;
				if (name.equals("setHeightSoFar")) {
					height = (Float) args[0];
					return null;
				}

				if (name.equals("addTitle") || name.equals("addPara")) {
					if (name.equals("addTitle")) {
						titles.add(args[0].toString());
					}
					text.add(args[0].toString());
					height += (name.equals("addPara") ? (Float) args[1] : 0f)
							+ 18f * Math.max(1, (int) Math.ceil(args[0].toString().length() * 8f / layoutWidth));
					paragraphs.add(args[0].toString());
					Bounds label = new Bounds();
					return proxy(LabelAPI.class, (method, values) -> method.equals("getPosition") ? label.api : null);
				}
				if (name.equals("addButton")) {
					assertTrue((Float) args[6] > 0f && (Float) args[6] <= layoutWidth);
					height += (Float) args[7] + (Float) args[8];
				}
				return hostCall(name, args);
			});
		}

		private Harness() {
			viewport.width = 700f;
			viewport.height = 380f;
		}

		private CustomPanelAPI detailHost(float width, float height) {
			Bounds bounds = new Bounds();
			bounds.api.setSize(width, height);
			return proxy(CustomPanelAPI.class, (name, args) -> switch (name) {
				case "getPosition" -> bounds.api;
				case "getPlugin" -> new BaseCustomUIPanelPlugin();
				case "createCustomPanel" -> {
					chartBounds.api.setSize((Float) args[0], (Float) args[1]);
					yield panel;
				}
				case "addComponent" -> {
					chartBounds.parent = bounds;
					yield chartBounds.api;
				}
				default -> null;
			});
		}

		private Object panelCall(String name, Object[] args) {
			return switch (name) {
				case "createCustomPanel" -> {
					if (args[2] == null) {
						rowBounds.api.setLocation(chartBounds.x, chartBounds.y).setSize((Float) args[0], (Float) args[1]);
						yield row;
					}
					if (!(args[2] instanceof BattleNext_TournamentView)) {
						displayPlugin = (CustomUIPanelPlugin) args[2];
					}
					chartBounds.width = (Float) args[0];
					chartBounds.height = (Float) args[1];
					yield panel;
				}
				case "getPosition", "addComponent" -> chartBounds.api;
				case "getPlugin" -> displayPlugin;
				case "addUIElement" -> {
					if (shrinkChildren) {
						chartBounds.width = 1f;
						chartBounds.height = 1f;
					}
					yield chartBounds.api;
				}
				case "createUIElement" -> {
					if ((Boolean) args[2]) {
						scrollingHostCreated = true;
					}
					yield host;
				}
				default -> null;
			};
		}

		private Object hostCall(String name, Object[] args) {
			return switch (name) {
				case "getExternalScroller" -> proxy(ScrollPanelAPI.class, (method, values) -> method.equals("getYOffset") ? 0f : null);
				case "getPosition" -> hostBounds.api;
				case "beginSubTooltip" -> {
					TextBlock block = new TextBlock();
					block.bounds.width = (Float) args[0];
					block.layoutWidth = (Float) args[0];
					summaryBlocks.add(block);
					yield block.api;
				}
				case "endSubTooltip" -> {
					summaryScope = false;
					yield null;
				}
				case "getHeightSoFar" -> contentHeight;
				case "setHeightSoFar" -> {
					contentHeight = (Float) args[0];
					yield null;
				}
				case "createLabel" -> {
					TestLabel label = new TestLabel();
					label.text = (String) args[0];
					label.color = (Color) args[1];
					label.bounds.width = (Float) args[2];
					nodeLabels.add(label);
					yield label.api;
				}
				case "addSectionHeading", "addTitle", "addPara" -> {
					if (name.equals("addTitle") && args[0].equals(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_summary"))) {
						assertTrue(summaryScope, "The title must belong to the summary tooltip");
					}
					if (name.equals("addPara")) {
						paragraphs.add(args[0].toString());
					}
					Bounds label = new Bounds();
					yield proxy(LabelAPI.class, (method, values) -> method.equals("getPosition") ? label.api : null);
				}
				case "createRect" -> targetApi;
				case "addCustomDoNotSetPosition" -> {
					if (args[0] instanceof CustomPanelAPI) {
						customChartAdded = true;
						assertTrue(reservedChartHeight > 0f);
					}
					yield args[0];
				}
				case "addSpacer" -> {
					reservedChartHeight = (Float) args[0];
					contentHeight += reservedChartHeight;
					yield targetApi;
				}
				case "addCustom" -> {
					if (args[0] instanceof CustomPanelAPI custom) {
						if (summaryBlocks.isEmpty()) {
							customChartAdded = true;
							reservedChartHeight = custom.getPosition().getHeight() + (Float) args[1];
						} else {
							summaryAdded = true;
							assertTrue(customChartAdded);
						}
						contentHeight += custom.getPosition().getHeight() + (Float) args[1];
					} else {
						summaryAdded = true;
						assertTrue(customChartAdded);
					}
					yield args[0];
				}
				case "addTooltipTo" -> {
					tooltip = (TooltipMakerAPI.TooltipCreator) args[0];
					yield null;
				}
				case "shortenString" -> {
					String value = (String) args[0];
					if (!shortenNodeText) yield value;
					int length = Math.max(3, (int) (((Number) args[1]).floatValue() / 8f));
					yield BattleNext_TournamentView.shorten(value, length);
				}
				case "addButton" -> {
					buttonIds.add(args[1]);
					TestButton button = new TestButton();
					button.bounds.api.setSize((Float) args[6], (Float) args[7]);
					buttons.add(button);
					yield button.api;
				}
				default -> null;
			};
		}
	}

	@Test
	void chartRestoresCanvasAndReservesSummarySpaceAfterChildLayout() {
		Harness harness = new Harness();
		harness.shrinkChildren = true;
		BattleNext_TournamentView chart = view(tournament(2,
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1));
		chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), 700f);
		assertEquals(700f, harness.chartBounds.width);
		float height = 700f * BattleNext_TournamentView.CANVAS_HEIGHT / BattleNext_TournamentView.CANVAS_WIDTH;

		assertEquals(height, harness.chartBounds.height);
		assertTrue(harness.contentHeight >= height + 10f);
		assertEquals(height + 10f, harness.reservedChartHeight);
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
		harness.shortenNodeText = true;
		chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), 700f);
		BattleNext_TournamentView.Node node = node(chart, "W1-1");
		List<TestLabel> labels = List.copyOf(harness.nodeLabels);
		assertEquals(220f, node.width);
		assertEquals(66f, node.height);
		assertEquals("W1-1", labels.get(0).text);
		assertEquals(BattleNext_UiTheme.MUTED, labels.get(0).color);
		assertEquals("-", labels.get(1).text);
		assertEquals("-", labels.get(3).text);
		assertTrue(labels.get(2).text.endsWith("..."));
		assertTrue(labels.get(2).text.startsWith("舰队 50%"));
		assertTrue(chart.tooltipText(node).contains(longName));
		assertEquals(Alignment.RMID, labels.get(1).alignment);
		assertEquals(Alignment.RMID, labels.get(3).alignment);
		assertEquals(6f, labels.get(0).bounds.x);
		assertEquals(-24f, labels.get(0).bounds.y);
		assertEquals(-42f, labels.get(1).bounds.y);
		assertEquals(-60f, labels.get(3).bounds.y);
		for (TestLabel label : labels) {
			assertEquals(18f, label.bounds.height);
			assertTrue(label.bounds.x + label.bounds.width <= node.width - 6f);
			assertFalse(label.text.contains("\n"));
		}
		start(cup);
		chart.zoomAt(90f, 90f, 2);
		chart.panBy(8f, 12f);
		BattleNext_TournamentView.ViewState browse = chart.captureState();
		chart.update(cup, instances.get(cup), participants.get(cup), names);
		assertEquals(Misc.getHighlightColor(), labels.get(0).color);
		assertEquals("0", labels.get(1).text);
		assertEquals("0", labels.get(3).text);
		engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
		chart.update(cup, instances.get(cup), participants.get(cup), names);
		assertEquals("0", labels.get(1).text);
		assertEquals("1", labels.get(3).text);
		assertEquals("Other fleet", labels.get(2).text);
		assertTrue(labels.get(4).text.startsWith("舰队 50%"));
		for (int round = 1; round < 12; round++) {
			engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
		}
		chart.update(cup, instances.get(cup), participants.get(cup), names);
		assertEquals("12", labels.get(1).text);
		assertEquals("0", labels.get(3).text);
		assertEquals(12f, labels.get(1).bounds.width);
		assertEquals(labels.get(1).bounds.width, labels.get(3).bounds.width);
		assertEquals(24f, labels.get(2).bounds.x);
		assertEquals(labels.get(2).bounds.x, labels.get(4).bounds.x);
		assertEquals(BattleNext_UiTheme.TEXT, labels.get(2).color);
		engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
		chart.update(cup, instances.get(cup), participants.get(cup), names);
		assertEquals("13", labels.get(1).text);
		assertEquals(BattleNext_UiTheme.ACCENT, labels.get(0).color);
		assertEquals(Misc.getPositiveHighlightColor(), labels.get(1).color);
		assertEquals(Misc.getPositiveHighlightColor(), labels.get(2).color);
		assertEquals(Misc.getNegativeHighlightColor(), labels.get(3).color);
		assertEquals(Misc.getNegativeHighlightColor(), labels.get(4).color);
		assertEquals(browse, chart.captureState());
		assertEquals(labels, harness.nodeLabels);
		chart.close();
	}

	@Test
	void mirroredCompletedRowsByeConditionalNodesAndDiagnosticsUseTheirOwnColors() {
		BattleNext_TournamentModel cup = tournament(3, BattleNext_TournamentModel.TournamentBracketType.ROUND_ROBIN, 1);
		start(cup);
		BattleNext_TournamentView chart = view(cup);
		Harness harness = new Harness();
		chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), 700f);
		BattleNext_TournamentView.Node waiting = node(chart, "RR-0-2");
		int waitingIndex = chart.nodes.indexOf(waiting) * 5;
		assertEquals(BattleNext_UiTheme.TEXT, harness.nodeLabels.get(waitingIndex).color);
		engine.recordRound(cup, instances.get(cup), "RR-0-1", "F1", "");
		chart.update(cup, instances.get(cup), participants.get(cup), Map.of("F1", "First", "F2", "Second", "F3", "Third"));
		for (BattleNext_TournamentView.Node cell : chart.nodes.stream().filter(item -> item.id.equals("RR-0-1")).toList()) {
			int offset = chart.nodes.indexOf(cell) * 5;
			boolean firstWins = cell.player.fleetId.equals("F1");
			assertEquals(firstWins ? "1" : "0", harness.nodeLabels.get(offset + 1).text);
			assertEquals(firstWins ? "0" : "1", harness.nodeLabels.get(offset + 3).text);
			assertEquals(BattleNext_UiTheme.ACCENT, harness.nodeLabels.get(offset).color);
			assertEquals(firstWins ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(), harness.nodeLabels.get(offset + 2).color);
		}
		chart.close();
		cup = tournament(2, BattleNext_TournamentModel.TournamentBracketType.DOUBLE_ELIMINATION, 1);
		chart = view(cup);
		harness = new Harness();
		chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), 700f);
		for (String id : List.of("L1-1", "GF_RESET")) {
			int offset = chart.nodes.indexOf(node(chart, id)) * 5;
			assertEquals(BattleNext_UiTheme.MUTED, harness.nodeLabels.get(offset).color);
			assertEquals("-", harness.nodeLabels.get(offset + 1).text);
			assertEquals("-", harness.nodeLabels.get(offset + 3).text);
		}
		int reset = chart.nodes.indexOf(node(chart, "GF_RESET")) * 5;
		assertEquals(BattleNext_UiTheme.MUTED, harness.nodeLabels.get(reset + 2).color);
		assertTrue(chart.tooltipText(node(chart, "GF_RESET")).contains("Required if"));
		chart.update(cup, null, participants.get(cup), Map.of("F1", "First"));
		int error = chart.nodes.indexOf(node(chart, "W1-1")) * 5;
		assertEquals(Misc.getNegativeHighlightColor(), harness.nodeLabels.get(error).color);
		assertTrue(chart.tooltipText(node(chart, "W1-1")).contains("Missing fleet"));
		chart.close();
	}

	@Test
	void realOpenGlDrawsVisibleNodeAndRestoresParentClip() throws Exception {

		Path nativePath = Path.of("../../starsector-core/native/windows").toAbsolutePath().normalize();
		System.setProperty("org.lwjgl.librarypath", nativePath.toString());
		Pbuffer buffer = new Pbuffer(800, 800,
				new PixelFormat(), null, null);
		try {
			buffer.makeCurrent();
			GL11.glViewport(0, 0, 800, 800);
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			GL11.glOrtho(0, 800, 0, 800, -1, 1);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glLoadIdentity();
			GL11.glClearColor(1, 0, 1, 1);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
			GL11.glEnable(GL11.GL_SCISSOR_TEST);
			GL11.glScissor(0, 0, 800, 800);
			Harness harness = new Harness();
			BattleNext_TournamentModel cup = tournament(2, BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1);
			BattleNext_TournamentView chart = view(cup);
			chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), 700f);
			chart.renderBelow(1f);
			chart.render(1f);
			IntBuffer clip = BufferUtils.createIntBuffer(16);
			GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
			assertEquals(800, clip.get(2));
			assertEquals(800, clip.get(3));
			ByteBuffer pixels = BufferUtils.createByteBuffer(800 * 800 * 4);
			GL11.glReadPixels(0, 0, 800, 800, GL11.GL_RGBA,
					GL11.GL_UNSIGNED_BYTE, pixels);
			for (int x : new int[]{0, 1, 2, 100, 699, 700}) {
				int offset = (100 * 800 + x) * 4;
				Color expected = x == 0 || x == 1 || x == 699 ? BattleNext_UiTheme.ACCENT
						: x == 700 ? Color.MAGENTA : Color.BLACK;

				assertEquals(expected.getRed(), pixels.get(offset) & 255, "Frame red at x=" + x);
				assertEquals(expected.getGreen(), pixels.get(offset + 1) & 255, "Frame green at x=" + x);
				assertEquals(expected.getBlue(), pixels.get(offset + 2) & 255, "Frame blue at x=" + x);
			}
			BattleNext_TournamentView.Node node = chart.nodes.get(0);
			float[] center = chart.project(node.x + node.width * 0.5f, node.y + node.height * 0.5f);
			int centerOffset = ((int) center[1] * 800 + (int) center[0]) * 4;
			assertEquals(0, pixels.get(centerOffset) & 255);
			assertEquals(0, pixels.get(centerOffset + 1) & 255);
			assertEquals(0, pixels.get(centerOffset + 2) & 255);
			int nodePixels = 0;
			for (int y = 40; y < 340; y++) {
				for (int x = 100; x < 600; x++) {
					int offset = (y * 800 + x) * 4;
					int red = pixels.get(offset) & 255;
					int green = pixels.get(offset + 1) & 255;
					int blue = pixels.get(offset + 2) & 255;
					if (red > 0 && red == green && green == blue) {
						nodePixels++;
					}
				}
			}
			assertTrue(nodePixels >= 100, "Visible node outline pixels: " + nodePixels);
			BattleNext_TournamentView.ViewState camera = chart.captureState();
			PositionAPI frame = harness.chartBounds.api;
			float left = frame.getX();
			float bottom = frame.getY();
			float right = left + frame.getWidth();
			float top = bottom + frame.getHeight();
			for (float[] target : new float[][]{{left + frame.getWidth() * 0.5f, bottom},
					{left, bottom + frame.getHeight() * 0.5f}, {right, bottom + frame.getHeight() * 0.5f},
					{left + frame.getWidth() * 0.5f, top - 40f}}) {
				chart.restoreState(camera);
				float[] projected = chart.project(node.x + node.width * 0.5f, node.y + node.height * 0.5f);
				float scale = frame.getWidth() / BattleNext_TournamentView.CANVAS_WIDTH;
				chart.panBy((target[0] - projected[0]) / scale, (projected[1] - target[1]) / scale);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				chart.renderBelow(1f);
				chart.render(1f);
				GL11.glReadPixels(0, 0, 800, 800, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
				for (float[] sample : new float[][]{{left + 1f, bottom + frame.getHeight() * 0.5f},
						{right - 1f, bottom + frame.getHeight() * 0.5f}, {left + frame.getWidth() * 0.5f, bottom + 1f},
						{left + frame.getWidth() * 0.5f, top - 1f}}) {
					int offset = ((int) sample[1] * 800 + (int) sample[0]) * 4;
					assertEquals(BattleNext_UiTheme.ACCENT.getRed(), pixels.get(offset) & 255, "Frame red after panning");
					assertEquals(BattleNext_UiTheme.ACCENT.getGreen(), pixels.get(offset + 1) & 255, "Frame green after panning");
					assertEquals(BattleNext_UiTheme.ACCENT.getBlue(), pixels.get(offset + 2) & 255, "Frame blue after panning");
				}
				GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
				assertEquals(800, clip.get(2));
				assertEquals(800, clip.get(3));
			}
			chart.restoreState(camera);
			start(cup);
			for (boolean completed : List.of(false, true)) {
				if (completed) {
					engine.recordRound(cup, instances.get(cup), "W1-1", "F1", "");
				}
				chart.update(cup, instances.get(cup), participants.get(cup), Map.of("F1", "First", "F2", "Second"));
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				chart.renderBelow(1f);
				chart.render(1f);
				GL11.glReadPixels(0, 0, 800, 800, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
				Color expected = completed ? BattleNext_UiTheme.ACCENT : Misc.getHighlightColor();
				float[] border = chart.project(node.x + node.width * 0.5f, node.y);
				boolean found = false;
				for (int y = (int) border[1] - 3; y <= (int) border[1] + 3; y++) {
					for (int x = (int) border[0] - 3; x <= (int) border[0] + 3; x++) {
						int offset = (y * 800 + x) * 4;
						if ((pixels.get(offset) & 255) == expected.getRed()
								&& (pixels.get(offset + 1) & 255) == expected.getGreen()
								&& (pixels.get(offset + 2) & 255) == expected.getBlue()) found = true;
					}
				}
				assertTrue(found, "Native border color for completed=" + completed);
				GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
				assertEquals(800, clip.get(2));
				assertEquals(800, clip.get(3));
			}
			assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
		} finally {
			buffer.destroy();
		}
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
		chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), 700f);
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
			chart.attach(harness.panel, harness.host, harness.viewport.api, new BattleNext_UiInputRouter(), 700f);
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
