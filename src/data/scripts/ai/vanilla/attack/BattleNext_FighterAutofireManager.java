package data.scripts.ai.vanilla.attack;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import org.lwjgl.util.vector.Vector2f;

import java.util.Map;

/** Port of com.fs.starfarer.combat.ai.attack.FighterAutofireManager. */
public class BattleNext_FighterAutofireManager implements BattleNext_AutofireManager {
	private final ShipAPI ship;
	private final Map<WeaponGroupAPI, BattleNext_WeaponGroupEvaluation> weaponGroupEvaluations;
	private final IntervalUtil autofireIntervalTracker = new IntervalUtil(0.4F, 0.7F);
	private final ShipwideAIFlags flags;

	public BattleNext_FighterAutofireManager(ShipAPI ship, Map<WeaponGroupAPI, BattleNext_WeaponGroupEvaluation> weaponGroupEvaluations, ShipwideAIFlags flags) {
		this.ship = ship;
		this.weaponGroupEvaluations = weaponGroupEvaluations;
		this.flags = flags;
	}

	public float getThreatFactor(BattleNext_ThreatEvalAI threatEvalAI) {
		boolean shieldOn = this.ship.getShield() != null && this.ship.getShield().isOn();
		float factor = 0.2F;
		if (shieldOn && this.ship.getHullSize().ordinal() > HullSize.FRIGATE.ordinal()) {
			factor = 0.2F;
		}

		if (threatEvalAI != null) {
			BattleNext_ThreatData maxThreat = threatEvalAI.getMaxThreatData();
			if (maxThreat != null && maxThreat.getShip().getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
				float sizeDifference = maxThreat.getShip().getHullSize().ordinal() - this.ship.getHullSize().ordinal() + 1.0F;
				factor += 0.07F * sizeDifference;
				if (this.ship.getMaxSpeed() + 20.0F < maxThreat.getShip().getMaxSpeed() && !maxThreat.getShip().isFighter()) {
					factor += 0.25F;
				}
			}
		}

		return 1.0F - factor;
	}

