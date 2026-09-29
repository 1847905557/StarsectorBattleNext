package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_BasicShipAI;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ShipStatisticsTracker;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.system.WingEngineModSystemAI.
 */
public class BattleNext_WingEngineModSystemAI implements BattleNext_SystemAI {
	private final ShipAPI ship;
	private final ShipSystemAPI system;
	private final BattleNext_ThreatEvalAI threatEval;
	private final BattleNext_FlockingAI flockingAI;
	private final IntervalUtil interval = new IntervalUtil(0.25F, 0.5F);
	private final float maxSpeedBonus;

	public BattleNext_WingEngineModSystemAI(ShipAPI ship, BattleNext_ThreatEvalAI threatEval, BattleNext_FlockingAI flockingAI) {
		this.ship = ship;
		this.system = ship.getSystem();
		this.threatEval = threatEval;
		this.flockingAI = flockingAI;
		// approximation: the engine applies the stats script to a fresh ShipStats
		// instance; the public live max-speed bonus is the available estimate.
		this.maxSpeedBonus = ship.getMutableStats().getMaxSpeed().getModifiedValue()
				- ship.getMutableStats().getMaxSpeed().getBaseValue();
	}

	@Override
	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		interval.advance(amount);
		if (system.isActive() || system.getCooldownRemaining() > 0.0F || system.isOutOfAmmo()) return;
		if (ship.getEngineController().isDisabled()) return;
		if (ship.isFighter() && ship.getWing() != null && !ship.getWing().isLeader(ship)) {
			ShipAPI leader = ship.getWing().getLeader();
			if (leader != null && leader.getSystem() != null && leader.getSystem().isOn()) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			}
			return;
		}
		if (!interval.intervalElapsed()) return;
		boolean freeSpeed = maxSpeedBonus > 50.0F
				|| ship.getFluxLevel() == 0.0F && system.getFluxPerSecond() <= 0.0F && system.getFluxPerUse() <= 0.0F;
		AssignmentInfo assignment = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner())
				.getTaskManager(ship.isAlly()).getAssignmentFor(ship);
		Vector2f assignmentPoint = assignment == null || assignment.getTarget() == null
				? null : assignment.getTarget().getLocation();
		float assignmentDistance = assignmentPoint == null ? 0.0F
				: BattleNext_ShipAIHelper.getDistance(ship.getLocation(), assignmentPoint);
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		boolean engaged = ai != null && ai.isEngaged();
		boolean safe = threatEval.getTotalThreat() <= 0.0F || threatEval.isAllThreatsValid();
		boolean travel = !engaged && assignmentDistance > 1000.0F && safe;
		travel |= target == null && !engaged && ship.getVelocity().length() > ship.getMaxSpeed() * 0.5F && safe;
		if (freeSpeed && (travel || ship.isRetreating())) {
			ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
			return;
		}
		if (system.getFluxPerUse() > 0.0F
				&& ship.getFluxLevel() + system.getFluxPerUse() / ship.getMaxFlux() > 0.75F) return;
		float desiredFacing = flockingAI.getDesiredFacing();
		if (desiredFacing != Float.MAX_VALUE) {
			float angle = BattleNext_ShipAIHelper.getAngleDiff(desiredFacing, ship.getFacing());
			float direction = BattleNext_ShipAIHelper.getTurnDirection(ship.getFacing(), desiredFacing);
			float turnTime = angle / ship.getMaxTurnRate();
			if (direction != Math.signum(ship.getAngularVelocity())) {
				turnTime += (Math.abs(ship.getAngularVelocity()) + ship.getMaxTurnRate() / 2.0F)
						/ ship.getTurnAcceleration();
			}
			if (angle > 90.0F || turnTime > 2.0F || turnTime > system.getCooldown() * 0.5F) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
				return;
			}
		}
		float heading = flockingAI.getAvoidanceRadius();
		if (heading != Float.MAX_VALUE && (assignmentPoint == null || assignmentDistance > 500.0F)) {
			Vector2f desiredVelocity = BattleNext_ShipAIHelper.getUnitVectorAtAngle(heading);
			desiredVelocity.scale(flockingAI.getCurrentMaxSpeed());
			float acceleration = (ship.getAcceleration() + ship.getDeceleration()) / 2.0F;
			float velocityGap = BattleNext_ShipAIHelper.getDistance(desiredVelocity, ship.getVelocity());
			float angle = ship.getVelocity().length() > 5.0F
					? BattleNext_ShipAIHelper.getAngleDiff(desiredVelocity, ship.getVelocity())
					: BattleNext_ShipAIHelper.getAngleDiff(desiredVelocity,
							BattleNext_ShipAIHelper.getUnitVectorAtAngle(ship.getFacing()));
			float stopTime = velocityGap / acceleration;
			if (angle > 90.0F || stopTime > 2.0F || stopTime > system.getCooldown() * 0.5F) {
				ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
				return;
			}
		}
		BattleNext_ShipStatisticsTracker tracker = BattleNext_ShipAIHelper.getStatisticsTracker(ship);
		boolean backingOff = tracker.fitBackingOff(5.0F).avgX > 0.8F;
		boolean hardFluxStable = tracker.fitHardFluxLevel(5.0F).slope > -0.02F && ship.getHardFluxLevel() > 0.03F;
		boolean distanceGrowing = tracker.fitDistanceToEnemy(5.0F).slope > 20.0F;
		if (backingOff && hardFluxStable && !distanceGrowing) ship.giveCommand(ShipCommand.USE_SYSTEM, null, -1);
	}
}
