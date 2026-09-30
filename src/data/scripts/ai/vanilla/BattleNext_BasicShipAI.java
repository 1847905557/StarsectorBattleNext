package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.attack.BattleNext_AttackAIModule;
import data.scripts.ai.vanilla.attack.BattleNext_AttackingGroup;
import data.scripts.ai.vanilla.attack.BattleNext_WeaponGroupEvaluation;
import data.scripts.ai.vanilla.maneuvers.*;
import data.scripts.ai.vanilla.movement.BattleNext_BasicEngineAI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import data.scripts.ai.vanilla.shields.BattleNext_FrontShieldAI;
import data.scripts.ai.vanilla.shields.BattleNext_OmniShieldAngleAI;
import data.scripts.ai.vanilla.shields.BattleNext_ShieldAI;
import data.scripts.ai.vanilla.system.BattleNext_PhaseCloakSystemAI;
import data.scripts.ai.vanilla.system.BattleNext_SystemAI;
import data.scripts.ai.vanilla.system.BattleNext_SystemAIFactory;
import org.lwjgl.util.vector.Vector2f;

import java.lang.ref.WeakReference;
import java.util.Iterator;

/**
 * Port of engine class com.fs.starfarer.combat.ai.BasicShipAI, the default
 * combat AI of every non-fighter ship.
 * Orchestrates threat evaluation, maneuver picking, weapon fire, shields,
 * venting, order response and collision avoidance.
 *
 * Deviations from vanilla:
 * - The engine ownership check (ship.getAI() == this) is dropped: via
 *   ShipAPI.setShipAI the engine wraps external AIs, so the raw identity
 *   comparison can never hold for this port.
 * - Reads of ship.getAIFlags() are replaced with this.aiFlags, which is what
 *   the engine-side accessor returns for the vanilla AI anyway.
 * - Ship.isPiece() (split station pieces) has no API equivalent and is
 *   approximated by !isAlive().
 */
public class BattleNext_BasicShipAI implements ShipAIPlugin, BattleNext_ManeuverAPI, BattleNext_Orderable {
	private static final String INSTANCE_KEY = "BattleNext_BasicShipAI_instance";
	// intentionally final here: vanilla allowed toggling this dev flag at runtime,
	// but no ported code ever writes it
	public static final boolean ALLOW_OLD_STRAFE = false;
	private static final float MIN_ATTACK_MODE_TIME = 10.0F;
	private static final float ATTACK_MODE_TIME_RANGE = 10.0F;

	private CombatEngineAPI engine;
	private ShipAPI ship;
	private BattleNext_BasicEngineAI engineAI;
	private BattleNext_FlockingAI flockingAI;
	private BattleNext_AttackAIModule attackAI;
	private BattleNext_ThreatEvalAI threatEvalAI;
	private BattleNext_ShieldAI shieldAI = null;
	private BattleNext_AttackMode attackMode;
	private float timeInMode = 10.0F;
	private float maxTimeInMode = 0.0F;
	private BattleNext_ManeuverManager sequence;
	private BattleNext_OrderResponseModule orderResponseModule;
	private ShipwideAIFlags aiFlags = new ShipwideAIFlags();
	private BattleNext_VentModule ventModule;
	private BattleNext_FighterPullbackModule fighterPullbackModule;
	private BattleNext_BehaviorModule behaviorModule;
	private BattleNext_SystemAI systemAI;
	private IntervalUtil collisionCheckTracker = new IntervalUtil(0.0501F, 0.0835F);
	private BattleNext_FiringSolutionEval eval;
	private ShipAIConfig config;
	private Vector2f prevCollisionDir = new Vector2f();
	private BattleNext_Maneuver lastThreatResponse = null;
	private int nearbyFriendlyShipCount = 0;
	private ShipAPI target = null;
	private float maxAttackRange = Float.MAX_VALUE;
	private boolean avoidingCollision;
	private boolean forceTarget = false;
	private boolean badTarget = false;
	private ShipAPI targetOverride = null;

	@Override
	public void forceCircumstanceEvaluation() {
		this.collisionCheckTracker.forceIntervalElapsed();
		this.threatEvalAI.getEvalIntervalTracker().forceIntervalElapsed();
	}

	@Override
	public ShipAIConfig getConfig() {
		return this.config;
	}

	public BattleNext_BasicShipAI(ShipAPI ship) {
		this(ship, new ShipAIConfig());
	}

	public BattleNext_BasicShipAI(ShipAPI ship, ShipAIConfig config) {
		this.config = config;
		this.sequence = new BattleNext_ManeuverManager(ship);
		this.eval = new BattleNext_FiringSolutionEval(ship, this.aiFlags);
		this.ship = ship;
		this.engine = BattleNext_ShipAIHelper.getEngine();
		this.engineAI = new BattleNext_BasicEngineAI(ship);
		this.flockingAI = new BattleNext_FlockingAI(ship, this.engineAI, this);
		this.attackAI = new BattleNext_AttackAIModule(ship, this.aiFlags);
		this.threatEvalAI = new BattleNext_ThreatEvalAI(ship, this, this.flockingAI);
		if (ship.getHullSpec().getShieldSpec() != null && ship.getHullSpec().getShieldSpec().getType() == ShieldType.PHASE) {
			BattleNext_SystemAI phaseAI = BattleNext_SystemAIFactory.create(ship, ship.getPhaseCloak(), this.aiFlags,
					this.threatEvalAI, this.attackAI, this.flockingAI, this);
			if (phaseAI instanceof BattleNext_PhaseCloakSystemAI phaseCloak) {
				phaseCloak.getPhaseCloakShieldAI().setUsePhaseCloak(true);
			}
			this.shieldAI = new BattleNext_ShieldAI() {
				@Override
				public boolean shouldKeepShieldOn() {
					return false;
				}

				@Override
				public boolean hasFiringSolution() {
					return phaseAI instanceof BattleNext_PhaseCloakSystemAI phaseCloak
						&& phaseCloak.getPhaseCloakShieldAI().getEval().hasSolution();
				}

				@Override
				public BattleNext_FiringSolutionEval getEval() {
					return phaseAI instanceof BattleNext_PhaseCloakSystemAI phaseCloak
						? phaseCloak.getPhaseCloakShieldAI().getEval() : null;
				}

				@Override
				public void advance(float amount, BattleNext_ThreatEvalAI threatEval, Vector2f avoidanceVector, Vector2f collisionThreatDir, ShipAPI attackTarget) {
					phaseAI.advance(amount, avoidanceVector, collisionThreatDir, attackTarget);
				}
			};
		} else if (ship.getShield() != null) {
			if (ship.getShield().getType() == ShieldType.OMNI) {
				this.shieldAI = new BattleNext_OmniShieldAngleAI(ship, this.aiFlags);
				((BattleNext_OmniShieldAngleAI) this.shieldAI).setCollisionAnalysisModule(this.flockingAI.getCollisionAnalysis());
			} else if (ship.getShield().getType() == ShieldType.FRONT) {
				this.shieldAI = new BattleNext_FrontShieldAI(ship, this.aiFlags);
			}
		}

		this.pickAttackModeIfNeeded(0.0F);
		this.orderResponseModule = new BattleNext_OrderResponseModule(ship, this.flockingAI, this.attackAI, this);
		this.ventModule = new BattleNext_VentModule(ship, this.threatEvalAI, this.flockingAI, this, this.aiFlags);
		if (!ship.isFighter()) {
			this.fighterPullbackModule = new BattleNext_FighterPullbackModule(ship, this.threatEvalAI, this.flockingAI, this, this.aiFlags);
		}

		this.behaviorModule = new BattleNext_BehaviorModule(ship, this.threatEvalAI, this.flockingAI, this, this.aiFlags);
		if (ship.getSystem() != null) {
			this.systemAI = BattleNext_SystemAIFactory.create(ship, ship.getSystem(), this.aiFlags,
					this.threatEvalAI, this.attackAI, this.flockingAI, this);
		}
		ship.setCustomData(INSTANCE_KEY, new WeakReference<>(this));
	}

	public static BattleNext_BasicShipAI getActiveInstance(ShipAPI ship) {
		Object reference = ship.getCustomData().get(INSTANCE_KEY);
		Object instance = reference instanceof WeakReference<?> ? ((WeakReference<?>) reference).get() : null;
		if (!(instance instanceof BattleNext_BasicShipAI)) return null;
		BattleNext_BasicShipAI ai = (BattleNext_BasicShipAI) instance;
		// approximation: ShipAPI exposes the wrapper AI and its delegated flags;
		// matching flag identity identifies the installed ported AI.
		return ship.getShipAI() != null && ship.getShipAI().getAIFlags() == ai.getAIFlags() ? ai : null;
	}

	@Override
	public ShipwideAIFlags getAIFlags() {
		return this.aiFlags;
	}

	public BattleNext_BasicEngineAI getEngineAI() {
		return this.engineAI;
	}

	@Override
	public float getMaxFiringRange() {
		return this.attackAI.getMaxRange();
	}

	public float getMaxNonMissileRange() {
		return this.attackAI.getNonMissileMaxRange();
	}

	public BattleNext_Maneuver getCurrentManeuver() {
		return this.sequence.getCurrentManeuver();
	}

