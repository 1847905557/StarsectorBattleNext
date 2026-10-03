package data.scripts.ai.vanilla.shields;

import com.fs.starfarer.api.combat.FogOfWarAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import data.scripts.ai.vanilla.BattleNext_FiringSolutionEval;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.collisions.BattleNext_CollisionAnalysisModule;
import data.scripts.tools.BattleNext_Logger;
import org.lwjgl.util.vector.Vector2f;

// profiler hooks dropped
public class BattleNext_OmniShieldAngleAI implements BattleNext_ShieldAI {
	private static final float unused = 3.0F;
	private float shieldRaiseDelay = 3.0F;
	private float timeSinceShieldToggle = 0.0F;
	private ShipAPI ship;
	private final ShipwideAIFlags aiFlags;
	private BattleNext_FiringSolutionEval eval;
	private float shieldUnfoldTime;
	// set when this AI is driven by the external shield control AI (OmniShieldControlAI)
	private boolean drivenByShieldControlAI = false;
	private boolean shieldsOn = false;
	private BattleNext_CollisionAnalysisModule collisionAnalysisModule = null;
	private float timeSinceShieldHit = 3.0F;

	public BattleNext_OmniShieldAngleAI(ShipAPI ship, ShipwideAIFlags aiFlags) {
		this(ship, aiFlags, false);
	}

	public BattleNext_OmniShieldAngleAI(ShipAPI ship, ShipwideAIFlags aiFlags, boolean drivenByShieldControlAI) {
		this.ship = ship;
		this.aiFlags = aiFlags;
		this.drivenByShieldControlAI = drivenByShieldControlAI;
		// engine and API HullSize ordinals are identical (both enums carry DEFAULT)
		this.shieldRaiseDelay = ship.getHullSize().ordinal() * 1.0F;
		this.eval = new BattleNext_FiringSolutionEval(ship, aiFlags);
		this.eval.setRequireDamageToReachHull(true);
		// engine reads the shield charge tracker, whose charge time is seeded from getUnfoldTime()
		this.shieldUnfoldTime = ship.getShield().getUnfoldTime();
	}

	@Override
	public boolean shouldKeepShieldOn() {
		return this.shieldsOn;
	}

	public void setCollisionAnalysisModule(BattleNext_CollisionAnalysisModule collisionAnalysisModule) {
		this.collisionAnalysisModule = collisionAnalysisModule;
	}