	@Override
	public void advance(float amount, BattleNext_ThreatEvalAI threatEvalAI, Vector2f avoidanceVector) {

		this.autofireIntervalTracker.advance(amount);
		if (!this.autofireIntervalTracker.intervalElapsed()) return;

		if (this.ship.isHoldFire()) {
			this.ship.giveCommand(ShipCommand.HOLD_FIRE, null, -1);
		}

		float fluxLevel = this.ship.getFluxLevel();
		for (BattleNext_WeaponGroupEvaluation evaluation : this.weaponGroupEvaluations.values()) {
			WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(this.ship, evaluation.getWeaponGroupIndex());
			if (group == null) continue;

			if (this.flags.hasFlag(AIFlags.DO_NOT_USE_FLUX)) {
				if (group.isAutofiring() && evaluation.usesEnergy()) {
					group.toggleOff();
				}
				continue;
			}

			boolean keepOn = avoidanceVector != null && evaluation.isPDGroup();
			if (this.ship.isFighter() || this.ship.isDrone()) {
				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if (weapon.isFiring()) {
						keepOn = true;
						break;
					}
				}
			}

			if (keepOn) {
				if (!group.isAutofiring()) {
					group.toggleOn();
				}
				continue;
			}

			if (this.flags.hasFlag(AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS)) {
				if (group.isAutofiring() && evaluation.usesEnergy()) {
					group.toggleOff();
				}
				continue;
			}

			float fluxAvailable = BattleNext_ShipAIHelper.getFluxAvailable(this.ship);
			float fluxPerSecond = evaluation.getFluxPerSecond();
			float runtime = 30.0F;
			if (fluxPerSecond > 0.0F) {
				runtime = fluxAvailable / fluxPerSecond;
			}

			boolean shieldOn = this.ship.getShield() != null && this.ship.getShield().isOn();
			float minimumRuntime = 2.0F;
			float fluxLimit = 0.2F;
			if (shieldOn && this.ship.getHullSize().ordinal() > HullSize.FRIGATE.ordinal()) {
				minimumRuntime = 2.0F;
				fluxLimit = 0.2F;
			}

			if (threatEvalAI != null) {
				BattleNext_ThreatData maxThreat = threatEvalAI.getMaxThreatData();
				if (maxThreat != null && maxThreat.getShip().getHullSize().ordinal() >= this.ship.getHullSize().ordinal()) {
					float ownSize = this.ship.getHullSize().ordinal();
					if (this.ship.getParentStation() != null) {
						ownSize = this.ship.getParentStation().getHullSize().ordinal();
					}

					float sizeDifference = ownSize - maxThreat.getShip().getHullSize().ordinal() + 1.0F;
					if (sizeDifference < 0.0F) {
						sizeDifference = 0.0F;
					}

					fluxLimit += 0.07F * sizeDifference;
					minimumRuntime += 0.5F * (5.0F - sizeDifference);
					if (this.ship.getMaxSpeed() + 20.0F > maxThreat.getShip().getMaxSpeed() || maxThreat.getShip().isFighter()) {
						fluxLimit += 0.25F;
					}
				}
			}

			if (group.isAutofiring() && (runtime < minimumRuntime || fluxLevel > fluxLimit)) {
				group.toggleOff();
			}

			boolean usable = this.isWeaponGroupUsable(group);
			if (group.isAutofiring()) {
				if (!usable) {
					group.toggleOff();
				}
			} else if (this.isWeaponGroupAutofirable(group) && usable && (!evaluation.usesEnergy() || fluxLevel < fluxLimit - 0.1F)) {
				group.toggleOn();
			}
		}
	}

	private boolean isWeaponGroupAutofirable(WeaponGroupAPI group) {
		return true;
	}

	private boolean isWeaponGroupUsable(WeaponGroupAPI group) {

		byte usableScore = 0;
		byte unusableScore = 0;
		float groupSize = 0.0F;
		float fluxLevel = this.ship.getFluxLevel();
		for (WeaponAPI weapon : group.getWeaponsCopy()) {
			switch (weapon.getSize()) {
				case SMALL:
					groupSize++;
					break;
				case MEDIUM:
					groupSize += 2.0F;
					break;
				case LARGE:
					groupSize += 4.0F;
			}
		}

		float totalSize = 0.0F;
		for (WeaponAPI weapon : this.ship.getUsableWeapons()) {
			switch (weapon.getSize()) {
				case SMALL:
					totalSize++;
					break;
				case MEDIUM:
					totalSize += 2.0F;
					break;
				case LARGE:
					totalSize += 4.0F;
			}
		}

		boolean smallGroup = groupSize < totalSize * 0.67F * (1.0F - fluxLevel);
		for (WeaponAPI weapon : group.getWeaponsCopy()) {
			if (weapon.hasAIHint(AIHints.STRIKE)) return false;
			if (weapon.usesAmmo() && weapon.getAmmo() <= 0) continue;

			boolean usable = true;
			if (!weapon.hasAIHint(AIHints.PD)) {
				boolean plentifulAmmo = weapon.getAmmo() > 20
					&& !weapon.hasAIHint(AIHints.GUIDED_POOR)
					&& !weapon.hasAIHint(AIHints.DO_NOT_AIM);
				if (!weapon.usesAmmo()) {
					plentifulAmmo = true;
				}

				if (weapon.getType() == WeaponType.MISSILE && !plentifulAmmo) {
					usable = false;
				}

				if (weapon.getSlot().getArc() <= 10.0F
					&& (!weapon.isBeam() || weapon.isBurstBeam())
					&& !this.ship.isFighter()
					&& (weapon.getSize() != WeaponSize.SMALL || this.ship.getHullSize().ordinal() < HullSize.DESTROYER.ordinal())
					&& !smallGroup) {
					usable = false;
				}
			}

			byte weight = 0;
			switch (weapon.getSize()) {
				case SMALL:
					weight = 1;
					break;
				case MEDIUM:
					weight = 3;
					break;
				case LARGE:
					weight = 7;
			}

			if (usable) {
				usableScore += weight;
			} else {
				unusableScore += weight;
			}
		}

		return usableScore >= unusableScore;
	}
}
