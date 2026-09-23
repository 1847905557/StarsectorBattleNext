package data.scripts.ai.vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.PhaseCloakSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.FiringSolutionEval.
 * Estimates incoming projectile/missile/weapon damage against this ship and
 * decides whether the shields should come up.
 * profiler hooks dropped.
 */
public class BattleNext_FiringSolutionEval {
	private ShipAPI ship;
	private IntervalUtil recomputeInterval = new IntervalUtil(0.05F, 0.1F);
	private boolean shieldFacingOk = false;
	private boolean hasSolution = false;
	private float bestTimeToHit = 1000.0F;
	private boolean hasIncomingDamage = false;
	private boolean usingBurstDps = false;
	private boolean useBurstDps = false;
	private boolean alwaysEvaluateDamage = false;
	private boolean assumingFiring = false;
	private boolean assumeFiring = false;
	private boolean nonCombat = false;
	private final ShipwideAIFlags aiFlags;
	private float maxRange = -1.0F;
	private boolean assessingIncomingDamage = false;
	private float maxWeaponDamage = 0.0F;
	private float armorDamage = 0.0F;
	private float hullDamage = 0.0F;
	private float shieldDamage = 0.0F;
	private boolean requiresDamageToReachHull = false;
	private List<CombatEntityAPI> incomingDamageSources = new ArrayList<>();
	private float evaluationWindow = 1.0F;
	private float minTimeToFire = 1000.0F;
	private boolean considerMissiles = false;
	private boolean considerShipScore = false;
	private boolean useWeaponPositions = false;

	public BattleNext_FiringSolutionEval(ShipAPI ship) {
		this(ship, null);
	}

	public BattleNext_FiringSolutionEval(ShipAPI ship, ShipwideAIFlags aiFlags) {
		this(ship, aiFlags, 2500.0F);
	}

	public BattleNext_FiringSolutionEval(ShipAPI ship, ShipwideAIFlags aiFlags, float maxRange) {
		this.maxRange = maxRange;
		this.ship = ship;
		this.aiFlags = aiFlags;
		this.nonCombat = ship.isNonCombat(true);
		this.assumeFiring = true;
	}

	public boolean hasIncomingDamage() {
		// intentionally kept: vanilla returns the burst-DPS flag here, not the incoming-damage flag
		return this.useBurstDps;
	}

	public void setConsiderMissiles(boolean useBurstDps) {
		// intentionally kept: vanilla setter name says missiles but toggles the burst-DPS flag
		this.useBurstDps = useBurstDps;
	}

	public boolean isAssumingFiring() {
		return this.assumingFiring;
	}

	public void setAssumingFiring(boolean assumingFiring) {
		this.assumingFiring = assumingFiring;
	}

	public boolean isUsingBurstDps() {
		return this.usingBurstDps;
	}

	public void setUseBurstDps(boolean usingBurstDps) {
		this.usingBurstDps = usingBurstDps;
	}

	public boolean isAssumeFiring() {
		return this.assumeFiring;
	}

	public void setAssumeFiring(boolean assumeFiring) {
		this.assumeFiring = assumeFiring;
	}

	public boolean hasSolution() {
		return this.hasSolution;
	}

	public boolean isAlwaysEvaluateDamage() {
		return this.alwaysEvaluateDamage;
	}

	public void setAlwaysEvaluateDamage(boolean alwaysEvaluateDamage) {
		this.alwaysEvaluateDamage = alwaysEvaluateDamage;
	}

