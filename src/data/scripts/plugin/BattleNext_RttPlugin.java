package data.scripts.plugin;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import data.scripts.ai.rtt.BattleNext_RttGeometry;
import data.scripts.ai.rtt.BattleNext_RttShipAI;
import data.scripts.battlesnext.util.BattleNext_FontData;
import data.scripts.order.BattleNext_OrderField;
import data.scripts.tools.BattleNext_CombatInput;
import data.scripts.tools.BattleNext_GeometryRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.*;

public class BattleNext_RttPlugin extends BaseEveryFrameCombatPlugin {

	private static final String AUTOPILOT_CONTROL = "C2_TOGGLE_AUTOPILOT";
	private static final String VENT_CONTROL = "SHIP_VENT_FLUX";
	private static final String SYSTEM_CONTROL = "SHIP_USE_SYSTEM";
	private static final String ADD_SELECTION_CONTROL = "SHIP_STRAFE_KEY";

	private static final String TOGGLE_GROUP_PREFIX = "SHIP_TOGGLE_GROUP_";
	private static final String SELECT_GROUP_PREFIX = "SHIP_SELECT_GROUP_";
	private static final int GROUP_COUNT = 7;

	private static final float DRAG_THRESHOLD = 10f;
	private static final int RING_SEGMENTS = 48;
	private static final float RING_SHIP_SCALE = 1.2f;
	private static final float RING_PADDING = 30f;
	private static final float GROUP_FONT_SIZE = 16f;
	private static final float GROUP_TEXT_GAP = 4f;
	private static final float RING_DIAGONAL = 0.70710677f;
	private static final Color SELECTION_COLOR = new Color(75, 215, 230, 230);
	private static final Color DRAG_FILL = new Color(75, 215, 230, 48);
	private static final Color MOVE_COLOR = new Color(75, 215, 230, 190);
	private static final Color ENGAGE_COLOR = new Color(240, 85, 85, 210);
	private static final Color FOLLOW_COLOR = new Color(95, 215, 125, 210);

	private final Set<ShipAPI> selected = new LinkedHashSet<>();
	private final Map<Integer, Set<ShipAPI>> groups = new HashMap<>();
	private final Map<ShipAPI, BattleNext_RttShipAI> controlled = new IdentityHashMap<>();
	private final Map<ShipAPI, ShipAIPlugin> previousAis = new IdentityHashMap<>();
	private CombatEngineAPI engine;
	private BattleNext_FontData.TextObject groupText;

	private boolean pressing;
	private boolean dragging;
	private boolean addSelection;
	private float startX;
	private float startY;
	private float pointerX;
	private float pointerY;

	public BattleNext_RttPlugin() {
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
	}