	private void pickAttackModeIfNeeded(float amount) {
		this.timeInMode += amount;
		if (this.timeInMode >= this.maxTimeInMode) {
			this.timeInMode = 0.0F;
			this.maxTimeInMode = (float) Math.random() * ATTACK_MODE_TIME_RANGE + MIN_ATTACK_MODE_TIME;
			boolean fastShip = this.ship.getMaxSpeed() > 100.0F + (float) Math.random() * 50.0F;
			// The decompiled switch runs on a jadx table whose labels are ordinal + 1:
			// label 2 = FIGHTER, 3 = FRIGATE, 4 = DESTROYER, 5 = CRUISER, 6 = CAPITAL_SHIP.
			if (fastShip && (float) Math.random() > 0.25F) {
				this.attackMode = BattleNext_AttackMode.STRAFE;
			} else {
				switch (this.ship.getHullSize()) {
					case FIGHTER:
						this.attackMode = BattleNext_AttackMode.STRAFE;
						break;
					case FRIGATE:
						if ((float) Math.random() > 0.5F) {
							this.attackMode = BattleNext_AttackMode.STRAFE;
						} else {
							this.attackMode = BattleNext_AttackMode.HOVER;
						}

						this.attackMode = BattleNext_AttackMode.STRAFE;
						break;
					case DESTROYER:
						if ((float) Math.random() > 0.6F) {
							this.attackMode = BattleNext_AttackMode.STRAFE;
						} else if ((float) Math.random() > 0.5F) {
							this.attackMode = BattleNext_AttackMode.BACK_AWAY;
						} else {
							this.attackMode = BattleNext_AttackMode.STRAFE;
						}
						break;
					default:
						if ((float) Math.random() > 0.8F) {
							this.attackMode = BattleNext_AttackMode.STRAFE;
						} else if ((float) Math.random() > 0.5F) {
							this.attackMode = BattleNext_AttackMode.BACK_AWAY;
						} else {
							this.attackMode = BattleNext_AttackMode.STRAFE;
						}
						break;
				}
			}
		}

		// intentionally kept dead: vanilla always forces non-strafe modes back to
		// STRAFE here, so HOVER and BACK_AWAY are unreachable (BasicShipAI.java:247).
		if (this.attackMode == BattleNext_AttackMode.BACK_AWAY || this.attackMode == BattleNext_AttackMode.HOVER) {
			this.attackMode = BattleNext_AttackMode.STRAFE;
		}
	}

	public boolean hasOrders() {
		return this.orderResponseModule.hasOrders();
	}

	@Override
	public BattleNext_DirectOrders getOrders() {
		return this.orderResponseModule.getOrders();
	}

