package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import data.scripts.battlesnext.util.annotation.PublicData;

import java.util.ArrayList;

/**
 * Port of engine class com.fs.starfarer.combat.ai.FighterPullbackModule.
 * Carrier-side policy deciding when fighter wings engage, support an ally, or
 * are recalled. Fighter AIs are not ported, so attack-run detection is approximated
 * through fighter ship targets and AI flags.
 */
public class BattleNext_FighterPullbackModule {
	private ShipAPI ship;
	private BattleNext_ThreatEvalAI threatEvalAI;
	private IntervalUtil intervalTracker = new IntervalUtil(0.5F, 1.0F);
	private final BattleNext_FlockingAI flockingAI;
	// intentionally kept dead: vanilla field never read
	private float unusedRandomDelay = (float) Math.random() * 6.0F + 4.0F;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private ShipwideAIFlags aiFlags;
	private BattleNext_FighterPullbackMode mode = BattleNext_FighterPullbackMode.NEVER;
	private boolean hasEnoughSupportWings = false;
	// intentionally kept dead: vanilla computes and then immediately overwrites this flag
	private boolean unusedFlag = false;
	private float elapsedPullbackTime = 0.0F;
	private int wingMemberCountAtPullback = 0;

	public BattleNext_FighterPullbackModule(ShipAPI ship, BattleNext_ThreatEvalAI threatEvalAI, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI, ShipwideAIFlags aiFlags) {
		this.ship = ship;
		this.threatEvalAI = threatEvalAI;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.aiFlags = maneuverAPI.getAIFlags();
		this.updateMode();
	}

	private void updateMode() {
		float fighterWingCount = 0.0F;
		float interceptorSupportCount = 0.0F;
		float bomberSupportCount = 0.0F;
		float bomberCount = 0.0F;
		float usableWingCount = 0.0F;

		for (FighterLaunchBayAPI bay : this.ship.getLaunchBaysCopy()) {
			if (bay.getWing() != null && !(bay.getWing().getRange() <= 0.0F)) {
				FighterWingSpecAPI spec = bay.getWing().getSpec();
				if (spec.isSupport() || spec.isInterceptor()) {
					fighterWingCount++;
					interceptorSupportCount++;
				} else if (spec.isRegularFighter()) {
					fighterWingCount++;
				}

				if (spec.isBomber() || spec.isSupport()) {
					bomberSupportCount++;
				}

				if (spec.isBomber()) {
					bomberCount++;
				}

				usableWingCount++;
			}
		}

		if (usableWingCount < 1.0F) {
			usableWingCount = 1.0F;
		}

		float nonBomberCount = usableWingCount - bomberCount;
		this.unusedFlag = nonBomberCount == usableWingCount && (nonBomberCount <= 1.0F || this.ship.isCapital() && nonBomberCount <= 2.0F);
		// intentionally kept dead: vanilla overwrites the flag with false right away
		this.unusedFlag = false;
		this.hasEnoughSupportWings = bomberSupportCount / usableWingCount >= 0.5F;
		float fighterWingRatio = fighterWingCount / usableWingCount;
		float interceptorSupportRatio = interceptorSupportCount / usableWingCount;
		float interceptorDivisor = 1.0F;
		// the decompiled table labels 3..6 map to FRIGATE..CAPITAL_SHIP in ascending order
		// (label = engine HullSize ordinal + 1; the mod API enum has identical ordinals)
		switch (this.ship.getHullSize()) {
			case FRIGATE:
				interceptorDivisor = 1.0F;
				break;
			case DESTROYER:
				interceptorDivisor = 2.0F;
				break;
			case CRUISER:
				interceptorDivisor = 3.0F;
				break;
			case CAPITAL_SHIP:
				interceptorDivisor = 6.0F;
		}

		float adjustedInterceptorRatio = interceptorSupportCount / interceptorDivisor;
		boolean fewInterceptors = adjustedInterceptorRatio <= 0.34F;
		String personality = BattleNext_ShipAIHelper.getPersonality(this.ship);
		boolean reckless = "reckless".equals(personality);
		boolean aggressive = "aggressive".equals(personality);
		boolean steady = "steady".equals(personality);
		boolean cautious = "cautious".equals(personality);
		boolean timid = "timid".equals(personality);
		if (fighterWingRatio < 0.66F || this.ship.isStationModule()) {
			this.mode = BattleNext_FighterPullbackMode.NEVER;
		} else if (!(fighterWingRatio < 0.75F) && !(interceptorSupportRatio < 0.5F)) {
			if (!aggressive && !reckless && !fewInterceptors) {
				this.mode = BattleNext_FighterPullbackMode.PRIMARY;
			} else {
				this.mode = BattleNext_FighterPullbackMode.EMERGENCY;
			}
		} else if (!aggressive && !reckless) {
			this.mode = BattleNext_FighterPullbackMode.EMERGENCY;
		} else {
			this.mode = BattleNext_FighterPullbackMode.NEVER;
		}

		if (this.mode == BattleNext_FighterPullbackMode.PRIMARY) {
			this.mode = BattleNext_FighterPullbackMode.EMERGENCY;
		}
	}

