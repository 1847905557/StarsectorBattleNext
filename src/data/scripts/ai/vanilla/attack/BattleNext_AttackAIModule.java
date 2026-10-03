package data.scripts.ai.vanilla.attack;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.WeaponGroupType;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.tools.BattleNext_GeometryRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.*;

/**
 * Port of engine class com.fs.starfarer.combat.ai.attack.AttackAIModule.
 * Manual weapon group attack AI: picks (group, target) attack runs and drives them.
 */
public class BattleNext_AttackAIModule {

	public static final float fighterExtraRange = 500.0F;

	static MissileSpecAPI requireMissileSpec(Object projectileSpec, String weaponId) {
		if (projectileSpec instanceof MissileSpecAPI missileSpec) return missileSpec;
		throw new IllegalArgumentException("MISSILE weapons must fire missiles; weapon id: [" + weaponId + "]");
	}

	private ShipAPI ship;
	private BattleNext_AttackState attackState = BattleNext_AttackState.PICKING_TARGET;
	private float stateTimer = 0.0F;
	private float stateDuration = 2.0F;
	private float maxRange;
	private BattleNext_AttackingGroup attackingGroup;
	private WeaponGroupAPI bestWeaponGroup;
	private float bestGroupRange;
	private BattleNext_WeaponGroupEvaluation bestWeaponGroupEvaluation;
	private float attackRange = -1.0F;
	private float nonMissileMaxRange;
	private float minMissileRange;
	private float minRange;
	private float maxThreatRange;
	private Map<WeaponGroupAPI, BattleNext_WeaponGroupEvaluation> weaponGroupEvaluations = new LinkedHashMap<>();
	private BattleNext_AutofireManager autofireManager;
	private final ShipwideAIFlags aiFlags;
	private CombatFleetManagerAPI fleetManager;
	private boolean hasNonSmallPD = false;
	private boolean hasNonMissileWeapon = false;
	private boolean hasNonPDWeapon = false;
	private List<PendingAttackRun> pendingAttackRuns = new ArrayList<>();
	private float timeOnTarget = 0.0F;
	private float timeSinceTargetChange = 0.0F;
	private CombatEntityAPI targetEntity = null;
	private float timeInAttackRun = 0.0F;
	private float averageCooldown = -1.0F;
	private boolean firing = false;
	private boolean firingAtTarget = false;
	private float burstTimer = 0.0F;
	private float burstInterval = 0.1F + 0.1F * (float) Math.random();
	private boolean suppressFire = true;
	private float timeAtHighFlux = 0.0F;
	private float fluxSuppressDelay = 3.0F;
	private Vector2f aimPoint;

