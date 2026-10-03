package data.scripts.ai.vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.impl.combat.PhaseCloakStats;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Port of engine class com.fs.starfarer.combat.ai.BehaviorModule.
 * Global threat/shield/retreat posture adjustments run on a low-frequency timer.
 */
public class BattleNext_BehaviorModule {
	private ShipAPI ship;
	private BattleNext_ThreatEvalAI threatEvalAI;
	private BattleNext_FlockingAI flockingAI;
	private BattleNext_ManeuverAPI maneuverAPI;
	private IntervalUtil intervalTracker = new IntervalUtil(0.25F, 0.75F);
	private ShipwideAIFlags aiFlags;
	public static final String phaseCheeseEverDetectedKey = "phase_cheese_ever_detected";
	public static final String phaseCheeseRecentlyDetectedKey = "phase_cheese_recently_detected";
	public static final String phaseCheeseOngoingKey = "phase_cheese_ongoing";

	public BattleNext_BehaviorModule(ShipAPI ship, BattleNext_ThreatEvalAI threatEvalAI, BattleNext_FlockingAI flockingAI, BattleNext_ManeuverAPI maneuverAPI, ShipwideAIFlags aiFlags) {
		this.ship = ship;
		this.threatEvalAI = threatEvalAI;
		this.flockingAI = flockingAI;
		this.maneuverAPI = maneuverAPI;
		this.aiFlags = maneuverAPI.getAIFlags();
	}

