package data.scripts.ai.vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of engine class com.fs.starfarer.combat.ai.VentModule.
 * Decides when the ship is safe enough to vent flux.
 */
public class BattleNext_VentModule {
	private ShipAPI ship;
	private BattleNext_ThreatEvalAI threatEvalAI;
	private IntervalUtil ventIntervalTracker = new IntervalUtil(0.5F, 1.0F);
	private final BattleNext_FlockingAI flockingAI;
	private float timeAboveFluxThreshold = 0.0F;
	private float ventCooldown = (float) Math.random() * 6.0F + 4.0F;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private BattleNext_FiringSolutionEval firingSolutionEval;

	public BattleNext_VentModule(ShipAPI ship, BattleNext_ThreatEvalAI threatEvalAI, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI, ShipwideAIFlags aiFlags) {
		this.ship = ship;
		this.threatEvalAI = threatEvalAI;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship, aiFlags);
		this.firingSolutionEval.setAssumeFiring(true);
		// intentionally kept: the vanilla setter named setUseWeaponPositions actually toggles missile consideration
		this.firingSolutionEval.setUseWeaponPositions(true);
	}

	public void advance(float amount, ShipAPI target) {
		if (!this.ship.getFluxTracker().isOverloadedOrVenting() && !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.DO_NOT_VENT) && !BattleNext_ShipAIHelper.isTravelDriveOn(this.ship)) {
			if (this.ship.getFluxLevel() > 0.7F) {
				this.timeAboveFluxThreshold += amount;
			} else {
				this.ventCooldown = (float) Math.random() * 6.0F + 4.0F;
				this.timeAboveFluxThreshold = 0.0F;
			}

			this.ventIntervalTracker.advance(amount);
			if (this.ventIntervalTracker.intervalElapsed()) {
				boolean nonCombat = this.ship.isNonCombat(true);
				boolean hasPointDefense = false;
				boolean hasWeaponReadyToFire = false;
				float ventTime = this.ship.getFluxTracker().getTimeToVent();
				float fluxVentTime = this.ship.getFluxTracker().getCurrFlux() / Math.max(1.0F, BattleNext_ShipAIHelper.getDissipationRate(this.ship));
				if (ventTime > fluxVentTime) {
					ventTime = fluxVentTime;
				}

				float fluxForVenting = BattleNext_ShipAIHelper.getFluxAvailable(this.ship) + ventTime * BattleNext_ShipAIHelper.getDissipationRate(this.ship);
				boolean firingSoon = false;
				boolean firingInBurst = false;
				float weaponFluxPerSecond = 0.0F;

				for (WeaponAPI weapon : this.ship.getUsableWeapons()) {
					if (weapon.hasAIHint(AIHints.PD)) {
						hasPointDefense = true;
					}

					if (weapon.getCooldownRemaining() <= ventTime && weapon.getFluxCostToFire() < fluxForVenting) {
						hasWeaponReadyToFire = true;
					}

					firingSoon |= weapon.isFiring() && weapon.getCooldownRemaining() < 2.0F;
					firingInBurst |= weapon.isFiring() && weapon.isInBurst();
					weaponFluxPerSecond += weapon.getDerivedStats().getFluxPerSecond();
				}

				boolean weaponFluxUnderDissipation = weaponFluxPerSecond < BattleNext_ShipAIHelper.getDissipationRate(this.ship) * (1.5F - this.ship.getFluxLevel());
				boolean firingSolutionThreat = this.flockingAI.getAvoidanceVector() != null
					&& this.maneuverAPI.getShieldAI() != null
					&& this.maneuverAPI.getShieldAI().hasFiringSolution();
				this.firingSolutionEval.setEvaluationWindow(ventTime);
				this.firingSolutionEval.resetSolution();
				float evalShieldDamage = this.firingSolutionEval.getShieldDamage();
				if (this.firingSolutionEval.hasSolution() && this.ship.getShield() != null) {
					firingSolutionThreat = true;
					return;
				}

				if (this.ship.getShield() == null && this.ship.getPhaseCloak() == null && this.ship.getHullSpec().getDefenseType() == ShieldType.NONE) {
					firingSolutionThreat = false;
				}

				boolean ventTimeUnder3 = this.ship.getFluxTracker().getTimeToVent() < 3.0F;
				float shieldRaiseTime = 2.0F;
				if (this.ship.getShield() != null) {
					float unfoldTime = this.ship.getShield().getUnfoldTime();
					if (this.ship.getShield().getType() == ShieldType.OMNI) {
						unfoldTime *= 0.5F;
					}

					shieldRaiseTime += unfoldTime;
				}

				int incomingMissiles = BattleNext_ShipAIHelper.getNumberOfIncomingMissiles(this.ship, ventTime + shieldRaiseTime);
				if (incomingMissiles > 0) {
					return;
				}

				boolean avoiding = this.flockingAI.getAvoidanceVector() != null;
				boolean lowHull = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
				ArrayList<BattleNext_ThreatData> threatList = new ArrayList<>(this.threatEvalAI.getAllThreatData());
				boolean targetFacingUs = true;
				boolean noPunishingThreat = true;
				if (target != null || this.threatEvalAI.getMaxThreatData() != null) {
					if (target == null) {
						target = this.threatEvalAI.getMaxThreatData().getShip();
					}

					Vector2f toTarget = Vector2f.sub(this.ship.getLocation(), target.getLocation(), new Vector2f());
					BattleNext_ShipAIHelper.normalise(toTarget);
					Vector2f targetHeading = BattleNext_ShipAIHelper.getUnitVectorAtAngle(target.getFacing());
					Vector2f targetVelocity = BattleNext_ShipAIHelper.normalise(new Vector2f(target.getVelocity()));
					float headingDot = Vector2f.dot(toTarget, targetHeading);
					// intentionally kept dead: vanilla only stores the away-facing test into an unread local
					boolean facingAway = false;
					if (headingDot < -0.25F) {
						facingAway = true;
					}

					targetFacingUs = headingDot > 0.71F;

					for (BattleNext_ThreatData data : threatList) {
						if (data.getShip() == target) {
							threatList.remove(data);
							break;
						}
					}

					BattleNext_ThreatData recomputedThreat = this.maneuverAPI.getThreatEvaluator().computeThreatData(target);
					threatList.add(recomputedThreat);
					float targetDisabledTime = 0.0F;
					if (target.getFluxTracker().isVenting()) {
						targetDisabledTime = target.getFluxTracker().getTimeToVent();
					} else if (target.getFluxTracker().isOverloaded()) {
						targetDisabledTime = target.getFluxTracker().getOverloadTimeRemaining();
					}

					float targetDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), target.getLocation());
					float targetRadiusSum = this.ship.getCollisionRadius() + target.getCollisionRadius();
					if (targetDistance < recomputedThreat.getThreatRange() + targetRadiusSum + 100.0F && targetDisabledTime < ventTime) {
						noPunishingThreat = false;
					}
				}

				float timeUntilDanger = 1000.0F;
				boolean dangerTracked = false;
				boolean dangerCloseNow = false;
				boolean scanAllShips = true;
				boolean noReachableThreat = false;
				if (scanAllShips) {
					CombatEngineAPI engine = BattleNext_ShipAIHelper.getEngine();
					List<ShipAPI> ships = engine.getShips();
					float nearestDangerTime = 1000.0F;

					for (ShipAPI other : ships) {
						if ((!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
							&& !other.isHulk()
							&& !other.isShuttlePod()
							&& other.getOwner() != this.ship.getOwner()
							&& this.threatEvalAI.getThreatData(other) == null) {
							float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation());
							float threatRange = 1200 + 200 * (other.getHullSize().ordinal() - 1);
							float disabledTime = 0.0F;
							if (other.getFluxTracker().isVenting()) {
								disabledTime = other.getFluxTracker().getTimeToVent();
							} else if (other.getFluxTracker().isOverloaded()) {
								disabledTime = other.getFluxTracker().getOverloadTimeRemaining();
							}

							ShipSystemAPI otherSystem = other.getSystem();
							boolean systemReady = otherSystem != null
								&& otherSystem.getCooldownRemaining() < ventTime
								&& (!systemUsesAmmo(otherSystem) || otherSystem.getAmmo() > 0)
								&& (!other.isNonCombat(false) || nonCombat)
								&& disabledTime < 3.0F;
							if (systemReady) {
								// approximation: engine spec AI hints getBurstMovementPotential() falls back to 0
								distance -= 0.0F;
							}

							if (!(distance > threatRange)) {
								float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, other);
								float otherCruiseSpeed = getCruiseSpeed(other);
								if (!other.isEngineBoostActive()) {
									otherCruiseSpeed -= other.getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
								}

								if (other.getPhaseCloak() != null && other.getHardFluxLevel() < 0.2F) {
									otherCruiseSpeed *= 2.0F;
								}

								if (systemReady) {
									// approximation: engine spec AI hints getActiveSpeedIncrease() falls back to 0
									otherCruiseSpeed += 0.0F;
								}

								float ownCruiseSpeed = getCruiseSpeed(this.ship);
								if (!this.ship.isEngineBoostActive()) {
									ownCruiseSpeed -= this.ship.getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
								}

								float closingRate = Math.max(closingSpeed, otherCruiseSpeed - ownCruiseSpeed);
								float innerRange = threatRange * 0.67F;
								float distancePastInner = distance - innerRange;
								if (distancePastInner < 0.0F) {
									dangerCloseNow = true;
									break;
								}

								if (closingRate > 0.0F) {
									float timeToThreat = distancePastInner / closingRate + disabledTime;
									if (timeToThreat < ventTime) {
										dangerCloseNow = true;
										if (timeToThreat < nearestDangerTime) {
											nearestDangerTime = timeToThreat;
										}
									}
								}
							}
						}
					}

					noReachableThreat = !dangerCloseNow;

					for (BattleNext_ThreatData data : threatList) {
						// approximation: engine ship.getArmorEvaluator().getArmorInArc(facing, arc)
						float armor = this.ship.getAverageArmorInSlice(BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), data.getShip().getLocation()), 15.0F);
						float armorMultiplier = computeDamageMultiplier(data.getMaxDamagePerShot(), armor);
						float effectiveDamage = data.getMaxDamagePerShot() * armorMultiplier;
						float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), data.getShip().getLocation());
						float disabledTime = 0.0F;
						if (data.getShip().getFluxTracker().isVenting()) {
							disabledTime = data.getShip().getFluxTracker().getTimeToVent();
						} else if (data.getShip().getFluxTracker().isOverloaded()) {
							disabledTime = data.getShip().getFluxTracker().getOverloadTimeRemaining();
						}

						ShipSystemAPI threatSystem = data.getShip().getSystem();
						boolean systemReady = threatSystem != null
							&& threatSystem.getCooldownRemaining() < ventTime
							&& (!systemUsesAmmo(threatSystem) || threatSystem.getAmmo() > 0)
							&& (!data.getShip().isNonCombat(false) || nonCombat)
							&& disabledTime < 3.0F;
						if (systemReady) {
							// approximation: engine spec AI hints getBurstMovementPotential() falls back to 0
							distance -= 0.0F;
						}

						float radiusSum = this.ship.getCollisionRadius() + data.getShip().getCollisionRadius();
						// approximation: engine spec AI hints are not exposed; getThreatDamage() falls back to 0
						// and getThreatSpeed() to 10000, so this branch can never run
						float threatDamage = 0.0F;
						float threatSpeed = 10000.0F;
						if (systemReady && threatDamage > 0.0F) {
							effectiveDamage = Math.max(threatDamage, effectiveDamage);
							if (threatSpeed < 10000.0F) {
								float timeToHit = (distance - radiusSum) / threatSpeed;
								if (timeToHit < 0.0F) {
									timeToHit = 0.0F;
								}

								data.setMinTimeToHit(Math.min(data.getMinTimeToHit(), timeToHit));
							}
						}

						boolean biggerHull = data.getShip().getHullSize().ordinal() >= this.ship.getHullSize().ordinal() && !data.getShip().isNonCombat(false);
						float damageFraction = 0.1F;
						if (this.ship.isFrigate()) {
							damageFraction = 0.05F;
						}

						if (!(effectiveDamage < armor * damageFraction)
							&& (biggerHull || !(evalShieldDamage * armorMultiplier * Math.max(ventTime * 10.0F, 3.0F) < this.ship.getHitpoints()) || !(effectiveDamage < armor * 0.25F))) {
							noReachableThreat = false;
							float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, data.getShip());
							float threatCruiseSpeed = getCruiseSpeed(data.getShip());
							if (!data.getShip().isEngineBoostActive()) {
								threatCruiseSpeed -= data.getShip().getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
							}

							if (data.getShip().getPhaseCloak() != null && data.getShip().getHardFluxLevel() < 0.2F) {
								threatCruiseSpeed *= 2.0F;
							}

							if (systemReady) {
								// approximation: engine spec AI hints getActiveSpeedIncrease() falls back to 0
								threatCruiseSpeed += 0.0F;
							}

							float ownCruiseSpeed = getCruiseSpeed(this.ship);
							if (!this.ship.isEngineBoostActive()) {
								ownCruiseSpeed -= this.ship.getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
							}

							float closingRate = Math.max(closingSpeed, threatCruiseSpeed - ownCruiseSpeed);
							float distancePastRange = distance - (data.getThreatRange() + radiusSum - 75.0F);
							float timeToThreat = 900.0F;
							if (closingRate > 0.0F) {
								timeToThreat = distancePastRange / closingRate + disabledTime;
								if (timeToThreat < 0.0F) {
									timeToThreat = 0.0F;
								}
							}

							if (data.getMinTimeToHit() - 1.0F < timeToThreat) {
								timeToThreat = data.getMinTimeToHit() - 1.0F;
							}

							if (timeToThreat < timeUntilDanger && distancePastRange > 0.0F) {
								timeUntilDanger = timeToThreat;
								dangerTracked = true;
							} else {
								timeUntilDanger = 0.0F;
								dangerTracked = true;
							}
						}
					}

					if (noReachableThreat) {
						timeUntilDanger = ventTime + 1.0F;
						dangerTracked = true;
					}
				}

				boolean hasShield = this.ship.getShield() != null;
				if (this.ship.getShield() != null && this.ship.getShield().getType() == ShieldType.FRONT) {
					// intentionally kept dead: vanilla only declares a local here
				} else {
					// intentionally kept dead
				}

				if (!hasPointDefense
					&& !hasWeaponReadyToFire
					&& !firingInBurst
					&& (!hasShield || !dangerTracked || timeUntilDanger > ventTime)
					&& (!hasShield || !avoiding)
					&& !firingSolutionThreat
					&& !weaponFluxUnderDissipation
					&& this.ship.getFluxLevel() > 0.1F
					&& !nonCombat
					&& (
						this.ship.getSystem() == null
							|| this.ship.getSystem().getCooldownRemaining() >= ventTime && !this.ship.getSystem().isActive()
							|| this.ship.getSystem().getSpecAPI().getThreatAmount() <= 0.0F
					)) {
					if (noReachableThreat && ventTime < 3.0F) {
						this.ship.getAIFlags().setFlag(AIFlags.DO_NOT_BACK_OFF, ventTime);
						this.ship.getAIFlags().setFlag(AIFlags.DO_NOT_BACK_OFF_EVEN_WHILE_VENTING, ventTime);
					}

					this.orderVent();
					return;
				}

				boolean targetFarAway = false;
				if (target != null) {
					float targetDistance = target == null ? Float.MAX_VALUE : BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), target.getLocation());
					float targetRadius = this.ship.getCollisionRadius() + (target == null ? 0.0F : target.getCollisionRadius()) * 0.75F;
					targetFarAway = targetDistance - targetRadius > this.maneuverAPI.getAttackAI().getNonMissileMaxRange() * 1.2F;
				}

				boolean targetLowHull = target != null && target.getHitpoints() < 150.0F * target.getHullSize().ordinal();
				boolean targetVulnerable = target != null && (target.getFluxTracker().isOverloadedOrVenting() || target.getFluxLevel() > 0.8F && targetLowHull);
				boolean weHaveMoreFlux = target != null && BattleNext_ShipAIHelper.getFluxAvailable(this.ship) > BattleNext_ShipAIHelper.getFluxAvailable(target);
				boolean targetHighFlux = target != null && target.getFluxLevel() > 0.8F;
				targetVulnerable &= hasWeaponReadyToFire;
				if (targetFarAway) {
					targetVulnerable = false;
				}

				boolean systemActive = this.ship.getSystem() != null && this.ship.getSystem().isActive();
				if (this.maneuverAPI.getAIFlags().hasFlag(AIFlags.OK_TO_CANCEL_SYSTEM_USE_TO_VENT)) {
					systemActive = false;
				}

				if (!scanAllShips) {
					// intentionally kept dead: scanAllShips is a vanilla constant true
					timeUntilDanger = this.firingSolutionEval.getMinTimeToFire();
					dangerTracked = timeUntilDanger < 1000.0F;
					dangerTracked = true;
					dangerCloseNow = false;
				}

				if (dangerTracked && timeUntilDanger > 2.0F && !lowHull && !dangerCloseNow && !firingSolutionThreat) {
					this.maneuverAPI.getAIFlags().setFlag(AIFlags.SAFE_FROM_DANGER_TIME, 1.0F, timeUntilDanger - 1.0F);
				} else {
					this.maneuverAPI.getAIFlags().unsetFlag(AIFlags.SAFE_FROM_DANGER_TIME);
				}

				if (dangerTracked && !noReachableThreat && timeUntilDanger > ventTime && !lowHull && !dangerCloseNow && !firingSolutionThreat) {
					this.maneuverAPI.getAIFlags().setFlag(AIFlags.SAFE_VENT, ventTime);
				} else {
					this.maneuverAPI.getAIFlags().unsetFlag(AIFlags.SAFE_VENT);
				}

				if (dangerTracked
					&& timeUntilDanger > ventTime
					&& !lowHull
					&& !firingInBurst
					&& (!this.ship.isFrigate() || timeUntilDanger > 5.0F)
					&& (!targetVulnerable || ventTimeUnder3 && targetFarAway)
					&& !dangerCloseNow
					&& !systemActive
					&& !firingSolutionThreat
					&& (!firingSoon || this.timeAboveFluxThreshold > this.ventCooldown)
					&& this.ship.getFluxLevel() > 0.1F) {
					this.orderVent();
					if (noReachableThreat && ventTime < 3.0F) {
						this.ship.getAIFlags().setFlag(AIFlags.DO_NOT_BACK_OFF, ventTime);
						this.ship.getAIFlags().setFlag(AIFlags.DO_NOT_BACK_OFF_EVEN_WHILE_VENTING, ventTime);
					}

					return;
				}

				if (ventTimeUnder3
					&& this.threatEvalAI.isAllThreatsValid()
					&& !targetVulnerable
					&& !weHaveMoreFlux
					&& !targetHighFlux
					&& this.ship.getSinceLastDamageTaken() > 0.5F
					&& !systemActive
					&& !firingInBurst
					&& !this.maneuverAPI.getAIFlags().hasFlag(AIFlags.HAS_INCOMING_DAMAGE)
					&& this.ship.getFluxTracker().getFluxLevel() > 0.2F
					&& !avoiding
					&& !firingSolutionThreat
					&& (targetFacingUs || noPunishingThreat)) {
					this.maneuverAPI.getAIFlags().setFlag(AIFlags.SAFE_VENT, ventTime);
					this.orderVent();
					return;
				}

				if (this.timeAboveFluxThreshold > this.ventCooldown
					&& !lowHull
					&& !this.threatEvalAI.isThreatHigh()
					&& this.ship.getMinFluxLevel() > 0.5F
					&& !firingSolutionThreat
					&& !avoiding) {
					this.maneuverAPI.getAIFlags().setFlag(AIFlags.BACK_OFF);
					this.maneuverAPI.getAIFlags().setFlag(AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS);
					return;
				}

				// intentionally kept dead: the flag below is a vanilla constant false and the
				// fleet manager lookup that follows it stores an unread local
				boolean ventAnyway = false;
				CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner());
				if (fleetManager != null) {
					DeployedFleetMemberAPI unusedMember = fleetManager.getDeployedFleetMember(this.ship);
				}

				if (ventAnyway || firingSolutionThreat) {
					return;
				}

				float totalWeaponScore = 0.0F;
				float disabledWeaponScore = 0.0F;

				for (WeaponAPI moduleWeapon : this.ship.getAllWeapons()) {
					// engine vanilla iterated ship modules, which contain every fitted weapon; the API
					// exposes the same set through getAllWeapons()
					float sizeScore = 0.0F;
					switch (getWeaponSizeScores()[moduleWeapon.getSize().ordinal()]) {
						case 1:
							sizeScore = 1.0F;
							break;
						case 2:
							sizeScore = 3.0F;
							break;
						case 3:
							sizeScore = 7.0F;
							break;
						default:
							break;
					}

					totalWeaponScore += sizeScore;
					if (moduleWeapon.isDisabled()) {
						disabledWeaponScore += sizeScore;
					}
				}

				float disabledFraction = 0.0F;
				if (totalWeaponScore > 0.0F) {
					disabledFraction = disabledWeaponScore / totalWeaponScore;
				}

				float ventFluxFraction = 0.95F;
				if (this.threatEvalAI.getTotalThreat() == 0.0F) {
					ventFluxFraction *= 0.9F;
				}

				if (disabledFraction > 0.25F) {
					ventFluxFraction *= 0.75F;
				}

				if (lowHull && this.threatEvalAI.getTotalThreat() != 0.0F) {
					ventFluxFraction = 0.99F;
				}

				if (this.ship.getFluxLevel() > ventFluxFraction && this.ship.getMinFluxLevel() > ventFluxFraction - 0.1F) {
					// intentionally kept dead: vanilla only declares a local here
				} else {
					// intentionally kept dead
				}

				// intentionally kept dead: this flag is a vanilla constant false
				boolean empDisabled = false;
				if (empDisabled && !isEMPed(this.ship) && !lowHull && !hasPointDefense && !firingSolutionThreat && !targetVulnerable) {
					this.orderVent();
				} else if (target != null
					&& target.getFluxTracker().isVenting()
					&& this.ship.getFluxLevel() > 0.65F
					&& !hasPointDefense
					&& !firingSolutionThreat
					&& !isEMPed(this.ship)
					&& !this.threatEvalAI.isThreatHigh()
					&& (this.ship.getMinFluxLevel() > 0.5F || this.ship.getFluxLevel() > 0.8F)
					&& !lowHull
					&& this.ship.getFluxTracker().getTimeToVent() < target.getFluxTracker().getTimeToVent() + 3.0F
					&& this.ship.getFluxTracker().getTimeToVent() > target.getFluxTracker().getTimeToVent() - 1.0F
					&& (float) Math.random() > 0.75F) {
					this.orderVent();
				}
			}
		}
	}

	private void orderVent() {
		this.ship.giveCommand(ShipCommand.VENT_FLUX, null, 0);
	}

	/**
	 * Port of engine Ship.isEMPed().
	 */
	private static boolean isEMPed(ShipAPI ship) {
		return ship.getFluxTracker().isOverloaded();
	}

	/**
	 * Port of engine ShipEngineController.getCruiseSpeed().
	 */
	private static float getCruiseSpeed(ShipAPI ship) {
		return BattleNext_ShipAIHelper.getCruiseSpeed(ship);
	}

	// Engine ShipSystem.usesAmmo() delegates to AmmoTracker.usesAmmo(), which
	// compares the current runtime ammo count against Integer.MAX_VALUE.
	static boolean systemUsesAmmo(ShipSystemAPI system) {
		return system.getAmmo() != Integer.MAX_VALUE;
	}

	/**
	 * Port of engine ArmorGrid.computeDamageMultiplier(float, float).
	 */
	private static float computeDamageMultiplier(float damage, float armorRating) {
		float fraction = armorRating / (armorRating + damage);
		float maxReduction;
		try {
			maxReduction = Global.getSettings().getFloat("maxArmorDamageReduction");
		} catch (RuntimeException missingKey) {
			// approximation: fall back to the vanilla settings value
			maxReduction = 0.85F;
		}

		if (fraction > maxReduction) {
			fraction = maxReduction;
		}

		return 1.0F - fraction;
	}

	/**
	 * Reconstruction of the engine getWeaponSizeScores() lookup lost in deobfuscation.
	 * The shared runtime enum yields SMALL=0, MEDIUM=1, LARGE=2 and every switch over the
	 * table labels 1/2/3 as reachable, so the table maps each ordinal to ordinal+1.
	 */
	private static int[] getWeaponSizeScores() {
		return new int[] {1, 2, 3};
	}
}
