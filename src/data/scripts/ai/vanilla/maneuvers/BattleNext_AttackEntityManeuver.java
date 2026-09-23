package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.AttackEntityManeuver.
 * Attack run against a non-ship entity; either rushes in facing the target or
 * backs away while keeping guns on it.
 */
public class BattleNext_AttackEntityManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private CombatEntityAPI target;
	private BattleNext_ManeuverAPI maneuverAPI;
	private boolean keepFacing;
	private final BattleNext_FlockingAI flockingAI;
	private float desiredFacing;

	public BattleNext_AttackEntityManeuver(ShipAPI ship, CombatEntityAPI target, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.target = target;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.keepFacing = (float) Math.random() > 0.5F;
	}

	@Override
	public void advance(float amount) {
		this.flockingAI.addCollisionObject(this.target);
		float lead = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null) {
			lead = this.maneuverAPI.getAttackingGroup().getRange();
		} else if (this.maneuverAPI.getAttackAI() != null) {
			lead = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.target, lead, !this.maneuverAPI.getAttackAI().isAttackGroupReady());
		this.desiredFacing = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), aimPoint);
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public CombatEntityAPI getTarget() {
		return this.target;
	}

	@Override
	public float getDesiredFacing() {
		return this.keepFacing ? this.ship.getFacing() : this.desiredFacing;
	}

	@Override
	public void init() {
		float lead = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null) {
			lead = this.maneuverAPI.getAttackingGroup().getRange();
		} else if (this.maneuverAPI.getAttackAI() != null) {
			lead = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		Vector2f interceptPoint = BattleNext_ShipAIHelper.getInterceptPoint(this.ship, this.target, lead, false);
		float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.target);
		BattleNext_ShipAIHelper.turnToFace(this.ship, interceptPoint, predictedAngleChange);
		if (this.keepFacing) {
			this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
			this.maneuverAPI.getAIFlags().setFlag(AIFlags.RUN_QUICKLY);
		} else {
			this.ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, -1);
			this.maneuverAPI.getAIFlags().setFlag(AIFlags.TURN_QUICKLY);
		}

		if (this.ship.getFluxLevel() > 0.01F && !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.DO_NOT_VENT)) {
			this.ship.giveCommand(ShipCommand.VENT_FLUX, null, -1);
		}

		if (!BattleNext_ShipAIHelper.isInArc(this.ship.getFacing(), 30.0F, this.ship.getLocation(), this.target.getLocation())) {
			this.maneuverAPI.getAIFlags().setFlag(AIFlags.DO_NOT_USE_FLUX);
			if (BattleNext_ShipAIHelper.isEngineDisabled(this.ship)) {
				this.maneuverAPI.getAIFlags().unsetFlag(AIFlags.DO_NOT_USE_FLUX);
			}
		}
	}

	@Override
	public float getTargetFacing() {
		return this.keepFacing ? this.ship.getFacing() : this.desiredFacing;
	}
}
