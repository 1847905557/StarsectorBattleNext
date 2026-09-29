package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.battlesnext.util.annotation.PublicData;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.List;

/**
 * Replacement for the engine-internal per-ship combat statistics
 * (Ship.getStatistics() in vanilla) which the mod API does not expose.
 * Keeps short sliding windows of sampled values and fits a linear trend on
 * demand, mirroring how BehaviorModule and AutofireManagerV2 read
 * fitTrend(duration).avgX / .slope.
 *
 * Deviations from vanilla:
 * - Damage dealt and taken are fed from an engine-wide damage listener by
 *   BattleNext_ShipAIHelper.
 * - "Backoff" is approximated with the BACKING_OFF AI flag of the ship.
 * - "Distance to enemy" uses the nearest enemy passed in by the caller.
 * One instance lives per ship inside the ship's custom data.
 */
public class BattleNext_ShipStatisticsTracker {
	public static final String CUSTOM_DATA_KEY = "BattleNext_ShipStatisticsTracker";

	private static final int HISTORY_POINTS = 20;

	private float clock = 0.0F;
	private final IntervalUtil intervalTracker = new IntervalUtil(0.75F, 1.25F);

	private final List<Sample> fluxLevelHistory = new ArrayList<>();
	private final List<Sample> hardFluxLevelHistory = new ArrayList<>();
	private final List<Sample> distanceToEnemyHistory = new ArrayList<>();
	private final List<Sample> damageTakenHistory = new ArrayList<>();
	private final List<Sample> damageDealtHistory = new ArrayList<>();
	private final List<Sample> backingOffHistory = new ArrayList<>();

	private float damageTakenAccum = 0.0F;
	private float damageDealtAccum = 0.0F;

	@PublicData
	public static class BattleNext_TrendFit {
		public float avgX;
		public float slope;
	}

	private static class Sample {
		private float time;
		private float value;
		private float interval;

		Sample(float time, float value, float interval) {
			this.time = time;
			this.value = value;
			this.interval = interval;
		}
	}

	public void addDamageDealt(float amount) {
		this.damageDealtAccum += amount;
	}

	public void addDamageTaken(float amount) {
		this.damageTakenAccum += amount;
	}

	public void advance(ShipAPI ship, CombatEntityAPI nearestEnemy, float amount, boolean backingOff) {
		this.clock += amount;
		this.intervalTracker.advance(amount);
		if (!this.intervalTracker.intervalElapsed()) return;

		float fluxLevel = ship.getFluxTracker().getFluxLevel();
		float hardFluxLevel = ship.getHardFluxLevel();
		float distanceToEnemy = 3000.0F;
		if (nearestEnemy != null) {
			distanceToEnemy = Misc.getDistance(ship.getLocation(), nearestEnemy.getLocation());
			distanceToEnemy -= (ship.getCollisionRadius() + nearestEnemy.getCollisionRadius()) * 0.75F;
			if (distanceToEnemy < 0.0F) {
				distanceToEnemy = 0.0F;
			}
		}

		this.record(this.fluxLevelHistory, this.clock, fluxLevel);
		this.record(this.hardFluxLevelHistory, this.clock, hardFluxLevel);
		this.record(this.distanceToEnemyHistory, this.clock, distanceToEnemy);
		this.record(this.damageTakenHistory, this.clock, this.damageTakenAccum);
		this.record(this.damageDealtHistory, this.clock, this.damageDealtAccum);
		this.record(this.backingOffHistory, this.clock, backingOff ? 1.0F : 0.0F);
		this.damageTakenAccum = 0.0F;
		this.damageDealtAccum = 0.0F;
	}

	private void record(List<Sample> history, float now, float value) {
		history.add(new Sample(now, value, this.intervalTracker.getIntervalDuration()));
		while (history.size() > HISTORY_POINTS) {
			history.remove(0);
		}
	}

	public BattleNext_TrendFit fitTrend(List<Sample> history, float duration) {
		BattleNext_TrendFit fit = new BattleNext_TrendFit();
		if (history.isEmpty()) {
			return fit;
		}

		float accumulatedInterval = 0.0F;
		int firstIndex = history.size() - 1;
		for (int index = history.size() - 1; index >= 0; index--) {
			firstIndex = index;
			accumulatedInterval += history.get(index).interval;
			if (accumulatedInterval > duration) break;
		}
		float sumTime = 0.0F;
		float sumValue = 0.0F;
		float sumTimeSquared = 0.0F;
		float sumTimeValue = 0.0F;
		int count = 0;
		for (int index = firstIndex; index < history.size(); index++) {
			Sample sample = history.get(index);
			sumTime += sample.time;
			sumValue += sample.value;
			sumTimeSquared += sample.time * sample.time;
			sumTimeValue += sample.time * sample.value;
			count++;
		}
		if (count == 0) {
			return fit;
		}

		float meanTime = sumTime / count;
		float meanValue = sumValue / count;
		fit.avgX = sumValue / accumulatedInterval;
		float denominator = sumTimeSquared - sumTime * meanTime;
		if (Math.abs(denominator) < 1.0E-6F) {
			fit.slope = 0.0F;
		} else {
			fit.slope = (sumTimeValue - sumTime * meanValue) / denominator;
		}
		return fit;
	}

	public BattleNext_TrendFit fitFluxLevel(float duration) {
		return this.fitTrend(this.fluxLevelHistory, duration);
	}

	public BattleNext_TrendFit fitHardFluxLevel(float duration) {
		return this.fitTrend(this.hardFluxLevelHistory, duration);
	}

	public BattleNext_TrendFit fitDistanceToEnemy(float duration) {
		return this.fitTrend(this.distanceToEnemyHistory, duration);
	}

	public BattleNext_TrendFit fitDamageTaken(float duration) {
		return this.fitTrend(this.damageTakenHistory, duration);
	}

	public BattleNext_TrendFit fitDamageDealt(float duration) {
		return this.fitTrend(this.damageDealtHistory, duration);
	}

	public BattleNext_TrendFit fitBackingOff(float duration) {
		return this.fitTrend(this.backingOffHistory, duration);
	}

	public boolean isDistanceHistoryFull() {
		return this.distanceToEnemyHistory.size() == HISTORY_POINTS;
	}
}
