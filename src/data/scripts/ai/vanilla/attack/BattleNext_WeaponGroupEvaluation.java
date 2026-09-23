package data.scripts.ai.vanilla.attack;

/**
 * Port of engine class com.fs.starfarer.combat.ai.attack.WeaponGroupEvaluation.
 * Static per-group summary (ranges, counts, flux profile) used by the attack AI
 * and the autofire manager.
 * The obfuscated flag f_722efca7_7 has no reader inside the ship AI chain and is
 * ported as an unused flag for structural fidelity.
 */
public class BattleNext_WeaponGroupEvaluation {
	private boolean isPDGroup = false;
	private boolean isStrikeGroup;
	private boolean usesEnergy = false;
	private boolean ammoLow = false;
	private boolean isAllMissiles = false;
	private boolean unusedFlag = false;
	private int weaponCount;
	private int turretCount;
	private int hardpointCount;
	private boolean hasAimableWeapons;
	private float minRange = Float.MAX_VALUE;
	private float maxRange = 0.0F;
	private float fluxPerSecond;
	private float dps;
	private int weaponGroupIndex;

	public boolean isPDGroup() {
		return this.isPDGroup;
	}

	public void setPDGroup(boolean isPDGroup) {
		this.isPDGroup = isPDGroup;
	}

	public boolean usesEnergy() {
		return this.usesEnergy;
	}

	public void setUsesEnergy(boolean usesEnergy) {
		this.usesEnergy = usesEnergy;
	}

	public float getMinRange() {
		return this.minRange;
	}

	public void setMinRange(float minRange) {
		this.minRange = minRange;
	}

	public float getMaxRange() {
		return this.maxRange;
	}

	public void setMaxRange(float maxRange) {
		this.maxRange = maxRange;
	}

	public int getWeaponCount() {
		return this.weaponCount;
	}

	public void setWeaponCount(int weaponCount) {
		this.weaponCount = weaponCount;
	}

	public int getTurretCount() {
		return this.turretCount;
	}

	public void setTurretCount(int turretCount) {
		this.turretCount = turretCount;
	}

	public int getHardpointCount() {
		return this.hardpointCount;
	}

	public void setHardpointCount(int hardpointCount) {
		this.hardpointCount = hardpointCount;
	}

	public int getWeaponGroupIndex() {
		return this.weaponGroupIndex;
	}

	public void setWeaponGroupIndex(int weaponGroupIndex) {
		this.weaponGroupIndex = weaponGroupIndex;
	}

	public boolean isAmmoLow() {
		return this.ammoLow;
	}

	public void setAmmoLow(boolean ammoLow) {
		this.ammoLow = ammoLow;
	}

	public boolean isStrikeGroup() {
		return this.isStrikeGroup;
	}

	public void setStrikeGroup(boolean isStrikeGroup) {
		this.isStrikeGroup = isStrikeGroup;
	}

	public boolean isAllMissiles() {
		return this.isAllMissiles;
	}

	public void setAllMissiles(boolean isAllMissiles) {
		this.isAllMissiles = isAllMissiles;
	}

	public boolean hasAimableWeapons() {
		return this.hasAimableWeapons;
	}

	public void setHasAimableWeapons(boolean hasAimableWeapons) {
		this.hasAimableWeapons = hasAimableWeapons;
	}

	public float getFluxPerSecond() {
		return this.fluxPerSecond;
	}

	public void setFluxPerSecond(float fluxPerSecond) {
		this.fluxPerSecond = fluxPerSecond;
	}

	public float getDps() {
		return this.dps;
	}

	public void setDps(float dps) {
		this.dps = dps;
	}

	public boolean isUnusedFlag() {
		return this.unusedFlag;
	}

	public void setUnusedFlag(boolean unusedFlag) {
		this.unusedFlag = unusedFlag;
	}
}
