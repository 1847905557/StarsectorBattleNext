package data.scripts.ai.vanilla.attack;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ShipStatisticsTracker;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of engine class com.fs.starfarer.combat.ai.attack.AutofireManagerV2.
 * Toggles weapon group autofire based on flux headroom, threat and ammo state.
 */
public class BattleNext_AutofireManagerV2 implements BattleNext_AutofireManager {
	private ShipAPI ship;
	private Map<WeaponGroupAPI, BattleNext_WeaponGroupEvaluation> weaponGroupEvaluations = new LinkedHashMap<>();
	private IntervalUtil autofireIntervalTracker;
	private final ShipwideAIFlags flags;
	// kept for structural fidelity: vanilla assigns it but never reads it in this class
	private CombatFleetManagerAPI fleetManager;

	public BattleNext_AutofireManagerV2(ShipAPI ship, Map<WeaponGroupAPI, BattleNext_WeaponGroupEvaluation> weaponGroupEvaluations, ShipwideAIFlags flags) {
		this.ship = ship;
		this.weaponGroupEvaluations = weaponGroupEvaluations;
		this.flags = flags;
		this.autofireIntervalTracker = new IntervalUtil(0.4F, 0.7F);
		this.fleetManager = BattleNext_ShipAIHelper.getEngine().getFleetManager(ship.getOriginalOwner());
	}

