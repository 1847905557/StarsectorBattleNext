package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.ShipAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * Port of engine class com.fs.starfarer.combat.ai.OO0O$O0 (deobfuscated result holder).
 * Result of the best-maneuver-target search: chosen target, an alternate target,
 * the firing range the choice is based on, whether the target is a bad pick,
 * and the aim point that justified it.
 */
public class BattleNext_BestTargetEval {
	private ShipAPI target;
	private ShipAPI alternateTarget;
	private float range;
	private boolean badTarget = false;
	private Vector2f aimPoint = new Vector2f();

	public ShipAPI getTarget() {
		return this.target;
	}

	public float getRange() {
		return this.range;
	}

	public void setRange(float range) {
		this.range = range;
	}

	public ShipAPI getAlternateTarget() {
		return this.alternateTarget;
	}

	public boolean isBadTarget() {
		return this.badTarget;
	}

	public void setBadTarget(boolean badTarget) {
		this.badTarget = badTarget;
	}

	public Vector2f getAimPoint() {
		return this.aimPoint;
	}

	public void setAimPoint(Vector2f aimPoint) {
		this.aimPoint = aimPoint;
	}

	public void setTarget(ShipAPI target) {
		this.target = target;
	}

	public void setAlternateTarget(ShipAPI alternateTarget) {
		this.alternateTarget = alternateTarget;
	}
}
