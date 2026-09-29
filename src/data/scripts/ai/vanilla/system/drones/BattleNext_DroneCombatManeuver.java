package data.scripts.ai.vanilla.system.drones;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DroneLauncherShipSystemAPI;
import com.fs.starfarer.api.combat.DroneLauncherShipSystemAPI.DroneOrders;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.maneuvers.BattleNext_Maneuver;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.drones.DroneCombatManeuver.
 */
public class BattleNext_DroneCombatManeuver implements BattleNext_Maneuver {
	private final ShipAPI ship;
	private final ShipAPI mothership;
	private final BattleNext_FlockingAI flockingAI;
	private final BattleNext_DroneAI droneAI;
	private final ShipSystemAPI system;
	private final DroneLauncherShipSystemAPI launcher;
	private final IntervalUtil retargetInterval = new IntervalUtil(0.25F, 0.5F);
	private final JSONObject behavior;
	private CombatEntityAPI target;
	private boolean landing;
	private float elapsedTime;

	public BattleNext_DroneCombatManeuver(ShipAPI ship, ShipAPI mothership, BattleNext_FlockingAI flockingAI,
			ShipSystemAPI system, BattleNext_DroneAI droneAI) {
		this.ship = ship;
		this.mothership = mothership;
		this.flockingAI = flockingAI;
		this.system = system;
		this.launcher = (DroneLauncherShipSystemAPI) system;
		this.droneAI = droneAI;
		JSONArray behaviors = system.getSpecAPI().getSpecJson().optJSONArray("droneBehavior");
		JSONObject selected = behaviors == null || behaviors.length() == 0 ? new JSONObject() : behaviors.optJSONObject(0);
		if (behaviors != null) {
			int index = launcher.getIndex(ship);
			for (int i = 0; i < behaviors.length(); i++) {
				JSONObject option = behaviors.optJSONObject(i);
				if (option == null) continue;
				JSONArray indices = option.optJSONArray("droneIndex");
				if (indices == null) continue;
				for (int j = 0; j < indices.length(); j++) {
					if (indices.optInt(j, -1) == index) selected = option;
				}
			}
		}
		this.behavior = selected == null ? new JSONObject() : selected;
	}

	@Override
	public void advance(float amount) {
		elapsedTime = amount;
		if (target instanceof ShipAPI enemy && enemy.isHulk()) target = null;
		if (target != null && !BattleNext_ShipAIHelper.getEngine().isInPlay(target)) target = null;
		retargetInterval.advance(amount);
		if (retargetInterval.intervalElapsed()) acquireTarget();
	}

	public void acquireTarget() {
		float attackRange = droneAI.getAttackAI().getMaxRange();
		Vector2f orbit = droneAI.getDesiredLocation();
		if (target != null && target.getOwner() != ship.getOwner()
				&& BattleNext_ShipAIHelper.getDistance(orbit, target.getLocation()) < attackRange
						+ BattleNext_ShipAIHelper.getDistanceToEntitySurface(ship.getLocation(), target, false)) return;
		target = null;
		JSONArray priority = behavior.optJSONArray("targetPriority");
		if (priority == null) return;
		for (int index = 0; index < priority.length(); index++) {
			CombatEntityAPI candidate = switch (priority.optString(index)) {
				case "MISSILE" -> BattleNext_ShipAIHelper.getNearestEnemyMissile(ship, orbit, 5000.0F);
				case "FIGHTER" -> BattleNext_ShipAIHelper.getNearestEnemyInRangeNoFighters(ship, orbit, 5000.0F);
				case "SHIP" -> BattleNext_ShipAIHelper.getNearestEnemyOfMinHullSizeInRange(ship, orbit, HullSize.FRIGATE, 5000.0F);
				case "SHIP_TARGET" -> mothership.getShipTarget();
				default -> null;
			};
			if (candidate == null || candidate instanceof ShipAPI enemy && enemy.isHulk()) continue;
			float surface = BattleNext_ShipAIHelper.getDistanceToEntitySurface(ship.getLocation(), candidate, false);
			float distance = BattleNext_ShipAIHelper.getDistance(orbit, candidate.getLocation());
			if (distance <= attackRange + surface + getAttackRange()) {
				target = candidate;
				break;
			}
		}
	}

