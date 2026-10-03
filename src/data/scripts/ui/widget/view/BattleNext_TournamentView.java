package data.scripts.ui.widget.view;

import data.scripts.tools.BattleNext_Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.battle.BattleNext_Match;
import data.scripts.battle.BattleNext_Round;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.annotation.PublicData;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class BattleNext_TournamentView extends BaseCustomUIPanelPlugin {

	public static final float CANVAS_WIDTH = 1200f;
	public static final float CANVAS_HEIGHT = 400f;
	protected static final float NODE_WIDTH = 220f;
	protected static final float NODE_HEIGHT = 82f;
	private static final float TOOLBAR_HEIGHT = 40f;
	private static final float FIT_MARGIN = 24f;
	private static final float ZOOM_STEP = 1.05f;
	private static final float MAX_ZOOM = 2.5f;
	private static final float BORDER_WIDTH = 2f;
	private static final Color BACKGROUND_COLOR = Color.BLACK;
	private static final Color TOOLTIP_TARGET_COLOR = new Color(0, 0, 0, 0);

	protected final BattleNext_TournamentModel tournament;
	protected final List<Node> nodes = new ArrayList<>();
	protected final List<Edge> edges = new ArrayList<>();
	private final Map<String, String> fleetNames;
	private final List<LabelAPI> labels = new ArrayList<>();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Common", "BattleNext_common_");
	private final BattleNext_I18nSection battleStrings = BattleNext_I18nSection.getInstance("Battle", "BattleNext_battle_");
	private final BattleNext_I18nSection managementStrings = BattleNext_I18nSection.getInstance("Management", "BattleNext_management_");
	private final BattleNext_I18nSection tournamentStrings = BattleNext_I18nSection.getInstance("Tournament", "BattleNext_tournament_");
	private CustomPanelAPI panel;
	private PositionAPI viewport;
	private BattleNext_UiInputRouter inputRouter;
	private UIComponentAPI tooltipTarget;
	private LabelAPI errorLabel;
	private ButtonAPI fitButton;
	private ButtonAPI resetButton;
	private float zoom = 1f;
	private float panX;
	private float panY;
	private float pointerX;
	private float pointerY;
	private boolean dragging;
	private Node hovered;
	private String layoutError = "";

	protected BattleNext_TournamentView(BattleNext_TournamentModel tournament, Map<String, String> names) {
		this.tournament = tournament;
		this.fleetNames = Map.copyOf(names);
	}

	public static BattleNext_TournamentView createView(BattleNext_TournamentModel tournament,
			Map<String, String> names) {
		BattleNext_TournamentView view;

		if (tournament.getBracketType() == null) {
			view = new BattleNext_SingleEliminationView(tournament, names);
		} else {
			view = switch (tournament.getBracketType()) {
				case SINGLE_ELIMINATION -> new BattleNext_SingleEliminationView(tournament, names);
				case DOUBLE_ELIMINATION -> new BattleNext_DoubleEliminationView(tournament, names);
				case ROUND_ROBIN -> new BattleNext_RoundRobinView(tournament, names);
				case KING_OF_THE_HILL -> new BattleNext_KingOfTheHillView(tournament, names);
			};
		}
		view.initializeLayout();
		return view;
	}

	private void initializeLayout() {

		try {
			if (tournament.getBracketType() == null) {
				throw new IllegalArgumentException(tournamentStrings.get("chart_missing_bracket"));
			}
			List<String> fleets = tournament.getFleetIds();

			if (fleets.size() < BattleNext_TournamentModel.MIN_FLEET_COUNT
					|| fleets.size() > BattleNext_TournamentModel.MAX_FLEET_COUNT
					|| new HashSet<>(fleets).size() != fleets.size() || fleets.contains("")) {
				throw new IllegalArgumentException(tournamentStrings.get("chart_invalid_participants"));
			}
			buildLayout();
		} catch (RuntimeException exception) {
			BattleNext_Logger.warn(BattleNext_TournamentView.class, "tournament.chart.layout.degraded",
				BattleNext_Logger.fields().put("tournament", tournament).build(), exception);
			layoutError = tournament.getBracketType() + ": " + exception.getMessage();
		}
		appendUnmappedMatches();
		fitAll();
	}

	public CustomPanelAPI attach(CustomPanelAPI owner, TooltipMakerAPI scroller, PositionAPI viewport,
			BattleNext_UiInputRouter router, float availableWidth) {

		this.viewport = viewport;
		this.inputRouter = router;

		float scale = Math.min(1f, availableWidth / CANVAS_WIDTH);
		float width = CANVAS_WIDTH * scale;
		float height = CANVAS_HEIGHT * scale;
		panel = owner.createCustomPanel(width, height, this);
		TooltipMakerAPI host = panel.createUIElement(width, height, false);
		BattleNext_UiTheme.configure(host);
		host.setBgAlpha(0f);

		for (Node node : nodes) {
			labels.add(host.createLabel(nodeText(node, host), BattleNext_UiTheme.TEXT, node.width - 12f));
		}

		if (!layoutError.isEmpty()) {
			errorLabel = host.createLabel(tournamentStrings.get("chart_error") + ": " + layoutError,
					Misc.getNegativeHighlightColor(), width - 48f);
		}
		tooltipTarget = host.createRect(TOOLTIP_TARGET_COLOR, 0f);
		host.addCustomDoNotSetPosition(tooltipTarget);
		tooltipTarget.getPosition().setSize(0f, 0f);
		host.addTooltipTo(new BaseTooltipCreator() {
			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object ignored) {
				if (hovered == null || !inputRouter.isEmpty()) return;

				BattleNext_UiTheme.configure(tooltip);
				tooltip.addPara(tooltipText(hovered), 0f);
			}
		}, tooltipTarget, TooltipMakerAPI.TooltipLocation.RIGHT, true);
		host.setHeightSoFar(height);
		host.getPosition().setSize(width, height);
		panel.addUIElement(host).inTL(0f, 0f);
		fitButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(width - 194f, 8f, 100f, 24f), tournamentStrings.get("chart_fit"),
				"view:fit", CutStyle.ALL, false);
		resetButton = BattleNext_PageLayout.fixedButton(panel,
				new UIRect(width - 86f, 8f, 78f, 24f), tournamentStrings.get("chart_zoom_100"),
				"view:reset", CutStyle.ALL, false);
		panel.getPosition().setSize(width, height);
		CustomPanelAPI row = owner.createCustomPanel(availableWidth, height, null);
		row.addComponent(panel).inTL((availableWidth - width) * 0.5f, 0f);
		scroller.addCustom(row, 10f);
		fitAll();
		return panel;
	}

	protected abstract void buildLayout();

	protected void buildWinners(int width, BattleNext_Match.MatchStage stage) {

		List<Integer> seeds = seedOrder(width);
		int levels = Integer.numberOfTrailingZeros(width);

		for (int round = 0; round < levels; round++) {
			int count = width >> (round + 1);

			for (int index = 0; index < count; index++) {
				Participant player;
				Participant enemy;

				if (round == 0) {
					player = seededFleet(seeds.get(index * 2));
					enemy = seededFleet(seeds.get(index * 2 + 1));
				} else {
					player = source("W" + round + "-" + (index * 2 + 1), false);
					enemy = source("W" + round + "-" + (index * 2 + 2), false);
				}

				float y = 60f + (index + 0.5f) * 110f * (1 << round) - NODE_HEIGHT * 0.5f;
				addMatchNode("W" + (round + 1) + "-" + (index + 1), stage, player, enemy,
						40f + round * 300f, y, NODE_WIDTH, NODE_HEIGHT, false);
			}
		}
	}

	protected void addMatchNode(String id, BattleNext_Match.MatchStage stage, Participant player,
			Participant enemy, float x, float y, float width, float height, boolean rowOrder) {

		BattleNext_Match match = tournament.findMatch(id);

		if (match != null && !rowOrder) {
			Participant first = player;
			Participant second = enemy;

			if (match.getPlayer().equals(enemy.fleetId) && !match.getPlayer().equals(player.fleetId)) {
				first = enemy;
				second = player;
			}

			player = new Participant(match.getPlayer(), first.sourceId, first.loser, true);
			enemy = new Participant(match.getEnemy(), second.sourceId, second.loser, true);
			stage = match.getStage();
		} else if (match == null && (player.known && player.fleetId.isEmpty()
				|| enemy.known && enemy.fleetId.isEmpty())) {
			stage = BattleNext_Match.MatchStage.BYE;
			if (player.known && player.fleetId.isEmpty()) {
				Participant swap = player;
				player = enemy;
				enemy = swap;
			}
		}

		if (match != null) {
			stage = match.getStage();
		}

		String error = "";

		if (player.known && !player.fleetId.isEmpty() && !fleetNames.containsKey(player.fleetId)) {
			error = tournamentStrings.format("chart_missing_fleet", player.fleetId);
		}
		if (enemy.known && !enemy.fleetId.isEmpty() && !fleetNames.containsKey(enemy.fleetId)) {
			error += (error.isEmpty() ? "" : "; ") + tournamentStrings.format("chart_missing_fleet", enemy.fleetId);
		}

		nodes.add(new Node(id, stage, player, enemy, match, x, y, width, height, error, ""));
		if (!player.sourceId.isEmpty()) {
			edges.add(new Edge(player.sourceId, id, player.loser));
		}
		if (!enemy.sourceId.isEmpty() && !enemy.sourceId.equals(player.sourceId)) {
			edges.add(new Edge(enemy.sourceId, id, enemy.loser));
		}
	}

	protected void addDecoration(String title, float x, float y, float width, float height) {
		Participant empty = new Participant("", "", false, true);

		nodes.add(new Node("", BattleNext_Match.MatchStage.MAIN, empty, empty, null,
				x, y, width, height, "", title));
	}

	protected Participant source(String id, boolean loser) {
		Node previous = findNode(id);
		BattleNext_Match match = tournament.findMatch(id);

		if (match != null && match.getStatus() == BattleNext_Match.MatchStatus.COMPLETE) {
			return new Participant(loser ? match.getLoserFleetId() : match.getWinnerFleetId(), id, loser, true);
		}
		if (previous != null && previous.stage == BattleNext_Match.MatchStage.BYE) {
			return new Participant(loser ? "" : previous.player.fleetId, id, loser,
					loser || previous.player.known);
		}
		return new Participant("", id, loser, false);
	}

	protected List<Integer> seedOrder(int width) {
		List<Integer> order = new ArrayList<>(List.of(1, 2));

		for (int bracket = 4; bracket <= width; bracket *= 2) {
			List<Integer> next = new ArrayList<>();

			for (int seed : order) {
				next.add(seed);
				next.add(bracket + 1 - seed);
			}
			order = next;
		}
		return order;
	}

	protected Participant seededFleet(int seed) {
		String fleetId = "";

		if (seed <= tournament.getFleetIds().size()) {
			fleetId = tournament.getFleetIds().get(seed - 1);
		}
		return new Participant(fleetId, "", false, true);
	}

	private void appendUnmappedMatches() {
		float y = 40f;

		for (Node node : nodes) {
			y = Math.max(y, node.y + node.height + 20f);
		}

		for (BattleNext_Match match : tournament.getMatches()) {
			if (findNode(match.getId()) != null) continue;

			Participant player = new Participant(match.getPlayer(), "", false, true);
			Participant enemy = new Participant(match.getEnemy(), "", false, true);
			nodes.add(new Node(match.getId(), match.getStage(), player, enemy, match,
					20f, y, NODE_WIDTH, NODE_HEIGHT, tournamentStrings.format("chart_unmapped_match", match.getId()), ""));
			y += NODE_HEIGHT + 20f;
		}
	}

	public void fitAll() {

		if (nodes.isEmpty()) return;

		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;

		for (Node node : nodes) {
			minX = Math.min(minX, node.x);
			minY = Math.min(minY, node.y);
			maxX = Math.max(maxX, node.x + node.width);
			maxY = Math.max(maxY, node.y + node.height);
		}

		float toolbar = panel == null ? TOOLBAR_HEIGHT : TOOLBAR_HEIGHT * CANVAS_WIDTH / panel.getPosition().getWidth();
		zoom = Math.min(1f, Math.min((CANVAS_WIDTH - FIT_MARGIN * 2f) / (maxX - minX),
				(CANVAS_HEIGHT - toolbar - FIT_MARGIN * 2f) / (maxY - minY)));
		panX = (CANVAS_WIDTH - (maxX - minX) * zoom) * 0.5f - minX * zoom;
		panY = toolbar + (CANVAS_HEIGHT - toolbar - (maxY - minY) * zoom) * 0.5f
				- minY * zoom;
		clearHover();
		if (panel != null) {
			PositionAPI canvas = panel.getPosition();
			float[] visible = visibleBounds();
			BattleNext_Logger.info(BattleNext_TournamentView.class, "tournament.chart.fitted",
				BattleNext_Logger.fields().put("tournament_id", tournament.getId()).put("node_count", nodes.size()).put("zoom", zoom).put("canvas_x", canvas.getX()).put("canvas_y", canvas.getY()).put("canvas_width", canvas.getWidth()).put("canvas_height", canvas.getHeight()).put("visible_bounds", visible).put("first_node", project(nodes.get(0).x, nodes.get(0).y)).build());
		}
	}

	public void resetZoom() {
		zoom = 1f;
		panX = FIT_MARGIN;
		panY = (panel == null ? TOOLBAR_HEIGHT : TOOLBAR_HEIGHT * CANVAS_WIDTH / panel.getPosition().getWidth())
				+ FIT_MARGIN;
		clearHover();
	}

	public void zoomAt(float logicalX, float logicalY, int steps) {
		float oldZoom = zoom;
		zoom = Math.max(0.01f, Math.min(MAX_ZOOM, zoom * (float) Math.pow(ZOOM_STEP, steps)));
		panX = logicalX - (logicalX - panX) * zoom / oldZoom;
		panY = logicalY - (logicalY - panY) * zoom / oldZoom;
		clearHover();
	}

	public void panBy(float logicalX, float logicalY) {
		panX += logicalX;
		panY += logicalY;
		clearHover();
	}

	public float[] project(float x, float y) {
		float left = 0f;
		float top = CANVAS_HEIGHT;
		float scale = 1f;

		if (panel != null) {
			PositionAPI position = panel.getPosition();
			left = position.getX();
			top = position.getY() + position.getHeight();
			scale = position.getWidth() / CANVAS_WIDTH;
		}
		return new float[]{left + (panX + x * zoom) * scale, top - (panY + y * zoom) * scale};
	}

	public Node hitTest(float screenX, float screenY) {
		float scale = panel == null ? 1f : panel.getPosition().getWidth() / CANVAS_WIDTH;

		for (Node node : nodes) {
			if (node.id.isEmpty()) continue;

			float[] point = project(node.x, node.y);
			if (screenX >= point[0] && screenX <= point[0] + node.width * zoom * scale
					&& screenY <= point[1] && screenY >= point[1] - node.height * zoom * scale) {
				return node;
			}
		}
		return null;
	}

	public static float[] intersection(float x, float y, float width, float height,
			float otherX, float otherY, float otherWidth, float otherHeight) {
		float left = Math.max(x, otherX);
		float bottom = Math.max(y, otherY);
		float right = Math.min(x + width, otherX + otherWidth);
		float top = Math.min(y + height, otherY + otherHeight);
		return new float[]{left, bottom, Math.max(0f, right - left), Math.max(0f, top - bottom)};
	}

	private float[] visibleBounds() {
		PositionAPI position = panel.getPosition();

		return intersection(position.getX(), position.getY(), position.getWidth(),
				position.getHeight() - TOOLBAR_HEIGHT, viewport.getX(), viewport.getY(),
				viewport.getWidth(), viewport.getHeight());
	}

	public void handleInput(List<InputEventAPI> events) {

		if (panel == null) return;

		if (!inputRouter.isEmpty()) {
			close();
			return;
		}
		float[] bounds = visibleBounds();
		PositionAPI position = panel.getPosition();
		float scale = position.getWidth() / CANVAS_WIDTH;

		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;

			float x = event.getX();
			float y = event.getY();
			boolean inside = x >= bounds[0] && x <= bounds[0] + bounds[2]
					&& y >= bounds[1] && y <= bounds[1] + bounds[3] && bounds[2] > 0f && bounds[3] > 0f;

			if (event.isMouseScrollEvent() && inside) {
				zoomAt((x - position.getX()) / scale,
						(position.getY() + position.getHeight() - y) / scale,
						Integer.signum(event.getEventValue()));
				event.consume();
			} else if (event.isLMBDownEvent() && inside) {
				dragging = true;
				clearHover();
				event.consume();
			} else if (event.isMouseMoveEvent() && dragging) {
				panBy((x - pointerX) / scale, (pointerY - y) / scale);
				event.consume();
			} else if (event.isLMBUpEvent() && dragging) {
				dragging = false;
				event.consume();
			}

			if (event.isMouseEvent()) {
				pointerX = x;
				pointerY = y;
			}
			if (!dragging && event.isMouseEvent()) {
				hovered = inside ? hitTest(pointerX, pointerY) : null;
			}
		}

		updateTooltipTarget();
	}

	@Override
	public void processInput(List<InputEventAPI> events) {
		handleInput(events);
	}

	@Override
	public void buttonPressed(Object id) {
		if (inputRouter == null || !inputRouter.isEmpty()) return;

		if ("view:fit".equals(id)) {
			fitAll();
		} else if ("view:reset".equals(id)) {
			resetZoom();
		}
	}

	@Override
	public void advance(float amount) {

		if (panel == null) return;

		boolean enabled = inputRouter.isEmpty();

		fitButton.setEnabled(enabled);
		resetButton.setEnabled(enabled);
		if (!enabled) {
			close();
			return;
		}
		if (fitButton.isChecked()) {
			fitButton.setChecked(false);
			fitAll();
		}
		if (resetButton.isChecked()) {
			resetButton.setChecked(false);
			resetZoom();
		}
		updateTooltipTarget();
	}

	public void close() {
		dragging = false;
		clearHover();
	}

	private void clearHover() {
		hovered = null;
		if (tooltipTarget != null) {
			tooltipTarget.getPosition().setSize(0f, 0f);
		}
	}

	private void updateTooltipTarget() {

		if (tooltipTarget == null) return;

		float[] bounds = visibleBounds();
		if (hovered == null || dragging || !inputRouter.isEmpty()) {
			clearHover();
			return;
		}

		float scale = panel.getPosition().getWidth() / CANVAS_WIDTH * zoom;
		float[] point = project(hovered.x, hovered.y);
		float[] hit = intersection(point[0], point[1] - hovered.height * scale,
				hovered.width * scale, hovered.height * scale, bounds[0], bounds[1], bounds[2], bounds[3]);
		if (pointerX < hit[0] || pointerX > hit[0] + hit[2] || pointerY < hit[1]
				|| pointerY > hit[1] + hit[3]) {
			clearHover();
			return;
		}
		tooltipTarget.getPosition().setLocation(hit[0], hit[1]).setSize(hit[2], hit[3]);
	}

	@Override
	public void renderBelow(float alphaMult) {

		if (panel == null) return;

		PositionAPI position = panel.getPosition();
		float[] bounds = intersection(position.getX(), position.getY(), position.getWidth(), position.getHeight(),
				viewport.getX(), viewport.getY(), viewport.getWidth(), viewport.getHeight());
		BattleNext_GeometryRenderer.GeometryClipState clip = BattleNext_GeometryRenderer.pushClip(
				bounds[0], bounds[1], bounds[2], bounds[3]);
		try {
			BattleNext_GeometryRenderer.fillRect(position, BACKGROUND_COLOR, alphaMult);
			float inset = BORDER_WIDTH * 0.5f;
			float left = position.getX() + inset;
			float bottom = position.getY() + inset;
			float right = position.getX() + position.getWidth() - inset;
			float top = position.getY() + position.getHeight() - inset;

			BattleNext_GeometryRenderer.lineLoop(new float[]{left, bottom, right, bottom,
					right, top, left, top}, BattleNext_UiTheme.ACCENT, alphaMult, BORDER_WIDTH);
		} finally {
			BattleNext_GeometryRenderer.popClip(clip);
		}
	}

	@Override
	public void render(float alphaMult) {
		if (panel == null) return;

		float[] bounds = visibleBounds();
		BattleNext_GeometryRenderer.GeometryClipState clip = BattleNext_GeometryRenderer.pushClip(
				bounds[0], bounds[1], bounds[2], bounds[3]);
		try {
			renderEdges(alphaMult);
			renderNodes(alphaMult);
		} finally {
			BattleNext_GeometryRenderer.popClip(clip);
		}
	}

	private void renderEdges(float alphaMult) {

		for (Edge edge : edges) {
			Node from = findNode(edge.from);
			Node to = findNode(edge.to);
			if (from == null || to == null) continue;

			float[] start = project(from.x + from.width, from.y + from.height * 0.5f);
			float[] end = project(to.x, to.y + to.height * 0.5f);
			float bend = (start[0] + end[0]) * 0.5f;
			Color color = edge.loser ? Misc.getNegativeHighlightColor() : BattleNext_UiTheme.MUTED;
			if (to.id.equals(tournament.getPendingMatchId()) || from.id.equals(tournament.getPendingMatchId())) {
				color = Misc.getHighlightColor();
			} else if (from.match != null && from.match.getStatus() == BattleNext_Match.MatchStatus.COMPLETE) {
				color = edge.loser ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
			}
			BattleNext_GeometryRenderer.lineStrip(new float[]{start[0], start[1], bend, start[1],
					bend, end[1], end[0], end[1]}, color, alphaMult, edge.loser ? 1f : 2f);
		}
	}

	private void renderNodes(float alphaMult) {

		PositionAPI position = panel.getPosition();
		float scale = position.getWidth() / CANVAS_WIDTH * zoom;
		float[] visible = visibleBounds();

		for (int index = 0; index < nodes.size(); index++) {
			Node node = nodes.get(index);
			float[] point = project(node.x, node.y);
			float width = node.width * scale;
			float height = node.height * scale;
			if (point[0] + width < visible[0] || point[0] > visible[0] + visible[2]) continue;
			if (point[1] < visible[1] || point[1] - height > visible[1] + visible[3]) continue;

			Color color = stateColor(node);
			BattleNext_GeometryRenderer.fillRect(point[0], point[1] - height, width, height,
					BACKGROUND_COLOR, alphaMult);
			BattleNext_GeometryRenderer.lineLoop(new float[]{point[0], point[1], point[0] + width, point[1],
					point[0] + width, point[1] - height, point[0], point[1] - height},
					node == hovered ? BattleNext_UiTheme.ACCENT : color, alphaMult,
					BORDER_WIDTH);
			LabelAPI label = labels.get(index);
			label.setColor(color);
			label.getPosition().setLocation(6f, -node.height + 6f).setSize(node.width - 12f, node.height - 12f);
			GL11.glPushMatrix();
			try {
				GL11.glTranslatef(point[0], point[1], 0f);
				GL11.glScalef(scale, scale, 1f);
				label.render(alphaMult);
			} finally {
				GL11.glPopMatrix();
			}
		}
		if (errorLabel != null) {
			errorLabel.getPosition().setLocation(position.getX() + 24f,
					position.getY() + position.getHeight() - TOOLBAR_HEIGHT - 80f);
			errorLabel.render(alphaMult);
		}
	}

	private Color stateColor(Node node) {
		if (!node.error.isEmpty()) return Misc.getNegativeHighlightColor();
		if (node.stage == BattleNext_Match.MatchStage.BYE || node.id.isEmpty()) return BattleNext_UiTheme.MUTED;
		if (node.id.equals(tournament.getPendingMatchId())) return Misc.getHighlightColor();
		if (node.match != null && node.match.getStatus() == BattleNext_Match.MatchStatus.COMPLETE) {
			if (node.stage == BattleNext_Match.MatchStage.ROUND_ROBIN
					&& !node.player.fleetId.equals(node.match.getWinnerFleetId())) {
				return Misc.getNegativeHighlightColor();
			}
			return Misc.getPositiveHighlightColor();
		}
		return node.match == null ? BattleNext_UiTheme.MUTED : BattleNext_UiTheme.TEXT;
	}

	private Node findNode(String id) {
		for (Node node : nodes) {
			if (node.id.equals(id)) return node;
		}
		return null;
	}

	protected String participantText(Participant participant) {
		if (participant.known && participant.fleetId.isEmpty()) return tournamentStrings.get("stage_bye");
		if (participant.known) return fleetNames.getOrDefault(participant.fleetId, participant.fleetId);

		return tournamentStrings.format(participant.loser ? "chart_loser_of" : "chart_winner_of", participant.sourceId);
	}

	private String nodeText(Node node, TooltipMakerAPI host) {
		if (node.id.isEmpty()) return host.shortenString(node.title, node.width - 12f);

		String player = host.shortenString(participantText(node.player), node.width - 12f);
		String enemy = host.shortenString(participantText(node.enemy), node.width - 12f);
		String score = tournamentStrings.get("chart_predicted");
		if (node.match != null) {
			score = scoreText(node);
		} else if (node.stage == BattleNext_Match.MatchStage.BYE) {
			score = tournamentStrings.get("stage_bye");
		} else if (node.stage == BattleNext_Match.MatchStage.GRAND_FINAL_RESET) {
			score = tournamentStrings.get(tournament.isCompleted() ? "chart_reset_unused" : "chart_reset_required_short");
		}
		return host.shortenString(node.id, node.width - 12f) + "\n" + player + "\n" + enemy + "\n"
				+ host.shortenString(score, node.width - 12f);
	}

	public static String shorten(String value, int maxCharacters) {
		int count = value.codePointCount(0, value.length());
		if (count <= maxCharacters) return value;

		int end = value.offsetByCodePoints(0, Math.max(0, maxCharacters - 3));
		return value.substring(0, end) + "...";
	}

	public String scoreText(Node node) {
		if (node.match == null) return tournamentStrings.get("chart_predicted");

		int player = node.match.getPlayerWins();
		int enemy = node.match.getEnemyWins();

		if (!node.player.fleetId.equals(node.match.getPlayer())) {
			player = node.match.getEnemyWins();
			enemy = node.match.getPlayerWins();
		}
		return player + " - " + enemy;
	}

	public String tooltipText(Node node) {

		StringBuilder result = new StringBuilder(tournamentStrings.get("stage_" + node.stage.name().toLowerCase(Locale.ROOT)))
				.append(" / ").append(node.id).append('\n');
		result.append(participantText(node.player)).append(" [").append(node.player.fleetId).append("]\n")
				.append(participantText(node.enemy)).append(" [").append(node.enemy.fleetId).append("]\n");
		result.append(tournamentStrings.get("chart_score")).append(": ").append(scoreText(node)).append('\n');
		if (node.match != null) {
			result.append(strings.get("status")).append(": ")
					.append(tournamentStrings.get("match_status_" + node.match.getStatus().name().toLowerCase(Locale.ROOT)))
					.append('\n').append(battleStrings.get("map_label")).append(": ").append(node.match.getMap())
					.append(" / ").append(battleStrings.get("map_scale_label")).append(": ")
					.append(BattleNext_I18nSection.buildFactor(node.match.getMapScale())).append('\n');
			for (int index = 0; index < node.match.getRounds().size(); index++) {
				BattleNext_Round round = node.match.getRounds().get(index);
				String winner = round.getWinnerFleetId();
				result.append(tournamentStrings.get("round")).append(' ').append(index + 1).append(": ")
						.append(winner.isEmpty() ? tournamentStrings.get("round_draw") : fleetNames.getOrDefault(winner, winner)
								+ " [" + winner + "]")
						.append(" / ").append(managementStrings.get("page_reports")).append(": ")
						.append(strings.get(round.getReport().getId().isEmpty() ? "unavailable" : "available")).append('\n');
			}
			if (!node.match.getDependencies().isEmpty()) {
				result.append(tournamentStrings.get("chart_sources")).append(": ")
						.append(String.join(", ", node.match.getDependencies())).append('\n');
			}
		} else {
			result.append(tournamentStrings.get("chart_predicted")).append('\n').append(battleStrings.get("map_label"))
					.append(": ").append(tournament.getMapId()).append(" / ")
					.append(battleStrings.get("map_scale_label")).append(": 1\n");
		}
		for (Participant participant : List.of(node.player, node.enemy)) {
			if (participant.sourceId.isEmpty()) continue;

			result.append(tournamentStrings.format(participant.loser ? "chart_loser_of" : "chart_winner_of",
					participant.sourceId)).append('\n');
		}
		if (node.stage == BattleNext_Match.MatchStage.GRAND_FINAL_RESET && node.match == null) {
			result.append(tournamentStrings.get(tournament.isCompleted() ? "chart_reset_unused" : "chart_reset_required")).append('\n');
		}
		if (!node.error.isEmpty()) {
			result.append(tournamentStrings.get("chart_error")).append(": ").append(node.error);
		}
		return result.toString();
	}

	public List<String> errors() {
		List<String> result = new ArrayList<>();

		if (!layoutError.isEmpty()) {
			result.add(layoutError);
		}
		for (Node node : nodes) {
			if (node.error.isEmpty()) continue;

			result.add(node.id + ": " + node.error);
		}
		return List.copyOf(result);
	}

	@PublicData
	public static class Participant {
		public final String fleetId;
		public final String sourceId;
		public final boolean loser;
		public final boolean known;

		public Participant(String fleetId, String sourceId, boolean loser, boolean known) {
			this.fleetId = fleetId;
			this.sourceId = sourceId;
			this.loser = loser;
			this.known = known;
		}
	}

	@PublicData
	public static class Node {
		public final String id;
		public final BattleNext_Match.MatchStage stage;
		public final Participant player;
		public final Participant enemy;
		public final BattleNext_Match match;
		public final float x;
		public final float y;
		public final float width;
		public final float height;
		public final String error;
		public final String title;

		public Node(String id, BattleNext_Match.MatchStage stage, Participant player, Participant enemy,
				BattleNext_Match match, float x, float y, float width, float height, String error, String title) {
			this.id = id;
			this.stage = stage;
			this.player = player;
			this.enemy = enemy;
			this.match = match;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.error = error;
			this.title = title;
		}
	}

	@PublicData
	public static class Edge {
		public final String from;
		public final String to;
		public final boolean loser;

		public Edge(String from, String to, boolean loser) {
			this.from = from;
			this.to = to;
			this.loser = loser;
		}
	}
}
