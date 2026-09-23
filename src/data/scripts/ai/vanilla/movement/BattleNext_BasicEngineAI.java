package data.scripts.ai.vanilla.movement;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;

/**
 * Port of engine class com.fs.starfarer.combat.ai.movement.BasicEngineAI.
 * Translates a desired heading/facing into concrete accelerate/strafe/turn commands.
 */
public class BattleNext_BasicEngineAI implements BattleNext_EngineAI {
	// intentionally kept dead: vanilla declares these thresholds and never reads them
	private static final float ACCEPTABLE_DISTANCE = 200.0F;
	private static final float ACCEPTABLE_FACING_DIFF = 1.0F;
	private static final float ACCEPTABLE_HEADING_DIFF = 1.0F;
	private static final float ACCEPTABLE_SPEED_DIFF = 0.01F;
	private static final float STOPPED_VELOCITY_THRESHOLD = 0.0F;
	private float desiredFacing = Float.MAX_VALUE;
	private float desiredHeading = Float.MAX_VALUE;
	private float desiredSpeedFraction = 1.0F;
	private boolean alwaysAccelerate = false;
	private boolean isAvoidingCollision = false;
	private float sinceHeadingChange = 0.0F;
	private ShipAPI ship;
	// intentionally kept dead: vanilla writes this delay and never reads it
	private float delay = 1.0F + (float)Math.random() * 2.0F;

	public BattleNext_BasicEngineAI(ShipAPI ship) {
		this.ship = ship;
	}

	@Override
	public void setAlwaysAccelerate(boolean alwaysAccelerate) {
		this.alwaysAccelerate = alwaysAccelerate;
	}

	@Override
	public boolean isAlwaysAccelerate() {
		return this.alwaysAccelerate;
	}

	@Override
	public void setDesiredFacing(float facing) {
		this.desiredFacing = facing;
	}

	public boolean isAvoidingCollision() {
		return this.isAvoidingCollision;
	}

	@Override
	public void setAvoidingCollision(boolean avoidingCollision) {
		this.isAvoidingCollision = avoidingCollision;
	}

	@Override
	public void setDesiredHeading(float heading, float speedFraction) {
		this.desiredSpeedFraction = speedFraction;
		this.delay = 0.5F + (float)Math.random() * 0.5F;
		this.sinceHeadingChange = 0.0F;
		this.desiredHeading = heading;
	}

	@Override
	public void advance(float amount) {
		this.sinceHeadingChange += amount;
		if (this.desiredFacing != Float.MAX_VALUE) {
			if (this.desiredHeading != Float.MAX_VALUE) {
				BattleNext_ShipAIHelper.giveMoveCommands(this.ship, this.desiredHeading, this.desiredSpeedFraction * 1000.0F);
			}

			BattleNext_ShipAIHelper.turnToFace(this.ship, 0.0F, this.desiredFacing);
		} else if (this.desiredHeading != Float.MAX_VALUE) {
			this.establishHeading(this.desiredHeading, this.desiredSpeedFraction, amount, true);
		}
	}

	private boolean establishHeading(float heading, float speedFraction, float amount, boolean allowTurnaround) {
		float velocityAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getVelocity());
		float headingDiff = BattleNext_ShipAIHelper.getAngleDiff(velocityAngle, heading);
		boolean stopped = this.ship.getVelocity().length() <= 0.0F;
		float facingDiff = BattleNext_ShipAIHelper.getAngleDiff(this.ship.getFacing(), heading);
		float speedChangeDir = this.getSpeedChangeDir(speedFraction);
		if (!stopped && !(headingDiff > 1.0F) && speedChangeDir == 0.0F) {
			return this.turnTowards(this.desiredHeading, amount);
		}

		boolean reverseHeading = false;
		boolean strafeAway = this.isAvoidingCollision && facingDiff > 45.0F && facingDiff < 135.0F;
		if (!strafeAway && allowTurnaround && this.isAvoidingCollision && facingDiff > 135.0F) {
			reverseHeading = true;
		}

		if (this.alwaysAccelerate) {
			this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
			reverseHeading = false;
			strafeAway = false;
		}

