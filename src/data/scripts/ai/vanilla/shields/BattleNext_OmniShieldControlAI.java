package data.scripts.ai.vanilla.shields;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.*;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.attack.BattleNext_AttackingGroup;
import data.scripts.ai.vanilla.attack.BattleNext_WeaponGroupEvaluation;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.Iterator;

// vanilla implements the engine AI marker interface as well; its advance is covered by
// BattleNext_Orderable and its render() was an engine-side debug hook and is dropped
public class BattleNext_OmniShieldControlAI implements BattleNext_ManeuverAPI, BattleNext_Orderable, ShipAIPlugin {
	private CombatEngineAPI engine;
	private ShipAPI ship;
	private BattleNext_FlockingAI flockingAI;
	private BattleNext_ThreatEvalAI threatEvalAI;
	private BattleNext_ShieldAI shieldAI = null;
	private ShipwideAIFlags aiFlags = new ShipwideAIFlags();
	private boolean avoidingCollision;
	private IntervalUtil collisionCheckTracker = new IntervalUtil(0.0501F, 0.0835F);
	private ShipAIConfig config;
	private int nearbyFriendlyShipCount = 0;
	private ShipAPI target = null;

	public void forceCircumstanceEvaluation() {
		this.collisionCheckTracker.forceIntervalElapsed();
		this.threatEvalAI.getEvalIntervalTracker().forceIntervalElapsed();
	}

	@Override
	public ShipAIConfig getConfig() {
		return this.config;
	}

	public BattleNext_OmniShieldControlAI(ShipAPI ship, ShipwideAIFlags aiFlags) {
		this.aiFlags = aiFlags;
		this.ship = ship;
		this.engine = BattleNext_ShipAIHelper.getEngine();
		// vanilla passes null for the engine AI: this AI issues no engine commands itself
		this.flockingAI = new BattleNext_FlockingAI(ship, null, this);
		this.threatEvalAI = new BattleNext_ThreatEvalAI(ship, this, this.flockingAI);
		this.shieldAI = new BattleNext_OmniShieldAngleAI(ship, this.aiFlags, true);
	}

	@Override
	public ShipwideAIFlags getAIFlags() {
		return this.aiFlags;
	}

	@Override
	public void advance(float amount) {

		this.aiFlags.advance(amount);
		this.threatEvalAI.advance(amount);
		this.collisionCheckTracker.advance(amount);
		float collisionCheckDuration = 0.0F;
		boolean checkCollisions = this.collisionCheckTracker.intervalElapsed();
		if (checkCollisions || this.avoidingCollision) {
			if (this.avoidingCollision) {
				collisionCheckDuration = amount;
			} else {
				collisionCheckDuration = this.collisionCheckTracker.getIntervalDuration();
			}

			this.avoidShipCollisions();
			this.avoidMissiles();
			boolean hasAvoidanceVector = this.flockingAI.getAvoidanceVector() != null;
			if (!hasAvoidanceVector) {
				this.avoidAsteroidCollisions();
			}
		}

		this.avoidingCollision = this.flockingAI.hasCollisionWarning();
		if (this.ship.getFixedLocation() != null || this.ship.getStationSlot() != null) {
			this.avoidingCollision = false;
		}

		if (collisionCheckDuration > 0.0F) {
			this.flockingAI.updateMaxSpeed(collisionCheckDuration);
		}

		Vector2f avoidanceVector = this.flockingAI.getAvoidanceVector();
		if (this.shieldAI != null) {
			this.shieldAI.advance(amount, this.threatEvalAI, avoidanceVector, this.flockingAI.getAvoidanceLocation(), null);
		}
	}

	public boolean isAvoidingCollision() {
		return this.avoidingCollision;
	}

