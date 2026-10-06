package data.scripts.ai.vanilla.collisions;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Port of engine class com.fs.starfarer.combat.ai.collisions.CollisionAnalysisModule.
 * Collects collision threats around the own ship each frame and derives an
 * avoidance heading from the arcs they block.
 */
public class BattleNext_CollisionAnalysisModule {
	public static final float NO_HEADING = Float.MAX_VALUE;
	private final ShipAPI ship;
	private float brakeTime;
	private float heading = Float.MAX_VALUE;
	private List<BattleNext_CollisionCheckSegment> segments = new ArrayList<>();
	private boolean collisionDetected = false;
	private boolean hitStationOrCapital = false;
	private boolean newCollision = false;
	private boolean hadCollision = false;
	private boolean collisionCourse = false;
	private boolean missileThreat = false;
	private float turnOffset = 0.0F;
	private float timeSinceThreat = 0.0F;
	private float timeSinceCollision = 0.0F;
	private float timeSinceCourse = 0.0F;
	private float timeSinceMissile = 0.0F;
	private int collisionCount = 0;
	private Set<Object> ignoredObjects = new HashSet<>();
	private Set<Object> dangerousObjects = new HashSet<>();
	private float maxThreatDamage = 0.0F;
	private float maxThreatMass = 0.0F;
	private Vector2f missileThreatDirection = null;
	private Vector2f objectThreatDirection = null;
	private CombatEntityAPI threatEntity = null;
	// intentionally kept dead: vanilla writes these snapshot fields every advance() but never reads them
	private List<BattleNext_CollisionCheckSegment> segmentSnapshot = new ArrayList<>();
	private float lastAvoidanceHeading;
	private float lastHeading;
	private boolean hadCollisionSnapshot = false;
	private boolean forceAvoidance = false;
	// intentionally kept dead: vanilla stores the maneuver API here and never reads it
	private final BattleNext_ManeuverAPI maneuverAPI;
	private float avoidanceHeading = Float.MAX_VALUE;
	// intentionally kept dead: vanilla held an unused engine TextureObject here
	private Object unusedTexture;

	public BattleNext_CollisionAnalysisModule(ShipAPI ship, BattleNext_ManeuverAPI maneuverAPI) {
		this.ship = ship;
		this.maneuverAPI = maneuverAPI;
		this.computeBrakeTime();
	}

	public void setForceAvoidance(boolean forceAvoidance) {
		this.forceAvoidance = forceAvoidance;
	}

	public void addShip(ShipAPI other) {
		this.addShip(other, false);
	}