		if ((!stopped || this.desiredSpeedFraction != 0.0F) && !this.alwaysAccelerate && !strafeAway) {
			if (!stopped && !(headingDiff > 1.0F) && !(speedChangeDir < 0.0F)) {
				if (speedChangeDir > 0.0F) {
					if (facingDiff <= 30.0F) {
						this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
					} else if (facingDiff >= 135.0F) {
						this.ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, -1);
					}
				}
			} else if (facingDiff <= 30.0F) {
				if (speedChangeDir < 0.0F) {
					this.ship.giveCommand(ShipCommand.DECELERATE, null, -1);
				} else {
					this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
				}
			} else if (facingDiff >= 125.0F) {
				if (speedChangeDir < 0.0F) {
					this.ship.giveCommand(ShipCommand.DECELERATE, null, -1);
				} else {
					this.ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, -1);
				}
			}
		}

		float turnTarget = this.desiredHeading;
		if (reverseHeading) {
			turnTarget += 180.0F;
		}

		if (BattleNext_ShipAIHelper.getAngleDiff(heading, this.ship.getFacing() + 90.0F) <= 55.0F) {
			this.ship.giveCommand(ShipCommand.STRAFE_LEFT, null, -1);
			if (strafeAway) {
				turnTarget -= 90.0F;
			}
		} else if (BattleNext_ShipAIHelper.getAngleDiff(heading, this.ship.getFacing() - 90.0F) <= 55.0F) {
			this.ship.giveCommand(ShipCommand.STRAFE_RIGHT, null, -1);
			if (strafeAway) {
				turnTarget += 90.0F;
			}
		}

		return this.turnTowards(turnTarget, amount);
	}

	private float getSpeedChangeDir(float speedFraction) {
		float currentFraction = this.ship.getVelocity().length() / this.ship.getMaxSpeed();
		if (currentFraction - 0.01F > speedFraction) {
			return -1.0F;
		} else {
			return currentFraction + 0.01F < this.desiredSpeedFraction ? 1.0F : 0.0F;
		}
	}

	private boolean turnTowards(float targetFacing, float amount) {
		float angularVelocity = this.ship.getAngularVelocity();
		float absAngularVelocity = Math.abs(angularVelocity);
		// approximation: ShipEngineControllerAPI has no getEffectiveTurnAcceleration(); ShipAPI.getTurnAcceleration() is the nearest equivalent
		// intentionally kept dead: vanilla computes these turn accelerations and never reads them
		float unusedTurnAccelA = this.ship.getTurnAcceleration() * amount;
		float unusedTurnAccelB = this.ship.getTurnAcceleration() * amount;
		float turnDeceleration = this.ship.getEngineController().getTurnDeceleration();
		float decelerationTime = absAngularVelocity / turnDeceleration;
		float stopArc = absAngularVelocity * decelerationTime - 0.5F * turnDeceleration * decelerationTime * decelerationTime;
		float projectedFacing = this.ship.getFacing() + Math.signum(angularVelocity) * stopArc;
		float projectedDiff = BattleNext_ShipAIHelper.getAngleDiff(projectedFacing, targetFacing);
		float currentDiff = BattleNext_ShipAIHelper.getAngleDiff(this.ship.getFacing(), targetFacing);
		if (projectedDiff > 1.0F) {
			float turnDirection = BattleNext_ShipAIHelper.getTurnDirection(this.ship.getFacing(), targetFacing);
			if (Math.signum(angularVelocity) == Math.signum(turnDirection) && stopArc > currentDiff) {
				turnDirection = -turnDirection;
			}

			if (turnDirection < 0.0F) {
				this.ship.giveCommand(ShipCommand.TURN_RIGHT, null, -1);
			} else {
				if (!(turnDirection > 0.0F)) {
					return false;
				}

				this.ship.giveCommand(ShipCommand.TURN_LEFT, null, -1);
			}

			return true;
		} else {
			return false;
		}
	}

	// vanilla had an empty render() override here; the engine-side debug hook is dropped along with the marker interface
}