	public void advance(float amount) {
		boolean shieldsForcedOn = false;
		boolean lowHull = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
		if (lowHull && this.threatEvalAI.getTotalThreat() > 0.0F) {
			this.aiFlags.unsetFlag(AIFlags.DO_NOT_USE_SHIELDS);
			shieldsForcedOn = true;
		}

		if (this.flockingAI.getAvoidanceVector() != null) {
			this.aiFlags.unsetFlag(AIFlags.DO_NOT_USE_SHIELDS);
			shieldsForcedOn = true;
		}

		if (this.maneuverAPI.getEval().hasSolution()) {
			this.aiFlags.unsetFlag(AIFlags.DO_NOT_USE_SHIELDS);
			shieldsForcedOn = true;
		}

		BattleNext_FiringSolutionEval eval = this.maneuverAPI.getEval();
		float shieldDamageLimit = this.ship.getHitpoints() * (0.1F + getAverageArmorFraction(this.ship) * 0.1F);
		shieldDamageLimit *= 0.1F;
		boolean heavyShieldDamage = eval.getShieldDamage() > shieldDamageLimit;
		if (heavyShieldDamage) {
			this.aiFlags.unsetFlag(AIFlags.DO_NOT_USE_SHIELDS);
		}

		this.intervalTracker.advance(amount);
		if (this.intervalTracker.intervalElapsed()) {
			float trendDuration = 5.0F;
			BattleNext_ShipStatisticsTracker tracker = BattleNext_ShipAIHelper.getStatisticsTracker(this.ship);
			BattleNext_ShipStatisticsTracker.BattleNext_TrendFit backingOffFit = tracker.fitBackingOff(trendDuration);
			BattleNext_ShipStatisticsTracker.BattleNext_TrendFit distanceFit = tracker.fitDistanceToEnemy(trendDuration);
			BattleNext_ShipStatisticsTracker.BattleNext_TrendFit hardFluxFit = tracker.fitHardFluxLevel(trendDuration);
			BattleNext_ShipStatisticsTracker.BattleNext_TrendFit damageTakenFit = tracker.fitDamageTaken(trendDuration);
			BattleNext_ShipStatisticsTracker.BattleNext_TrendFit damageDealtFit = tracker.fitDamageDealt(trendDuration);
			boolean backingOffPersistently = backingOffFit.avgX > 0.95F;
			boolean notBackingOff = backingOffFit.avgX < 0.01F;
			boolean hardFluxRising = hardFluxFit.slope > -0.02F && this.ship.getHardFluxLevel() > 0.03F;
			boolean distanceOpeningFast = distanceFit.slope > 20.0F;
			boolean distanceOpening = distanceFit.slope > 5.0F;
			boolean distanceStable = distanceFit.slope > -2.0F && distanceFit.slope < 5.0F && tracker.isDistanceHistoryFull();
			boolean maneuveringDanger = this.flockingAI.getAvoidanceVector() != null || eval.hasSolution();
			float damageTakenAvg = damageTakenFit.avgX;
			float damageDealtAvg = damageDealtFit.avgX;
			boolean takingHeavyDamage = damageTakenAvg > shieldDamageLimit;
			boolean isPhaseShip = this.ship.getPhaseCloak() != null;
			boolean hasShield = this.ship.getShield() != null;
			float missileWarningTime = 2.0F;
			if (hasShield) {
				missileWarningTime = this.ship.getShield().getUnfoldTime();
				if (this.ship.getShield().getType() == ShieldType.OMNI) {
					missileWarningTime *= 0.25F;
				}
			}

			int incomingMissiles = BattleNext_ShipAIHelper.getNumberOfIncomingMissiles(this.ship, missileWarningTime);
			boolean noImmediateDanger = !heavyShieldDamage && !takingHeavyDamage && !maneuveringDanger && incomingMissiles <= 0;
			if (!shieldsForcedOn
				&& BattleNext_ShipAIHelper.isPhaseShip(this.ship)
				&& this.ship.isPhased()
				&& !this.ship.getFluxTracker().isOverloadedOrVenting()
				&& this.ship.getHardFluxLevel() > 0.3F) {
				BattleNext_ThreatData maxThreat = this.threatEvalAI.getMaxThreatData();
				boolean enemyClosesTooFast = true;
				float threatAngle = this.ship.getFacing();
				if (maxThreat != null && maxThreat.getShip() != null) {
					float distanceToThreat = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), maxThreat.getShip().getLocation());
					float threatRange = maxThreat.getThreatRange();
					float threatMargin = threatRange - distanceToThreat;
					float hardFluxFraction = 1.0F - this.ship.getHardFluxLevel();
					float timeToFluxCap = 10000.0F;
					if (hardFluxFit.slope > 0.0F) {
						timeToFluxCap = hardFluxFraction / hardFluxFit.slope;
					}

					if (distanceFit.slope > 0.0F) {
						enemyClosesTooFast = threatMargin / distanceFit.slope < timeToFluxCap * 0.5F;
					} else {
						enemyClosesTooFast = false;
					}

					threatAngle = Misc.getAngleInDegrees(this.ship.getLocation(), maxThreat.getShip().getLocation());
				}

				if (backingOffPersistently && !enemyClosesTooFast) {
					float ventArc = 360.0F - Math.max(0.0F, this.ship.getHardFluxLevel() - 0.25F) * 180.0F;
					float threatInArc = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship, threatAngle, ventArc, this.ship.getCollisionRadius() + 750.0F, true, true);
					float ownThreat = BattleNext_ShipAIHelper.getThreat(this.ship);
					if (threatInArc < ownThreat / 2.0F) {
						this.ship.giveCommand(ShipCommand.VENT_FLUX, null, 0);
					}
				}
			}

			if (!shieldsForcedOn && !isPhaseShip && hasShield) {
				if (backingOffPersistently && (hardFluxRising || this.ship.getShield().isOff() && this.ship.getFluxLevel() > 0.01F) && !distanceOpeningFast && noImmediateDanger) {
					this.aiFlags.setFlag(AIFlags.DO_NOT_USE_SHIELDS, 1.0F);
				} else if (distanceOpening && (hardFluxRising || this.ship.getShield().isOff() && this.ship.getFluxLevel() > 0.01F) && noImmediateDanger) {
					this.aiFlags.setFlag(AIFlags.DO_NOT_USE_SHIELDS, 1.0F);
				} else if (!noImmediateDanger) {
					this.aiFlags.unsetFlag(AIFlags.DO_NOT_USE_SHIELDS);
				}
			}

			this.updateEngagementStance(damageTakenAvg, damageDealtAvg, lowHull, notBackingOff, distanceStable, isPhaseShip, hasShield);
		}
	}

	// Split from advance(); contains everything the vanilla method executes after the
	// shield-suppression block, in the same evaluation order.
	private void updateEngagementStance(float damageTakenAvg, float damageDealtAvg, boolean lowHull, boolean notBackingOff, boolean distanceStable, boolean isPhaseShip, boolean hasShield) {
		CombatEngineAPI engine = BattleNext_ShipAIHelper.getEngine();
		CombatFleetManagerAPI ownFleetManager = engine.getFleetManager(this.ship.getOriginalOwner());
		// engine.getEnemyFleetManager(owner) returns the fleet manager on the opposite side
		CombatFleetManagerAPI enemyFleetManager = engine.getFleetManager(this.ship.getOriginalOwner() == 0 ? 1 : 0);
		if (!engine.isSimulation() && ownFleetManager != null && enemyFleetManager != null && ownFleetManager.isCanForceShipsToEngageWhenBattleClearlyLost()) {
			float ownStrength = computeDeployedStrength(ownFleetManager);
			float enemyStrength = computeDeployedStrength(enemyFleetManager);
			float ownNonWingCount = 0.0F;

			for (DeployedFleetMemberAPI member : ownFleetManager.getDeployedCopyDFM()) {
				if (!member.isFighterWing()) {
					ownNonWingCount++;
				}
			}

			if (enemyStrength > ownStrength * 4.0F && ownNonWingCount <= 4.0F) {
				this.aiFlags.setFlag(AIFlags.DO_NOT_BACK_OFF, 1.0F);
			} else if (enemyStrength > ownStrength * 2.0F) {
				float longDeployedStrength = 0.0F;

				for (DeployedFleetMemberAPI member : ownFleetManager.getDeployedCopyDFM()) {
					if (!member.isFighterWing() && member.getShip() != null) {
						ShipAPI deployed = member.getShip();
						float deployedTime = deployed.getTimeDeployedForCRReduction();
						if (deployedTime >= 60.0F) {
							longDeployedStrength += BattleNext_ShipAIHelper.getDeployedMemberStrength(member, deployed);
						}
					}
				}

				if (enemyStrength > ownStrength * 2.0F && longDeployedStrength > ownStrength * 0.75F && ownFleetManager.getDeployedCopyDFM().size() <= 4) {
					this.aiFlags.setFlag(AIFlags.DO_NOT_BACK_OFF, 1.0F);
				}
			} else if (ownFleetManager.getDeployedCopyDFM().size() == 1 && this.ship.isFrigate()) {
				this.aiFlags.setFlag(AIFlags.DO_NOT_BACK_OFF, 1.0F);
			}
		}

		String personality = BattleNext_ShipAIHelper.getPersonality(this.ship);
		boolean reckless = "reckless".equals(personality);
		boolean aggressive = "aggressive".equals(personality);
		boolean steady = "steady".equals(personality);
		boolean cautious = "cautious".equals(personality);
		boolean timid = "timid".equals(personality);
		if (BattleNext_ShipAIHelper.isShipOrderedToAttack(this.ship)) {
			reckless = true;
			timid = false;
			cautious = false;
			steady = false;
			aggressive = false;
		}

		ShipAPI target = this.getCurrentTarget(this.ship);
		int enemyOwner = 0;
		if (this.ship.getOwner() == 0) {
			enemyOwner = 1;
		}

		CombatFleetManagerAPI targetFleetManager = engine.getFleetManager(enemyOwner);
		// the engine reads the fleet's own task manager here, which the API exposes via getTaskManager(false)
		boolean escaping = targetFleetManager != null && targetFleetManager.getGoal() == FleetGoal.ESCAPE;
		if (targetFleetManager != null && targetFleetManager.getTaskManager(false) != null && targetFleetManager.getTaskManager(false).isInFullRetreat()) {
			escaping = true;
		}

		if (target != null && notBackingOff && distanceStable && !escaping) {
			float maxEngagementRange = 0.0F;
			if (this.maneuverAPI.getAttackAI() != null) {
				maxEngagementRange = this.maneuverAPI.getAttackAI().getMaxRange();
				maxEngagementRange += BattleNext_ShipAIHelper.getDistanceToEntitySurface(this.ship.getLocation(), target, true);
				maxEngagementRange += this.ship.getCollisionRadius() * 0.75F;
				maxEngagementRange += 100.0F;
			}

			float distanceToTarget = Misc.getDistance(target.getLocation(), this.ship.getLocation());
			if (distanceToTarget > maxEngagementRange && distanceToTarget < 3000.0F) {
				this.aiFlags.setFlag(AIFlags.BACK_OFF, 1.0F + (float) Math.random() * 5.0F);
			}
		}

		if (target != null) {
			boolean selfNonCombat = this.ship.isNonCombat(true);
			boolean targetFleeing = false;
			boolean targetIsBigger = false;
			boolean targetHelpless = false;
			Vector2f awayFromTarget = Vector2f.sub(this.ship.getLocation(), target.getLocation(), new Vector2f());
			BattleNext_ShipAIHelper.normalise(awayFromTarget);
			Vector2f targetHeading = BattleNext_ShipAIHelper.getUnitVectorAtAngle(target.getFacing());
			float headingAlignment = Vector2f.dot(awayFromTarget, targetHeading);
			if (headingAlignment < -0.67F) {
				targetFleeing = true;
			}

			CombatFleetManagerAPI targetSideFleetManager = engine.getFleetManager(target.getOriginalOwner());
			if (targetSideFleetManager != null && targetSideFleetManager.getGoal() == FleetGoal.ESCAPE && headingAlignment < 0.0F) {
				targetFleeing = true;
			}

			Set<ShipTypeHints> targetHints = target.getVariant().getHints();
			// approximation: ShipVariantAPI does not expose isNonCombat(); the port reproduces the
			// engine formula from the variant: (carrier or civilian) and not combat
			boolean targetVariantNonCombat = (targetHints.contains(ShipTypeHints.CARRIER) || target.getVariant().isCivilian()) && !targetHints.contains(ShipTypeHints.COMBAT);
			targetIsBigger = this.ship.getHullSize().ordinal() < target.getHullSize().ordinal() && (!targetVariantNonCombat || selfNonCombat);
			// Ship.getMaxSpeed() delegates to the engine controller's effective max speed.
			boolean targetSlower = BattleNext_ShipAIHelper.getMaxSpeed(target) < this.ship.getMaxSpeed() + 30.0F;
			targetHelpless = target.getFluxTracker().isOverloadedOrVenting()
				&& (target.getFluxTracker().getOverloadTimeRemaining() > 3.0F || target.getFluxTracker().getTimeToVent() > 4.0F)
				|| target.isPhased() && target.getFluxLevel() > 0.8F;
			boolean outdamagingThreat = damageDealtAvg > damageTakenAvg * 3.0F && damageDealtAvg > 33.0F * target.getHullSize().ordinal() && !lowHull;
			CombatFleetManagerAPI ownFleetManager2 = engine.getFleetManager(this.ship.getOriginalOwner());
			if ((!aggressive && !reckless || lowHull) && !targetFleeing && !isPhaseShip && !escaping && !target.isFighter() && targetIsBigger && !targetHelpless && targetSlower && !outdamagingThreat && ownFleetManager2 != null) {
				float weightedDistanceSum = 0.0F;
				float weightSum = 0.0F;

				for (DeployedFleetMemberAPI member : ownFleetManager2.getDeployedCopyDFM()) {
					ShipAPI other = member.getShip();
					// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
					if (other == null) {
						continue;
					}
					if (other != this.ship && !other.isHulk() && !other.isFighter()) {
						ShipAPI otherTarget = this.getCurrentTarget(other);
						if ((otherTarget == null || otherTarget == target) && !other.isNonCombat(true) && !BattleNext_ShipAIHelper.isEscortTargetOf(other, this.ship)) {
							float distanceToTargetFromOther = BattleNext_ShipAIHelper.getDistance(target.getLocation(), other.getLocation());
							float otherThreat = BattleNext_ShipAIHelper.getThreat(other);
							if (!(distanceToTargetFromOther > 4000.0F)) {
								distanceToTargetFromOther -= getThreat(this.threatEvalAI, other);
								weightedDistanceSum += distanceToTargetFromOther * otherThreat;
								weightSum += otherThreat;
							}
						}
					}
				}

				float ownWeightedDistance = BattleNext_ShipAIHelper.getDistance(target.getLocation(), this.ship.getLocation());
				ownWeightedDistance -= getThreat(this.threatEvalAI, this.ship);
				float advantageNeeded = 300.0F;
				if (cautious) {
					advantageNeeded = 100.0F;
				}

				if (timid) {
					advantageNeeded = 10.0F;
				}

				if (!hasShield && !isPhaseShip) {
					advantageNeeded *= 0.5F;
				}

				if (weightSum > 0.0F) {
					float averageWeightedDistance = weightedDistanceSum / weightSum;
					if (averageWeightedDistance - ownWeightedDistance > advantageNeeded && averageWeightedDistance > advantageNeeded) {
						this.aiFlags.setFlag(AIFlags.BACK_OFF, 1.0F);
					}
				}
			}

			ShipAPI cornerCheeseRoot = target;
			if (target != null && target.isFighter() && target.getWing() != null && target.getWing().getSourceShip() != null) {
				cornerCheeseRoot = target.getWing().getSourceShip();
			}

			// approximation: the engine tracks very recent corner-cheese targets in an internal timeout
			// tracker fed by its own surround-feasibility analysis, none of which the mod API exposes;
			// replaced with the engine's own corner-proximity test (see isVeryRecentCornerCheeseTarget)
			if (isVeryRecentCornerCheeseTarget(cornerCheeseRoot)) {
				float wingRangeRelief = 0.0F;
				if (!cornerCheeseRoot.getLaunchBaysCopy().isEmpty() && cornerCheeseRoot.getCurrentCR() > 0.0F) {
					for (FighterLaunchBayAPI bay : cornerCheeseRoot.getLaunchBaysCopy()) {
						if (bay.getWing() != null) {
							float wingRange = bay.getWing().getRange();
							wingRangeRelief = Math.max(wingRangeRelief, wingRange - 1500.0F);
						}
					}
				}

				this.aiFlags.setFlag(AIFlags.BACK_OFF, 1.0F);
				this.aiFlags.setFlag(AIFlags.BACK_OFF_MIN_RANGE, 1.0F, 3700.0F + wingRangeRelief);
				this.aiFlags.setFlag(AIFlags.STANDING_OFF_VS_SHIP_ON_MAP_BORDER, 1.0F);
			}
		}

		this.manageCarrierFighters();
	}

	public static boolean isPhaseCheeseEverDetected() {
		String key = phaseCheeseEverDetectedKey;
		return BattleNext_ShipAIHelper.getEngine().getCustomData().containsKey(key);
	}

	public static boolean isPhaseCheeseOngoing() {
		String key = phaseCheeseOngoingKey;
		return BattleNext_ShipAIHelper.containsCustomDataWithTimeout(key);
	}

	public List<ShipAPI> getEnemyShipsForFighterManagement() {
		if (BattleNext_ShipAIHelper.getEngine().isSimulation() && !Global.getSettings().isDevMode()) {
			return null;
		}

		String key = phaseCheeseEverDetectedKey;
		boolean everDetected = BattleNext_ShipAIHelper.getEngine().getCustomData().containsKey(key);
		CombatFleetManagerAPI ownFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner());
		CombatFleetManagerAPI enemyFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner() == 0 ? 1 : 0);
		ArrayList<ShipAPI> phaseMenaces = new ArrayList<>();
		float phaseMenaceThreat = 0.0F;
		float nearbyPhaseMenaceThreat = 0.0F;
		float regularThreat = 0.0F;
		float nearbyRegularThreat = 0.0F;
		int phaseMenaceCount = 0;

		for (DeployedFleetMemberAPI member : enemyFleetManager.getDeployedCopyDFM()) {
			if (!member.isFighterWing()) {
				ShipAPI enemy = member.getShip();
				// approximation: engine DeployedFleetMember.getShip() fabricates a placeholder instead of returning null
				if (enemy == null) {
					continue;
				}
				if (!enemy.isRetreating() || enemy == BattleNext_ShipAIHelper.getEngine().getPlayerShip()) {
					float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), enemy.getLocation());
					float threat = BattleNext_ShipAIHelper.getThreat(enemy);
					if (enemy.isStationModule() && enemy.getParentStation() != null && enemy.getParentStation().isStation()) {
						threat *= 4.0F;
					}

					if (!member.isFighterWing() && this.isPhaseShipMenace(enemy)) {
						phaseMenaceThreat += threat;
						phaseMenaceCount++;
						if (distance < 4000.0F || everDetected) {
							nearbyPhaseMenaceThreat += threat;
							phaseMenaces.add(enemy);
						}
					} else if (!enemy.isNonCombat(false) && !BattleNext_ShipAIHelper.isCarrier(enemy)) {
						regularThreat += threat;
						if (distance < 4000.0F) {
							nearbyRegularThreat += threat;
						}
					}
				}
			}
		}

		if (nearbyPhaseMenaceThreat <= 0.0F) {
			return null;
		}

		if (regularThreat >= 4.0F) {
			if (!BattleNext_ShipAIHelper.containsCustomDataWithTimeout(phaseCheeseRecentlyDetectedKey)) {
				return null;
			}
		} else if (!phaseMenaces.isEmpty()) {
			BattleNext_ShipAIHelper.setCustomDataWithTimeout(phaseCheeseRecentlyDetectedKey, true, 20.0F + (float) Math.random() * 20.0F);
		}

		if (!phaseMenaces.isEmpty()) {
			BattleNext_ShipAIHelper.getEngine().getCustomData().put(key, true);
			BattleNext_ShipAIHelper.setCustomDataWithTimeout(phaseCheeseOngoingKey, true, 1.0F);
		}

		return phaseMenaces;
	}

	protected boolean isPhaseShipMenace(ShipAPI enemy) {
		if (enemy != null && !enemy.isFighter() && BattleNext_ShipAIHelper.isPhaseShip(enemy)) {
			boolean menace = enemy.isFrigate() && enemy.getCurrentCR() > 0.3F;
			float minSpeedFluxLevel = enemy.getMutableStats()
				.getDynamic()
				.getMod("phase_cloak_flux_level_for_min_speed_mod")
				.computeEffective(PhaseCloakStats.BASE_FLUX_LEVEL_FOR_MIN_SPEED);
			// the engine reads this to weigh phase speed; the port keeps the computation for parity
			float phaseSpeedBonus = enemy.getMutableStats().getDynamic().getMod("phase_cloak_speed").computeEffective(0.0F);
			if (menace) {
				// engine.getTaskManager(owner, ally) maps to fleetManager(owner).getTaskManager(ally)
				CombatFleetManagerAPI enemyFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(enemy.getOriginalOwner());
				AssignmentInfo assignment = enemyFleetManager.getTaskManager(enemy.isAlly()).getAssignmentFor(enemy);
				if (assignment != null && assignment.getTarget() instanceof BattleObjectiveAPI) {
					menace = false;
				}

				for (BattleObjectiveAPI objective : BattleNext_ShipAIHelper.getEngine().getObjectives()) {
					float distanceToObjective = BattleNext_ShipAIHelper.getDistance(enemy.getLocation(), objective.getLocation());
					float distanceToObjectiveFromSelf = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), objective.getLocation());
					if (distanceToObjective < 2000.0F && (distanceToObjective < distanceToObjectiveFromSelf || this.ship.getFullTimeDeployed() < 60.0F && !isPhaseCheeseEverDetected())) {
						menace = false;
					}
				}
			}

			return menace;
		} else {
			return false;
		}
	}

	public Vector2f getCampLocation(Vector2f fallback) {
		// approximation: the engine reads the combat map rectangle, which the API does not expose;
		// the helper recomputes it from the map width/height assuming a map centered at the origin
		float centerX = (BattleNext_ShipAIHelper.getMapMinX() + BattleNext_ShipAIHelper.getMapMaxX()) * 0.5F;
		Vector2f campLocation = new Vector2f(centerX, BattleNext_ShipAIHelper.getMapMaxY() - 5000.0F);
		if (this.ship.getOriginalOwner() == 0) {
			campLocation = new Vector2f(centerX, BattleNext_ShipAIHelper.getMapMinY() + 5000.0F);
		}

		BattleObjectiveAPI nearestObjective = null;
		float nearestObjectiveDistance = Float.MAX_VALUE;

		for (BattleObjectiveAPI objective : BattleNext_ShipAIHelper.getEngine().getObjectives()) {
			float distance = BattleNext_ShipAIHelper.getDistance(fallback, objective.getLocation());
			if (distance < nearestObjectiveDistance) {
				nearestObjectiveDistance = distance;
				nearestObjective = objective;
			}
		}

		if (nearestObjective != null && nearestObjectiveDistance < 3000.0F) {
			campLocation = new Vector2f(nearestObjective.getLocation());
		}

		return campLocation;
	}

	public boolean canFightersEngage(ShipAPI carrier, ShipAPI enemy) {
		if (carrier.getLaunchBaysCopy().isEmpty()) {
			return false;
		}

		float phaseCloakMaxSpeed = BattleNext_ShipAIHelper.getPhaseCloakMaxSpeed(enemy);
		float distanceToEnemy = BattleNext_ShipAIHelper.getDistance(carrier.getLocation(), enemy.getLocation());
		float enemyCR = enemy.getCurrentCR();
		if (!(enemyCR < 0.3F) && !(enemyCR - Math.max(0.0F, enemy.getHardFluxLevel() - 0.5F) * 0.5F < 0.3F) && !(enemy.getHardFluxLevel() > 0.75F)) {
			float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, enemy);

			for (FighterLaunchBayAPI bay : this.ship.getLaunchBaysCopy()) {
				FighterWingAPI wing = bay.getWing();
				if (wing != null) {
					// approximation: the engine wing spec flag isAlwaysEscort has no API counterpart
					boolean alwaysEscort = BattleNext_ShipAIHelper.isAlwaysEscortWing(wing.getSpec()) || bay.getWing().getRange() <= 0.0F;
					if (!alwaysEscort) {
						ShipAPI leader = wing.getLeader();
						if (leader != null) {
							float leaderSpeed = leader.getMaxSpeedWithoutBoost();
							float leaderDistance = BattleNext_ShipAIHelper.getDistance(leader.getLocation(), enemy.getLocation());
							if (leaderDistance < 500.0F + enemy.getCollisionRadius() && closingSpeed < 10.0F) {
								return true;
							}

							float wingRange = wing.getRange();
							if (!(leaderSpeed < phaseCloakMaxSpeed)) {
								float speedAdvantage = leaderSpeed - phaseCloakMaxSpeed;
								if (speedAdvantage < 1.0F) {
									speedAdvantage = 1.0F;
								}

								float interceptTime = leaderDistance / speedAdvantage;
								float enemyRunDistance = phaseCloakMaxSpeed * interceptTime;
								if (!(distanceToEnemy + enemyRunDistance > wingRange)) {
									return true;
								}
							}
						}
					}
				}
			}

			return false;
		} else {
			return true;
		}
	}

	public boolean shouldPullBack(ShipAPI carrier, ShipAPI enemy) {
		if (this.isPhaseShipMenace(carrier)) {
			return true;
		}

		if (enemy.getCurrentCR() < 0.2F) {
			return true;
		}

		if (!carrier.isFrigate()) {
			return false;
		}

		float phaseCloakMaxSpeed = BattleNext_ShipAIHelper.getPhaseCloakMaxSpeed(enemy);
		if (enemy.getCurrentCR() < 0.3F && carrier.isFrigate()) {
			return true;
		}

		float carrierSpeed = carrier.getMaxSpeedWithoutBoost();
		if (carrier.getSystem() != null) {
			// approximation: ShipSystemSpecAPI.getAIHints() is not exposed; the engine added the
			// AI hint's average speed increase here
			carrierSpeed += 0.0F;
		}

		if (carrierSpeed < enemy.getMaxSpeedWithoutBoost()) {
			return false;
		} else {
			float distance = BattleNext_ShipAIHelper.getDistance(carrier.getLocation(), enemy.getLocation());
			float proximityBonus = Math.max(0.0F, (1.0F - distance / 2000.0F) * 0.25F);
			if (carrierSpeed > phaseCloakMaxSpeed * (0.65F - proximityBonus)) {
				return true;
			} else {
				return enemy.getHardFluxLevel() < distance / 2000.0F && enemy.getHardFluxLevel() < 0.5F ? false : carrierSpeed > phaseCloakMaxSpeed;
			}
		}
	}

	public ShipAPI selectEscortTarget(List<ShipAPI> candidates) {
		int frigates = 0;
		int destroyers = 0;
		int cruisers = 0;
		int capitals = 0;

		for (ShipAPI candidate : candidates) {
			if (candidate.isFrigate()) {
				frigates++;
			}

			if (candidate.isDestroyer()) {
				destroyers++;
			}

			if (candidate.isCruiser()) {
				cruisers++;
			}

			if (candidate.isCapital()) {
				capitals++;
			}
		}

		HullSize escortSizeLimit = null;
		if (capitals > 0 && (frigates > 0 || destroyers > 0)) {
			escortSizeLimit = HullSize.DESTROYER;
		}

		if (escortSizeLimit != null && this.ship.getHullSize().ordinal() <= escortSizeLimit.ordinal()) {
			ShipAPI chosen = null;
			float closestDistance = Float.MAX_VALUE;

			for (ShipAPI candidate : candidates) {
				if (candidate != this.ship && candidate.getHullSize().ordinal() >= HullSize.CAPITAL_SHIP.ordinal()) {
					if (BattleNext_ShipAIHelper.isEscortTargetOf(this.ship, candidate)) {
						chosen = candidate;
						break;
					}

					if (!BattleNext_ShipAIHelper.isShipBeingEscorted(candidate)) {
						float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), candidate.getLocation());
						if (distance < closestDistance) {
							closestDistance = distance;
							chosen = candidate;
						}
					}
				}
			}

			if (chosen == null) {
				return null;
			}

			for (ShipAPI candidate : candidates) {
				if (candidate != this.ship && candidate.getHullSize().ordinal() <= escortSizeLimit.ordinal()) {
					float distance = BattleNext_ShipAIHelper.getDistance(chosen.getLocation(), candidate.getLocation());
					if (distance < closestDistance) {
						return null;
					}
				}
			}

			return chosen;
		} else {
			return null;
		}
	}

	public void manageCarrierFighters() {
		if (this.ship.getOwner() != 0) {
			// engine.getTaskManager(owner, ally) maps to fleetManager(owner).getTaskManager(ally)
			CombatFleetManagerAPI ownFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner());
			CombatTaskManagerAPI ownTaskManager = ownFleetManager != null ? ownFleetManager.getTaskManager(this.ship.isAlly()) : null;
			CombatFleetManagerAPI enemyFleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner() == 0 ? 1 : 0);
			if (ownTaskManager != null && ownFleetManager != null) {
				if (ownFleetManager.getGoal() != FleetGoal.ESCAPE && enemyFleetManager.getGoal() != FleetGoal.ESCAPE) {
					List<ShipAPI> phaseMenaces = this.getEnemyShipsForFighterManagement();
					if (phaseMenaces != null && !phaseMenaces.isEmpty()) {
						if (ownTaskManager.isFullAssault()) {
							if (ownFleetManager.getOwner() == 0) {
								return;
							}

							ownTaskManager.setFullAssault(false);
						}

						ShipAPI nearestMenace = null;
						float nearestMenaceDistance = Float.MAX_VALUE;
						boolean allMenacesLowCR = true;

						for (ShipAPI menace : phaseMenaces) {
							float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), menace.getLocation());
							if (distance < nearestMenaceDistance) {
								nearestMenaceDistance = distance;
								nearestMenace = menace;
							}

							allMenacesLowCR &= menace.getCurrentCR() < 0.3F;
						}

						if (!allMenacesLowCR) {
							// intentionally kept dead: vanilla fetched the combat map here and never used it
							ArrayList<ShipAPI> alliedShips = new ArrayList<>();
							int alliedCount = 0;
							float maxCollisionRadius = 0.0F;
							Vector2f threatWeightedCenter = new Vector2f();
							float centerThreatSum = 0.0F;
							float totalDeploymentCost = 0.0F;
							float lowPeakFrigateCost = 0.0F;

							for (DeployedFleetMemberAPI member : ownFleetManager.getDeployedCopyDFM()) {
								if (!member.isFighterWing() && member.getShip() != null) {
									ShipAPI allied = member.getShip();
									alliedCount++;
									alliedShips.add(allied);
									maxCollisionRadius = Math.max(maxCollisionRadius, allied.getCollisionRadius());
									float alliedThreat = BattleNext_ShipAIHelper.getThreat(allied);
									Vector2f weightedPosition = new Vector2f(allied.getLocation());
									weightedPosition.scale(alliedThreat);
									Vector2f.add(threatWeightedCenter, weightedPosition, threatWeightedCenter);
									centerThreatSum += alliedThreat;
									if (allied.getHullSpec().hasTag("omega")) {
										return;
									}

									if (allied.getHullSpec().hasTag("monster")) {
										return;
									}

									if (member.getMember() != null) {
										// the engine reaches the same FleetMemberAPI through ship.getFleetMember()
										float deploymentCost = member.getMember().getDeploymentCostSupplies();
										totalDeploymentCost += deploymentCost;
										if (allied.isFrigate() && allied.getPeakTimeRemaining() < nearestMenace.getPeakTimeRemaining() - 30.0F) {
											lowPeakFrigateCost += deploymentCost;
										}
									}
								}
							}

							threatWeightedCenter.scale(1.0F / Math.max(1.0F, centerThreatSum));
							if (!(lowPeakFrigateCost > totalDeploymentCost * 0.1F)) {
								if (alliedShips.size() >= 3) {
									Vector2f campLocation = this.getCampLocation(threatWeightedCenter);
									Vector2f holdPosition = new Vector2f(threatWeightedCenter);
									float distanceToCamp = BattleNext_ShipAIHelper.getDistance(holdPosition, campLocation);
									if (distanceToCamp >= 1000.0F) {
										Vector2f campOffset = BattleNext_ShipAIHelper.getUnitVectorAtAngle(BattleNext_ShipAIHelper.getAngleFast(holdPosition, campLocation));
										campOffset.scale(1000.0F);
										Vector2f.add(holdPosition, campOffset, holdPosition);
									} else {
										holdPosition.set(campLocation);
									}

									float distanceToHold = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), holdPosition);
									if (distanceToHold < 1500.0F && alliedShips.size() > 0) {
										Vector2f facingWeightedCenter = new Vector2f();
										float facingThreatSum = 0.0F;

										for (ShipAPI allied : alliedShips) {
											if (allied != this.ship) {
												float distanceToAllied = BattleNext_ShipAIHelper.getDistance(allied.getLocation(), this.ship.getLocation());
												float minSeparation = (allied.getCollisionRadius() + this.ship.getCollisionRadius()) * 0.75F;
												if (minSeparation < 300.0F) {
													minSeparation = 300.0F;
												}

												if (distanceToAllied < minSeparation + 300.0F) {
													float alliedThreat = BattleNext_ShipAIHelper.getThreat(allied);
													Vector2f weightedPosition = new Vector2f(allied.getLocation());
													weightedPosition.scale(alliedThreat);
													Vector2f.add(facingWeightedCenter, weightedPosition, facingWeightedCenter);
													facingThreatSum += alliedThreat;
												}
											}
										}

										facingWeightedCenter.scale(1.0F / Math.max(1.0F, facingThreatSum));
										ShipAPI menace = nearestMenace;
										float threatAtMenaceAngle = 0.0F;
										if (menace != null) {
											float angleToMenace = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), menace.getLocation());
											threatAtMenaceAngle = BattleNext_ShipAIHelper.getTotalThreatInRange(this.ship, angleToMenace, 30.0F, 400.0F + this.ship.getCollisionRadius(), true, true);
										}

										if (threatAtMenaceAngle > 0.0F || menace == null) {
											float facingAngle = BattleNext_ShipAIHelper.getAngleFast(facingWeightedCenter, this.ship.getLocation());
											this.ship.getAIFlags().setFlag(AIFlags.FACING_OVERRIDE_FOR_MOVE_AND_ESCORT_MANEUVERS, 1.0F, facingAngle);
										}
									}

									boolean engage = this.canFightersEngage(this.ship, nearestMenace);
									if (engage) {
										this.ship.setForceCarrierPullBackTime(0.0F);
									} else {
										this.ship.setForceCarrierPullBackTime(1.0F);
									}

									this.ship.getAIFlags().setFlag(AIFlags.CAMP_LOCATION, 1.0F, holdPosition);
									ShipAPI escortTarget = this.selectEscortTarget(alliedShips);
									if (escortTarget != null) {
										this.ship.getAIFlags().setFlag(AIFlags.ESCORT_OTHER_SHIP, 1.0F, escortTarget);
									}

									if (this.shouldPullBack(this.ship, nearestMenace)) {
										this.ship.setForceCarrierPullBackTime(0.0F);
										this.ship.getAIFlags().unsetFlag(AIFlags.CAMP_LOCATION);
										this.ship.getAIFlags().unsetFlag(AIFlags.ESCORT_OTHER_SHIP);
										this.ship.getAIFlags().unsetFlag(AIFlags.FACING_OVERRIDE_FOR_MOVE_AND_ESCORT_MANEUVERS);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	protected ShipAPI getCurrentTarget(ShipAPI other) {
		if (other.getShipTarget() != null) {
			return other.getShipTarget();
		}

		Object maneuverTarget = other.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
		return maneuverTarget instanceof ShipAPI ? (ShipAPI) maneuverTarget : BattleNext_ShipAIHelper.getNearestEnemy(other, true);
	}

	public static float getThreat(BattleNext_ThreatEvalAI threatEvalAI, ShipAPI other) {
		// approximation: native BasicShipAI attack range is hidden behind the
		// ShipAPI wrapper; threat evaluation supplies its observable range.
		BattleNext_BasicShipAI basicAI = BattleNext_BasicShipAI.getActiveInstance(other);
		if (basicAI != null) {
			if (basicAI.getAttackAI() != null) {
				return basicAI.getAttackAI().getNonMissileMaxRange();
			}
		}

		BattleNext_ThreatData threatData = threatEvalAI.computeThreatData(other);
		return threatData.getThreatRange();
	}

	// approximation of engine CombatEngine.isInCorner; the engine's very-recent corner-cheese
	// tracker also requires surround-feasibility checks that the mod API cannot see
	private static boolean isVeryRecentCornerCheeseTarget(ShipAPI target) {
		if (target == null) {
			return false;
		}

		float cornerDistance = 1000.0F;
		if (target.isCapital()) {
			cornerDistance = 2500.0F;
		} else if (target.isCruiser()) {
			cornerDistance = 2000.0F;
		} else if (target.isDestroyer() || target.isFrigate()) {
			cornerDistance = 1750.0F;
		}

		float x = target.getLocation().x;
		float y = target.getLocation().y;
		float minX = BattleNext_ShipAIHelper.getMapMinX();
		float maxX = BattleNext_ShipAIHelper.getMapMaxX();
		float minY = BattleNext_ShipAIHelper.getMapMinY();
		float maxY = BattleNext_ShipAIHelper.getMapMaxY();
		float bottomLeft = x - minX + (y - minY);
		float bottomRight = maxX - x + (y - minY);
		float topLeft = x - minX + (maxY - y);
		float topRight = maxX - x + (maxY - y);
		float threshold = cornerDistance * 2.0F;
		if (bottomLeft < threshold) {
			return true;
		} else if (bottomRight < threshold) {
			return true;
		} else if (topLeft < threshold) {
			return true;
		} else if (topRight < threshold) {
			return true;
		} else {
			cornerDistance = 1000.0F;
			if (x < minX + cornerDistance) {
				return true;
			} else if (x > maxX - cornerDistance) {
				return true;
			} else {
				return y < minY + cornerDistance ? true : y > maxY - cornerDistance;
			}
		}
	}

	// approximation of the engine's own fleet strength model
	private static float computeDeployedStrength(CombatFleetManagerAPI fleetManager) {
		float strength = 0.0F;

		for (DeployedFleetMemberAPI member : fleetManager.getDeployedCopyDFM()) {
			if (!member.isFighterWing()) {
				float weight = 0.25F;
				if (!member.isStationModule()) {
					weight = 1.0F;
					if (member.getShip() != null && member.getShip().isNonCombat(false) && member.getShip().getAllWings().isEmpty()) {
						weight = 0.25F;
					}

					if (member.getShip() != null && !member.getShip().controlsLocked() && !member.getShip().isRetreating()) {
						strength += BattleNext_ShipAIHelper.getDeployedMemberStrength(member, member.getShip()) * weight;
					} else {
						strength += BattleNext_ShipAIHelper.getDeployedMemberStrength(member, member.getShip()) * 0.25F * weight;
					}
				}
			}
		}

		return strength;
	}

	// Port of ArmorGrid.getAverageArmorFraction() using the exposed grid cells.
	private static float getAverageArmorFraction(ShipAPI target) {
		ArmorGridAPI grid = target.getArmorGrid();
		float total = 0.0F;
		int cells = 0;
		float[][] values = grid.getGrid();
		int columns = values.length;
		int rows = columns > 0 ? values[0].length : 0;
		for (int x = 0; x < columns; x++) {
			for (int y = 0; y < rows; y++) {
				total += grid.getArmorFraction(x, y);
				cells++;
			}
		}
		return cells > 0 ? total / cells : 1.0F;
	}
}