	private void avoidShipCollisions() {

		this.nearbyFriendlyShipCount = 0;
		ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemy(this.ship);
		boolean hasEnemy = nearestEnemy != null;
		// intentionally kept dead: computed in vanilla and never read
		boolean unusedDroneFlag = false;
		float enemyAngle = 0.0F;
		if (hasEnemy) {
			enemyAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), nearestEnemy.getLocation());
		}

		// intentionally kept dead: enemyAngle is never read in vanilla
		float checkRange = this.ship.getCollisionRadius() * 2.0F + 2000.0F;
		Iterator<Object> check = this.engine.getAiGridShips().getCheckIterator(this.ship.getLocation(), checkRange, checkRange);

		while (check.hasNext()) {
			if (check.next() instanceof ShipAPI other && this.ship != other && !other.isFighter() && !other.isShuttlePod()) {
				boolean ignoreShip = this.ship.getOwner() == other.getOwner() && other.getEngineController().isDisabled();
				boolean friendlyDrone = other.isDrone() && other.getCollisionClass() == CollisionClass.SHIP;
				ignoreShip |= friendlyDrone;
				if ((other.getHullSize().ordinal() >= this.ship.getHullSize().ordinal() || ignoreShip || other.isHulk())
					&& (
						!other.isFrigate()
							|| !this.ship.isFrigate()
							|| !(other.getMass() < this.ship.getMass())
							|| ignoreShip
							|| other.isHulk()
							|| other == this.engine.getPlayerShip()
					)) {
					// intentionally kept dead: computed in vanilla and never read
					boolean unusedCollisionEntry = false;
					boolean sameOwner = this.ship.getOwner() == other.getOwner();
					float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation());
					float combinedRadius = this.ship.getCollisionRadius() + other.getCollisionRadius();
					if (sameOwner && distance < combinedRadius * 3.0F) {
						this.nearbyFriendlyShipCount++;
					}

					if (friendlyDrone) {
						this.flockingAI.getCollisionAnalysis().addShip(other, true);
					} else {
						this.flockingAI.addCollisionEntityByType(other);
					}
				}
			}
		}
	}

	private void avoidAsteroidCollisions() {
		Vector2f location = this.ship.getLocation();
		float checkRange = this.ship.getCollisionRadius() * 2.0F + 500.0F;
		Iterator<Object> check = this.engine.getAiGridAsteroids().getCheckIterator(location, checkRange, checkRange);

		while (check.hasNext()) {
			if (check.next() instanceof CombatAsteroidAPI asteroid && (asteroid.getMass() * 1.5F >= this.ship.getMass() || this.ship.getHitpoints() < 100.0F)) {
				this.flockingAI.addCollisionEntityByType(asteroid);
			}
		}
	}

	private void avoidMissiles() {
		int owner = this.ship.getOwner();
		float checkRange = 5000.0F + this.ship.getCollisionRadius() * 2.0F;
		Iterator<Object> check = BattleNext_ShipAIHelper.getEngine().getAiGridMissiles().getCheckIterator(this.ship.getLocation(), checkRange, checkRange);

		while (check.hasNext()) {
			if (check.next() instanceof MissileAPI missile
				&& (missile.getOwner() != owner || missile.isFizzling() || missile.getCollisionClass() == CollisionClass.MISSILE_FF)
				&& (missile.getSource() != this.ship || missile.isFizzling())) {
				this.flockingAI.addCollisionEntity(missile);
			}
		}
	}

	@Override
	public BattleNext_FlockingAI getFlockingAI() {
		return this.flockingAI;
	}

	@Override
	public float getCollisionRadius() {
		return this.ship.getCollisionRadius();
	}

	@Override
	public Vector2f getLocation() {
		return this.ship.getLocation();
	}

	@Override
	public boolean isFighter() {
		return false;
	}

	@Override
	public BattleNext_ThreatEvalAI getThreatEvaluator() {
		return this.threatEvalAI;
	}

	@Override
	public BattleNext_ShieldAI getShieldAI() {
		return this.shieldAI;
	}

	@Override
	public void cancelCurrentManeuver() {

	}

	@Override
	public boolean doesShipVelocityMatterForAim() {
		return false;
	}

	@Override
	public BattleNext_AttackAIModule getAttackAI() {
		return null;
	}

	@Override
	public BattleNext_AttackingGroup getAttackingGroup() {
		return null;
	}

	@Override
	public BattleNext_FiringSolutionEval getEval() {
		return null;
	}

	@Override
	public BattleNext_WeaponGroupEvaluation getEvaluationFor(WeaponGroupAPI group) {
		return null;
	}

	@Override
	public float getMaxFiringRange() {
		return 0.0F;
	}

	@Override
	public float getOptimalNonMissileRange() {
		return 0.0F;
	}

	@Override
	public boolean keepTarget() {
		return false;
	}

	@Override
	public void setMaxTargetRange(float maxTargetRange) {

	}

	@Override
	public boolean canObeyOrders() {
		return false;
	}

	@Override
	public BattleNext_DirectOrders getOrders() {
		return null;
	}

	@Override
	public boolean isEngaged() {
		return false;
	}

	@Override
	public void notifyNewOrderGivenToBattleGroup() {

	}

	@Override
	public ShipAPI getShip() {
		return this.ship;
	}

	public boolean needsRefit() {
		return false;
	}

	public void setDoNotFireDelay(float delay) {

	}
}