	private float getAttackRange() {
		return (float) (launcher.getDroneOrders() == DroneOrders.ATTACK
				? behavior.optDouble("freeRoamRange", 0.0F) : behavior.optDouble("holdRoamRange", 0.0F));
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public float getDesiredFacing() {
		return BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), droneAI.getDesiredLocation());
	}

	@Override
	public float getTargetFacing() {
		return Float.MAX_VALUE;
	}

	@Override
	public void init() {
		// approximation: the engine DroneRing and SmoothMovementModuleCombatCopy
		// expose direct position and velocity control; public drone indices and
		// command-based steering preserve orbit and recall intent.
		int index = Math.max(0, launcher.getIndex(ship));
		float angle = (float) behavior.optDouble("initialOrbitAngle", 0.0F)
				+ index * 137.50776F + elapsedTime * (float) behavior.optDouble("orbitSpeed", 0.0F)
						* (float) behavior.optDouble("orbitDir", 1.0F) / Math.max(1.0F, ship.getCollisionRadius());
		float radius = mothership.getCollisionRadius() + (float) behavior.optDouble("orbitRadius", 0.0F);
		Vector2f offset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(angle);
		offset.scale(radius);
		Vector2f desired = Vector2f.add(mothership.getLocation(), offset, null);
		if (launcher.getDroneOrders() == DroneOrders.RECALL) {
			Vector2f landingPoint = launcher.getLandingLocation(ship);
			if (landingPoint != null) desired.set(landingPoint);
			if (BattleNext_ShipAIHelper.getDistance(ship.getLocation(), desired) < 150.0F && !landing) {
				ship.beginLandingAnimation(mothership);
				landing = true;
			}
		} else {
			if (landing) {
				ship.abortLanding();
				landing = false;
			}
			if (target != null && getAttackRange() > 0.0F) {
				float distance = BattleNext_ShipAIHelper.getDistance(ship.getLocation(), target.getLocation());
				float attackRadius = droneAI.getAttackAI().getAttackRange() * 0.75F + target.getCollisionRadius();
				if (distance < attackRadius + getAttackRange()) {
					Vector2f toward = Vector2f.sub(target.getLocation(), desired, null);
					if (toward.length() > 1.0F) toward.normalise();
					toward.scale(Math.min(getAttackRange(), distance - attackRadius));
					Vector2f.add(desired, toward, desired);
				}
				droneAI.setEngaged(distance - target.getCollisionRadius() <= droneAI.getAttackAI().getAttackRange());
			}
		}
		if (target != null && behavior.optBoolean("faceEnemy", false)) {
			BattleNext_ShipAIHelper.turnToFace(ship, target.getLocation(), 0.0F);
		} else {
			String facingMode = behavior.optString("defaultFacing", "MATCH_SHIP_FACING");
			float facing = switch (facingMode) {
				case "MATCH_DRONE_HEADING" -> ship.getVelocity().length() > 5.0F
						? BattleNext_ShipAIHelper.getAngleFast(ship.getVelocity()) : mothership.getFacing();
				case "MATCH_SHIP_HEADING" -> mothership.getVelocity().length() > 5.0F
						? BattleNext_ShipAIHelper.getAngleFast(mothership.getVelocity()) : mothership.getFacing();
				case "AWAY_FROM_SHIP" -> BattleNext_ShipAIHelper.getAngleFast(mothership.getLocation(), ship.getLocation());
				default -> mothership.getFacing();
			};
			BattleNext_ShipAIHelper.turnToFace(ship, 0.0F, facing);
		}
		float heading = BattleNext_ShipAIHelper.getAngleFast(ship.getLocation(), desired);
		BattleNext_ShipAIHelper.giveMoveCommands(ship, heading, ship.getMaxSpeed());
		droneAI.getDesiredLocation().set(desired);
	}

	@Override
	public CombatEntityAPI getTarget() {
		return target;
	}
}
