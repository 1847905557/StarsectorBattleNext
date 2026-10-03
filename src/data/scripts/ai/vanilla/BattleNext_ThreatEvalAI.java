package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.maneuvers.BattleNext_AttackEntityManeuver;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.ai.vanilla.movement.BattleNext_FlockingAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;

/**
 * Port of engine class com.fs.starfarer.combat.ai.ThreatEvalAI.
 * Scores every enemy ship around the owner and turns the picture into a
 * threat response maneuver when the pressure gets high enough.
 * profiler hooks dropped.
 */
public class BattleNext_ThreatEvalAI {
	// intentionally kept dead: these constants are unreferenced in vanilla
	public static final float UNUSED_CONSTANT_5 = 5.0F;
	public static final float UNUSED_CONSTANT_10 = 10.0F;
	public static final float UNUSED_CONSTANT_4 = 4.0F;
	public static final float UNUSED_CONSTANT_0_0005 = 5.0E-4F;
	private IntervalUtil evalIntervalTracker;
	private ShipAPI ship;
	private Map<ShipAPI, BattleNext_ThreatData> threatMap = new HashMap<>();
	private float totalThreat = 0.0F;
	private float threatDivisor = 1.0F;
	private final BattleNext_ManeuverAPI maneuverAPI;
	private final BattleNext_FlockingAI flockingAI;
	private float threatThreshold = 7.0F;
	private float unusedBraveryModifier = 0.5F;
	private BattleNext_FiringSolutionEval firingSolutionEval;
	private Map<ShipAPI, BattleNext_ThreatData> threatCache = new HashMap<>();
	private float prevFluxLevel = 0.0F;
	private boolean shieldHit = false;
	private float timeSinceLastManeuver = 0.0F;
	private boolean allThreatsActive = false;

	public BattleNext_ThreatEvalAI(ShipAPI ship, BattleNext_ManeuverAPI maneuverAPI, BattleNext_FlockingAI flockingAI) {
		this.ship = ship;
		this.maneuverAPI = maneuverAPI;
		this.flockingAI = flockingAI;
		this.evalIntervalTracker = new IntervalUtil(0.25F, 0.5F);
		this.evalIntervalTracker.randomize();
		this.firingSolutionEval = new BattleNext_FiringSolutionEval(ship, maneuverAPI.getAIFlags());
		this.initThreatThresholds();
	}

	public IntervalUtil getEvalIntervalTracker() {
		return this.evalIntervalTracker;
	}

	private void initThreatThresholds() {
		CombatFleetManagerAPI fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(this.ship.getOriginalOwner());
		if (fleetManager != null) {
			DeployedFleetMemberAPI member = fleetManager.getDeployedFleetMember(this.ship);
			if (member != null) {
				// approximation: engine read the deployed member's personality bravery value
				float bravery = BattleNext_ShipAIHelper.getBravery(this.ship);
				this.threatThreshold = 2.0F + bravery;
				this.unusedBraveryModifier = 0.5F;
				if (bravery >= 5.0F) {
					this.unusedBraveryModifier += 0.1F * (bravery - 5.0F);
				} else {
					this.unusedBraveryModifier -= 0.03F * bravery;
				}
			}
		}
	}

	public BattleNext_ThreatResponse advance(float amount) {
		try {
			this.timeSinceLastManeuver += amount;
			this.evalIntervalTracker.advance(amount);
			if (this.ship.getShield() != null && BattleNext_ShipAIHelper.shieldWasHit(this.ship)) {
				this.shieldHit = true;
			}

			if (!this.evalIntervalTracker.intervalElapsed()) {
				return null;
			}

			this.threatCache.clear();
			this.updateThreats(amount);
			float strengthRatio = computeFleetStrengthRatio(this.ship, 1500.0F, this);
			this.computeThreatDivisor(strengthRatio);
			float totalThreatValue = this.getTotalThreat();
			BattleNext_ThreatData maxThreat = this.getMaxThreatData();
			float fluxLevel = this.ship.getFluxLevel();
			float fluxRate = (fluxLevel - this.prevFluxLevel) / this.evalIntervalTracker.getIntervalDuration();
			if (fluxRate > 0.25F && this.shieldHit && maxThreat != null) {
				totalThreatValue = this.threatThreshold * 3.0F;
			}

			this.shieldHit = false;
			this.prevFluxLevel = fluxLevel;
			if (totalThreatValue <= 0.0F || maxThreat == null) {
				return null;
			}

			ArrayList<Float> threatAngles = new ArrayList<>();

			for (BattleNext_ThreatData data : this.threatMap.values()) {
				float angle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), data.getShip().getLocation());
				threatAngles.add(angle);
			}