	public void advance(float amount, ShipAPI currentTarget) {
		boolean pullBackActive = this.ship.isPullBackFighters();
		if (pullBackActive) {
			this.elapsedPullbackTime += amount;
		} else {
			this.elapsedPullbackTime = 0.0F;
		}

		this.intervalTracker.advance(amount);
		if (this.intervalTracker.intervalElapsed()) {
			float bayCount = this.ship.getLaunchBaysCopy().size();
			if (!(bayCount <= 0.0F)) {
				boolean allWingsAlwaysEscort = true;

				for (FighterLaunchBayAPI bay : this.ship.getLaunchBaysCopy()) {
					FighterWingAPI wing = bay.getWing();
					if (wing != null) {
						allWingsAlwaysEscort &= BattleNext_ShipAIHelper.isAlwaysEscortWing(wing.getSpec()) || bay.getWing().getRange() <= 0.0F;
					}
				}

				if (!allWingsAlwaysEscort) {
					if (this.ship.getForceCarrierPullBackTime() > 0.0F && this.ship.getForceCarrierTargetTime() <= 0.0F) {
						if (!pullBackActive) {
							this.pullBackFighters();
						}
					} else if (this.ship.getForceCarrierTargetTime() > 0.0F
						&& this.ship.getForceCarrierTarget() != null
						&& this.ship.getForceCarrierTarget().isAlive()) {
						this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 3.0F, this.ship.getForceCarrierTarget());
						if (pullBackActive) {
							this.pullBackFighters();
						}
					} else if (this.aiFlags.hasFlag(AIFlags.DO_NOT_USE_FLUX)) {
						if (!pullBackActive) {
							this.pullBackFighters();
						}
					} else {
						this.updateMode();
						float attackWingMembers = 0.0F;
						float totalWingMembers = 0.0F;
						float wingCapacity = 0.0F;

						for (FighterLaunchBayAPI bay : this.ship.getLaunchBaysCopy()) {
							FighterWingAPI wing = bay.getWing();
							if (wing != null && !BattleNext_ShipAIHelper.isAlwaysEscortWing(wing.getSpec())) {
								float wingWeight = 1.0F;
								if (wing.getSpec().isBomber()) {
									wingWeight *= 2.0F;
								}

								if (wing.getSpec().isInterceptor()) {
									wingWeight *= 0.5F;
								}

								wingCapacity += wing.getSpec().getNumFighters() * wingWeight;
								totalWingMembers += wing.getWingMembers().size() * wingWeight;
								if (!wing.getSpec().isSupport()) {
									attackWingMembers += wing.getWingMembers().size() * wingWeight;
								}
							}
						}

						if (wingCapacity < 1.0F) {
							wingCapacity = 1.0F;
						}

						float recoveryFraction = totalWingMembers / wingCapacity;
						float attackFraction = attackWingMembers / wingCapacity;
						float replacementProgress = 0.0F;
						float regrownMembers = 0.0F;
						if (pullBackActive) {
							replacementProgress = this.elapsedPullbackTime / getAverageReplacementTime(this.ship);
							regrownMembers = getTotalWingMembers(this.ship) - this.wingMemberCountAtPullback;
							if (this.elapsedPullbackTime > 10.0F && regrownMembers < replacementProgress * 0.25F && replacementProgress >= 1.25F && attackFraction <= 0.67F) {
								this.pullBackFighters();
								return;
							}
						}

						if (pullBackActive && this.ship.getFluxTracker().isOverloadedOrVenting() && this.threatEvalAI.isThreatLow()) {
							if (this.threatEvalAI.getMaxThreatData() != null && this.threatEvalAI.getMaxThreatData().getShip() != null) {
								this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 3.0F, this.threatEvalAI.getMaxThreatData().getShip());
							} else {
								this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 3.0F, this.ship);
							}

							this.pullBackFighters();
						} else {
							boolean lowHull = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
							boolean highFlux = this.ship.getFluxLevel() > 0.9F - attackFraction * 0.5F && this.ship.getFluxLevel() > 0.9F;
							boolean emergency = (lowHull || highFlux) && this.threatEvalAI.getTotalThreat() > 0.0F;
							emergency |= this.maneuverAPI.getEval().hasSolution()
								&& (this.ship.getCurrentCR() <= 0.0F || this.ship.getShield() == null || this.ship.getFluxLevel() > 0.9F);
							if (pullBackActive && emergency) {
								if (this.threatEvalAI.getMaxThreatData() != null && this.threatEvalAI.getMaxThreatData().getShip() != null) {
									this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 3.0F, this.threatEvalAI.getMaxThreatData().getShip());
								} else {
									this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 3.0F, this.ship);
								}

								this.pullBackFighters();
							} else if (!emergency) {
								float sharedReplacementRate = this.ship.getSharedFighterReplacementRate();
								if (!pullBackActive || !(sharedReplacementRate < 0.7F) || !(this.elapsedPullbackTime < 10.0F)) {
									boolean assignedTargetFound = false;
									// engine.getTaskManager(owner, ally) maps to fleetManager(owner).getTaskManager(ally)
									CombatTaskManagerAPI taskManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner()).getTaskManager(this.ship.isAlly());
									if (taskManager != null) {
										AssignmentInfo assignment = taskManager.getAssignmentFor(this.ship);
										if (assignment != null
											&& (assignment.getType() == CombatAssignmentType.INTERCEPT || assignment.getType() == CombatAssignmentType.STRIKE)
											&& assignment.getTarget() instanceof DeployedFleetMemberAPI) {
											DeployedFleetMemberAPI assignmentTarget = (DeployedFleetMemberAPI) assignment.getTarget();
											// vanilla passed search-mode constant OO0O$oo.f_6a00a25e_2, ported as
											// ManeuverSidePreference.ANY (no side preference among clustered enemies)
											BattleNext_BestTargetEval bestTarget = BattleNext_ShipAIHelper.getBestManeuverTarget(this.ship, BattleNext_ShipAIHelper.ManeuverSidePreference.ANY, false, assignmentTarget.getShip());
											if (bestTarget != null && bestTarget.getTarget() != null) {
												float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, bestTarget.getTarget());
												float distanceToTarget = Misc.getDistance(bestTarget.getTarget().getLocation(), this.ship.getLocation())
													- bestTarget.getTarget().getCollisionRadius();
												if (distanceToTarget < wingRange) {
													this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 3.0F, bestTarget.getTarget());
													assignedTargetFound = true;
												}
											}
										}
									}