	@Override
	public void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector, Vector2f collisionThreatDir, ShipAPI target) {

		if (this.ship.getFluxTracker().isOverloadedOrVenting()) {
			this.timeSinceShieldToggle = 0.0F;
			this.timeSinceShieldHit = 0.0F;
			this.shieldsOn = false;
		} else if (this.aiFlags.hasFlag(AIFlags.DO_NOT_USE_SHIELDS)) {
			if (this.ship.getShield().isOn()) {
				this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
				this.shieldsOn = false;
			}
		} else {
			this.eval.advance(amount, threatEvalAI, avoidanceVector, collisionThreatDir);
			if (this.eval.isShieldFacingOk() && this.ship.getShield().isOn()) {
				this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
				this.shieldsOn = false;
				this.timeSinceShieldToggle = 0.0F;
			} else {
				boolean keepShieldsOn = false;
				if (this.ship.getShield().isOn() && this.aiFlags.hasFlag(AIFlags.KEEP_SHIELDS_ON)) {
					keepShieldsOn = true;
				}

				if (this.aiFlags.hasFlag(AIFlags.DO_NOT_USE_FLUX)) {
					if (this.ship.getShield().isOn() && !keepShieldsOn) {
						this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
						this.shieldsOn = false;
					}
				} else {
					if (avoidanceVector != null && this.collisionAnalysisModule != null && this.collisionAnalysisModule.hasCollision() && threatEvalAI.getTotalThreat() > 0.0F) {
						avoidanceVector = null;
					}

					boolean shouldRaiseShield = threatEvalAI.getTotalThreat() > 0.0F && !threatEvalAI.isAllThreatsValid()
						|| avoidanceVector != null
						|| this.ship.getSinceLastDamageTaken() <= 0.5F
						|| collisionThreatDir != null
						|| this.eval.hasDetectedIncomingDamage() && this.eval.getBestTimeToHit() < this.shieldUnfoldTime + 1.0F;
					if (this.drivenByShieldControlAI && collisionThreatDir == null && avoidanceVector == null) {
						shouldRaiseShield = true;
					}

					if (!shouldRaiseShield && this.ship.getShield().isOn() && !keepShieldsOn) {
						this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
						this.shieldsOn = false;
						this.timeSinceShieldToggle = 0.0F;
					} else {
						ShipAPI threatShip = null;
						if (threatEvalAI.getMaxThreatData() != null) {
							threatShip = threatEvalAI.getMaxThreatData().getShip();
							float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), threatShip.getLocation());
							float combinedRadius = this.ship.getCollisionRadius() + threatShip.getCollisionRadius();
							if (threatEvalAI.getMaxThreatData().getThreatRange() + combinedRadius + 100.0F < distance) {
								float bestThreatValue = 0.0F;
								BattleNext_ThreatData fallbackThreatData = null;

								for (BattleNext_ThreatData threatData : threatEvalAI.getAllThreatData()) {
									if (!threatData.isThreat()) {
										distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), threatData.getShip().getLocation());
										combinedRadius = this.ship.getCollisionRadius() + threatData.getShip().getCollisionRadius();
										if (!(threatData.getThreatRange() + combinedRadius + 100.0F < distance) && threatData.getThreatValue() > bestThreatValue) {
											bestThreatValue = threatData.getThreatValue();
											threatShip = threatData.getShip();
											fallbackThreatData = threatData;
										}
									}
								}

								if (fallbackThreatData != null) {
									distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), threatShip.getLocation());
									combinedRadius = this.ship.getCollisionRadius() + threatShip.getCollisionRadius();
									if (fallbackThreatData.getThreatRange() + combinedRadius + 100.0F < distance) {
										threatShip = null;
										shouldRaiseShield = false;
									}
								}
							}
						}

						boolean phasedThreatNear = false;
						float threatDistance = 10.0F;
						if (threatShip != null) {
							threatDistance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), threatShip.getLocation());
							if (threatShip.isPhased()) {
								float phasedRadius = this.ship.getCollisionRadius() + threatShip.getCollisionRadius();
								phasedRadius *= 0.75F;
								phasedThreatNear = threatShip.isFrigate() && threatDistance - phasedRadius < 700.0F;
								phasedThreatNear |= threatShip.isDestroyer() && threatDistance - phasedRadius < 400.0F;
							}
						}

						boolean threatVisible = false;
						if (this.drivenByShieldControlAI && threatShip != null) {
							FogOfWarAPI fogOfWar = BattleNext_ShipAIHelper.getEngine().getFogOfWar(this.ship.getOriginalOwner());
							threatVisible = fogOfWar.isVisible(threatShip);
						}

						boolean debug = false;
						// intentionally kept dead: vanilla debug flag is always false
						if (this.ship.getSinceLastDamageTaken() < 1.0F || BattleNext_ShipAIHelper.shieldWasHit(this.ship)) {
							this.timeSinceShieldHit = 0.0F;
						}

						float energyFraction = BattleNext_ShipAIHelper.getFluxAvailable(this.ship) / this.ship.getMaxFlux();
						boolean okToRaise = false;
						if (!this.eval.isShieldFacingOk()
							&& (energyFraction > 0.2F || this.ship.getShield().isOn() || shouldRaiseShield || phasedThreatNear || avoidanceVector != null && this.eval.hasSolution() || collisionThreatDir != null)) {
							if (debug) {
								BattleNext_Logger.info(BattleNext_OmniShieldAngleAI.class, "shield.raise.allowed",
				BattleNext_Logger.fields().put("ship_id", this.ship.getId()).put("shield_on", this.ship.getShield().isOn()).put("flux", this.ship.getFluxLevel()).put("debug", debug).build());
							}

							okToRaise = true;
						}

						if (!shouldRaiseShield && avoidanceVector == null && collisionThreatDir == null) {
							if (debug) {
								BattleNext_Logger.info(BattleNext_OmniShieldAngleAI.class, "shield.raise.no_threat",
				BattleNext_Logger.fields().put("ship_id", this.ship.getId()).put("shield_on", this.ship.getShield().isOn()).put("flux", this.ship.getFluxLevel()).put("debug", debug).build());
							}

							okToRaise = false;
						}

						boolean fluxTooHigh = false;
						if (energyFraction < 0.5F) {
							this.timeSinceShieldToggle += amount;
							if (threatShip != null) {
								float energy = BattleNext_ShipAIHelper.getFluxAvailable(this.ship);
								float maxFlux = this.ship.getMaxFlux();
								float fluxThreshold = 0.2F;
								if (avoidanceVector != null) {
									fluxThreshold = 0.1F;
								}

								if (energy < maxFlux * fluxThreshold && this.timeSinceShieldToggle > this.shieldRaiseDelay) {
									okToRaise = false;
									fluxTooHigh = true;
									if (debug) {
										BattleNext_Logger.info(BattleNext_OmniShieldAngleAI.class, "shield.raise.flux_rejected",
				BattleNext_Logger.fields().put("ship_id", this.ship.getId()).put("shield_on", this.ship.getShield().isOn()).put("flux", this.ship.getFluxLevel()).put("debug", debug).build());
									}
								}
							}
						} else {
							this.timeSinceShieldToggle = 0.0F;
						}

						if (this.ship.getShield().isOn() || okToRaise) {
							if (avoidanceVector == null || !this.eval.hasSolution() && threatEvalAI.isThreatHigh()) {
								if (threatShip != null && (!this.drivenByShieldControlAI || threatVisible)) {
									float maxAimOffset = (this.ship.getShield().getActiveArc() - 10.0F) * 0.6F;
									float threatAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), threatShip.getLocation());
									// intentionally kept dead: assigned in vanilla and never read
									float unusedMinAngleDiff = Float.MAX_VALUE;
									float maxThreatValue = 0.0F;
									float heaviestThreatAngle = 0.0F;

									for (BattleNext_ThreatData threatData : threatEvalAI.getAllThreatData()) {
										if (threatData.getShip() != threatShip) {
											float otherThreatAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), threatData.getShip().getLocation());
											// intentionally kept dead: computed in vanilla and never read
											float unusedAngleDiff = BattleNext_ShipAIHelper.getAngleDiff(threatAngle, otherThreatAngle);
											if (threatData.getThreatValue() > maxThreatValue) {
												heaviestThreatAngle = otherThreatAngle;
												maxThreatValue = threatData.getThreatValue();
											}
										}
									}

									float aimAngle = threatAngle;
									if (maxThreatValue > 0.0F) {
										threatAngle = BattleNext_ShipAIHelper.normalizeAngle(threatAngle);
										heaviestThreatAngle = BattleNext_ShipAIHelper.normalizeAngle(heaviestThreatAngle);
										aimAngle = threatAngle + Math.min(maxAimOffset, BattleNext_ShipAIHelper.getAngleDiff(threatAngle, heaviestThreatAngle)) * 0.5F * BattleNext_ShipAIHelper.getTurnDirection(threatAngle, heaviestThreatAngle);
									}

									Vector2f aimPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(aimAngle);
									aimPoint.scale(threatDistance);
									Vector2f.add(this.ship.getLocation(), aimPoint, aimPoint);
									this.ship.getMouseTarget().set(aimPoint);
								} else if (avoidanceVector != null) {
									Vector2f aimPoint = new Vector2f(avoidanceVector);
									aimPoint.scale(500.0F);
									Vector2f.add(aimPoint, this.ship.getLocation(), aimPoint);
									this.ship.getMouseTarget().set(aimPoint);
								} else if (collisionThreatDir != null) {
									Vector2f aimPoint = new Vector2f(collisionThreatDir);
									aimPoint.scale(500.0F);
									Vector2f.add(aimPoint, this.ship.getLocation(), aimPoint);
									this.ship.getMouseTarget().set(aimPoint);
								} else if (this.ship.getShield().isOn()) {
									ShipAPI nearestEnemy = BattleNext_ShipAIHelper.getNearestEnemyInRange(this.ship, 1000.0F);
									if (nearestEnemy != null) {
										this.ship.getMouseTarget().set(nearestEnemy.getLocation());
									} else {
										Vector2f aimPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.ship.getFacing());
										aimPoint.scale(500.0F);
										Vector2f.add(aimPoint, this.ship.getLocation(), aimPoint);
										this.ship.getMouseTarget().set(aimPoint);
									}
								}
							} else {
								Vector2f aimPoint = new Vector2f(avoidanceVector);
								aimPoint.scale(500.0F);
								Vector2f.add(aimPoint, this.ship.getLocation(), aimPoint);
								this.ship.getMouseTarget().set(aimPoint);
							}
						}

						boolean criticalHull = this.ship.getHitpoints() < 150.0F * this.ship.getHullSize().ordinal();
						boolean shieldFacingBad = false;
						if (this.ship.getMouseTarget() != null && okToRaise) {
							float aimAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.ship.getMouseTarget());
							float facingTolerance = 120.0F;
							float activeArc = this.ship.getShield().getActiveArc();
							if (threatShip != null && threatShip.isPhased()) {
								facingTolerance = 10.0F;
								if (phasedThreatNear) {
									activeArc = Math.max(activeArc, 30.0F);
								}
							}

							if (!BattleNext_ShipAIHelper.isInArc(this.ship.getShield().getFacing(), activeArc + facingTolerance, aimAngle)) {
								shieldFacingBad = true;
							}
						}

						this.timeSinceShieldHit += amount;
						if (shouldRaiseShield) {
							this.timeSinceShieldHit = 0.0F;
						}

						if (this.ship.getShield().isOn() && (!criticalHull || !shouldRaiseShield) && !keepShieldsOn) {
							if (okToRaise || !(this.timeSinceShieldHit > 1.0F) && !fluxTooHigh) {
								if (shieldFacingBad) {
									this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
									this.timeSinceShieldHit = 0.0F;
								}
							} else {
								this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, -1);
								this.shieldsOn = false;
								this.timeSinceShieldHit = 0.0F;
							}
						} else if (BattleNext_ShipAIHelper.isShieldIdle(this.ship)
								&& (okToRaise || criticalHull && shouldRaiseShield)) {
							if (debug) {
								BattleNext_Logger.info(BattleNext_OmniShieldAngleAI.class, "shield.toggle.on",
				BattleNext_Logger.fields().put("ship_id", this.ship.getId()).put("shield_on", this.ship.getShield().isOn()).put("flux", this.ship.getFluxLevel()).put("debug", debug).build());
							}

							this.timeSinceShieldToggle = 0.0F;
							this.ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, this.ship.getMouseTarget(), -1);
							if (this.ship.getMouseTarget() != null) {
								float mouseTargetAngle = BattleNext_ShipAIHelper.getAngleFast(this.ship.getLocation(), this.ship.getMouseTarget());
								this.ship.getShield().forceFacing(mouseTargetAngle);
							}

							this.shieldsOn = true;
						}
					}
				}
			}
		}
	}

	public void unused() {

	}

	@Override
	public boolean hasFiringSolution() {
		return this.eval.hasSolution();
	}

	@Override
	public BattleNext_FiringSolutionEval getEval() {
		return this.eval;
	}
}