	public void addShip(ShipAPI other, boolean forceAdd) {
		if (this.ship.getParentStation() == null) {
			if (other != this.ship) {
				if (!BattleNext_ShipAIHelper.isSameStationOrShip(this.ship, other)) {
					float collisionRadius = other.getCollisionRadius();
					float facing = other.getFacing();
					boolean isLooseDrone = false;
					// intentionally kept dead: vanilla type check that is always true for a Ship parameter
					if (other instanceof ShipAPI) {
						isLooseDrone = other.isDrone() && other.getCollisionClass() == CollisionClass.SHIP;
					}

					if (isLooseDrone && other.getDroneSource() != null) {
						ShipAPI droneSource = other.getDroneSource();
						if (!BattleNext_ShipAIHelper.isSameStationOrShip(this.ship, droneSource) && this.ship != droneSource) {
							boolean alreadyTracked = false;

							for (BattleNext_CollisionCheckSegment segment : this.segments) {
								if (segment.getEntity() == droneSource) {
									alreadyTracked = true;
									break;
								}
							}

							if (!alreadyTracked) {
								float distanceToSource = Misc.getDistance(droneSource.getLocation(), other.getLocation());
								Vector2f towardSource = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(this.ship.getLocation(), droneSource.getLocation()));
								towardSource.scale(1.0F);
								this.addCollisionSegment(droneSource, droneSource.getLocation(), towardSource, distanceToSource + 0.0F, facing);
							}

							return;
						}
					}

					Vector2f velocity = other.getVelocity();
					if (other.isStation()) {
						if (other.getOwner() == 0) {
							facing = 90.0F;
						} else {
							facing = 270.0F;
						}

						velocity = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(this.ship.getLocation(), other.getLocation()));
						velocity.scale(1.0F);
					}

					if (other.getParentStation() != null) {
						velocity = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(this.ship.getLocation(), other.getParentStation().getLocation()));
						velocity.scale(1.0F);
						this.addCollisionSegment(other.getParentStation(), other.getParentStation().getLocation(), velocity, other.getParentStation().getCollisionRadius(), facing);
					} else {
						boolean interceptAdvantage = false;
						if ((this.ship.isCruiser() || this.ship.isCapital()) && this.ship.getHullSize().ordinal() >= other.getHullSize().ordinal()) {
							boolean ownInterceptTask = BattleNext_ShipAIHelper.hasInterceptTask(this.ship);
							boolean otherInterceptTask = BattleNext_ShipAIHelper.hasInterceptTask(other);
							interceptAdvantage = ownInterceptTask && !otherInterceptTask;
						}

						if (forceAdd || (other.getHullSize().ordinal() >= this.ship.getHullSize().ordinal() || this.isDangerousEntity(other)) && !interceptAdvantage) {
							this.addCollisionSegment(other, other.getLocation(), other.getVelocity(), collisionRadius, facing);
						}
					}
				}
			}
		}
	}

	public void addAsteroid(CombatAsteroidAPI asteroid) {
		this.addAsteroid(asteroid, false);
	}

	public void addAsteroid(CombatAsteroidAPI asteroid, boolean forceAdd) {
		if (forceAdd || !(asteroid.getMass() < this.ship.getMass() * 1.5F) || this.isDangerousEntity(asteroid)) {
			this.addCollisionSegment(asteroid, asteroid.getLocation(), asteroid.getVelocity(), asteroid.getCollisionRadius(), Float.MAX_VALUE);
		}
	}

	private boolean isDangerousEntity(CombatEntityAPI entity) {
		boolean isLooseDrone = false;
		boolean controlsLocked = false;
		if (entity instanceof ShipAPI shipEntity) {
			isLooseDrone = shipEntity.isDrone() && shipEntity.getCollisionClass() == CollisionClass.SHIP;
			controlsLocked = shipEntity.controlsLocked();
		}

		return this.ship.getMass() <= entity.getMass() * 0.75F
			|| this.ship.getHitpoints() < entity.getMass() * 2.0F
			|| entity instanceof ShipAPI && (controlsLocked || ((ShipAPI)entity).isStation() || ((ShipAPI)entity).isStationModule())
			|| isLooseDrone;
	}

	public void addMissile(MissileAPI missile) {
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), missile.getLocation()) - this.ship.getCollisionRadius();
		if (missile.getEtaModifier() < 0.0F) {
			float missileSpeed = Math.max(missile.getMaxSpeed(), 50.0F);
			distance += missile.getEtaModifier() * missileSpeed * 1.5F;
		}

		DamageAPI damage = missile.getDamage();
		float damageDealt = damage.computeDamageDealt(0.0F);
		boolean hasFrontShield = this.hasFrontShield();
		if (hasFrontShield && distance < 2000.0F) {
			distance *= 0.25F;
		}

		if (!(distance > damageDealt)) {
			boolean ignoreThreat = false;
			if (damage.getType() != DamageType.FRAGMENTATION
				|| (!(damageDealt < 500.0F) || this.ship.isFighter() || this.ship.isFrigate()) && (!this.hasOmniShield() || !(this.ship.getFluxLevel() < 0.9F))) {
				if (damageDealt < 1000.0F
					&& this.hasOmniShield()
					&& this.ship.getFluxLevel() < 0.9F
					&& this.ship.getHullSize().ordinal() >= HullSize.CRUISER.ordinal()) {
					ignoreThreat = true;
				}
			} else {
				ignoreThreat = true;
			}

			if (this.ship.getHullSpec().getHints().contains(ShipTypeHints.NEVER_DODGE_MISSILES)) {
				ignoreThreat = true;
			}

			// approximation: engine read the launching weapon's AI hints; MissileAPI exposes only getWeaponSpec()
			boolean strikeWeapon = missile.getWeaponSpec() != null
				&& (missile.getWeaponSpec().getAIHints().contains(AIHints.STRIKE) || missile.getWeaponSpec().getAIHints().contains(AIHints.DANGEROUS));
			// approximation: engine compared getSpec().getType() against MissileType.MIRV; MissileAPI.isMirv() is the API equivalent
			if (!ignoreThreat && this.ship.getShield() != null && this.ship.getShield().isOn() && !missile.isMirv() && !missile.isMine() && !strikeWeapon) {
				boolean inShieldArc = BattleNext_ShipAIHelper.isInArc(
					this.ship.getShield().getFacing(),
					Math.max(0.0F, this.ship.getShield().getActiveArc() - 10.0F),
					this.ship.getShieldCenterEvenIfNoShield(),
					missile.getLocation()
				);
				if (inShieldArc) {
					float fluxToAbsorb = this.ship.getShield().getFluxPerPointOfDamage()
						* this.ship.getMutableStats().getShieldDamageTakenMult().getModifiedValue()
						* damageDealt
						* missile.getDamageType().getShieldMult();
					float fluxAvailable = this.getFluxAvailable();
					if (fluxToAbsorb < fluxAvailable * 0.05F && fluxToAbsorb < 1000.0F) {
						ignoreThreat = true;
					}
				} else {
					float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, missile);
					if (closingSpeed < -this.ship.getCollisionRadius() * 0.1F && closingSpeed < -10.0F && distance > this.ship.getCollisionRadius() * 0.1F) {
						ignoreThreat = true;
					}
				}
			}

			this.addCollisionSegment(missile, missile.getLocation(), missile.getVelocity(), missile.getCollisionRadius(), missile.getFacing(), ignoreThreat);
		}
	}

	public void addPlanet(CombatEntityAPI planet) {
		// vanilla skipped background planets via Planet.isBackgroundPlanet(); star/background state is not visible through CombatEntityAPI so the segment is always added
		this.addCollisionSegment(planet, planet.getLocation(), planet.getVelocity(), planet.getCollisionRadius() + 500.0F, Float.MAX_VALUE);
	}

	public void addIgnoredObject(Object object) {
		this.ignoredObjects.add(object);
		if (object instanceof ShipAPI other) {
			// approximation: the API exposes only getChildModulesCopy(); vanilla read getChildModules()
			for (ShipAPI module : other.getChildModulesCopy()) {
				this.ignoredObjects.add(module);
			}

			if (other.getParentStation() != null) {
				this.ignoredObjects.add(other.getParentStation());

				// approximation: the API exposes only getChildModulesCopy(); vanilla read getChildModules()
				for (ShipAPI module : other.getParentStation().getChildModulesCopy()) {
					this.ignoredObjects.add(module);
				}
			}
		}
	}

	public void addDangerousObject(Object object) {
		this.dangerousObjects.add(object);
	}

	public void addCollisionSegment(Object entity, Vector2f location, Vector2f velocity, float collisionRadius, float facing) {
		this.addCollisionSegment(entity, location, velocity, collisionRadius, facing, false);
	}

	public void addCollisionSegment(Object entity, Vector2f location, Vector2f velocity, float collisionRadius, float facing, boolean ignoreThreat) {
		float ownRadius = this.ship.getCollisionRadius();
		Vector2f ownLocation = this.ship.getLocation();
		float distance = BattleNext_ShipAIHelper.getDistance(ownLocation, location);
		if (entity instanceof ShipAPI otherShip) {
			if (otherShip.isStation()) {
				collisionRadius += 700.0F;
			}

			float targetTimeMult = otherShip.getMutableStats().getTimeMult().getModifiedValue();
			if (targetTimeMult != 1.0F) {
				velocity = new Vector2f(velocity);
				velocity.scale(targetTimeMult);
			}
		}

		boolean isMissile = entity instanceof MissileAPI;
		boolean isMine = false;
		boolean minePrimed = false;
		float mineExplosionRange = 0.0F;
		float untilMineExplosion = 0.0F;
		boolean slowMissile = false;
		float effectiveBrakeTime = this.brakeTime;
		if (isMissile) {
			MissileAPI missile = (MissileAPI)entity;
			isMine = missile.isMine();
			if (isMine && missile.isNoMineFFConcerns() && missile.getOwner() == this.ship.getOwner()) {
				return;
			}

			slowMissile = missile.getSpec().getHullSpec().getEngineSpec().getMaxTurnRate() <= 0.0F;
			if (slowMissile) {
				effectiveBrakeTime += 5.0F;
			}

			minePrimed = missile.isMinePrimed();
			if (isMine && this.ship.isPhased()) {
				minePrimed = true;
			}

			mineExplosionRange = missile.getMineExplosionRange();
			mineExplosionRange += Math.max(50.0F, mineExplosionRange * 0.25F);
			untilMineExplosion = missile.getUntilMineExplosion();
			boolean mineTriggerFlagged = this.ship.getAIFlags().hasFlag(AIFlags.HAS_POTENTIAL_MINE_TRIGGER_NEARBY);
			if (mineTriggerFlagged) {
				untilMineExplosion = 0.01F;
				minePrimed = true;
			}

			if (isMine) {
				effectiveBrakeTime = 3.0F;
			}

			if (this.ship.getPhaseCloak() != null && this.ship.getFluxLevel() < 0.8F) {
				effectiveBrakeTime = Math.min(effectiveBrakeTime, this.ship.getPhaseCloak().getCooldown() + 1.0F);
				if (this.ship.isPhased() && !isMine) {
					effectiveBrakeTime *= 0.2F;
				}
			}
		}

		boolean isShip = entity instanceof ShipAPI;
		ignoreThreat |= isShip
			&& !this.forceAvoidance
			&& ((ShipAPI)entity).getHullSize().ordinal() < this.ship.getHullSize().ordinal()
			&& (!((ShipAPI)entity).isDrone() || ((ShipAPI)entity).getCollisionClass() != CollisionClass.SHIP)
			&& !((ShipAPI)entity).controlsLocked();
		float combinedRadius = (ownRadius + collisionRadius) * 1.0F;
		float maxCheckDistance = combinedRadius + 750.0F + (isMissile ? 500 : 0);
		if (!(distance > maxCheckDistance)) {
			Vector2f awayDirection = Vector2f.sub(ownLocation, location, new Vector2f());
			BattleNext_ShipAIHelper.normalise(awayDirection);
			Vector2f ownVelocity = this.ship.getVelocity();
			float ownTimeMult = this.ship.getMutableStats().getTimeMult().getModifiedValue();
			if (ownTimeMult != 1.0F) {
				ownVelocity = new Vector2f(ownVelocity);
				ownVelocity.scale(ownTimeMult);
			}

			Vector2f relativeVelocity = Vector2f.sub(velocity, ownVelocity, new Vector2f());
			float closingRate = Vector2f.dot(awayDirection, relativeVelocity);
			if (isMine) {
				combinedRadius += mineExplosionRange - collisionRadius;
			}

			if (this.dangerousObjects.contains(entity)) {
				combinedRadius *= 1.25F;
			}

			if (isMine && distance < ownRadius + mineExplosionRange && minePrimed && untilMineExplosion <= effectiveBrakeTime) {
				closingRate = Math.max(100.0F, distance - combinedRadius) / Math.max(0.1F, untilMineExplosion);
			} else if (isMine && closingRate < 50.0F) {
				closingRate = -100.0F;
			}

			float minClosingRate = -10.0F;
			if (entity instanceof ShipAPI && ((ShipAPI)entity).getHitpoints() <= 100 + 300 * (((ShipAPI)entity).getHullSize().ordinal() - 1)) {
				minClosingRate = -100.0F;
			}

			boolean etaOverridden = false;
			float timeToImpact = 0.0F;
			if (isMissile) {
				if (closingRate > 0.0F) {
					timeToImpact = Math.max(0.0F, distance - ownRadius) / closingRate;
				} else {
					timeToImpact = Float.MAX_VALUE;
				}

				MissileAPI missile = (MissileAPI)entity;
				if (missile.getEtaModifier() < 0.0F) {
					float etaAdjust = missile.getEtaModifier();
					etaAdjust -= 3.0F;
					float missileSpeed = Math.max(missile.getMaxSpeed(), 50.0F);
					timeToImpact = (distance - ownRadius) / missileSpeed + etaAdjust;
					combinedRadius = -etaAdjust * missileSpeed * 2.0F;
					etaOverridden = true;
				}
			}

			if (isMissile && this.hasFrontShield()) {
				if (distance > combinedRadius + 50.0F && timeToImpact > effectiveBrakeTime) {
					ignoreThreat = true;
				}

				combinedRadius *= 10.0F;
			}

			if (!(closingRate < minClosingRate) || etaOverridden) {
				float timeToCollision;
				if (etaOverridden) {
					timeToCollision = timeToImpact;
				} else if (closingRate > 0.0F) {
					timeToCollision = Math.max(0.0F, distance - combinedRadius) / closingRate;
				} else {
					timeToCollision = Float.MAX_VALUE;
				}

				if (isMissile && (!isMine || untilMineExplosion < 1.5F && timeToImpact < 1.5F)) {
					this.newCollision = false;
				}

				if ((timeToCollision < effectiveBrakeTime || distance < combinedRadius + 50.0F) && !this.ignoredObjects.contains(entity)) {
					if (!ignoreThreat) {
						this.collisionDetected = true;
						this.collisionCount++;
					}

					this.timeSinceCollision = 0.0F;
					if (distance < combinedRadius || timeToCollision < 2.0F) {
						if (isMissile) {
							if (!ignoreThreat) {
								this.missileThreat = true;
							}

							this.timeSinceMissile = 0.0F;
							MissileAPI missile = (MissileAPI)entity;
							float threatDamage = Math.max(0.0F, 5.0F - timeToCollision) * missile.getDamage().computeDamageDealt(0.0F);
							if (threatDamage > this.maxThreatDamage) {
								this.maxThreatDamage = threatDamage;
								this.missileThreatDirection = new Vector2f(awayDirection);
								this.missileThreatDirection.negate();
							}
						} else {
							if (closingRate > 0.0F && entity instanceof CombatEntityAPI dangerEntity && this.isDangerousEntity(dangerEntity)) {
								float threatMass = Math.max(0.0F, 5.0F - timeToCollision) * dangerEntity.getMass();
								this.maxThreatMass = threatMass;
								this.objectThreatDirection = new Vector2f(awayDirection);
								this.threatEntity = dangerEntity;
								this.objectThreatDirection.negate();
							}

							if (!ignoreThreat) {
								this.collisionCourse = true;
							}

							this.timeSinceCourse = 0.0F;
						}
					}
				}

				if (!ignoreThreat) {
					BattleNext_CollisionCheckSegment segment = new BattleNext_CollisionCheckSegment(entity, location, velocity, collisionRadius, facing);
					segment.setTimeToCollision(timeToCollision);
					float horizon = Math.min(effectiveBrakeTime, timeToCollision);
					// intentionally kept dead: vanilla start offset is always 0.0F
					float startOffset = 0.0F;
					if (isMissile && (this.hasNoDefenses() || this.ship.getFluxTracker().isOverloadedOrVenting())) {
						horizon *= 2.0F;
					}

					Vector2f startPos = new Vector2f(location.x + relativeVelocity.x * startOffset, location.y + relativeVelocity.y * startOffset);
					Vector2f endPos = new Vector2f(location.x + relativeVelocity.x * horizon, location.y + relativeVelocity.y * horizon);
					float startDist = BattleNext_ShipAIHelper.getDistance(ownLocation, startPos);
					float endDist = BattleNext_ShipAIHelper.getDistance(ownLocation, endPos);
					Vector2f startOffsetVector = Vector2f.sub(startPos, ownLocation, new Vector2f());
					Vector2f endOffsetVector = Vector2f.sub(endPos, ownLocation, new Vector2f());
					Vector2f spanVector = Vector2f.add(startOffsetVector, endOffsetVector, new Vector2f());
					float spanCenterAngle = BattleNext_ShipAIHelper.getAngleFast(spanVector);
					float blockingRadius = ownRadius + collisionRadius;
					if (isMine) {
						blockingRadius += mineExplosionRange;
						this.hitStationOrCapital = true;
					}

					float angleDelta = BattleNext_ShipAIHelper.getAngleDiff(startOffsetVector, endOffsetVector);
					// intentionally kept dead: vanilla computes this margin then immediately overwrites it with 0.0F
					float margin = Math.max(0.0F, blockingRadius - startDist);
					margin = 0.0F;
					float startArc = Math.min(180.0F, margin + 360.0F * blockingRadius / (6.28F * startDist));
					float endArc = Math.min(180.0F, margin + 360.0F * blockingRadius / (6.28F * endDist));
					float span = Math.min(170.0F, angleDelta + startArc + endArc);
					float arcDiff = BattleNext_ShipAIHelper.getAngleDiff(startArc, endArc);
					float turnSign = distance < endDist ? -1.0F : 1.0F;
					turnSign *= BattleNext_ShipAIHelper.getTurnDirection(startOffsetVector, endOffsetVector);
					float centerAngle = spanCenterAngle + turnSign * arcDiff / 2.0F;
					segment.setAngleSpan(span);
					segment.setCenterAngle(centerAngle);
					this.segments.add(segment);
				}
			}
		}
	}

	public boolean hasCollision() {
		return this.hadCollision;
	}

	public CombatEntityAPI getThreatEntity() {
		return this.threatEntity;
	}

	public Vector2f getMissileThreatDirection() {
		return this.missileThreatDirection;
	}

	public Vector2f getObjectThreatDirection() {
		return this.objectThreatDirection;
	}

	public void setHeading(float heading) {
		this.heading = heading;
	}

	public void advance(float amount) {
		this.segmentSnapshot.clear();
		this.segmentSnapshot.addAll(this.segments);
		this.lastAvoidanceHeading = this.avoidanceHeading;
		this.lastHeading = this.heading;
		this.hadCollisionSnapshot = this.collisionDetected;
		this.timeSinceCourse += amount;
		this.timeSinceMissile += amount;
		this.timeSinceCollision += amount;
		this.timeSinceThreat += amount;
		this.hitStationOrCapital = false;
		this.hadCollision = this.newCollision;
		this.newCollision = true;
		float gracePeriod = this.brakeTime * 0.25F;
		if (this.timeSinceCollision > gracePeriod) {
			this.collisionDetected = false;
		}

		if (this.timeSinceCourse > gracePeriod) {
			this.collisionCourse = false;
			this.objectThreatDirection = null;
			this.threatEntity = null;
		}

		if (this.timeSinceMissile > gracePeriod) {
			this.missileThreat = false;
			this.missileThreatDirection = null;
		}

		if (this.timeSinceThreat > gracePeriod) {
			this.turnOffset = 0.0F;
		}

		this.collisionCount = 0;
		this.maxThreatMass = 0.0F;
		this.maxThreatDamage = 0.0F;
		this.ignoredObjects.clear();
		this.dangerousObjects.clear();
		this.avoidanceHeading = Float.MAX_VALUE;
		this.heading = Float.MAX_VALUE;
		this.computeBrakeTime();
		this.segments.clear();
	}

	public boolean needsAvoidance() {
		return this.collisionDetected || this.collisionCourse || this.missileThreat;
	}

	public void computeAvoidanceCourse() {
		if (!this.needsAvoidance()) {
			throw new RuntimeException("Collision avoidance not needed");
		}

		List<BattleNext_CollisionTimeRange> safeGaps = this.buildDangerSegments();
		if (safeGaps == null) {
			this.avoidanceHeading = this.heading;
		} else if (safeGaps.isEmpty()) {
			this.avoidanceHeading = Float.MAX_VALUE;
		} else {
			// intentionally kept dead: vanilla declares a null Object here and never uses it
			Object unused = null;
			boolean singleThreat = !this.missileThreat && this.collisionCount <= 1;
			boolean singleWideGap = safeGaps.size() == 1 && safeGaps.get(0).getWidth() > 120.0F;
			BattleNext_CollisionCheckSegment firstSegment = null;
			if (this.segments.size() >= 1 && singleThreat || singleWideGap) {
				firstSegment = this.segments.get(0);
			}

			float referenceHeading;
			if (this.hasHeading()) {
				if (this.ship.getVelocity().length() > 10.0F) {
					Vector2f headingVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.heading);
					Vector2f velocityDirection = BattleNext_ShipAIHelper.normalise(new Vector2f(this.ship.getVelocity()));
					headingVector.scale(3.0F);
					Vector2f blended = Vector2f.add(headingVector, velocityDirection, new Vector2f());
					referenceHeading = BattleNext_ShipAIHelper.getAngleFast(blended);
				} else {
					referenceHeading = this.heading;
				}
			} else {
				float speed = this.ship.getVelocity().length();
				if (speed > 30.0F) {
					referenceHeading = BattleNext_ShipAIHelper.getAngleFast(this.ship.getVelocity());
				} else {
					referenceHeading = this.ship.getFacing();
				}
			}

			for (BattleNext_CollisionCheckSegment segment : this.segments) {
				if (segment.getEntity() instanceof ShipAPI && ((ShipAPI)segment.getEntity()).isStation()) {
					this.hitStationOrCapital = true;
				}

				if (segment.getEntity() instanceof ShipAPI other) {
					if (other.getOwner() == this.ship.getOwner() && other.getHullSize().ordinal() >= HullSize.CRUISER.ordinal()) {
						this.hitStationOrCapital = true;
					}
				}
			}

			float bestDiff = Float.MAX_VALUE;

			for (BattleNext_CollisionTimeRange gap : safeGaps) {
				float targetAngle;
				if (this.collisionCourse && !singleThreat) {
					targetAngle = gap.getStartAngle();
				} else if (!this.missileThreat && !singleThreat) {
					targetAngle = BattleNext_ShipAIHelper.clampAngleToArc(gap.getStartAngle(), gap.getWidth(), referenceHeading);
				} else if (firstSegment != null) {
					Vector2f steerVector = new Vector2f();
					Vector2f segmentVelocity = firstSegment.getVelocity();
					if (segmentVelocity.length() > 5.0F) {
						steerVector.set(segmentVelocity);
						BattleNext_ShipAIHelper.normalise(steerVector);
						steerVector.negate();
					} else if (firstSegment.getEntity() instanceof ShipAPI && ((ShipAPI)firstSegment.getEntity()).isStation()) {
						steerVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.ship.getFacing() + 180.0F);
					} else if (firstSegment.getFacing() != Float.MAX_VALUE) {
						steerVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(firstSegment.getFacing());
						steerVector.negate();
					}

					Vector2f.add(steerVector, BattleNext_ShipAIHelper.getUnitVectorAtAngle(referenceHeading), steerVector);
					if (this.collisionCourse) {
						Vector2f gapDirection = BattleNext_ShipAIHelper.getUnitVectorAtAngle(gap.getStartAngle());
						gapDirection.scale(2.0F);
						Vector2f.add(steerVector, gapDirection, steerVector);
					}

					float gapWidth = gap.getWidth();
					if (singleWideGap) {
						gapWidth = Math.min(gapWidth - 60.0F, gapWidth * 0.5F);
					}

					targetAngle = BattleNext_ShipAIHelper.clampAngleToArc(gap.getStartAngle(), gapWidth, BattleNext_ShipAIHelper.getAngleFast(steerVector));
				} else {
					float escapeMargin = 45.0F;
					if (this.hasNoDefenses() || this.ship.getFluxTracker().isOverloadedOrVenting()) {
						escapeMargin = 10.0F;
					}

					this.turnOffset = BattleNext_ShipAIHelper.getTurnDirection(gap.getStartAngle(), referenceHeading);
					targetAngle = BattleNext_ShipAIHelper.clampAngleToArc(gap.getStartAngle(), gap.getWidth(), gap.getStartAngle() + escapeMargin * this.turnOffset);
				}

				float angleDiff = BattleNext_ShipAIHelper.getAngleDiff(targetAngle, referenceHeading);
				if (angleDiff < bestDiff) {
					bestDiff = angleDiff;
					this.avoidanceHeading = targetAngle;
				}
			}

			if (!this.hitStationOrCapital
				&& this.ship.isPhased()
				&& !this.isUnphasing()
				&& this.ship.getHardFluxLevel() < 0.8F
				&& this.ship.getFluxLevel() < 0.9F) {
				this.avoidanceHeading = this.heading;
			}
		}
	}

	public boolean hasAvoidanceHeading() {
		return this.avoidanceHeading != Float.MAX_VALUE;
	}

	public float getAvoidanceHeading() {
		if (!this.hasAvoidanceHeading()) {
			throw new RuntimeException("No heading available");
		} else {
			return this.avoidanceHeading;
		}
	}

	public float getHeading() {
		return this.heading;
	}

	public boolean hasHeading() {
		return this.heading != Float.MAX_VALUE;
	}

	private void computeBrakeTime() {
		float maxSpeed = this.ship.getMaxSpeed();
		float acceleration = this.ship.getAcceleration();
		float deceleration = this.ship.getDeceleration();
		this.brakeTime = 1.0F * maxSpeed / ((acceleration + deceleration) / 2.0F);
		if (this.brakeTime > 5.0F) {
			this.brakeTime = 5.0F;
		}
	}

	private List<BattleNext_CollisionTimeRange> buildDangerSegments() {
		if (this.segments.isEmpty()) {
			return null;
		}

		List<BattleNext_CollisionDangerEntry> entries = new ArrayList<>();

		for (BattleNext_CollisionCheckSegment segment : this.segments) {
			BattleNext_CollisionDangerEntry startEntry = new BattleNext_CollisionDangerEntry(segment, BattleNext_CollisionDangerEntry.DANGER_START, segment.getCenterAngle() - segment.getAngleSpan() / 2.0F);
			BattleNext_CollisionDangerEntry endEntry = new BattleNext_CollisionDangerEntry(segment, BattleNext_CollisionDangerEntry.DANGER_END, segment.getCenterAngle() + segment.getAngleSpan() / 2.0F);
			entries.add(startEntry);
			entries.add(endEntry);
		}

		entries.sort(new BattleNext_CollisionDangerComparator(this));
		List<BattleNext_CollisionTimeRange> safeGaps = new ArrayList<>();
		int depth = 0;
		float gapWidth = 0.0F;
		float gapStart = 0.0F;
		boolean inGap = false;

		for (BattleNext_CollisionDangerEntry entry : entries) {
			if (entry.isDangerStart()) {
				depth++;
			} else {
				depth--;
			}

			if (inGap && depth > 0) {
				gapWidth = entry.getAngle() - gapStart;
				BattleNext_CollisionTimeRange gap = new BattleNext_CollisionTimeRange();
				gap.setStartAngle(BattleNext_ShipAIHelper.normalizeAngle(entry.getAngle() - gapWidth / 2.0F));
				gap.setWidth(gapWidth);
				safeGaps.add(gap);
				gapWidth = 0.0F;
				inGap = false;
			}

			if (depth == 0) {
				inGap = true;
				gapStart = entry.getAngle();
			}
		}

		BattleNext_CollisionDangerEntry firstEntry = entries.get(0);
		float wrappedAngle = firstEntry.getAngle() + 360.0F;
		if (wrappedAngle > gapStart) {
			gapWidth = wrappedAngle - gapStart;
			BattleNext_CollisionTimeRange gap = new BattleNext_CollisionTimeRange();
			gap.setStartAngle(BattleNext_ShipAIHelper.normalizeAngle(wrappedAngle - gapWidth / 2.0F));
			gap.setWidth(gapWidth);
			safeGaps.add(gap);
		}

		return safeGaps;
	}

	public void unusedReset() {
		// intentionally kept dead: vanilla body is empty
	}

	private boolean hasOmniShield() {
		// approximation: ShipAPI has no hasOmniShield(); this replicates the engine implementation
		return this.ship.getShield() != null && this.ship.getShield().getType() == ShieldAPI.ShieldType.OMNI;
	}

	private boolean hasFrontShield() {
		// approximation: ShieldAPI has no isFront(); type check replicates the engine implementation
		return this.ship.getShield() != null && this.ship.getShield().getType() == ShieldAPI.ShieldType.FRONT;
	}

	private boolean isUnphasing() {
		// approximation: ShipAPI has no isUnphasing(); this replicates the engine implementation via the phase cloak state
		return this.ship.getPhaseCloak() != null && this.ship.getPhaseCloak().getState() == ShipSystemAPI.SystemState.OUT;
	}

	private boolean hasNoDefenses() {
		// approximation: ShipAPI has no hasNoDefenses(); this replicates the engine implementation
		return this.ship.getShield() == null
			&& this.ship.getPhaseCloak() == null
			&& (this.ship.getSystem() == null || !this.ship.getSystem().getId().equals(this.ship.getHullSpec().getShipDefenseId()));
	}

	private float getFluxAvailable() {
		// approximation: ShipAPI has no getFluxAvailable(); this replicates the engine implementation
		return this.ship.getFluxTracker().getMaxFlux() - this.ship.getFluxTracker().getCurrFlux();
	}
}