	@Override
	public void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector) {
		this.autofireIntervalTracker.advance(amount);
		if (this.autofireIntervalTracker.intervalElapsed()) {
			if (this.ship.isHoldFire()) {
				this.ship.giveCommand(ShipCommand.HOLD_FIRE, null, -1);
			}

			// approximation: engine ship.getStatistics().getFluxLevelHistory().fitTrend(5.0F)
			BattleNext_ShipStatisticsTracker.BattleNext_TrendFit fluxTrend = BattleNext_ShipAIHelper.getStatisticsTracker(this.ship).fitFluxLevel(5.0F);
			float fluxLevel = this.ship.getFluxLevel();
			// approximation: engine fluxTracker.getDissipationRate() == fluxStats.getFluxDissipation().getModifiedValue()
			float dissipation = this.ship.getMutableStats().getFluxDissipation().getModifiedValue();
			if (this.ship.getShield() != null && this.ship.getShield().isOn()) {
				// approximation: engine ship.getShieldUpkeep() delegates to Shield.getUpkeep()
				dissipation -= this.ship.getShield().getUpkeep();
			}

			if (this.ship.getPhaseCloak() != null && this.ship.getPhaseCloak().isOn()) {
				dissipation -= this.ship.getPhaseCloak().getFluxPerSecond();
			}

			if (this.ship.getSystem() != null && this.ship.getSystem().isOn()) {
				dissipation -= this.ship.getSystem().getFluxPerSecond();
			}

			boolean positiveNetDissipation = false;
			if (fluxLevel < 0.9F
				&& fluxLevel <= this.ship.getHardFluxLevel() + 0.01F
				&& dissipation > 0.0F
				&& (this.ship.getShield() != null && this.ship.getShield().isOn() || this.ship.isPhased())) {
				positiveNetDissipation = true;
			}

			if (fluxLevel < 0.9F && this.ship.getShield() == null && !BattleNext_ShipAIHelper.isPhaseShip(this.ship) && dissipation > 0.0F) {
				positiveNetDissipation = true;
			}

			// intentionally kept dead: vanilla sorts an always-empty list and the deobf comparator
			// method was named compareByFluxPerSecond instead of compare
			ArrayList<BattleNext_WeaponGroupEvaluation> sortedDeadList = new ArrayList<>();
			sortedDeadList.sort((one, two) -> (int)Math.signum(one.getFluxPerSecond() - two.getFluxPerSecond()));
			float spentFluxBudget = 0.0F;

			for (BattleNext_WeaponGroupEvaluation evaluation : this.weaponGroupEvaluations.values()) {
				WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, evaluation.getWeaponGroupIndex());
				if (group != null) {
					if (!this.flags.hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_FLUX)) {
						boolean keepOn = (avoidanceVector != null || this.ship.getFluxLevel() < 0.95F) && evaluation.isPDGroup();
						if (group.isAutofiring()) {
							float burstingWeight = 0.0F;
							float totalWeight = 0.0F;

							for (WeaponAPI weapon : group.getWeaponsCopy()) {
								float weight = 0.0F;
								switch (weaponSizeScore(weapon.getSize())) {
									case 1:
										weight = 1.0F;
										break;
									case 2:
										weight = 2.0F;
										break;
									case 3:
										weight = 4.0F;
								}

								if (weapon.isInBurst() && weapon.getSpec().getBurstSize() > 1
									|| weapon.getSpec().isBurstBeam() && weapon.isFiring() && weapon.getCooldownRemaining() <= 0.0F) {
									burstingWeight += weight;
								}

								totalWeight += weight;
							}

							if (totalWeight < 1.0F) {
								totalWeight = 1.0F;
							}

							if (burstingWeight / totalWeight >= 0.5F) {
								keepOn = true;
							}
						}

						if (keepOn) {
							if (!group.isAutofiring()) {
								group.toggleOn();
							}

							spentFluxBudget += evaluation.getFluxPerSecond();
						} else {
							float fluxSlopeAllowance = Math.max(0.1F, 0.5F - fluxTrend.slope);
							if (positiveNetDissipation) {
								fluxSlopeAllowance = 2.0F;
							}

							boolean ammoLow = evaluation.isAmmoLow() && !evaluation.usesEnergy();
							boolean fluxOk = !evaluation.usesEnergy() || evaluation.getFluxPerSecond() < (dissipation - spentFluxBudget) * fluxSlopeAllowance;
							if (positiveNetDissipation && !fluxOk) {
								float burstFluxCost = 0.0F;

								for (WeaponAPI weapon : group.getWeaponsCopy()) {
									burstFluxCost += weapon.getFluxCostToFire();
								}

								float fluxLevelNow = this.ship.getFluxLevel();
								// approximation: engine ship.getFluxAvailable() == maxFlux - currFlux
								fluxOk |= fluxLevelNow < 0.9F && burstFluxCost < (this.ship.getMaxFlux() - this.ship.getCurrFlux()) * 0.33F;
							}

							if (!fluxOk && this.flags.hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS)) {
								if (group.isAutofiring() && !keepOn && evaluation.usesEnergy()) {
									group.toggleOff();
								}
							} else if (!this.isWeaponGroupUsable(group)) {
								if (group.isAutofiring()) {
									group.toggleOff();
								}
							} else if (fluxOk && !ammoLow) {
								if (!group.isAutofiring()) {
									group.toggleOn();
								}

								spentFluxBudget += evaluation.getFluxPerSecond();
							} else {
								// approximation: engine ship.getFluxAvailable() == maxFlux - currFlux
								float fluxAvailable = this.ship.getMaxFlux() - this.ship.getCurrFlux();
								float groupFluxRate = evaluation.getFluxPerSecond();
								float runtime = 30.0F;
								if (groupFluxRate > 0.0F) {
									runtime = fluxAvailable / groupFluxRate;
									if (evaluation.isPDGroup()) {
										runtime *= 1.5F;
									}
								}

								if (runtime < 10.0F) {
									if (group.isAutofiring()) {
										group.toggleOff();
									}
								} else if (group.isAutofiring() && fluxTrend.slope < 0.0F) {
									spentFluxBudget += evaluation.getFluxPerSecond();
								} else {
									float targetFluxLevel = 2.0F;
									float fluxMargin = 0.2F;
									if (threatEvalAI != null) {
										BattleNext_ThreatData maxThreat = threatEvalAI.getMaxThreatData();
										if (maxThreat != null && maxThreat.getShip().getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
											float ownSize = this.ship.getHullSize().ordinal();
											float threatSize = maxThreat.getShip().getHullSize().ordinal();
											if (this.ship.getParentStation() != null) {
												ownSize = this.ship.getParentStation().getHullSize().ordinal();
											}

											if (maxThreat.getShip().getParentStation() != null) {
												threatSize = maxThreat.getShip().getParentStation().getHullSize().ordinal();
											}

											float sizeAdvantage = ownSize - threatSize + 1.0F;
											if (sizeAdvantage < 0.0F) {
												sizeAdvantage = 0.0F;
											}

											fluxMargin += 0.07F * sizeAdvantage;
											targetFluxLevel += 0.5F * (5.0F - sizeAdvantage);
											if (this.ship.getMaxSpeed() + 20.0F > maxThreat.getShip().getMaxSpeed() || maxThreat.getShip().isFighter()) {
												fluxMargin += 0.25F;
											}
										}
									}

									fluxMargin -= fluxTrend.slope * 5.0F;
									if (fluxMargin < 0.0F) {
										fluxMargin = 0.0F;
									}

									if (fluxLevel > fluxMargin) {
										if (ammoLow) {
											group.toggleOn();
										} else {
											group.toggleOff();
										}
									} else if (fluxLevel < Math.max(0.05F, fluxMargin - 0.1F)) {
										if (ammoLow) {
											group.toggleOff();
										} else {
											group.toggleOn();
											spentFluxBudget += evaluation.getFluxPerSecond();
										}
									}
								}
							}
						}
					} else if (group.isAutofiring() && evaluation.usesEnergy()) {
						group.toggleOff();
					}
				}
			}
		}
	}

	private boolean isWeaponGroupUsable(WeaponGroupAPI group) {
		byte usableScore = 0;
		byte unusableScore = 0;
		float weightTotal = 0.0F;

		for (WeaponAPI weapon : group.getWeaponsCopy()) {
			switch (weaponSizeScore(weapon.getSize())) {
				case 1:
					weightTotal++;
					break;
				case 2:
					weightTotal += 2.0F;
					break;
				case 3:
					weightTotal += 4.0F;
			}
		}

		float shipWeightTotal = 0.0F;

		for (WeaponAPI weapon : this.ship.getUsableWeapons()) {
			switch (weaponSizeScore(weapon.getSize())) {
				case 1:
					shipWeightTotal++;
					break;
				case 2:
					shipWeightTotal += 2.0F;
					break;
				case 3:
					shipWeightTotal += 4.0F;
			}
		}

		for (WeaponAPI weapon : group.getWeaponsCopy()) {
			if (weapon.hasAIHint(AIHints.STRIKE) && weapon.usesAmmo() && weapon.getAmmoPerSecond() < 0.05F) {
				return false;
			}

			if (!weapon.usesAmmo() || weapon.getAmmo() > 0) {
				boolean usable = true;
				if (!weapon.hasAIHint(AIHints.PD)) {
					boolean guidedEnough = weapon.getAmmo() > 20 && !weapon.hasAIHint(AIHints.GUIDED_POOR) && !weapon.hasAIHint(AIHints.DO_NOT_AIM);
					guidedEnough |= weapon.hasAIHint(AIHints.NO_MANUAL_FIRE);
					if (weapon.getType() == WeaponType.MISSILE && !guidedEnough) {
						usable = false;
					}
				}

				byte sizeScore = 0;
				switch (weaponSizeScore(weapon.getSize())) {
					case 1:
						sizeScore = 1;
						break;
					case 2:
						sizeScore = 3;
						break;
					case 3:
						sizeScore = 7;
				}

				if (usable) {
					usableScore += sizeScore;
				} else {
					unusableScore += sizeScore;
				}
			}
		}

		return usableScore >= unusableScore && usableScore > 0;
	}

	// replaces the decompiler's weaponSizeScores() lookup table: value is WeaponSize ordinal + 1
	private static int weaponSizeScore(WeaponSize size) {
		return size.ordinal() + 1;
	}
}