	@Override
	public void advance(float amount) {
		if (!this.ship.isHulk()) {
			// approximation: vanilla ShipStatistics advances from the engine before
			// ship AI; the public API requires the ported AI to advance its own tracker.
			ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemyOfMinHullSizeInRange(this.ship, HullSize.FRIGATE, 3000.0F);
			BattleNext_ShipAIHelper.getStatisticsTracker(this.ship).advance(this.ship, nearestEnemy, amount, this.aiFlags.hasFlag(AIFlags.BACKING_OFF));

			boolean controlsLocked = this.ship.controlsLocked();
			if (controlsLocked) {
				this.ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
				this.ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
				this.ship.blockCommandForOneFrame(ShipCommand.FIRE);
				this.ship.blockCommandForOneFrame(ShipCommand.FIRE);
				// approximation: the engine FIRE_AT_WILL command is reached through the API
				// TOGGLE_AUTOFIRE (engine Ship.giveCommand maps TOGGLE_AUTOFIRE -> FIRE_AT_WILL)
				this.ship.blockCommandForOneFrame(ShipCommand.TOGGLE_AUTOFIRE);
				this.ship.setHoldFireOneFrame(true);
				this.ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
			}

			if (this.ship.getShipTarget() != null && (this.ship.getShipTarget().isHulk() || this.ship.getShipTarget().isExpired())) {
				this.ship.setShipTarget(null);
			}

			if (this.aiFlags.getCustom(AIFlags.MANEUVER_TARGET) instanceof ShipAPI maneuverTarget
				&& (maneuverTarget.isHulk() || maneuverTarget.isExpired() || !maneuverTarget.isAlive())
				&& this.isChaseLikeManeuver(this.sequence.getCurrentManeuver())) {
				this.sequence.clearAllManeuvers();
			}

			this.orderResponseModule.advance(amount);
			this.aiFlags.advance(amount);
			this.pickAttackModeIfNeeded(amount);

			BattleNext_ThreatResponse threatResponse = this.threatEvalAI.advance(amount);
			if (this.ship.getFixedLocation() != null || this.ship.getStationSlot() != null) {
				threatResponse = null;
			}

			BattleNext_ThreatData biggestThreat = this.threatEvalAI.getMaxThreatData();
			if (biggestThreat != null && biggestThreat.getShip() != null) {
				this.aiFlags.setFlag(AIFlags.BIGGEST_THREAT, 0.1F, biggestThreat);
			}

			if (!this.ship.isRetreating() && threatResponse != null) {
				BattleNext_Maneuver currentManeuver = this.sequence.getCurrentManeuver();
				if (threatResponse.maneuver == null) {
					if (currentManeuver instanceof BattleNext_ThreatAvoidanceManeuver) {
						boolean keepAvoidance = true;
						float avoidOffset = ((BattleNext_ThreatAvoidanceManeuver) currentManeuver).getAvoidOffset();
						if (avoidOffset > 90.0F) {
							keepAvoidance = false;
						}

						if (keepAvoidance) {
							this.sequence.clearAllManeuvers();
						}
					}
				} else if (currentManeuver == null || currentManeuver != this.lastThreatResponse) {
					this.setManeuver(threatResponse.maneuver, threatResponse.duration);
					this.lastThreatResponse = threatResponse.maneuver;
				}
			}

			this.sequence.advance(amount);
			this.collisionCheckTracker.advance(amount);
			float collisionCheckDuration = 0.0F;
			boolean checkCollisions = this.collisionCheckTracker.intervalElapsed();
			if (checkCollisions || this.avoidingCollision) {
				if (this.avoidingCollision) {
					collisionCheckDuration = amount;
				} else {
					collisionCheckDuration = this.collisionCheckTracker.getIntervalDuration();
				}

				this.checkForPotentialMineTriggers();
				this.avoidShipCollisions();
				this.avoidMissiles();
				boolean avoiding = this.flockingAI.getAvoidanceVector() != null;
				if (!avoiding) {
					this.avoidAsteroidCollisions();
				}
			}

			this.ventModule.advance(amount, this.target);
			if (this.fighterPullbackModule != null) {
				this.fighterPullbackModule.advance(amount, this.target);
			}

			this.behaviorModule.advance(amount);
			this.avoidingCollision = this.flockingAI.hasCollisionWarning();
			if (this.ship.getFixedLocation() != null || this.ship.getStationSlot() != null) {
				this.avoidingCollision = false;
			}

			if (!this.avoidingCollision && this.sequence.isCurrentManeuverDone()) {
				this.sequence.initCurrentManeuver();
				if (this.sequence.getCurrentManeuver() instanceof BattleNext_StrafeTargetManeuverV2) {
					BattleNext_StrafeTargetManeuverV2 strafeManeuver = (BattleNext_StrafeTargetManeuverV2) this.sequence.getCurrentManeuver();
					this.flockingAI.setAvoidanceRadius(strafeManeuver.computeFacing(true));
					this.flockingAI.setDesiredFacing(strafeManeuver.getTargetFacing());
				} else {
					this.flockingAI.setAvoidanceRadius(this.sequence.getDesiredFacing());
					this.flockingAI.setDesiredFacing(this.sequence.getTargetFacing());
				}
			} else {
				if (this.avoidingCollision) {
					this.prevCollisionDir = this.flockingAI.getAvoidanceVector();
				}

				this.engineAI.setAvoidingCollision(this.avoidingCollision);
				this.flockingAI.setAvoidanceRadius(this.sequence.getDesiredFacing());
				this.flockingAI.setDesiredFacing(this.sequence.getTargetFacing());
				this.flockingAI.update(amount);
				this.engineAI.advance(amount);
			}

			if (collisionCheckDuration > 0.0F) {
				this.flockingAI.updateMaxSpeed(collisionCheckDuration);
			}

			if (!this.avoidingCollision) {
				this.prevCollisionDir = null;
			}

			Vector2f avoidanceVector = this.flockingAI.getAvoidanceVector();
			this.attackAI.advance(amount, this.threatEvalAI, avoidanceVector);
			ShipAPI attackTarget = this.target;
			if (attackTarget == null) {
				attackTarget = BattleNext_ShipAIHelper.getManeuverTarget(this.ship);
			}

			if (attackTarget == null) {
				ShipAPI escortTarget = BattleNext_ShipAIHelper.getEscortTarget(this.ship);
				if (escortTarget != null) {
					attackTarget = escortTarget.getShipTarget();
					if (attackTarget == null) {
						attackTarget = BattleNext_ShipAIHelper.getManeuverTarget(escortTarget);
					}
				}
			}

			if (attackTarget == this.ship) {
				attackTarget = null;
			}

			if (this.shieldAI != null) {
				this.shieldAI.advance(amount, this.threatEvalAI, avoidanceVector, this.flockingAI.getAvoidanceLocation(), attackTarget);
			}

			this.eval.advance(amount, this.threatEvalAI, avoidanceVector, this.flockingAI.getAvoidanceLocation(), true);
			if (this.systemAI != null) {
				boolean targetInvisible = attackTarget != null && !BattleNext_ShipAIHelper.isEntityVisible(this.ship.getOriginalOwner(), attackTarget);
				this.aiFlags.setFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM, 1.0F, targetInvisible ? null : attackTarget);
				if (rttShouldAdvanceSystemAI()) {
					this.systemAI.advance(amount, avoidanceVector, this.flockingAI.getAvoidanceLocation(), targetInvisible ? null : attackTarget);
				}
			}

			if (this.sequence.isManeuverQueueEmpty()) {
				this.pickManeuver();
			}
		}
	}

	protected boolean rttShouldAdvanceSystemAI() {
		return true;
	}

	private boolean isChaseLikeManeuver(BattleNext_Maneuver maneuver) {
		return maneuver instanceof BattleNext_AssignmentBasedManeuver
			|| maneuver instanceof BattleNext_StrafeTargetManeuverV2
			|| maneuver instanceof BattleNext_PursuitAttackManeuver;
	}

	private void checkForPotentialMineTriggers() {
		float checkRange = this.ship.getCollisionRadius() + 350.0F;
		float checkSpan = checkRange * 2.0F;
		Iterator<?> shipGrid = BattleNext_ShipAIHelper.getEngine().getAiGridShips()
			.getCheckIterator(this.ship.getLocation(), checkSpan, checkSpan);
		boolean found = false;

		while (shipGrid.hasNext()) {
			Object next = shipGrid.next();
			if (next instanceof ShipAPI) {
				ShipAPI other = (ShipAPI) next;
				if (other != this.ship && (!other.isFighter() || other.getOwner() != this.ship.getOwner())) {
					float distance = Misc.getDistance(this.ship.getLocation(), other.getLocation());
					if (distance < checkRange) {
						found = true;
						break;
					}
				}
			}
		}

		if (!found) {
			Iterator<?> asteroidGrid = BattleNext_ShipAIHelper.getEngine().getAiGridAsteroids()
				.getCheckIterator(this.ship.getLocation(), checkSpan, checkSpan);
			while (asteroidGrid.hasNext()) {
				Object next = asteroidGrid.next();
				if (next instanceof CombatAsteroidAPI) {
					float distance = Misc.getDistance(this.ship.getLocation(), ((CombatAsteroidAPI) next).getLocation());
					if (distance < checkRange) {
						found = true;
						break;
					}
				}
			}
		}

		if (found) {
			this.aiFlags.setFlag(AIFlags.HAS_POTENTIAL_MINE_TRIGGER_NEARBY, 0.5F);
		} else {
			this.aiFlags.unsetFlag(AIFlags.HAS_POTENTIAL_MINE_TRIGGER_NEARBY);
		}
	}

	public boolean isAvoidingCollision() {
		return this.avoidingCollision;
	}

	private void avoidShipCollisions() {
		this.nearbyFriendlyShipCount = 0;

		float searchRadius = this.ship.getCollisionRadius() * 2.0F + 2000.0F;
		Iterator<?> grid = BattleNext_ShipAIHelper.getEngine().getAiGridShips()
			.getCheckIterator(this.ship.getLocation(), searchRadius, searchRadius);

		while (grid.hasNext()) {
			Object next = grid.next();
			if (!(next instanceof ShipAPI)) {
				continue;
			}
			ShipAPI other = (ShipAPI) next;
			if (this.ship == other || other.isFighter() || other.isShuttlePod()) {
				continue;
			}
			boolean friendlyDisabled = this.ship.getOwner() == other.getOwner() && BattleNext_ShipAIHelper.isEngineDisabled(other);
			boolean solidDrone = other.isDrone() && other.getCollisionClass() == CollisionClass.SHIP;
			friendlyDisabled |= solidDrone;
			if (BattleNext_ShipAIHelper.isEscortTargetOf(other, this.ship)
				&& this.ship.getHullSize().ordinal() >= other.getHullSize().ordinal()) {
				this.flockingAI.addCollisionObject(other);
			}

			if ((other.getHullSize().ordinal() >= this.ship.getHullSize().ordinal() || friendlyDisabled || other.isHulk())
				&& (
					!other.isFrigate()
						|| !this.ship.isFrigate()
						|| !(other.getMass() < this.ship.getMass())
						|| friendlyDisabled
						|| other.isHulk()
						|| other == this.engine.getPlayerShip()
				)) {
				boolean sameOwner = this.ship.getOwner() == other.getOwner();
				float distance = Misc.getDistance(this.ship.getLocation(), other.getLocation());
				float combinedRadius = this.ship.getCollisionRadius() + other.getCollisionRadius();
				if (sameOwner && distance < combinedRadius * 3.0F) {
					this.nearbyFriendlyShipCount++;
				}

				if (solidDrone) {
					this.flockingAI.getCollisionAnalysis().addShip(other, true);
				} else {
					this.flockingAI.addCollisionEntityByType(other);
				}
			}
		}
	}

	private void avoidAsteroidCollisions() {
		Vector2f location = this.ship.getLocation();
		float searchRadius = this.ship.getCollisionRadius() * 2.0F + 500.0F;
		Iterator<?> grid = BattleNext_ShipAIHelper.getEngine().getAiGridAsteroids()
			.getCheckIterator(location, searchRadius, searchRadius);

		while (grid.hasNext()) {
			Object next = grid.next();
			if (!(next instanceof CombatAsteroidAPI)) {
				continue;
			}
			CombatAsteroidAPI asteroid = (CombatAsteroidAPI) next;
			if (asteroid.getMass() * 1.5F >= this.ship.getMass() || this.ship.getHitpoints() < 100.0F) {
				this.flockingAI.addCollisionEntityByType(asteroid);
			}
		}
	}

	private void avoidMissiles() {
		int owner = this.ship.getOwner();
		CollisionGridAPI missileGrid = BattleNext_ShipAIHelper.getEngine().getAiGridMissiles();
		float searchRadius = this.ship.getCollisionRadius();
		Iterator<?> grid = missileGrid.getCheckIterator(this.ship.getLocation(), 5000.0F + searchRadius * 2.0F, 5000.0F + searchRadius * 2.0F);

		while (grid.hasNext()) {
			Object next = grid.next();
			if (!(next instanceof MissileAPI)) {
				continue;
			}
			MissileAPI missile = (MissileAPI) next;
			if (missile.getOwner() == owner && !missile.isFizzling() && missile.getCollisionClass() == CollisionClass.MISSILE_NO_FF) {
				boolean ownMine = missile.isMine() && !missile.isNoMineFFConcerns();
				if (!ownMine) {
					continue;
				}
			}

			if (missile.getSource() == this.ship && !missile.isFizzling()) {
				boolean ownMine = missile.isMine() && !missile.isNoMineFFConcerns();
				if (!ownMine) {
					continue;
				}
			}

			this.flockingAI.addCollisionEntity(missile);
		}
	}

	private void avoidMapEdges() {
		// intentionally kept dead: empty in vanilla (BasicShipAI.java:589)
	}

	private boolean isGoodBackAwayTarget(ShipAPI other) {
		if (other.getOwner() != this.ship.getOwner() && !other.isHulk()) {
			float distance = Misc.getDistance(this.ship.getLocation(), other.getLocation());
			float angleDiff = BattleNext_ShipAIHelper.getAngleDiff(this.ship.getFacing(), this.ship.getLocation(), other.getLocation());
			return distance < 800.0F && angleDiff < 30.0F;
		} else {
			return false;
		}
	}

	private boolean isValidOrderTarget(ShipAPI candidate) {
		return candidate != null && !candidate.isHulk() && BattleNext_ShipAIHelper.getEngine().getShips().contains(candidate);
	}

	@Override
	public void cancelCurrentManeuver() {
		this.sequence.clearAllManeuvers();
		this.pickManeuver();
	}

	@Override
	public BattleNext_FlockingAI getFlockingAI() {
		return this.flockingAI;
	}

	public void setManeuver(BattleNext_Maneuver maneuver, float duration) {
		duration += -0.5F + (float) Math.random() * 1.0F;
		if (duration < 0.5F) {
			duration = 0.5F;
		}

		this.sequence.clearAllManeuvers();
		this.sequence.addManeuver(maneuver, duration);
	}

	@Override
	public boolean isEngaged() {
		return this.orderResponseModule.isEngaged();
	}

	// Single AI decision entry ported from BasicShipAI.pickManeuver (vanilla 637-1600).
	// Kept as one method on purpose: every local feeds the final decision tree and
	// the vanilla evaluation order must not change. It is a highly coupled entry
	// flow, which the project rules allow above the normal length budget.
	private void pickManeuver() {
		if (!this.sequence.isManeuverQueueEmpty()) {
			return;
		}

		if (this.ship.getFixedLocation() != null) {
			this.setManeuver(new data.scripts.ai.vanilla.maneuvers.BattleNext_HoldPositionManeuver(this.ship, this.flockingAI, this), 3.0F);
			return;
		} else if (this.ship.getStationSlot() != null) {
			this.setManeuver(new data.scripts.ai.vanilla.maneuvers.BattleNext_IdleManeuver(this.ship, this.flockingAI, this), 3.0F);
			BattleNext_BestTargetEval stationTarget = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
			if (stationTarget != null) {
				this.target = stationTarget.getTarget();
				this.badTarget = stationTarget.isBadTarget();
			} else {
				if (this.ship.isCruiser() || this.ship.isCapital()) {
					this.target = BattleNext_ShipAIHelper.getNearestEnemyCruiserOrCapital(this.ship);
					if (this.target != null) {
						float distance = Misc.getDistance(this.ship.getLocation(), this.target.getLocation());
						if (distance > this.getMaxFiringRange() + 500.0F) {
							this.target = null;
						}
					}
				}

				if (this.target == null) {
					if (this.ship.isFrigate()) {
						this.target = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, this.maxAttackRange, true);
						if (this.target != null && this.target.isFighter() && this.target.getWing() != null) {
							ShipAPI sourceShip = this.target.getWing().getSourceShip();
							if (sourceShip != null) {
								float carrierDistance = Misc.getDistance(this.target.getLocation(), sourceShip.getLocation());
								if (carrierDistance < sourceShip.getCollisionRadius() + 700.0F && sourceShip.isPullBackFighters()) {
									this.target = BattleNext_ShipAIHelper.getNearestEnemy(this.ship, true);
								}
							}
						}
					} else {
						this.target = BattleNext_ShipAIHelper.getNearestEnemy(this.ship, true);
					}

					if (this.target != null) {
						float distance = Misc.getDistance(this.ship.getLocation(), this.target.getLocation());
						if (distance > this.maxAttackRange) {
							this.target = null;
						}
					}

					if (this.target == null) {
						this.target = BattleNext_ShipAIHelper.getNearestEnemyInRangeNoFighters(this.ship, this.ship.getLocation(), Float.MAX_VALUE, true);
						if (this.target != null) {
							float distance = Misc.getDistance(this.ship.getLocation(), this.target.getLocation());
							if (distance > this.maxAttackRange) {
								this.target = null;
							}
						}
					}
				}

				if (this.target == null) {
					this.target = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, this.maxAttackRange, false);
				}
			}

			if (!this.isValidOrderTarget(this.target)) {
				this.target = null;
			}
			return;
		}

		this.pickFieldManeuver();
	}

	// map width/height replace the engine CombatMap bounds (map is centered on origin)
	private float mapMinX() {
		return -BattleNext_ShipAIHelper.getEngine().getMapWidth() / 2.0F;
	}

	private float mapMaxX() {
		return BattleNext_ShipAIHelper.getEngine().getMapWidth() / 2.0F;
	}

	private float mapMinY() {
		return -BattleNext_ShipAIHelper.getEngine().getMapHeight() / 2.0F;
	}

	private float mapMaxY() {
		return BattleNext_ShipAIHelper.getEngine().getMapHeight() / 2.0F;
	}

	private Vector2f mapCenter() {
		return new Vector2f(0.0F, 0.0F);
	}

	private void pickFieldManeuver() {
		com.fs.starfarer.api.combat.CombatTaskManagerAPI taskManager = BattleNext_ShipAIHelper.getEngine()
			.getFleetManager(this.ship.getOriginalOwner()).getTaskManager(this.ship.isAlly());

		// AVOID assignments: run away from the avoided entity
		AssignmentInfo avoidAssignment = null;
		float avoidDistance = Float.MAX_VALUE;
		for (AssignmentInfo assignment : taskManager.getAllAssignments()) {
			if (assignment.getType() == CombatAssignmentType.AVOID && assignment.getTarget() != null
				&& assignment.getTarget().getLocation() != null) {
				// approximation: vanilla skipped AVOID tasks whose assigned fleet member is
				// friendly; the API exposes the assigned members as a list
				if (!assignment.getAssignedMembers().isEmpty()) {
					ShipAPI assigneeShip = assignment.getAssignedMembers().get(0).getShip();
					if (assigneeShip != null && !BattleNext_ShipAIHelper.isShipEnemy(this.ship, assigneeShip)) {
						continue;
					}
				}

				float distance = Misc.getDistance(this.ship.getLocation(), assignment.getTarget().getLocation());
				if (distance < 3500.0F && distance < avoidDistance) {
					avoidDistance = distance;
					avoidAssignment = assignment;
				}
			}
		}

		if (avoidAssignment != null) {
			Vector2f awayFromAvoid = avoidAssignment.getTarget().getLocation();
			float avoidAngle = BattleNext_ShipAIHelper.getAngleFast(awayFromAvoid, this.ship.getLocation());
			avoidAngle += (float) Math.random() * 90.0F - 45.0F;
			Vector2f retreatPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(avoidAngle);
			retreatPoint.scale(2000.0F);
			Vector2f.add(retreatPoint, this.ship.getLocation(), retreatPoint);

			ShipAPI nearestEnemyAnywhere = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, Float.MAX_VALUE, true);
			if (nearestEnemyAnywhere != null) {
				float enemyDistance = Misc.getDistance(this.ship.getLocation(), nearestEnemyAnywhere.getLocation());
				if (enemyDistance > 2500.0F) {
					float awayFromEnemy = BattleNext_ShipAIHelper.getAngleFast(awayFromAvoid, nearestEnemyAnywhere.getLocation());
					retreatPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(awayFromEnemy);
					retreatPoint.scale(2000.0F);
					Vector2f.add(retreatPoint, nearestEnemyAnywhere.getLocation(), retreatPoint);
				}
			}

			BattleNext_DirectOrders orders = this.orderResponseModule.getOrders();
			if (orders == null || orders.getType() != BattleNext_DirectOrdersType.RETREAT && orders.getType() != BattleNext_DirectOrdersType.REPAIR_AND_REFIT) {
				Vector2f attackPoint = retreatPoint;
				if (orders != null && orders.getType() == BattleNext_DirectOrdersType.CARRY_OUT_COMBAT_TASK
					&& orders.getTaskTarget() != null && orders.getTaskTarget().getLocation() != null) {
					attackPoint = new Vector2f(orders.getTaskTarget().getLocation());
				}

				this.setManeuver(new data.scripts.ai.vanilla.maneuvers.BattleNext_TaskAttackManeuver(this.ship, attackPoint, this), 3.0F);
				return;
			}
		}

		BattleNext_OrderResponseData orderResponse = getRttOrderResponse();
		if (orderResponse == null) orderResponse = this.orderResponseModule.getOrderResponse();

		// map border avoidance
		if (!this.orderResponseModule.isRetreating() && !this.aiFlags.hasFlag(AIFlags.DO_NOT_AVOID_BORDER)) {
			Vector2f location = this.ship.getLocation();
			float borderMargin = this.attackAI.getNonMissileMaxRange() / 2.0F + 300.0F;
			if (borderMargin < 500.0F) {
				borderMargin = 500.0F;
			}

			float flagDuration = 5.0F;
			if (orderResponse != null && orderResponse.maneuver instanceof data.scripts.ai.vanilla.maneuvers.BattleNext_EscortTargetManeuverV3) {
				borderMargin = 100.0F;
				flagDuration = 2.0F;
			}

			if (location.x > this.mapMaxX() - borderMargin
				|| location.x < this.mapMinX() + borderMargin
				|| location.y > this.mapMaxY() - borderMargin
				|| location.y < this.mapMinY() + borderMargin) {
				if (this.target != null && !this.target.isAlive()) {
					this.target = null;
				}

				this.aiFlags.setFlag(AIFlags.AVOIDING_BORDER, flagDuration);
				if (this.target != null) {
					float duration = 5.0F;
					// vanilla passes a literal false holdTarget here (BasicShipAI.java:806)
					BattleNext_StrafeTargetManeuverV2 borderStrafe = new BattleNext_StrafeTargetManeuverV2(
						this.ship, this.target, false, 0.0F, 1200.0F, 0.0F, this.flockingAI, this, false
					);
					this.setManeuver(borderStrafe, duration);
				} else {
					data.scripts.ai.vanilla.maneuvers.BattleNext_TaskMoveManeuver borderMove =
						new data.scripts.ai.vanilla.maneuvers.BattleNext_TaskMoveManeuver(this.ship, this.mapCenter(), this);
					this.setManeuver(borderMove, flagDuration);
				}

				return;
			}
		}

		BattleNext_BestTargetEval bestTarget = null;
		this.forceTarget = false;
		this.badTarget = false;
		boolean needsFallbackTarget = false;
		if (orderResponse != null) {
			if (this.targetOverride != null && this.isValidOrderTarget(this.targetOverride)) {
				this.forceTarget = true;
				this.target = this.targetOverride;
			} else if (orderResponse.forcedTarget != null && !orderResponse.forcedTarget.getChildModulesCopy().isEmpty()) {
				// approximation: the vanilla call passes a module-filter predicate; the port
				// uses the unfiltered search.
				bestTarget = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
				if (bestTarget != null) {
					this.target = bestTarget.getTarget();
					this.forceTarget = true;
				} else {
					needsFallbackTarget = true;
				}
			} else if (orderResponse.forcedTarget != null && orderResponse.forcedTarget.isTargetable()) {
				this.target = orderResponse.forcedTarget;
				this.forceTarget = true;
			} else {
				this.target = null;
				this.forceTarget = false;
				needsFallbackTarget = true;
			}

			boolean carrierAutoEscort = this.ship.getVariant().isCarrier()
				&& !this.ship.getHullSpec().getHints().contains(ShipTypeHints.NO_AUTO_ESCORT)
				&& this.ship.getLaunchBaysCopy().size() >= this.ship.getMutableStats().getNumFighterBays().getModifiedValue() * 0.66F;
			if (carrierAutoEscort && !this.ship.isRetreating()) {
				com.fs.starfarer.api.combat.CombatTaskManagerAPI ownTaskManager = BattleNext_ShipAIHelper.getEngine()
					.getFleetManager(this.ship.getOriginalOwner()).getTaskManager(this.ship.isAlly());
				if (ownTaskManager != null) {
					AssignmentInfo assignment = ownTaskManager.getAssignmentFor(this.ship);
					if (assignment != null) {
						String personality = BattleNext_ShipAIHelper.getPersonality(this.ship);
						boolean reckless = "reckless".equals(personality);
						boolean aggressive = "aggressive".equals(personality);
						boolean skipForIntercept = assignment.getType() == CombatAssignmentType.INTERCEPT && (reckless || aggressive);
						if (!skipForIntercept) {
							ShipAPI escortTarget = BattleNext_ShipAIHelper.findAutoEscortTarget(this.ship, false, assignment);
							if (escortTarget != null) {
								data.scripts.ai.vanilla.maneuvers.BattleNext_EscortTargetManeuverV3 escortManeuver =
									new data.scripts.ai.vanilla.maneuvers.BattleNext_EscortTargetManeuverV3(
										this.ship, escortTarget, this.attackAI, this.flockingAI, this
									);
								this.setManeuver(escortManeuver, 2.0F + (carrierAutoEscort ? 1.0F : 0.0F));
								return;
							}
						}
					}
				}
			}

			if (orderResponse.maneuver != null) {
				this.setManeuver(orderResponse.maneuver, orderResponse.duration);
				return;
			}
		}

		if (needsFallbackTarget || orderResponse == null) {
			this.target = null;
			bestTarget = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship);
			if (bestTarget != null) {
				this.target = bestTarget.getTarget();
				this.badTarget = bestTarget.isBadTarget();
			} else {
				if (this.ship.isCruiser() || this.ship.isCapital()) {
					this.target = BattleNext_ShipAIHelper.getNearestEnemyCruiserOrCapital(this.ship);
					if (this.target != null) {
						float distance = Misc.getDistance(this.ship.getLocation(), this.target.getLocation());
						if (distance > this.getMaxFiringRange() + 500.0F) {
							this.target = null;
						}
					}
				}

				if (this.target == null) {
					if (this.ship.isFrigate()) {
						this.target = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, this.maxAttackRange, true);
						if (this.target != null && this.target.isFighter() && this.target.getWing() != null) {
							ShipAPI sourceShip = this.target.getWing().getSourceShip();
							if (sourceShip != null) {
								float carrierDistance = Misc.getDistance(this.target.getLocation(), sourceShip.getLocation());
								if (carrierDistance < sourceShip.getCollisionRadius() + 700.0F && sourceShip.isPullBackFighters()) {
									this.target = BattleNext_ShipAIHelper.getNearestEnemy(this.ship, true);
								}
							}
						}
					} else {
						this.target = BattleNext_ShipAIHelper.getNearestEnemy(this.ship, true);
					}

					if (this.target != null) {
						float distance = Misc.getDistance(this.ship.getLocation(), this.target.getLocation());
						if (distance > this.maxAttackRange) {
							this.target = null;
						}
					}

					if (this.target == null) {
						this.target = BattleNext_ShipAIHelper.getNearestEnemyInRangeNoFighters(this.ship, this.ship.getLocation(), Float.MAX_VALUE, true);
						if (this.target != null) {
							float distance = Misc.getDistance(this.ship.getLocation(), this.target.getLocation());
							if (distance > this.maxAttackRange) {
								this.target = null;
							}
						}
					}
				}

				if (this.target == null) {
					this.target = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, this.maxAttackRange, false);
				}
			}

			if (this.targetOverride != null && this.isValidOrderTarget(this.targetOverride)) {
				this.forceTarget = true;
				this.target = this.targetOverride;
			}

			if (!this.isValidOrderTarget(this.target)) {
				this.target = null;
				bestTarget = null;
			}
		}

		this.targetOverride = null;
		if (this.target != null && !BattleNext_ShipAIHelper.isEntityVisible(this.ship.getOriginalOwner(), this.target)) {
			data.scripts.ai.vanilla.maneuvers.BattleNext_ExploreManeuver explore = new data.scripts.ai.vanilla.maneuvers.BattleNext_ExploreManeuver(
				this.ship, this.flockingAI, this
			);
			this.setManeuver(explore, 2.0F + (float) Math.random() * 2.0F);
			return;
		}

		this.maxAttackRange = Float.MAX_VALUE;
		if (this.target == null) {
			return;
		}

		this.pickEngagementManeuver(bestTarget);
	}

	protected BattleNext_OrderResponseData getRttOrderResponse() {
		return null;
	}

	private void pickEngagementManeuver(BattleNext_BestTargetEval bestTarget) {
		com.fs.starfarer.api.combat.CombatTaskManagerAPI taskManager = BattleNext_ShipAIHelper.getEngine()
			.getFleetManager(this.ship.getOriginalOwner()).getTaskManager(this.ship.isAlly());

		boolean allowChase = true;
		boolean shipNonCombat = this.ship.isNonCombat(true);
		boolean shipUnarmed = this.ship.isNonCombat(false);
		boolean targetNonCombat = this.target.isNonCombat(false);
		boolean keepDistance = false;
		boolean pressAttack = false;
		boolean harassAssignment = false;
		CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner());
		if (fleetManager != null) {
			com.fs.starfarer.api.combat.CombatTaskManagerAPI ownTaskManager = fleetManager.getTaskManager(this.ship.isAlly());
			BattleNext_DirectOrders orders = this.orderResponseModule.getOrders();
			if (orders != null && orders.getTaskType() != null) {
				if (orders.getTaskType() == CombatAssignmentType.HARASS) {
					keepDistance = true;
				} else if (orders.getTaskType() == CombatAssignmentType.RECON) {
					// vanilla marks RECON tasks here without further use
				}
			} else {
				AssignmentInfo assignment = ownTaskManager.getAssignmentFor(this.ship);
				if (assignment != null && assignment.getType() == CombatAssignmentType.HARASS) {
					keepDistance = true;
				}
			}
		}

		if (keepDistance) {
			allowChase = false;
		}

		String personality = BattleNext_ShipAIHelper.getPersonality(this.ship);
		boolean reckless = "reckless".equals(personality);
		boolean aggressive = "aggressive".equals(personality);
		boolean hasInterceptTask = BattleNext_ShipAIHelper.hasInterceptTask(this.ship);
		boolean hasStrikeTask = BattleNext_ShipAIHelper.hasStrikeTask(this.ship)
			&& (!BattleNext_ShipAIHelper.isCarrier(this.ship) || this.ship.getHullSpec().getHints().contains(ShipTypeHints.COMBAT));
		boolean orderedToAttack = BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship);
		if ((personality.equals("cautious") || personality.equals("timid") || shipUnarmed)
			&& (shipUnarmed || !targetNonCombat)
			&& !aggressive
			&& !reckless
			&& !orderedToAttack
			&& this.ship.getHullSize().ordinal() <= this.target.getHullSize().ordinal()) {
			keepDistance = true;
		}

		if ((aggressive || reckless || orderedToAttack) && !shipUnarmed) {
			pressAttack = true;
			keepDistance = false;
		}

		if (!this.config.backingOffWhileNotVentingAllowed) {
			keepDistance = false;
		}

		float distance = Misc.getDistance(this.ship.getLocation(), this.target.getLocation());
		boolean targetRecovering = this.target.getFluxTracker().isOverloadedOrVenting()
			&& (this.target.getFluxTracker().getOverloadTimeRemaining() > 5.0F || this.target.getFluxTracker().getTimeToVent() > 5.0F);
		boolean selfRecovering = this.ship.getFluxTracker().isOverloadedOrVenting()
			&& (this.ship.getFluxTracker().getOverloadTimeRemaining() > 0.0F || this.ship.getFluxTracker().getTimeToVent() > 0.0F);
		boolean selfFluxHigh = this.ship.getFluxLevel() > 0.7F;
		boolean fluxAdvantage = BattleNext_ShipAIHelper.getFluxAvailable(this.ship) >= BattleNext_ShipAIHelper.getFluxAvailable(this.target);
		boolean targetFluxHigh = this.target.getFluxLevel() > 0.8F;
		boolean smallerThanTarget = this.ship.getHullSize().ordinal() < this.target.getHullSize().ordinal();
		boolean hullCritical = this.ship.getHullLevel() < 0.25F;
		// vanilla quirk kept: the target radius is multiplied by the distance here
		// (BasicShipAI.java:1019), so this is not a real combined radius.
		float reachDistance = this.ship.getCollisionRadius()
			+ this.target.getCollisionRadius() * BattleNext_ShipAIHelper.getDistanceToEntity(this.ship, this.target);
		boolean carrierAutoEscort = this.ship.getVariant().isCarrier()
			&& !this.ship.getHullSpec().getHints().contains(ShipTypeHints.NO_AUTO_ESCORT)
			&& this.ship.getLaunchBaysCopy().size() >= this.ship.getMutableStats().getNumFighterBays().getModifiedValue() * 0.66F;
		if (!taskManager.isFullAssault()
			&& !hasInterceptTask
			&& !hasStrikeTask
			&& !this.ship.isRetreating()
			&& (!shipNonCombat && !reckless && !orderedToAttack && !aggressive && !BattleNext_ShipAIHelper.isShipInTrouble(this.ship, null) || carrierAutoEscort)) {
			boolean tryEscort = true;
			if (carrierAutoEscort) {
				AssignmentInfo assignment = taskManager.getAssignmentFor(this.ship);
				if (assignment != null && assignment.getType() == CombatAssignmentType.SEARCH_AND_DESTROY) {
					tryEscort = false;
				}
			}

			if (this.aiFlags.hasFlag(AIFlags.MOVEMENT_DEST)) {
				tryEscort = false;
			}

			if (tryEscort) {
				ShipAPI escortTarget = BattleNext_ShipAIHelper.findAutoEscortTarget(this.ship);
				if (escortTarget != null && (escortTarget.getHullSize().ordinal() > this.ship.getHullSize().ordinal() || carrierAutoEscort)) {
					data.scripts.ai.vanilla.maneuvers.BattleNext_EscortTargetManeuverV3 escortManeuver =
						new data.scripts.ai.vanilla.maneuvers.BattleNext_EscortTargetManeuverV3(
							this.ship, escortTarget, this.attackAI, this.flockingAI, this
						);
					this.setManeuver(escortManeuver, 2.0F + (carrierAutoEscort ? 1.0F : 0.0F));
					return;
				}
			}
		}

		float targetAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.target.getLocation());
		int enemiesInArc = BattleNext_ShipAIHelper.getNumberOfEnemiesInArcRange(this.ship, targetAngle, 90.0F, distance, false, true);
		int alliesInLineOfFire = BattleNext_ShipAIHelper.getNumberOfAlliesInLineOfFire(this.ship, this.target, 3000.0F, true);
		Vector2f awayFromTarget = Vector2f.sub(this.ship.getLocation(), this.target.getLocation(), new Vector2f());
		BattleNext_ShipAIHelper.normalise(awayFromTarget);
		Vector2f targetFacingVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.target.getFacing());
		Vector2f targetVelocityVector = BattleNext_ShipAIHelper.normalise(new Vector2f(this.target.getVelocity()));
		float facingDot = Vector2f.dot(awayFromTarget, targetFacingVector);
		boolean targetFacingAway = false;
		if (facingDot < -0.25F) {
			targetFacingAway = true;
		}

		boolean facingNotStraight = facingDot < 0.71F;
		float desiredRange;
		if (this.attackAI.getAttackingGroup() != null) {
			desiredRange = this.attackAI.getAttackingGroup().getMinWeaponRange();
		} else {
			desiredRange = this.attackAI.getBestGroupRange();
		}

		boolean stayAtRange = keepDistance;
		if ((personality.equals("steady") || personality.equals("cautious") || personality.equals("timid") || shipUnarmed)
			&& (shipUnarmed || !targetNonCombat)
			&& !aggressive
			&& !reckless
			&& !orderedToAttack
			&& !hasInterceptTask) {
			stayAtRange = true;
		}

		boolean missileOnlyAtLongRange = !this.attackAI.hasNonMissileWeapon() && this.attackAI.getMinMissileRange() > this.attackAI.getMinRange();
		missileOnlyAtLongRange &= stayAtRange;
		if (desiredRange > this.attackAI.getAttackRange() && !missileOnlyAtLongRange) {
			desiredRange = this.attackAI.getAttackRange();
		}

		if (missileOnlyAtLongRange && this.ship.getSystem() != null && this.ship.getMutableStats() != null) {
			com.fs.starfarer.api.combat.ShipSystemAPI system = this.ship.getSystem();
			com.fs.starfarer.api.combat.ShipSystemSpecAPI systemSpec = system.getSpecAPI();
			if (systemSpec.getThreatAmount() > 0.0F && BattleNext_ShipAIHelper.isInArc(systemSpec.getThreatAngle(), systemSpec.getThreatArc(), 0.0F)) {
				float systemRange = systemSpec.getThreatRange(this.ship.getMutableStats()) - this.ship.getCollisionRadius() * 0.75F;
				systemRange *= 0.75F;
				if (systemRange < desiredRange) {
					desiredRange = systemRange;
				}
			}
		}

		if (!selfRecovering && this.attackAI.hasNonMissileWeapon() && (targetRecovering || !selfFluxHigh && (fluxAdvantage || targetFluxHigh))) {
			desiredRange = this.attackAI.getMinRange();
		}

		BattleNext_ThreatData threatData = this.threatEvalAI.computeThreatData(this.target);
		desiredRange = Math.min(desiredRange, threatData.getThreatRange() + 200.0F);
		if (keepDistance && !shipNonCombat) {
			if (!this.aiFlags.hasFlag(AIFlags.HARASS_MOVE_IN) && !this.aiFlags.hasFlag(AIFlags.HARASS_MOVE_IN_COOLDOWN)) {
				if (targetFacingAway && alliesInLineOfFire > 0) {
					this.aiFlags.setFlag(AIFlags.HARASS_MOVE_IN, 3.0F + this.ship.getHullSize().ordinal());
				}
			} else if (targetFacingAway && alliesInLineOfFire > 0 && !this.aiFlags.hasFlag(AIFlags.HARASS_MOVE_IN)) {
				this.aiFlags.setFlag(AIFlags.HARASS_MOVE_IN, 3.0F + this.ship.getHullSize().ordinal());
				this.aiFlags.unsetFlag(AIFlags.HARASS_MOVE_IN_COOLDOWN);
			}

			if (this.aiFlags.hasFlag(AIFlags.HARASS_MOVE_IN)) {
				desiredRange = Math.max(this.attackAI.getNonMissileMaxRange(), desiredRange);
				keepDistance = false;
			} else {
				float harassMargin = 200.0F;
				if (this.attackAI.getNonMissileMaxRange() > threatData.getThreatRange()) {
					harassMargin = this.attackAI.getNonMissileMaxRange() - threatData.getThreatRange();
					harassMargin *= 0.7F;
					if (harassMargin < 50.0F) {
						harassMargin = 50.0F;
					}

					if (harassMargin > 200.0F) {
						harassMargin = 200.0F;
					}
				}

				desiredRange = threatData.getThreatRange() + harassMargin;
			}
		}

		if (pressAttack) {
			desiredRange = Math.min(Math.max(this.attackAI.getMaxThreatRange() * 0.7F, this.attackAI.getMaxThreatRange() - 200.0F), desiredRange);
			if (desiredRange < 100.0F) {
				desiredRange = 100.0F;
			}
		}

		if (BattleNext_ShipAIHelper.isCarrier(this.ship) && !this.attackAI.hasNonMissileWeapon()) {
			float wingRange = this.attackAI.getMinMissileRange();
			if (this.target != null) {
				wingRange = Math.min(wingRange, BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, this.target));
			}

			float attackRange = this.attackAI.getAttackRange();
			if (wingRange > attackRange) {
				if (stayAtRange) {
					desiredRange = Math.max(1500.0F, wingRange * 0.8F);
				} else {
					desiredRange = wingRange * 0.8F;
				}
			} else {
				desiredRange = 2000.0F;
			}
		}

		if (!pressAttack && !keepDistance) {
			desiredRange -= 100.0F;
		}

		float bestTargetRange = 0.0F;
		if (bestTarget != null) {
			bestTargetRange = bestTarget.getRange();
		}

		boolean frontShield = this.ship.getShield() != null && this.ship.getShield().getType() == com.fs.starfarer.api.combat.ShieldAPI.ShieldType.FRONT;
		float ownRadius = this.ship.getCollisionRadius();
		float flankCheckRadius = ownRadius * 2.0F + 500.0F;
		if (this.ship.isCruiser()) {
			flankCheckRadius += 300.0F;
		}

		if (this.ship.isCapital()) {
			flankCheckRadius += 700.0F;
		}

		if (enemiesInArc > 0) {
			float leftFlankThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship, targetAngle + 90.0F, 179.0F, flankCheckRadius, true, true);
			float rightFlankThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship, targetAngle - 90.0F, 179.0F, flankCheckRadius, true, true);
			float strafeSide = leftFlankThreat > rightFlankThreat ? 1.0F : -1.0F;
			if (leftFlankThreat == rightFlankThreat) {
				strafeSide = 0.0F;
			}

			if (shipNonCombat) {
				desiredRange = threatData.getThreatRange() + reachDistance + 1200.0F;
				float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, this.target);
				if (desiredRange > wingRange) {
					desiredRange = wingRange;
				}

				allowChase = false;
			}

			if (strafeSide != 0.0F) {
				BattleNext_StrafeTargetManeuverV2 flankStrafe = new BattleNext_StrafeTargetManeuverV2(
					this.ship, this.target, this.badTarget, 0.0F, desiredRange, strafeSide, this.flockingAI, this, allowChase
				);
				this.setManeuver(flankStrafe, 2.0F);
				return;
			}

			if (distance < desiredRange + 1000.0F) {
				BattleNext_StrafeTargetManeuverV2 closeStrafe = new BattleNext_StrafeTargetManeuverV2(
					this.ship, this.target, this.badTarget, 0.0F, desiredRange, this.flockingAI, this, allowChase
				);
				this.setManeuver(closeStrafe, 2.0F);
				return;
			}
		}

		if (shipNonCombat && !targetNonCombat) {
			desiredRange = threatData.getThreatRange() + reachDistance + 1200.0F;
			float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, this.target);
			if (desiredRange > wingRange) {
				desiredRange = wingRange;
			}

			allowChase = false;
		}

		if (this.ship.getHullSize().ordinal() >= HullSize.FRIGATE.ordinal() && (!fluxAdvantage || smallerThanTarget) && selfFluxHigh && !targetRecovering) {
			BattleNext_StrafeTargetManeuverV2 fluxStrafe = new BattleNext_StrafeTargetManeuverV2(
				this.ship, this.target, this.badTarget, 0.0F, desiredRange, this.flockingAI, this, allowChase
			);
			this.setManeuver(fluxStrafe, 3.0F);
			return;
		}

		if (selfRecovering) {
			BattleNext_StrafeTargetManeuverV2 recoveringStrafe = new BattleNext_StrafeTargetManeuverV2(
				this.ship, this.target, this.badTarget, 0.0F, Math.max(desiredRange, distance - 200.0F - reachDistance), this.flockingAI, this, allowChase
			);
			this.setManeuver(recoveringStrafe, 3.0F);
			return;
		}

		int alliesInRange = BattleNext_ShipAIHelper.getNumberOfAlliesInRange(this.ship, 3000.0F, true);
		boolean backOffWanted = desiredRange > threatData.getThreatRange() && threatData.getThreatRange() > 0.0F;
		float predictedDistance = BattleNext_ShipAIHelper.getPredictedDistance(this.ship, this.target.getLocation(), this.target.getVelocity());
		boolean closingIn = predictedDistance < 0.0F;
		boolean fastShip = this.ship.getMaxSpeed() > 100.0F;
		boolean notBiggerThanTarget = this.ship.getHullSize().ordinal() <= this.target.getHullSize().ordinal();
		if (targetRecovering) {
			backOffWanted = false;
		}

		if (shipNonCombat && (!targetNonCombat || !this.ship.getVariant().isCarrier() && this.target.getVariant().isCarrier())) {
			backOffWanted = true;
			desiredRange = threatData.getThreatRange() + reachDistance + 1200.0F;
			float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, this.target);
			if (desiredRange > wingRange) {
				desiredRange = wingRange;
			}

			allowChase = false;
		}

		if (!shipNonCombat && (targetNonCombat || this.target.isRetreating())) {
			backOffWanted = false;
		}

		if (keepDistance) {
			backOffWanted = true;
		}

		if (orderedToAttack) {
			backOffWanted = false;
		}

		if (this.aiFlags.hasFlag(AIFlags.BACK_OFF)) {
			backOffWanted = true;
		}

		float frontThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship, targetAngle, 120.0F, distance + 1000.0F, true);
		float frontWideThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship, targetAngle, 150.0F, distance + 1500.0F, true, true);
		float rearThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship, this.ship.getFacing() + 180.0F, 170.0F, Math.max(1500.0F, distance + 500.0F), true);
		if (frontWideThreat <= 0.0F) {
			targetFacingAway = false;
		}

		float ownThreat = BattleNext_ShipAIHelper.getThreat(this.ship);
		boolean flanked = frontThreat > frontWideThreat + Math.max(HullSize.CAPITAL_SHIP.ordinal(), ownThreat);
		flanked &= distance < Math.max(threatData.getThreatRange(), desiredRange) + reachDistance + 750.0F;
		if (rearThreat > 0.0F) {
			flanked = true;
		}

		float threatAroundTarget = BattleNext_ShipAIHelper.getTotalThreatInRangeExcludingShip(
			this.ship, this.target.getLocation(), BattleNext_ShipAIHelper.getAngleFast(this.target.getLocation(), this.ship.getLocation()),
			190.0F, Math.max(1500.0F, distance + 500.0F), this.target, true
		);
		if (threatAroundTarget > 0.0F && distance < Math.max(2000.0F, desiredRange * 2.0F)) {
			flanked = true;
		}

		if (this.getEval().hasSolution()) {
			flanked = true;
		}

		if (orderedToAttack) {
			flanked = false;
		}

		if (flanked) {
			BattleNext_StrafeTargetManeuverV2 flankedStrafe = new BattleNext_StrafeTargetManeuverV2(
				this.ship, this.target, this.badTarget, 0.0F, Math.max(desiredRange, distance - 200.0F - reachDistance), this.flockingAI, this, allowChase
			);
			this.setManeuver(flankedStrafe, 2.0F);
			return;
		}

		if (orderedToAttack) {
			hasInterceptTask = true;
		}

		data.scripts.ai.vanilla.maneuvers.BattleNext_WeaponFireControlHelper fireControl = new data.scripts.ai.vanilla.maneuvers.BattleNext_WeaponFireControlHelper();
		fireControl.init(this.ship, this.target, this);
		boolean notOnTargetHeading = !fireControl.isOnTargetHeading();
		boolean doNotPursue = this.aiFlags.hasFlag(AIFlags.DO_NOT_PURSUE);
		doNotPursue |= this.aiFlags.hasFlag(AIFlags.BACK_OFF);
		if (!ALLOW_OLD_STRAFE
			|| hasInterceptTask
			|| hasStrikeTask
			|| doNotPursue
			|| alliesInLineOfFire != 0
			|| alliesInRange <= 0
			|| enemiesInArc == 1
			|| targetFacingAway
			|| !(distance > desiredRange + reachDistance + 100.0F)
			|| !(distance > threatData.getThreatRange() + reachDistance - 100.0F)
			|| this.ship.isCapital()
			|| frontShield
			|| !notBiggerThanTarget) {
			this.pickNewStyleApproach(
				allowChase, desiredRange, distance, reachDistance, threatData, targetFacingAway, flankCheckRadius, targetRecovering,
				backOffWanted, shipNonCombat, doNotPursue, frontShield, alliesInLineOfFire, notOnTargetHeading, targetVelocityVector,
				targetFacingVector, pressAttack, keepDistance
			);
			return;
		}

		// intentionally kept dead: ALLOW_OLD_STRAFE is false, so this vanilla pursuit
		// fallback is unreachable (BasicShipAI.java:1591).
		float pursuitRange = Math.min(2000.0F, threatData.getThreatRange() + 750.0F * facingDot);
		float targetDistance = BattleNext_ShipAIHelper.getDistance(this.ship, this.target);
		data.scripts.ai.vanilla.maneuvers.BattleNext_PursuitAttackManeuver oldPursuit = new data.scripts.ai.vanilla.maneuvers.BattleNext_PursuitAttackManeuver(
			this.ship, this.target, 0.0F, pursuitRange, targetDistance, this.flockingAI, this
		);
		this.setManeuver(oldPursuit, 1.0F);
	}

	// Port of the new-style approach block (vanilla BasicShipAI.java:1344-1588).
	private void pickNewStyleApproach(
		boolean allowChase, float desiredRange, float distance, float reachDistance, BattleNext_ThreatData threatData,
		boolean targetFacingAway, float flankCheckRadius, boolean targetRecovering, boolean backOffWanted, boolean shipNonCombat,
		boolean doNotPursue, boolean frontShield, int alliesInLineOfFire, boolean notOnTargetHeading,
		Vector2f targetVelocityVector, Vector2f targetFacingVector, boolean pressAttack, boolean keepDistance) {
		boolean withinApproach = BattleNext_ShipAIHelper.getAngleDiff(
			BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.target.getLocation()), this.ship.getFacing()) < 60.0F;
		if (withinApproach && distance < Math.max(threatData.getThreatRange(), desiredRange) + reachDistance + 1500.0F) {
			float leftFlankThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship,
				BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.target.getLocation()) + 90.0F, 179.0F, flankCheckRadius, true, true);
			float rightFlankThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship,
				BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.target.getLocation()) - 90.0F, 179.0F, flankCheckRadius, true, true);
			float strafeSide = leftFlankThreat > rightFlankThreat ? 1.0F : -1.0F;
			if (leftFlankThreat == rightFlankThreat) {
				strafeSide = 0.0F;
			}

			if (strafeSide != 0.0F) {
				// vanilla multiplies the distance adjustment by a constant -1.0F (var74,
				// BasicShipAI.java:1348), NOT by the side sign; the side only selects the
				// strafe direction parameter
				BattleNext_StrafeTargetManeuverV2 approachStrafe = new BattleNext_StrafeTargetManeuverV2(
					this.ship, this.target, this.badTarget, 0.0F, Math.max(desiredRange, distance - 200.0F - reachDistance),
					strafeSide, this.flockingAI, this, allowChase
				);
				this.setManeuver(approachStrafe, 2.0F);
				return;
			}
		}

		if (!targetRecovering) {
			float velocityFacingDot = Vector2f.dot(targetVelocityVector, targetFacingVector);
			boolean targetRetreating = velocityFacingDot < -0.1F;
			if (distance > desiredRange + reachDistance + Math.min(desiredRange, 1000.0F)) {
				float speedScale = 1.0F;
				if (backOffWanted && distance < desiredRange + reachDistance + 500.0F) {
					speedScale = 0.25F;
				}

				int enemyFleetOwner = 0;
				if (this.ship.getOwner() == 0) {
					enemyFleetOwner = 1;
				}

				CombatFleetManagerAPI enemyFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(enemyFleetOwner);
				boolean enemyEscaping = enemyFleetManager != null
					&& enemyFleetManager.getGoal() == com.fs.starfarer.api.fleet.FleetGoal.ESCAPE;
				if (!doNotPursue
					&& (
						targetFacingAway
							|| this.target.isFrigate() && !backOffWanted && ((float) Math.random() > 0.5F || enemyEscaping)
							|| !this.threatEvalAI.isThreatHigh() && (closingInHelper() || !this.ship.isFrigate() || enemyEscaping)
					)) {
					BattleNext_Maneuver approachManeuver = new data.scripts.ai.vanilla.maneuvers.BattleNext_AssignmentBasedManeuver(
						this.ship, this.target, speedScale, this.flockingAI, this
					);
					if (notOnTargetHeading) {
						approachManeuver = new BattleNext_StrafeTargetManeuverV2(
							this.ship, this.target, this.badTarget, 0.0F, Math.max(200.0F, desiredRange * 0.5F), this.flockingAI, this, allowChase
						);
					}

					if (this.target.isFrigate() && !backOffWanted) {
						this.setManeuver(approachManeuver, 3.0F);
						return;
					}

					this.setManeuver(approachManeuver, 1.5F);
					return;
				}

				if (backOffWanted && distance < desiredRange + reachDistance - 100.0F) {
					desiredRange += 1000.0F;
				}

				float pursuitRange = Math.min(threatData.getThreatRange() + 500.0F, distance - 300.0F - reachDistance);
				pursuitRange = Math.max(desiredRange, pursuitRange);
				if (frontShield || this.ship.isCapital()) {
					BattleNext_StrafeTargetManeuverV2 pursuitStrafe = new BattleNext_StrafeTargetManeuverV2(
						this.ship, this.target, this.badTarget, 0.0F, pursuitRange, this.flockingAI, this, allowChase
					);
					this.setManeuver(pursuitStrafe, 2.0F);
					return;
				}

				if (ALLOW_OLD_STRAFE && pursuitRange > desiredRange + 500.0F && !doNotPursue) {
					data.scripts.ai.vanilla.maneuvers.BattleNext_PursuitAttackManeuver oldPursuit =
						new data.scripts.ai.vanilla.maneuvers.BattleNext_PursuitAttackManeuver(
							this.ship, this.target, 0.0F, pursuitRange, this.flockingAI, this
						);
					this.setManeuver(oldPursuit, 2.0F);
				} else {
					BattleNext_StrafeTargetManeuverV2 pursuitStrafe = new BattleNext_StrafeTargetManeuverV2(
						this.ship, this.target, this.badTarget, 0.0F, pursuitRange, this.flockingAI, this, allowChase
					);
					this.setManeuver(pursuitStrafe, 2.0F);
				}

				return;
			}

			// intentionally kept dead: vanilla computes this then overwrites it with
			// false immediately (BasicShipAI.java:1428).
			boolean nearTarget = distance < Math.min(1000.0F, (desiredRange + reachDistance) / 2.0F);
			nearTarget = false;
			boolean chaseFrigate = this.ship.getHullSize().ordinal() >= this.target.getHullSize().ordinal()
				&& targetRetreating
				&& this.target.getMaxSpeed() + 50.0F > this.ship.getMaxSpeed()
				&& frontThreatHelper() <= BattleNext_ShipAIHelper.getThreat(this.target)
				&& distance > desiredRange + reachDistance
				&& this.target == BattleNext_ShipAIHelper.getNearestEnemyOfMinHullSizeInRange(this.ship, HullSize.FRIGATE, distance + 200.0F)
				&& (float) Math.random() > 0.25F;
			if (!chaseFrigate || doNotPursue) {
				boolean smallTargetForCapital = (this.target.isFrigate() || this.target.isFighter()) && this.ship.isCapital();
				boolean fastShip = this.ship.getMaxSpeed() > 100.0F + (float) Math.random() * 50.0F;
				if (this.threatEvalAI.isThreatHigh() && fastShip) {
					// intentionally kept dead: vanilla assigns a flag that is never read
				}

				boolean approachHoldRange = !fastShip && !this.threatEvalAI.isThreatHigh()
					&& (this.threatEvalAI.getMaxThreatData() == null || this.target == this.threatEvalAI.getMaxThreatData().getShip())
					&& distance > reachDistance + 300.0F;
				approachHoldRange &= !backOffWanted;
				if (approachHoldRange) {
					// vanilla consumes one random here with no further effect; the call is
					// kept so the RNG sequence stays identical
					Math.random();
				}
				if (!this.getConfig().backingOffWhileNotVentingAllowed && !this.ship.getFluxTracker().isOverloadedOrVenting()) {
					smallTargetForCapital = true;
				}

				// sideOffset stays 0: vanilla only rolls a value when two flags that are
				// both hardcoded false hold, so the roll never happens
				float sideOffset = 0.0F;

				if (!this.aiFlags.hasFlag(AIFlags.PHASE_ATTACK_RUN) && !this.aiFlags.hasFlag(AIFlags.PHASE_ATTACK_RUN_IN_GOOD_SPOT)) {
					if (this.attackMode != BattleNext_AttackMode.STRAFE) {
						// intentionally kept dead: attackMode can only be STRAFE here because
						// vanilla forces all modes to STRAFE (BasicShipAI.java:247)
					}

					if (backOffWanted && this.attackMode != BattleNext_AttackMode.STRAFE) {
						if (smallTargetForCapital || !((float) Math.random() > 0.5F)) {
							BattleNext_StrafeTargetManeuverV2 backOffStrafe = new BattleNext_StrafeTargetManeuverV2(
								this.ship, this.target, this.badTarget, sideOffset, desiredRange, this.flockingAI, this, allowChase
							);
							this.setManeuver(backOffStrafe, 5.0F);
						} else {
							data.scripts.ai.vanilla.maneuvers.BattleNext_WeaponAssistAttackManeuver assist =
								new data.scripts.ai.vanilla.maneuvers.BattleNext_WeaponAssistAttackManeuver(this.ship, this.target, this.flockingAI, this);
							this.setManeuver(assist, 3.0F);
						}

						return;
					} else if (this.attackMode != BattleNext_AttackMode.BACK_AWAY && !nearTarget) {
						if (this.attackMode == BattleNext_AttackMode.STRAFE) {
							boolean targetFar = distance - reachDistance > 1250.0F + desiredRange;
							boolean targetClose = distance - reachDistance < 250.0F;
							if (!doNotPursue
								&& (!((float) Math.random() > 0.33F) && fastShip && !targetClose && !frontShield && !this.ship.isCapital() || targetFar)) {
								if (targetFar && this.ship.isCapital() && !shipNonCombat) {
									BattleNext_Maneuver capitalPush = new data.scripts.ai.vanilla.maneuvers.BattleNext_AssignmentBasedManeuver(
										this.ship, this.target, 1.0F, this.flockingAI, this
									);
									if (notOnTargetHeading) {
										capitalPush = new BattleNext_StrafeTargetManeuverV2(
											this.ship, this.target, this.badTarget, sideOffset, desiredRange * 0.15F, this.flockingAI, this, allowChase
										);
									}

									this.setManeuver(capitalPush, 2.0F);
								} else {
									BattleNext_StrafeTargetManeuverV2 pushStrafe = new BattleNext_StrafeTargetManeuverV2(
										this.ship, this.target, this.badTarget, sideOffset, desiredRange, this.flockingAI, this, allowChase
									);
									this.setManeuver(pushStrafe, 5.0F);
								}

								return;
							} else {
								BattleNext_StrafeTargetManeuverV2 holdStrafe = new BattleNext_StrafeTargetManeuverV2(
									this.ship, this.target, this.badTarget, sideOffset, desiredRange, this.flockingAI, this, allowChase
								);
								this.setManeuver(holdStrafe, 8.0F);
								return;
							}
						} else {
							if (this.attackMode == BattleNext_AttackMode.HOVER) {
								// intentionally kept dead: attackMode can only be STRAFE
								data.scripts.ai.vanilla.maneuvers.BattleNext_ChaseAttackManeuver chase =
									new data.scripts.ai.vanilla.maneuvers.BattleNext_ChaseAttackManeuver(
										this.ship, this.target, desiredRange, this.flockingAI, this
									);
								this.setManeuver(chase, 5.0F);
								return;
							}

							return;
						}
					} else {
						if (smallTargetForCapital || !((float) Math.random() > 0.5F)) {
							BattleNext_StrafeTargetManeuverV2 closeStrafe = new BattleNext_StrafeTargetManeuverV2(
								this.ship, this.target, this.badTarget, sideOffset, desiredRange, this.flockingAI, this, allowChase
							);
							this.setManeuver(closeStrafe, 5.0F);
							return;
						}

						if (this.target.isFighter() && !((float) Math.random() > 0.5F) && !doNotPursue) {
							BattleNext_Maneuver fighterChase = new data.scripts.ai.vanilla.maneuvers.BattleNext_AssignmentBasedManeuver(
								this.ship, this.target, 1.0F, this.flockingAI, this
							);
							if (notOnTargetHeading) {
								fighterChase = new BattleNext_StrafeTargetManeuverV2(
									this.ship, this.target, this.badTarget, sideOffset, desiredRange * 0.15F, this.flockingAI, this, allowChase
								);
							}

							this.setManeuver(fighterChase, 3.0F);
						} else {
							data.scripts.ai.vanilla.maneuvers.BattleNext_WeaponAssistAttackManeuver assist =
								new data.scripts.ai.vanilla.maneuvers.BattleNext_WeaponAssistAttackManeuver(this.ship, this.target, this.flockingAI, this);
							this.setManeuver(assist, 3.0F);
						}

						return;
					}
				}

				BattleNext_StrafeTargetManeuverV2 phaseStrafe = new BattleNext_StrafeTargetManeuverV2(
					this.ship, this.target, this.badTarget, sideOffset, desiredRange, this.flockingAI, this, allowChase
				);
				this.setManeuver(phaseStrafe, 3.0F);
				return;
			}

			Math.random();
			BattleNext_Maneuver frigateChase = new data.scripts.ai.vanilla.maneuvers.BattleNext_AssignmentBasedManeuver(
				this.ship, this.target, 1.0F, this.flockingAI, this
			);
			this.setManeuver(frigateChase, 1.0F + (float) Math.random() * 1.0F);
			return;
		}

		if (backOffWanted) {
			// intentionally kept dead: the constructed maneuver is discarded in vanilla
			// (BasicShipAI.java:1570)
			new data.scripts.ai.vanilla.maneuvers.BattleNext_ChaseAttackManeuver(this.ship, this.target, desiredRange * 0.5F, this.flockingAI, this);
			BattleNext_StrafeTargetManeuverV2 backOffStrafe = new BattleNext_StrafeTargetManeuverV2(
				this.ship, this.target, this.badTarget, 0.0F, Math.max(200.0F, desiredRange * 0.5F), this.flockingAI, this, allowChase
			);
			this.setManeuver(backOffStrafe, 2.0F);
			return;
		}

		if (notOnTargetHeading) {
			BattleNext_StrafeTargetManeuverV2 alignStrafe = new BattleNext_StrafeTargetManeuverV2(
				this.ship, this.target, this.badTarget, 0.0F, Math.max(200.0F, desiredRange * 0.5F), this.flockingAI, this, allowChase
			);
			this.setManeuver(alignStrafe, 2.0F);
		} else {
			BattleNext_Maneuver alignCharge = new data.scripts.ai.vanilla.maneuvers.BattleNext_AssignmentBasedManeuver(
				this.ship, this.target, 1.0F, this.flockingAI, this
			);
			this.setManeuver(alignCharge, 2.0F);
		}
	}

	// vanilla closing-speed roll targetFacingAway-ish check (BasicShipAI.java:1383)
	private boolean closingInHelper() {
		float predicted = BattleNext_ShipAIHelper.getPredictedDistance(this.ship, this.target.getLocation(), this.target.getVelocity());
		return predicted < 0.0F;
	}

	private float frontThreatHelper() {
		float targetAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.target.getLocation());
		return BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship, targetAngle, 120.0F,
			BattleNext_ShipAIHelper.getDistance(this.ship, this.target) + 1000.0F, true);
	}

	@Override
	public BattleNext_AttackingGroup getAttackingGroup() {
		return this.attackAI.getAttackingGroup();
	}

	@Override
	public BattleNext_WeaponGroupEvaluation getEvaluationFor(WeaponGroupAPI group) {
		return this.attackAI.getWeaponGroupEvaluation(group);
	}

	@Override
	public boolean doesShipVelocityMatterForAim() {
		return this.attackAI.isAttackGroupReady();
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
	public void setMaxTargetRange(float maxTargetRange) {
		this.maxAttackRange = maxTargetRange;
	}

	@Override
	public boolean canObeyOrders() {
		return !this.orderResponseModule.isRetreating();
	}

	@Override
	public BattleNext_ThreatEvalAI getThreatEvaluator() {
		return this.threatEvalAI;
	}

	@Override
	public void notifyNewOrderGivenToBattleGroup() {
	}

	@Override
	public float getOptimalNonMissileRange() {
		return this.attackAI.getAttackRange();
	}

	@Override
	public BattleNext_AttackAIModule getAttackAI() {
		return this.attackAI;
	}

	@Override
	public boolean keepTarget() {
		return this.forceTarget;
	}

	public ShipAPI getTargetOverride() {
		return this.targetOverride;
	}

	@Override
	public void setTargetOverride(ShipAPI targetOverride) {
		this.targetOverride = targetOverride;
	}

	@Override
	public BattleNext_ShieldAI getShieldAI() {
		return this.shieldAI;
	}

	@Override
	public BattleNext_FiringSolutionEval getEval() {
		return this.eval;
	}

	@Override
	public void setDoNotFireDelay(float amount) {
	}

	@Override
	public boolean needsRefit() {
		return false;
	}

	public BattleNext_OrderResponseModule getOrderResponseModule() {
		return this.orderResponseModule;
	}

	@Override
	public ShipAPI getShip() {
		return this.ship;
	}
}
