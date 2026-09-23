package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.maneuvers.WeaponAssistAttackManeuver.
 * Backs away from the target while weapon groups bear; vents when the flux
 * budget allows it.
 */
public class BattleNext_WeaponAssistAttackManeuver implements BattleNext_Maneuver {
	private ShipAPI ship;
	private ShipAPI targetShip;
	private BattleNext_ManeuverAPI maneuverAPI;
	private boolean ventRequested = false;
	private final BattleNext_FlockingAI flockingAI;
	private float desiredFacing = Float.MAX_VALUE;
	private float facingAngle = Float.MAX_VALUE;
	private BattleNext_WeaponFireControlHelper weaponFireControlHelper = new BattleNext_WeaponFireControlHelper();

	public BattleNext_WeaponAssistAttackManeuver(ShipAPI ship, ShipAPI targetShip, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.targetShip = targetShip;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.weaponFireControlHelper.init(ship, targetShip, maneuverAPI);
	}

	@Override
	public void advance(float amount) {
		if (this.ventRequested) {
			this.maneuverAPI.getAIFlags().setFlag(AIFlags.DO_NOT_USE_FLUX);
			if (this.ship.getFluxLevel() > 0.01F) {
				this.ship.giveCommand(ShipCommand.VENT_FLUX, null, -1);
			}
		}

		if (this.ship.getEngineController().isDisabled()) {
			this.maneuverAPI.getAIFlags().unsetFlag(AIFlags.DO_NOT_USE_FLUX);
		}

		if (BattleNext_ShipAIHelper.isShipAboutToBeHit(this.ship, this.targetShip)) {
			this.flockingAI.addCollisionObject(this.targetShip);
		}

		this.maneuverAPI.getAIFlags().setFlag(AIFlags.BACKING_OFF);
		float lead = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null) {
			lead = this.maneuverAPI.getAttackingGroup().getRange();
		} else if (this.maneuverAPI.getAttackAI() != null) {
			lead = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetShip, lead, !this.maneuverAPI.getAttackAI().isAttackGroupReady());
		this.desiredFacing = BattleNext_ShipAIHelper.getAngle(this.ship.getLocation(), aimPoint);
		float fluxThreshold = 0.95F;
		BattleNext_ThreatData maxThreat = this.maneuverAPI.getThreatEvaluator().getMaxThreatData();
		if (maxThreat != null && maxThreat.getShip().getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
			fluxThreshold -= 0.09F * (maxThreat.getShip().getHullSize().ordinal() - this.ship.getHullSize().ordinal() + 1.0F);
		}

		// intentionally kept dead: vanilla overwrites the scaled threshold with 1.0F
		fluxThreshold = 1.0F;
		boolean backOff = this.ship.getFluxTracker().getOverloadTimeRemaining() > 2.0F
			|| this.ship.getFluxTracker().isVenting() && this.ship.getFluxTracker().getTimeToVent() > 2.0F
			|| this.ship.getFluxLevel() > fluxThreshold;
		boolean lowHitpoints = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
		backOff |= lowHitpoints && (!(this.ship.getFluxLevel() < 0.8F) || (this.ship.getShield() == null || !this.ship.getShield().isOn()));
		backOff |= maxThreat != null && !maxThreat.getShip().isFighter() && this.ship.getShield() != null && this.ship.getShield().isOff();
		backOff |= this.ship.getShield() == null && this.ship.getFluxLevel() > 0.95F;
		backOff &= this.maneuverAPI.getEval() != null
			&& (
				this.maneuverAPI.getEval().hasDetectedIncomingDamage()
					|| this.maneuverAPI.getThreatEvaluator().getTotalThreat() > 0.05F && !this.maneuverAPI.getThreatEvaluator().isAllThreatsValid()
			);
		backOff &= this.maneuverAPI.getThreatEvaluator().getMaxThreatData() != null;
		if (this.ship.getShield() != null && isFrontShield(this.ship)) {
			backOff &= this.ship.getArmorGrid().getArmorRating() > 50.0F * this.ship.getHullSize().ordinal();
		}

		backOff &= !this.ship.isPhased();
		backOff &= !BattleNext_ShipAIHelper.isPhaseShip(this.ship) || !(this.ship.getFluxLevel() < 0.85F);
		if (!this.maneuverAPI.getConfig().turnToFaceWithUndamagedArmor) {
			backOff = false;
		}

		if (backOff) {
			// intentionally kept dead: vanilla pins this flag to false right away
			boolean alwaysFalse = true;
			alwaysFalse = false;
			if (!alwaysFalse) {
				float bestArmorAngle = this.computeBestArmorAngle(this.ship.getFacing(), isFrontShield(this.ship) ? 150.0F : 210.0F);
				float armorOffset = bestArmorAngle - this.ship.getFacing();
				this.desiredFacing -= armorOffset;
			}
		} else {
			float angleOffset = this.weaponFireControlHelper.getAbsAngleOffset();
			if (this.weaponFireControlHelper.needsLeftTurn()) {
				this.desiredFacing -= angleOffset;
			} else if (this.weaponFireControlHelper.needsRightTurn()) {
				this.desiredFacing += angleOffset;
			}
		}

		this.facingAngle = this.computeMoveAngle() + 180.0F;
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public CombatEntityAPI getTarget() {
		return this.targetShip;
	}

	@Override
	public float getDesiredFacing() {
		return this.facingAngle == Float.MAX_VALUE ? this.computeMoveAngle() + 180.0F : this.facingAngle;
	}

	@Override
	public void init() {
		float predictedAngleChange = BattleNext_ShipAIHelper.getPredictedAngleChange(this.ship, this.targetShip);
		BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, this.desiredFacing, predictedAngleChange);
		BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.getDesiredFacing(), 1000.0F);
	}

	private float computeMoveAngle() {
		float lead = Float.MAX_VALUE;
		if (this.maneuverAPI.getAttackingGroup() != null) {
			lead = this.maneuverAPI.getAttackingGroup().getRange();
		} else if (this.maneuverAPI.getAttackAI() != null) {
			lead = this.maneuverAPI.getAttackAI().getAverageCooldown();
		}

		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(this.ship, this.targetShip, lead, !this.maneuverAPI.getAttackAI().isAttackGroupReady());
		return BattleNext_ShipAIHelper.getAngle(this.ship.getLocation(), aimPoint);
	}

	@Override
	public float getTargetFacing() {
		return this.desiredFacing;
	}

	// approximation: engine ArmorEvaluator.getBestArmorAngle is not in the API;
	// sample slices across the arc and return the angle of the thickest one
	private float computeBestArmorAngle(float facing, float arc) {
		float bestAngle = facing;
		float bestArmor = -1.0F;
		float step = 15.0F;
		for (float angle = facing - arc / 2.0F; angle <= facing + arc / 2.0F; angle += step) {
			float armor = this.ship.getAverageArmorInSlice(angle - step / 2.0F, step);
			if (armor > bestArmor) {
				bestArmor = armor;
				bestAngle = angle;
			}
		}

		return bestAngle;
	}

	// approximation: engine ShieldAPI.isFront is not present in the mod-visible
	// API; the shield type is used instead
	private static boolean isFrontShield(ShipAPI ship) {
		return ship.getShield() != null && ship.getShield().getType() == ShieldAPI.ShieldType.FRONT;
	}
}