			float angleGap = BattleNext_ShipAIHelper.getLargestAngleGap(threatAngles);
			float gapMidpoint = BattleNext_ShipAIHelper.getLargestAngleGapMidpoint(threatAngles);
			float gapToHeading = BattleNext_ShipAIHelper.getAngleDiff(gapMidpoint, this.ship.getFacing());
			float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), maxThreat.getShip().getLocation());
			float radiusSum = this.ship.getCollisionRadius() + maxThreat.getShip().getCollisionRadius();
			if (gapToHeading < 30.0F && this.threatMap.size() > 1) {
				// intentionally kept dead: vanilla only declares a local here
			} else {
				// intentionally kept dead
			}

			// intentionally kept dead: the three booleans below are never read in vanilla
			boolean dominant = this.isDominantThreat(maxThreat);
			boolean mobile = this.ship.getMaxSpeed() > 40.0F && this.ship.getMaxTurnRate() > 20.0F;
			if (!this.ship.isFrigate() && !this.ship.isFighter()) {
				// intentionally kept dead: vanilla only declares a local here
			} else {
				// intentionally kept dead
			}

			boolean wideGap = angleGap > 215.0F;
			boolean farThreat = distance > radiusSum * 6.0F;
			boolean isFighter = this.ship.isFighter();
			boolean canManeuver = (!this.ship.isCapital() || !maxThreat.getShip().isFrigate()) && !maxThreat.getShip().isFighter();
			canManeuver &= !BattleNext_ShipAIHelper.isEngineDisabled(this.ship);
			if (maxThreat.getShip().isFrigate() && this.threatMap.size() > 1) {
				canManeuver = false;
			}

			if (maxThreat.getShip().isCapital()) {
				canManeuver = false;
			}

			if (this.ship.getShield() != null && this.ship.getShield().getType() == ShieldType.OMNI) {
				canManeuver = false;
			}

			if (this.ship.getShield() != null && this.ship.getShield().isOn()) {
				boolean threatInsideShield = BattleNext_ShipAIHelper.isInArc(
					this.ship.getShield().getFacing(), this.ship.getShield().getActiveArc(), this.ship.getShieldCenterEvenIfNoShield(), maxThreat.getShip().getLocation()
				);
				if (threatInsideShield) {
					canManeuver = false;
				}
			}

			if (totalThreatValue > 0.0F && !mobile && gapToHeading < 30.0F && canManeuver && this.ship.getShipTarget() == maxThreat.getShip() && this.timeSinceLastManeuver > 22.0F) {
				this.timeSinceLastManeuver = (float) Math.random() * 10.0F;
				return new BattleNext_ThreatResponse(
					new BattleNext_AttackEntityManeuver(this.ship, maxThreat.getShip(), this.flockingAI, this.maneuverAPI), 7.0F + this.ship.getFluxTracker().getTimeToVent());
			}

			if (isFighter || this.getMaxThreatData() == null || !this.getMaxThreatData().getShip().isFighter()) {
				// intentionally kept dead: vanilla overwrites the flag with false immediately
				boolean fluxNearMax = this.ship.getFluxLevel() > 0.95F;
				fluxNearMax = false;
				boolean smallThreat = maxThreat.getShip().isFrigate() || maxThreat.getShip().isFighter();
				smallThreat |= BattleNext_ShipAIHelper.isEscortManeuverActive(this.ship);
				this.firingSolutionEval.resetSolution();
				float maxWeaponDamage = this.firingSolutionEval.getMaxWeaponDamage();
				float armor = this.ship.getArmorGrid().getArmorRating();
				if (this.getMaxThreatData() != null) {
					// approximation: engine ship.getArmorEvaluator().getArmorInArc(facing, arc)
					armor = this.ship
						.getAverageArmorInSlice(BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.getMaxThreatData().getShip().getLocation()), 90.0F);
				}

				float hullPoints = 0.0F;
				hullPoints += this.ship.getHitpoints();
				float fluxPool = Float.MAX_VALUE;
				if (this.ship.getShield() != null) {
					float fluxPerPoint = this.ship.getShield().getFluxPerPointOfDamage() / this.ship.getMutableStats().getShieldDamageTakenMult().getModifiedValue();
					if (fluxPerPoint > 0.0F) {
						fluxPool += BattleNext_ShipAIHelper.getFluxAvailable(this.ship) / fluxPerPoint * 0.2F;
					}
				}

				float hullBuffer = this.ship.getHitpoints() * 0.2F;
				boolean armorStrong = hullBuffer > this.firingSolutionEval.getMaxWeaponDamage() * 2.0F;
				boolean dangerToHull;
				if (!(fluxPool > hullBuffer) && armorStrong) {
					dangerToHull = this.firingSolutionEval.getMaxWeaponDamage() > hullBuffer;
				} else {
					dangerToHull = this.firingSolutionEval.getHullDamage() > fluxPool;
				}

				if (smallThreat || (!(totalThreatValue > 0.0F) || !(totalThreatValue > this.threatThreshold)) && !dangerToHull || this.threatMap.isEmpty()) {
					return new BattleNext_ThreatResponse(null, 0.0F);
				}

				this.maneuverAPI.getAIFlags().setFlag(AIFlags.NEEDS_HELP);
				return null;
			}
		} finally {
			// intentionally kept empty: vanilla released the profiler span here
		}

		return null;
	}

	public float getTotalThreat() {
		return this.totalThreat / this.threatDivisor;
	}

	public boolean isAllThreatsActive() {
		return this.allThreatsActive;
	}

	public boolean isThreatHigh() {
		return this.getTotalThreat() > this.threatThreshold * 0.5F;
	}

	public boolean isThreatLow() {
		return this.getTotalThreat() > this.threatThreshold * 0.1F;
	}

	public float getThreat(ShipAPI ship) {
		BattleNext_ThreatData data = this.threatMap.get(ship);
		if (data != null && data.isThreat()) {
			return 0.0F;
		} else {
			return data != null ? data.getThreatValue() / this.threatDivisor : 0.0F;
		}
	}

	public boolean isThreatHigh(ShipAPI ship) {
		BattleNext_ThreatData data = this.threatMap.get(ship);
		return data != null ? data.getSystemThreat() / this.threatDivisor > this.threatThreshold * 0.25F : false;
	}

	public BattleNext_ThreatData getThreatData(ShipAPI ship) {
		return ship == null ? null : this.threatMap.get(ship);
	}

	public float getThreatLevel(ShipAPI ship) {
		BattleNext_ThreatData data = this.threatMap.get(ship);
		return data != null ? data.getThreatLevel() : 0.0F;
	}

	public BattleNext_ThreatData getMaxThreatData() {
		BattleNext_ThreatData max = null;

		for (BattleNext_ThreatData data : this.threatMap.values()) {
			if (max == null || max.getThreatValue() < data.getThreatValue()) {
				max = data;
			}
		}

		return max;
	}

	public Collection<BattleNext_ThreatData> getAllThreatData() {
		return this.threatMap.values();
	}

	public boolean isAllThreatsValid() {
		for (BattleNext_ThreatData data : this.getAllThreatData()) {
			if (!data.isThreat()) {
				return false;
			}
		}

		return true;
	}

	public int countInactiveThreats() {
		int count = 0;

		for (BattleNext_ThreatData data : this.getAllThreatData()) {
			if (!data.isThreat()) {
				count++;
			}
		}

		return count;
	}

	public BattleNext_ThreatData getNearestThreatData(Vector2f location) {
		BattleNext_ThreatData nearest = null;
		float nearestDistance = Float.MAX_VALUE;

		for (BattleNext_ThreatData data : this.threatMap.values()) {
			float distance = BattleNext_ShipAIHelper.getDistance(data.getShip().getLocation(), location);
			if (distance < nearestDistance) {
				nearest = data;
				nearestDistance = distance;
			}
		}

		return nearest;
	}

	private boolean isDominantThreat(BattleNext_ThreatData data) {
		if (data == null) {
			return false;
		}

		for (BattleNext_ThreatData other : this.threatMap.values()) {
			if (other != data && other.getThreatValue() * 2.0F >= data.getThreatValue()) {
				return false;
			}
		}

		return true;
	}

	public static float computeFleetStrengthRatio(ShipAPI ship, float range, BattleNext_ThreatEvalAI threatEvalAI) {
		CombatEngineAPI engine = BattleNext_ShipAIHelper.getEngine();
		Vector2f location = ship.getLocation();
		int owner = ship.getOwner();
		List<ShipAPI> ships = engine.getShips();
		float strengthWeight = 0.25F;
		float ownStrength = BattleNext_ShipAIHelper.computeFleetStrength(ship) * strengthWeight;
		float enemyStrength = 0.0F;
		CombatFleetManagerAPI playerFleet = engine.getFleetManager(FleetSide.PLAYER);
		CombatFleetManagerAPI enemyFleet = engine.getFleetManager(FleetSide.ENEMY);
		HashSet<FighterWingAPI> countedWings = new HashSet<>();
		HashSet<DeployedFleetMemberAPI> countedMembers = new HashSet<>();

		for (ShipAPI other : ships) {
			if (!other.isHulk() && other != ship) {
				float distance = BattleNext_ShipAIHelper.getDistance(location, other.getLocation());
				if (!(distance > range)) {
					float threatWeight = 0.25F;
					if (threatEvalAI != null) {
						threatWeight *= threatEvalAI.getThreatLevel(other);
					}

					DeployedFleetMemberAPI member = playerFleet.getDeployedFleetMember(other);
					if (member == null) {
						member = enemyFleet.getDeployedFleetMember(other);
					}

					if (member != null) {
						if (!countedMembers.contains(member)) {
							countedMembers.add(member);
							if (other.getOwner() == owner) {
								ownStrength += BattleNext_ShipAIHelper.getDeployedMemberStrength(member, other) * strengthWeight;
							} else {
								enemyStrength += BattleNext_ShipAIHelper.getDeployedMemberStrength(member, other) * threatWeight;
							}
						}
					} else if (other.isFighter() && !countedWings.contains(other.getWing())) {
						FighterWingAPI wing = other.getWing();
						if (wing != null) {
							countedWings.add(wing);
							float wingFraction = (float) wing.getWingMembers().size() / wing.getSpec().getNumFighters();
							if (other.getOwner() == owner) {
								ownStrength += wing.getSpec().getFleetPoints() * strengthWeight * wingFraction;
							} else {
								enemyStrength += wing.getSpec().getFleetPoints() * threatWeight * wingFraction;
							}
						}
					} else if (other.getOwner() == owner) {
						ownStrength += BattleNext_ShipAIHelper.computeFleetStrength(other) * strengthWeight;
					} else {
						enemyStrength += BattleNext_ShipAIHelper.computeFleetStrength(other) * threatWeight;
					}
				}
			}
		}

		return enemyStrength / (enemyStrength + ownStrength);
	}

	private void computeThreatDivisor(float strengthRatio) {
		if (this.ship.isFighter() && this.ship.getWing() != null) {
			for (ShipAPI wingMember : this.ship.getWing().getWingMembers()) {
				this.threatDivisor = this.threatDivisor + this.computeShipDefense(wingMember, strengthRatio);
			}
		} else {
			this.threatDivisor = this.computeShipDefense(this.ship, strengthRatio);
		}
	}

	private float computeShipDefense(ShipAPI ship, float strengthRatio) {
		float defense = 1.0F;
		if (ship.getShield() != null) {
			defense += (ship.getFluxTracker().getMaxFlux() - ship.getFluxTracker().getCurrFlux()) * 1.0F / ship.getShield().getFluxPerPointOfDamage() * 1.0F;
		}

		defense *= 2.0F;
		defense += ship.getHitpoints();
		float armorTotal = 0.0F;
		ArmorGridAPI grid = ship.getArmorGrid();
		// approximation: the API grid interface has no width/height accessors, dimensions come from getGrid()
		float[][] gridValues = grid.getGrid();
		int columns = gridValues.length;
		int rows = columns > 0 ? gridValues[0].length : 0;

		for (int x = 0; x < columns; x++) {
			for (int y = 0; y < rows; y++) {
				armorTotal += grid.getArmorValue(x, y);
			}
		}

		defense += armorTotal;
		defense *= 5.0E-4F;
		return defense * (2.5F - strengthRatio);
	}

	private void updateThreats(float amount) {
		CombatEngineAPI engine = BattleNext_ShipAIHelper.getEngine();
		int owner = this.ship.getOwner();
		this.threatMap.clear();
		float previousTotal = this.totalThreat;
		this.totalThreat = 0.0F;
		// intentionally kept dead: totalThreat was just zeroed, the branch can never fire
		if (this.totalThreat < 1.0F) {
			this.totalThreat = 0.0F;
		}

		this.allThreatsActive = true;
		Vector2f location = this.ship.getLocation();
		List<ShipAPI> ships = engine.getShips();
		boolean foundThreat = false;

		for (ShipAPI other : ships) {
			if (!other.isHulk() && other.getOwner() != owner) {
				float distance = BattleNext_ShipAIHelper.getDistance(location, other.getLocation());
				if (!(distance > 10000.0F) && (!other.isFighter() || !(distance > 3000.0F)) && (!other.isStationModule() || isActiveModule(other))) {
					BattleNext_ThreatData data = new BattleNext_ThreatData();
					this.fillThreatData(other, data);
					float threatLevel = data.getThreatValue();
					if (threatLevel > 0.0F) {
						this.totalThreat += threatLevel;
						this.threatMap.put(other, data);
						this.allThreatsActive = this.allThreatsActive & data.isThreat();
						foundThreat = true;
					}
				}
			}
		}

		if (!foundThreat) {
			this.allThreatsActive = false;
		}

		// intentionally kept dead: previousTotal is never read afterwards
		if (previousTotal < 1.0F) {
			previousTotal = 0.0F;
		}
	}

	public BattleNext_ThreatData computeThreatData(ShipAPI ship) {
		BattleNext_ThreatData data = new BattleNext_ThreatData();
		this.fillThreatData(ship, data);
		return data;
	}

	private void fillThreatData(ShipAPI ship, BattleNext_ThreatData data) {
		if (this.threatCache.containsKey(ship)) {
			BattleNext_ThreatData cached = this.threatCache.get(ship);
			data.setShipInternal(cached.getShip());
			data.setThreatFlagInternal(cached.isThreat());
			data.setBeamThreatRangeInternal(cached.getBeamThreatRange());
			data.setThreatRangeInternal(cached.getThreatRange());
			data.setSystemThreatInternal(cached.getSystemThreat());
			data.setMinTimeToHit(cached.getMinTimeToHit());
			data.setThreatLevelInternal(cached.getThreatValue());
			data.setMaxDamagePerShot(cached.getMaxDamagePerShot());
		} else {
			this.evaluateShipThreat(ship, data);
		}
	}

	private void evaluateShipThreat(ShipAPI target, BattleNext_ThreatData data) {
		try {
			Vector2f toTarget = Vector2f.sub(this.ship.getLocation(), target.getLocation(), new Vector2f());
			BattleNext_ShipAIHelper.normalise(toTarget);
			float closingSpeed = BattleNext_ShipAIHelper.getClosingSpeed(this.ship, target);
			data.setThreatFlagInternal(true);
			Vector2f shipLocation = this.ship.getLocation();
			float distance = BattleNext_ShipAIHelper.getDistance(shipLocation, target.getLocation());
			float threatScore = 0.0F;
			data.setShipInternal(target);
			float radiusSum = this.ship.getCollisionRadius() + target.getCollisionRadius();
			float explosionRadius = this.ship.getCollisionRadius() + DamagingExplosionSpec.getShipExplosionRadius(target) + 100.0F;
			float evalBonus = 0.0F;
			// approximation: the engine reads BasicShipAI firing estimates on any
			// native ship; ShipAPI exposes only a wrapper, so native estimates are unavailable.
			BattleNext_BasicShipAI targetAI = BattleNext_BasicShipAI.getActiveInstance(target);
			if (targetAI != null) {
				BattleNext_FiringSolutionEval eval = targetAI.getEval();
				evalBonus = (eval.getMaxWeaponDamage() + eval.getHullDamage()) * 2.0F;
			}

			if (!this.ship.isFighter() && !target.isFighter() && target.getHitpoints() <= 300 + 250 * (target.getHullSize().ordinal() - 1) + evalBonus && distance < explosionRadius) {
				threatScore = 4.0F * (target.getHullSize().ordinal() - 1);
				data.setThreatFlagInternal(false);
			}

			float disabledTime = 0.0F;
			if (target.getFluxTracker().isVenting()) {
				disabledTime = target.getFluxTracker().getTimeToVent();
			} else if (target.getFluxTracker().isOverloaded()) {
				disabledTime = target.getFluxTracker().getOverloadTimeRemaining();
			}

			if (disabledTime > 7.0F || target.controlsLocked()) {
				data.setThreatFlagInternal(false);
				data.setThreatLevelInternal(threatScore);
				data.setSystemThreatInternal(threatScore);
				data.setThreatRangeInternal(300.0F);
				data.setMaxDamagePerShot(100.0F);
				this.threatCache.put(target, data);
				return;
			}

			boolean bomberThreat = false;
			if (target.isFighter()
				&& target.getWing() != null
				&& target.getWing().getSpec().isBomber()
				&& !target.getFluxTracker().isOverloadedOrVenting()
				&& BattleNext_ShipAIHelper.isInArc(target.getFacing(), 45.0F, target.getLocation(), this.ship.getLocation())) {
				bomberThreat = true;
			}

			if (distance > 15000.0F) {
				data.setThreatLevelInternal(threatScore);
				data.setSystemThreatInternal(threatScore);
				data.setThreatRangeInternal(radiusSum + 100.0F);
				data.setMaxDamagePerShot(100.0F);
				this.threatCache.put(target, data);
				return;
			}

			if (!BattleNext_ShipAIHelper.areHulksInTheWay(target, this.ship, target.getLocation(), this.ship.getLocation(), true)) {
				float systemThreat = 0.0F;
				boolean frontShield = this.ship.getShield() != null && this.ship.getShield().getType() == ShieldType.FRONT;
				if (target.getSystem() != null && target.getSystem().getCooldownRemaining() < 3.0F) {
					ShipSystemSpecAPI systemSpec = target.getSystem().getSpecAPI();
					float frontBonus = frontShield ? 400 : 0;
					if (systemSpec.getThreatAmount() > 0.0F
						&& distance < systemSpec.getThreatRange(target.getMutableStats()) + radiusSum + frontBonus
						&& BattleNext_ShipAIHelper.isInArc(target.getFacing() + systemSpec.getThreatAngle(), systemSpec.getThreatArc(), target.getLocation(), this.ship.getLocation())) {
						threatScore += systemSpec.getThreatAmount();
						systemThreat += systemSpec.getThreatAmount();
						float systemRange = systemSpec.getThreatRange(target.getMutableStats()) + target.getCollisionRadius();
						if (data.getThreatRange() < systemRange) {
							data.setThreatRangeInternal(systemRange);
							data.setThreatFlagInternal(false);
						}
					}
				}

				boolean interceptorWing = this.ship.isFighter() && this.ship.getWing() != null && this.ship.getWing().getSpec().isInterceptor();
				boolean bigShield = this.ship.getShield() != null && (float) Math.toRadians(this.ship.getShield().getArc()) * this.ship.getShield().getRadius() > 250.0F;
				float beamThreat = 0.0F;
				float beamShieldDamage = 0.0F;

				for (int groupIndex = 0; groupIndex < 7; groupIndex++) {
					WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(target, groupIndex);
					if (group != null) {
						for (WeaponAPI weapon : group.getWeaponsCopy()) {
							float sizeScore = 0.0F;
							switch (getWeaponSizeScores()[weapon.getSize().ordinal()]) {
								case 1:
									sizeScore = 1.0F;
									break;
								case 2:
									sizeScore = 2.0F;
									break;
								case 3:
									sizeScore = 4.0F;
									break;
								default:
									break;
							}

							float damagePerShot = weapon.getDerivedStats().getDamagePerShot() * weapon.getDamageType().getArmorMult();
							if (weapon.isBeam()) {
								damagePerShot = weapon.getDamage().getBaseDamage() * BattleNext_ShipAIHelper.getDpsToHitStrengthMult();
								damagePerShot *= 2.0F;
							}

							if (weapon.hasAIHint(AIHints.STRIKE) || weapon.hasAIHint(AIHints.DANGEROUS)) {
								sizeScore += 4 * (this.ship.getHullSize().ordinal() - 1);
							}

							boolean pdVsFighter = interceptorWing && weapon.hasAIHint(AIHints.PD);
							if (pdVsFighter) {
								sizeScore *= 5.0F;
							}

							float range = weapon.getRange();
							float rangeFactor = 1.0F;
							float aimBonus = 0.0F;
							if (frontShield) {
								rangeFactor = 1.05F;
								if (closingSpeed > 0.0F
									&& (
										!target.isFighter()
											|| target.getWing() == null
											|| target.getWing().getSourceShip() == null
											|| !target.getWing().getSourceShip().isPullBackFighters()
											|| !(distance > 1000.0F + radiusSum)
									)) {
									rangeFactor += 2.0F * closingSpeed / range;
								}

								aimBonus = 30.0F;
							} else {
								rangeFactor = 1.05F;
								aimBonus = 10.0F;
							}

							// approximation: engine isUnphasing() reads the phase cloak charge state; the
							// API exposes the same state through ShipSystemAPI.getState()
							boolean unphasing = target.getPhaseCloak() != null && target.getPhaseCloak().getState() == ShipSystemAPI.SystemState.OUT;
							if (target.isPhased() || unphasing) {
								aimBonus += 20.0F;
							}

							float projectileSpeed = weapon.getProjectileSpeed();
							if (weapon.getSpec() != null && weapon.getSpec().getProjectileSpec() instanceof MissileSpecAPI missileSpec) {
								projectileSpeed = missileSpec.getHullSpec().getEngineSpec().getMaxSpeed();
								MutableShipStatsAPI stats = target.getMutableStats();
								if (stats != null) {
									projectileSpeed = stats.getMissileMaxSpeedBonus().computeEffective(projectileSpeed);
								}
							}

							if (projectileSpeed > 0.0F) {
								float flightTime = distance / projectileSpeed;
								float turnWindow = Math.max(0.0F, 2.0F - flightTime);
								float turnRate = 0.0F;
								if (!weapon.getSlot().isHardpoint() && weapon.getSlot().getArc() > 10.0F) {
									// approximation: vanilla read weapon.getAimTracker().getTurnRate(); the weapon turn rate stands in
									turnRate += weapon.getTurnRate() * turnWindow;
								}

								turnRate += Math.abs(target.getMaxTurnRate()) * turnWindow;
								aimBonus += turnRate;
							}

							if (target.isFighter()) {
								range += 150.0F;
								if (this.ship.getShield() != null && this.ship.getShield().getType() == ShieldType.FRONT) {
									range += 250.0F;
								}
							}

							boolean doNotAim = weapon.hasAIHint(AIHints.DO_NOT_AIM);
							boolean poorAim = weapon.hasAIHint(AIHints.GUIDED_POOR) || doNotAim;
							if (!weapon.isBeam() && (weapon.getType() != WeaponType.MISSILE || !poorAim)) {
								range += weapon.getProjectileFadeRange();
							}

							if (weapon.isBeam()) {
								rangeFactor = 1.0F;
							}

							range *= rangeFactor;
							if (weapon.getType() == WeaponType.MISSILE && !weapon.hasAIHint(AIHints.STRIKE) && !weapon.hasAIHint(AIHints.DANGEROUS)) {
								sizeScore *= 0.25F;
							}

							if ((weapon.getType() != WeaponType.MISSILE || !poorAim) && !weapon.hasAIHint(AIHints.BOMB)) {
								if (range / rangeFactor > data.getThreatRange()) {
									data.setThreatRangeInternal(range / rangeFactor);
								}

								if (weapon.isBeam() && range / rangeFactor > data.getBeamThreatRange()) {
									data.setBeamThreatRangeInternal(range / rangeFactor);
								}
							}

							if (!(distance > range + radiusSum)) {
								systemThreat += sizeScore;
								if (!weapon.usesAmmo() || weapon.getAmmo() != 0 || !(weapon.getSpec().getAmmoPerSecond() <= 0.0F)) {
									if (damagePerShot > data.getMaxDamagePerShot()) {
										data.setMaxDamagePerShot(damagePerShot);
									}

									if (!weapon.isDisabled()) {
										if (poorAim
											&& weapon.getSpec() != null
											&& weapon.getSpec().getProjectileSpec() instanceof MissileSpecAPI missileSpec) {
											float missileSpeed = missileSpec.getHullSpec().getEngineSpec().getMaxSpeed();
											MutableShipStatsAPI stats = target.getMutableStats();
											if (stats != null) {
												missileSpeed = stats.getMissileMaxSpeedBonus().computeEffective(missileSpeed);
											}

											float timeToHit = (distance - target.getCollisionRadius()) / Math.max(1.0F, missileSpeed + closingSpeed);
											timeToHit += weapon.getCooldownRemaining();
											if (timeToHit < 0.0F) {
												timeToHit = 0.0F;
											}

											if (timeToHit < data.getMinTimeToHit()) {
												data.setMinTimeToHit(timeToHit);
											}
										}

										if (!this.ship.isFrigate() && !this.ship.isFighter()
											|| (weapon.getType() != WeaponType.MISSILE || weapon.hasAIHint(AIHints.ANTI_FTR))
												&& (
													!weapon.hasAIHint(AIHints.STRIKE) && !weapon.hasAIHint(AIHints.DANGEROUS)
														|| !this.ship.isFighter() && weapon.hasAIHint(AIHints.USE_VS_FRIGATES)
												)) {
											float angularSize = (float) Math.toDegrees(this.ship.getCollisionRadius() / distance);
											float aimTolerance = 20.0F + angularSize;
											if (pdVsFighter) {
												aimTolerance += 25.0F;
											}

											if ((
													!bigShield
														|| weapon.hasAIHint(AIHints.DO_NOT_AIM)
														|| BattleNext_ShipAIHelper.isInArc(
															weapon.getSlot().computeMidArcAngle(target), weapon.getSlot().getArc() + 15.0F + aimBonus, target.getLocation(), shipLocation
														)
												)
												&& (weapon.hasAIHint(AIHints.DO_NOT_AIM) || BattleNext_ShipAIHelper.isInArc(weapon.getCurrAngle(), aimTolerance + aimBonus, target.getLocation(), shipLocation))) {
												boolean firing = weapon.isFiring();
												if (firing
													&& !weapon.hasAIHint(AIHints.DO_NOT_AIM)
													&& !weapon.hasAIHint(AIHints.GUIDED_POOR)
													&& !BattleNext_ShipAIHelper.isInArc(weapon.getCurrAngle(), angularSize * 2.0F + 10.0F, target.getLocation(), shipLocation)) {
													firing = false;
													sizeScore *= 0.5F;
												}

												if (!firing) {
													sizeScore *= 0.25F;
												}

												threatScore += sizeScore;
												float timeToHit = (distance - target.getCollisionRadius()) / Math.max(1.0F, projectileSpeed + closingSpeed);
												boolean bigMissilePod = weapon.getType() == WeaponType.MISSILE && weapon.getMaxAmmo() >= (weapon.getSize().ordinal() + 1) * 10;
												if (weapon.hasAIHint(AIHints.BOMB)) {
													bigMissilePod = false;
													bomberThreat = false;
												}

												if (weapon.getType() != WeaponType.MISSILE && !weapon.hasAIHint(AIHints.BOMB)
													|| bigMissilePod && range <= 2000.0F
													|| bomberThreat
													|| timeToHit < 1.0F) {
													data.setThreatFlagInternal(false);
												}

												if (weapon.isBeam()) {
													beamThreat += sizeScore;
													beamShieldDamage += weapon.getDamage().getDamage() * weapon.getDamageType().getShieldMult();
												}
											}
										}
									}
								}
							}
						}
					}
				}

				float shieldRegen = 0.0F;
				if (this.ship.getShield() != null
					&& !this.ship.getFluxTracker().isOverloaded()
					&& !this.ship.getFluxTracker().isVenting()
					&& this.ship.getFluxLevel() < 0.5F) {
					shieldRegen = (BattleNext_ShipAIHelper.getDissipationRate(this.ship) - this.ship.getShield().getUpkeep()) * this.ship.getShield().getFluxPerPointOfDamage();
				}

				if (shieldRegen > beamShieldDamage) {
					threatScore -= beamThreat * 0.8F;
				}

				data.setShipInternal(target);
				data.setThreatLevelInternal(threatScore);
				data.setSystemThreatInternal(systemThreat);
				if (data.getThreatRange() < 300.0F) {
					data.setThreatRangeInternal(300.0F);
				}

				this.threatCache.put(target, data);
				return;
			}

			data.setThreatLevelInternal(threatScore);
			data.setSystemThreatInternal(threatScore);
			data.setThreatRangeInternal(radiusSum + 100.0F);
			data.setMaxDamagePerShot(100.0F);
			this.threatCache.put(target, data);
		} finally {
			// intentionally kept empty: vanilla released the profiler span here
		}
	}

	/**
	 * Reconstruction of the engine getWeaponSizeScores() lookup lost in deobfuscation.
	 * The shared runtime enum yields SMALL=0, MEDIUM=1, LARGE=2 and every switch over the
	 * table labels 1/2/3 as reachable, so the table maps each ordinal to ordinal+1.
	 */
	private static int[] getWeaponSizeScores() {
		return new int[] {1, 2, 3};
	}

	/**
	 * Port of engine Ship.isActiveModule(Ship).
	 */
	private static boolean isActiveModule(ShipAPI ship) {
		boolean notAModule = ship.getVariant().getHullSpec().getOrdnancePoints(null) <= 0
			&& ship.getVariant().getWeaponGroups().isEmpty()
			&& ship.getMutableStats().getNumFighterBays().getModifiedValue() <= 0.0F;
		return !notAModule;
	}
}
