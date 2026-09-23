package data.scripts.ai.vanilla.attack;

import com.fs.starfarer.api.combat.AutofireAIPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import java.util.ArrayList;
import java.util.Iterator;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.PointDefenseAutofireAI.
 * Per-weapon point defense targeting and fire permission state machine.
 */
public class BattleNext_PointDefenseAutofireAI implements AutofireAIPlugin {
	// f_d435ae80_4, inlined by the compiler into the literals below
	private static final float IDLE_REEVAL_INTERVAL = 0.05F;
	// f_d435ae80_8 / f_d435ae80_3, inlined by the compiler into the literals below
	private static final float TRACKING_REEVAL_INTERVAL = 0.5F;
	private WeaponAPI weapon;
	private float reEvalTimer;
	private float reEvalInterval;
	private BattleNext_AutofireState state = BattleNext_AutofireState.WAITING;
	private float reEvalIntervalMult = 1.0F;
	private CombatEntityAPI target;
	private ShipAPI ship;
	private IntervalUtil intervalTracker = new IntervalUtil(0.25F, 0.5F);
	private boolean blocked = false;
	private boolean ffDanger = false;
	private boolean ammoOk = true;
	private float timeFiring = 0.0F;
	private float timeSinceLastFire = 0.0F;
	private CombatEntityAPI previousTarget = null;

	public BattleNext_PointDefenseAutofireAI(WeaponAPI weapon) {
		this.weapon = weapon;
		this.ship = weapon.getShip();
		this.reEvalInterval = (float) Math.random() * IDLE_REEVAL_INTERVAL;
		if (weapon.getSize() != WeaponSize.SMALL && !weapon.hasAIHint(AIHints.PD)) {
			if (weapon.getSize() == WeaponSize.MEDIUM) {
				this.reEvalIntervalMult = 0.5F;
			} else if (weapon.getSize() == WeaponSize.LARGE) {
				this.reEvalIntervalMult = 1.0F;
			}
		} else {
			this.reEvalIntervalMult = 0.25F;
		}
	}

	@Override
	public void forceOff() {
		this.setTarget(null);
		this.state = BattleNext_AutofireState.WAITING;
		this.reEvalTimer = 0.0F;
		this.reEvalInterval = (float) Math.random() * IDLE_REEVAL_INTERVAL;
	}

	@Override
	public void advance(float amount) {
		this.reEvalTimer += amount;
		if (this.target != this.previousTarget) {
			this.previousTarget = this.target;
			this.timeFiring = 0.0F;
			this.timeSinceLastFire = 0.0F;
		}

		if (this.weapon.isFiring()) {
			this.timeFiring += amount;
			this.timeSinceLastFire = 0.0F;
		} else {
			this.timeSinceLastFire += amount;
		}

		if (this.timeSinceLastFire > 3.0F) {
			this.timeFiring = 0.0F;
			this.timeSinceLastFire = 0.0F;
		}

		if (!this.weapon.isDisabled() && (!this.weapon.usesAmmo() || this.weapon.getAmmo() != 0)) {
			this.intervalTracker.advance(amount);
			if (this.intervalTracker.intervalElapsed()) {
				this.updateBlockedState();
			}

			if (this.state == BattleNext_AutofireState.TRACKING
				&& (!this.weapon.hasAIHint(AIHints.PD) || this.target instanceof MissileAPI)
				&& this.isTargetValid()
				&& this.canHitTarget(0.0F)) {
				this.updateAmmoOk();
				if (this.ammoOk) {
					this.state = BattleNext_AutofireState.FIRING;
				}
			}

			if (this.reEvalTimer >= this.reEvalInterval) {
				this.reEvalTarget();
				if (this.state == BattleNext_AutofireState.TRACKING) {
					this.reEvalInterval = (float) Math.random() * TRACKING_REEVAL_INTERVAL + TRACKING_REEVAL_INTERVAL;
					this.reEvalInterval = this.reEvalInterval * this.reEvalIntervalMult;
				} else {
					this.reEvalInterval = (float) Math.random() * IDLE_REEVAL_INTERVAL + IDLE_REEVAL_INTERVAL;
				}

				if (this.state == BattleNext_AutofireState.TRACKING && this.isTargetValid() && this.canHitTarget(0.0F)) {
					this.updateAmmoOk();
					if (this.ammoOk) {
						this.state = BattleNext_AutofireState.FIRING;
					}
				}

				this.reEvalTimer = 0.0F;
			}
		} else {
			this.state = BattleNext_AutofireState.WAITING;
		}
	}