	public BattleNext_AttackAIModule(ShipAPI ship, ShipwideAIFlags aiFlags) {
		this.ship = ship;
		this.aiFlags = aiFlags;
		this.initWeaponGroupEvaluations();
		if (!ship.isFighter() && !ship.isDrone()) {
			this.autofireManager = new BattleNext_AutofireManagerV2(ship, this.weaponGroupEvaluations, aiFlags);
		} else {
			this.autofireManager = new BattleNext_FighterAutofireManager(ship, this.weaponGroupEvaluations, aiFlags);
		}

		this.fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner());
	}

	public ShipwideAIFlags getAIFlags() {
		return this.aiFlags;
	}

	public float getBestGroupRange() {
		return this.bestGroupRange;
	}

	public BattleNext_AttackingGroup getAttackingGroup() {
		return this.attackingGroup;
	}

	public ShipAPI getAttackTarget() {
		return this.isCurrentTargetValid() ? this.attackingGroup.getShip() : null;
	}

	public float getAverageCooldown() {
		return this.averageCooldown < 0.0F ? Float.MAX_VALUE : this.averageCooldown;
	}

	public void computeAverageCooldown() {
		float weightedSpeedTotal = 0.0F;
		float weightTotal = 0.0F;

		for (int groupIndex = 0; groupIndex < 7; groupIndex++) {
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
			if (group != null && group.getWeaponsCopy() != null) {
				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if ((weapon.getSlot().isHardpoint() || !(weapon.getSlot().getArc() > 20.0F))
						&& !weapon.hasAIHint(AIHints.DO_NOT_AIM)
						&& !weapon.hasAIHint(AIHints.GUIDED_POOR)
						&& !weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
						float forwardAngleDiff = Misc.getAngleDiff(weapon.getSlot().getAngle(), 0.0F);
						if (!(forwardAngleDiff > 10.0F)) {
							float weight = 1.0F;
							switch (weaponSizeScore(weapon.getSize())) {
								case 1:
									weight = 1.0F;
									break;
								case 2:
									weight = 3.0F;
									break;
								case 3:
									weight = 8.0F;
							}

							weightTotal += weight;
							weightedSpeedTotal += weapon.getProjectileSpeed() * weight;
						}
					}
				}
			}
		}

		if (weightTotal <= 0.5F) {
			this.averageCooldown = Float.MAX_VALUE;
		} else {
			this.averageCooldown = weightedSpeedTotal / weightTotal;
		}
	}

	public void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector) {
		if (this.averageCooldown < 0.0F) {
			this.computeAverageCooldown();
		}

		this.timeAtHighFlux += amount;
		this.timeInAttackRun += amount;
		if (!this.aiFlags.hasFlag(AIFlags.IN_ATTACK_RUN) && !this.aiFlags.hasFlag(AIFlags.POST_ATTACK_RUN)) {
			this.timeInAttackRun = 0.0F;
		}

		ShipAPI groupTarget = null;
		if (this.attackingGroup != null) {
			groupTarget = this.attackingGroup.getShip();
		}

		if (groupTarget != this.targetEntity && groupTarget != null) {
			this.targetEntity = groupTarget;
			this.timeOnTarget = 0.0F;
			this.timeSinceTargetChange = 0.0F;
		}

		if (this.firingAtTarget) {
			this.timeOnTarget += amount;
			this.timeSinceTargetChange = 0.0F;
		} else {
			this.timeSinceTargetChange += amount;
		}

		if (this.timeSinceTargetChange > 3.0F) {
			this.timeOnTarget = 0.0F;
			this.timeSinceTargetChange = 0.0F;
			this.targetEntity = null;
		}

		this.firingAtTarget = false;
		if (this.suppressFire || !(this.ship.getFluxLevel() < 0.7F) && !(this.timeAtHighFlux > this.fluxSuppressDelay)) {
			if (!this.suppressFire && this.ship.getShield() != null && this.ship.getShield().isOn()) {
				this.aiFlags.setFlag(AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS);
			}
		} else {
			this.suppressFire = true;
			this.timeAtHighFlux = 0.0F;
		}

		Iterator<PendingAttackRun> pendingIter = this.pendingAttackRuns.iterator();

		while (pendingIter.hasNext()) {
			PendingAttackRun pendingRun = pendingIter.next();
			pendingRun.setTimeRemaining(pendingRun.getTimeRemaining() - amount);
			if (pendingRun.getTimeRemaining() <= 0.0F) {
				pendingIter.remove();
			}
		}

		if (this.attackingGroup != null && this.attackingGroup.getShip() != null) {
			ShipAPI target = this.attackingGroup.getShip();
			if (target.getFluxTracker().isOverloadedOrVenting()) {
				float recoverTime;
				if (target.getFluxTracker().isOverloaded()) {
					recoverTime = target.getFluxTracker().getOverloadTimeRemaining();
				} else {
					recoverTime = target.getFluxTracker().getTimeToVent();
				}

				if (recoverTime > 4.0F) {
					this.pendingAttackRuns.clear();
				}
			}
		}

		this.autofireManager.advance(amount, threatEvalAI, avoidanceVector);
		if (this.attackState == BattleNext_AttackState.PICKING_TARGET) {
			this.selectAttackTarget(threatEvalAI);
			this.initWeaponGroupEvaluations();
		} else if (this.attackState == BattleNext_AttackState.FIRING && !this.aiFlags.hasFlag(AIFlags.DO_NOT_USE_FLUX)) {
			this.executeAttackRun(amount, threatEvalAI);
		}

		Vector2f fallbackAimPoint = null;
		if (this.aimPoint == null) {
			if (this.attackingGroup != null && this.attackingGroup.getShip() != null) {
				float aimMult = 1.0F;
				if (this.attackingGroup.hasAimableWeapons()) {
					aimMult = 1.0F;
				}

				fallbackAimPoint = BattleNext_ShipAIHelper.getAimPoint(
					this.ship, this.attackingGroup.getShip(), this.attackingGroup.getRange() * aimMult, !this.isAttackGroupReady(), this.timeOnTarget
				);
				this.ship.getMouseTarget().set(fallbackAimPoint);
			} else {
				Vector2f facingPoint = BattleNext_ShipAIHelper.getUnitVectorAtAngle(this.ship.getFacing());
				facingPoint.scale(2500.0F);
				Vector2f.add(this.ship.getLocation(), facingPoint, this.ship.getMouseTarget());
			}
		}

		if ((this.aimPoint != null || fallbackAimPoint != null) && this.attackingGroup != null && this.attackingGroup.getShip() != null) {
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, this.attackingGroup.getWeaponGroupIndex());
			if (group != null && group.getWeaponsCopy() != null) {
				// approximation: vanilla pushes the aim point into each weapon's AimTracker;
				// the public API has no AimTracker setter, so mouseTarget drives group aiming.
			}
		}

		this.stateTimer += amount;
		if (this.stateTimer > this.stateDuration) {
			this.stateTimer = 0.0F;
			this.stateDuration = 1.0F;
			if (this.attackState == BattleNext_AttackState.PICKING_TARGET) {
				this.attackState = BattleNext_AttackState.WAITING;
				this.stateDuration = 0.1F;
			} else if (this.attackState == BattleNext_AttackState.WAITING) {
				this.attackState = BattleNext_AttackState.PICKING_TARGET;
				this.stateDuration = 1.0F;
			} else if (this.attackState == BattleNext_AttackState.FIRING) {
				this.aimPoint = null;
				this.attackingGroup = null;
				this.attackState = BattleNext_AttackState.PICKING_TARGET;
				this.stateDuration = 1.0F;
			}
		}
	}

	// intentionally kept dead: vanilla method is never called
	private void stopAttack() {
		this.stopAttackRun(2.0F);
	}

	private void stopAttackRun(float duration) {
		duration *= 0.5F;
		this.aimPoint = null;
		this.attackingGroup = null;
		this.attackState = BattleNext_AttackState.WAITING;
		this.stateDuration = duration;
		this.stateTimer = 0.0F;
		this.ship.setShipTarget(null);
	}

	private void executeAttackRun(float amount, BattleNext_ThreatEvalAI threatEvalAI) {
		if (!this.isCurrentTargetValid()) {
			this.stopAttackRun(0.5F + (float) Math.random());
		} else {
			int groupIndex = this.attackingGroup.getWeaponGroupIndex();
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
			if (group != null && (!group.isAutofiring() || isOverrideActive(group))) {
				ShipAPI target = this.attackingGroup.getShip();
				this.ship.setShipTarget(target);
				if (target == null || !target.isPhased() || !(this.ship.getFluxLevel() > 0.3F) || !this.attackingGroup.usesFlux()) {
					if (!this.aiFlags.hasFlag(AIFlags.DELAY_STRIKE_FIRE) || !this.attackingGroup.isStrikeGroup()) {
						if (!this.isAttackWorthwhile(this.attackingGroup)) {
							this.stopAttackRun(0.5F + (float) Math.random());
						} else {
							float aimMult = 1.0F;
							if (this.attackingGroup.hasAimableWeapons()) {
								aimMult = 1.0F;
							}

							BattleNext_ShipAIHelper.setComputingAimPoint(true);
							Vector2f aim = BattleNext_ShipAIHelper.getAimPoint(
								this.ship, target, this.attackingGroup.getRange() * aimMult, !this.isAttackGroupReady(), this.timeOnTarget
							);
							BattleNext_ShipAIHelper.setComputingAimPoint(false);
							if (aim != null) {
								this.aimPoint = new Vector2f(aim);
								Vector2f aimOffset = Vector2f.sub(aim, this.ship.getLocation(), new Vector2f());
								float aimDist = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), aim);
								BattleNext_ShipAIHelper.normalise(aimOffset);
								aimOffset.scale(Math.max(900.0F, aimDist));
								Vector2f.add(this.ship.getLocation(), aimOffset, this.ship.getMouseTarget());
								if (this.burstTimer < this.attackingGroup.getCheckInterval()) {
									this.burstTimer += amount;
								} else if (!BattleNext_ShipAIHelper.areHulksInTheWay(this.ship, target, this.ship.getLocation(), aim)) {
									if (this.attackingGroup.canFireSafely()) {
										float danger = BattleNext_ShipAIHelper.computeDanger(
											this.ship,
											target,
											this.ship.getLocation(),
											aim,
											this.attackingGroup.getRange(),
											this.attackingGroup.getBurstDuration(),
											this.attackingGroup.getMinWeaponRange()
										);
										if (danger > 0.1F || this.attackingGroup.isStrikeGroup() && danger > 0.0F) {
											for (WeaponAPI weapon : group.getWeaponsCopy()) {
												if (!weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
													Vector2f weaponPos = weapon.getSlot().computePosition(this.ship);
													boolean fromTargetingOval = weapon.hasAIHint(AIHints.RANGE_FROM_TARGETING_OVAL);
													boolean fromShipRadius = weapon.hasAIHint(AIHints.RANGE_FROM_SHIP_RADIUS);
													if (fromTargetingOval || fromShipRadius) {
														weaponPos = this.ship.getLocation();
													}

													Vector2f shotEnd = BattleNext_ShipAIHelper.getUnitVectorAtAngle(weapon.getCurrAngle());
													shotEnd.scale(weapon.getRange());
													Vector2f.add(weaponPos, shotEnd, shotEnd);
													CollisionClass projectileCollision = weapon.getProjectileCollisionClass();
													boolean canHitShips = false;
													if (projectileCollision != CollisionClass.RAY_FIGHTER
														&& projectileCollision != CollisionClass.PROJECTILE_FIGHTER
														&& projectileCollision != CollisionClass.MISSILE_NO_FF
														&& projectileCollision != CollisionClass.PROJECTILE_NO_FF) {
														canHitShips = true;
													}

													if (canHitShips) {
														float dangerRange = weapon.getRange();
														if (!weapon.isBeam()) {
															dangerRange += 150.0F;
														}

														if (fromTargetingOval) {
															dangerRange += BattleNext_ShipAIHelper.getDistanceToEntitySurface(aim, this.ship, false);
														} else if (fromShipRadius) {
															dangerRange += this.ship.getCollisionRadius();
														}

														if (BattleNext_ShipAIHelper.computeDanger(
																this.ship,
																target,
																weaponPos,
																shotEnd,
																weapon.getProjectileSpeed(),
																weapon.getSpec().getDerivedStats().getBurstFireDuration(),
																dangerRange
															)
															> 0.1F) {
															return;
														}
													}
												}
											}
										}
									}

									if (isAlternating(group)
										&& this.attackingGroup.getCurrentWeaponIndex() != -1
										&& (
											group.getActiveWeapon() == null
												|| group.getWeaponsCopy().indexOf(group.getActiveWeapon()) != this.attackingGroup.getCurrentWeaponIndex()
										)) {
										// approximation: WeaponGroupAPI cannot set the active alternating weapon
										// index, so the engine-only group.setCurrentActive(...) sync is dropped
									}

									if (this.canAttackTarget()) {
										if (group != null) {
											if (!isAlternating(group) || !this.attackingGroup.hasUniformWeapons() || !((float) Math.random() > 0.97F)) {
												this.burstInterval -= amount;
												if (isAlternating(group) && this.attackingGroup.hasUniformWeapons()) {
													if (!(this.burstInterval <= 0.0F)) {
														return;
													}

													this.burstInterval = 0.2F + 0.1F * (float) Math.random();
												}

												if (this.attackingGroup.getEvaluationInterval() > 0.0F && this.attackingGroup.getAmmoThreshold() > 0.0F) {
													this.ship.giveCommand(ShipCommand.FIRE, this.ship.getMouseTarget(), -1);
													this.firing = true;
													this.firingAtTarget = true;
												}

												this.attackingGroup.setElapsedTime(this.attackingGroup.getElapsedTime() + amount);
												if (this.attackingGroup.getElapsedTime() >= this.attackingGroup.getEvaluationInterval()
													|| this.attackingGroup.hasEnoughAmmo(this.ship)) {
													PendingAttackRun pendingRun = new PendingAttackRun();
													pendingRun.setGroupIndex(this.attackingGroup.getWeaponGroupIndex());
													pendingRun.setTimeRemaining(3.0F + 3.0F * (float) Math.random());
													this.pendingAttackRuns.add(pendingRun);
													this.selectAttackTarget(threatEvalAI);
												}
											}
										}
									}
								}
							}
						}
					}
				}
			} else {
				this.stopAttackRun(0.5F + (float) Math.random());
			}
		}
	}

	private void addAttackCooldown(int groupIndex, float baseDuration, float randomDuration) {
		for (PendingAttackRun pendingRun : this.pendingAttackRuns) {
			if (pendingRun.getGroupIndex() == groupIndex) {
				return;
			}
		}

		PendingAttackRun pendingRun = new PendingAttackRun();
		pendingRun.setGroupIndex(groupIndex);
		pendingRun.setTimeRemaining(baseDuration + randomDuration * (float) Math.random());
		this.pendingAttackRuns.add(pendingRun);
	}

	private boolean canAttackTarget() {
		if (this.attackingGroup == null) {
			return false;
		}

		ShipAPI target = this.attackingGroup.getShip();
		if (target == null) {
			return false;
		}

		if (this.ship == null) {
			return false;
		}

		float targetRadius = target.getCollisionRadius();
		Vector2f aim = BattleNext_ShipAIHelper.getAimPoint(this.ship, target, this.attackingGroup.getRange(), !this.isAttackGroupReady(), this.timeOnTarget);
		if (aim == null) {
			return false;
		}

		Vector2f relVelocity = Vector2f.sub(target.getVelocity(), this.ship.getVelocity(), new Vector2f());
		float relSpeed = relVelocity.length();
		WeaponGroupAPI selectedGroup = this.ship.getSelectedGroupAPI();
		if (selectedGroup == null) {
			return false;
		}

		float readyScore = 0.0F;
		float unreadyScore = 0.0F;

		for (WeaponAPI weapon : selectedGroup.getWeaponsCopy()) {
			if (weapon != null
				&& weapon.getAmmo() > 0
				&& !weapon.isDisabled()
				&& !weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)
				&& (!isAlternating(selectedGroup) || selectedGroup.getActiveWeapon() == weapon)
				&& weapon.getSlot() != null) {
				Vector2f weaponPos = weapon.getSlot().computePosition(this.ship);
				boolean fromTargetingOval = weapon.hasAIHint(AIHints.RANGE_FROM_TARGETING_OVAL);
				boolean fromShipRadius = weapon.hasAIHint(AIHints.RANGE_FROM_SHIP_RADIUS);
				if (fromTargetingOval || fromShipRadius) {
					weaponPos = this.ship.getLocation();
				}

				float weaponDist = BattleNext_ShipAIHelper.getDistance(weaponPos, aim);
				if (fromTargetingOval) {
					weaponDist -= BattleNext_ShipAIHelper.getDistanceToEntitySurface(aim, this.ship, false);
				} else if (fromShipRadius) {
					weaponDist -= this.ship.getCollisionRadius();
				}

				float strikeMult = 1.0F;
				if (this.attackingGroup.isStrikeGroup()) {
					if (!this.ship.isFighter()) {
						strikeMult = 0.25F;
					} else {
						strikeMult = 4.0F;
					}
				}

				if (weapon.getSize() != null) {
					boolean isBomb = weapon.hasAIHint(AIHints.BOMB);
					if (weapon.hasAIHint(AIHints.STRIKE) && isBomb && relSpeed < 40.0F) {
						unreadyScore += weapon.getSize().ordinal() + 1;
					} else {
						float arcMult = 1.0F;
						float hardpointOffset = 0.0F;
						if (weapon.getSlot().isHardpoint() && weapon.getType() == WeaponType.MISSILE) {
							hardpointOffset = Math.abs(weapon.getSlot().getLocation().y * 0.6F);
							hardpointOffset = Math.min(targetRadius * 0.5F, hardpointOffset);
							arcMult = 2.0F;
						}

						boolean bombWaited = isBomb && this.timeInAttackRun > 3.0F;
						float extraArc = (strikeMult * targetRadius + hardpointOffset) / ((float) (Math.PI * 2) * weaponDist) * 360.0F;
						if (weapon.getSpec() != null) {
							extraArc += weapon.getSpec().getExtraArcForAI() / 2.0F;
						}

						float totalArc = weapon.getSlot().getArc() + extraArc * arcMult;
						boolean inArc = BattleNext_ShipAIHelper.isInArc(weapon.getSlot().computeMidArcAngle(this.ship), totalArc, weaponPos, aim);
						float weaponRange = weapon.getRange();
						if (this.ship.isFighter() && weapon.hasAIHint(AIHints.EXTRA_RANGE_ON_FIGHTER)) {
							weaponRange += fighterExtraRange;
						}

						if (weapon.getProjectileFadeRange() <= 0.0F && weaponRange > 300.0F) {
							weaponRange -= targetRadius * 0.4F;
						}

						if (!inArc && (this.attackingGroup.missilesAimable() || bombWaited)) {
							weaponRange *= 0.9F;
							inArc = true;
						}

						if (inArc && weaponDist <= weaponRange + targetRadius * 0.75F) {
							float targetAngle = BattleNext_ShipAIHelper.getAngleFast(weaponPos, aim);
							float angleError = BattleNext_ShipAIHelper.getAngleDiff(weapon.getCurrAngle(), targetAngle) - extraArc;
							if (angleError < 0.0F) {
								angleError = 0.0F;
							}

							if (this.attackingGroup.missilesAimable() || weapon.getType() == WeaponType.LAUNCH_BAY || bombWaited) {
								angleError = 0.0F;
							}

							if (angleError > 0.0F) {
								unreadyScore += weapon.getSize().ordinal() + 1;
							} else {
								readyScore += weapon.getSize().ordinal() + 1;
							}
						}
					}
				}
			}
		}

		return readyScore > unreadyScore;
	}

	public float getMaxRange() {
		return this.maxRange;
	}

	private void selectAttackTarget(BattleNext_ThreatEvalAI threatEvalAI) {
		CombatEngineAPI engine = BattleNext_ShipAIHelper.getEngine();
		FogOfWarAPI fogOfWar = BattleNext_ShipAIHelper.getEngine().getFogOfWar(this.ship.getOwner());
		List<ShipAPI> ships = engine.getShips();
		ArrayList<BattleNext_AttackingGroup> candidates = new ArrayList<>();

		for (ShipAPI other : ships) {
			if ((fogOfWar == null || fogOfWar.isVisible(other) || this.ship.isDrone()) && !other.isShuttlePod()) {
				float surfaceDist = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), other.getLocation())
					- this.ship.getCollisionRadius()
					- other.getCollisionRadius();
				if (!(surfaceDist > this.maxRange) && !other.isHulk() && other.getOwner() != 100 && other.isTargetable() && this.ship.getOwner() != other.getOwner()) {
					groupLoop:
					for (int groupIndex = 0; groupIndex < 7; groupIndex++) {
						for (PendingAttackRun pendingRun : this.pendingAttackRuns) {
							if (pendingRun.getGroupIndex() == groupIndex) {
								continue groupLoop;
							}
						}

						BattleNext_AttackingGroup candidate = this.evaluateAttackGroup(groupIndex, other);
						if ((this.suppressFire || candidate == null || !candidate.usesFlux()) && candidate != null) {
							candidates.add(candidate);
						}
					}
				}
			}
		}

		if (candidates.isEmpty()) {
			this.attackState = BattleNext_AttackState.WAITING;
			this.stateTimer = 0.0F;
			this.stateDuration = 1.0F;
			this.attackingGroup = null;
			this.selectWeaponGroup();
		} else {
			candidates.sort((one, two) -> {
				boolean oneIsFighter = one.getShip() != null && one.getShip().isFighter();
				boolean twoIsFighter = two.getShip() != null && two.getShip().isFighter();
				if (oneIsFighter && !twoIsFighter) {
					return 1;
				} else if (twoIsFighter && !oneIsFighter) {
					return -1;
				} else {
					float scoreDiff = one.getScore() - two.getScore();
					if (scoreDiff < 0.0F) {
						return 1;
					} else {
						return scoreDiff > 0.0F ? -1 : (int)(one.getDistance() - two.getDistance());
					}
				}
			});
			this.attackState = BattleNext_AttackState.FIRING;
			this.firing = false;
			this.stateTimer = 0.0F;
			this.attackingGroup = candidates.get(0);
			this.ship.giveCommand(ShipCommand.SELECT_GROUP, null, this.attackingGroup.getWeaponGroupIndex());
			this.burstTimer = 0.0F;
			this.stateDuration = 3.0F + this.attackingGroup.getCheckInterval();
		}
	}

	private void selectWeaponGroup() {
		float bestScore = 0.0F;
		int bestIndex = -1;
		WeaponGroupAPI bestGroup = null;

		for (int groupIndex = 0; groupIndex < 7; groupIndex++) {
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
			if (group != null && group.getWeaponsCopy() != null) {
				int score = 0;

				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if (weapon != null && !weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
						byte sizeScore = 1;
						switch (weaponSizeScore(weapon.getSize())) {
							case 1:
								sizeScore = 1;
								break;
							case 2:
								sizeScore = 4;
								break;
							case 3:
								sizeScore = 8;
						}

						score += sizeScore;
					}
				}

				if (group.isAutofiring()) {
					score = 1000 - score;
				} else {
					score += 1000;
				}

				if (bestIndex < 0 || score > bestScore) {
					bestIndex = groupIndex;
					bestScore = score;
					bestGroup = group;
				}
			} else if (bestGroup == null || bestGroup.isAutofiring()) {
				bestGroup = group;
				bestIndex = groupIndex;
				bestScore = 1000.0F;
			}
		}

		if (bestIndex >= 0 && (this.ship.getSelectedGroupAPI() != bestGroup || bestGroup == null)) {
			this.ship.giveCommand(ShipCommand.SELECT_GROUP, null, bestIndex);
		}
	}

	private boolean isWeaponOnStandingOrders(WeaponAPI weapon) {
		// approximation: CombatFleetManagerAPI exposes no standing orders; the engine default
		// (strikeAllowed and longRangeAllowed both true) makes every weapon allowed
		return this.fleetManager == null ? true : true;
	}

	public boolean hasActiveAttackGroup() {
		return this.attackingGroup == null || this.attackingGroup.hasAimableWeapons();
	}

	public boolean isBestGroupStrike() {
		return this.bestWeaponGroupEvaluation != null && this.bestWeaponGroupEvaluation.hasAimableWeapons();
	}

	// approximation: WeaponGroupAPI.isOverrideActive() does not exist; engine overrides are
	// unreachable from the API, so this is a constant false
	private static boolean isOverrideActive(WeaponGroupAPI group) {
		return false;
	}

	// approximation: engine WeaponGroup.isAlternating(); the API exposes the group type only
	private static boolean isAlternating(WeaponGroupAPI group) {
		return group.getType() == WeaponGroupType.ALTERNATING;
	}

	private BattleNext_AttackingGroup evaluateAttackGroup(int groupIndex, ShipAPI target) {
		WeaponGroupAPI weaponGroup = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
		if (weaponGroup == null) {
			return null;
		}

		if (weaponGroup.isAutofiring() && !isOverrideActive(weaponGroup)) {
			return null;
		}

		BattleNext_AttackingGroup group = new BattleNext_AttackingGroup();
		group.setWeaponGroupIndex(groupIndex);
		group.setDistance(BattleNext_ShipAIHelper.getDistance(target.getLocation(), this.ship.getLocation()));
		float targetRadius = target.getCollisionRadius();
		Vector2f targetLoc = target.getLocation();
		float score = 0.0F;
		float maxTimeToReady = 0.0F;
		float projectileSpeedTotal = 0.0F;
		float inRangeCount = 0.0F;
		float instantProjectileCount = 0.0F;
		boolean hasZeroFluxWeapon = false;
		boolean hasAimable = false;
		boolean canFireSafely = false;
		boolean missilesAimable = true;
		boolean allGuidedPoor = true;
		boolean hasUniformWeapons = true;
		boolean alternating = isAlternating(weaponGroup);
		float lastWeaponScore = 0.0F;
		float maxScoreDelta = 0.0F;
		boolean bestRunConservative = false;
		boolean strikeReady = false;
		int bestWeaponIndex = -1;
		boolean bestRunMissilesAimable = true;
		boolean killShot = false;
		boolean overkill = false;
		boolean kineticVsLowFlux = false;
		boolean kineticMissile = false;
		boolean hasHeatseeker = false;
		boolean usesFlux = false;
		this.hasNonPDWeapon = true;
		float burstDuration = 0.0F;
		Vector2f toTargetDir = Vector2f.sub(target.getLocation(), this.ship.getLocation(), new Vector2f());
		BattleNext_ShipAIHelper.normalise(toTargetDir);
		Vector2f relVelocity = Vector2f.sub(target.getVelocity(), this.ship.getVelocity(), new Vector2f());
		Vector2f relDir = BattleNext_ShipAIHelper.normalise(new Vector2f(relVelocity));
		float alignment = Vector2f.dot(toTargetDir, relDir);
		float targetSpeed = target.getVelocity().length();
		float lateralSpeed = (1.0F - Math.abs(alignment)) * targetSpeed;
		float toTargetAngle = BattleNext_ShipAIHelper.getAngleFast(toTargetDir);
		// approximation: engine ship.getArmorEvaluator().getArmorInArc(facing, arc)
		float armorInArc = this.ship.getAverageArmorInSlice(toTargetAngle, 90.0F);
		boolean thinArmor = armorInArc <= this.ship.getArmorGrid().getArmorRating() * 0.6F;
		boolean badlyDamaged = this.ship.getHullLevel() < 0.5F;
		boolean noDefenseSystems = this.ship.getShield() == null && this.ship.getPhaseCloak() == null;
		float hpFloor = Math.min(500.0F, this.ship.getMaxHitpoints() * 0.5F);
		boolean desperate = this.ship.getHitpoints() < hpFloor;
		if (noDefenseSystems && (thinArmor || badlyDamaged)) {
			desperate = true;
		}

		if (BattleNext_ShipAIHelper.isPhaseShip(this.ship) && this.ship.getHullLevel() < 0.75F) {
			desperate = true;
		}

		if (desperate && target != null && target.isFighter()) {
			desperate = false;
		}

		// approximation: engine checks this.ship.getSystem() instanceof AmmoReloadSystem; the API
		// exposes only the system spec, so a missile-capable system spec marks the same case
		ShipSystemAPI system = this.ship.getSystem();
		if (system != null && system.getSpecAPI() != null
			&& system.getSpecAPI().getWeaponTypes() != null
			&& system.getSpecAPI().getWeaponTypes().contains(WeaponType.MISSILE)) {
			if (!target.isFighter()
				&& (system.getAmmo() == Integer.MAX_VALUE || system.getAmmo() > 3 || system.getAmmo() > 1 && system.getAmmoPerSecond() > 0.0F)) {
				// approximation: engine ship.getFluxCapacity() == flux tracker capacity == maxFlux
				float fluxCapacity = this.ship.getMaxFlux();
				float systemFluxFraction = system.getFluxPerUse() / fluxCapacity;
				float currentFluxLevel = this.ship.getFluxLevel();
				if (currentFluxLevel + systemFluxFraction + 0.75F * (float) Math.random() < 0.75F) {
					desperate = true;
				}
			}
		}

		desperate |= this.ship.getVariant().hasHullMod("missile_reload");
		desperate |= this.ship.getHullSpec().getHints().contains(ShipTypeHints.ALWAYS_PANIC);
		boolean allMissilesLowAmmo = true;
		// intentionally kept dead: vanilla accumulates this counter but never reads it
		int unusedTotalAmmo = 0;
		String firstWeaponId = null;
		float shotsToKillTarget = 1000.0F;
		float minRoF = 1000.0F;
		group.computeTotalAmmo(this.ship);

		for (WeaponAPI weapon : weaponGroup.getWeaponsCopy()) {
			if (!this.isWeaponOnStandingOrders(weapon)) {
				return null;
			}

			if (!weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
				boolean guidedPoorOffset = weapon.getType() == WeaponType.MISSILE
					&& Math.abs(weapon.getSlot().getAngle()) > 5.0F
					&& weapon.hasAIHint(AIHints.GUIDED_POOR);
				if (!weapon.hasAIHint(AIHints.DO_NOT_AIM)
					&& !guidedPoorOffset
					&& (weapon.getType() != WeaponType.MISSILE || !(Math.abs(weapon.getSlot().getAngle()) > 5.0F) || !weapon.hasAIHint(AIHints.GUIDED_POOR))) {
					missilesAimable = false;
				}

				if (!weapon.hasAIHint(AIHints.GUIDED_POOR)) {
					allGuidedPoor = false;
				}

				if (weapon.hasAIHint(AIHints.HEATSEEKER)) {
					hasHeatseeker = true;
				}

				if (!weapon.getSpec().getAIHints().contains(AIHints.PD)) {
					this.hasNonPDWeapon = false;
				}

				allMissilesLowAmmo &= weapon.getType() == WeaponType.MISSILE;
				allMissilesLowAmmo &= weapon.getAmmo() <= 20;
				shotsToKillTarget = Math.min(shotsToKillTarget, target.getHitpoints() / Math.max(weapon.getDamage().getDamage(), 1.0F));
				minRoF = Math.min(weapon.getDerivedStats().getRoF(), minRoF);
				killShot |= weapon.getType() == WeaponType.MISSILE
					&& !this.ship.isFighter()
					&& (target.getHitpoints() * 2.0F < weapon.getDamage().getDamage() && target.getHitpoints() < target.getMaxHitpoints() * 0.5F
						|| hasNoDefenses(target));
				overkill |= target.getHitpoints() * 2.0F < weapon.getDamage().getDamage();
				// approximation: engine ship.getFluxAvailable() == maxFlux - currFlux
				kineticVsLowFlux |= weapon.getType() == WeaponType.MISSILE
					&& weapon.getDamage().getType() == DamageType.KINETIC
					&& (target.getMaxFlux() - target.getCurrFlux()) * 4.0F < weapon.getDamage().getDamage();
				kineticMissile |= weapon.getType() == WeaponType.MISSILE && weapon.getDamage().getType() == DamageType.KINETIC;
				float fluxCost = weapon.getFluxCostToFire();
				if (fluxCost > 0.0F) {
					usesFlux = true;
				}

				CollisionClass projectileCollision = weapon.getProjectileCollisionClass();
				if (projectileCollision != CollisionClass.RAY_FIGHTER
					&& projectileCollision != CollisionClass.PROJECTILE_FIGHTER
					&& projectileCollision != CollisionClass.HITS_SHIPS_ONLY_NO_FF
					&& projectileCollision != CollisionClass.NONE
					&& projectileCollision != CollisionClass.MISSILE_NO_FF
					&& projectileCollision != CollisionClass.PROJECTILE_NO_FF) {
					canFireSafely = true;
				}

				if (firstWeaponId == null) {
					firstWeaponId = weapon.getId();
				}

				if (!firstWeaponId.equals(weapon.getId())) {
					hasUniformWeapons = false;
				}

				if (weapon.usesAmmo()) {
					unusedTotalAmmo += weapon.getAmmo();
				} else {
					unusedTotalAmmo += 1000;
				}

				if (weapon.getSlot().isHardpoint() && !weapon.hasAIHint(AIHints.DO_NOT_AIM) && !guidedPoorOffset) {
					hasAimable = true;
				}

				if (weapon.getSpec().getDerivedStats().getBurstFireDuration() > burstDuration) {
					burstDuration = weapon.getSpec().getDerivedStats().getBurstFireDuration();
				}
			}
		}

		shotsToKillTarget = (float) Math.ceil(shotsToKillTarget);
		if (shotsToKillTarget < 1.0F) {
			shotsToKillTarget = 1.0F;
		}

		group.setBurstDuration(burstDuration);
		group.setHasAimableWeapons(hasAimable);
		group.setCanFireSafely(canFireSafely);
		group.setMissilesAimable(missilesAimable);
		group.setAllGuidedPoor(allGuidedPoor);
		group.setHasUniformWeapons(hasUniformWeapons);
		group.setUsesFlux(usesFlux);
		ShieldAPI targetShield = target.getShield();
		boolean shieldFacingUs = targetShield != null
			&& targetShield.isOn()
			&& BattleNext_ShipAIHelper.isInArc(targetShield.getFacing(), Math.max(30.0F, targetShield.getActiveArc()), target.getLocation(), this.ship.getLocation());
		boolean shieldTowardUs = targetShield != null
			&& targetShield.isOn()
			&& BattleNext_ShipAIHelper.isInArc(target.getFacing(), 120.0F, targetShield.getFacing());
		boolean shieldWideOpen = targetShield != null
			&& targetShield.isOn()
			&& BattleNext_ShipAIHelper.isInArc(targetShield.getFacing(), targetShield.getActiveArc() + 130.0F, target.getLocation(), this.ship.getLocation());
		boolean targetFacingUs = BattleNext_ShipAIHelper.isInArc(target.getFacing(), 120.0F, target.getLocation(), this.ship.getLocation());
		float predictedDist = BattleNext_ShipAIHelper.getPredictedDistance(this.ship, target.getLocation(), target.getVelocity());
		if (!shieldWideOpen && targetShield != null && !this.ship.isFighter()) {
			killShot = true;
		}

		if (!this.ship.isFighter() && !desperate && allMissilesLowAmmo) {
			if ((hasHeatseeker || killShot) && hasUniformWeapons) {
				int hostileMissiles = BattleNext_ShipAIHelper.getNumberOfHostileMissilesInArcRange(target, 0.0F, 360.0F, 3500.0F);
				if (hostileMissiles > 0 && BattleNext_ShipAIHelper.getTimeToRecover(target) > 2.0F) {
					hostileMissiles = (int) Math.floor(hostileMissiles * 0.75F);
					if (hostileMissiles < 1) {
						hostileMissiles = 1;
					}
				}

				shotsToKillTarget -= hostileMissiles;
				if (target.isFrigate() && target.getShield() == null && target.getPhaseCloak() == null) {
					shotsToKillTarget = Math.min(2.0F, shotsToKillTarget);
				}

				if (killShot && !hasHeatseeker && shotsToKillTarget > 0.0F) {
					float evalInterval = shotsToKillTarget / minRoF / weaponGroup.getWeaponsCopy().size() + 0.01F;
					if (isAlternating(weaponGroup)) {
						evalInterval = Math.min(evalInterval, 1.0F);
					}

					group.setEvaluationInterval(evalInterval);
					if (shotsToKillTarget <= 1.0F) {
						group.setEvaluationInterval(0.01F);
					}
				} else if (shotsToKillTarget > 0.0F) {
					group.setEvaluationInterval(0.01F);
				} else {
					group.setEvaluationInterval(0.0F);
				}

				group.setAmmoThreshold(shotsToKillTarget);
			} else if (kineticVsLowFlux && hasUniformWeapons) {
				group.setEvaluationInterval(0.5F);
				if (target.isFighter()) {
					group.setEvaluationInterval(0.01F);
				}

				if (target.getFluxLevel() < 0.25F) {
					group.setEvaluationInterval(0.01F);
				}
			} else if (kineticMissile && hasUniformWeapons) {
				group.setEvaluationInterval(1.0F);
				if (target.isFighter()) {
					group.setEvaluationInterval(0.01F);
				}

				if (target.getFluxLevel() < 0.25F) {
					group.setEvaluationInterval(0.01F);
				}
			}
		}

		float savedEvalInterval = group.getEvaluationInterval();
		group.setMinWeaponRange(10000.0F);
		float fluxCostTotal = 0.0F;
		boolean allWeaponsDry = true;
		boolean weAreFaster = BattleNext_ShipAIHelper.getCruiseSpeed(this.ship) > BattleNext_ShipAIHelper.getCruiseSpeed(target);
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), targetLoc) - this.ship.getCollisionRadius() - target.getCollisionRadius();
		if (weAreFaster) {
			distance *= 0.7F;
		}

		boolean closeIn = distance < this.bestGroupRange * 0.7;
		boolean veryClose = distance < this.bestGroupRange * 0.4F;
		if (this.attackRange > 0.0F && this.hasNonMissileWeapon()) {
			closeIn = distance < this.attackRange * 0.8;
			veryClose = distance < this.attackRange * 0.5F;
		}

		float closingSpeed = predictedDist;
		if (weAreFaster) {
			closingSpeed += 10.0F;
		} else {
			closingSpeed -= 10.0F;
		}

		boolean catchingUp = closingSpeed < (float) Math.random() * 30.0F;

		for (WeaponAPI weapon : weaponGroup.getWeaponsCopy()) {
			if (weapon.getAmmo() > 0) {
				allWeaponsDry = false;
				if (!weapon.isDisabled() && !weapon.hasAIHint(AIHints.NO_MANUAL_FIRE) && !(weapon.getCooldownRemaining() > 3.0F)) {
					if (weapon.hasAIHint(AIHints.STRIKE)) {
						group.setStrikeGroup(true);
					}

					float conserveThreshold = 0.0F;
					// approximation: engine checked ProjectileWeaponSpec; WeaponSpecAPI.getBurstSize()
					// covers the same weapons
					if (weapon.usesAmmo()) {
						float burstSize = weapon.getSpec().getBurstSize();
						conserveThreshold = burstSize * 9.0F;
						if (weapon.hasAIHint(AIHints.CONSERVE_1)) {
							conserveThreshold = burstSize * 1.0F;
						} else if (weapon.hasAIHint(AIHints.CONSERVE_2)) {
							conserveThreshold = burstSize * 2.0F;
						} else if (weapon.hasAIHint(AIHints.CONSERVE_3)) {
							conserveThreshold = burstSize * 3.0F;
						} else if (weapon.hasAIHint(AIHints.CONSERVE_4)) {
							conserveThreshold = burstSize * 4.0F;
						} else if (weapon.hasAIHint(AIHints.CONSERVE_5)) {
							conserveThreshold = burstSize * 5.0F;
						} else if (weapon.hasAIHint(AIHints.CONSERVE_ALL)) {
							conserveThreshold = burstSize * 10000.0F;
						} else {
							conserveThreshold = burstSize * 9.0F;
						}
					}

					if (weapon.getDerivedStats().getFluxPerSecond() <= 0.0F) {
						// intentionally kept dead: vanilla sets this flag but never reads it
						hasZeroFluxWeapon = true;
					}

					Vector2f weaponPos = weapon.getSlot().computePosition(this.ship);
					boolean fromTargetingOval = weapon.hasAIHint(AIHints.RANGE_FROM_TARGETING_OVAL);
					boolean fromShipRadius = weapon.hasAIHint(AIHints.RANGE_FROM_SHIP_RADIUS);
					if (fromTargetingOval || fromShipRadius) {
						weaponPos = this.ship.getLocation();
					}

					float weaponDist = BattleNext_ShipAIHelper.getDistance(weaponPos, targetLoc);
					if (fromTargetingOval) {
						weaponDist -= BattleNext_ShipAIHelper.getDistanceToEntitySurface(targetLoc, this.ship, false);
					} else if (fromShipRadius) {
						weaponDist -= this.ship.getCollisionRadius();
					}

					float surfaceDist = weaponDist;
					boolean guidedPoorOffset = weapon.getType() == WeaponType.MISSILE
						&& Math.abs(weapon.getSlot().getAngle()) > 5.0F
						&& weapon.hasAIHint(AIHints.GUIDED_POOR);
					boolean unaimable = (weapon.hasAIHint(AIHints.DO_NOT_AIM) || guidedPoorOffset) && (alternating || missilesAimable);
					float rangeMult = 1.0F;
					if (weapon.getType() == WeaponType.MISSILE
						&& Math.abs(weapon.getSlot().getAngle()) > 5.0F
						&& weapon.hasAIHint(AIHints.GUIDED_POOR)
						&& surfaceDist > targetRadius + this.ship.getCollisionRadius() + 400.0F) {
						unaimable = alternating || missilesAimable;
						rangeMult = 0.9F;
					}

					boolean isBomb = weapon.hasAIHint(AIHints.BOMB);
					boolean bombWaited = isBomb && this.timeInAttackRun > 3.0F;
					if (bombWaited && isBomb) {
						unaimable = true;
					}

					float extraArc = 2.0F * targetRadius / ((float) (Math.PI * 2) * weaponDist) * 360.0F;
					if (extraArc >= 180.0F) {
						extraArc = 180.0F;
					}

					if (extraArc < 0.0F) {
						extraArc = 0.0F;
					}

					if (weapon.getSlot().getArc() <= 10.0F) {
						extraArc *= 0.5F;
					}

					float weaponRange = weapon.getRange();
					if (this.ship.isFighter() && weapon.hasAIHint(AIHints.EXTRA_RANGE_ON_FIGHTER)) {
						weaponRange += fighterExtraRange;
					}

					if ((unaimable || BattleNext_ShipAIHelper.isInArc(weapon.getSlot().computeMidArcAngle(this.ship), weapon.getSlot().getArc() + extraArc, weaponPos, targetLoc))
						&& surfaceDist <= weaponRange * rangeMult + targetRadius * 0.75F) {
						inRangeCount++;
						float weight = 1.0F;
						switch (weaponSizeScore(weapon.getSize())) {
							case 1:
								weight = 1.0F;
								break;
							case 2:
								weight = 3.0F;
								break;
							case 3:
								weight = 7.0F;
						}

						if (weapon.hasAIHint(AIHints.IMPORTANT)) {
							weight += 7.0F;
						}

						if (weapon.getSlot().isHardpoint()) {
							weight *= 2.0F;
						}

						if (weapon.getType() == WeaponType.BALLISTIC) {
							weight++;
						}

						if (weapon.getType() == WeaponType.MISSILE) {
							weight += 2.0F;
						}

						if (target != null && target.isFighter()) {
							weight *= 0.2F;
						}

						boolean highExplosive = weapon.getDamageType() == DamageType.HIGH_EXPLOSIVE;
						boolean energy = weapon.getDamageType() == DamageType.ENERGY;
						boolean fragmentation = weapon.getDamageType() == DamageType.FRAGMENTATION;
						boolean kinetic = weapon.getDamageType() == DamageType.KINETIC;
						// intentionally kept dead: vanilla declares this flag but never sets it
						boolean unusedTypeFlag = false;
						boolean heatseeker = weapon.hasAIHint(AIHints.HEATSEEKER);
						float targetFluxLevel = target.getFluxLevel();
						// approximation: engine ship.getFluxAvailable() == maxFlux - currFlux
						float targetFluxAvailable = target.getMaxFlux() - target.getCurrFlux();
						if (highExplosive && shieldFacingUs) {
							weight *= 0.5F;
						} else if (highExplosive && !shieldFacingUs) {
							weight *= 2.0F;
						} else if (kinetic && shieldFacingUs) {
							weight *= 2.0F;
						} else if (fragmentation && shieldFacingUs) {
							weight *= 0.25F;
						}

						score += weight;
						if (target.isPhased() && (!isUnphasing(target) || (float) Math.random() < 0.8F) && target.getFluxLevel() < 0.97F) {
							score -= weight * 2.0F;
						}

						boolean wingStrikeWait = weapon.hasAIHint(AIHints.STRIKE)
							&& !weapon.hasAIHint(AIHints.USE_VS_FRIGATES)
							&& this.ship.isFighter()
							&& this.ship.getWing() != null
							&& !this.ship.getWingLeader().getAIFlags().hasFlag(AIFlags.IN_ATTACK_RUN);
						if (this.ship.isFighter() && this.ship.getWing() != null && this.ship.getWing().getRange() <= 0.0F) {
							wingStrikeWait = false;
						}

						if (wingStrikeWait) {
							score -= weight * 4.0F + 100.0F;
						} else if (bombWaited) {
							score += weight * 4.0F + 100.0F;
						}

						if (weapon.usesAmmo()
							&& weapon.getAmmoPerSecond() * 100.0F >= weapon.getMaxAmmo()
							&& weapon.getAmmo() <= weapon.getMaxAmmo() * 0.1F
							&& weapon.getAmmo() < 5) {
							score -= weight * 1.0F;
						}

						float shieldFluxPrediction = 0.0F;
						float missileTravelTime = 0.0F;
						if (weapon.getType() == WeaponType.MISSILE) {
							MissileSpecAPI missileSpec = requireMissileSpec(
									weapon.getSpec().getProjectileSpec(), weapon.getId());
							float missileSpeed = missileSpec.getHullSpec().getEngineSpec().getMaxSpeed();
							if (this.ship.getMutableStats() != null) {
								missileSpeed = this.ship.getMutableStats().getMissileMaxSpeedBonus().computeEffective(missileSpeed);
							}

							missileTravelTime = (surfaceDist - target.getCollisionRadius()) / Math.max(1.0F, missileSpeed + predictedDist);
							if (!target.getFluxTracker().isOverloadedOrVenting()) {
								// approximation: engine fluxTracker.getDissipationRate() == fluxStats.getFluxDissipation().getModifiedValue()
								shieldFluxPrediction = target.getMutableStats().getFluxDissipation().getModifiedValue() * missileTravelTime * 0.5F;
							}
						}

						float targetHitpoints = target.getHitpoints();
						boolean conserveForArmor = weapon.getType() == WeaponType.MISSILE && targetHitpoints * 2.0F < weapon.getDamage().getDamage();
						float targetArmorInArc = 0.0F;
						if (weapon.hasAIHint(AIHints.CONSERVE_FOR_ANTI_ARMOR)) {
							// approximation: engine target.getArmorEvaluator().getArmorInArc(facing, arc)
							float facingArmor = target.getAverageArmorInSlice(target.getFacing(), 90.0F);
							// approximation: engine target.getArmorEvaluator().getArmorInArc(facing, arc)
							facingArmor += target.getAverageArmorInSlice(toTargetAngle + 180.0F, 90.0F);
							facingArmor /= 2.0F;
							// jadx hull size switch table labels are API ordinal + 1
							float armorFloorBySize = 100.0F;
							switch (hullSizeScore(this.ship.getHullSize())) {
								case 2:
									armorFloorBySize = 0.0F;
									break;
								case 3:
									armorFloorBySize = 75.0F;
									break;
								case 4:
									armorFloorBySize = 150.0F;
									break;
								case 5:
									armorFloorBySize = 200.0F;
									break;
								case 6:
									armorFloorBySize = 300.0F;
							}

							facingArmor -= armorFloorBySize;
							targetArmorInArc = facingArmor;
						}

						if (!conserveForArmor && weapon.hasAIHint(AIHints.CONSERVE_FOR_ANTI_ARMOR)) {
							conserveForArmor = weapon.getType() == WeaponType.MISSILE
								&& weapon.getDamage().getDamage() * Math.min(3, weapon.getAmmo()) >= targetArmorInArc;
						}

						if (this.ship.isFighter()) {
							conserveForArmor = false;
						}

						float groupShieldDamage = 0.0F;

						for (WeaponAPI otherWeapon : weaponGroup.getWeaponsCopy()) {
							if (!otherWeapon.usesAmmo() || otherWeapon.getAmmo() > 0) {
								groupShieldDamage += otherWeapon.getDamage().getDamage() * weapon.getDamageType().getShieldMult();
							}
						}

						boolean conserveVsShields = weapon.getType() == WeaponType.MISSILE
							&& weapon.getAmmo() <= conserveThreshold
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& weapon.usesAmmo()
							&& (highExplosive || fragmentation || unusedTypeFlag || energy)
							&& target.getShield() != null
							&& (targetFluxAvailable + shieldFluxPrediction) / target.getShield().getFluxPerPointOfDamage() > groupShieldDamage * 1.25F
							&& (
								target.getShield().isOn() && shieldWideOpen
									|| targetFacingUs
									|| surfaceDist > 400.0F + target.getCollisionRadius()
										&& (isOmniShield(target.getShield()) || surfaceDist > 500.0F + target.getCollisionRadius())
							);
						if (target.getShield() == null && target.getPhaseCloak() == null && !target.getFluxTracker().isOverloadedOrVenting()) {
							float randomConserveChance = 0.8F;
							if (weapon.hasAIHint(AIHints.CONSERVE_FOR_ANTI_ARMOR) || weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)) {
								randomConserveChance = 0.0F;
							}

							if ((float) Math.random() < randomConserveChance) {
								conserveVsShields = true;
							}
						}

						if (weapon.hasAIHint(AIHints.CONSERVE_FOR_ANTI_ARMOR) && !this.ship.isFighter() && !target.isFighter()) {
							float armorRatio = targetArmorInArc / Math.max(target.getArmorGrid().getArmorRating(), 1.0F);
							if (armorRatio > 1.0F) {
								armorRatio = 1.0F;
							}

							if (armorRatio < 0.0F) {
								armorRatio = 0.0F;
							}

							if ((float) Math.random() < 0.9F - armorRatio * armorRatio * 1.0F) {
								conserveVsShields = true;
								score -= weight * 2.0F;
							}
						}

						float frigateConserveChance = 0.05F + this.ship.getFluxLevel() * 0.15F;
						if (target.getStationSlot() != null) {
							frigateConserveChance += 0.25F;
						}

						if (weapon.getType() == WeaponType.MISSILE
							&& !weapon.hasAIHint(AIHints.CONSERVE_FOR_ANTI_ARMOR)
							&& weapon.getAmmo() <= conserveThreshold
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& weapon.usesAmmo()
							&& (highExplosive || fragmentation || unusedTypeFlag || energy)
							&& target.getHullSize().ordinal() <= HullSize.FRIGATE.ordinal()
							&& target.getShield() == null
							&& target.getPhaseCloak() == null
							&& (float) Math.random() > frigateConserveChance) {
							conserveVsShields = true;
						}

						boolean phaseSave = false;
						if (target.getPhaseCloak() != null
							&& targetFluxLevel < 0.9F
							&& weapon.getType() == WeaponType.MISSILE
							&& weapon.getAmmo() <= conserveThreshold
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& weapon.usesAmmo()) {
							conserveVsShields = true;
						}

						if (target.getFluxTracker().isOverloadedOrVenting()
							&& !target.isFighter()
							&& weapon.getType() == WeaponType.MISSILE
							&& weapon.getSpec().getProjectileSpec() instanceof MissileSpecAPI) {
							float recoverTime;
							if (target.getFluxTracker().isOverloaded()) {
								recoverTime = target.getFluxTracker().getOverloadTimeRemaining();
							} else {
								recoverTime = target.getFluxTracker().getTimeToVent();
							}

							if (recoverTime < 1.0F) {
								recoverTime = 1.0F;
							}

							if (missileTravelTime > recoverTime - 1.0F) {
								conserveVsShields = true;
							} else {
								if (missileTravelTime > recoverTime - 2.5F) {
									group.setEvaluationInterval(savedEvalInterval * 0.25F + (float) Math.random() * 0.75F);
								}

								conserveVsShields = false;
							}
						}

						if (!heatseeker
							|| !((float) Math.random() > 0.33F)
							|| target.getShield() != null
								&& !isEMPed(target)
								&& (!target.getFluxTracker().isVenting() || !(target.getFluxTracker().getTimeToVent() > 4.0F))
								&& (!isOmniShield(target.getShield()) || !shieldTowardUs)
								&& (!isFrontShield(target.getShield()) || !(target.getShield().getArc() < 270.0F) && !(target.getShield().getActiveArc() < 45.0F))) {
							if (weapon.hasAIHint(AIHints.HEATSEEKER)) {
								conserveVsShields = true;
							}
						} else {
							conserveVsShields = false;
						}

						if (weapon.getType() == WeaponType.MISSILE && (target.getFluxLevel() > 0.5F || !shieldFacingUs) && weapon.hasAIHint(AIHints.STRIKE)) {
							conserveVsShields = false;
						}

						if (this.ship.isFighter()) {
							conserveVsShields = false;
						}

						boolean fighterVsShip = this.ship.isFighter() && !target.isFighter();
						boolean worthwhileTarget = target.getHullSize().ordinal() >= HullSize.DESTROYER.ordinal()
							|| desperate
							|| target.getStationSlot() != null
							|| weapon.hasAIHint(AIHints.USE_VS_FRIGATES) && target.getHullSize() == HullSize.FRIGATE
							|| weapon.hasAIHint(AIHints.ANTI_FTR) && target.getHullSize() == HullSize.FIGHTER;
						boolean spendAmmo = desperate;
						if (!spendAmmo && weapon.usesAmmo() && weapon.getMaxAmmo() > 0) {
							float ammoFraction = (float) weapon.getAmmo() / weapon.getMaxAmmo();
							if (ammoFraction > this.ship.getHullLevel() * 1.5F && (weapon.getAmmo() > 1 || (float) Math.random() < 0.1F)) {
								spendAmmo = true;
							}

							boolean hasDefenses = this.ship.getShield() != null || this.ship.getPhaseCloak() != null && BattleNext_ShipAIHelper.isPhaseShip(this.ship);
							float ammoRichThreshold = this.ship.getHullLevel() * 1.5F;
							if (hasDefenses) {
								ammoRichThreshold = ammoRichThreshold * 0.5F + (1.5F - this.ship.getHardFluxLevel()) * 0.5F;
							}

							if (ammoFraction > ammoRichThreshold && (weapon.getAmmo() > 1 || (float) Math.random() < 0.1F)) {
								spendAmmo = true;
							}
						}

						if (target.getHullSize() == HullSize.FRIGATE) {
							float ownHitpoints = this.ship.getHitpoints();
							float ownHpCeiling = Math.max(this.ship.getMaxHitpoints() * 0.75F, 1000.0F);
							float ownCR = this.ship.getCurrentCR();
							if (ownHitpoints < ownHpCeiling) {
								worthwhileTarget |= (float) Math.random() < 1.0F - ownHitpoints / ownHpCeiling;
								worthwhileTarget |= Math.random() < 0.05F + this.ship.getFluxLevel() * 0.15F;
							}

							if (ownCR < 0.2F) {
								worthwhileTarget |= Math.random() < 0.05F + (0.2F - ownCR);
							}
						}

						float strikeRangeCap = weapon.hasAIHint(AIHints.USE_VS_FRIGATES) ? 100 : 10;
						if (!weapon.hasAIHint(AIHints.USE_VS_FRIGATES)) {
							// jadx hull size switch table labels are API ordinal + 1;
							// case 5 falls through into case 6 in vanilla
							switch (hullSizeScore(target.getHullSize())) {
								case 5:
									strikeRangeCap += 10.0F;
								case 6:
									strikeRangeCap += 20.0F;
							}
						}

						if (weapon.getType() == WeaponType.MISSILE) {
							strikeRangeCap = Math.max(target.getCollisionRadius() * 0.5F - lateralSpeed * missileTravelTime, strikeRangeCap);
						}

						float minStrikeRange = 125.0F;
						if (weapon.hasAIHint(AIHints.GUIDED_POOR) || unaimable) {
							strikeRangeCap = 10000.0F;
							minStrikeRange = 10000.0F;
						}

						if (target.getStationSlot() != null) {
							strikeRangeCap = 100.0F;
							minStrikeRange = 100.0F;
						}

						boolean frigateMissileConserve = target.isFrigate()
							&& weapon.getType() == WeaponType.MISSILE
							&& weapon.getAmmo() <= 10
							&& weapon.getAmmoTracker().getAmmoPerSecond() <= 0.0F
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& !unaimable
							&& target.getStationSlot() == null;
						if (this.ship.isFighter()) {
							frigateMissileConserve = false;
						}

						if (spendAmmo) {
							conserveVsShields = false;
							frigateMissileConserve = false;
						}

						boolean inefficientVsShield = false;
						boolean conserveKinetic = false;
						if (kinetic
							&& !spendAmmo
							&& weapon.getType() == WeaponType.MISSILE
							&& weapon.getAmmo() <= conserveThreshold
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& weapon.usesAmmo()
							&& (!closeIn || catchingUp && !veryClose || !shieldWideOpen)) {
							conserveKinetic = true;
						}

						if (this.ship.getHullSize().ordinal() < target.getHullSize().ordinal()
							&& ((float) Math.random() < 0.1F + this.ship.getFluxLevel() * 0.9F || this.ship.getFluxLevel() - 0.15F > target.getFluxLevel())) {
							conserveKinetic = true;
						}

						if (weapon.hasAIHint(AIHints.DO_NOT_CONSERVE) || !weapon.usesAmmo()) {
							conserveKinetic = false;
						}

						if (fighterVsShip) {
							conserveKinetic = false;
						}

						if (fighterVsShip && kinetic) {
							spendAmmo = true;
						}

						if (!conserveKinetic
							&& frigateMissileConserve
							&& (float) Math.random() > 0.5F
							&& (targetSpeed < 150.0F && lateralSpeed < minStrikeRange || BattleNext_ShipAIHelper.isEngineDisabled(target) && targetSpeed < 150.0F)
							&& (worthwhileTarget || !weapon.hasAIHint(AIHints.STRIKE))
							&& (closeIn && !catchingUp || veryClose)) {
							frigateMissileConserve = false;
							strikeReady = true;
							phaseSave = true;
							score += weight;
						}

						if (weapon.hasAIHint(AIHints.USE_LESS_VS_SHIELDS) && !shieldFacingUs && (worthwhileTarget || !weapon.hasAIHint(AIHints.STRIKE))) {
							score += weight * 2.0F;
							if (BattleNext_ShipAIHelper.getTimeToRecover(target) > 1.0F) {
								score += weight * 1.0F;
							}
						}

						if (weapon.hasAIHint(AIHints.STRIKE)
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& worthwhileTarget
							&& weapon.getType() == WeaponType.MISSILE
							&& weapon.getAmmo() <= 5
							&& !this.ship.isFighter()
							&& BattleNext_ShipAIHelper.getTimeToRecover(target) < missileTravelTime) {
							strikeReady = true;
							phaseSave = true;
						}

						if (weapon.getType() == WeaponType.MISSILE
							&& weapon.getAmmo() <= conserveThreshold
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& weapon.usesAmmo()
							&& !conserveVsShields
							&& target.getPhaseCloak() != null
							&& target.getHardFluxLevel() < 0.9F) {
							strikeReady = true;
						}

						if (spendAmmo || this.ship.isFighter()) {
							strikeReady = false;
							phaseSave = false;
						}

						if (this.ship.isFighter()) {
							strikeRangeCap += 20.0F;
						}

						if (unaimable) {
							strikeRangeCap = 10000.0F;
						}

						if (spendAmmo
							|| fighterVsShip
							|| weapon.getType() != WeaponType.MISSILE
							|| strikeReady && target.isFrigate() && !frigateMissileConserve && !conserveVsShields
							|| !conserveVsShields && (!(lateralSpeed > 50.0F) || unaimable) && (!target.isFighter() || weapon.hasAIHint(AIHints.ANTI_FTR))) {
							if (!weapon.hasAIHint(AIHints.FIRE_WHEN_INEFFICIENT)
								&& targetShield != null
								&& shieldFacingUs
								&& targetShield.getFluxPerPointOfDamage()
										* target.getMutableStats().getShieldDamageTakenMult().getModifiedValue()
										* weapon.getDamageType().getShieldMult()
									< 0.5F * weapon.getSpec().getDerivedStats().getFluxPerDam()
								&& target.getFluxLevel() - 0.2F < this.ship.getFluxLevel()
								&& this.ship.getFluxLevel() > 0.4F
								&& this.ship.getHardFluxLevel() + 0.1F < this.ship.getFluxLevel()) {
								inefficientVsShield = true;
								score -= weight * 2.0F;
							} else if (weapon.hasAIHint(AIHints.HEATSEEKER) && BattleNext_ShipAIHelper.isEngineDisabled(target) && !spendAmmo) {
								score -= weight * 2.0F;
							} else if (frigateMissileConserve) {
								score -= weight * 2.0F;
							} else if (!weapon.hasAIHint(AIHints.STRIKE)
								|| worthwhileTarget && !(lateralSpeed > strikeRangeCap) && (!(weaponDist < 125.0F + targetRadius * 0.5F) || this.ship.isFighter())) {
								if (weapon.hasAIHint(AIHints.STRIKE)) {
									score += weight + 4.0F;
								} else if (conserveForArmor && (float) Math.random() < 0.93F) {
									score -= weight;
								}
							} else {
								score -= weight * 2.0F;
							}
						} else {
							score -= weight * 2.0F;
						}

						float highFluxThreshold = 0.75F;
						if (weapon.getType() == WeaponType.MISSILE
							&& (worthwhileTarget || !weapon.hasAIHint(AIHints.STRIKE))
							&& !kinetic
							&& !weapon.hasAIHint(AIHints.HEATSEEKER)
							&& (!target.isFighter() || weapon.hasAIHint(AIHints.ANTI_FTR))
							&& target.getShield() != null
							&& (target.getFluxLevel() > highFluxThreshold
								|| target.getFluxTracker().isOverloadedOrVenting() && BattleNext_ShipAIHelper.getTimeToRecover(target) > missileTravelTime + 1.0F)
							&& (closeIn && !catchingUp || veryClose)) {
							score += weight * 2.0F;
							if (weapon.hasAIHint(AIHints.CONSERVE_FOR_ANTI_ARMOR)) {
								score += weight * 4.0F;
							}
						}

						if (kinetic
							&& shieldFacingUs
							&& (worthwhileTarget || !weapon.hasAIHint(AIHints.STRIKE))
							&& !target.isFighter()
							&& weapon.getType() == WeaponType.MISSILE
							&& !weapon.hasAIHint(AIHints.ANTI_FTR)
							&& (target.isFrigate() && !frigateMissileConserve || !conserveKinetic)) {
							if (!inefficientVsShield) {
								score += weight * 2.0F;
							}
						} else if (kinetic
							&& !spendAmmo
							&& weapon.getType() == WeaponType.MISSILE
							&& weapon.getAmmo() <= conserveThreshold
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& weapon.usesAmmo()) {
							score -= weight;
						}

						if (unusedTypeFlag && shieldFacingUs && !weapon.hasAIHint(AIHints.HEATSEEKER) && !weapon.hasAIHint(AIHints.STRIKE)) {
							score -= weight;
						}

						float targetAngle = BattleNext_ShipAIHelper.getAngleFast(weaponPos, targetLoc);
						float angleError = BattleNext_ShipAIHelper.getAngleDiff(weapon.getCurrAngle(), targetAngle) - extraArc;
						if (angleError < 0.0F) {
							angleError = 0.0F;
						}

						if (weapon.getType() == WeaponType.LAUNCH_BAY || missilesAimable) {
							angleError = 0.0F;
						}

						float timeToReady = 0.0F;
						if (weapon.getTurnRate() > 0.0F) {
							timeToReady = angleError / weapon.getTurnRate();
						} else if (angleError > 0.0F) {
							timeToReady = 3.0F;
						}

						float cooldownRemaining = weapon.getCooldownRemaining();
						if (cooldownRemaining > 2.0F) {
							timeToReady += cooldownRemaining;
						}

						if (timeToReady > maxTimeToReady) {
							maxTimeToReady = timeToReady;
						}

						fluxCostTotal += weapon.getFluxCostToFire();
						if (weapon.getProjectileSpeed() > 100000.0F) {
							instantProjectileCount++;
						} else {
							projectileSpeedTotal += weapon.getProjectileSpeed();
						}

						if (weaponRange * rangeMult < group.getMinWeaponRange()) {
							group.setMinWeaponRange(weaponRange * rangeMult);
						}

						if (alternating) {
							float scoreDelta = score - lastWeaponScore;
							lastWeaponScore = score;
							if (scoreDelta > maxScoreDelta) {
								maxScoreDelta = scoreDelta;
								bestRunConservative = phaseSave;
								bestWeaponIndex = weaponGroup.getWeaponsCopy().indexOf(weapon);
								bestRunMissilesAimable = unaimable;
							}
						}
					}
				}
			}
		}

		if (alternating && bestWeaponIndex != -1 && !hasUniformWeapons) {
			group.setMissilesAimable(bestRunMissilesAimable);
			group.setCurrentWeaponIndex(bestWeaponIndex);
			WeaponAPI bestWeapon = weaponGroup.getWeaponsCopy().get(bestWeaponIndex);
			fluxCostTotal = bestWeapon.getFluxCostToFire();
			if (bestWeapon.hasAIHint(AIHints.HEATSEEKER) && !desperate) {
				group.setEvaluationInterval(0.01F);
			}

			CollisionClass bestWeaponCollision = bestWeapon.getProjectileCollisionClass();
			if (bestWeaponCollision != CollisionClass.RAY_FIGHTER
				&& bestWeaponCollision != CollisionClass.PROJECTILE_FIGHTER
				&& bestWeaponCollision != CollisionClass.MISSILE_NO_FF
				&& bestWeaponCollision != CollisionClass.PROJECTILE_NO_FF) {
				group.setCanFireSafely(true);
			} else {
				group.setCanFireSafely(false);
			}

			if (bestRunConservative) {
				group.setEvaluationInterval(0.01F);
			}

			score = maxScoreDelta;
		} else {
			if (alternating && bestWeaponIndex != -1) {
				WeaponAPI bestWeapon = weaponGroup.getWeaponsCopy().get(bestWeaponIndex);
				fluxCostTotal = bestWeapon.getFluxCostToFire();
			}

			if (strikeReady) {
				group.setEvaluationInterval(0.01F);
			}
		}

		if (allWeaponsDry) {
			this.addAttackCooldown(groupIndex, 3.0F, 3.0F);
		}

		if (!(score <= 0.0F) && !(group.getEvaluationInterval() <= 0.0F)) {
			float fluxReserve = 200.0F;
			if (fluxCostTotal + fluxReserve > this.ship.getMaxFlux() && fluxCostTotal <= this.ship.getMaxFlux()) {
				fluxReserve = (this.ship.getMaxFlux() - fluxCostTotal) / 2.0F;
			}

			// approximation: engine ship.getFluxAvailable() == maxFlux - currFlux
			float fluxAvailable = this.ship.getMaxFlux() - this.ship.getCurrFlux();
			if (fluxCostTotal > 0.0F
				&& (fluxCostTotal + fluxReserve > fluxAvailable || fluxCostTotal + fluxReserve > this.ship.getMaxFlux() && fluxCostTotal > fluxAvailable)) {
				return null;
			}

			if (inRangeCount == 0.0F) {
				return null;
			}

			if (instantProjectileCount < inRangeCount) {
				group.setRange(projectileSpeedTotal / (inRangeCount - instantProjectileCount));
			} else {
				group.setRange(Float.MAX_VALUE);
			}

			group.setShip(target);
			group.setCheckInterval(maxTimeToReady);
			score -= group.getCheckInterval() * 2.0F;
			group.setScore(score);
			if (!this.isAttackWorthwhile(group)) {
				return null;
			} else {
				return score <= 0.0F ? null : group;
			}
		} else {
			return null;
		}
	}

	private boolean isCurrentTargetValid() {
		List<ShipAPI> ships = BattleNext_ShipAIHelper.getEngine().getShips();
		FogOfWarAPI fogOfWar = BattleNext_ShipAIHelper.getEngine().getFogOfWar(this.ship.getOwner());
		return this.attackingGroup != null
			&& this.attackingGroup.getShip() != null
			&& !this.attackingGroup.getShip().isHulk()
			&& ships.contains(this.attackingGroup.getShip())
			&& (fogOfWar == null || fogOfWar.isVisible(this.attackingGroup.getShip()));
	}

	public BattleNext_WeaponGroupEvaluation getWeaponGroupEvaluation(WeaponGroupAPI group) {
		return this.weaponGroupEvaluations.get(group);
	}

	public boolean isAttackGroupReady() {
		if (this.getAttackingGroup() != null) {
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, this.getAttackingGroup().getWeaponGroupIndex());
			if (group != null) {
				BattleNext_WeaponGroupEvaluation evaluation = this.getWeaponGroupEvaluation(group);
				if (evaluation != null) {
					return !evaluation.isAllMissiles();
				}
			}
		}

		return true;
	}

	private void initWeaponGroupEvaluations() {
		for (int groupIndex = 0; groupIndex < 7; groupIndex++) {
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
			if (group != null) {
				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if (!weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
						float weaponRange = weapon.getRange();
						if (this.ship.isFighter() && weapon.hasAIHint(AIHints.EXTRA_RANGE_ON_FIGHTER)) {
							weaponRange += fighterExtraRange;
						}

						if (weaponRange > this.maxRange) {
							this.maxRange = weaponRange;
						}
					}
				}
			}
		}

		this.weaponGroupEvaluations.clear();
		this.minRange = Float.MAX_VALUE;
		this.maxThreatRange = Float.MAX_VALUE;
		this.minMissileRange = Float.MAX_VALUE;
		this.attackRange = Float.MAX_VALUE;
		this.nonMissileMaxRange = 0.0F;
		this.hasNonSmallPD = false;
		this.hasNonMissileWeapon = false;
		float bestMaxRange = 0.0F;
		float bestScore = 0.0F;
		float bestNonMissileScore = 0.0F;
		int evalIndex = 0;

		for (int groupIndex = 0; groupIndex < 7; groupIndex++) {
			BattleNext_WeaponGroupEvaluation evaluation = new BattleNext_WeaponGroupEvaluation();
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
			if (group != null) {
				float score = 0.0F;
				float minGroupRange = 10000.0F;
				float dps = 0.0F;
				float fluxPerSecond = 0.0F;
				int weaponCount = 0;
				int turretCount = 0;
				int hardpointCount = 0;
				boolean ammoLow = false;
				boolean strikeGroup = false;
				boolean allMissiles = true;
				boolean hasAimable = false;
				boolean noUsableWeapons = true;

				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if (weapon.getAmmo() > 0 && !weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
						noUsableWeapons = false;
						if (weapon.getAmmo() != Integer.MAX_VALUE && !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE) && weapon.getAmmoTracker().getAmmoPerSecond() <= 0.0F) {
							ammoLow = true;
						}

						if (weapon.hasAIHint(AIHints.STRIKE)) {
							strikeGroup = true;
						}

						if (weapon.getType() != WeaponType.MISSILE) {
							allMissiles = false;
						}

						float weaponRange = weapon.getRange();
						if (this.ship.isFighter() && weapon.hasAIHint(AIHints.EXTRA_RANGE_ON_FIGHTER)) {
							weaponRange += fighterExtraRange;
						}

						if (weaponRange > bestMaxRange) {
							bestMaxRange = weaponRange;
						}

						dps += weapon.getSpec().getDerivedStats().getDps();
						if (weapon.hasAIHint(AIHints.PD)) {
							fluxPerSecond += weapon.getSpec().getDerivedStats().getFluxPerSecond() * 0.5F;
							fluxPerSecond += weapon.getSpec().getDerivedStats().getSustainedFluxPerSecond() * 0.5F;
						} else {
							fluxPerSecond += weapon.getSpec().getDerivedStats().getFluxPerSecond();
						}

						weaponCount++;
						if (weapon.getSlot().isHardpoint()) {
							hardpointCount++;
						}

						if (weapon.getSlot().isTurret()) {
							turretCount++;
						}

						if (weapon.getSlot().isHidden()) {
							if (weapon.getSlot().getArc() <= 10.0F) {
								hardpointCount++;
							} else {
								turretCount++;
							}
						}

						float weight = 1.0F;
						switch (weaponSizeScore(weapon.getSize())) {
							case 1:
								weight = 1.0F;
								break;
							case 2:
								weight = 3.0F;
								break;
							case 3:
								weight = 7.0F;
						}

						if (weapon.getSlot().isHardpoint()) {
							weight *= 2.0F;
						}

						if (weapon.getType() == WeaponType.BALLISTIC) {
							weight++;
						}

						if (weapon.getType() == WeaponType.MISSILE) {
							weight += 2.0F;
						}

						if (weapon.hasAIHint(AIHints.PD_ONLY)) {
							weight *= 0.1F;
						}

						if (weapon.hasAIHint(AIHints.PD)) {
							weight *= 0.25F;
						}

						float forwardOffset = 0.0F;
						boolean coversForward = BattleNext_ShipAIHelper.isInArc(weapon.getSlot().getAngle(), weapon.getArc(), 0.0F);
						if (!coversForward) {
							float clampedAngle = BattleNext_ShipAIHelper.clampAngleToArc(weapon.getSlot().getAngle(), weapon.getArc(), 0.0F);
							float angleFromForward = BattleNext_ShipAIHelper.getAngleDiff(clampedAngle, 0.0F);
							if (weapon.hasAIHint(AIHints.PD)) {
								weight *= 0.5F * (180.0F - angleFromForward) / 180.0F;
							} else {
								weight *= 1.0F * (180.0F - angleFromForward) / 180.0F;
							}
						} else {
							// approximation: engine slot.getNode().getLocation() == API slot.getLocation()
							forwardOffset = this.ship.getCollisionRadius() * 0.6F - weapon.getSlot().getLocation().x;
							if (forwardOffset < 0.0F) {
								forwardOffset = 0.0F;
							}
						}

						if (weaponRange < minGroupRange) {
							minGroupRange = Math.max(100.0F, weaponRange - forwardOffset);
						}

						score += weight;
						float conserveThreshold = 0.0F;
						if (weapon.usesAmmo()) {
							// approximation: engine checked ProjectileWeaponSpec; WeaponSpecAPI.getBurstSize()
							// covers the same weapons
							float burstSize = weapon.getSpec().getBurstSize();
							conserveThreshold = burstSize * 5.0F;
						}

						if (weapon.getType() == WeaponType.MISSILE
							&& weapon.getAmmo() <= conserveThreshold
							&& !weapon.hasAIHint(AIHints.DO_NOT_CONSERVE)
							&& (!this.ship.isFighter() || !weapon.hasAIHint(AIHints.STRIKE))) {
							score -= weight * 2.0F;
						}

						if (weapon.usesEnergy()) {
							evaluation.setUsesEnergy(true);
						}

						if (weaponRange > evaluation.getMaxRange()) {
							evaluation.setMaxRange(weaponRange);
						}

						if (weaponRange < evaluation.getMinRange()) {
							evaluation.setMinRange(weaponRange);
						}

						if (weapon.hasAIHint(AIHints.PD)) {
							evaluation.setPDGroup(true);
						}

						if (!weapon.getSpec().getAIHints().contains(AIHints.PD) || weapon.getSlot().getArc() <= 10.0F) {
							if (weaponRange < this.minRange) {
								this.minRange = weaponRange;
							}

							if (weapon.getType() != WeaponType.MISSILE) {
								this.hasNonMissileWeapon = true;
							}
						}

						if (weapon.getSpec().getAIHints().contains(AIHints.PD) && weapon.getSize() != WeaponSize.SMALL) {
							this.hasNonSmallPD = true;
						}

						if (weaponRange < this.maxThreatRange) {
							this.maxThreatRange = weaponRange;
						}

						if (weapon.getType() != WeaponType.MISSILE && weaponRange > this.nonMissileMaxRange) {
							this.nonMissileMaxRange = weaponRange;
						}

						if (weapon.getType() == WeaponType.MISSILE && weaponRange < this.minMissileRange) {
							this.minMissileRange = weaponRange;
						}

						if (weapon.getSlot().isHardpoint() && !weapon.hasAIHint(AIHints.DO_NOT_AIM)) {
							hasAimable = true;
						}
					}
				}

				if (noUsableWeapons) {
					this.addAttackCooldown(groupIndex, 3.0F, 3.0F);
				}

				evaluation.setHasAimableWeapons(hasAimable);
				if (weaponCount == 0) {
					evaluation.setMinRange(0.0F);
					evaluation.setMaxRange(0.0F);
					minGroupRange = 0.0F;
				}

				evaluation.setFluxPerSecond(fluxPerSecond);
				evaluation.setDps(dps);
				evaluation.setAllMissiles(allMissiles);
				evaluation.setStrikeGroup(strikeGroup);
				evaluation.setAmmoLow(ammoLow);
				evaluation.setWeaponGroupIndex(evalIndex++);
				evaluation.setWeaponCount(weaponCount);
				evaluation.setTurretCount(turretCount);
				evaluation.setHardpointCount(hardpointCount);
				this.weaponGroupEvaluations.put(group, evaluation);
				if (score > bestScore || score == bestScore && minGroupRange < this.bestGroupRange) {
					bestScore = score;
					this.bestWeaponGroup = group;
					this.bestGroupRange = minGroupRange;
				}

				if (!allMissiles && score > bestNonMissileScore) {
					bestNonMissileScore = score;
					this.bestWeaponGroupEvaluation = evaluation;
					this.attackRange = minGroupRange;
				}
			}
		}

		if (this.ship.getSystem() != null) {
			ShipSystemAPI system = this.ship.getSystem();
			ShipSystemSpecAPI systemSpec = system.getSpecAPI();
			float threatAmount = systemSpec.getThreatAmount();
			if (threatAmount > 0.0F) {
				float threatRange = systemSpec.getThreatRange(this.ship.getMutableStats());
				if (threatRange < this.minRange && threatAmount > 4.0F) {
					this.minRange = threatRange;
				}

				if (threatRange < this.maxThreatRange) {
					this.maxThreatRange = threatRange;
				}
			}
		}

		if (this.ship.getPhaseCloak() != null) {
			ShipSystemAPI phaseCloak = this.ship.getPhaseCloak();
			ShipSystemSpecAPI phaseSpec = phaseCloak.getSpecAPI();
			float threatAmount = phaseSpec.getThreatAmount();
			if (threatAmount > 0.0F) {
				float threatRange = phaseSpec.getThreatRange(this.ship.getMutableStats());
				if (threatRange < this.minRange && threatAmount > 4.0F) {
					this.minRange = threatRange;
				}

				if (threatRange < this.maxThreatRange) {
					this.maxThreatRange = threatRange;
				}
			}
		}

		if (this.attackRange < this.minRange) {
			this.minRange = this.attackRange;
		}

		if (this.attackRange < this.maxThreatRange) {
			this.maxThreatRange = this.attackRange;
		}

		if (this.minMissileRange > 100000.0F) {
			this.minMissileRange = this.attackRange;
		}

		if (this.attackRange > 100000.0F) {
			this.attackRange = 2000.0F;
		}

		this.maxRange = bestMaxRange;
	}

	public float getMinMissileRange() {
		return this.minMissileRange;
	}

	public boolean hasNonSmallPD() {
		return this.hasNonSmallPD;
	}

	public boolean hasNonMissileWeapon() {
		return this.hasNonMissileWeapon;
	}

	public float getNonMissileMaxRange() {
		return this.nonMissileMaxRange;
	}

	public float getMinRange() {
		return this.minRange;
	}

	public float getMaxThreatRange() {
		return this.maxThreatRange;
	}

	public float getAttackRange() {
		return this.attackRange < 0.0F ? this.bestGroupRange : this.attackRange;
	}

	public WeaponGroupAPI getBestWeaponGroup() {
		return this.bestWeaponGroup;
	}

	public void renderAimLine() {
		if (this.aimPoint != null) {
			BattleNext_GeometryRenderer.drawPoint(this.aimPoint.x - this.ship.getLocation().x,
					this.aimPoint.y - this.ship.getLocation().y, 20.0F, Color.RED, 1f);
		}
	}

	public float getAimAngleForShip(float facing, ShipAPI target) {
		Vector2f aimVector = BattleNext_ShipAIHelper.getUnitVectorAtAngle(facing);
		aimVector.scale(BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), target.getLocation()) * 0.65F - target.getCollisionRadius());
		Vector2f.add(aimVector, this.ship.getLocation(), aimVector);
		return this.computeWeaponScoreAtPoint(aimVector, 1.0F);
	}

	public float getAimAngleForPoint(Vector2f point, float leadTime) {
		float distance = BattleNext_ShipAIHelper.getDistance(this.ship.getLocation(), point);
		return this.getBestAimAngle(leadTime, distance);
	}

	public float getBestAimAngle(float leadTime, float distance) {
		float radius = distance;
		float bestAngle = 0.0F;
		float bestScore = 0.0F;
		float bestFacingDiff = Float.MAX_VALUE;

		for (float angle = 0.0F; angle < 360.0F; angle += 10.0F) {
			Vector2f point = BattleNext_ShipAIHelper.getUnitVectorAtAngle(angle);
			point.scale(radius);
			Vector2f.add(point, this.ship.getLocation(), point);
			float score = this.computeWeaponScoreAtPoint(point, leadTime);
			if (score >= bestScore) {
				float facingDiff = BattleNext_ShipAIHelper.getAngleDiff(angle, this.ship.getFacing());
				if (score > bestScore || facingDiff < bestFacingDiff) {
					bestAngle = angle;
					bestScore = score;
					bestFacingDiff = facingDiff;
				}
			}
		}

		return BattleNext_ShipAIHelper.normalizeAngle(bestAngle - this.ship.getFacing());
	}

	private float computeWeaponScoreAtPoint(Vector2f point, float leadTime) {
		float score = 0.0F;

		for (int groupIndex = 0; groupIndex < 7; groupIndex++) {
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
			if (group != null) {
				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if (this.isWeaponOnStandingOrders(weapon)
						&& !weapon.isDisabled()
						&& weapon.getAmmo() > 0
						&& !(weapon.getCooldownRemaining() > 5.0F)
						&& !weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
						Vector2f weaponPos = weapon.getSlot().computePosition(this.ship);
						boolean fromTargetingOval = weapon.hasAIHint(AIHints.RANGE_FROM_TARGETING_OVAL);
						boolean fromShipRadius = weapon.hasAIHint(AIHints.RANGE_FROM_SHIP_RADIUS);
						if (fromTargetingOval || fromShipRadius) {
							weaponPos = this.ship.getLocation();
						}

						float weaponDist = BattleNext_ShipAIHelper.getDistance(weaponPos, point);
						if (fromTargetingOval) {
							weaponDist -= BattleNext_ShipAIHelper.getDistanceToEntitySurface(point, this.ship, false);
						} else if (fromShipRadius) {
							weaponDist -= this.ship.getCollisionRadius();
						}

						boolean guidedPoorOffset = weapon.getType() == WeaponType.MISSILE
							&& Math.abs(weapon.getSlot().getAngle()) > 5.0F
							&& weapon.hasAIHint(AIHints.GUIDED_POOR);
						boolean unaimable = weapon.hasAIHint(AIHints.DO_NOT_AIM) || guidedPoorOffset;
						if (weapon.getType() != WeaponType.MISSILE || !unaimable) {
							float extraArc = 2.0F * leadTime / ((float) (Math.PI * 2) * weaponDist) * 360.0F;
							if (weapon.getSlot().isHardpoint()) {
								extraArc += 20.0F;
							}

							float directDist = BattleNext_ShipAIHelper.getDistance(weaponPos, point);
							float forwardBonus = 0.0F;
							float midArcAngle = weapon.getSlot().computeMidArcAngle(this.ship);
							if (BattleNext_ShipAIHelper.getAngleDiff(midArcAngle, this.ship.getFacing()) < 15.0F) {
								forwardBonus = 300.0F;
							}

							float weaponRange = weapon.getRange();
							if (this.ship.isFighter() && weapon.hasAIHint(AIHints.EXTRA_RANGE_ON_FIGHTER)) {
								weaponRange += fighterExtraRange;
							}

							if (unaimable || BattleNext_ShipAIHelper.isInArc(midArcAngle, weapon.getSlot().getArc() + extraArc, weaponPos, point)
								&& directDist <= weaponRange + leadTime * 0.75F + forwardBonus) {
								float weight = 1.0F;
								switch (weaponSizeScore(weapon.getSize())) {
									case 1:
										weight = 1.0F;
										break;
									case 2:
										weight = 3.0F;
										break;
									case 3:
										weight = 7.0F;
								}

								score += weight;
							}
						}
					}
				}
			}
		}

		return score;
	}

	public boolean isAttackWorthwhile(BattleNext_AttackingGroup group) {
		if (!this.ship.isFighter() && !this.ship.isDrone()) {
			if (this.ship.isStationModule()) {
				return true;
			}

			if (this.ship.getShield() == null) {
				return true;
			}

			if (group == null) {
				return false;
			}

			if (group.getShip() == null) {
				return false;
			}

			ShipAPI target = group.getShip();
			ShieldAPI targetShield = target.getShield();
			if (target.getFluxTracker().isOverloadedOrVenting()) {
				return true;
			}

			if (target.isFighter()) {
				return true;
			}

			boolean shieldFacingUs = targetShield != null
				&& targetShield.isOn()
				&& BattleNext_ShipAIHelper.isInArc(targetShield.getFacing(), targetShield.getActiveArc(), target.getLocation(), this.ship.getLocation());
			if (!shieldFacingUs) {
				return true;
			}

			String personality = BattleNext_ShipAIHelper.getPersonality(this.ship);
			boolean reckless = "reckless".equals(personality);
			boolean aggressive = "aggressive".equals(personality);
			boolean steady = "steady".equals(personality);
			boolean cautious = "cautious".equals(personality);
			boolean timid = "timid".equals(personality);
			float personalityBias = 0.0F;
			if (reckless) {
				personalityBias = -0.2F;
			} else if (aggressive) {
				personalityBias = -0.1F;
			} else if (steady) {
				personalityBias = 0.0F;
			} else if (cautious) {
				personalityBias = 0.1F;
			} else if (timid) {
				personalityBias = 0.2F;
			}

			int groupIndex = group.getWeaponGroupIndex();
			WeaponGroupAPI weaponGroup = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, groupIndex);
			float softFluxLevel = Math.max(this.ship.getFluxLevel() - this.ship.getHardFluxLevel(), 0.0F);
			float fluxHeadroom = 1.0F - this.ship.getFluxLevel();
			float burstFluxFraction = 0.0F;
			float energyWeaponCount = 0.0F;

			for (WeaponAPI weapon : weaponGroup.getWeaponsCopy()) {
				if (!weapon.isDisabled() && weapon.getAmmo() > 0 && weapon.usesEnergy() && !weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
					burstFluxFraction += weapon.getFluxCostToFire();
					energyWeaponCount++;
				}
			}

			burstFluxFraction /= Math.max(1.0F, this.ship.getMaxFlux());
			if (isAlternating(weaponGroup) && energyWeaponCount > 0.0F) {
				burstFluxFraction /= energyWeaponCount;
			}

			fluxHeadroom -= burstFluxFraction;
			if (fluxHeadroom < 0.03F) {
				return false;
			}

			float ownFluxLevel = this.ship.getFluxLevel();
			// approximation: engine target.getFluxAvailable() == maxFlux - currFlux
			float targetFluxPressure = 1.0F - (target.getMaxFlux() - target.getCurrFlux()) / Math.max(this.ship.getMaxFlux(), 1.0F);
			if (targetFluxPressure < 0.0F) {
				targetFluxPressure = 0.0F;
			}

			float efficiency = 1.0F;
			efficiency *= 0.75F + ownFluxLevel + (softFluxLevel - 1.0F * fluxHeadroom) * 1.0F - targetFluxPressure + personalityBias;
			if (softFluxLevel <= 0.0F) {
				efficiency = 1.0F - fluxHeadroom * 10.0F;
				if (efficiency < 0.0F) {
					efficiency = 0.0F;
				}
			}

			float score = 0.0F;

			for (WeaponAPI weapon : weaponGroup.getWeaponsCopy()) {
				if (!weapon.isDisabled() && weapon.getAmmo() > 0 && !weapon.hasAIHint(AIHints.NO_MANUAL_FIRE)) {
					float weight = 1.0F;
					switch (weaponSizeScore(weapon.getSize())) {
						case 1:
							weight = 1.0F;
							break;
						case 2:
							weight = 3.0F;
							break;
						case 3:
							weight = 7.0F;
					}

					float fluxPerDamageVsShield = 1.0F;
					if (targetShield != null) {
						fluxPerDamageVsShield = targetShield.getFluxPerPointOfDamage()
							* target.getMutableStats().getShieldDamageTakenMult().getModifiedValue()
							* weapon.getDamageType().getShieldMult();
					}

					float fluxPerDamage = 1.0F * weapon.getSpec().getDerivedStats().getFluxPerDam();
					fluxPerDamage *= efficiency;
					if (weapon.hasAIHint(AIHints.USE_LESS_VS_SHIELDS) && (weapon.getAmmo() < weapon.getMaxAmmo() || weapon.getAmmo() <= 1)) {
						fluxPerDamage = 100.0F;
					}

					if (!weapon.hasAIHint(AIHints.FIRE_WHEN_INEFFICIENT) && shieldFacingUs && fluxPerDamageVsShield < fluxPerDamage) {
						score -= weight;
					} else {
						score += weight;
					}
				}
			}

			return score >= 0.0F;
		} else {
			return true;
		}
	}

	// approximation: engine Ship.isUnphasing() == phase cloak charge state == OUT; not exposed on ShipAPI
	private static boolean isUnphasing(ShipAPI ship) {
		return ship.getPhaseCloak() != null && ship.getPhaseCloak().getState() == ShipSystemAPI.SystemState.OUT;
	}

	// approximation: engine Ship.isEMPed() == fluxTracker.isOverloaded()
	private static boolean isEMPed(ShipAPI ship) {
		return ship.getFluxTracker().isOverloaded();
	}

	// approximation: engine Shield.isOmni() == type OMNI
	private static boolean isOmniShield(ShieldAPI shield) {
		return shield != null && shield.getType() == ShieldAPI.ShieldType.OMNI;
	}

	// approximation: engine Shield.isFront() == type FRONT
	private static boolean isFrontShield(ShieldAPI shield) {
		return shield != null && shield.getType() == ShieldAPI.ShieldType.FRONT;
	}

	// approximation: engine Ship.hasNoDefenses()
	private static boolean hasNoDefenses(ShipAPI ship) {
		return ship.getShield() == null
			&& ship.getPhaseCloak() == null
			&& (ship.getSystem() == null || !ship.getSystem().getId().equals(ship.getHullSpec().getShipDefenseId()));
	}

	// replaces the decompiler's weaponSizeScores() lookup table: value is WeaponSize ordinal + 1
	private static int weaponSizeScore(WeaponSize size) {
		return size.ordinal() + 1;
	}

	// replaces the decompiler's getHullSizeScores() lookup table: value is HullSize ordinal + 1
	// (the jadx table labels sit one above the API ordinals; the engine enum itself matches the API)
	private static int hullSizeScore(HullSize size) {
		return size.ordinal() + 1;
	}

	private static class PendingAttackRun {
		private int groupIndex;
		private float timeRemaining;

		private int getGroupIndex() {
			return this.groupIndex;
		}

		private void setGroupIndex(int groupIndex) {
			this.groupIndex = groupIndex;
		}

		private float getTimeRemaining() {
			return this.timeRemaining;
		}

		private void setTimeRemaining(float timeRemaining) {
			this.timeRemaining = timeRemaining;
		}
	}
}