									if (this.mode != BattleNext_FighterPullbackMode.NEVER && !assignedTargetFound) {
										Pair<ShipAPI, Float> bestFighterTarget = this.findBestFighterTarget();
										ShipAPI supportTarget = bestFighterTarget.one;
										float supportTargetScore = bestFighterTarget.two;
										if (supportTarget == null) {
											supportTarget = BattleNext_ShipAIHelper.getEscortTarget(this.ship);
											if (supportTarget != null) {
												supportTargetScore = BattleNext_ShipAIHelper.getThreat(supportTarget);
											}
										}

										if (supportTarget != null) {
											float supportTargetThreat = BattleNext_ShipAIHelper.getThreat(supportTarget);
											boolean scoreOverThreat = supportTargetScore >= supportTargetThreat * 3.0F;
											if (scoreOverThreat || this.mode == BattleNext_FighterPullbackMode.PRIMARY) {
												this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 5.0F, supportTarget);
												attackFraction = recoveryFraction;
											}
										}
									}

									if (!pullBackActive && attackFraction <= 0.35F) {
										boolean attackRunInProgress = false;

										for (FighterLaunchBayAPI bay : this.ship.getLaunchBaysCopy()) {
											if (bay.getWing() != null && bay.getWing().getSpec().isBomber()) {
												ShipAPI leader = bay.getWing().getLeader();
												if (leader != null && isFighterInAttackRun(leader)) {
													// approximation: vanilla required the fighter AI's current maneuver
													// to be an AttackRunManeuver targeting a ship
													ShipAPI attackRunTarget = leader.getShipTarget();
													if (attackRunTarget != null) {
														attackRunInProgress = true;
													}
												}
											}
										}

										if (!attackRunInProgress) {
											this.pullBackFighters();
											this.aiFlags.unsetFlag(AIFlags.CARRIER_FIGHTER_TARGET);
											return;
										}
									}

									// intentionally kept dead: vanilla declares this local and never assigns it
									ShipAPI unusedCarrierTarget = null;
									boolean hasCarrierFighterTarget = this.aiFlags.hasFlag(AIFlags.CARRIER_FIGHTER_TARGET);
									if (hasCarrierFighterTarget) {
										ShipAPI heldTarget = (ShipAPI) this.aiFlags.getCustom(AIFlags.CARRIER_FIGHTER_TARGET);
										if (heldTarget != null) {
											float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, currentTarget);
											boolean targetOutOfRange = Misc.getDistance(this.ship.getLocation(), heldTarget.getLocation()) > wingRange;
											if (!this.isClearTarget(heldTarget, 60.0F)) {
												targetOutOfRange = true;
											}

											if (heldTarget.isHulk() || heldTarget.isExpired() || !heldTarget.isAlive() || targetOutOfRange) {
												hasCarrierFighterTarget = false;
												this.aiFlags.unsetFlag(AIFlags.CARRIER_FIGHTER_TARGET);
											}
										}
									}

									if (!hasCarrierFighterTarget) {
										ShipAPI newFighterTarget = this.getCarrierFighterTarget();
										if (newFighterTarget != null) {
											this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 20.0F, newFighterTarget);
											hasCarrierFighterTarget = true;
										}
									}

									boolean disengage = false;
									if (unusedCarrierTarget == null && !hasCarrierFighterTarget) {
										Pair<ShipAPI, ShipAPI> engagement = this.findFleetEngagementTarget();
										ShipAPI attacker = engagement.one;
										ShipAPI victim = engagement.two;
										if (attacker != null && victim != null) {
											disengage = true;
											if (attacker.getFluxLevel() > 0.5F) {
												disengage = false;
											}

											if (victim.getFluxLevel() > 0.5F) {
												disengage = false;
											}

											if (victim.isRetreating()) {
												disengage = false;
											}

											if (victim.controlsLocked()) {
												disengage = false;
											}

											if (attacker.getParentStation() == this.ship) {
												disengage = false;
											}

											float attackerVictimDistance = BattleNext_ShipAIHelper.getDistance(attacker.getLocation(), victim.getLocation());
											float carrierVictimDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), victim.getLocation());
											float carrierVictimRange = this.ship.getCollisionRadius() + victim.getCollisionRadius();
											float attackerVictimRange = attacker.getCollisionRadius() + victim.getCollisionRadius();
											float closestLeaderDistance = 1.0E8F;

											for (FighterLaunchBayAPI bay : this.ship.getLaunchBaysCopy()) {
												if (bay.getWing() != null) {
													float leaderDistance = BattleNext_ShipAIHelper.getDistance(bay.getWing().getLeader().getLocation(), victim.getLocation());
													if (leaderDistance < closestLeaderDistance) {
														closestLeaderDistance = leaderDistance;
													}
												}
											}

											float maxWeaponRange = 0.0F;

											for (WeaponAPI weapon : attacker.getUsableWeapons()) {
												if (weapon.getType() != WeaponType.MISSILE) {
													float range = weapon.getRange();
													if (range > maxWeaponRange) {
														maxWeaponRange = range;
													}
												}
											}

											float victimRecoveryTime = 0.0F;
											if (victim.getFluxTracker().isVenting()) {
												victimRecoveryTime = victim.getFluxTracker().getTimeToVent();
											} else if (victim.getFluxTracker().isOverloaded()) {
												victimRecoveryTime = victim.getFluxTracker().getOverloadTimeRemaining();
											}

											if (closestLeaderDistance > attackerVictimDistance + attacker.getCollisionRadius() * 1.0F || attackerVictimDistance < attackerVictimRange + maxWeaponRange * 0.9F || victimRecoveryTime > 3.0F) {
												disengage = false;
											}

											float carrierAngle = BattleNext_ShipAIHelper.getAngleFast(victim.getLocation(), this.ship.getLocation());
											float attackerAngle = BattleNext_ShipAIHelper.getAngleFast(victim.getLocation(), attacker.getLocation());
											float angleBetween = BattleNext_ShipAIHelper.getAngleDiff(carrierAngle, attackerAngle);
											if (angleBetween >= 120.0F) {
												disengage = false;
											}

											float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, currentTarget);
											boolean outOfWingRange = carrierVictimDistance > wingRange;
											if (outOfWingRange) {
												disengage = true;
											}

											if (this.unusedFlag && victim != null && !victim.isRetreating()) {
												disengage = true;
											}

											if (!disengage) {
												this.aiFlags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 20.0F, victim);
												hasCarrierFighterTarget = true;
											}

											if (disengage) {
												if (!pullBackActive) {
													this.pullBackFighters();
												}

												return;
											}
										}
									}

									if (!pullBackActive && (!this.aiFlags.hasFlag(AIFlags.CARRIER_FIGHTER_TARGET) || disengage)) {
										this.pullBackFighters();
									} else if (pullBackActive && recoveryFraction >= 0.9F && attackFraction > 0.0F && hasCarrierFighterTarget && !disengage) {
										ShipAPI heldTarget = (ShipAPI) this.aiFlags.getCustom(AIFlags.CARRIER_FIGHTER_TARGET);
										if (heldTarget != null && BattleNext_ShipAIHelper.getEngine().getFogOfWar(this.ship.getOwner()).isVisible(heldTarget)) {
											this.pullBackFighters();
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private Pair<ShipAPI, Float> findBestFighterTarget() {
		CombatFleetManagerAPI ownFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner());
		float searchRange = 3000.0F;
		ShipAPI best = null;
		float bestScore = 0.0F;

		for (DeployedFleetMemberAPI member : ownFleetManager.getDeployedCopyDFM()) {
			ShipAPI allied = member.getShip();
			// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
			if (allied == null) {
				continue;
			}
			if (allied != this.ship && allied.getParentStation() != this.ship && !allied.isFighter() && !BattleNext_ShipAIHelper.isCarrier(allied)) {
				float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, allied);
				float distance = Misc.getDistance(allied.getLocation(), this.ship.getLocation()) - allied.getCollisionRadius();
				if (!(distance > wingRange)) {
					float alliedThreat = BattleNext_ShipAIHelper.getThreat(allied);
					float pressure = this.computeThreatInArea(allied, searchRange);
					pressure += this.getEnemyFighterPressure(allied) * 3.0F;
					float timeToRecover = BattleNext_ShipAIHelper.getTimeToRecover(allied);
					if (timeToRecover >= 4.0F) {
						pressure *= 1.5F;
					}

					if (!(pressure <= alliedThreat) && pressure > bestScore) {
						best = allied;
						bestScore = pressure;
					}
				}
			}
		}

		return new Pair<>(best, bestScore);
	}

	private float computeThreatInArea(ShipAPI allied, float range) {
		float friendlyThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(allied, 0.0F, 360.0F, range, true, true);
		friendlyThreat += BattleNext_ShipAIHelper.getThreat(allied);
		float hostileThreat = BattleNext_ShipAIHelper.getTotalThreatInRange(allied, 0.0F, 360.0F, range, false);
		return hostileThreat - friendlyThreat;
	}

	private float getEnemyFighterPressure(ShipAPI allied) {
		// engine.getEnemyFleetManager(owner) returns the fleet manager on the opposite side
		CombatFleetManagerAPI enemyFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(allied.getOriginalOwner() == 0 ? 1 : 0);
		float pressure = 0.0F;

		for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
			ShipAPI enemy = member.getShip();
			// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
			if (enemy == null) {
				continue;
			}
			if (enemy.isFighter()) {
				float distance = Misc.getDistance(enemy.getLocation(), allied.getLocation());
				if (distance < 1000.0F + allied.getCollisionRadius()) {
					pressure += 0.5F;
				}
		} else if (enemy.getLaunchBaysCopy().size() > 0 && allied == BattleNext_ShipAIHelper.getCarrierTarget(enemy)) {
				pressure += enemy.getLaunchBaysCopy().size();
			}
		}

		return pressure;
	}

	private ShipAPI getCarrierFighterTarget() {
		for (FighterLaunchBayAPI bay : this.ship.getLaunchBaysCopy()) {
			if (bay.getWing() != null && !BattleNext_ShipAIHelper.isAlwaysEscortWing(bay.getWing().getSpec()) && !bay.getWing().getSpec().isSupport()) {
				ShipAPI leader = bay.getWing().getLeader();
				if (leader != null && isFighterInAttackRun(leader)) {
					ShipAPI attackRunTarget = getFighterAttackRunTarget(leader);
					if (attackRunTarget != null) {
						float distance = Misc.getDistance(leader.getLocation(), attackRunTarget.getLocation());
						distance -= attackRunTarget.getCollisionRadius();
						if (distance < 1000.0F) {
							return attackRunTarget;
						}
					}
				}
			}
		}

		boolean alliedShipsNearby = this.hasAlliedShipsNearby();
		Pair<ShipAPI, ShipAPI> engagement = this.findFleetEngagementTarget();
		ShipAPI engagementTarget = null;
		if (engagement != null) {
			engagementTarget = engagement.two;
		}

		// engine.getEnemyFleetManager(owner) returns the fleet manager on the opposite side
		CombatFleetManagerAPI enemyFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner() == 0 ? 1 : 0);
		ArrayList<ShipAPI> candidates = new ArrayList<>();
		ShipAPI fighterTarget = getFighterTarget(this.ship);
		HullSize biggestEngagedSize = null;

		for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
			ShipAPI enemy = member.getShip();
			// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
			if (enemy == null) {
				continue;
			}
			if (!enemy.isFighter() && enemy.isTargetable()) {
				float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, enemy);
				float distance = Misc.getDistance(enemy.getLocation(), this.ship.getLocation()) - enemy.getCollisionRadius();
				if (distance < wingRange + 2000.0F) {
					HullSize enemySize = enemy.getHullSize();
					if (biggestEngagedSize == null || biggestEngagedSize.ordinal() < enemySize.ordinal()) {
						biggestEngagedSize = enemySize;
					}
				}

				if ((!(distance > wingRange) || enemy == fighterTarget) && !(distance > wingRange + 2000.0F)) {
					float timeToRecover = BattleNext_ShipAIHelper.getTimeToRecover(enemy);
					if (timeToRecover > 4.0F) {
						candidates.add(enemy);
					} else if (!(enemy.getFluxLevel() > 0.75F) && !(enemy.getHullLevel() < 0.1F)) {
						if ((!alliedShipsNearby || engagementTarget == enemy) && enemy == fighterTarget) {
							if (this.unusedFlag) {
								boolean specialCase = false;
								if (enemy.getFluxLevel() > 0.75F && engagementTarget == enemy) {
									specialCase = true;
								}

								boolean behindCarrier = Misc.isInArc(this.ship.getFacing() + 180.0F, 90.0F, this.ship.getLocation(), enemy.getLocation());
								if (distance < wingRange * 0.5F && behindCarrier) {
									specialCase = true;
								}

								if (!specialCase) {
									continue;
								}
							}

							candidates.add(enemy);
						}

						float angleOffApproach = BattleNext_ShipAIHelper.getAngleDiff(enemy.getFacing(), BattleNext_ShipAIHelper.getAngleFast(enemy.getLocation(), this.ship.getLocation()));
						if (angleOffApproach > 120.0F) {
							candidates.add(enemy);
						}
					} else {
						candidates.add(enemy);
					}
				}
			}
		}

		boolean needBigTarget = this.hasEnoughSupportWings && biggestEngagedSize != null && biggestEngagedSize.ordinal() >= HullSize.DESTROYER.ordinal();
		int bestSize = 0;
		ShipAPI bestCandidate = null;

		for (ShipAPI candidate : candidates) {
			if (this.isClearTarget(candidate, 60.0F) && (!needBigTarget || !candidate.isFrigate() || candidate.isStationModule())) {
				int candidateSize = candidate.getHullSize().ordinal();
				if (candidateSize > bestSize) {
					bestSize = candidateSize;
					bestCandidate = candidate;
				}

				if (candidate == fighterTarget) {
					return candidate;
				}
			}
		}

		return bestCandidate;
	}

	private boolean isClearTarget(ShipAPI target, float arc) {
		// engine.getEnemyFleetManager(owner) returns the fleet manager on the opposite side
		CombatFleetManagerAPI enemyFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner() == 0 ? 1 : 0);
		float targetDistance = Misc.getDistance(target.getLocation(), this.ship.getLocation()) - target.getCollisionRadius();

		for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
			ShipAPI blocker = member.getShip();
			// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
			if (blocker == null) {
				continue;
			}
			if (!blocker.isFighter()
				&& (blocker.getHullSize().ordinal() >= HullSize.DESTROYER.ordinal() || blocker.getParentStation() != null)
				&& blocker != target
				&& blocker.isTargetable()) {
				float blockerDistance = Misc.getDistance(blocker.getLocation(), this.ship.getLocation()) - blocker.getCollisionRadius();
				if (!(blockerDistance > targetDistance) && BattleNext_ShipAIHelper.isInArc(this.ship.getLocation(), target.getLocation(), arc, this.ship.getLocation(), blocker.getLocation()) && blockerDistance < targetDistance) {
					return false;
				}
			}
		}

		return true;
	}

	private Pair<ShipAPI, ShipAPI> findFleetEngagementTarget() {
		CombatFleetManagerAPI ownFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner());
		int bestSize = 0;
		ShipAPI attacker = null;
		ShipAPI fighterTarget = null;
		boolean selfNonCombat = this.ship.isNonCombat(true);
		boolean alliedShipsNearby = this.hasAlliedShipsNearby();
		float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(this.ship, fighterTarget);

		for (DeployedFleetMemberAPI member : ownFleetManager.getDeployedCopyDFM()) {
			ShipAPI allied = member.getShip();
			// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
			if (allied == null) {
				continue;
			}
			if ((allied != this.ship || !selfNonCombat || !alliedShipsNearby)
				&& !member.isFighterWing()
				&& (allied.getHullSize() != HullSize.FRIGATE || this.ship.getHullSize().ordinal() < HullSize.DESTROYER.ordinal())) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), allied.getLocation());
				if (!(distance > 3000.0F) && !(distance > wingRange)) {
					ShipAPI alliedFighterTarget = getFighterTarget(allied);
					if (alliedFighterTarget != null && alliedFighterTarget.getHullSize().ordinal() > bestSize) {
						fighterTarget = alliedFighterTarget;
						bestSize = alliedFighterTarget.getHullSize().ordinal();
						attacker = allied;
					}
				}
			}
		}

		return new Pair<>(attacker, fighterTarget);
	}

	public static ShipAPI getFighterTarget(ShipAPI carrier) {
		if (carrier.getAI() instanceof ShipAIPlugin) {
			ShipAIPlugin carrierAI = (ShipAIPlugin) carrier.getAI();
			if (carrierAI.getAIFlags() != null && carrierAI.getAIFlags().hasFlag(AIFlags.MANEUVER_TARGET)) {
				return (ShipAPI) carrierAI.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
			}
		}

		if (carrier == BattleNext_ShipAIHelper.getEngine().getPlayerShip()) {
			BattleNext_BestTargetEval bestTarget = BattleNext_ShipAIHelper.getBestManeuverTarget(carrier);
			if (bestTarget != null && bestTarget.getTarget() != null) {
				return bestTarget.getTarget();
			}

			ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemy(carrier);
			if (nearestEnemy != null) {
				float distance = Misc.getDistance(nearestEnemy.getLocation(), carrier.getLocation());
				float wingRange = BattleNext_ShipAIHelper.getCarrierWingRange(carrier, nearestEnemy);
				if (distance <= wingRange) {
					return nearestEnemy;
				}
			}
		}

		ShipAPI target = carrier.getShipTarget();
		return target != null && target.getOwner() != carrier.getOwner() ? target : null;
	}

	private boolean hasAlliedShipsNearby() {
		CombatFleetManagerAPI ownFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner());

		for (DeployedFleetMemberAPI member : ownFleetManager.getDeployedCopyDFM()) {
			ShipAPI allied = member.getShip();
			// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
			if (allied == null) {
				continue;
			}
			if (allied != this.ship
				&& !member.isFighterWing()
				&& (allied.getHullSize() != HullSize.FRIGATE || this.ship.getHullSize().ordinal() <= HullSize.DESTROYER.ordinal())) {
				float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), allied.getLocation());
				if (distance < 3000.0F) {
					return true;
				}
			}
		}

		return false;
	}

	private void pullBackFighters() {
		if (!this.ship.isPullBackFighters()) {
			this.wingMemberCountAtPullback = getTotalWingMembers(this.ship);
			this.aiFlags.unsetFlag(AIFlags.CARRIER_FIGHTER_TARGET);
		}

		this.ship.giveCommand(ShipCommand.PULL_BACK_FIGHTERS, null, -1);
	}

	public static int getTotalWingMembers(ShipAPI carrier) {
		int total = 0;

		for (FighterLaunchBayAPI bay : carrier.getLaunchBaysCopy()) {
			if (bay.getWing() != null) {
				total += bay.getWing().getWingMembers().size();
			}
		}

		return total;
	}

	public static float getAverageReplacementTime(ShipAPI carrier) {
		// approximation: FighterLaunchBayAPI does not expose the replacement time; the spec
		// default of 10 seconds per bay makes the average a constant
		return 10.0F;
	}

	// approximation: FighterAI.getCurrentManeuver() is hidden by ShipAIPlugin.
	// A ship target can persist outside AttackRunManeuver; IN_ATTACK_RUN marks
	// only part of that maneuver and can be mirrored by a wing follower.
	private static boolean isFighterInAttackRun(ShipAPI fighter) {
		if (fighter.getShipTarget() != null) {
			return true;
		}

		ShipwideAIFlags flags = fighter.getAIFlags();
		return flags != null && flags.hasFlag(AIFlags.IN_ATTACK_RUN);
	}

	// approximation: vanilla reads AttackRunManeuver.getTarget(); ShipAPI exposes
	// only the fighter's ship target, which can differ from the maneuver target.
	private static ShipAPI getFighterAttackRunTarget(ShipAPI fighter) {
		if (!isFighterInAttackRun(fighter)) {
			return null;
		}

		return fighter.getShipTarget();
	}

	// replacement for com.fs.util.container.Pair
	@PublicData
	private static class Pair<One, Two> {
		public One one;
		public Two two;

		Pair(One one, Two two) {
			this.one = one;
			this.two = two;
		}
	}
}