	public void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector, Vector2f collisionThreatDir) {
		this.advance(amount, threatEvalAI, avoidanceVector, collisionThreatDir, false);
	}

	public void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector, Vector2f collisionThreatDir, boolean reset) {
		this.recomputeInterval.advance(amount);
		if (this.recomputeInterval.intervalElapsed()) {
			this.assessingIncomingDamage = false;
			if (!reset) {
				this.updateShieldState(threatEvalAI, avoidanceVector, collisionThreatDir);
				if (!this.assessingIncomingDamage) {
					float range = 2500.0F;
					if (this.maxRange > 0.0F) {
						range = this.maxRange;
					}

					this.getSolutionScore(range, new BattleNext_FiringSolutionEval.SolutionResult(), 0.0F, 360.0F);
				}
			} else {
				this.resetSolution();
			}
		}
	}

	// intentionally kept: vanilla getter/getter-field pairs are crossed
	// (getMaxWeaponDamage reads the armor damage total, getArmorDamage reads the max per-shot damage)
	public float getMaxWeaponDamage() {
		return this.armorDamage;
	}

	public float getShieldDamage() {
		// intentionally kept: vanilla reads the hull damage total here
		return this.hullDamage;
	}

	public float getHullDamage() {
		// intentionally kept: vanilla reads the shield damage total here
		return this.shieldDamage;
	}

	public float getArmorDamage() {
		return this.maxWeaponDamage;
	}

	public void resetSolution() {
		this.computeSolution(1000000.0F);
	}

	public void computeSolution(float maxTime) {
		BattleNext_FiringSolutionEval.SolutionResult result = new BattleNext_FiringSolutionEval.SolutionResult();
		float range = 2500.0F;
		if (this.maxRange > 0.0F) {
			range = this.maxRange;
		}

		if (this.hasFiringSolution(range, result, 0.0F, 360.0F, maxTime)) {
			this.hasSolution = true;
		} else {
			this.hasSolution = false;
		}

		this.computeIncomingWeaponDamage(0.0F, 360.0F, range, result);
		this.bestTimeToHit = result.minTimeToImpact;
		this.maxWeaponDamage = result.maxDamagePerShot;
		this.armorDamage = result.highExplosiveDamage * DamageType.HIGH_EXPLOSIVE.getArmorMult()
			+ result.energyDamage * DamageType.ENERGY.getArmorMult()
			+ result.beamDamage * DamageType.ENERGY.getArmorMult()
			+ result.fragmentationDamage * DamageType.FRAGMENTATION.getArmorMult()
			+ result.kineticDamage * DamageType.KINETIC.getArmorMult();
		this.armorDamage = this.armorDamage * this.ship.getMutableStats().getArmorDamageTakenMult().getModifiedValue();
		if (this.assumeFiring) {
			this.armorDamage = this.armorDamage + result.empDamage;
		}

		this.hullDamage = result.highExplosiveDamage * DamageType.HIGH_EXPLOSIVE.getHullMult()
			+ result.energyDamage * DamageType.ENERGY.getHullMult()
			+ result.beamDamage * DamageType.ENERGY.getHullMult()
			+ result.fragmentationDamage * DamageType.FRAGMENTATION.getHullMult()
			+ result.kineticDamage * DamageType.KINETIC.getHullMult();
		this.hullDamage = this.hullDamage * this.ship.getMutableStats().getHullDamageTakenMult().getModifiedValue();
		if (this.assumeFiring) {
			this.hullDamage = this.hullDamage + result.empDamage;
		}

		this.shieldDamage = result.highExplosiveDamage * DamageType.HIGH_EXPLOSIVE.getShieldMult()
			+ result.energyDamage * DamageType.ENERGY.getShieldMult()
			+ result.beamDamage * DamageType.ENERGY.getShieldMult()
			+ result.fragmentationDamage * DamageType.FRAGMENTATION.getShieldMult()
			+ result.kineticDamage * DamageType.KINETIC.getShieldMult();
		if (this.ship.getShield() != null) {
			this.shieldDamage = this.shieldDamage * this.ship.getShield().getFluxPerPointOfDamage();
			this.shieldDamage = this.shieldDamage * this.ship.getMutableStats().getShieldDamageTakenMult().getModifiedValue();
		}
	}

	public float getBestTimeToHit() {
		return this.bestTimeToHit;
	}

	public boolean hasDetectedIncomingDamage() {
		return this.hasIncomingDamage;
	}

	public void updateShieldState(BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector, Vector2f collisionThreatDir) {
		if (this.ship.getShield() == null) {
			this.shieldFacingOk = false;
		} else if (this.ship.getFluxLevel() < 0.2F && !this.alwaysEvaluateDamage) {
			this.shieldFacingOk = false;
		} else {
			float shieldFacing = this.ship.getShield().getFacing();
			if (this.ship.getShield().isOff() && this.ship.getShield().getType() == ShieldType.OMNI) {
				if (avoidanceVector != null) {
					shieldFacing = BattleNext_ShipAIHelper.getAngleFast(avoidanceVector);
				} else if (threatEvalAI.getMaxThreatData() != null) {
					shieldFacing = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), threatEvalAI.getMaxThreatData().getShip().getLocation());
				} else {
					if (collisionThreatDir == null) {
						this.shieldFacingOk = false;
						return;
					}

					shieldFacing = BattleNext_ShipAIHelper.getAngleFast(collisionThreatDir);
				}
			} else if (this.ship.getShield().isOff() && this.ship.getShield().getType() == ShieldType.FRONT) {
				if (avoidanceVector == null && threatEvalAI.getMaxThreatData() == null && collisionThreatDir == null) {
					this.shieldFacingOk = false;
					return;
				}

				shieldFacing = this.ship.getFacing();
			}

			float activeArc = this.ship.getShield().getActiveArc();
			if (activeArc < 20.0F) {
				activeArc = 20.0F;
			}

			BattleNext_FiringSolutionEval.SolutionResult result = new BattleNext_FiringSolutionEval.SolutionResult();
			float range = 2500.0F;
			if (this.maxRange > 0.0F) {
				range = this.maxRange;
			}

			if (this.hasFiringSolution(range, result, shieldFacing, activeArc, 100000.0F)) {
				if (!this.usingBurstDps) {
					this.shieldFacingOk = false;
				}

				this.hasSolution = true;
			} else {
				this.hasSolution = false;
				this.bestTimeToHit = result.minTimeToImpact;
				this.computeIncomingWeaponDamage(shieldFacing, activeArc, range, result);
				// approximation: engine ship.getArmorEvaluator().getArmorInArc(facing, arc)
				float armor = this.ship.getAverageArmorInSlice(shieldFacing, activeArc);
				if (this.alwaysEvaluateDamage) {
					float armorTotal = result.highExplosiveDamage * DamageType.HIGH_EXPLOSIVE.getArmorMult()
						+ result.energyDamage * DamageType.ENERGY.getArmorMult()
						+ result.beamDamage * DamageType.ENERGY.getArmorMult()
						+ result.fragmentationDamage * DamageType.FRAGMENTATION.getArmorMult()
						+ result.kineticDamage * DamageType.KINETIC.getArmorMult();
					if (this.assumeFiring) {
						armorTotal += result.empDamage;
					}

					this.armorDamage = armorTotal;
					float hullTotal = result.highExplosiveDamage * DamageType.HIGH_EXPLOSIVE.getHullMult()
						+ result.energyDamage * DamageType.ENERGY.getHullMult()
						+ result.beamDamage * DamageType.ENERGY.getHullMult()
						+ result.fragmentationDamage * DamageType.FRAGMENTATION.getHullMult()
						+ result.kineticDamage * DamageType.KINETIC.getHullMult();
					hullTotal *= this.ship.getMutableStats().getHullDamageTakenMult().getModifiedValue();
					if (this.assumeFiring) {
						hullTotal += result.empDamage;
					}

					this.hullDamage = hullTotal;
				}

				float shieldTotal = result.highExplosiveDamage * DamageType.HIGH_EXPLOSIVE.getShieldMult()
					+ result.energyDamage * DamageType.ENERGY.getShieldMult()
					+ result.beamDamage * DamageType.ENERGY.getShieldMult()
					+ result.fragmentationDamage * DamageType.FRAGMENTATION.getShieldMult()
					+ result.kineticDamage * DamageType.KINETIC.getShieldMult();
				if (this.alwaysEvaluateDamage) {
					this.shieldDamage = shieldTotal;
				}

				shieldTotal *= this.ship.getShield().getFluxPerPointOfDamage();
				shieldTotal *= this.ship.getMutableStats().getShieldDamageTakenMult().getModifiedValue();
				boolean lowHull = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
				float armorFactor = Math.max(0.0F, armor / this.ship.getArmorGrid().getArmorRating() - 0.5F) * 2.0F;
				float fluxLevel = this.ship.getFluxLevel();
				float fluxAvailable = BattleNext_ShipAIHelper.getFluxAvailable(this.ship);
				float ventFlux = (this.ship.getCurrFlux() - this.ship.getMinFlux()) * Math.min(1.0F, 1.0F * (1.0F - this.ship.getFluxLevel()));
				boolean armorFactorBelow02 = armorFactor < 0.2F;
				boolean armorFactorAbove075 = armorFactor > 0.75F;
				boolean armorFactorAbove04 = armorFactor > 0.4F;
				float ventTime;
				if (shieldTotal > 0.0F) {
					ventTime = (fluxAvailable + ventFlux) / shieldTotal;
				} else {
					ventTime = 10000.0F;
				}

				// intentionally kept dead: armorFactorAbove075 and armorFactorAbove04 are never read in vanilla
				if (!lowHull || !armorFactorBelow02 && this.ship.getShield().getType() != ShieldType.FRONT) {
					if (armorFactor - 0.2F < 1.0F - fluxLevel && ventTime > this.ship.getHullSize().ordinal()) {
						this.shieldFacingOk = false;
					} else {
						float hullAfterArmor = result.highExplosiveDamage * DamageType.HIGH_EXPLOSIVE.getHullMult()
							+ result.energyDamage * DamageType.ENERGY.getHullMult()
							+ result.beamDamage * DamageType.ENERGY.getHullMult()
							+ result.fragmentationDamage * DamageType.FRAGMENTATION.getHullMult()
							+ result.kineticDamage * DamageType.KINETIC.getHullMult();
						hullAfterArmor *= this.ship.getMutableStats().getHullDamageTakenMult().getModifiedValue();
						float armorAbsorbed = armorFactor * this.ship.getArmorGrid().getArmorRating();
						hullAfterArmor -= armorAbsorbed;
						hullAfterArmor *= hullAfterArmor / Math.max(1.0F, hullAfterArmor + armorAbsorbed);
						if (hullAfterArmor < 0.0F) {
							hullAfterArmor = 0.0F;
						}

						float timeToDeath = this.ship.getHitpoints() / Math.max(hullAfterArmor, 1.0F);
						float dangerWindow = 2.0F;
						if (this.ship.isFrigate()) {
							dangerWindow = 1.0F;
						}

						if (this.ship.isCapital()) {
							dangerWindow = 3.0F;
						}

						if (this.nonCombat) {
							dangerWindow *= 0.5F;
						}

						if (timeToDeath < 3.0F) {
							dangerWindow = Math.max(dangerWindow * 0.25F, dangerWindow - (3.0F - timeToDeath));
						}

						if (result.burstFireTimeRemaining > 0.0F && result.burstFireTimeRemaining < dangerWindow) {
							dangerWindow = result.burstFireTimeRemaining + 0.25F;
						}

						if (!(ventTime < dangerWindow) && (!this.ship.isFrigate() || !(ventTime < Math.min(dangerWindow, 1.0F)))) {
							this.shieldFacingOk = false;
						} else {
							this.shieldFacingOk = true;
							this.aiFlags.setFlag(AIFlags.NEEDS_HELP);
						}
					}
				} else {
					this.shieldFacingOk = false;
				}
			}
		}
	}

	public boolean isShieldFacingOk() {
		return this.shieldFacingOk;
	}

	public void setRequireDamageToReachHull(boolean requireDamageToReachHull) {
		this.requiresDamageToReachHull = requireDamageToReachHull;
	}

	public boolean hasFiringSolution(float range, BattleNext_FiringSolutionEval.SolutionResult result) {
		return this.hasFiringSolution(range, result, 0.0F, 360.0F, 1000000.0F);
	}

	public boolean hasFiringSolution(float range, BattleNext_FiringSolutionEval.SolutionResult result, float angle, float arc, float maxTime) {
		float total = this.computeIncomingDamage(range, result, angle, arc, maxTime, false);
		if (!this.requiresDamageToReachHull) {
			return total > this.ship.getArmorGrid().getArmorRating() * 0.5F || total > this.ship.getHitpoints() * 0.25F;
		}
		return total > this.ship.getArmorGrid().getArmorRating() * 0.5F && total > this.ship.getHitpoints() * 0.25F
			|| total > this.ship.getArmorGrid().getArmorRating() * 4.0F;
	}

	public List<CombatEntityAPI> getIncomingDamageSources() {
		return this.incomingDamageSources;
	}

	public float getSolutionScore(float range, BattleNext_FiringSolutionEval.SolutionResult result, float angle, float arc) {
		return this.computeIncomingDamage(range, result, angle, arc, 1000000.0F, false);
	}

	public float computeIncomingDamage(float range, BattleNext_FiringSolutionEval.SolutionResult result, float angle, float arc, float maxTime, boolean countKineticTwice) {
		this.incomingDamageSources.clear();
		this.assessingIncomingDamage = true;
		CombatEngineAPI engine = BattleNext_ShipAIHelper.getEngine();
		BattleNext_FiringSolutionEval.SolutionResult totals = new BattleNext_FiringSolutionEval.SolutionResult();
		// approximation: vanilla iterated the combined AI collision grid over this box;
		// the API exposes flat entity lists, so iterate those with an equivalent box filter
		float halfExtent = range;
		Vector2f center = this.ship.getLocation();

		for (MissileAPI missile : engine.getMissiles()) {
			if (Math.abs(missile.getLocation().x - center.x) > halfExtent || Math.abs(missile.getLocation().y - center.y) > halfExtent) {
				continue;
			}

			this.evaluateMissileEntity(missile, missile.getCollisionClass(), missile.getOwner(), missile.getSource(), false,
				range, totals, angle, arc, result, maxTime);
		}

		for (DamagingProjectileAPI projectile : engine.getProjectiles()) {
			if (Math.abs(projectile.getLocation().x - center.x) > halfExtent || Math.abs(projectile.getLocation().y - center.y) > halfExtent) {
				continue;
			}

			this.evaluateProjectileEntity(projectile, projectile.getCollisionClass(), projectile.getOwner(), projectile.getSource(),
				projectile.isFromMissile(), range, totals, angle, arc, result, maxTime);
		}

		for (BeamAPI beam : engine.getBeams()) {
			// approximation: engine MovingRay entities have no API counterpart; the beam origin
			// stands in for the ray location and the source ship for the ray entity
			Vector2f beamPoint = beam.getFrom();
			if (beam.getTo() != null && (Math.abs(beamPoint.x - center.x) > halfExtent || Math.abs(beamPoint.y - center.y) > halfExtent)) {
				beamPoint = beam.getTo();
				if (Math.abs(beamPoint.x - center.x) > halfExtent || Math.abs(beamPoint.y - center.y) > halfExtent) {
					continue;
				}
			}

			if (beam.getSource() == null) {
				continue;
			}

			this.evaluateBeamEntity(beam, CollisionClass.RAY, beam.getSource().getOwner(), beam.getSource(), false,
				range, totals, angle, arc, result, maxTime);
		}

		float total = totals.beamDamage + totals.energyDamage + totals.highExplosiveDamage + totals.fragmentationDamage + totals.kineticDamage;
		if (this.assumeFiring) {
			total += totals.empDamage;
		}

		if (countKineticTwice) {
			total += totals.kineticDamage;
		}

		if (total > 0.0F) {
			if (this.aiFlags != null) {
				this.aiFlags.setFlag(AIFlags.HAS_INCOMING_DAMAGE);
			}

			this.hasIncomingDamage = true;
		} else {
			if (this.aiFlags != null) {
				this.aiFlags.removeFlag(AIFlags.HAS_INCOMING_DAMAGE);
			}

			this.hasIncomingDamage = false;
		}

		return total;
	}

	/**
	 * Shared threat-relevance filter and missile accumulation path for one candidate entity.
	 * Mirrors the per-iteration logic of vanilla computeIncomingDamage.
	 */
	private void evaluateMissileEntity(MissileAPI missile, CollisionClass collisionClass, int owner, ShipAPI source, boolean fromMissile,
		float range, BattleNext_FiringSolutionEval.SolutionResult totals, float angle, float arc,
		BattleNext_FiringSolutionEval.SolutionResult result, float maxTime) {
		if (!this.isThreatEntity(collisionClass, owner, source, missile != null, missile != null && missile.isFizzling(),
			missile != null && missile.isMine(), missile != null && missile.isNoMineFFConcerns())) {
			return;
		}

		boolean targetsThisShip = false;
		boolean aggressiveSeeker = false;
		if (missile.getMissileAI() instanceof GuidedMissileAI) {
			GuidedMissileAI guidedAI = (GuidedMissileAI) missile.getMissileAI();
			targetsThisShip = guidedAI.getTarget() == this.ship;
			// approximation: engine compared the engine MissileType enum; the API spec only exposes the type string
			aggressiveSeeker = "HEATSEEKER".equals(missile.getSpec().getTypeString());
			// approximation: engine read effective engine-controller stats; use the missile flight stats instead
			if (missile.getMaxTurnRate() >= 180.0F
				&& missile.getAcceleration() >= missile.getMaxSpeed()
				&& missile.getMaxSpeed() >= 100.0F) {
				aggressiveSeeker = true;
			}
		}

		this.accumulateDamage(missile, missile.getLocation(), missile.getVelocity(), missile.getDamage(),
			range, totals, angle, arc, result, maxTime, aggressiveSeeker && targetsThisShip);
	}

	private void evaluateProjectileEntity(DamagingProjectileAPI projectile, CollisionClass collisionClass, int owner, ShipAPI source, boolean fromMissile,
		float range, BattleNext_FiringSolutionEval.SolutionResult totals, float angle, float arc,
		BattleNext_FiringSolutionEval.SolutionResult result, float maxTime) {
		if (!this.isThreatEntity(collisionClass, owner, source, false, false, false, false)) {
			return;
		}

		DamageAPI damage = projectile.getDamage();
		if (damage.isDps()) {
			return;
		}

		float armorDamageValue = damage.getDamage() * damage.getType().getArmorMult();
		if (fromMissile || armorDamageValue > this.ship.getHitpoints() * 0.25F || armorDamageValue > this.ship.getMaxHitpoints() * 0.05F) {
			this.accumulateDamage(projectile, projectile.getLocation(), projectile.getVelocity(), damage,
				range, totals, angle, arc, result, maxTime, false);
		}
	}

	private void evaluateBeamEntity(BeamAPI beam, CollisionClass collisionClass, int owner, ShipAPI source, boolean fromMissile,
		float range, BattleNext_FiringSolutionEval.SolutionResult totals, float angle, float arc,
		BattleNext_FiringSolutionEval.SolutionResult result, float maxTime) {
		if (!this.isThreatEntity(collisionClass, owner, source, false, false, false, false)) {
			return;
		}

		DamageAPI damage = beam.getDamage();
		if (damage.isDps()) {
			return;
		}

		float armorDamageValue = damage.getDamage() * damage.getType().getArmorMult();
		if (fromMissile || armorDamageValue > this.ship.getHitpoints() * 0.25F || armorDamageValue > this.ship.getMaxHitpoints() * 0.05F) {
			// approximation: vanilla passed the MovingRay itself; the source ship stands in for it
			this.accumulateDamage(source, beam.getFrom(), new Vector2f(0.0F, 0.0F), damage,
				range, totals, angle, arc, result, maxTime, false);
		}
	}

	/**
	 * Port of the owner/source/mine filter at the top of the vanilla iteration body.
	 */
	private boolean isThreatEntity(CollisionClass collisionClass, int owner, ShipAPI source,
		boolean isMissile, boolean isFizzling, boolean isMine, boolean noMineFFConcerns) {
		boolean sameOwnerAsOriginal = owner == this.ship.getOriginalOwner();
		boolean threat = !sameOwnerAsOriginal;
		if (collisionClass != null
			&& sameOwnerAsOriginal
			&& collisionClass != CollisionClass.RAY_FIGHTER
			&& collisionClass != CollisionClass.PROJECTILE_FIGHTER
			&& collisionClass != CollisionClass.MISSILE_NO_FF
			&& collisionClass != CollisionClass.PROJECTILE_NO_FF) {
			threat = true;
		}

		if (this.ship == source && (!isMissile || !isFizzling)) {
			threat = false;
		}

		if (isMissile && isMine && !noMineFFConcerns) {
			threat = true;
		}

		return threat;
	}

	private void accumulateDamage(
		CombatEntityAPI source,
		Vector2f location,
		Vector2f velocity,
		DamageAPI damage,
		float maxTime,
		BattleNext_FiringSolutionEval.SolutionResult totals,
		float angle,
		float arc,
		BattleNext_FiringSolutionEval.SolutionResult result,
		float mineTime,
		boolean guidedTarget
	) {
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), location);
		MissileAPI missile = null;
		if (source instanceof MissileAPI) {
			missile = (MissileAPI) source;
		}

		if (distance > maxTime) {
			return;
		}

		if (arc < 360.0F && !BattleNext_ShipAIHelper.isInArc(angle, arc, this.ship.getLocation(), location)) {
			return;
		}

		boolean isMine = false;
		boolean minePrimed = false;
		float untilMineExplosion = 0.0F;
		float mineExplosionRange = 0.0F;
		if (missile != null && missile.isMine()) {
			isMine = missile.isMine();
			if (isMine && missile.isNoMineFFConcerns() && missile.getOwner() == this.ship.getOwner()) {
				return;
			}

			minePrimed = missile.isMinePrimed();
			if (isMine && this.ship.isPhased()) {
				minePrimed = true;
			}

			untilMineExplosion = missile.getUntilMineExplosion();
			boolean potentialMineTrigger = this.ship.getAIFlags().hasFlag(AIFlags.HAS_POTENTIAL_MINE_TRIGGER_NEARBY);
			if (potentialMineTrigger) {
				untilMineExplosion = 0.01F;
				minePrimed = true;
			}

			mineExplosionRange = missile.getMineExplosionRange();
			mineExplosionRange += Math.max(50.0F, mineExplosionRange * 0.25F);
		}

		float angleToSource = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), location);
		// approximation: engine ship.getArmorEvaluator().getArmorInArc(facing, arc)
		float armor = this.ship.getAverageArmorInSlice(angleToSource, 30.0F);
		Vector2f offset = Vector2f.sub(this.ship.getLocation(), location, new Vector2f());
		BattleNext_ShipAIHelper.normalise(offset);
		Vector2f relativeVelocity = Vector2f.sub(velocity, this.ship.getVelocity(), new Vector2f());
		float closingSpeed = Vector2f.dot(offset, relativeVelocity);
		if (guidedTarget) {
			closingSpeed = Math.max(closingSpeed, velocity.length() * 0.5F);
		}

		float collisionRadius = this.ship.getCollisionRadius();
		if (isMine && distance < collisionRadius + mineExplosionRange && minePrimed && untilMineExplosion <= mineTime) {
			float totalRange = collisionRadius + mineExplosionRange;
			closingSpeed = Math.max(100.0F, distance - totalRange) / Math.max(0.1F, untilMineExplosion);
		}

		float radiusFactor = 0.8F;
		radiusFactor = 1.0F;
		// intentionally kept dead: vanilla overwrites the 0.8 initializer with 1.0 immediately
		if (closingSpeed > 1.0F || distance <= this.ship.getCollisionRadius() * radiusFactor) {
			if (distance < this.ship.getCollisionRadius() * radiusFactor) {
				closingSpeed = this.ship.getCollisionRadius();
			}

			float timeToImpact = Math.max(0.0F, distance - this.ship.getCollisionRadius() * radiusFactor) / closingSpeed;
			boolean negativeEta = source instanceof MissileAPI && ((MissileAPI) source).getEtaModifier() < 0.0F;
			float etaModifier = 0.0F;
			if (source instanceof MissileAPI) {
				etaModifier = ((MissileAPI) source).getEtaModifier();
				timeToImpact += etaModifier;
			}

			if (timeToImpact <= maxTime) {
				if (!guidedTarget && !negativeEta) {
					Vector2f projected = Vector2f.add(location, relativeVelocity, new Vector2f(location));
					float segmentDistance = distanceToSegment(location, projected, this.ship.getLocation());
					if (segmentDistance > this.ship.getCollisionRadius() * 1.25F) {
						return;
					}
				}

				if (timeToImpact < result.minTimeToImpact) {
					result.minTimeToImpact = timeToImpact;
				}

				float travelTime = distance / closingSpeed;
				travelTime += etaModifier;
				if (travelTime < 1.0F) {
					travelTime = 1.0F;
				}

				float damageFactor = 1.0F - Math.min(1.0F, travelTime / 5.0F);
				if (etaModifier < 0.0F) {
					damageFactor = 0.5F + 0.5F * damageFactor;
				}

				float armorDamage = damage.getDamage();
				float perShotDamage = damage.getDamage();
				float fluxComponent = damage.getFluxComponent();
				armorDamage *= damageFactor;
				armorDamage *= damage.getType().getArmorMult();
				armorDamage *= computeDamageMultiplier(armorDamage, armor);
				fluxComponent *= damageFactor;
				armorDamage += fluxComponent * 1.0F;
				if (damage.getType() == DamageType.KINETIC) {
					if (travelTime < 1.0F) {
						travelTime = 1.0F;
					}
				} else if (travelTime < 3.0F) {
					travelTime = 3.0F;
				}

				perShotDamage /= travelTime;
				if (armorDamage > 500.0F && source != null) {
					this.incomingDamageSources.add(source);
				}

				switch (damage.getType()) {
					case KINETIC:
						totals.kineticDamage += armorDamage;
						result.kineticDamage += perShotDamage;
						break;
					case HIGH_EXPLOSIVE:
						totals.highExplosiveDamage += armorDamage;
						result.highExplosiveDamage += perShotDamage;
						break;
					case FRAGMENTATION:
						totals.fragmentationDamage += armorDamage;
						result.fragmentationDamage += perShotDamage;
						break;
					case ENERGY:
						totals.energyDamage += armorDamage;
						result.energyDamage += perShotDamage;
						break;
					default:
						break;
				}

				totals.empDamage += fluxComponent;
			}
		}
	}

	public void setEvaluationWindow(float evaluationWindow) {
		this.evaluationWindow = evaluationWindow;
	}

	public float getMinTimeToFire() {
		return this.minTimeToFire;
	}

	public void setUseWeaponPositions(boolean considerMissiles) {
		// intentionally kept: vanilla setter name says weapon positions but toggles missile consideration
		this.considerMissiles = considerMissiles;
	}

	public void setConsiderShipScore(boolean considerShipScore) {
		this.considerShipScore = considerShipScore;
	}

	public void setEvaluateFromWeaponPositions(boolean useWeaponPositions) {
		this.useWeaponPositions = useWeaponPositions;
	}

	public void computeIncomingWeaponDamage(float angle, float arc, float range, BattleNext_FiringSolutionEval.SolutionResult result) {
		CombatEngineAPI engine = BattleNext_ShipAIHelper.getEngine();
		int owner = this.ship.getOwner();
		this.minTimeToFire = 1000.0F;
		float window = this.evaluationWindow;
		ShipSystemAPI system = this.ship.getPhaseCloak();
		if (system == null) {
			system = this.ship.getSystem();
		}

		if (system instanceof PhaseCloakSystemAPI) {
			window = system.getCooldown();
			if (!system.isOn()) {
				window *= 0.5F;
			}
		}

		if (window <= 0.0F) {
			return;
		}

		Vector2f shipLocation = this.ship.getLocation();

		for (ShipAPI other : engine.getShips()) {
			if (other.isHulk() || other.getOwner() == owner) {
				continue;
			}

			float disabledTime = 0.0F;
			if (other.getFluxTracker().isVenting()) {
				disabledTime = other.getFluxTracker().getTimeToVent();
			} else if (other.getFluxTracker().isOverloaded()) {
				disabledTime = other.getFluxTracker().getOverloadTimeRemaining();
			}

			float distance = BattleNext_ShipAIHelper.getDistance(shipLocation, other.getLocation());
			if (distance > range) {
				continue;
			}

			float radiusSum = this.ship.getCollisionRadius() + other.getCollisionRadius();
			float angularSize = 2.0F * this.ship.getCollisionRadius() / ((float) (Math.PI * 2) * distance) * 360.0F;
			if (disabledTime > window) {
				continue;
			}

			float shipScore = BattleNext_ShipAIHelper.getShipScore(this.ship, other, window);
			float scoreCushion = Math.max(0.0F, shipScore * window);
			if (!this.considerShipScore) {
				scoreCushion = 0.0F;
			}

			// approximation: vanilla built engine WeaponGroup containers; flat per-group weapon lists are used instead
			List<List<WeaponAPI>> weaponGroups = new ArrayList<>();
			for (WeaponGroupAPI group : other.getWeaponGroupsCopy()) {
				weaponGroups.add(group.getWeaponsCopy());
			}

			ShipSystemAPI otherSystem = other.getSystem();
			if (otherSystem != null) {
				if (otherSystem.getSpecAPI().getThreatAmount() > 0.0F) {
					boolean includeSystem = true;
					boolean threatOnlyWhenCloseToActive = false;
					// approximation: engine spec AI hints are not exposed by the API, assume no restriction to the active state
					if (threatOnlyWhenCloseToActive && otherSystem.getState() == SystemState.IN) {
						float effectLevel = otherSystem.getEffectLevel();
						float chargeUpLeft = otherSystem.getChargeUpDur() * (1.0F - effectLevel);
						includeSystem = chargeUpLeft < 0.5F;
					}

					if (includeSystem) {
						// approximation: vanilla reused the system's own weapon modules when the system is an
						// engine WeaponSystem; the API cannot enumerate those, so the system is always evaluated
						// as one fake weapon
						List<WeaponAPI> systemGroup = new ArrayList<>();
						systemGroup.add(new BattleNext_FakeWeapon(other, otherSystem));
						weaponGroups.add(systemGroup);
					}
				}
			}

			for (List<WeaponAPI> group : weaponGroups) {
				if (group == null) {
					continue;
				}

				int weaponIndex = 0;

				for (WeaponAPI weapon : group) {
					// intentionally kept dead: vanilla increments a per-group weapon counter it never reads
					weaponIndex++;
					if ((weapon.isFiring() || this.assumingFiring)
						&& (weapon.getType() != WeaponType.MISSILE || this.considerMissiles)
						&& (
							this.considerMissiles
								|| !weapon.hasAIHint(AIHints.BOMB)
								|| !weapon.hasAIHint(AIHints.STRIKE) && !weapon.hasAIHint(AIHints.DANGEROUS)
						)
						&& (!weapon.usesAmmo() || weapon.getAmmo() != 0 || !(weapon.getAmmoTracker().getAmmoPerSecond() <= 0.0F))
						&& !weapon.isDisabled()) {
						float weaponRange = weapon.getRange();
						if (!weapon.isBeam()) {
							weaponRange += 150.0F;
						}

						Vector2f weaponLocation = null;
						if (this.useWeaponPositions) {
							weaponLocation = weapon.getSlot().computePosition(other);
							float weaponDistance = BattleNext_ShipAIHelper.getDistance(weaponLocation, this.ship.getLocation());
							if (weaponDistance + scoreCushion > weaponRange + radiusSum) {
								continue;
							}
						} else if (distance + scoreCushion > weaponRange + radiusSum) {
							continue;
						}

						if (weaponLocation == null) {
							weaponLocation = weapon.getSlot().computePosition(other);
						}

						boolean doNotAim = weapon.hasAIHint(AIHints.DO_NOT_AIM);
						boolean poorAim = weapon.hasAIHint(AIHints.GUIDED_POOR) || doNotAim;
						float turnRateSum = 0.0F;
						float turnRateArc = 0.0F;
						float damageWindowFactor = 1.0F;
						float timeToFire = 1000.0F;
						if (this.assumingFiring || weapon.isFiring()) {
							float windowLeft = window;
							float projectileSpeed = weapon.getProjectileSpeed();
							if (weapon.getSpec() != null && weapon.getSpec().getProjectileSpec() instanceof MissileSpecAPI) {
								MissileSpecAPI missileSpec = (MissileSpecAPI) weapon.getSpec().getProjectileSpec();
								projectileSpeed = missileSpec.getHullSpec().getEngineSpec().getMaxSpeed();
								MutableShipStatsAPI stats = other.getMutableStats();
								if (stats != null) {
									projectileSpeed = stats.getMissileMaxSpeedBonus().computeEffective(projectileSpeed);
								}

								projectileSpeed *= 0.75F;
								if (weapon.hasAIHint(AIHints.HEATSEEKER)) {
									projectileSpeed *= 0.67F;
								}
							}

							float flightTime = distance / projectileSpeed;
							if (windowLeft > 0.0F && !weapon.isFiring()) {
								damageWindowFactor = 1.0F - flightTime / windowLeft;
								if (damageWindowFactor < 0.0F) {
									damageWindowFactor = 0.0F;
								}

								windowLeft -= flightTime;
								if (windowLeft < 0.0F) {
									windowLeft = 0.0F;
								}
							}

							// approximation: vanilla read weapon.getAimTracker().getTurnRate(); the API only
							// exposes the weapon turn rate, which stands in for it
							turnRateSum += other.getMaxTurnRate() * windowLeft + weapon.getTurnRate() * windowLeft;
							turnRateSum *= 0.5F;
							turnRateArc += other.getMaxTurnRate() * windowLeft;
							float cooldown = weapon.getCooldown();
							float cooldownElapsed = cooldown - weapon.getCooldownRemaining();
							if (weapon.getCooldownRemaining() > windowLeft && cooldownElapsed > flightTime * 1.25F) {
								continue;
							}

							if (cooldownElapsed > flightTime) {
								timeToFire = flightTime + Math.max(disabledTime, weapon.getCooldownRemaining());
							} else if (poorAim) {
								timeToFire = Math.max(0.0F, flightTime - cooldownElapsed);
							} else if (weapon.isFiring()) {
								timeToFire = 0.0F;
							} else if (weapon.getCooldownRemaining() < flightTime) {
								timeToFire = flightTime - weapon.getCooldownRemaining();
							} else {
								timeToFire = flightTime + disabledTime;
							}
						}

						if ((poorAim || BattleNext_ShipAIHelper.isInArc(weapon.getCurrAngle(), angularSize + turnRateSum, weaponLocation, shipLocation))
							&& (poorAim || BattleNext_ShipAIHelper.isInArc(weapon.getSlot().computeMidArcAngle(other), weapon.getArc() + angularSize + turnRateArc, weaponLocation, shipLocation))
							&& (poorAim || BattleNext_ShipAIHelper.isInArc(angle, arc + angularSize + turnRateArc, shipLocation, weaponLocation))) {
							if (timeToFire < this.minTimeToFire) {
								this.minTimeToFire = timeToFire;
							}

							float sustainedDps = weapon.getDerivedStats().getSustainedDps();
							float empPerSecond = weapon.getDerivedStats().getEmpPerSecond();
							float damagePerShot = weapon.getDerivedStats().getDamagePerShot();
							if (this.useBurstDps) {
								float burstWindow = window;
								float burstDamage = weapon.getDerivedStats().getBurstDamage();
								float burstDuration = weapon.getDerivedStats().getBurstFireDuration();
								if (burstDamage <= 0.0F) {
									burstDuration = Math.min(1.0F / weapon.getDerivedStats().getRoF(), burstWindow);
									if (burstDuration <= 0.05F) {
										burstDuration = 0.05F;
									}

									burstDamage = weapon.getDerivedStats().getDamagePerShot();
								}

								if (burstDuration > burstWindow * 2.0F) {
									burstDuration = burstWindow * 2.0F;
								}

								if (burstDamage > 0.0F && burstDuration > 0.0F) {
									sustainedDps = burstDamage / burstDuration;
								}
							}

							if (sustainedDps * damageWindowFactor > damagePerShot) {
								sustainedDps *= damageWindowFactor;
								empPerSecond *= damageWindowFactor;
							}

							if (damagePerShot > result.maxDamagePerShot) {
								result.maxDamagePerShot = damagePerShot;
							}

							if (weapon.isBeam()) {
								result.minTimeToImpact = 0.0F;
							} else if (timeToFire < result.minTimeToImpact) {
								result.minTimeToImpact = timeToFire;
							}

							switch (weapon.getDamageType()) {
								case KINETIC:
									result.kineticDamage += sustainedDps;
									break;
								case HIGH_EXPLOSIVE:
									result.highExplosiveDamage += sustainedDps;
									break;
								case FRAGMENTATION:
									result.fragmentationDamage += sustainedDps;
									break;
								case ENERGY:
									if (weapon.isBeam()) {
										result.beamDamage += sustainedDps;
									} else {
										result.energyDamage += sustainedDps;
									}
									break;
								default:
									break;
							}

							result.empDamage += empPerSecond;
							if (weapon.getSpec() != null && weapon.getSpec().hasTag("fires_one_burst") && weapon.isFiring()) {
								float burstRemaining = weapon.getBurstFireTimeRemaining();
								result.burstFireTimeRemaining = Math.max(result.burstFireTimeRemaining, burstRemaining);
							}
						}
					}
				}
			}
		}
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
	 * Port of com.fs.util.VectorMathUtils.distanceToSegment(a, b, point).
	 */
	private static float distanceToSegment(Vector2f a, Vector2f b, Vector2f point) {
		float dx = b.x - a.x;
		float dy = b.y - a.y;
		float lengthSquared = dx * dx + dy * dy;
		float t = 0.0F;
		if (lengthSquared > 0.0F) {
			t = ((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared;
		}

		if (t < 0.0F) {
			t = 0.0F;
		} else if (t > 1.0F) {
			t = 1.0F;
		}

		float closestX = a.x + t * dx;
		float closestY = a.y + t * dy;
		float outX = point.x - closestX;
		float outY = point.y - closestY;
		return (float) Math.sqrt(outX * outX + outY * outY);
	}

	/**
	 * Port of engine class FiringSolutionEval.o, the per-solution damage accumulator.
	 */
	/**
	 * Pure data holder for the best firing solution; public fields are allowed
	 * by the project PublicData rule for data types.
	 */
	@data.scripts.battlesnext.util.annotation.PublicData
	public static class SolutionResult {
		private float maxDamagePerShot;
		private float kineticDamage;
		private float fragmentationDamage;
		private float highExplosiveDamage;
		private float energyDamage;
		private float beamDamage;
		private float empDamage;
		private float burstFireTimeRemaining;
		private float minTimeToImpact = 1000.0F;
	}
}