	private void updateAmmoOk() {
		if (this.weapon.hasAIHint(AIHints.USE_LESS_VS_SHIELDS)
			&& this.target != null
			&& this.target instanceof ShipAPI
			&& !((ShipAPI) this.target).isFighter()) {
			if (this.target.getShield() != null && this.target.getShield().isOn()) {
				ShipAPI ship = this.weapon.getShip();
				WeaponSlotAPI slot = this.weapon.getSlot();
				Vector2f slotPos = slot.computePosition(ship);
				boolean shieldFacingUs = BattleNext_ShipAIHelper.isInArc(
					this.target.getShield().getFacing(),
					this.target.getShield().getActiveArc(),
					((ShipAPI) this.target).getShieldCenterEvenIfNoShield(),
					slotPos
				);
				if (shieldFacingUs) {
					if (this.weapon.getAmmo() <= 1 || this.weapon.getAmmo() <= this.weapon.getMaxAmmo() * 0.8F) {
						this.ammoOk = false;
					}

					if (!this.ammoOk && this.weapon.getAmmo() >= this.weapon.getMaxAmmo() * 1.0F) {
						this.ammoOk = true;
					}

					if (!this.weapon.usesAmmo() || this.weapon.getAmmoPerSecond() <= 0.0F) {
						this.ammoOk = false;
					}
				} else {
					this.ammoOk = true;
				}
			} else {
				this.ammoOk = true;
			}
		} else if (!this.weapon.hasAIHint(AIHints.PD)
			&& this.weapon.usesAmmo()
			&& !(this.weapon.getSpec().getAmmoPerSecond() <= 0.0F)
			&& !this.weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)) {
			if (this.weapon.getAmmo() <= 1) {
				this.ammoOk = false;
			}

			if (!this.ammoOk
				&& (this.weapon.getAmmo() >= this.weapon.getMaxAmmo() * 0.34F
					|| this.weapon.getAmmo() >= 5
					|| this.weapon.getMaxAmmo() < 12)) {
				this.ammoOk = true;
			}
		}
	}

	private void setTarget(CombatEntityAPI target) {
		this.target = target;
		this.updateBlockedState();
	}

	private void reEvalTarget() {
		if (this.isTargetValid()
			&& this.canHitTarget(30.0F)
			&& (!this.weapon.hasAIHint(AIHints.PD) || this.target instanceof MissileAPI)) {
			if (this.ship.getShipTarget() != null
				&& this.ship.getShipTarget() != this.target
				&& (!this.weapon.hasAIHint(AIHints.PD) || !(this.target instanceof MissileAPI))
				&& this.ship.getShipTarget().getOriginalOwner() != this.ship.getOwner()
				&& this.canFireAtTarget(null, this.ship.getShipTarget())) {
				this.setTarget(null);
				if (this.weapon.hasAIHint(AIHints.PD)) {
					this.findShipTarget();
				} else {
					this.setTarget(this.ship.getShipTarget());
					if (!this.isTargetValid()) {
						this.setTarget(null);
						this.findShipTarget();
					}
				}
			}
		} else {
			this.setTarget(null);
			this.findShipTarget();
		}

		if (this.target != null) {
			this.state = BattleNext_AutofireState.TRACKING;
		} else {
			this.state = BattleNext_AutofireState.WAITING;
		}
	}

	// intentionally kept dead: vanilla method is never called
	private boolean unusedAlwaysFalse() {
		return false;
	}

	private float getAutofireAccuracyBonus() {
		return this.weapon.getSpec().getAutofireAccBonus();
	}

	private float getTargetLeadTime() {
		return (this.weapon.hasAIHint(AIHints.PD) || this.weapon.hasAIHint(AIHints.PD_ONLY))
				&& !this.weapon.hasAIHint(AIHints.STRIKE)
				&& this.ship != null
				&& this.ship.getMutableStats().getDynamic().getValue("pd_best_target_leading", 0.0F) >= 1.0F
			? 10000.0F
			: this.timeFiring;
	}

	private void findShipTarget() {
		if (!this.isTargetValid() || this.weapon.hasAIHint(AIHints.PD) && !(this.target instanceof MissileAPI)) {
			boolean onlyFightersAllowed = this.weapon.hasAIHint(AIHints.PD_ONLY) && !this.weapon.hasAIHint(AIHints.ANTI_FTR);
			boolean pdOnlyAntiFighter = this.weapon.hasAIHint(AIHints.PD_ONLY) && this.weapon.hasAIHint(AIHints.ANTI_FTR);
			if (!this.weapon.hasAIHint(AIHints.PD_ALSO)
				&& (this.weapon.hasAIHint(AIHints.PD) || this.weapon.hasAIHint(AIHints.PD_ONLY))
				&& !this.weapon.hasAIHint(AIHints.STRIKE)) {
				this.findMissileTarget();
				if (this.isTargetValid()) {
					return;
				}
			}

			if (!onlyFightersAllowed) {
				if (this.weapon.hasAIHint(AIHints.PD)
					&& this.weapon.getAmmoTracker() != null
					&& this.weapon.getAmmoTracker().getAmmoPerSecond() > 0.0F
					&& this.weapon.getAmmo() <= 10
					&& this.weapon.getSpec().getReloadSize() <= 1.0F
					&& this.weapon.getAmmo() < this.weapon.getMaxAmmo()) {
					if (!this.weapon.hasAIHint(AIHints.ANTI_FTR)) {
						return;
					}

					pdOnlyAntiFighter = true;
				}

				ShipAPI ship = this.weapon.getShip();
				WeaponSlotAPI slot = this.weapon.getSlot();
				Vector2f slotPos = slot.computePosition(ship);
				FogOfWarAPI fogOfWar = BattleNext_ShipAIHelper.getEngine().getFogOfWar(ship.getOwner());
				float bestAngle = Float.MAX_VALUE;
				ArrayList<CombatEntityAPI> spareTargets = new ArrayList<>();
				float searchSize = this.weapon.getRange() * 2.0F + 50.0F;
				boolean fromTargetingOval = this.weapon.hasAIHint(AIHints.RANGE_FROM_TARGETING_OVAL);
				boolean fromShipRadius = this.weapon.hasAIHint(AIHints.RANGE_FROM_SHIP_RADIUS);
				if (fromTargetingOval || fromShipRadius) {
					slotPos = ship.getLocation();
					searchSize += ship.getCollisionRadius();
				}

				Iterator<Object> gridIter = BattleNext_ShipAIHelper.getEngine().getAiGridShips().getCheckIterator(slotPos, searchSize, searchSize);

				while (gridIter.hasNext()) {
					if (gridIter.next() instanceof ShipAPI other
						&& ship.getOwner() != other.getOwner()
						&& other.isTargetable()
						&& (fogOfWar == null || fogOfWar.isVisible(other) || ship.isDrone())
						&& !other.isShuttlePod()
						&& (!pdOnlyAntiFighter || other.isFighter() || other.isDrone())) {
						boolean plainFighter = other.isFighter() && other.getCollisionClass() == CollisionClass.FIGHTER;
						if ((
								!plainFighter
									|| !this.weapon.hasAIHint(AIHints.STRIKE)
									|| this.weapon.hasAIHint(AIHints.PD)
									|| this.weapon.hasAIHint(AIHints.PD_ONLY)
									|| this.weapon.hasAIHint(AIHints.ANTI_FTR)
							)
							&& (
								!plainFighter
									|| this.weapon.getType() != WeaponType.MISSILE
									|| !this.weapon.usesAmmo()
									|| this.weapon.hasAIHint(AIHints.DO_NOT_AIM)
									|| this.weapon.hasAIHint(AIHints.PD)
									|| this.weapon.hasAIHint(AIHints.PD_ONLY)
									|| this.weapon.hasAIHint(AIHints.ANTI_FTR)
							)
							&& (
								!other.isFrigate()
									|| other.getStationSlot() != null
									|| !this.weapon.hasAIHint(AIHints.STRIKE)
									|| this.weapon.hasAIHint(AIHints.USE_VS_FRIGATES)
							)
							&& ship.getOwner() != other.getOwner()
							&& !other.isHulk()
							&& other.getOwner() != 100) {
							float surfaceDist = BattleNext_ShipAIHelper.getDistanceToEntitySurface(slotPos, other, true);
							float centerDist = BattleNext_ShipAIHelper.getDistance(slotPos, other.getLocation());
							if (fromTargetingOval) {
								centerDist -= BattleNext_ShipAIHelper.getDistanceToEntitySurface(other.getLocation(), ship, false);
							} else if (fromShipRadius) {
								centerDist -= ship.getCollisionRadius();
							}

							float weaponRange = this.weapon.getRange();
							boolean inRange = centerDist <= weaponRange + surfaceDist;
							if (inRange) {
								Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(
									ship, other, this.weapon.getProjectileSpeed(), false, this.getTargetLeadTime(), this.getAutofireAccuracyBonus()
								);
								float extraArc = 1.75F * surfaceDist / ((float) (Math.PI * 2) * centerDist) * 360.0F;
								boolean doNotAim = this.weapon.hasAIHint(AIHints.DO_NOT_AIM);
								boolean inArc = BattleNext_ShipAIHelper.isInArc(slot.computeMidArcAngle(ship), slot.getArc() + extraArc, slotPos, aimPoint);
								if (doNotAim && !inArc) {
									inArc = true;
								}

								if (inRange && inArc) {
									if (ship.getShipTarget() != null && other == ship.getShipTarget()) {
										this.setTarget(other);
										break;
									}

									float angleDiff = BattleNext_ShipAIHelper.getAngleDiff(this.weapon.getCurrAngle(), slotPos, aimPoint);
									if (angleDiff < bestAngle) {
										bestAngle = angleDiff;
										this.setTarget(other);
									}
								}
							}
						}
					}
				}

				if (!this.isTargetValid() && this.weapon.hasAIHint(AIHints.PD) && this.weapon.hasAIHint(AIHints.PD_ALSO)) {
					this.findMissileTarget();
				}

				// intentionally kept dead: vanilla never adds to this list
				if (!spareTargets.isEmpty()) {
					int pick = (int) (Math.random() * spareTargets.size());
					this.setTarget(spareTargets.get(pick));
				}
			}
		}
	}

	private boolean canFireAtTarget(Vector2f from, ShipAPI target) {
		WeaponSlotAPI slot = this.weapon.getSlot();
		if (from == null) {
			from = slot.computePosition(this.ship);
		}

		boolean fromTargetingOval = this.weapon.hasAIHint(AIHints.RANGE_FROM_TARGETING_OVAL);
		boolean fromShipRadius = this.weapon.hasAIHint(AIHints.RANGE_FROM_SHIP_RADIUS);
		if (fromTargetingOval || fromShipRadius) {
			from = this.ship.getLocation();
		}

		float surfaceDist = BattleNext_ShipAIHelper.getDistanceToEntitySurface(from, target, true);
		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(
			this.ship, target, this.weapon.getProjectileSpeed(), false, this.getTargetLeadTime(), this.getAutofireAccuracyBonus()
		);
		float aimDist = BattleNext_ShipAIHelper.getDistance(from, aimPoint);
		float extraArc = 1.75F * surfaceDist / ((float) (Math.PI * 2) * aimDist) * 360.0F;
		boolean doNotAim = this.weapon.hasAIHint(AIHints.DO_NOT_AIM);
		boolean inArc = BattleNext_ShipAIHelper.isInArc(slot.computeMidArcAngle(this.ship), slot.getArc() + extraArc, this.ship.getLocation(), aimPoint);
		if (doNotAim && !inArc) {
			inArc = true;
		}

		if (fromTargetingOval && target != null) {
			aimDist -= BattleNext_ShipAIHelper.getDistanceToEntitySurface(target.getLocation(), this.ship, false);
		} else if (fromShipRadius) {
			aimDist -= this.ship.getCollisionRadius();
		}

		float weaponRange = this.weapon.getRange();
		boolean inRange = aimDist <= weaponRange + surfaceDist;
		return inRange && inArc;
	}

	private void findMissileTarget() {
		ShipAPI ship = this.weapon.getShip();
		WeaponSlotAPI slot = this.weapon.getSlot();
		Vector2f slotPos = slot.computePosition(ship);
		float searchSize = this.weapon.getRange() * 2.0F + 100.0F;
		boolean fromTargetingOval = this.weapon.hasAIHint(AIHints.RANGE_FROM_TARGETING_OVAL);
		boolean fromShipRadius = this.weapon.hasAIHint(AIHints.RANGE_FROM_SHIP_RADIUS);
		if (fromTargetingOval || fromShipRadius) {
			slotPos = ship.getLocation();
			searchSize += ship.getCollisionRadius();
		}

		float bestAngle = Float.MAX_VALUE;
		CombatEntityAPI forcedTarget = null;
		boolean forcedTargetValid = false;
		ArrayList<CombatEntityAPI> candidates = new ArrayList<>();
		if (forcedTarget == null) {
			Iterator<Object> gridIter = BattleNext_ShipAIHelper.getEngine().getAiGridMissiles().getCheckIterator(slotPos, searchSize, searchSize);

			while (gridIter.hasNext()) {
				if (gridIter.next() instanceof MissileAPI missile
					&& missile.getCollisionClass() != CollisionClass.NONE
					&& missile.getOwner() != ship.getOwner()) {
					if (missile.isFlare()) {
						boolean ignoresFlares = ship != null && ship.getMutableStats().getDynamic().getValue("pd_ignores_flares", 0.0F) >= 1.0F;
						ignoresFlares |= this.weapon.hasAIHint(AIHints.IGNORES_FLARES);
						if (ignoresFlares) {
							continue;
						}
					}

					float missileRadius = missile.getCollisionRadius();
					Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(
						ship, missile, this.weapon.getProjectileSpeed(), false, this.getTargetLeadTime(), this.getAutofireAccuracyBonus()
					);
					float aimDist = BattleNext_ShipAIHelper.getDistance(slotPos, aimPoint);
					if (fromTargetingOval) {
						aimDist -= BattleNext_ShipAIHelper.getDistanceToEntitySurface(aimPoint, ship, false);
					} else if (fromShipRadius) {
						aimDist -= ship.getCollisionRadius();
					}

					float weaponRange = this.weapon.getRange();
					boolean inRange = aimDist <= weaponRange + missileRadius;
					if (inRange) {
						boolean inArc = BattleNext_ShipAIHelper.isInArc(slot.computeMidArcAngle(ship), slot.getArc() + 15.0F, ship.getLocation(), aimPoint);
						boolean doNotAim = this.weapon.hasAIHint(AIHints.DO_NOT_AIM);
						if (doNotAim && !inArc) {
							inArc = true;
						}

						if (inRange && inArc) {
							candidates.add(missile);
						}
					}
				}
			}
		}

		if (!candidates.isEmpty() && (forcedTarget == null || !forcedTargetValid)) {
			int pick = (int) (Math.random() * candidates.size());
			this.setTarget(candidates.get(pick));
		} else {
			this.setTarget(forcedTarget);
		}
	}

	private boolean isTargetValid() {
		if (this.target == null) {
			return false;
		}

		if (this.target instanceof ShipAPI && ((ShipAPI) this.target).isHulk()) {
			return false;
		}

		if (this.target.wasRemoved()) {
			return false;
		}

		if (this.target.getOwner() != 100 && this.target.getOwner() != this.weapon.getShip().getOwner()) {
			FogOfWarAPI fogOfWar = BattleNext_ShipAIHelper.getEngine().getFogOfWar(this.ship.getOwner());
			if (fogOfWar != null && !fogOfWar.isVisible(this.target) && !this.ship.isDrone()) {
				return false;
			}

			boolean stillThere = this.target != null && !this.target.isExpired();
			if (!stillThere) {
				this.setTarget(null);
			}

			return stillThere;
		} else {
			return false;
		}
	}

	private void updateBlockedState() {
		this.blocked = false;
		this.ffDanger = false;
		if (this.target != null) {
			Vector2f slotPos = this.weapon.getSlot().computePosition(this.ship);
			Vector2f shotEnd = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.weapon.getCurrAngle());
			shotEnd.scale(this.weapon.getRange());
			Vector2f.add(slotPos, shotEnd, shotEnd);
			if (!this.canHitTarget(0.0F)) {
				Vector2f targetPoint = this.getTarget();
				if (targetPoint != null) {
					shotEnd = targetPoint;
				}
			}

			if (BattleNext_ShipAIHelper.areHulksInTheWay(this.ship, this.target, slotPos, shotEnd)) {
				this.blocked = true;
			}

			CollisionClass projectileCollision = this.weapon.getProjectileCollisionClass();
			boolean canHitShips = false;
			if (projectileCollision != CollisionClass.RAY_FIGHTER
				&& projectileCollision != CollisionClass.PROJECTILE_FIGHTER
				&& projectileCollision != CollisionClass.HITS_SHIPS_ONLY_NO_FF
				&& projectileCollision != CollisionClass.NONE
				&& projectileCollision != CollisionClass.MISSILE_NO_FF
				&& projectileCollision != CollisionClass.PROJECTILE_NO_FF) {
				canHitShips = true;
			}

			if (canHitShips) {
				float dangerRange = this.weapon.getRange();
				if (!this.weapon.isBeam()) {
					dangerRange += 150.0F;
				}

				if (BattleNext_ShipAIHelper.computeDanger(
						this.ship,
						this.target,
						slotPos,
						shotEnd,
						this.weapon.getProjectileSpeed(),
						this.weapon.getSpec().getDerivedStats().getBurstFireDuration(),
						dangerRange
					)
					> 0.1F) {
					this.ffDanger = true;
				}
			}
		}
	}

	@Override
	public boolean shouldFire() {
		if (this.state != BattleNext_AutofireState.FIRING) {
			return false;
		}

		if (this.target == null) {
			return false;
		}

		if (!this.blocked && !this.ffDanger) {
			// approximation: engine getFluxAvailable() == fluxTracker.getMaxFlux() - fluxTracker.getCurrFlux()
			if (this.weapon.getFluxCostToFire() > this.ship.getMaxFlux() - this.ship.getCurrFlux()) {
				return false;
			}

			if (this.target instanceof ShipAPI targetShip) {
				if (targetShip.isPhased()) {
					if (this.weapon.usesAmmo()) {
						return false;
					}

					if (this.weapon.getCooldown() >= 5.0F) {
						return false;
					}

					ShipwideAIFlags flags = this.ship.getAIFlags();
					if (flags != null) {
						float fluxCostFraction = this.weapon.getFluxCostToFire() / this.ship.getMaxFlux();
						float softFluxLevel = this.ship.getFluxLevel() - this.ship.getHardFluxLevel();
						float headroom = 1.0F - this.ship.getFluxLevel();
						return softFluxLevel <= 0.1F && headroom >= fluxCostFraction * 4.0F;
					}

					// approximation: engine isUnphasing() == phase cloak charge state == OUT
					if (targetShip.isPhased()
						&& (!isUnphasing(targetShip) || (float) Math.random() < 0.95F)) {
						return false;
					}
				}

				if (this.weapon.hasAIHint(AIHints.USE_LESS_VS_SHIELDS)
					&& this.target != null
					&& this.target instanceof ShipAPI
					&& !((ShipAPI) this.target).isFighter()
					&& this.target.getShield() != null
					&& this.target.getShield().isOn()) {
					ShipAPI ship = this.weapon.getShip();
					WeaponSlotAPI slot = this.weapon.getSlot();
					Vector2f slotPos = slot.computePosition(ship);
					boolean shieldFacingUs = BattleNext_ShipAIHelper.isInArc(
						this.target.getShield().getFacing(),
						this.target.getShield().getActiveArc(),
						((ShipAPI) this.target).getShieldCenterEvenIfNoShield(),
						slotPos
					);
					if (shieldFacingUs && (this.weapon.getAmmo() <= 1 || this.weapon.getAmmo() <= this.weapon.getMaxAmmo() * 0.75F)) {
						return false;
					}
				}
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	public Vector2f getTarget() {
		return this.target == null
			? null
			: BattleNext_ShipAIHelper.getAimPoint(
				this.weapon.getShip(), this.target, this.weapon.getProjectileSpeed(), false, this.getTargetLeadTime(), this.getAutofireAccuracyBonus()
			);
	}

	@Override
	public ShipAPI getTargetShip() {
		return this.target instanceof ShipAPI ? (ShipAPI) this.target : null;
	}

	@Override
	public MissileAPI getTargetMissile() {
		return this.target instanceof MissileAPI ? (MissileAPI) this.target : null;
	}

	@Override
	public WeaponAPI getWeapon() {
		return this.weapon;
	}

	// approximation: engine Ship.isUnphasing() == phase cloak charge state == OUT; not exposed on ShipAPI
	private static boolean isUnphasing(ShipAPI ship) {
		return ship.getPhaseCloak() != null && ship.getPhaseCloak().getState() == ShipSystemAPI.SystemState.OUT;
	}

	private boolean canHitTarget(float extraRadius) {
		if (this.target == null) {
			return false;
		}

		if (this.weapon.getAmmo() <= 0) {
			return false;
		}

		ShipAPI ship = this.weapon.getShip();
		Vector2f aimPoint = BattleNext_ShipAIHelper.getAimPoint(
			ship, this.target, this.weapon.getProjectileSpeed(), true, this.getTargetLeadTime(), this.getAutofireAccuracyBonus()
		);
		if (aimPoint == null) {
			return false;
		}

		Vector2f slotPos = this.weapon.getSlot().computePosition(ship);
		float surfaceDist = BattleNext_ShipAIHelper.getDistanceToEntitySurface(slotPos, this.target, true);
		float aimDist = BattleNext_ShipAIHelper.getDistance(slotPos, aimPoint);
		float leadArc = 1.5F * surfaceDist * 0.5F / ((float) (Math.PI * 2) * aimDist) * 360.0F;
		leadArc += this.weapon.getSpec().getExtraArcForAI() / 2.0F;
		float weaponRange = this.weapon.getRange();
		if (this.weapon.getProjectileFadeRange() <= 0.0F && weaponRange > 300.0F) {
			weaponRange -= surfaceDist * 0.4F;
		}

		if ((
				this.weapon.hasAIHint(AIHints.DO_NOT_AIM)
					|| BattleNext_ShipAIHelper.isInArc(this.weapon.getSlot().computeMidArcAngle(ship), this.weapon.getSlot().getArc() + leadArc * 2.0F, slotPos, aimPoint)
			)
			&& aimDist <= weaponRange + surfaceDist) {
			float targetAngle = BattleNext_ShipAIHelper.getAngleFast(slotPos, aimPoint);
			float angleError = BattleNext_ShipAIHelper.getAngleDiff(this.weapon.getCurrAngle(), targetAngle);
			angleError -= leadArc;
			if (angleError < 0.0F || this.weapon.hasAIHint(AIHints.DO_NOT_AIM)) {
				angleError = 0.0F;
			}

			return !(angleError > 0.0F);
		} else {
			return false;
		}
	}
}
