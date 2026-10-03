package data.scripts.ai.rtt;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import data.scripts.ai.vanilla.BattleNext_BasicShipAI;
import data.scripts.ai.vanilla.BattleNext_OrderResponseData;
import data.scripts.ai.vanilla.maneuvers.BattleNext_TaskMoveManeuver;
import org.lwjgl.util.vector.Vector2f;

public class BattleNext_RttShipAI extends BattleNext_BasicShipAI {

	public static final String CONTROL_KEY = "BattleNext_RttShipAI_controlled";
	private static final float RESPONSE_DURATION = 2f;
	private static final float FOLLOW_GAP = 300f;
	private static final float MANUAL_SYSTEM_PRIORITY = 3f;
	static final float ARRIVAL_RANGE = 80f;

	public enum CommandType {
		MOVE, ENGAGE, FOLLOW
	}

	private final ShipAPI ship;
	private CommandType commandType;
	private Vector2f destination;
	private Vector2f movementPoint;
	private ShipAPI target;
	private final RttManualSystemControl manualSystemControl;

	public BattleNext_RttShipAI(ShipAPI ship) {
		super(ship);
		this.ship = ship;
		this.manualSystemControl = new RttManualSystemControl(ship, getAIFlags());
	}

	public static boolean isControlled(ShipAPI ship) {
		return ship.getCustomData().get(CONTROL_KEY) instanceof BattleNext_RttShipAI;
	}

	public void orderMove(Vector2f point) {
		commandType = CommandType.MOVE;
		destination = new Vector2f(point);
		movementPoint = new Vector2f(destination);
		target = null;
		setTargetOverride(null);
		cancelCurrentManeuver();
	}

	public void orderEngage(ShipAPI enemy) {
		commandType = CommandType.ENGAGE;
		target = enemy;
		destination = new Vector2f(enemy.getLocation());
		movementPoint = null;
		cancelCurrentManeuver();
	}

	public void orderFollow(ShipAPI friendly) {
		commandType = CommandType.FOLLOW;
		target = friendly;
		destination = new Vector2f(friendly.getLocation());
		movementPoint = followPoint(ship, friendly);
		setTargetOverride(null);
		cancelCurrentManeuver();
	}

	public void rttUseSystemAt(Vector2f point) {
		manualSystemControl.rttUseSystemAt(point);
	}

	@Override
	protected boolean rttShouldAdvanceSystemAI() {
		return !manualSystemControl.rttPriorityActive();
	}

	public CommandType getCommandType() {
		return commandType;
	}

	public Vector2f getDestination() {
		if (commandType == CommandType.FOLLOW && activeTarget()) {
			return followPoint(ship, target);
		}
		if (activeTarget()) return target.getLocation();
		return destination;
	}

	public Vector2f getVisualTargetPoint() {
		if (activeTarget()) return target.getLocation();
		return destination;
	}

	@Override
	public void advance(float amount) {
		if (target != null) {
			if (!activeTarget()) {
				if (commandType == CommandType.FOLLOW) destination = followPoint(ship, target);
				commandType = CommandType.MOVE;
				target = null;
				movementPoint = new Vector2f(destination);
				setTargetOverride(null);
				cancelCurrentManeuver();
			} else {
				destination = new Vector2f(target.getLocation());
			}
		}
		if (commandType == CommandType.ENGAGE) setTargetOverride(target);
		if (commandType != CommandType.ENGAGE) {
			Vector2f point = getDestination();
			if (movementPoint == null) movementPoint = new Vector2f(point);
			else movementPoint.set(point);
			boolean arrived = Vector2f.sub(point, ship.getLocation(), null).length() <= ARRIVAL_RANGE;
			if (arrived && getCurrentManeuver() instanceof BattleNext_TaskMoveManeuver
					|| !arrived && getCurrentManeuver() instanceof BattleNext_RttMoveManeuver) {
				cancelCurrentManeuver();
			}
		}
		super.advance(amount);
		manualSystemControl.rttAdvance(amount);
	}

	static class RttManualSystemControl {
		private final ShipAPI ship;
		private final ShipwideAIFlags flags;
		private float remaining;
		private Vector2f pendingTarget;

		RttManualSystemControl(ShipAPI ship, ShipwideAIFlags flags) {
			this.ship = ship;
			this.flags = flags;
		}

		void rttUseSystemAt(Vector2f point) {
			remaining = MANUAL_SYSTEM_PRIORITY;
			pendingTarget = new Vector2f(point);
			flags.setFlag(AIFlags.SYSTEM_TARGET_COORDS, 1f, pendingTarget);
			ship.getMouseTarget().set(pendingTarget);
			ship.giveCommand(ShipCommand.USE_SYSTEM, pendingTarget, -1);
		}

		boolean rttPriorityActive() {
			return remaining > 0f;
		}

		void rttAdvance(float amount) {
			if (pendingTarget != null) {
				ship.getMouseTarget().set(pendingTarget);
				flags.setFlag(AIFlags.SYSTEM_TARGET_COORDS, 1f, pendingTarget);
				pendingTarget = null;
			}
			remaining = Math.max(0f, remaining - amount);
		}
	}

	@Override
	protected BattleNext_OrderResponseData getRttOrderResponse() {
		if (commandType == null) return null;
		BattleNext_OrderResponseData response = new BattleNext_OrderResponseData(null);
		if (commandType == CommandType.ENGAGE && target != null) {
			response.forcedTarget = target;
			return response;
		}
		Vector2f point = getDestination();
		if (point == null) return null;
		if (Vector2f.sub(point, ship.getLocation(), null).length() <= ARRIVAL_RANGE) {
			response.maneuver = new BattleNext_RttMoveManeuver(ship, this::getDestination);
		} else {
			response.maneuver = BattleNext_TaskMoveManeuver.rttMove(ship, movementPoint, this, ARRIVAL_RANGE);
		}
		response.duration = RESPONSE_DURATION;
		return response;
	}

	private boolean activeTarget() {
		return target != null && target.isAlive() && !target.isExpired()
				&& Global.getCombatEngine().getShips().contains(target);
	}

	public static Vector2f followPoint(ShipAPI ship, ShipAPI target) {
		Vector2f center = target.getLocation();
		Vector2f delta = Vector2f.sub(ship.getLocation(), center, null);
		float length = delta.length();
		if (length < 1f) delta.set(1f, 0f);
		else delta.scale(1f / length);
		delta.scale(ship.getCollisionRadius() + target.getCollisionRadius() + FOLLOW_GAP);
		return Vector2f.add(center, delta, null);
	}
}
