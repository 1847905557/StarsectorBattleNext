package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.ShipAPI;

/**
 * Port of engine class com.fs.starfarer.combat.ai.ThreatData.
 * Holds the evaluated threat one enemy ship poses to the owning AI ship.
 */
public class BattleNext_ThreatData {
	private com.fs.starfarer.api.combat.ShipAPI ship;
	private float maxDamagePerShot = 0.0F;
	private float threatLevel = 0.0F;
	private float systemThreat = 0.0F;
	private float threatRange = 0.0F;
	private float beamThreatRange = 0.0F;
	private boolean isThreat = true;
	private float minTimeToHit = 1000.0F;

	public float getThreatLevel() {
		return this.systemThreat == 0.0F ? 0.0F : this.threatLevel / this.systemThreat;
	}

	public ShipAPI getShip() {
		return this.ship;
	}

	public float getThreatRange() {
		return this.threatRange;
	}

	public boolean isThreat() {
		return this.isThreat;
	}

	public float getThreatValue() {
		return this.threatLevel;
	}

	public float getSystemThreat() {
		return this.systemThreat;
	}

	public float getMinTimeToHit() {
		return this.minTimeToHit;
	}

	public void setMinTimeToHit(float minTimeToHit) {
		this.minTimeToHit = minTimeToHit;
	}

	public float getBeamThreatRange() {
		return this.beamThreatRange;
	}

	public float getMaxDamagePerShot() {
		return this.maxDamagePerShot;
	}

	public void setMaxDamagePerShot(float maxDamagePerShot) {
		this.maxDamagePerShot = maxDamagePerShot;
	}

	void setShipInternal(ShipAPI ship) {
		this.ship = ship;
	}

	void setThreatLevelInternal(float threatLevel) {
		this.threatLevel = threatLevel;
	}

	void setSystemThreatInternal(float systemThreat) {
		this.systemThreat = systemThreat;
	}

	void setThreatRangeInternal(float threatRange) {
		this.threatRange = threatRange;
	}

	void setBeamThreatRangeInternal(float beamThreatRange) {
		this.beamThreatRange = beamThreatRange;
	}

	void setThreatFlagInternal(boolean isThreat) {
		this.isThreat = isThreat;
	}
}
