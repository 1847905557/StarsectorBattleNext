package data.scripts.ai.vanilla.attack;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import data.scripts.ai.vanilla.BattleNext_ShipAIHelper;

/**
 * Port of engine class com.fs.starfarer.combat.ai.attack.AttackingGroup.
 * One scored (weapon group, target ship) candidate of the attack AI.
 * Obfuscated members renamed: f_d435ae80 -> score, f_d435ae80_6 -> minWeaponRange,
 * f_722efca7 -> allGuidedPoor.
 */
public class BattleNext_AttackingGroup {
	private int weaponGroupIndex;
	private float score;
	private float checkInterval;
	private float range;
	private float minWeaponRange;
	private boolean isStrikeGroup;
	private boolean missilesAimable;
	private boolean allGuidedPoor;
	private boolean canFireSafely;
	private ShipAPI ship;
	private boolean hasUniformWeapons;
	private int currentWeaponIndex = -1;
	private boolean usesFlux = true;
	private float evaluationInterval = Float.MAX_VALUE;
	private float elapsedTime = 0.0F;
	private float distance;
	private boolean hasAimableWeapons;
	private float burstDuration = 0.0F;
	private float totalAmmo = 0.0F;
	private float ammoThreshold = Float.MAX_VALUE;

	public boolean isAllGuidedPoor() {
		return this.allGuidedPoor;
	}

	public void setAllGuidedPoor(boolean allGuidedPoor) {
		this.allGuidedPoor = allGuidedPoor;
	}

	public float getTotalAmmo() {
		return this.totalAmmo;
	}

	public void setTotalAmmo(float totalAmmo) {
		this.totalAmmo = totalAmmo;
	}

	public float getAmmoThreshold() {
		return this.ammoThreshold;
	}

	public void setAmmoThreshold(float ammoThreshold) {
		this.ammoThreshold = ammoThreshold;
	}

	public boolean hasEnoughAmmo(ShipAPI ship) {
		WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(ship, this.weaponGroupIndex);
		float ammo = 0.0F;
		for (WeaponAPI weapon : group.getWeaponsCopy()) {
			if (weapon.usesAmmo()) {
				ammo += weapon.getAmmo();
			}
		}
		return this.totalAmmo - ammo >= this.ammoThreshold;
	}

	public void computeTotalAmmo(ShipAPI ship) {
		WeaponGroupAPI group = BattleNext_ShipAIHelper.getWeaponGroup(ship, this.weaponGroupIndex);
		float ammo = 0.0F;
		for (WeaponAPI weapon : group.getWeaponsCopy()) {
			if (weapon.usesAmmo()) {
				ammo += weapon.getAmmo();
			}
		}
		this.totalAmmo = ammo;
	}

	public float getBurstDuration() {
		return this.burstDuration;
	}

	public void setBurstDuration(float burstDuration) {
		this.burstDuration = burstDuration;
	}

	public int getCurrentWeaponIndex() {
		return this.currentWeaponIndex;
	}

	public void setCurrentWeaponIndex(int currentWeaponIndex) {
		this.currentWeaponIndex = currentWeaponIndex;
	}

	public boolean hasUniformWeapons() {
		return this.hasUniformWeapons;
	}

	public void setHasUniformWeapons(boolean hasUniformWeapons) {
		this.hasUniformWeapons = hasUniformWeapons;
	}

	public ShipAPI getShip() {
		return this.ship;
	}

	public void setShip(ShipAPI ship) {
		this.ship = ship;
	}

	public int getWeaponGroupIndex() {
		return this.weaponGroupIndex;
	}

	public void setWeaponGroupIndex(int weaponGroupIndex) {
		this.weaponGroupIndex = weaponGroupIndex;
	}

	public float getScore() {
		return this.score;
	}

	public void setScore(float score) {
		this.score = score;
	}

	public float getCheckInterval() {
		return this.checkInterval;
	}

	public void setCheckInterval(float checkInterval) {
		this.checkInterval = checkInterval;
	}

	public float getRange() {
		return this.range;
	}

	public void setRange(float range) {
		this.range = range;
	}

	public float getMinWeaponRange() {
		return this.minWeaponRange;
	}

	public void setMinWeaponRange(float minWeaponRange) {
		this.minWeaponRange = minWeaponRange;
	}

	public boolean isStrikeGroup() {
		return this.isStrikeGroup;
	}

	public void setStrikeGroup(boolean isStrikeGroup) {
		this.isStrikeGroup = isStrikeGroup;
	}

	public boolean missilesAimable() {
		return this.missilesAimable;
	}

	public void setMissilesAimable(boolean missilesAimable) {
		this.missilesAimable = missilesAimable;
	}

	public boolean canFireSafely() {
		return this.canFireSafely;
	}

	public void setCanFireSafely(boolean canFireSafely) {
		this.canFireSafely = canFireSafely;
	}

	public float getDistance() {
		return this.distance;
	}

	public void setDistance(float distance) {
		this.distance = distance;
	}

	public void setHasAimableWeapons(boolean hasAimableWeapons) {
		this.hasAimableWeapons = hasAimableWeapons;
	}

	public boolean hasAimableWeapons() {
		return this.hasAimableWeapons;
	}

	public boolean usesFlux() {
		return this.usesFlux;
	}

	public void setUsesFlux(boolean usesFlux) {
		this.usesFlux = usesFlux;
	}

	public float getEvaluationInterval() {
		return this.evaluationInterval;
	}

	public void setEvaluationInterval(float evaluationInterval) {
		this.evaluationInterval = evaluationInterval;
	}

	public float getElapsedTime() {
		return this.elapsedTime;
	}

	public void setElapsedTime(float elapsedTime) {
		this.elapsedTime = elapsedTime;
	}
}