	@Override
	public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {

		if (engine == null || engine.isCombatOver()) return;

		updateControlState();
		boolean active = acceptsMouseInput();

		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isControlDownEvent(AUTOPILOT_CONTROL) && releaseSelected()) {
				event.consume();
				continue;
			}
			if (!active) continue;
			if (!event.isRepeat() && handleGroupEvent(event)) {
				event.consume();
				continue;
			}
			if (!selected.isEmpty() && !event.isRepeat() && event.isControlDownEvent(VENT_CONTROL)) {
				for (ShipAPI ship : selected) {
					ship.giveCommand(ShipCommand.VENT_FLUX, null, -1);
				}
				event.consume();
				continue;
			}
			if (!selected.isEmpty() && !event.isRepeat() && event.isControlDownEvent(SYSTEM_CONTROL)) {
				Vector2f point = new Vector2f(engine.getViewport().convertScreenXToWorldX(event.getX()),
						engine.getViewport().convertScreenYToWorldY(event.getY()));
				for (ShipAPI ship : selected) {
					if (ship.getSystem() != null) control(ship).rttUseSystemAt(point);
				}
				event.consume();
				continue;
			}
			if (event.isLMBDownEvent()) {
				pressing = true;
				dragging = false;
				addSelection = event.isControlActivated(ADD_SELECTION_CONTROL);
				startX = pointerX = event.getX();
				startY = pointerY = event.getY();
				event.consume();
				continue;
			}
			if (pressing && event.isMouseMoveEvent()) {
				pointerX = event.getX();
				pointerY = event.getY();
				float dx = pointerX - startX;
				float dy = pointerY - startY;
				if (dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD) dragging = true;
				if (dragging) event.consume();
				continue;
			}
			if (pressing && event.isLMBUpEvent()) {
				pointerX = event.getX();
				pointerY = event.getY();
				selectAt(engine.getViewport());
				pressing = false;
				dragging = false;
				event.consume();
				continue;
			}
			if (event.isRMBDownEvent() && !selected.isEmpty()) {
				orderAt(engine.getViewport(), event.getX(), event.getY());
				event.consume();
			}
		}
	}

	@Override
	public void advance(float amount, List<InputEventAPI> events) {

		if (engine == null) return;
		if (engine.isCombatOver()) {
			cleanup();
			return;
		}
		updateControlState();
	}

	private void updateControlState() {
		prune();
		if (!acceptsMouseInput()) {
			pressing = false;
			dragging = false;
			addSelection = false;
		}
	}

	private boolean acceptsMouseInput() {
		return acceptsMouseInput(engine);
	}

	static boolean acceptsMouseInput(CombatEngineAPI engine) {
		return !BattleNext_CombatInput.isPlayerPlaying(engine)
				&& engine.getViewport().isExternalControl()
				&& !BattleNext_CombatInput.isUiCovered(engine);
	}

	@Override
	public void renderInWorldCoords(ViewportAPI viewport) {
		if (engine == null || engine.isCombatOver()) return;
		for (ShipAPI ship : selected) {
			if (!validSelection(ship)) continue;
			circle(ship.getLocation(), ship.getCollisionRadius() * RING_SHIP_SCALE + RING_PADDING, SELECTION_COLOR);
			BattleNext_RttShipAI ai = controlled.get(ship);
			if (ai == null || ai.getVisualTargetPoint() == null) continue;
			Color color = switch (ai.getCommandType()) {
				case ENGAGE -> ENGAGE_COLOR;
				case FOLLOW -> FOLLOW_COLOR;
				case MOVE -> MOVE_COLOR;
			};
			Vector2f point = ai.getVisualTargetPoint();
			BattleNext_GeometryRenderer.lineStrip(new float[]{ship.getLocation().x, ship.getLocation().y,
					point.x, point.y}, color, viewport.getAlphaMult(), 1.5f);
		}
		if (pressing && dragging && acceptsMouseInput()) {
			float firstX = viewport.convertScreenXToWorldX(startX);
			float firstY = viewport.convertScreenYToWorldY(startY);
			float secondX = viewport.convertScreenXToWorldX(pointerX);
			float secondY = viewport.convertScreenYToWorldY(pointerY);
			float left = Math.min(firstX, secondX);
			float bottom = Math.min(firstY, secondY);
			float width = Math.abs(secondX - firstX);
			float height = Math.abs(secondY - firstY);
			BattleNext_GeometryRenderer.fillRect(left, bottom, width, height, DRAG_FILL, viewport.getAlphaMult());
			BattleNext_GeometryRenderer.lineLoop(new float[]{left, bottom, left + width, bottom,
					left + width, bottom + height, left, bottom + height}, SELECTION_COLOR, viewport.getAlphaMult(), 1f);
		}
	}

	@Override
	public void renderInUICoords(ViewportAPI viewport) {

		if (engine == null || engine.isCombatOver()) return;
		for (ShipAPI ship : selected) {
			if (!validSelection(ship)) continue;
			StringJoiner numbers = new StringJoiner(",");
			for (int number = 1; number <= GROUP_COUNT; number++) {
				Set<ShipAPI> group = groups.get(number);
				if (group != null && group.contains(ship)) numbers.add(Integer.toString(number));
			}
			if (numbers.length() == 0) continue;
			if (groupText == null) {
				groupText = BattleNext_FontData.getInsigniaNormal();
				groupText.setFontSize(GROUP_FONT_SIZE);
			}
			groupText.setText(numbers.toString());
			float offset = (ship.getCollisionRadius() * RING_SHIP_SCALE + RING_PADDING) * RING_DIAGONAL;
			float x = viewport.convertWorldXtoScreenX(ship.getLocation().x + offset) + GROUP_TEXT_GAP;
			float y = viewport.convertWorldYtoScreenY(ship.getLocation().y - offset) - GROUP_TEXT_GAP;
			groupText.setBaseColor(Color.BLACK);
			groupText.draw(x + 1f, y - 1f);
			groupText.setBaseColor(SELECTION_COLOR);
			groupText.draw(x, y);
		}
	}

	private boolean handleGroupEvent(InputEventAPI event) {

		for (int number = 1; number <= GROUP_COUNT; number++) {
			boolean assign = event.isControlDownEvent(TOGGLE_GROUP_PREFIX + number);
			if (!assign && !event.isControlDownEvent(SELECT_GROUP_PREFIX + number)) continue;
			pressing = false;
			dragging = false;
			addSelection = false;
			if (assign) {
				if (!selected.isEmpty()) {
					groups.put(number, new LinkedHashSet<>(selected));
				}
			} else {
				Set<ShipAPI> group = groups.get(number);
				if (group != null && !group.isEmpty()) {
					if (!event.isControlActivated(ADD_SELECTION_CONTROL)) {
						selected.clear();
					}
					selected.addAll(group);
				}
			}
			return true;
		}
		return false;
	}

	private void selectAt(ViewportAPI viewport) {
		float x = viewport.convertScreenXToWorldX(pointerX);
		float y = viewport.convertScreenYToWorldY(pointerY);
		float firstX = viewport.convertScreenXToWorldX(startX);
		float firstY = viewport.convertScreenYToWorldY(startY);
		Set<ShipAPI> next = BattleNext_RttGeometry.select(selected, eligibleShips(),
				firstX, firstY, x, y, dragging, addSelection);
		selected.clear();
		selected.addAll(next);
	}

	private void orderAt(ViewportAPI viewport, float screenX, float screenY) {
		float x = viewport.convertScreenXToWorldX(screenX);
		float y = viewport.convertScreenYToWorldY(screenY);
		List<ShipAPI> targets = new ArrayList<>();
		for (ShipAPI ship : engine.getShips()) {
			if (ship.isAlive() && !ship.isExpired() && !ship.isFighter() && !ship.isDrone()
					&& !ship.isStation() && !ship.isStationModule()) targets.add(ship);
		}
		ShipAPI target = BattleNext_RttGeometry.hit(targets, x, y);
		if (target != null) {
			for (ShipAPI ship : selected) {
				BattleNext_RttShipAI ai = control(ship);
				if (ship == target) ai.orderMove(new Vector2f(ship.getLocation()));
				else if (target.getOwner() == ship.getOwner()) ai.orderFollow(target);
				else ai.orderEngage(target);
			}
			return;
		}
		Map<ShipAPI, Vector2f> points = BattleNext_RttGeometry.formation(new ArrayList<>(selected),
				new Vector2f(x, y), engine.getMapWidth(), engine.getMapHeight());
		for (ShipAPI ship : selected) {
			control(ship).orderMove(points.get(ship));
		}
	}

	private BattleNext_RttShipAI control(ShipAPI ship) {
		BattleNext_RttShipAI ai = controlled.get(ship);
		if (ai != null) return ai;
		ShipAIPlugin previous = ship.getShipAI();
		String orderFieldKey = BattleNext_OrderField.KEY_PREFIX + ship.getOwner() + (ship.isAlly() ? "_ally" : "");
		Object field = engine.getCustomData().get(orderFieldKey);
		if (field instanceof BattleNext_OrderField orderField) orderField.releaseShip(ship);
		ai = new BattleNext_RttShipAI(ship);
		previousAis.put(ship, previous);
		controlled.put(ship, ai);
		ship.setCustomData(BattleNext_RttShipAI.CONTROL_KEY, ai);
		ship.setShipAI(ai);
		return ai;
	}

	private boolean releaseSelected() {
		boolean released = false;
		for (ShipAPI ship : selected) {
			BattleNext_RttShipAI ai = controlled.remove(ship);
			if (ai == null) continue;
			ShipAIPlugin previous = previousAis.remove(ship);
			ship.removeCustomData(BattleNext_RttShipAI.CONTROL_KEY);
			restoreShipAI(ship, previous);
			released = true;
		}
		return released;
	}

	static void restoreShipAI(ShipAPI ship, ShipAIPlugin previous) {
		if (previous != null) {
			ship.setShipAI(previous);
			return;
		}
		ship.resetDefaultAI();
	}

	private void cleanup() {
		for (ShipAPI ship : controlled.keySet()) {
			ship.removeCustomData(BattleNext_RttShipAI.CONTROL_KEY);
		}
		controlled.clear();
		previousAis.clear();
		selected.clear();
		pressing = false;
		dragging = false;
		addSelection = false;
		groups.clear();
		if (groupText != null) {
			groupText.dispose();
			groupText = null;
		}
		engine.removePlugin(this);
	}

	private void prune() {
		selected.removeIf(ship -> !validSelection(ship));
		for (Set<ShipAPI> group : groups.values()) {
			group.removeIf(ship -> !validSelection(ship));
		}
		controlled.entrySet().removeIf(entry -> {
			if (entry.getKey().isAlive() && !entry.getKey().isExpired()
					&& engine.getShips().contains(entry.getKey())) return false;
			entry.getKey().removeCustomData(BattleNext_RttShipAI.CONTROL_KEY);
			previousAis.remove(entry.getKey());
			return true;
		});
	}

	private List<ShipAPI> eligibleShips() {
		List<ShipAPI> ships = new ArrayList<>();
		for (ShipAPI ship : engine.getShips()) {
			if (validSelection(ship)) ships.add(ship);
		}
		return ships;
	}

	private boolean validSelection(ShipAPI ship) {
		return ship.isAlive() && !ship.isExpired() && engine.getShips().contains(ship)
				&& ship.getOwner() == 0 && ship.getOriginalOwner() == 0
				&& !ship.isFighter() && !ship.isDrone() && !ship.isStation() && !ship.isStationModule();
	}

	private void circle(Vector2f center, float radius, Color color) {
		float[] vertices = new float[RING_SEGMENTS * 2];
		for (int index = 0; index < RING_SEGMENTS; index++) {
			float angle = (float) (Math.PI * 2.0 * index / RING_SEGMENTS);
			vertices[index * 2] = center.x + (float) Math.cos(angle) * radius;
			vertices[index * 2 + 1] = center.y + (float) Math.sin(angle) * radius;
		}
		BattleNext_GeometryRenderer.lineLoop(vertices, color, 1f, 1.5f);
	}
}
