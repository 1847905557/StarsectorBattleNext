package data.scripts.ai.vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.impl.combat.PhaseCloakStats;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WingRole;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.vanilla.maneuvers.BattleNext_EscortTargetManeuverV3;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Port of engine classes com.fs.starfarer.combat.ai.AIUtils and
 * com.fs.starfarer.prototype.Utils, merged into one static helper for the
 * ported ship AI chain.
 * Profiler hooks dropped.
 * Engine types are replaced by their mod API equivalents throughout; every
 * approximation site carries a local comment.
 */
public class BattleNext_ShipAIHelper {
	private BattleNext_ShipAIHelper() {
	}

	/**
	 * Port of engine AIUtils.f_d435ae80_2.
	 */
	public static final float MANEUVER_TARGET_SEARCH_RANGE = 2200.0F;

	/**
	 * Port of engine AIUtils.f_722efca7. Set while the shared aim point
	 * computation runs; the intercept solver rejects low-closing solutions
	 * while it is set.
	 */
	private static final AtomicBoolean COMPUTING_AIM_POINT =
		new AtomicBoolean(false);

	public static boolean isComputingAimPoint() {
		return COMPUTING_AIM_POINT.get();
	}

	public static void setComputingAimPoint(boolean value) {
		COMPUTING_AIM_POINT.set(value);
	}
	// mutable aim error slots; held in a final array so the static reference stays final
	private static final float[] AIM_ERROR_OFFSETS = new float[2];
	public static final float aimErrorDivisor = 15.0F;

	private static final String BLOCKING_SHIPS_KEY = "BattleNext_ShipAIHelper_BlockingShips";

	private static class BlockingShipsCache {
		private float sampledAt;
		private List<ShipAPI> ships;
	}

	private static final String DAMAGE_LISTENER_INSTALLED_KEY = "BattleNext_ShipAIHelper_DamageListenerInstalled";

	// ===== engine access and infrastructure =====

	public static CombatEngineAPI getEngine() {
		return Global.getCombatEngine();
	}

	/**
	 * Per-ship combat statistics tracker stored in the ship's custom data.
	 * The engine-wide damage listener feeding addDamageDealt is installed the
	 * first time any tracker is created; the boolean flag in the engine custom
	 * data keeps it from being installed twice.
	 */
	public static BattleNext_ShipStatisticsTracker getStatisticsTracker(ShipAPI ship) {
		Map<String, Object> customData = ship.getCustomData();
		BattleNext_ShipStatisticsTracker tracker = (BattleNext_ShipStatisticsTracker) customData.get(BattleNext_ShipStatisticsTracker.CUSTOM_DATA_KEY);
		if (tracker == null) {
			tracker = new BattleNext_ShipStatisticsTracker();
			customData.put(BattleNext_ShipStatisticsTracker.CUSTOM_DATA_KEY, tracker);
			installDamageDealtListener();
		}

		return tracker;
	}

	private static void installDamageDealtListener() {
		CombatEngineAPI engine = getEngine();
		if (engine == null) {
			return;
		}

		if (Boolean.TRUE.equals(engine.getCustomData().get(DAMAGE_LISTENER_INSTALLED_KEY))) {
			return;
		}

		engine.getCustomData().put(DAMAGE_LISTENER_INSTALLED_KEY, Boolean.TRUE);
		engine.getListenerManager().addListener((DamageListener)(source, target, result) -> {
			if (target instanceof ShipAPI) {
				Object targetTracker = ((ShipAPI) target).getCustomData().get(BattleNext_ShipStatisticsTracker.CUSTOM_DATA_KEY);
				if (targetTracker instanceof BattleNext_ShipStatisticsTracker) {
					float amount = result.getDamageToHull() + result.getTotalDamageToArmor() + result.getDamageToShields();
					((BattleNext_ShipStatisticsTracker) targetTracker).addDamageTaken(amount);
				}
			}

			if (!(source instanceof ShipAPI)) {
				return;
			}

			Object existing = ((ShipAPI) source).getCustomData().get(BattleNext_ShipStatisticsTracker.CUSTOM_DATA_KEY);
			if (existing instanceof BattleNext_ShipStatisticsTracker) {
				// approximation: the result object has no single total; sum hull, armor and shield components
				float amount = result.getDamageToHull() + result.getTotalDamageToArmor() + result.getDamageToShields();
				((BattleNext_ShipStatisticsTracker) existing).addDamageDealt(amount);
			}
		});
	}

	public static WeaponGroupAPI getWeaponGroup(ShipAPI ship, int index) {
		List<WeaponGroupAPI> groups = ship.getWeaponGroupsCopy();
		if (index < 0 || index >= groups.size()) {
			return null;
		}

		return groups.get(index);
	}

	public static String getPersonality(ShipAPI ship) {
		String personality = null;
		if (BattleNext_BasicShipAI.getActiveInstance(ship) != null && ship.getShipAI() != null) {
			ShipAIConfig config = ship.getShipAI().getConfig();
			if (config != null && config.personalityOverride != null) {
				personality = config.personalityOverride;
			}
		}

		if (personality == null && ship.getCaptain() != null && ship.getCaptain().getPersonalityAPI() != null) {
			personality = ship.getCaptain().getPersonalityAPI().getId();
		}

		// approximation: ShipAPI omits Ship.getFallbackPersonalityId(); the
		// engine initializes that field to steady when no captain is present.
		return personality == null ? "steady" : personality;
	}

	// PersonalityAPI omits the engine bravery field; the merged personality
	// spreadsheet is the same data source that initializes PersonalityImpl.
	public static float getBravery(ShipAPI ship) {
		String personality = getPersonality(ship);
		try {
			JSONArray rows = BattleNext_ModFileManager.readMergedCsv(BattleNext_ShipAIHelper.class, "id",
					"data/characters/personalities.csv");
			for (int index = 0; index < rows.length(); index++) {
				JSONObject row = rows.optJSONObject(index);
				if (row != null && personality.equals(row.optString("id", "").trim())) {
					return (float) row.getDouble("bravery");
				}
			}
		} catch (IOException | JSONException exception) {
			throw new IllegalStateException("Unable to load personality bravery", exception);
		}
		throw new IllegalStateException("Personality bravery missing: " + personality);
	}

	// Port of ShipHullSpec.computeFleetStrengthValue(): every hull weapon slot
	// contributes by size, including slots without a fitted weapon.
	public static float computeFleetStrength(ShipAPI ship) {
		float strength = 0.0F;
		for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
			switch (slot.getSlotSize()) {
				case SMALL:
					strength += 1.0F;
					break;
				case MEDIUM:
					strength += 2.0F;
					break;
				case LARGE:
					strength += 4.0F;
					break;
			}
		}

		return strength;
	}

	// Port of DeployedFleetMember.getMemberStrength(): non-wing strength follows
	// the deployed ship's current hull fraction.
	public static float getDeployedMemberStrength(DeployedFleetMemberAPI member, ShipAPI ship) {
		float strength = member.getMember().getMemberStrength();
		if (!member.isFighterWing()) {
			ShipAPI deployed = ship != null ? ship : member.getShip();
			if (deployed != null) {
				strength *= 0.5F + 0.5F * deployed.getHitpoints() / deployed.getMaxHitpoints();
			}
			// approximation: a missing API deployed ship has no hull fraction;
			// the member's undamaged strength is used until a ship is available.
		}

		return strength;
	}

	/**
	 * approximation: the engine shield wasHit flag is not exposed; any damage
	 * taken within the last 0.1 seconds counts as a shield hit.
	 */
	public static boolean shieldWasHit(ShipAPI ship) {
		return ship.getSinceLastDamageTaken() < 0.1F;
	}

	/**
	 * approximation: engine Shield.isIdle() is not exposed. ShieldAPI.isOn()
	 * reports charge-tracker activation, so the inactive state also includes
	 * transitions that the original idle check would reject.
	 */
	public static boolean isShieldIdle(ShipAPI ship) {
		ShieldAPI shield = ship.getShield();
		return shield == null || !shield.isOn();
	}

	public static boolean isPhaseShip(ShipAPI ship) {
		return ship.getPhaseCloak() != null;
	}

	// Port of Ship.isCarrier(): the variant carrier flag requires sufficient
	// actual launch bays relative to the modified fighter-bay stat.
	public static boolean isCarrier(ShipAPI ship) {
		return ship.getVariant().isCarrier()
			&& ship.getLaunchBaysCopy().size() >= ship.getMutableStats().getNumFighterBays().getModifiedValue() * 0.66F;
	}

	public static boolean isEngineDisabled(ShipAPI ship) {
		return ship.getEngineController().isDisabled();
	}

	public static boolean isTravelDriveOn(ShipAPI ship) {
		return ship.getTravelDrive() != null && ship.getTravelDrive().isActive();
	}

	/**
	 * approximation: FighterWingSpecAPI has no isAlwaysEscort(); support-role
	 * wings fill the same slot in AI planning.
	 */
	public static boolean isAlwaysEscortWing(FighterWingSpecAPI spec) {
		return spec.getRole() == WingRole.SUPPORT;
	}

	public static float getDpsToHitStrengthMult() {
		return Global.getSettings().getFloat("dpsToHitStrengthMult");
	}

	// approximation: the API exposes only the map extent, not the map origin;
	// the map is assumed to be centered on the world origin
	public static float getMapMinX() {
		return -getEngine().getMapWidth() / 2.0F;
	}

	// approximation: the API exposes only the map extent, not the map origin;
	// the map is assumed to be centered on the world origin
	public static float getMapMaxX() {
		return getEngine().getMapWidth() / 2.0F;
	}

	// approximation: the API exposes only the map extent, not the map origin;
	// the map is assumed to be centered on the world origin
	public static float getMapMinY() {
		return -getEngine().getMapHeight() / 2.0F;
	}

	// approximation: the API exposes only the map extent, not the map origin;
	// the map is assumed to be centered on the world origin
	public static float getMapMaxY() {
		return getEngine().getMapHeight() / 2.0F;
	}

	private static class TimeoutEntry {
		private Object value;
		private float expireAt;
	}

	// approximation: CombatEngineAPI omits the engine's TimeoutTrackerMap;
	// combat custom data owns an equivalent elapsed-combat-time map.
	private static final String TIMED_CUSTOM_DATA_KEY = "BattleNext_ShipAIHelper_TimedCustomData";

	@SuppressWarnings("unchecked")
	private static Map<String, TimeoutEntry> timedCustomData(CombatEngineAPI engine) {
		return (Map<String, TimeoutEntry>) engine.getCustomData().computeIfAbsent(
			TIMED_CUSTOM_DATA_KEY, ignored -> new ConcurrentHashMap<String, TimeoutEntry>());
	}

	public static Object getCustomDataWithTimeout(String key) {
		return getCustomDataWithTimeout(getEngine(), key);
	}

	static Object getCustomDataWithTimeout(CombatEngineAPI engine, String key) {
		if (engine == null) {
			return null;
		}
		Map<String, TimeoutEntry> entries = timedCustomData(engine);
		TimeoutEntry entry = entries.get(key);
		if (entry == null) {
			return null;
		}

		float now = engine.getTotalElapsedTime(false);
		if (now >= entry.expireAt) {
			entries.remove(key);
			return null;
		}

		return entry.value;
	}

	public static void setCustomDataWithTimeout(String key, Object value, float seconds) {
		setCustomDataWithTimeout(getEngine(), key, value, seconds);
	}

	static void setCustomDataWithTimeout(CombatEngineAPI engine, String key, Object value, float seconds) {
		if (engine == null) {
			return;
		}
		TimeoutEntry entry = new TimeoutEntry();
		entry.value = value;
		float now = engine.getTotalElapsedTime(false);
		entry.expireAt = now + seconds;
		timedCustomData(engine).put(key, entry);
	}

	public static boolean containsCustomDataWithTimeout(String key) {
		return getCustomDataWithTimeout(key) != null;
	}

	// ===== Utils math =====

	public static final float degreesToRadians = (float) (Math.PI / 180.0);
	public static final float radiansToDegrees = 57.295784F;

	private static final int atanTableLastIndex = 1048575;
	private static final int atanTableEntries = 1048576;
	private static final int atanTableWidth = (int) Math.sqrt(1048576.0);
	private static final float atanTableScale = 1.0F / (atanTableWidth - 1);
	// intentionally kept dead: degreesPerRadian is written but never read in
	// the vanilla sources as well
	private static final float degreesPerRadian = 180.0F / (float) Math.PI;
	private static final float[] atan2Table = new float[1048576];

	static {
		for (int x = 0; x < atanTableWidth; x++) {
			for (int y = 0; y < atanTableWidth; y++) {
				float fx = (float) x / atanTableWidth;
				float fy = (float) y / atanTableWidth;
				atan2Table[y * atanTableWidth + x] = (float) Math.atan2(fy, fx);
			}
		}
	}

	// intentionally kept dead: atanTableLastIndex, atanTableEntries and
	// degreesPerRadian are unused in the vanilla sources as well
	public static Vector2f rotate(Vector2f vector, float degrees) {
		float cos = (float) Math.cos(degrees * degreesToRadians);
		float sin = (float) Math.sin(degrees * degreesToRadians);
		Vector2f result = new Vector2f();
		result.x = vector.x * cos - vector.y * sin;
		result.y = vector.x * sin + vector.y * cos;
		return result;
	}

	public static Vector2f rotateAround(Vector2f vector, Vector2f offset, float degrees) {
		float cos = (float) Math.cos(degrees * degreesToRadians);
		float sin = (float) Math.sin(degrees * degreesToRadians);
		new Vector2f();
		vector.x = vector.x + (offset.x * cos - offset.y * sin);
		vector.y = vector.y + (offset.x * sin + offset.y * cos);
		return vector;
	}

	public static Vector2f getUnitVectorAtAngle(float angle) {
		Vector2f result = new Vector2f();
		float radians = angle * degreesToRadians;
		result.x = (float) Math.cos(radians);
		result.y = (float) Math.sin(radians);
		return result;
	}

	public static Vector2f getUnitVector(float radians) {
		Vector2f result = new Vector2f();
		result.x = (float) Math.cos(radians);
		result.y = (float) Math.sin(radians);
		return result;
	}

	public static float getAngleDiff(float angle, Vector2f from, Vector2f to) {
		Vector2f delta = Vector2f.sub(to, from, new Vector2f());
		return getAngleDiff(angle, getAngleFast(delta));
	}

	public static float getAngleDiff(Vector2f from, Vector2f to) {
		return getAngleDiff(getAngleFast(from), getAngleFast(to));
	}

	public static float getAngleDiff(float angle1, float angle2) {
		float diff = normalizeAngle(angle1 - angle2);
		return diff > 180.0F ? 360.0F - diff : diff;
	}

	public static Vector2f normalise(Vector2f vector) {
		return vector.lengthSquared() > Float.MIN_VALUE ? (Vector2f) vector.normalise() : vector;
	}

	public static float getDistance(Vector2f from, Vector2f to) {
		return (float) Math.sqrt((from.x - to.x) * (from.x - to.x) + (from.y - to.y) * (from.y - to.y));
	}

	public static float getDistanceSquared(Vector2f from, Vector2f to) {
		return (from.x - to.x) * (from.x - to.x) + (from.y - to.y) * (from.y - to.y);
	}

	public static Vector2f subtract(Vector2f from, Vector2f to) {
		return Vector2f.sub(from, to, new Vector2f());
	}

	public static float getAngle(Vector2f vector) {
		return (float) Math.atan2(vector.y, vector.x) * radiansToDegrees;
	}

	public static float getAngleFast(Vector2f vector) {
		return atan2Fast(vector.y, vector.x) * radiansToDegrees;
	}

	public static float getAngle(Vector2f from, Vector2f to) {
		return (float) Math.atan2(to.y - from.y, to.x - from.x) * radiansToDegrees;
	}

	public static float getAngleFast(Vector2f from, Vector2f to) {
		return atan2Fast(to.y - from.y, to.x - from.x) * radiansToDegrees;
	}

	public static boolean isInArc(float direction, float arc, Vector2f from, Vector2f to) {
		if (arc >= 360.0F) {
			return true;
		}

		if (direction < 0.0F) {
			direction += 360.0F;
		}

		Vector2f delta = new Vector2f(to.x - from.x, to.y - from.y);
		if (delta.lengthSquared() == 0.0F) {
			return false;
		}

		float angle = getAngleFast(delta);
		if (angle < 0.0F) {
			angle += 360.0F;
		}

		float arcStart = direction - arc / 2.0F;
		if (arcStart < 0.0F) {
			arcStart += 360.0F;
		}

		if (arcStart > 360.0F) {
			arcStart -= 360.0F;
		}

		float arcEnd = direction + arc / 2.0F;
		if (arcEnd < 0.0F) {
			arcEnd += 360.0F;
		}

		if (arcEnd > 360.0F) {
			arcEnd -= 360.0F;
		}

		if (angle >= arcStart && angle <= arcEnd) {
			return true;
		} else {
			return angle >= arcStart && arcStart > arcEnd ? true : angle <= arcEnd && arcStart > arcEnd;
		}
	}

	public static boolean isInArc(Vector2f from, Vector2f to, float arc, Vector2f arcOrigin, Vector2f target) {
		return isInArc(getAngleFast(from, to), arc, arcOrigin, target);
	}

	public static boolean isInArc(float direction, float arc, float angle) {
		if (arc < 0.0F) {
			return false;
		}

		direction = normalizeAngle(direction);
		angle = normalizeAngle(angle);
		float diff = Math.abs(angle - direction) - arc / 2.0F;
		float diffWrapped = Math.abs(360.0F - Math.abs(angle - direction)) - arc / 2.0F;
		return diff <= 0.0F || diffWrapped <= 0.0F;
	}

	public static float clampAngleToArc(float direction, float arc, float angle) {
		if (isInArc(direction, arc, angle)) {
			return angle;
		}

		float toStart = getAngleDiff(angle, direction - arc / 2.0F);
		float toEnd = getAngleDiff(angle, direction + arc / 2.0F);
		return normalizeAngle(toStart > toEnd ? direction + arc / 2.0F : direction - arc / 2.0F);
	}

	public static float normalizeAngle(float angle) {
		return (angle % 360.0F + 360.0F) % 360.0F;
	}

	public static float getWrappedAngleDiff(float direction, float arc, float angle) {
		direction = normalizeAngle(direction);
		angle = normalizeAngle(angle);
		float diff = Math.abs(angle - direction) - arc / 2.0F;
		float diffWrapped = Math.abs(360.0F - Math.abs(angle - direction)) - arc / 2.0F;
		if (!(diff <= 0.0F) && !(diffWrapped <= 0.0F)) {
			return Math.min(diff, diffWrapped);
		} else {
			return 0.0F;
		}
	}

	public static float getTurnDirection(float facing, Vector2f from, Vector2f to) {
		float diff = Misc.normalizeAngle(getAngleFast(from, to)) - Misc.normalizeAngle(facing);
		if (diff < 0.0F) {
			diff += 360.0F;
		}

		if (diff == 0.0F || diff == 360.0F) {
			return 0.0F;
		} else {
			return diff > 180.0F ? -1.0F : 1.0F;
		}
	}

	public static float getTurnDirection(Vector2f from, Vector2f to) {
		return getTurnDirection(getAngleFast(from), new Vector2f(0.0F, 0.0F), to);
	}

	public static float getTurnDirection(float facing, float target) {
		float diff = Misc.normalizeAngle(target) - Misc.normalizeAngle(facing);
		if (diff < 0.0F) {
			diff += 360.0F;
		}

		if (diff == 0.0F || diff == 360.0F) {
			return 0.0F;
		} else {
			return diff > 180.0F ? -1.0F : 1.0F;
		}
	}

	public static boolean isAngleBetween(float start, float end, float angle) {
		start = normalizeAngle(start);
		end = normalizeAngle(end);
		angle = normalizeAngle(angle);
		if (angle >= start && angle <= end) {
			return true;
		}

		if (start > end) {
			if (angle <= end) {
				return true;
			}

			if (angle >= start) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Arc-aware turn direction: when the target angle lies inside the arc
	 * centered on arcCenter, the long way around the back of the arc is taken
	 * so the turn sweeps through the arc center instead of crossing it.
	 */
	public static float getTurnDirection(float facing, float target, float arcCenter, float arc) {
		if (arc >= 360.0F) {
			return getTurnDirection(facing, target);
		} else {
			facing = normalizeAngle(facing);
			target = normalizeAngle(target);
			if (!isInArc(arcCenter, arc, target)) {
				return getTurnDirection(facing, target);
			} else {
				float arcBack = normalizeAngle(arcCenter + 180.0F);
				if (facing == target) {
					return 0.0F;
				} else {
					return isAngleBetween(facing, target, arcBack) ? -1.0F : 1.0F;
				}
			}
		}
	}

	public static float getLargestAngleGap(List<Float> angles) {
		if (angles.isEmpty()) {
			return 0.0F;
		}

		Collections.sort(angles);
		angles.add(angles.get(0));
		float largest = 0.0F;
		float previous = angles.get(0);
		int lastIndex = angles.size() - 1;

		for (int i = 1; i < angles.size(); i++) {
			float gap;
			if (i == lastIndex) {
				gap = angles.get(i) + 360.0F - previous;
			} else {
				gap = angles.get(i) - previous;
			}

			if (gap > largest) {
				largest = gap;
			}

			previous = angles.get(i);
		}

		return largest;
	}

	public static float getLargestAngleGapMidpoint(List<Float> angles) {
		if (angles.isEmpty()) {
			return 0.0F;
		}

		Collections.sort(angles);
		angles.add(angles.get(0));
		float largest = 0.0F;
		float previous = angles.get(0);
		float midpoint = -1.0F;
		int lastIndex = angles.size() - 1;

		for (int i = 1; i < angles.size(); i++) {
			float gap;
			if (i == lastIndex) {
				gap = angles.get(i) + 360.0F - previous;
			} else {
				gap = angles.get(i) - previous;
			}

			if (gap > largest) {
				largest = gap;
				midpoint = previous + largest / 2.0F;
			}

			previous = angles.get(i);
		}

		return midpoint;
	}

	public static float atan2DegreesFast(float y, float x) {
		return atan2Fast(y, x) * (180.0F / (float) Math.PI);
	}

	public static float atan2Degrees(float y, float x) {
		return (float) Math.atan2(y, x) * (180.0F / (float) Math.PI);
	}

	public static float atan2Fast(float y, float x) {
		float offset;
		float sign;
		if (x < 0.0F) {
			if (y < 0.0F) {
				x = -x;
				y = -y;
				sign = 1.0F;
			} else {
				x = -x;
				sign = -1.0F;
			}

			offset = (float) -Math.PI;
		} else {
			if (y < 0.0F) {
				y = -y;
				sign = -1.0F;
			} else {
				sign = 1.0F;
			}

			offset = 0.0F;
		}

		float scale = 1.0F / ((Math.max(x, y)) * atanTableScale);
		int xi = (int) (x * scale);
		int yi = (int) (y * scale);
		int index = yi * atanTableWidth + xi;
		return index >= 0 && index < atan2Table.length ? (atan2Table[index] + offset) * sign : 0.0F;
	}

	/**
	 * approximation: com.fs.util.VectorMathUtils.distanceToSegment is absent
	 * from the available source and public API. Projecting onto the finite
	 * segment and clamping to its endpoints preserves the collision-distance
	 * criterion used by AIUtils.computeDanger.
	 */
	private static float distanceToSegment(Vector2f a, Vector2f b, Vector2f point) {
		float dx = b.x - a.x;
		float dy = b.y - a.y;
		float lengthSquared = dx * dx + dy * dy;
		float t = lengthSquared <= 0.0F ? 0.0F : ((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared;
		if (t < 0.0F) {
			t = 0.0F;
		}

		if (t > 1.0F) {
			t = 1.0F;
		}

		float cx = a.x + t * dx;
		float cy = a.y + t * dy;
		return getDistance(point, new Vector2f(cx, cy));
	}

	/**
	 * approximation: com.fs.util.VectorMathUtils.segmentIntersection is absent
	 * from the available source and public API. A finite-segment parametric
	 * intersection supplies the point used by hull and shield checks.
	 */
	private static Vector2f segmentIntersection(Vector2f p1, Vector2f p2, Vector2f p3, Vector2f p4) {
		float d1x = p2.x - p1.x;
		float d1y = p2.y - p1.y;
		float d2x = p4.x - p3.x;
		float d2y = p4.y - p3.y;
		float denominator = d1x * d2y - d1y * d2x;
		if (denominator == 0.0F) {
			return null;
		}

		float t = ((p3.x - p1.x) * d2y - (p3.y - p1.y) * d2x) / denominator;
		float u = ((p3.x - p1.x) * d1y - (p3.y - p1.y) * d1x) / denominator;
		if (t < 0.0F || t > 1.0F || u < 0.0F || u > 1.0F) {
			return null;
		}

		return new Vector2f(p1.x + t * d1x, p1.y + t * d1y);
	}

	/**
	 * Replaces engine Utils.getPredictedAngleChange.
	 */
	public static float getPredictedAngleChange(CombatEntityAPI from, CombatEntityAPI to) {
		if (from != null && to != null) {
			Vector2f fromNext = new Vector2f(from.getLocation());
			Vector2f toNext = new Vector2f(to.getLocation());
			fromNext.x = fromNext.x + from.getVelocity().x * 1.0F;
			fromNext.y = fromNext.y + from.getVelocity().y * 1.0F;
			toNext.x = toNext.x + to.getVelocity().x * 1.0F;
			toNext.y = toNext.y + to.getVelocity().y * 1.0F;
			float angleNow = getAngleFast(from.getLocation(), to.getLocation());
			float angleNext = getAngleFast(fromNext, toNext);
			return getAngleDiff(angleNow, angleNext) * getTurnDirection(angleNow, angleNext);
		} else {
			return 0.0F;
		}
	}

	// ===== hull and weapon size scoring =====

	/**
	 * Score per HullSize used by the vanilla threat and target-ranking
	 * tables. The decompiled lookup switch uses labels equal to the ordinal
	 * plus one; this table is indexed directly by the HullSize ordinal, so
	 * DEFAULT maps to 1 and CAPITAL_SHIP to 6.
	 */
	private static final int[] HULL_SIZE_SCORES = {1, 2, 3, 4, 5, 6};

	/**
	 * Score per WeaponSize. Same decompiled switch-label convention; SMALL
	 * maps to 1, MEDIUM to 2 and LARGE to 3.
	 */
	private static final int[] WEAPON_SIZE_SCORES = {1, 2, 3};

	private static int[] getHullSizeScores() {
		return HULL_SIZE_SCORES;
	}

	private static int[] getWeaponSizeScores() {
		return WEAPON_SIZE_SCORES;
	}

	// ===== AIUtils: fighter, wing and basic ship info =====

	public static boolean isFighterBeingReplaced(ShipAPI fighter) {
		if (!fighter.isFighter() || fighter.getWing() == null || fighter.getWing().getSourceShip() == null || !fighter.getWing().getSourceShip().isPullBackFighters()) {
			return false;
		}

		if (fighter.getWing().getSpec().isBomber() && fighter.getWing().getRange() > 0.0F) {
			return true;
		}

		float replacementRate = fighter.getWing().getSourceShip().getSharedFighterReplacementRate();
		if (replacementRate <= 0.5F) {
			return true;
		}

		if (replacementRate > 0.9F) {
			return false;
		}

		float members = fighter.getWing().getWingMembers().size();
		switch (fighter.getWing().getSpec().getNumFighters()) {
			case 1:
				return false;
			case 2:
				if (members < 2.0F) {
					return true;
				}

				return false;
			case 3:
				if (members < 2.0F) {
					return true;
				}

				return false;
			case 4:
				if (members < 3.0F) {
					return true;
				}

				return false;
			case 5:
				if (members < 4.0F) {
					return true;
				}

				return false;
			case 6:
				if (members < 5.0F) {
					return true;
				}

				return false;
			default:
				return false;
		}
	}

	public static float getFluxScore(ShipAPI ship, boolean shipVenting, ShipAPI target, boolean targetVenting) {
		if (ship == null) {
			return 0.0F;
		}

		if (target == null) {
			return 0.0F;
		}

		float fluxAdvantage = target.getFluxLevel() - ship.getFluxLevel();
		if (fluxAdvantage < 0.0F) {
			return 0.0F;
		}

		float weight = 1.0F;
		if (targetVenting && !shipVenting) {
			weight = 4.0F;
		} else if (shipVenting && !targetVenting) {
			weight = 0.0F;
		} else {
			int sizeDiff = target.getHullSize().ordinal() - ship.getHullSize().ordinal();
			if (sizeDiff <= 0) {
				weight = 4.0F;
			} else if (sizeDiff <= 1) {
				weight = 2.0F;
			} else {
				weight = 1.0F;
			}
		}

		return 200.0F * Math.min(1.0F, fluxAdvantage * weight);
	}

	public static BattleNext_Orderable getOrderable(ShipAPI ship) {
		BattleNext_BasicShipAI portedAI = BattleNext_BasicShipAI.getActiveInstance(ship);
		if (portedAI != null) return portedAI;
		return ship.getAI() instanceof BattleNext_Orderable ? (BattleNext_Orderable) ship.getAI() : null;
	}

	public static float getTimeToRecover(ShipAPI ship) {
		float time = 0.0F;
		if (ship.getFluxTracker().isVenting()) {
			time = ship.getFluxTracker().getTimeToVent();
		} else if (ship.getFluxTracker().isOverloaded()) {
			time = ship.getFluxTracker().getOverloadTimeRemaining();
		}

		return time;
	}

	public static float getCarrierWingRange(ShipAPI carrier, ShipAPI ignored) {
		float range = 100000.0F;
		if (!carrier.getLaunchBaysCopy().isEmpty() && carrier.getCurrentCR() > 0.0F) {
			boolean interceptorFound = false;

			for (FighterLaunchBayAPI bay : carrier.getLaunchBaysCopy()) {
				if ((!interceptorFound || bay.getWing() == null || !bay.getWing().getSpec().isInterceptor())
					&& (bay.getWing() == null || !isAlwaysEscortWing(bay.getWing().getSpec()))
					&& (bay.getWing() == null || !bay.getWing().getSpec().isSupport())
					&& bay.getWing() != null
					&& !(bay.getWing().getRange() <= 0.0F)) {
					float wingRange = bay.getWing().getRange() * 0.9F;
					range = Math.min(range, wingRange);
				}
			}
		} else {
			range = 1500.0F;
		}

		if (range < 1500.0F) {
			range = 1500.0F;
		}

		return range;
	}

	public static Vector2f getClosestPointOnMapBorder(Vector2f point) {
		Vector2f topLeft = new Vector2f(getMapMinX(), getMapMaxY());
		Vector2f topRight = new Vector2f(getMapMaxX(), getMapMaxY());
		Vector2f bottomLeft = new Vector2f(getMapMinX(), getMapMinY());
		Vector2f bottomRight = new Vector2f(getMapMaxX(), getMapMinY());
		float[] distances = new float[]{
			distanceToSegment(topLeft, topRight, point),
			distanceToSegment(topLeft, bottomLeft, point),
			distanceToSegment(bottomLeft, bottomRight, point),
			distanceToSegment(bottomRight, topRight, point)
		};
		float closest = Float.MAX_VALUE;
		int closestIndex = 0;

		for (int i = 0; i < distances.length; i++) {
			if (distances[i] < closest) {
				closestIndex = i;
				closest = distances[i];
			}
		}

		switch (closestIndex) {
			case 0:
				return new Vector2f(0.0F, -1.0F);
			case 1:
				return new Vector2f(1.0F, 0.0F);
			case 2:
				return new Vector2f(0.0F, 1.0F);
			case 3:
				return new Vector2f(-1.0F, 0.0F);
			default:
				return new Vector2f();
		}
	}

	// approximation: engine CombatTask and DirectOrders are not exposed; the
	// assignment targeting the other ship stands in for the task, and the
	// ship's own assignment stands in for its direct orders
	public static boolean isShipEnemy(ShipAPI ship, ShipAPI other) {
		if (ship.isRetreating()) {
			return false;
		}

		CombatTaskManagerAPI taskManager = getEngine().getFleetManager(ship.getOriginalOwner()).getTaskManager(ship.isAlly());
		CombatFleetManagerAPI ownFleet = getEngine().getFleetManager(ship.getOriginalOwner());
		CombatFleetManagerAPI otherFleet = getEngine().getFleetManager(other.getOriginalOwner());
		if (taskManager != null && otherFleet != null) {
			DeployedFleetMemberAPI otherMember = otherFleet.getDeployedFleetMember(other);
			if (otherMember == null) {
				return false;
			}

			AssignmentInfo task = taskManager.getAssignmentInfoForTarget(otherMember);
			if (task != null && task.getType() == CombatAssignmentType.AVOID) {
				AssignmentInfo orders = ownFleet == null ? null : taskManager.getAssignmentFor(ship);
				if (orders != null
					&& orders.getType() == CombatAssignmentType.AVOID
					&& orders.getTarget() != null
					&& orders.getTarget() == task.getTarget()) {
					return false;
				}

				if (orders != null && orders.getTarget() != null) {
					CombatAssignmentType ordersType = orders.getType();
					if (ordersType == CombatAssignmentType.LIGHT_ESCORT || ordersType == CombatAssignmentType.MEDIUM_ESCORT || ordersType == CombatAssignmentType.HEAVY_ESCORT) {
						return false;
					}
				}

				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	public static float getShipScore(ShipAPI ship, ShipAPI target, float leadTime) {
		if (leadTime <= 0.0F) {
			return 0.0F;
		}

		boolean shipIsNonCombat = ship.isNonCombat(true);
		float recoverTime = 0.0F;
		if (target.getFluxTracker().isVenting()) {
			recoverTime = target.getFluxTracker().getTimeToVent();
		} else if (target.getFluxTracker().isOverloaded()) {
			recoverTime = target.getFluxTracker().getOverloadTimeRemaining();
		}

		ShipSystemAPI system = target.getSystem();
		// approximation: ShipSystemAPI has no usesAmmo(); systems without a
		// limited magazine report a huge ammo count
		boolean systemReady = system != null
			&& system.getCooldownRemaining() < leadTime
			&& (system.getAmmo() >= Integer.MAX_VALUE || system.getAmmo() > 0)
			&& (!target.isNonCombat(false) || shipIsNonCombat);
		float effectiveTime = Math.max(0.0F, leadTime - recoverTime);
		if (!systemReady) {
			effectiveTime = 0.0F;
		}

		float closingSpeed = getClosingSpeed(ship, target);
		float targetSpeed = getPlanningSpeedWithoutZeroFluxBoost(target);
		if (effectiveTime > 0.0F) {
			// approximation: system spec AI hints are not exposed; burst
			// movement potential treated as 0
			targetSpeed += 0.0F;
		}

		if (target.getPhaseCloak() != null && target.getHardFluxLevel() < 0.2F) {
			targetSpeed *= 2.0F;
		}

		if (effectiveTime > 0.0F) {
			// approximation: system spec AI hints are not exposed; active
			// speed increase treated as 0
			targetSpeed += 0.0F * effectiveTime / leadTime;
		}

		float shipSpeed = getPlanningSpeedWithoutZeroFluxBoost(ship);

		return Math.max(closingSpeed, targetSpeed - shipSpeed);
	}

	public static MissileAPI getNearestEnemyMissile(ShipAPI ship) {
		return getNearestEnemyMissile(ship, Float.MAX_VALUE);
	}

	public static MissileAPI getNearestEnemyMissile(ShipAPI ship, float maxRange) {
		return getNearestEnemyMissile(ship, ship.getLocation(), maxRange);
	}

	public static MissileAPI getNearestEnemyMissile(ShipAPI ship, Vector2f from, float maxRange) {
		List<MissileAPI> missiles = getEngine().getMissiles();
		float closest = Float.MAX_VALUE;
		MissileAPI result = null;

		for (MissileAPI missile : missiles) {
			if (missile.getOwner() != ship.getOwner()) {
				float distance = getDistance(missile.getLocation(), from);
				if (distance < closest && distance < maxRange) {
					closest = distance;
					result = missile;
				}
			}
		}

		return result;
	}

	public static float getClosingSpeed(CombatEntityAPI from, CombatEntityAPI to) {
		return getClosingSpeed(from.getLocation(), to.getLocation(), from.getVelocity(), to.getVelocity());
	}

	public static float getClosingSpeed(Vector2f fromLocation, Vector2f toLocation, Vector2f fromVelocity, Vector2f toVelocity) {
		Vector2f offset = Vector2f.sub(fromLocation, toLocation, new Vector2f());
		normalise(offset);
		Vector2f relativeVelocity = Vector2f.sub(toVelocity, fromVelocity, new Vector2f());
		return Vector2f.dot(offset, relativeVelocity);
	}

	public static float computeDanger(ShipAPI ship, CombatEntityAPI danger, Vector2f from, Vector2f to, float closingSpeedBonus, float widthMargin, float range) {
		float directDistance = getDistance(from, to);
		float dangerDistance = directDistance;
		float closingSpeed;
		if (danger != null) {
			closingSpeed = getClosingSpeed(ship, danger) + closingSpeedBonus;
			dangerDistance = getDistance(from, danger.getLocation()) + danger.getCollisionRadius();
		} else {
			closingSpeed = closingSpeedBonus;
		}

		float timeToImpact = directDistance / closingSpeed;
		if (timeToImpact > 3.0F) {
			timeToImpact = 3.0F;
		}

		float fighterSpeedMargin = Math.max(ship.getMaxSpeed(), ship.getVelocity().length() + 20.0F);
		fighterSpeedMargin = Math.max(ship.getHullSpec().getEngineSpec().getMaxSpeed(), fighterSpeedMargin);
		fighterSpeedMargin *= 0.5F;
		if (!ship.isFighter()) {
			fighterSpeedMargin = 0.0F;
		}

		boolean dangerIsPhasedFighter = danger instanceof ShipAPI && (((ShipAPI) danger).isFighter() || ((ShipAPI) danger).isPhased()) && (widthMargin >= 1.0F || closingSpeedBonus < 10000.0F);
		float heading = getAngleFast(from, to);
		List<ShipAPI> ships = getEngine().getShips();
		float worst = 0.0F;

		for (ShipAPI other : ships) {
			if (other != ship && !other.isFighter() && (!other.isDrone() || other.getCollisionClass() != CollisionClass.FIGHTER) && other != danger) {
				boolean throughStationPair = false;
				if (ship.getParentStation() == other && ship.getStationSlot() != null || other.getParentStation() == ship && other.getStationSlot() != null) {
					boolean doNotFireThrough = other.getMutableStats().getDynamic().getMod("do_not_fire_through").computeEffective(0.0F) <= 0.0F;
					if (doNotFireThrough) {
						continue;
					}

					throughStationPair = true;
				}

				if (ship.getParentStation() != null
					&& ship.getParentStation() == other.getParentStation()
					&& ship.getStationSlot() != null
					&& other.getStationSlot() != null) {
					boolean doNotFireThrough = other.getMutableStats().getDynamic().getMod("do_not_fire_through").computeEffective(0.0F) <= 0.0F;
					if (doNotFireThrough) {
						continue;
					}

					throughStationPair = true;
				}

				if (ship.getOwner() == other.getOwner()) {
					float distance = getDistance(from, other.getLocation());
					float otherSpeed = Math.max(other.getMaxSpeed(), other.getVelocity().length() + 20.0F);
					otherSpeed = Math.max(other.getHullSpec().getEngineSpec().getMaxSpeed(), otherSpeed);
					otherSpeed += fighterSpeedMargin;
					if (throughStationPair) {
						otherSpeed = 0.0F;
					}

					if (!(distance > range + other.getCollisionRadius() + otherSpeed * timeToImpact)) {
						float angleToOther = getAngleFast(from, other.getLocation());
						float angleDiff = getAngleDiff(heading, angleToOther);
						if ((ship.isFighter() && !(distance > other.getCollisionRadius() * 1.0F) || !(angleDiff > 100.0F))
							&& (!throughStationPair || !(angleDiff > 30.0F))
							&& (!(distance >= other.getCollisionRadius() + ship.getCollisionRadius()) || !(angleDiff >= 120.0F))
							&& (dangerIsPhasedFighter || !(distance > dangerDistance + other.getCollisionRadius()) || !(angleDiff < 30.0F))) {
							float clearance = other.getCollisionRadius() * 1.1F;
							if (other.getShield() == null || !other.getShield().isOn()) {
								clearance *= 0.8F;
							}

							boolean stationModuleVsDrone = ship.isStationModule() && other.isDrone();
							float droneMult = 1.0F;
							if (stationModuleVsDrone) {
								droneMult = 0.25F;
							}

							clearance += timeToImpact * otherSpeed * 0.75F * droneMult;
							clearance += widthMargin * otherSpeed * 1.0F * droneMult;
							float segmentDistance = distanceToSegment(from, to, other.getLocation());
							if (segmentDistance < clearance) {
								float dangerLevel = 1.0F - segmentDistance / clearance;
								if (dangerLevel > worst) {
									worst = dangerLevel;
								}
							}
						}
					}
				}
			}
		}

		return worst;
	}

	public static boolean isSameStationOrShip(ShipAPI ship, ShipAPI other) {
		return ship == other
			? true
			: ship.getParentStation() != null && ship.getParentStation() == other.getParentStation()
				|| other.getParentStation() == ship
				|| ship.getParentStation() == other;
	}

	public static void clearBlockingShips() {
		CombatEngineAPI engine = getEngine();
		if (engine != null) engine.getCustomData().remove(BLOCKING_SHIPS_KEY);
	}

	public static void resetBlockingShips() {
		clearBlockingShips();
	}

	// approximation: CombatEngineAPI does not expose the engine's two cache
	// reset points; elapsed combat time identifies the current AI advance frame.
	static List<ShipAPI> getBlockingShips(CombatEngineAPI engine) {
		float now = engine.getTotalElapsedTime(false);
		Object stored = engine.getCustomData().get(BLOCKING_SHIPS_KEY);
		if (stored instanceof BlockingShipsCache cache && cache.sampledAt == now) {
			return cache.ships;
		}
		BlockingShipsCache cache = new BlockingShipsCache();
		cache.sampledAt = now;
		cache.ships = new ArrayList<>();
		for (ShipAPI other : engine.getShips()) {
			if (!other.isFighter() && (other.isHulk() && other.getStationSlot() == null || !other.isTargetable())) {
				cache.ships.add(other);
			}
		}
		engine.getCustomData().put(BLOCKING_SHIPS_KEY, cache);
		return cache.ships;
	}

	public static boolean areHulksInTheWay(ShipAPI ship, CombatEntityAPI danger, Vector2f from, Vector2f to) {
		return areHulksInTheWay(ship, danger, from, to, false);
	}

	public static boolean areHulksInTheWay(ShipAPI ship, CombatEntityAPI danger, Vector2f from, Vector2f to, boolean halfWidth) {
		float dangerStartDistance = getDistance(from, danger.getLocation());
		float worst = 0.0F;

		for (ShipAPI blocker : getBlockingShips(getEngine())) {
			if ((blocker.getParentStation() == null || blocker.getParentStation() != ship.getParentStation())
				&& ship.getParentStation() != blocker
				&& blocker.getParentStation() != ship
				&& blocker != ship
				&& !blocker.isFighter()
				&& blocker != danger) {
				float blockerDistance = getDistance(from, blocker.getLocation());
				if (!(blockerDistance > dangerStartDistance + blocker.getCollisionRadius()) && (blocker.getParentStation() == null || blocker.getStationSlot() == null)) {
					float clearance = blocker.getCollisionRadius() * 1.0F;
					if (blocker.getShield() == null || !blocker.getShield().isOn()) {
						clearance *= 0.8F;
					}

					if (halfWidth) {
						clearance *= 0.5F;
					}

					float segmentDistance = distanceToSegment(from, to, blocker.getLocation());
					if (segmentDistance < clearance) {
						// approximation: engine Bounds.rotateAndTranslate maps
						// to BoundsAPI.update(location, facing)
						if (danger.getExactBounds() != null && blocker.getExactBounds() != null) {
							blocker.getExactBounds().update(blocker.getLocation(), blocker.getFacing());
							danger.getExactBounds().update(danger.getLocation(), danger.getFacing());
							Vector2f blockerHit = getClosestPointOnBounds(from, danger.getLocation(), blocker.getExactBounds());
							Vector2f dangerHit = getClosestPointOnBounds(from, danger.getLocation(), danger.getExactBounds());
							if (blockerHit == null || dangerHit == null) {
								continue;
							}

							float blockerHitDistance = getDistance(from, blockerHit);
							float dangerHitDistance = getDistance(from, dangerHit);
							if (blockerHitDistance < dangerHitDistance - danger.getCollisionRadius() * 1.0F) {
								return true;
							}
						}

						if (!(blockerDistance > dangerStartDistance)) {
							float overlap = 1.0F - segmentDistance / clearance;
							if (overlap > worst) {
								worst = overlap;
							}

							if (overlap > 0.1F) {
								return true;
							}
						}
					}
				}
			}
		}

		return worst > 0.1F;
	}

	public static Vector2f getClosestPointOnBounds(Vector2f from, Vector2f to, BoundsAPI bounds) {
		Vector2f closest = null;
		float closestDistance = Float.MAX_VALUE;

		for (BoundsAPI.SegmentAPI segment : bounds.getSegments()) {
			Vector2f hit = segmentIntersection(from, to, segment.getP1(), segment.getP2());
			if (hit != null) {
				float distance = getDistanceSquared(from, hit);
				if (distance < closestDistance) {
					closestDistance = distance;
					closest = hit;
				}
			}
		}

		return closest;
	}

	// ===== AIUtils: enemy search =====

	public static ShipAPI getNearestEnemy(ShipAPI ship) {
		return getNearestEnemy(ship, false);
	}

	public static ShipAPI getNearestEnemy(ShipAPI ship, boolean skipAvoidTargets) {
		return getNearestEnemy(ship, skipAvoidTargets, 1000000.0F);
	}

	public static ShipAPI getNearestEnemy(ShipAPI ship, boolean skipAvoidTargets, float maxRange) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (!other.isFighter()
				&& !other.isHulk()
				&& ship.getOwner() != other.getOwner()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100
				&& (!skipAvoidTargets || !isShipEnemy(ship, other))) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (!(distance > maxRange) && distance < closest) {
					result = other;
					closest = distance;
				}
			}
		}

		return result;
	}

	public static ShipAPI getNearestEnemyCruiserOrCapital(ShipAPI ship) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (!other.isFighter()
				&& !other.isFrigate()
				&& !other.isDestroyer()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& !other.isHulk()
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100
				&& other.isTargetable()
				&& !isShipEnemy(ship, other)) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (distance < closest) {
					result = other;
					closest = distance;
				}
			}
		}

		return result;
	}

	public static ShipAPI getNearestEnemyDestroyerOrBigger(ShipAPI ship) {
		return getNearestEnemyDestroyerOrBigger(ship, false);
	}

	public static ShipAPI getNearestEnemyDestroyerOrBigger(ShipAPI ship, boolean skipAvoidTargets) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (!other.isFighter()
				&& !other.isFrigate()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& !other.isHulk()
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100
				&& (!skipAvoidTargets || !isShipEnemy(ship, other))) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (distance < closest) {
					result = other;
					closest = distance;
				}
			}
		}

		return result;
	}

	public static ShipAPI getNearestEnemyExcludingDrones(ShipAPI ship, boolean skipAvoidTargets) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (!other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& !other.isHulk()
				&& !other.isDrone()
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100
				&& (!skipAvoidTargets || !isShipEnemy(ship, other))) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (distance < closest) {
					result = other;
					closest = distance;
				}
			}
		}

		return result;
	}

	public static ShipAPI getNearestEnemyOfMinHullSizeInRange(ShipAPI ship, HullSize minSize, float maxRange) {
		return getNearestEnemyOfMinHullSizeInRange(ship, ship.getLocation(), minSize, maxRange);
	}

	public static ShipAPI getNearestEnemyOfMinHullSizeInRange(ShipAPI ship, Vector2f from, HullSize minSize, float maxRange) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (other.getHullSize().ordinal() >= minSize.ordinal()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& !other.isHulk()
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100) {
				float distance = getDistance(from, other.getLocation());
				if (!(distance > maxRange) && distance < closest) {
					result = other;
					closest = distance;
				}
			}
		}

		return result;
	}

	public static ShipAPI getNearestEnemyInArcRange(ShipAPI ship, Vector2f from, float facing, float arc, HullSize minSize, float maxRange) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (other.getHullSize().ordinal() >= minSize.ordinal()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& !other.isHulk()
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100) {
				float distance = getDistance(from, other.getLocation());
				if (!(distance > maxRange) && Misc.isInArc(facing, arc, from, other.getLocation()) && distance < closest) {
					result = other;
					closest = distance;
				}
			}
		}

		return result;
	}

	public static ShipAPI getNearestShip(int owner, Vector2f from, boolean includeFighters) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (!other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& !other.isHulk()
				&& other.isTargetable()
				&& (includeFighters || !other.isFighter())
				&& owner != other.getOwner()
				&& other.getOwner() != 100) {
				float distance = getDistance(from, other.getLocation());
				if (distance < closest) {
					result = other;
					closest = distance;
				}
			}
		}

		return result;
	}

	public static ShipAPI getNearestEnemyInRangeIncludingFighters(ShipAPI ship, float maxRange) {
		return getNearestEnemyInRange(ship, maxRange, false);
	}

	public static ShipAPI getNearestEnemyInRange(ShipAPI ship, float maxRange, boolean skipAvoidTargets) {
		ShipAPI nearest = getNearestEnemyExcludingDrones(ship, skipAvoidTargets);
		if (nearest != null) {
			float distance = getDistance(ship.getLocation(), nearest.getLocation());
			if (distance > maxRange) {
				nearest = null;
			}
		}

		return nearest;
	}

	public static ShipAPI getNearestEnemyInRange(ShipAPI ship) {
		return getNearestEnemyInRangeNoFighters(ship, Float.MAX_VALUE);
	}

	public static ShipAPI getNearestEnemyInRangeNoFighters(ShipAPI ship, float maxRange) {
		return getNearestEnemyInRangeNoFighters(ship, ship.getLocation(), maxRange, false);
	}

	public static ShipAPI getNearestEnemyInRangeNoFighters(ShipAPI ship, Vector2f from, float maxRange) {
		return getNearestEnemyInRangeNoFighters(ship, from, maxRange, false);
	}

	// intentionally kept faithful: the vanilla body matches fighters only,
	// despite the method name
	public static ShipAPI getNearestEnemyInRangeNoFighters(ShipAPI ship, Vector2f from, float maxRange, boolean skipAvoidTargets) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (!other.isShuttlePod()
				&& other.isFighter()
				&& !other.isHulk()
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100
				&& (!skipAvoidTargets || !isShipEnemy(ship, other))) {
				float distance = getDistance(from, other.getLocation());
				if (distance < closest && distance < maxRange) {
					result = other;
					closest = distance;
				}
			}
		}

		return result;
	}

	// approximation: the engine scans its internal list of all collision
	// entities; the API only exposes ships, missiles, asteroids and damaging
	// projectiles, so those four lists are scanned together
	private static List<CombatEntityAPI> getAllCollisionEntities() {
		CombatEngineAPI engine = getEngine();
		List<CombatEntityAPI> entities = new ArrayList<>();
		Set<CombatEntityAPI> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		addUniqueCollisionEntities(entities, seen, engine.getShips());
		addUniqueCollisionEntities(entities, seen, engine.getMissiles());
		addUniqueCollisionEntities(entities, seen, engine.getAsteroids());
		addUniqueCollisionEntities(entities, seen, engine.getProjectiles());
		return entities;
	}

	static void addUniqueCollisionEntities(List<CombatEntityAPI> output, Set<CombatEntityAPI> seen, List<? extends CombatEntityAPI> candidates) {
		for (CombatEntityAPI entity : candidates) {
			if (seen.add(entity)) {
				output.add(entity);
			}
		}
	}

	public static CombatEntityAPI getNearestEnemyEntity(CombatEntityAPI excluded, CollisionClass... collisionClasses) {
		return getNearestEnemyEntity(excluded.getLocation(), excluded.getOwner(), collisionClasses);
	}

	public static CombatEntityAPI getNearestEnemyEntity(Vector2f from, int owner, CollisionClass... collisionClasses) {
		List<CombatEntityAPI> entities = getAllCollisionEntities();
		float closest = Float.MAX_VALUE;
		CombatEntityAPI result = null;
		int bestClassIndex = 1000;
		FogOfWarAPI fogOfWar = getEngine().getFogOfWar(owner);

		for (CombatEntityAPI entity : entities) {
			int classIndex = indexOfCollisionClass(entity.getCollisionClass(), collisionClasses);
			boolean notTargetableShip = entity instanceof ShipAPI ? !((ShipAPI) entity).isTargetable() : entity.getCollisionClass() == CollisionClass.FIGHTER || entity.getCollisionClass() == CollisionClass.SHIP;
			if (classIndex <= bestClassIndex
				&& !notTargetableShip
				&& owner != entity.getOwner()
				&& entity.getOwner() != 100) {
				float distance = getDistance(from, entity.getLocation());
				if ((!(distance > 3000.0F) || isEntityVisible(owner, entity)) && distance < closest) {
					bestClassIndex = classIndex;
					result = entity;
					closest = distance;
				}
			}
		}

		return result;
	}

	public static CombatEntityAPI getNearestEnemyEntityByCollisionClass(Vector2f from, int owner, CollisionClass... collisionClasses) {
		List<CombatEntityAPI> entities = getAllCollisionEntities();
		List<CollisionClass> classes = Arrays.asList(collisionClasses);
		float closest = Float.MAX_VALUE;
		CombatEntityAPI result = null;

		for (CombatEntityAPI entity : entities) {
			if (classes.contains(entity.getCollisionClass())
				&& entity.getOwner() != owner
				&& (!(entity instanceof ShipAPI) || ((ShipAPI) entity).isTargetable())
				&& entity.getOwner() != 100) {
				float distance = getDistance(from, entity.getLocation());
				if ((!(distance > 3000.0F) || isEntityVisible(owner, entity)) && distance < closest) {
					result = entity;
					closest = distance;
				}
			}
		}

		return result;
	}

	// approximation: the engine AI grid visits cells overlapping a square of
	// side 2 * maxRange. The public API exposes entity lists instead of that
	// grid, so the same square bounds the candidates without cell-edge spill.
	public static CombatEntityAPI getNearestEnemyEntityInRange(Vector2f from, int owner, float maxRange, CollisionClass... collisionClasses) {
		List<CombatEntityAPI> candidates = new ArrayList<>();
		for (CombatEntityAPI entity : getAllCollisionEntities()) {
			if (isWithinAiGridQuery(from, entity.getLocation(), maxRange)) {
				candidates.add(entity);
			}
		}

		List<CollisionClass> classes = Arrays.asList(collisionClasses);
		float closest = Float.MAX_VALUE;
		CombatEntityAPI result = null;

		for (CombatEntityAPI entity : candidates) {
			if (classes.contains(entity.getCollisionClass())
				&& entity.getOwner() != owner
				&& (!(entity instanceof ShipAPI) || ((ShipAPI) entity).isTargetable())
				&& entity.getOwner() != 100) {
				float distance = getDistance(from, entity.getLocation());
				if ((!(distance > 3000.0F) || isEntityVisible(owner, entity)) && distance < closest) {
					result = entity;
					closest = distance;
				}
			}
		}

		return result;
	}

	static boolean isWithinAiGridQuery(Vector2f center, Vector2f candidate, float halfExtent) {
		return Math.abs(candidate.x - center.x) <= halfExtent
			&& Math.abs(candidate.y - center.y) <= halfExtent;
	}

	public static CombatEntityAPI getNearestEnemyEntity(CombatEntityAPI excluded, Vector2f from, CollisionClass... collisionClasses) {
		List<CombatEntityAPI> entities = getAllCollisionEntities();
		List<CollisionClass> classes = Arrays.asList(collisionClasses);
		float closest = Float.MAX_VALUE;
		CombatEntityAPI result = null;

		for (CombatEntityAPI entity : entities) {
			if (classes.contains(entity.getCollisionClass()) && entity != excluded && (!(entity instanceof ShipAPI) || ((ShipAPI) entity).isTargetable()) && entity.getOwner() != 100) {
				float distance = getDistance(from, entity.getLocation());
				if (distance < closest) {
					result = entity;
					closest = distance;
				}
			}
		}

		return result;
	}

	private static int indexOfCollisionClass(CollisionClass collisionClass, CollisionClass[] collisionClasses) {
		for (int i = 0; i < collisionClasses.length; i++) {
			if (collisionClass == collisionClasses[i]) {
				return i;
			}
		}

		return 1001;
	}

	// ===== AIUtils: intercept points and predictions =====

	public static Vector2f getInterceptPoint(CombatEntityAPI shooter, CombatEntityAPI target, float closingSpeedBonus, boolean returnNullIfUnreachable) {
		return getInterceptPoint(shooter, target.getLocation(), target.getVelocity(), closingSpeedBonus, returnNullIfUnreachable, false);
	}

	public static Vector2f computeInterceptPoint(CombatEntityAPI shooter, CombatEntityAPI target, float closingSpeedBonus, boolean returnNullIfUnreachable) {
		return getInterceptPoint(shooter, target.getLocation(), target.getVelocity(), closingSpeedBonus, returnNullIfUnreachable, true);
	}

	public static Vector2f getInterceptPoint(CombatEntityAPI shooter, Vector2f targetLocation, Vector2f targetVelocity, float closingSpeedBonus, boolean returnNullIfUnreachable, boolean fullLead) {
		Vector2f relativeVelocity = Vector2f.sub(targetVelocity, shooter.getVelocity(), new Vector2f());
		Vector2f heading = Vector2f.sub(targetLocation, shooter.getLocation(), new Vector2f());
		normalise(heading);
		float closingSpeed = getClosingSpeed(shooter.getLocation(), targetLocation, shooter.getVelocity(), targetVelocity) + closingSpeedBonus;
		if (closingSpeed <= 10.0F) {
			if (returnNullIfUnreachable) {
				return null;
			}

			closingSpeed = 20.0F;
		}

		Vector2f interceptPoint = new Vector2f(targetLocation);
		Vector2f leadDirection = normalise(new Vector2f(relativeVelocity));
		float alignment = Vector2f.dot(heading, leadDirection);
		float lateralFactor = 1.0F - Math.abs(alignment);
		float distance = getDistance(shooter.getLocation(), targetLocation);
		distance -= shooter.getCollisionRadius();
		distance *= 0.85F;
		if (distance < 0.0F) {
			distance = 0.0F;
		}

		float timeToImpact = distance / closingSpeed;
		if (timeToImpact > 10.0F) {
			timeToImpact = 10.0F;
		}

		if (fullLead) {
			interceptPoint.x = interceptPoint.x + relativeVelocity.x * timeToImpact;
			interceptPoint.y = interceptPoint.y + relativeVelocity.y * timeToImpact;
		} else {
			interceptPoint.x = interceptPoint.x + relativeVelocity.x * lateralFactor * timeToImpact;
			interceptPoint.y = interceptPoint.y + relativeVelocity.y * lateralFactor * timeToImpact;
		}

		return interceptPoint;
	}

	public static float getPredictedDistance(CombatEntityAPI entity, Vector2f otherLocation, Vector2f otherVelocity) {
		return getPredictedDistance(entity.getLocation(), entity.getVelocity(), otherLocation, otherVelocity);
	}

	public static float getPredictedDistance(Vector2f location, Vector2f velocity, Vector2f otherLocation, Vector2f otherVelocity) {
		float leadTime = 1.0F;
		Vector2f predictedLocation = new Vector2f(location);
		Vector2f predictedOtherLocation = new Vector2f(otherLocation);
		float currentDistance = getDistance(predictedLocation, predictedOtherLocation);
		predictedLocation.x = predictedLocation.x + velocity.x * leadTime;
		predictedLocation.y = predictedLocation.y + velocity.y * leadTime;
		predictedOtherLocation.x = predictedOtherLocation.x + otherVelocity.x * leadTime;
		predictedOtherLocation.y = predictedOtherLocation.y + otherVelocity.y * leadTime;
		float predictedDistance = getDistance(predictedLocation, predictedOtherLocation);
		return currentDistance - predictedDistance;
	}

	// ===== AIUtils: turn and move commands =====

	public static float turnToFace(ShipAPI ship, Vector2f target, boolean decelerateWhenClose) {
		float turnDirection = getTurnDirection(ship.getFacing(), ship.getLocation(), target);
		float angleDiff = getAngleDiff(ship.getFacing(), ship.getLocation(), target);
		if (angleDiff > 1.0F) {
			if (turnDirection < 0.0F) {
				ship.giveCommand(ShipCommand.TURN_RIGHT, null, -1);
			} else if (turnDirection > 0.0F) {
				ship.giveCommand(ShipCommand.TURN_LEFT, null, -1);
			}
		}

		float velocityFacingDiff = getAngleDiff(ship.getFacing(), new Vector2f(0.0F, 0.0F), ship.getVelocity());
		float distance = getDistance(target, ship.getLocation());
		float driftDistance = ship.getVelocity().length() * 0.5F * (ship.getMaxSpeed() / (ship.getAcceleration() / 3.0F));
		if (!(distance > driftDistance) && decelerateWhenClose) {
			ship.giveCommand(ShipCommand.DECELERATE, null, -1);
		} else if (velocityFacingDiff > 90.0F) {
			ship.giveCommand(ShipCommand.DECELERATE, null, -1);
		} else if (velocityFacingDiff > 30.0F) {
			ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
		} else if (ship.getVelocity().length() < ship.getMaxSpeed()) {
			ship.giveCommand(ShipCommand.ACCELERATE, null, -1);
		}

		return distance;
	}

	public static float turnToFace(ShipAPI ship, Vector2f target) {
		float turnDirection = getTurnDirection(ship.getFacing(), ship.getLocation(), target);
		float angleDiff = getAngleDiff(ship.getFacing(), ship.getLocation(), target);
		if (angleDiff > 5.0F) {
			if (turnDirection < 0.0F) {
				ship.giveCommand(ShipCommand.TURN_RIGHT, null, -1);
			} else if (turnDirection > 0.0F) {
				ship.giveCommand(ShipCommand.TURN_LEFT, null, -1);
			}
		}

		return angleDiff;
	}

	public static float giveSpeedCommand(ShipAPI ship, float maxSpeed) {
		float speed = ship.getVelocity().length();
		if (speed > maxSpeed) {
			ship.giveCommand(ShipCommand.DECELERATE, null, -1);
		}

		return speed;
	}

	// The engine ShipCommandReceiver turnToFace overloads are not portable:
	// under the mod API their receiver type collapses into ShipAPI, which
	// clashes with the float-returning ship overloads above, and vanilla
	// ship-typed calls always resolved to those float overloads.

	public static void giveMoveCommands(ShipAPI receiver, float heading, float desiredSpeed) {
		setMoveCommands(receiver, heading, desiredSpeed);
	}

	public static void setMoveCommands(ShipAPI receiver, float heading, float desiredSpeed) {
		Vector2f velocity = receiver.getVelocity();
		// approximation: effective engine stats are not exposed on the engine
		// controller API; the ship's own stats are used instead
		float maxSpeed = receiver.getMaxSpeed();
		float acceleration = receiver.getAcceleration();
		float deceleration = receiver.getDeceleration();
		float strafeAcceleration = receiver.getAcceleration();
		boolean debug = false;
		if (debug) {
			// intentionally kept dead: the vanilla debug block read the mouse
			// position through engine-only input and viewport APIs
		}

		Vector2f headingVector = getUnitVectorAtAngle(heading);
		float velocityAlongHeading = Vector2f.dot(velocity, headingVector);
		if (desiredSpeed > maxSpeed) {
			desiredSpeed = maxSpeed;
		}

		if (receiver.isDrone()) {
			float speedError = Math.abs(desiredSpeed - velocityAlongHeading);
			float velocityError = Math.abs(velocityAlongHeading - receiver.getVelocity().length());
			if (speedError < acceleration * 0.1F && velocityError < 10.0F) {
				return;
			}
		}

		Vector2f velocityDirection = normalise(new Vector2f(velocity));
		Vector2f facingVector = getUnitVectorAtAngle(receiver.getFacing());
		Vector2f backwardsVector = (Vector2f) new Vector2f(facingVector).negate();
		Vector2f reverseVector = (Vector2f) new Vector2f(velocityDirection).negate();
		Vector2f strafeLeftVector = getUnitVectorAtAngle(receiver.getFacing() + 90.0F);
		Vector2f strafeRightVector = getUnitVectorAtAngle(receiver.getFacing() - 90.0F);
		float bestScore = 0.0F;
		float accelerateScore = computeMovementScore(velocity, headingVector, facingVector, acceleration, maxSpeed) - velocityAlongHeading;
		float decelerateScore = computeMovementScore(velocity, headingVector, reverseVector, deceleration, maxSpeed) - velocityAlongHeading;
		float backwardsScore = computeMovementScore(velocity, headingVector, backwardsVector, deceleration, maxSpeed) - velocityAlongHeading;
		float strafeLeftScore = computeMovementScore(velocity, headingVector, strafeLeftVector, strafeAcceleration, maxSpeed) - velocityAlongHeading;
		float strafeRightScore = computeMovementScore(velocity, headingVector, strafeRightVector, strafeAcceleration, maxSpeed) - velocityAlongHeading;
		bestScore = Math.max(bestScore, accelerateScore);
		bestScore = Math.max(bestScore, backwardsScore);
		bestScore = Math.max(bestScore, strafeLeftScore);
		bestScore = Math.max(bestScore, strafeRightScore);
		float positiveScoreSum = Math.max(accelerateScore, 0.0F) + Math.max(backwardsScore, 0.0F) + Math.max(strafeLeftScore, 0.0F) + Math.max(strafeRightScore, 0.0F);
		float threshold = 0.0F;
		float bestFraction = 0.75F;
		float sumFraction = 0.33F;
		int commandsGiven = 0;
		if (accelerateScore > threshold && (accelerateScore > bestScore * bestFraction || accelerateScore > positiveScoreSum * sumFraction) && accelerateScore > backwardsScore) {
			receiver.giveCommand(ShipCommand.ACCELERATE, null, -1);
			commandsGiven++;
		}

		if (backwardsScore > threshold && (backwardsScore > bestScore * bestFraction || backwardsScore > positiveScoreSum * sumFraction) && backwardsScore > accelerateScore) {
			receiver.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, -1);
			commandsGiven++;
		}

		if (strafeLeftScore > threshold && (strafeLeftScore > bestScore * bestFraction || strafeLeftScore > positiveScoreSum * sumFraction) && strafeLeftScore > strafeRightScore) {
			receiver.giveCommand(ShipCommand.STRAFE_LEFT, null, -1);
			commandsGiven++;
		}

		if (strafeRightScore > threshold && (strafeRightScore > bestScore * bestFraction || strafeRightScore > positiveScoreSum * sumFraction) && strafeRightScore > strafeLeftScore) {
			receiver.giveCommand(ShipCommand.STRAFE_RIGHT, null, -1);
			commandsGiven++;
		}

		if (decelerateScore > threshold && (decelerateScore > decelerateScore * bestFraction || decelerateScore > positiveScoreSum * sumFraction) && commandsGiven < 2) {
			receiver.giveCommand(ShipCommand.DECELERATE, null, -1);
			commandsGiven++;
		}
	}

	private static float computeMovementScore(Vector2f velocity, Vector2f headingVector, Vector2f direction, float acceleration, float maxSpeed) {
		acceleration *= 0.016666668F;
		Vector2f velocityStep = (Vector2f) new Vector2f(direction).scale(acceleration);
		Vector2f newVelocity = Vector2f.add(velocity, velocityStep, new Vector2f());
		float speed = newVelocity.length();
		if (speed >= maxSpeed) {
			float excess = speed - maxSpeed;
			newVelocity.scale((speed - excess) / speed);
		}

		return Vector2f.dot(newVelocity, headingVector);
	}

	public static ShipAPI getNearestEnemyNotInArc(ShipAPI ship, ShipAPI target, float maxRange, boolean includeFighters) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;
		float baseDistance = Math.max(100.0F, getDistance(ship.getLocation(), target.getLocation()));
		float targetArc = Math.min(150.0F, (float) Math.toDegrees(target.getCollisionRadius() * 2.0F / baseDistance));
		float heading = getAngleFast(ship.getLocation(), target.getLocation());

		for (ShipAPI other : ships) {
			if ((!other.isFighter() || includeFighters)
				&& (!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100
				&& !isInArc(heading, targetArc, ship.getLocation(), other.getLocation())) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (distance < closest && distance < maxRange) {
					closest = distance;
					result = other;
				}
			}
		}

		return result;
	}

	public static boolean isShipInRangeOf(ShipAPI ship, Vector2f point, ShipAPI of) {
		return isPointInRangeOf(ship, point, of.getLocation(), of.getCollisionRadius() * 1.0F + 150.0F);
	}

	public static boolean isPointInRangeOf(ShipAPI ship, Vector2f point, Vector2f center, float radius) {
		float centerDistance = Misc.getDistance(point, center);
		if (centerDistance < radius) {
			return true;
		}

		float centerToShipDistance = Math.max(100.0F, getDistance(ship.getLocation(), center));
		float centerToPointDistance = Math.max(100.0F, getDistance(ship.getLocation(), point));
		float arc = Math.min(150.0F, (float) Math.toDegrees(radius * 2.0F / centerToShipDistance));
		float heading = getAngleFast(ship.getLocation(), center);
		return centerToShipDistance < centerToPointDistance && isInArc(heading, arc, ship.getLocation(), point);
	}

	public static ShipAPI getNearestEnemyInRange(ShipAPI ship, float maxRange) {
		return getNearestEnemyInRange(ship, ship.getLocation(), maxRange);
	}

	public static ShipAPI getNearestEnemyInRange(ShipAPI ship, Vector2f from, float maxRange) {
		return getNearestEnemyInRange(ship, from, maxRange, null);
	}

	public static ShipAPI getNearestEnemyInRange(ShipAPI ship, Vector2f from, float maxRange, ShipAPI excluded) {
		return getNearestEnemyInRange(ship, from, maxRange, excluded, true);
	}

	public static ShipAPI getNearestEnemyInRange(ShipAPI ship, Vector2f from, float maxRange, ShipAPI excluded, boolean includeFighters) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if ((!other.isFighter() || includeFighters)
				&& (!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100
				&& other != excluded
				&& other.isTargetable()) {
				float distance = getDistance(from, other.getLocation());
				if (distance < closest && distance < maxRange) {
					closest = distance;
					result = other;
				}
			}
		}

		return result;
	}

	public static ShipAPI getNearestEnemyInRangeNoTaskCheck(ShipAPI ship, float maxRange) {
		List<ShipAPI> ships = getEngine().getShips();
		float closest = Float.MAX_VALUE;
		ShipAPI result = null;

		for (ShipAPI other : ships) {
			if (!other.isHulk() && !other.isShuttlePod() && ship.getOwner() != other.getOwner() && other.getOwner() != 100 && other.isTargetable()) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (distance < closest && distance < maxRange) {
					closest = distance;
					result = other;
				}
			}
		}

		return result;
	}

	// ===== AIUtils: allies and escort bookkeeping =====

	public static int getNumberOfAlliesEscortingTarget(ShipAPI ship, ShipAPI target, Vector2f from, float maxRange) {
		return getNumberOfAlliesEscortingTarget(ship, target, from, maxRange, HullSize.FIGHTER);
	}

	public static int getNumberOfAlliesEscortingTarget(ShipAPI ship, ShipAPI target, Vector2f from, float maxRange, HullSize minSize) {
		List<ShipAPI> ships = getEngine().getShips();
		int count = 0;

		for (ShipAPI other : ships) {
			if (ship.getOwner() == other.getOwner()
				&& (!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& (!other.isFighter() || ship.isFighter())
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& other != ship
				&& other != target
				&& other.getParentStation() == null
				&& other.getHullSize().ordinal() >= minSize.ordinal()
				&& isEscortTargetOf(other, target)
				&& ship.getOwner() == other.getOwner()
				&& !isSameStationOrShip(ship, other)) {
				float distanceToOther = getDistance(from, other.getLocation());
				float shipToOtherDistance = getDistance(ship.getLocation(), other.getLocation());
				if (shipToOtherDistance > distanceToOther && distanceToOther < maxRange) {
					count++;
				}
			}
		}

		return count;
	}

	public static float getDistance(ShipAPI ship, ShipAPI other) {
		// returns a side indicator, not a distance; the vanilla name is kept
		float heading = getAngleFast(ship.getLocation(), other.getLocation());
		float range = getDistance(ship.getLocation(), other.getLocation()) + 750.0F;
		float rightThreat = getTotalThreatInRange(ship, heading + 70.0F, 140.0F, range, true, false);
		float leftThreat = getTotalThreatInRange(ship, heading - 70.0F, 140.0F, range, true, false);
		float side = rightThreat > leftThreat ? 1.0F : -1.0F;
		if (rightThreat == leftThreat) {
			side = 0.0F;
		}

		return side;
	}

	public static int getNumberOfAlliesInRange(ShipAPI ship, ShipAPI near) {
		List<ShipAPI> ships = getEngine().getShips();
		float referenceDistance = getDistance(ship.getLocation(), near.getLocation());
		int count = 0;

		for (ShipAPI other : ships) {
			if (!other.isFighter()
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& other != ship
				&& other != near
				&& other.getHullSize().ordinal() + 1 >= ship.getHullSize().ordinal()
				&& ship.getOwner() == other.getOwner()
				&& !isSameStationOrShip(ship, other)) {
				float shipToOtherDistance = getDistance(ship.getLocation(), other.getLocation());
				if (shipToOtherDistance < referenceDistance) {
					float nearToOtherDistance = getDistance(near.getLocation(), other.getLocation());
					if (nearToOtherDistance < referenceDistance) {
						count++;
					}
				}
			}
		}

		return count;
	}

	public static int getNumberOfAlliesInLineOfFire(ShipAPI ship, ShipAPI target, float maxRange, boolean ignoreWingLeaders) {
		List<ShipAPI> ships = getEngine().getShips();
		float targetDistance = getDistance(ship.getLocation(), target.getLocation());
		float heading = getAngleFast(ship.getLocation(), target.getLocation());
		int count = 0;

		for (ShipAPI other : ships) {
			if ((!other.isFighter() || (other.getWing() == null || other.getWing().isLeader(other)) && !ignoreWingLeaders)
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& other != ship
				&& other != target
				&& ship.getOwner() == other.getOwner()
				&& !isSameStationOrShip(ship, other)) {
				float angleToOther = getAngleFast(ship.getLocation(), other.getLocation());
				float angleDiff = getAngleDiff(heading, angleToOther);
				float distance = getDistance(ship.getLocation(), other.getLocation());
				float clearance = target.getCollisionRadius() + other.getCollisionRadius();
				if (angleDiff < 30.0F && distance > targetDistance + clearance && distance < maxRange) {
					count++;
				}
			}
		}

		return count;
	}

	public static int getNumberOfEnemiesInLineOfFire(ShipAPI ship, ShipAPI target, float maxRange) {
		List<ShipAPI> ships = getEngine().getShips();
		float targetDistance = getDistance(ship.getLocation(), target.getLocation());
		float heading = getAngleFast(ship.getLocation(), target.getLocation());
		int count = 0;

		for (ShipAPI other : ships) {
			if ((!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& other != ship
				&& other != target
				&& other.isTargetable()
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100) {
				float angleToOther = getAngleFast(ship.getLocation(), other.getLocation());
				float angleDiff = getAngleDiff(heading, angleToOther);
				float distance = getDistance(ship.getLocation(), other.getLocation());
				float clearance = target.getCollisionRadius() + other.getCollisionRadius();
				if (angleDiff < 60.0F && distance > targetDistance + clearance && distance < maxRange) {
					count++;
				}
			}
		}

		return count;
	}

	public static int getNumberOfAlliesInRange(ShipAPI ship, float maxRange) {
		List<ShipAPI> ships = getEngine().getShips();
		int count = 0;

		for (ShipAPI other : ships) {
			if (!isNotValidTarget(ship, other) && ship.getOwner() == other.getOwner() && !isSameStationOrShip(ship, other)) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (distance < maxRange) {
					count++;
				}
			}
		}

		return count;
	}

	public static int getNumberOfAlliesInRange(ShipAPI ship, float maxRange, boolean ignoreFighters) {
		List<ShipAPI> ships = getEngine().getShips();
		int count = 0;

		for (ShipAPI other : ships) {
			if (!isNotValidTarget(ship, other)
				&& (!ignoreFighters || !other.isFighter())
				&& !isEscortTargetOf(other, ship)
				&& ship.getOwner() == other.getOwner()
				&& !isSameStationOrShip(ship, other)) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (distance < maxRange) {
					count++;
				}
			}
		}

		return count;
	}

	// ===== AIUtils: escort relationships =====

	public static boolean isEscortTargetOf(ShipAPI ship, ShipAPI target) {
		return isEscortTargetOf(ship, target, 0);
	}

	// approximation: native FighterAI exposes no current maneuver through the
	// public API. Its MANEUVER_TARGET flag can also name an attack target, so
	// the recursive escort relationship can include a non-escort fighter.
	public static boolean isEscortTargetOf(ShipAPI ship, ShipAPI target, int depth) {
		if (depth < 2) {
			ShipAPI escortTarget = getEscortTarget(ship);
			if (escortTarget != null) {
				if (escortTarget != target && !isEscortTargetOf(escortTarget, target, depth + 1)) {
					return false;
				}

				return true;
			}
		}

		return false;
	}

	// approximation: the engine reads the escort task from the war-room task
	// manager's direct orders, which the mod API does not expose; the ship's
	// current assignment stands in for it
	public static boolean hasEscortOrder(ShipAPI ship) {
		CombatTaskManagerAPI taskManager = getEngine().getFleetManager(ship.getOriginalOwner()).getTaskManager(ship.isAlly());
		if (taskManager == null) {
			return false;
		}

		CombatFleetManagerAPI fleetManager = getEngine().getFleetManager(ship.getOriginalOwner());
		if (fleetManager == null) {
			return false;
		}

		DeployedFleetMemberAPI member = fleetManager.getDeployedFleetMember(ship);
		if (member == null) {
			return false;
		}

		AssignmentInfo assignment = taskManager.getAssignmentFor(ship);
		return assignment == null
			? false
			: assignment.getType() == CombatAssignmentType.LIGHT_ESCORT
				|| assignment.getType() == CombatAssignmentType.MEDIUM_ESCORT
				|| assignment.getType() == CombatAssignmentType.HEAVY_ESCORT;
	}

	// approximation: the engine compares the war-room tasks of both ships,
	// which the mod API does not expose; both ships' current assignments are
	// compared by type and target instead
	public static boolean isShipTaskTarget(ShipAPI ship, ShipAPI target) {
		CombatTaskManagerAPI taskManager = getEngine().getFleetManager(ship.getOriginalOwner()).getTaskManager(ship.isAlly());
		if (taskManager == null) {
			return true;
		}

		CombatFleetManagerAPI fleetManager = getEngine().getFleetManager(ship.getOriginalOwner());
		if (fleetManager == null) {
			return true;
		}

		DeployedFleetMemberAPI member = fleetManager.getDeployedFleetMember(ship);
		if (member == null) {
			return true;
		}

		DeployedFleetMemberAPI targetMember = fleetManager.getDeployedFleetMember(target);
		if (targetMember == null) {
			return true;
		}

		AssignmentInfo assignment = taskManager.getAssignmentInfoForTarget(member);
		if (assignment == null) {
			return true;
		}

		AssignmentInfo targetAssignment = taskManager.getAssignmentInfoForTarget(targetMember);
		return targetAssignment == null ? true : assignment == targetAssignment;
	}

	public static boolean isEscorting(ShipAPI ship) {
		BattleNext_BasicShipAI ai = BattleNext_BasicShipAI.getActiveInstance(ship);
		return ai != null && ai.getCurrentManeuver() instanceof BattleNext_EscortTargetManeuverV3;
	}

	public static boolean isShipBeingEscorted(ShipAPI ship) {
		CombatTaskManagerAPI taskManager = getEngine().getFleetManager(ship.getOriginalOwner()).getTaskManager(ship.isAlly());
		if (taskManager == null) {
			return false;
		}

		CombatFleetManagerAPI fleetManager = getEngine().getFleetManager(ship.getOriginalOwner());
		if (fleetManager == null) {
			return false;
		}

		DeployedFleetMemberAPI member = fleetManager.getDeployedFleetMember(ship);
		if (member == null) {
			return false;
		}

		// approximation: the engine checks the war-room escort task's member
		// list; the API exposes only the assignment's assigned members
		AssignmentInfo assignment = taskManager.getAssignmentInfoForTarget(member);
		return assignment != null && !assignment.getAssignedMembers().isEmpty()
			? assignment.getType() == CombatAssignmentType.LIGHT_ESCORT || assignment.getType() == CombatAssignmentType.MEDIUM_ESCORT || assignment.getType() == CombatAssignmentType.HEAVY_ESCORT
			: false;
	}

	public static boolean isEscortManeuverActive(ShipAPI ship) {
		return getEscortTarget(ship) != null;
	}

	public static ShipAPI getEscortTarget(ShipAPI ship) {
		BattleNext_BasicShipAI portedAI = BattleNext_BasicShipAI.getActiveInstance(ship);
		if (portedAI != null) {
			if (portedAI.getCurrentManeuver() instanceof BattleNext_EscortTargetManeuverV3) {
				return ((BattleNext_EscortTargetManeuverV3) portedAI.getCurrentManeuver()).getTargetShip();
			}
			return null;
		}
		// approximation: native FighterAI current maneuver is hidden by the
		// public API; MANEUVER_TARGET also carries completed attack targets.
		// The flag supplies a candidate without proving an escort maneuver.
		ShipwideAIFlags flags = ship.getAIFlags();
		if (flags == null) {
			return null;
		}

		Object custom = flags.getCustom(AIFlags.MANEUVER_TARGET);
		return custom instanceof ShipAPI ? (ShipAPI) custom : null;
	}

	public static ShipwideAIFlags getAIFlags(ShipAPI ship) {
		// the BasicShipAI, FighterAI and DroneAI branches of the vanilla
		// lookup all collapse into the generic ShipAIPlugin branch under the
		// mod API
		if (ship.getAI() instanceof ShipAIPlugin) {
			return ((ShipAIPlugin) ship.getAI()).getAIFlags();
		}

		String key = "nonAI_SAIF_" + ship.getId();
		Object existing = getEngine().getCustomData().get(key);
		if (existing instanceof ShipwideAIFlags) {
			return (ShipwideAIFlags) existing;
		}

		ShipwideAIFlags flags = new ShipwideAIFlags();
		getEngine().getCustomData().put(key, flags);
		return flags;
	}

	private static boolean isNotValidTarget(ShipAPI ship, ShipAPI other) {
		if (other.isFighter() && other.getWing() != null && !other.getWing().isLeader(other)) {
			return true;
		} else if (other.isHulk()) {
			return true;
		} else if (other.isShuttlePod()) {
			return true;
		} else if (other.isDrone()) {
			return true;
		} else {
			return other.isStationModule() || other.getStationSlot() != null ? true : other == ship;
		}
	}

	public static ShipAPI findAutoEscortTarget(ShipAPI ship) {
		return findAutoEscortTarget(ship, true, null);
	}

	public static ShipAPI findAutoEscortTarget(ShipAPI ship, boolean requireTrouble, AssignmentInfo assignment) {
		boolean hasAllies = getNumberOfAllies(ship, null) > 0;
		if (hasAllies) {
			return null;
		}

		List<ShipAPI> ships = getEngine().getShips();
		boolean carrierWantsEscort = ship.getVariant().isCarrier()
			&& !ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.NO_AUTO_ESCORT)
			&& ship.getLaunchBaysCopy().size() >= ship.getMutableStats().getNumFighterBays().getModifiedValue() * 0.66F;
		CombatTaskManagerAPI taskManager = null;
		if (assignment != null) {
			taskManager = getEngine().getFleetManager(ship.getOriginalOwner()).getTaskManager(ship.isAlly());
		}

		ShipAPI result = null;
		float closest = Float.MAX_VALUE;

		for (ShipAPI other : ships) {
			if (!isNotValidTarget(ship, other) && !other.isFighter() && ship.getOwner() == other.getOwner() && !isPhaseShip(other)) {
					if (other.getHullSize().ordinal() >= ship.getHullSize().ordinal()) {
					float maxRange = 1000.0F;
					if (carrierWantsEscort) {
						maxRange = 2000.0F;
					}

					if (!requireTrouble) {
						maxRange = 100000.0F;
					}

					float distance = getDistance(ship.getLocation(), other.getLocation());
					if (!(distance > maxRange + other.getCollisionRadius() + ship.getCollisionRadius())) {
						boolean inTrouble = !requireTrouble || isShipInTrouble(other, ship);
						if (!isEscortTargetOf(other, ship)) {
							if (carrierWantsEscort) {
								if (!isShipTaskTarget(ship, other)) {
									continue;
								}

								int otherAllies = getNumberOfAllies(other, ship);
													if (otherAllies >= 1 || other.isNonCombat(false) || other.getHullSize().ordinal() + 1 < ship.getHullSize().ordinal()) {
									continue;
								}

								inTrouble = true;
								boolean otherCarrierWantsEscort = other.getVariant().isCarrier()
									&& other.getLaunchBaysCopy().size() >= other.getMutableStats().getNumFighterBays().getModifiedValue() * 0.66F;
								if (otherCarrierWantsEscort && isEscorting(other)) {
									continue;
								}
							}

							if (assignment != null) {
								AssignmentInfo otherAssignment = taskManager.getAssignmentFor(other);
								if (otherAssignment == null || otherAssignment.getType() != assignment.getType() || otherAssignment.getTarget() != assignment.getTarget()) {
									continue;
								}
							}

							if (requireTrouble && !carrierWantsEscort) {
								ShipAPI nearestAlly = getNearestAllyNotInTrouble(other);
								if (nearestAlly != ship) {
									continue;
								}
							}

							if (inTrouble && distance < closest) {
								result = other;
								closest = distance;
							}
						}
					}
				}
			}
		}

		return result;
	}

	public static boolean isShipInTrouble(ShipAPI ship, ShipAPI attacker) {
		if (getEngine().getTotalElapsedTime(false) < 20.0F) {
			return false;
		}

		if (attacker != null && isShipOrderedToAttack(attacker)) {
			return false;
		}

		boolean inTrouble = ship.getFluxTracker().isOverloadedOrVenting()
			&& (ship.getFluxTracker().getOverloadTimeRemaining() > 0.0F || ship.getFluxTracker().getTimeToVent() > 0.0F);
		inTrouble |= ship.getEngineController().isDisabled();
		inTrouble |= ship.getFluxLevel() > 0.9F;
		// approximation: ShipAPI has no getHullLevel(); hull fraction is
		// derived from hitpoints
		inTrouble |= ship.getHitpoints() / ship.getMaxHitpoints() < 0.25F;
		inTrouble |= ship.isCapital();
		ShipwideAIFlags flags = getAIFlags(ship);
		if (flags != null) {
			inTrouble |= flags.hasFlag(AIFlags.NEEDS_HELP);
		}

		return inTrouble;
	}

	public static ShipAPI getNearestAllyNotInTrouble(ShipAPI ship) {
		List<ShipAPI> ships = getEngine().getShips();
		ShipAPI result = null;
		float closest = Float.MAX_VALUE;

		for (ShipAPI other : ships) {
			if (!other.isFighter()
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& other != ship
				&& !other.isStationModule()
				&& other.getStationSlot() == null
				&& ship.getOwner() == other.getOwner()
					&& other.getHullSize().ordinal() <= ship.getHullSize().ordinal()
				&& !isSameStationOrShip(ship, other)) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (!(distance > 1000.0F + other.getCollisionRadius() + ship.getCollisionRadius())) {
					boolean inTrouble = isShipInTrouble(other, null);
					if (!inTrouble && distance < closest) {
						result = other;
						closest = distance;
					}
				}
			}
		}

		return result;
	}

	public static List<ShipAPI> getAlliedShips(ShipAPI ship, ShipAPI excluded) {
		ArrayList<ShipAPI> allies = new ArrayList<>();

		for (ShipAPI other : getEngine().getShips()) {
			if (!other.isFighter()
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& other != ship
				&& other != excluded
				&& ship.getOwner() == other.getOwner()
				&& !isSameStationOrShip(ship, other)
				&& isEscortTargetOf(other, ship)) {
				allies.add(other);
			}
		}

		return allies;
	}

	public static int getNumberOfAllies(ShipAPI ship, ShipAPI excluded) {
		int count = 0;

		for (ShipAPI other : getEngine().getShips()) {
			if (!other.isFighter()
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& other != ship
				&& other != excluded
				&& ship.getOwner() == other.getOwner()
				&& !isSameStationOrShip(ship, other)
				&& isEscortTargetOf(other, ship)) {
				count++;
			}
		}

		return count;
	}

	// ===== AIUtils: enemy counting =====

	public static int getNumberOfEnemiesInArcRange(ShipAPI ship, Vector2f from, float arc, float maxRange, HullSize minSize, CombatEntityAPI excluded) {
		List<ShipAPI> ships = getEngine().getShips();
		float heading = getAngleFast(ship.getLocation(), from);
		int count = 0;

		for (ShipAPI other : ships) {
			boolean bomberThreat = other.isFighter()
				&& other.getWing() != null
				&& other.getWing().getSpec().isBomber()
				&& isInArc(other.getFacing(), 150.0F, other.getLocation(), ship.getLocation());
			boolean stationModule = other.isStationModule() && other.getParentStation() != null && other.getParentStation().isStation();
			if ((bomberThreat || other.getHullSize().ordinal() >= minSize.ordinal() || stationModule)
				&& (bomberThreat || !other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& other != ship
				&& other.getOwner() != 100
				&& ship.getOwner() != other.getOwner()
				&& other != excluded) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (!(distance > maxRange + ship.getCollisionRadius() + other.getCollisionRadius()) && isInArc(heading, arc, ship.getLocation(), other.getLocation())) {
					count++;
				}
			}
		}

		return count;
	}

	public static int getNumberOfEnemiesInArcRange(ShipAPI ship, Vector2f from, float arc, float maxRange) {
		List<ShipAPI> ships = getEngine().getShips();
		float heading = getAngleFast(ship.getLocation(), from);
		int count = 0;

		for (ShipAPI other : ships) {
			if (!other.isHulk() && !other.isShuttlePod() && other != ship && other.getOwner() != 100 && ship.getOwner() != other.getOwner() && other.isFighter()) {
				boolean bomberThreat = other.isFighter()
					&& other.getWing() != null
					&& other.getWing().getSpec().isBomber()
					&& isInArc(other.getFacing(), 210.0F, other.getLocation(), ship.getLocation());
				if (bomberThreat) {
					// approximation: FighterWingAPI has no fighterNeedsRefit();
					// fighters are assumed to not need refit
					bomberThreat &= true;
				}

				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (!(distance > maxRange + ship.getCollisionRadius() + other.getCollisionRadius())) {
					if (other.isFighter()) {
						for (WeaponAPI weapon : other.getUsableWeapons()) {
							if (weapon.hasAIHint(AIHints.STRIKE) || weapon.hasAIHint(AIHints.DANGEROUS)) {
								bomberThreat = true;
								break;
							}
						}
					}

					if (bomberThreat && isInArc(heading, arc, ship.getLocation(), other.getLocation())) {
						count++;
					}
				}
			}
		}

		return count;
	}

	public interface TargetValidityFilter {
		boolean isShipValid(ShipAPI queryShip, Vector2f at, ShipAPI candidate);

		default boolean allowsSameStationOrShip() {
			return true;
		}
	}

	// The engine applies the station restriction only to its OO0O$Oo filter
	// subtype, using its f_722efca7_3 flag. Generic filters pass unchanged.
	public static int countEnemiesInRange(ShipAPI ship, Vector2f from, float facing, float arc, float maxRange, TargetValidityFilter filter) {
		List<ShipAPI> ships = getEngine().getShips();
		int count = 0;

		for (ShipAPI other : ships) {
			if (filter.isShipValid(ship, from, other)
				&& (!isSameStationOrShip(ship, other) || filter.allowsSameStationOrShip())) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (!(distance > maxRange) && (arc >= 360.0F || isInArc(facing, arc, ship.getLocation(), other.getLocation()))) {
					count++;
				}
			}
		}

		return count;
	}

	// intentionally kept faithful: the vanilla filter compares the querying
	// ship against itself and therefore never passes
	public static boolean isEnemyInRange(final ShipAPI ship, Vector2f point) {
		float heading = getAngleFast(ship.getLocation(), point);
		float distance = getDistance(ship.getLocation(), point);
		int count = countEnemiesInRange(ship, ship.getLocation(), heading, 30.0F, distance, (queryShip, at, candidate) -> queryShip != ship && queryShip.getOwner() == ship.getOwner());
		return count > 0;
	}

	public static HullSize getSmallerHullSize(HullSize size) {
		// switch labels are the ordinal plus one, per the decompiled lookup
		// table; case 2 is FIGHTER up to case 6 being CAPITAL_SHIP
		switch (getHullSizeScores()[size.ordinal()]) {
			case 1:
				return HullSize.DEFAULT;
			case 2:
				return HullSize.FIGHTER;
			case 3:
				return HullSize.FIGHTER;
			case 4:
				return HullSize.FRIGATE;
			case 5:
				return HullSize.DESTROYER;
			case 6:
				return HullSize.CRUISER;
			default:
				return size;
		}
	}

	public static HullSize getLargerHullSize(HullSize size) {
		// switch labels are the ordinal plus one, per the decompiled lookup
		// table; case 2 is FRIGATE up to case 6 being CAPITAL_SHIP
		switch (getHullSizeScores()[size.ordinal()]) {
			case 1:
				return HullSize.DEFAULT;
			case 2:
				return HullSize.FRIGATE;
			case 3:
				return HullSize.DESTROYER;
			case 4:
				return HullSize.CRUISER;
			case 5:
				return HullSize.CAPITAL_SHIP;
			case 6:
				return HullSize.CAPITAL_SHIP;
			default:
				return size;
		}
	}

	public static int getNumberOfEnemiesInArcRange(ShipAPI ship, float facing, float arc, float maxRange) {
		List<ShipAPI> ships = getEngine().getShips();
		int count = 0;

		for (ShipAPI other : ships) {
			boolean bomber = other.getWing() != null && other.getWing().getSpec().isBomber();
			if ((!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isDrone()
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
					&& (other.getHullSize().ordinal() >= ship.getHullSize().ordinal() - 2 || bomber || other.isDestroyer() || other.getParentStation() != null)
				&& other != ship
				&& other.getOwner() != 100
				&& ship.getOwner() != other.getOwner()) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (!(distance > maxRange) && isInArc(facing, arc, ship.getLocation(), other.getLocation())) {
					count++;
				}
			}
		}

		return count;
	}

	public static int getNumberOfEnemiesInArcRangeExcludingFighters(ShipAPI ship, float facing, float arc, float maxRange) {
		return getNumberOfEnemiesInArcRange(ship, facing, arc, maxRange, true, true);
	}

	public static int getNumberOfEnemiesInArcRange(ShipAPI ship, float facing, float arc, float maxRange, boolean includeFighters, boolean minSizeFilter) {
		List<ShipAPI> ships = getEngine().getShips();
		int count = 0;

		for (ShipAPI other : ships) {
			if (!other.isFighter() || includeFighters) {
				boolean bomber = other.getWing() != null && other.getWing().getSpec().isBomber();
				if ((!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
					&& !other.isDrone()
					&& !other.isHulk()
					&& !other.isShuttlePod()
							&& (!minSizeFilter || other.getHullSize().ordinal() >= ship.getHullSize().ordinal() - 2 || bomber || other.getParentStation() != null)
					&& other != ship
					&& other.getOwner() != 100
					&& ship.getOwner() == other.getOwner()
					&& !isSameStationOrShip(ship, other)) {
					float distance = getDistance(ship.getLocation(), other.getLocation());
					if (!(distance > maxRange) && isInArc(facing, arc, ship.getLocation(), other.getLocation())) {
						count++;
					}
				}
			}
		}

		return count;
	}

	// ===== AIUtils: missiles =====

	public static int getNumberOfHostileMissilesInArcRange(ShipAPI ship, float facing, float arc, float maxRange) {
		List<MissileAPI> missiles = getEngine().getMissiles();
		int count = 0;

		for (MissileAPI missile : missiles) {
			if (missile.getOwner() != ship.getOriginalOwner()
				|| missile.getCollisionClass() != CollisionClass.MISSILE_NO_FF
					&& missile.getCollisionClass() != CollisionClass.PROJECTILE_NO_FF
					&& missile.getCollisionClass() != CollisionClass.RAY_FIGHTER
					&& missile.getCollisionClass() != CollisionClass.PROJECTILE_FIGHTER) {
				float damageMult = 1.0F;
				if (missile.getDamage().getType() == DamageType.FRAGMENTATION) {
					damageMult = 0.33F;
				}

				float damage = missile.getDamage().getDamage() * damageMult;
				if (!(damage < 200.0F)) {
					float distance = getDistance(ship.getLocation(), missile.getLocation());
					if (!(distance > maxRange)) {
						if (missile.isGuided()) {
							GuidedMissileAI missileAI = (GuidedMissileAI) missile.getMissileAI();
							if (missileAI != null && missileAI.getTarget() != ship) {
								continue;
							}
						}

						float closingSpeed = getClosingSpeed(missile, ship);
						if (!(closingSpeed < 0.0F) && (arc >= 360.0F || isInArc(facing, arc, ship.getLocation(), missile.getLocation()))) {
							count++;
						}
					}
				}
			}
		}

		return count;
	}

	public static int getNumberOfIncomingMissiles(ShipAPI ship, float seconds) {
		List<MissileAPI> missiles = getEngine().getMissiles();
		float armorRating = ship.getArmorGrid().getArmorRating();
		int count = 0;

		for (MissileAPI missile : missiles) {
			if (missile.getOwner() != ship.getOriginalOwner()
				|| missile.getCollisionClass() != CollisionClass.MISSILE_NO_FF
					&& missile.getCollisionClass() != CollisionClass.PROJECTILE_NO_FF
					&& missile.getCollisionClass() != CollisionClass.RAY_FIGHTER
					&& missile.getCollisionClass() != CollisionClass.PROJECTILE_FIGHTER) {
				float distance = getDistance(ship.getLocation(), missile.getLocation());
				if (!(distance > 2500.0F)) {
					MissileSpecAPI spec = missile.getSpec();
					float maxSpeed = spec.getHullSpec().getEngineSpec().getMaxSpeed();
					MutableShipStatsAPI sourceStats = null;
					float damage = spec.getDamage().getDamage();
					ShipAPI missileSource = missile.getSource();
					if (missileSource != null) {
						sourceStats = missileSource.getMutableStats();
					}

					if (sourceStats != null) {
						maxSpeed = sourceStats.getMissileMaxSpeedBonus().computeEffective(maxSpeed);
						damage *= sourceStats.getMissileWeaponDamageMult().getModifiedValue();
					}

					damage = missile.getDamageAmount();
					if (!(damage < armorRating * 0.05F) && (!(damage * 20.0F < ship.getHitpoints()) || !(damage < armorRating * 0.25F))) {
						float closingSpeed = getClosingSpeed(missile, ship);
						closingSpeed += Math.max(0.0F, maxSpeed - missile.getVelocity().length());
						float timeToImpact = 1000.0F;
						if (closingSpeed > 1.0F) {
							timeToImpact = distance / closingSpeed;
						}

						timeToImpact += missile.getEtaModifier();
						boolean incoming = timeToImpact <= seconds;
						if (incoming) {
							count++;
						}
					}
				}
			}
		}

		return count;
	}

	public static int getNumberOfIncomingMissilesInArcRange(ShipAPI ship, float facing, float arc, float maxRange) {
		List<MissileAPI> missiles = getEngine().getMissiles();
		int count = 0;

		for (MissileAPI missile : missiles) {
			if (missile.getOwner() != ship.getOriginalOwner()
				|| missile.getCollisionClass() != CollisionClass.MISSILE_NO_FF
					&& missile.getCollisionClass() != CollisionClass.PROJECTILE_NO_FF
					&& missile.getCollisionClass() != CollisionClass.RAY_FIGHTER
					&& missile.getCollisionClass() != CollisionClass.PROJECTILE_FIGHTER) {
				float damageMult = 1.0F;
				if (missile.getDamage().getType() == DamageType.FRAGMENTATION) {
					damageMult = 0.33F;
				}

				float damage = missile.getDamage().getDamage() * damageMult;
				if (!(damage < 200.0F)) {
					float distance = getDistance(ship.getLocation(), missile.getLocation());
					if (!(distance > maxRange)) {
						// approximation: MissileAPI does not expose engine
						// locations; missiles always have engine locations in
						// vanilla, so the no-engine bypass never applies
						boolean hasNoEngines = false;
						if (arc >= 360.0F || isInArc(facing, arc, ship.getLocation(), missile.getLocation())) {
							float closingSpeed = getClosingSpeed(ship.getLocation(), missile.getLocation(), new Vector2f(), missile.getVelocity());
							if (!(closingSpeed < 0.0F) || hasNoEngines) {
								count++;
							}
						}
					}
				}
			}
		}

		return count;
	}

	// ===== AIUtils: threat =====

	public static float getTotalThreatInRange(ShipAPI ship, float facing, float arc, float maxRange, boolean includeStations) {
		return getTotalThreatInRangeExcludingShip(ship, ship.getLocation(), facing, arc, maxRange, null, includeStations);
	}

	public static float getTotalThreatInRangeExcludingShip(ShipAPI ship, Vector2f from, float facing, float arc, float maxRange, ShipAPI excluded, boolean includeStations) {
		List<ShipAPI> ships = getEngine().getShips();
		float totalThreat = 0.0F;

		for (ShipAPI other : ships) {
			if ((!includeStations || !other.isFighter() && !other.isStationModule())
				&& (!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isHulk()
				&& !other.isDrone()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()
				&& other != ship
				&& other.getOwner() != 100
				&& ship.getOwner() != other.getOwner()
				&& !other.controlsLocked()
				&& !other.isBeingIgnored()
				&& other != excluded) {
				float distance = getDistance(from, other.getLocation());
				if (!(distance > maxRange) && (arc >= 360.0F || isInArc(facing, arc, from, other.getLocation()))) {
					totalThreat += getThreat(other);
				}
			}
		}

		return totalThreat;
	}

	public static float getTotalThreatInRange(ShipAPI ship, float facing, float arc, float maxRange, boolean excludeFighters, boolean excludeStations) {
		return getTotalThreatInRange(ship, ship.getLocation(), facing, arc, maxRange, excludeFighters, excludeStations);
	}

	public static float getTotalThreatInRange(ShipAPI ship, Vector2f from, float facing, float arc, float maxRange, boolean excludeFighters, boolean excludeStations) {
		List<ShipAPI> ships = getEngine().getShips();
		float totalThreat = 0.0F;

		for (ShipAPI other : ships) {
			if ((!excludeFighters || !other.isFighter())
				&& (!excludeStations || !other.isStationModule())
				&& (!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& other != ship
				&& other.getOwner() != 100
				&& ship.getOwner() == other.getOwner()
				&& !other.controlsLocked()
				&& !isSameStationOrShip(ship, other)) {
				float distance = getDistance(from, other.getLocation());
				if (!(distance > maxRange) && (arc >= 360.0F || isInArc(facing, arc, from, other.getLocation()))) {
					totalThreat = (int) (totalThreat + getThreat(other));
				}
			}
		}

		return totalThreat;
	}

	public static float getThreat(ShipAPI ship) {
		return getThreat(ship, true);
	}

	public static float getThreat(ShipAPI ship, boolean reduceForNonCombat) {
		// approximation: the vanilla check tests for the engine DroneAI type,
		// which is not exposed; any drone ship matches
		if (ship.isDrone()) {
			return 0.1F;
		}

		boolean nonCombat = ship.isNonCombat(false);
		float threat = 0.0F;
		// switch labels are the ordinal plus one; case 2 is FIGHTER up to
		// case 6 being CAPITAL_SHIP
		switch (getHullSizeScores()[ship.getHullSize().ordinal()]) {
			case 2:
				threat++;
				break;
			case 3:
				threat++;
				break;
			case 4:
				threat += 2.0F;
				break;
			case 5:
				threat += 4.0F;
				break;
			case 6:
				threat += 8.0F;
		}

		if (nonCombat && reduceForNonCombat) {
			threat *= 0.25F;
		}

		if (ship.isDrone()) {
			threat *= 0.1F;
		}

		return threat;
	}

	public static float getAsteroidThreat(ShipAPI ship, Vector2f from, float facing, float arc, float maxRange) {
		List<CombatEntityAPI> asteroids = getEngine().getAsteroids();
		float threat = 0.0F;

		for (CombatEntityAPI asteroid : asteroids) {
			// approximation: the vanilla check skips ring asteroids, but the
			// ring flag is not exposed on the API; all asteroids are counted
			float distance = getDistance(from, asteroid.getLocation());
			if (!(distance > maxRange) && (arc >= 360.0F || isInArc(facing, arc, from, asteroid.getLocation()))) {
				threat = (int) (threat + asteroid.getMass());
			}
		}

		return threat;
	}

	// ===== AIUtils: best maneuver target =====

	/**
	 * Port of engine enum OO0O$oo, the side preference for the maneuver
	 * target search. PREFER_RIGHT (engine f_6a00a25e_3) skips candidates
	 * whose direction to the centroid is clockwise; PREFER_LEFT (engine
	 * f_6a00a25e) skips candidates whose direction to the centroid is
	 * counterclockwise; ANY (engine f_6a00a25e_2) applies no filter and is
	 * the vanilla default.
	 */
	public enum ManeuverSidePreference {
		PREFER_RIGHT,
		PREFER_LEFT,
		ANY
	}

	private static class ManeuverTargetScanState {
		private List<ShipAPI> candidates = new ArrayList<>();
		private Vector2f centroid = new Vector2f();
		private float totalSize = 0.0F;
		private Object maneuverTargetFlag = null;
		private ShipAPI interceptTarget = null;
		// intentionally kept dead: the vanilla usability flag is written but
		// never read
		private boolean interceptTargetUsable = false;
	}

	public static BattleNext_BestTargetEval getBestManeuverTarget(ShipAPI ship) {
		return getBestManeuverTarget(ship, ManeuverSidePreference.ANY, false);
	}

	public static BattleNext_BestTargetEval getBestManeuverTarget(ShipAPI ship, ManeuverSidePreference sidePreference, boolean expandedRange) {
		return getBestManeuverTarget(ship, sidePreference, expandedRange, null);
	}

	public static BattleNext_BestTargetEval getBestManeuverTarget(ShipAPI ship, ManeuverSidePreference sidePreference, boolean expandedRange, ShipAPI forcedTarget) {
		ManeuverTargetScanState state = collectManeuverTargetCandidates(ship, expandedRange, forcedTarget);
		return selectManeuverTarget(ship, state, sidePreference, forcedTarget);
	}

	// first phase of the vanilla getBestManeuverTarget: builds the candidate
	// list, the size-weighted centroid of all candidates and the intercept
	// task target
	private static ManeuverTargetScanState collectManeuverTargetCandidates(ShipAPI ship, boolean expandedRange, ShipAPI forcedTarget) {
		ManeuverTargetScanState state = new ManeuverTargetScanState();
		state.maneuverTargetFlag = ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
		List<ShipAPI> ships = getEngine().getShips();
		BattleNext_Orderable orderable = getOrderable(ship);
		if (orderable != null) {
			BattleNext_DirectOrders orders = orderable.getOrders();
			boolean interceptTask = orders != null && orders.getTaskType() == CombatAssignmentType.INTERCEPT;
			if (interceptTask && orders.getTaskTarget() != null) {
				if (orders.getTaskTarget() instanceof DeployedFleetMemberAPI taskMember) {
					// the engine getAsDFM().getShipIfShip() returns null for wings
					state.interceptTarget = taskMember.isFighterWing() ? null : taskMember.getShip();
				}

				if (state.interceptTarget != null) {
					float distance = Misc.getDistance(ship.getLocation(), state.interceptTarget.getLocation());
					distance -= ship.getCollisionRadius() + state.interceptTarget.getCollisionRadius();
					if (distance < 2950.0F) {
						state.interceptTargetUsable = true;
					}
				}
			}
		}

		for (ShipAPI other : ships) {
			if (other.getOwner() != ship.getOwner()
				&& (!other.isFighter() || other.getWing() == null || other.getWing().isLeader(other))
				&& (!other.isFighter() || !other.isRetreating())
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& !other.isBeingIgnored()) {
				if (other.isDrone()) {
					// intentionally kept dead: the vanilla drone-mothership
					// proximity check has an empty body
					Object mothership = other.getAIFlags().getCustom(AIFlags.DRONE_MOTHERSHIP);
					if (mothership instanceof ShipAPI) {
						float mothershipDistance = getDistance(other.getLocation(), ((ShipAPI) mothership).getLocation());
						if (mothershipDistance < ((ShipAPI) mothership).getCollisionRadius() + 300.0F) {
						}
					}
				} else {
					if (other.isFighter() && other.getWing() != null) {
						ShipAPI sourceShip = other.getWing().getSourceShip();
						if (sourceShip != null) {
							float sourceDistance = getDistance(other.getLocation(), sourceShip.getLocation());
							if (sourceDistance < sourceShip.getCollisionRadius() + 700.0F && sourceShip.isPullBackFighters()) {
								continue;
							}
						}

						float distanceToShip = getDistance(ship.getLocation(), other.getLocation()) - ship.getCollisionRadius();
						if (ship.isFrigate() && distanceToShip < 750.0F || ship.isDestroyer() && distanceToShip < 1250.0F) {
							continue;
						}
					}

							if ((forcedTarget != null
						|| expandedRange
						|| other.getHullSize().ordinal() >= ship.getHullSize().ordinal() - 2
						|| other.isDestroyer()
						|| other.getParentStation() != null)
						&& other.isTargetable()
						&& (forcedTarget == null || isSameStationOrShip(other, forcedTarget))
						&& (forcedTarget != null || !other.isStationModule() || !other.getHullSpec().hasTag("module_hull_bar_only"))
						&& ship.getOwner() != other.getOwner()
						&& other.getOwner() != 100) {
						float distance = getDistance(ship.getLocation(), other.getLocation());
						if (distance < MANEUVER_TARGET_SEARCH_RANGE || expandedRange && distance < 4400.0F || forcedTarget != null) {
							state.candidates.add(other);
							float size = Misc.getSizeNum(other.getHullSize());
							state.totalSize += size;
							state.centroid.x = state.centroid.x + other.getLocation().x * size;
							state.centroid.y = state.centroid.y + other.getLocation().y * size;
						}
					}
				}
			}
		}

		return state;
	}

	// second phase of the vanilla getBestManeuverTarget: picks the target
	// from the candidates and assembles the eval
	private static BattleNext_BestTargetEval selectManeuverTarget(ShipAPI ship, ManeuverTargetScanState state, ManeuverSidePreference sidePreference, ShipAPI forcedTarget) {
		if (state.totalSize == 0.0F) {
			if (state.interceptTarget != null) {
				return buildManeuverTargetEval(state.interceptTarget, state.interceptTarget, false, new Vector2f(state.interceptTarget.getLocation()));
			} else {
				return null;
			}
		} else if (state.candidates.size() != 1) {
			state.centroid.x /= state.totalSize;
			state.centroid.y /= state.totalSize;
			Vector2f shipLocation = ship.getLocation();
			float headingToCentroid = getAngleFast(shipLocation, state.centroid);
			boolean badTarget = false;
			float bestDistance = Float.MAX_VALUE;
			float bestAngleDiff = Float.MAX_VALUE;
			ShipAPI closest = null;
			ShipAPI chosen = null;

			for (ShipAPI candidate : state.candidates) {
				Vector2f candidateToCentroid = subtract(state.centroid, candidate.getLocation());
				normalise(candidateToCentroid);
				boolean aligned = true;
				float minDot = Float.MAX_VALUE;

				for (ShipAPI other : state.candidates) {
					if (other != candidate) {
						Vector2f candidateToOther = subtract(other.getLocation(), candidate.getLocation());
						normalise(candidateToOther);
						float dot = Vector2f.dot(candidateToCentroid, candidateToOther);
						boolean sameDirection = dot > 0.0F;
						if (!sameDirection) {
							aligned = false;
							break;
						}

						if (dot < minDot) {
							minDot = dot;
						}
					}
				}

				float distance = getDistance(ship.getLocation(), candidate.getLocation());
				if (distance < bestDistance && !candidate.isDrone()) {
					closest = candidate;
					bestDistance = distance;
					if (minDot < 0.5F) {
						badTarget = true;
					} else {
						badTarget = false;
					}
				}

				if (aligned) {
					if (sidePreference != ManeuverSidePreference.ANY) {
						float headingToCandidate = getAngleFast(shipLocation, candidate.getLocation());
						float side = getTurnDirection(headingToCandidate, headingToCentroid);
						if (sidePreference == ManeuverSidePreference.PREFER_RIGHT && side < 0.0F || sidePreference == ManeuverSidePreference.PREFER_LEFT && side > 0.0F) {
							continue;
						}
					}

					// the vanilla comparison carries two always-zero offsets
					float centroidToCandidateAngle = Misc.getAngleInDegrees(state.centroid, candidate.getLocation());
					float candidateToShipAngle = Misc.getAngleInDegrees(candidate.getLocation(), ship.getLocation());
					float angleDiff = getAngleDiff(centroidToCandidateAngle, candidateToShipAngle);
					float offset1 = 0.0F;
					float offset2 = 0.0F;
					if (angleDiff + offset1 < bestAngleDiff + offset2 || chosen != null && chosen.isDrone() && !candidate.isDrone()) {
						bestAngleDiff = angleDiff;
						chosen = candidate;
					}
				}
			}

			if (chosen != null) {
				BattleNext_BestTargetEval eval = new BattleNext_BestTargetEval();
				eval.setTarget(chosen);
				eval.setAlternateTarget(closest);
				eval.setBadTarget(badTarget);
				eval.setAimPoint(state.centroid);
				float headingToChosen = getAngleFast(shipLocation, chosen.getLocation());
				float side = getTurnDirection(headingToChosen, headingToCentroid);
				if (eval.getTarget() != null && eval.getTarget().getParentStation() != null && eval.isBadTarget()) {
					float stationAngularVelocity = eval.getTarget().getParentStation().getAngularVelocity();
					if (Math.abs(stationAngularVelocity) >= 1.0F) {
						side = -Math.signum(stationAngularVelocity);
					}
				}

				// vanilla stores the side indicator in the eval's float field
				eval.setRange(side);
				boolean keepTarget = state.maneuverTargetFlag != null && eval.getTarget() == state.maneuverTargetFlag;
				keepTarget |= eval.getAlternateTarget() == null || eval.getAlternateTarget().isDrone();
				if (!ship.getAIFlags().hasFlag(AIFlags.AVOIDING_BORDER) && !keepTarget && eval.getAlternateTarget() != eval.getTarget() && eval.getAlternateTarget() != null) {
					boolean sameStation = eval.getTarget().getParentStation() != null && eval.getAlternateTarget().getParentStation() == eval.getTarget().getParentStation();
					eval.setTarget(eval.getAlternateTarget());
					eval.setBadTarget(true);
					if (sameStation) {
						eval.setBadTarget(false);
					}

				eval.setRange(getAngleToEntity(ship, eval.getTarget(), eval.isBadTarget()));
				}

				if (isShipOrderedToAttack(ship)) {
				eval.setBadTarget(false);
				}

				if (state.interceptTarget != null) {
					eval.setTarget(state.interceptTarget);
					eval.setBadTarget(false);
				}

				// intentionally kept dead: vanilla checks the expanded-range
				// flag here and does nothing with it

				return eval;
			} else if (state.interceptTarget != null) {
				return buildManeuverTargetEval(state.interceptTarget, state.interceptTarget, false, new Vector2f(state.interceptTarget.getLocation()));
			} else {
				return null;
			}
		} else if (forcedTarget != null) {
			ShipAPI single = state.candidates.get(0);
			return buildManeuverTargetEval(single, single, false, state.centroid);
		} else if (state.interceptTarget != null) {
			return buildManeuverTargetEval(state.interceptTarget, state.interceptTarget, false, new Vector2f(state.interceptTarget.getLocation()));
		} else {
			return null;
		}
	}

	private static BattleNext_BestTargetEval buildManeuverTargetEval(ShipAPI target, ShipAPI alternateTarget, boolean badTarget, Vector2f aimPoint) {
		BattleNext_BestTargetEval eval = new BattleNext_BestTargetEval();
		eval.setTarget(target);
		eval.setAlternateTarget(alternateTarget);
		eval.setBadTarget(badTarget);
		eval.setAimPoint(aimPoint);
		return eval;
	}

	public static float getAngleToEntity(ShipAPI ship, CombatEntityAPI entity) {
		return getAngleToEntity(ship, entity, false);
	}

	public static float getAngleToEntity(ShipAPI ship, CombatEntityAPI entity, boolean useParentStation) {
		Vector2f centerOfEnemies = getCenterOfEnemies(ship);
		if (centerOfEnemies == null) {
			return 0.0F;
		}

		Vector2f shipLocation = ship.getLocation();
		float angleToEntity = getAngleFast(shipLocation, entity.getLocation());
		float angleToCenter = getAngleFast(shipLocation, centerOfEnemies);
		float side = getTurnDirection(angleToEntity, angleToCenter);
		if (entity != null && entity instanceof ShipAPI && ((ShipAPI) entity).getParentStation() != null && useParentStation) {
			float stationAngularVelocity = ((ShipAPI) entity).getParentStation().getAngularVelocity();
			if (Math.abs(stationAngularVelocity) >= 1.0F) {
				side = -Math.signum(stationAngularVelocity);
			}
		}

		return side;
	}

	public static Vector2f getCenterOfEnemies(ShipAPI ship) {
		List<ShipAPI> ships = getEngine().getShips();
		ArrayList<ShipAPI> enemies = new ArrayList<>();
		Vector2f center = new Vector2f();
		float totalSize = 0.0F;

		for (ShipAPI other : ships) {
			if (!other.isFighter()
				&& !other.isHulk()
				&& !other.isShuttlePod()
				&& !other.isDrone()
				&& !other.controlsLocked()
				&& !other.isBeingIgnored()
					&& (other.getHullSize().ordinal() >= ship.getHullSize().ordinal() - 2 || other.isDestroyer() || other.getParentStation() != null)
				&& ship.getOwner() != other.getOwner()
				&& other.getOwner() != 100) {
				float distance = getDistance(ship.getLocation(), other.getLocation());
				if (distance < MANEUVER_TARGET_SEARCH_RANGE) {
					enemies.add(other);
					float size = Misc.getSizeNum(other.getHullSize());
					totalSize += size;
					center.x = center.x + other.getLocation().x * size;
					center.y = center.y + other.getLocation().y * size;
				}
			}
		}

		if (totalSize == 0.0F) {
			return null;
		}

		if (enemies.size() == 1) {
			return null;
		}

		center.x /= totalSize;
		center.y /= totalSize;
		return center;
	}

	// ===== AIUtils: aim point and intercept solver =====

	public static Vector2f getAimPoint(ShipAPI ship, CombatEntityAPI target, float closingSpeedBonus, boolean returnNullIfUnreachable, float aimErrorOffset1, float aimErrorOffset2) {
		AIM_ERROR_OFFSETS[0] = aimErrorOffset1;
		AIM_ERROR_OFFSETS[1] = aimErrorOffset2;
		Vector2f aimPoint = getAimPoint(ship, target, closingSpeedBonus, returnNullIfUnreachable);
		AIM_ERROR_OFFSETS[0] = 0.0F;
		AIM_ERROR_OFFSETS[1] = 0.0F;
		return aimPoint;
	}

	public static Vector2f getAimPoint(ShipAPI ship, CombatEntityAPI target, float closingSpeedBonus, boolean returnNullIfUnreachable, float aimErrorOffset1) {
		AIM_ERROR_OFFSETS[0] = aimErrorOffset1;
		Vector2f aimPoint = getAimPoint(ship, target, closingSpeedBonus, returnNullIfUnreachable);
		AIM_ERROR_OFFSETS[0] = 0.0F;
		return aimPoint;
	}

	public static Vector2f getAimPoint(ShipAPI ship, CombatEntityAPI target, float closingSpeedBonus, boolean returnNullIfUnreachable) {
		float aimAccuracy = 1.5F;
		if (ship != null) {
			aimAccuracy = ship.getAimAccuracy();
		}

		return getInterceptPoint(ship, aimAccuracy, target, closingSpeedBonus, returnNullIfUnreachable);
	}

	public static Vector2f getInterceptPoint(CombatEntityAPI shooter, float aimAccuracy, CombatEntityAPI target, float closingSpeedBonus) {
		return getInterceptPoint(shooter, aimAccuracy, target.getLocation(), target.getVelocity(), target.getCollisionRadius(), closingSpeedBonus, false);
	}

	public static Vector2f getInterceptPoint(CombatEntityAPI shooter, float aimAccuracy, CombatEntityAPI target, float closingSpeedBonus, boolean ignoreShooterVelocity) {
		return getInterceptPoint(shooter, aimAccuracy, target.getLocation(), target.getVelocity(), target.getCollisionRadius(), closingSpeedBonus, ignoreShooterVelocity);
	}

	public static Vector2f getInterceptPoint(CombatEntityAPI shooter, float aimAccuracy, Vector2f targetLocation, Vector2f targetVelocity, float targetRadius, float closingSpeedBonus, boolean ignoreShooterVelocity) {
		if (AIM_ERROR_OFFSETS[0] > 0.0F) {
			float divisor = aimErrorDivisor;
			if (divisor > 0.0F) {
				float accuracyLoss = AIM_ERROR_OFFSETS[0] / divisor;
				float adjustedAccuracy = aimAccuracy - accuracyLoss;
				if (adjustedAccuracy < 1.0F) {
					adjustedAccuracy = 1.0F;
				}

				aimAccuracy = adjustedAccuracy;
			}
		}

		aimAccuracy -= AIM_ERROR_OFFSETS[1];
		if (aimAccuracy < 1.0F) {
			aimAccuracy = 1.0F;
		}

		// intentionally kept faithful: the vanilla solver overwrites this
		// parameter with false before it is read
		ignoreShooterVelocity = false;
		float distance = getDistance(shooter.getLocation(), targetLocation);
		distance -= (shooter.getCollisionRadius() + targetRadius) * 0.75F;
		if (distance < 0.0F) {
			distance = 0.0F;
		}

		Vector2f targetDirection = Vector2f.sub(targetLocation, shooter.getLocation(), new Vector2f());
		normalise(targetDirection);
		Vector2f effectiveShooterVelocity = shooter.getVelocity();
		if (ignoreShooterVelocity) {
			effectiveShooterVelocity = new Vector2f();
		}

		if (shooter instanceof MissileAPI shooterMissile) {
			float alignment = Vector2f.dot(targetVelocity, targetDirection);
			float maxEffectiveSpeed = Math.max(closingSpeedBonus, shooterMissile.getMaxSpeed());
			maxEffectiveSpeed = Math.max(100.0F, maxEffectiveSpeed);
			float timeToImpact = distance / Math.max(100.0F, maxEffectiveSpeed - alignment);
			float accelTime = shooterMissile.getMaxSpeed() / Math.max(10.0F, shooterMissile.getAcceleration());
			accelTime *= 2.0F;
			Vector2f missileVelocity = new Vector2f(shooter.getVelocity());
			if (timeToImpact < 0.1F) {
				timeToImpact = 0.1F;
			}

			missileVelocity.scale(accelTime / (timeToImpact + accelTime));
			effectiveShooterVelocity = new Vector2f(missileVelocity);
		}

		Vector2f relativeVelocity = Vector2f.sub(targetVelocity, effectiveShooterVelocity, new Vector2f());
		float closingSpeed = getClosingSpeed(shooter.getLocation(), targetLocation, effectiveShooterVelocity, targetVelocity);
		float weightedClosing = closingSpeed + closingSpeedBonus;
		if (shooter instanceof MissileAPI) {
			closingSpeed = 0.0F;
		}

		weightedClosing *= aimAccuracy;
		closingSpeed *= aimAccuracy;
		if (weightedClosing <= 10.0F) {
			weightedClosing = 20.0F;
		}

		if (isComputingAimPoint() && weightedClosing < 100.0F) {
			return null;
		}

		Vector2f interceptPoint = new Vector2f(targetLocation);
		float timeToImpact = distance / weightedClosing;
		timeToImpact = (distance - closingSpeed * timeToImpact) / weightedClosing;
		if (timeToImpact < 0.0F) {
			timeToImpact = 0.0F;
		}

		if (timeToImpact > 10.0F) {
			timeToImpact = 10.0F;
		}

		interceptPoint.x = interceptPoint.x + relativeVelocity.x * timeToImpact;
		interceptPoint.y = interceptPoint.y + relativeVelocity.y * timeToImpact;
		return interceptPoint;
	}

	// ===== AIUtils: remaining ship checks =====

	public static boolean isShipWorthAttacking(ShipAPI ship, ShipAPI target, BattleNext_ManeuverAPI maneuver) {
		boolean orderedToAttack = isShipOrderedToAttack(ship);
		// pure ordinal size comparison, ported as-is
		return target.getHullSize().ordinal() - ship.getHullSize().ordinal() >= 2
			&& maneuver.getThreatEvaluator().getThreat(target) > 0.0F
			&& !orderedToAttack
			&& !target.isNonCombat(false);
	}

	public static boolean isShipAboutToBeHit(ShipAPI ship, CombatEntityAPI incoming) {
		float distance = getDistance(ship.getLocation(), incoming.getLocation());
		float clearance = ship.getCollisionRadius() + incoming.getCollisionRadius();
		if (incoming instanceof ShipAPI station && station.getParentStation() != null) {
			float stationDistance = getDistance(ship.getLocation(), station.getParentStation().getLocation());
			if (stationDistance < distance) {
				float moduleToStationDistance = Misc.getDistance(incoming.getLocation(), station.getParentStation().getLocation());
				clearance += Math.max(station.getParentStation().getCollisionRadius(), moduleToStationDistance);
			}
		}

		float closingSpeed = getClosingSpeed(ship, incoming);
		return closingSpeed > 250.0F && (distance - clearance) / closingSpeed < 2.0F ? false : distance > Math.min(clearance * 1.5F, clearance + 150.0F);
	}

	public static boolean hasEnoughFirepower(ShipAPI ship, ShipAPI target) {
		float firepower = 0.0F;

		for (int i = 0; i < 7; i++) {
			WeaponGroupAPI group = getWeaponGroup(ship, i);
			if (group != null) {
				for (WeaponAPI weapon : group.getWeaponsCopy()) {
					if ((!weapon.usesAmmo() || weapon.getAmmo() > 0) && (weapon.hasAIHint(AIHints.STRIKE) || weapon.hasAIHint(AIHints.DANGEROUS))) {
						firepower += getWeaponSizeValue(weapon);
					}
				}
			}
		}

		float required = 0.0F;
		// switch labels are the ordinal plus one; case 2 is FIGHTER up to
		// case 6 being CAPITAL_SHIP
		switch (getHullSizeScores()[target.getHullSize().ordinal()]) {
			case 2:
				required = 1.0F;
				break;
			case 3:
				required = 1.0F;
				break;
			case 4:
				required = 2.0F;
				break;
			case 5:
				required = 6.0F;
				break;
			case 6:
				required = 7.0F;
		}

		return firepower >= required;
	}

	public static float getWeaponSizeValue(WeaponAPI weapon) {
		float value = 0.0F;
		// switch labels are the ordinal plus one; case 1 is SMALL up to
		// case 3 being LARGE
		switch (getWeaponSizeScores()[weapon.getSize().ordinal()]) {
			case 1:
				value = 1.0F;
				break;
			case 2:
				value = 3.0F;
				break;
			case 3:
				value = 7.0F;
		}

		return value;
	}

	public static float getDistanceToEntity(CombatEntityAPI from, CombatEntityAPI to) {
		float distance = getDistanceToEntitySurface(from.getLocation(), to, false);
		float radius = to.getCollisionRadius();
		if (distance > radius) {
			distance = radius;
		}

		return radius <= 0.0F ? 1.0F : distance / radius;
	}

	public static float getDistanceToEntity(Vector2f from, CombatEntityAPI to) {
		float distance = getDistanceToEntitySurface(from, to, false);
		float radius = to.getCollisionRadius();
		if (distance > radius) {
			distance = radius;
		}

		return radius <= 0.0F ? 1.0F : distance / radius;
	}

	public static ShipAPI getManeuverTarget(ShipAPI ship) {
		if (ship == null) {
			return null;
		}

		if (ship.getAI() instanceof ShipAIPlugin ai) {
			if (ai.getAIFlags() != null
				&& ai.getAIFlags().hasFlag(AIFlags.MANEUVER_TARGET)
				&& ai.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET) instanceof ShipAPI) {
				return (ShipAPI) ai.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
			}
		}

		return ship.getShipTarget();
	}

	// Port of FighterAI.getCarrierTarget() using the AI flags exposed on ShipAPI.
	// Ship.isFriendOf() treats owner 100 as neutral on either side.
	public static ShipAPI getCarrierTarget(ShipAPI carrier) {
		ShipwideAIFlags flags = carrier.getAIFlags();
		if (flags != null && flags.hasFlag(AIFlags.CARRIER_FIGHTER_TARGET)) {
			Object flagged = flags.getCustom(AIFlags.CARRIER_FIGHTER_TARGET);
			if (flagged instanceof ShipAPI target) {
				boolean friend = target.getOwner() != 100 && carrier.getOwner() != 100 && target.getOwner() == carrier.getOwner();
				if (!target.isHulk() && (!friend || !target.isFighter())) return target;
			}
		}

		if (flags != null && flags.getCustom(AIFlags.MANEUVER_TARGET) instanceof ShipAPI target) {
			if (!target.isHulk() && target.getOwner() != 100 && carrier.getOwner() != 100 && target.getOwner() != carrier.getOwner()) return target;
		}

		ShipAPI target = carrier.getShipTarget();
		if (target == null || target.isHulk()) return null;
		boolean friend = target.getOwner() != 100 && carrier.getOwner() != 100 && target.getOwner() == carrier.getOwner();
		return friend && target.isFighter() ? null : target;
	}

	public static float getDistanceToEntitySurface(Vector2f point, CombatEntityAPI entity, boolean includeShield) {
		if (!(entity instanceof ShipAPI ship)) {
			return entity.getCollisionRadius();
		} else {
			if (includeShield) {
				ShieldAPI shield = ship.getShield();
				if (shield != null && shield.isOn() && isInArc(shield.getFacing(), shield.getActiveArc(), shield.getLocation(), point)) {
					float angleToPoint = getAngleFast(shield.getLocation(), point);
					Vector2f leftPoint = getUnitVectorAtAngle(angleToPoint - 10.0F);
					Vector2f rightPoint = getUnitVectorAtAngle(angleToPoint + 10.0F);
					Vector2f centerPoint = getUnitVectorAtAngle(angleToPoint);
					leftPoint.scale(shield.getRadius());
					rightPoint.scale(shield.getRadius());
					centerPoint.scale(shield.getRadius());
					Vector2f.add(leftPoint, shield.getLocation(), leftPoint);
					Vector2f.add(rightPoint, shield.getLocation(), rightPoint);
					Vector2f.add(centerPoint, shield.getLocation(), centerPoint);
					Vector2f shipLocation = ship.getLocation();
					if (segmentIntersection(leftPoint, rightPoint, shipLocation, point) != null) {
						return getDistance(ship.getLocation(), centerPoint) + 10.0F;
					}
				}
			}

			// approximation: ShipAPI exposes only its base sprite; the
			// ellipse fit below mirrors the vanilla sprite-based computation
			float spriteWidth = ship.getSpriteAPI().getWidth();
			float spriteHeight = ship.getSpriteAPI().getHeight();
			float spriteCenterX = ship.getSpriteAPI().getCenterX();
			float spriteCenterY = ship.getSpriteAPI().getCenterY();
			float centerYOffset = 0.0F;
			if (spriteCenterY != -1.0F) {
				centerYOffset = spriteHeight / 2.0F - spriteCenterY;
			}

			float centerXOffset = 0.0F;
			if (spriteCenterX != -1.0F) {
				centerXOffset = spriteWidth / 2.0F - spriteCenterX;
			}

			float widthHeightRatio = spriteWidth / spriteHeight;
			float shipFacing = ship.getFacing();
			float angleToPoint = getAngleFast(ship.getLocation(), point);
			float angleDiff = getAngleDiff(shipFacing, angleToPoint);
			boolean flipped = false;
			boolean turnRight = false;
			if (angleDiff > 90.0F) {
				angleDiff = 180.0F - angleDiff;
				flipped = true;
			}

			if (getTurnDirection(shipFacing, angleToPoint) > 0.0F) {
				turnRight = true;
			}

			float semiMinor = ship.getCollisionRadius();
			semiMinor = spriteHeight / 2.0F;
			semiMinor = Math.min(semiMinor, ship.getCollisionRadius());
			float semiMinorAdjusted = semiMinor;
			float semiMajorAdjusted = semiMinor * widthHeightRatio;
			if (flipped) {
				semiMinorAdjusted -= centerYOffset;
			} else {
				semiMinorAdjusted += centerYOffset;
			}

			if (turnRight) {
				semiMajorAdjusted -= centerXOffset;
			} else {
				semiMajorAdjusted += centerXOffset;
			}

			float minAxis = Math.min(semiMinorAdjusted, semiMajorAdjusted);
			float maxAxis = Math.max(semiMinorAdjusted, semiMajorAdjusted);
			float surfaceAngle = normalizeAngle(angleToPoint - shipFacing - 90.0F);
			float surfaceDistance;
			if (semiMinorAdjusted > semiMajorAdjusted) {
				surfaceDistance = minAxis
					* maxAxis
					/ (float) Math.sqrt(Math.pow(maxAxis * (float) Math.cos(Math.toRadians(surfaceAngle)), 2.0) + Math.pow(minAxis * (float) Math.sin(Math.toRadians(surfaceAngle)), 2.0));
			} else {
				surfaceDistance = minAxis
					* maxAxis
					/ (float) Math.sqrt(Math.pow(maxAxis * (float) Math.sin(Math.toRadians(surfaceAngle)), 2.0) + Math.pow(minAxis * (float) Math.cos(Math.toRadians(surfaceAngle)), 2.0));
			}

			return surfaceDistance + 10.0F;
		}
	}

	// approximation: the seen-enemy set of the engine fleet manager is not
	// exposed; fog of war visibility is the only visibility signal available
	public static boolean hasSeenEnemy(int owner, ShipAPI ship) {
		return false;
	}

	public static boolean isEntityVisible(int owner, CombatEntityAPI entity) {
		if (entity == null) {
			return false;
		} else {
			FogOfWarAPI fogOfWar = getEngine().getFogOfWar(owner);
			if (fogOfWar != null && fogOfWar.isVisible(entity)) {
				return true;
			} else {
				return !(entity instanceof ShipAPI) ? false : hasSeenEnemy(owner, (ShipAPI) entity);
			}
		}
	}

	public static boolean hasInterceptTask(ShipAPI ship) {
		BattleNext_Orderable orderable = getOrderable(ship);
		if (orderable == null) return false;
		BattleNext_DirectOrders orders = orderable.getOrders();
		return orders != null && orders.getTaskType() == CombatAssignmentType.INTERCEPT;
	}

	public static boolean hasStrikeTask(ShipAPI ship) {
		BattleNext_Orderable orderable = getOrderable(ship);
		if (orderable == null) return false;
		BattleNext_DirectOrders orders = orderable.getOrders();
		return orders != null && orders.getTaskType() == CombatAssignmentType.STRIKE;
	}

	public static float getCRMult(ShipAPI ship) {
		boolean cautious = "cautious".equals(getPersonality(ship));
		boolean timid = "timid".equals(getPersonality(ship));
		float noCRLossSeconds = ship.getHullSpec().getNoCRLossSeconds();
		noCRLossSeconds = Math.min(90.0F, noCRLossSeconds);
		float peakCRDuration = ship.getMutableStats().getPeakCRDuration().computeEffective(ship.getHullSpec().getNoCRLossTime());
		float remainingPeakTime = Math.max(0.0F, peakCRDuration - ship.getTimeDeployedForCRReduction());
		float multiplier = (1.0F - Math.min(1.0F, remainingPeakTime / noCRLossSeconds)) * 0.4F;
		if (multiplier > 0.0F) {
			float currentCR = ship.getCurrentCR();
			multiplier += Math.max(0.0F, 0.6F - currentCR) * 0.4F;
		}

		if (timid || cautious) {
			multiplier *= 0.5F;
		}

		return multiplier;
	}

	public static boolean isShipOrderedToAttack(ShipAPI ship) {
		CombatTaskManagerAPI taskManager = getEngine().getFleetManager(ship.getOriginalOwner()).getTaskManager(ship.isAlly());
		boolean intercept = hasInterceptTask(ship);
		boolean strike = hasStrikeTask(ship) && !isCarrier(ship);
		boolean reckless = "reckless".equals(getPersonality(ship));
		boolean timid = "timid".equals(getPersonality(ship));
		// intentionally kept faithful: vanilla resets the timid check to false
		timid = false;
		boolean nonCombat = ship.isNonCombat(true);
		return (taskManager.isFullAssault() || intercept || strike || reckless) && !timid && !nonCombat;
	}

	public static float getMaxSpeed(ShipAPI ship) {
		float speed = getCruiseSpeed(ship);
		if (ship.getPhaseCloak() != null && ship.getHardFluxLevel() < 0.2F) {
			speed *= 2.0F;
		}

		if (ship.getSystem() != null) {
			// approximation: system spec AI hints are not exposed; average
			// speed increase treated as 0
			speed += 0.0F;
		}

		return speed;
	}

	// Port of ShipEngineController.getCruiseSpeed(): the modified speed includes
	// the active zero-flux boost, while an inactive boost is added for planning.
	public static float getCruiseSpeed(ShipAPI ship) {
		float speed = ship.getMutableStats().getMaxSpeed().getModifiedValue();
		if (!ship.isEngineBoostActive()) {
			speed += ship.getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
		}
		return speed;
	}

	// AIUtils.getShipScore() starts with cruise speed and removes only a boost
	// that is currently inactive. An active boost remains in modified max speed.
	static float getPlanningSpeedWithoutZeroFluxBoost(ShipAPI ship) {
		float speed = getCruiseSpeed(ship);
		if (!ship.isEngineBoostActive()) {
			speed -= ship.getMutableStats().getZeroFluxSpeedBoost().getModifiedValue();
		}
		return speed;
	}

	public static float getPhaseCloakMaxSpeed(ShipAPI ship) {
		float maxTimeMult = PhaseCloakStats.getMaxTimeMult(ship.getMutableStats());
		float speed = ship.getMaxSpeedWithoutBoost();
		if (PhaseCloakStats.FLUX_LEVEL_AFFECTS_SPEED) {
			// the vanilla solver reads the same mutable stat key
			float fluxLevelForMinSpeed = ship.getMutableStats()
				.getDynamic()
				.getMod("phase_cloak_flux_level_for_min_speed_mod")
				.computeEffective(PhaseCloakStats.BASE_FLUX_LEVEL_FOR_MIN_SPEED);
			if (fluxLevelForMinSpeed > 0.0F) {
				float hardFluxFraction = ship.getHardFluxLevel() / fluxLevelForMinSpeed;
				if (hardFluxFraction > 1.0F) {
					hardFluxFraction = 1.0F;
				}

				float speedMult = PhaseCloakStats.MIN_SPEED_MULT + (1.0F - PhaseCloakStats.MIN_SPEED_MULT) * (1.0F - hardFluxFraction);
				float minSpeedMult = PhaseCloakStats.MIN_SPEED_MULT;
				float averageMult = (minSpeedMult + speedMult) / 2.0F / speedMult;
				speed *= averageMult;
			} else {
				speed *= PhaseCloakStats.MIN_SPEED_MULT;
			}
		}

		return speed * maxTimeMult;
	}

	// ===== flux helpers shared by the decision modules =====

	// port of engine Ship.getFluxAvailable() (Ship.java:3923)
	public static float getFluxAvailable(ShipAPI ship) {
		return ship.getFluxTracker().getMaxFlux() - ship.getFluxTracker().getCurrFlux();
	}

	// port of engine FluxTracker.getDissipationRate() (FluxTracker.java:528/:106)
	public static float getDissipationRate(ShipAPI ship) {
		return ship.getMutableStats().getFluxDissipation().getModifiedValue();
	}

	// ===== turnToFace overloads for the engine ShipCommandReceiver family =====
	// In the engine Ship implements ShipCommandReceiver, so ships hit these
	// receivers; in the port they are plain ShipAPI overloads.

	// port of AIUtils.turnToFace(receiver, target, angleOffset): the target heading
	// is the bearing to the target minus the offset
	public static boolean turnToFace(ShipAPI ship, Vector2f target, float angleOffset) {
		float bearing = getAngleFast(ship.getLocation(), target) - angleOffset;
		return turnToFace(ship, angleOffset, bearing, 0.0F);
	}

	// port of AIUtils.turnToFace(receiver, target, angleOffset, angularVelocityOffset)
	public static boolean turnToFace(ShipAPI ship, Vector2f target, float angleOffset, float angularVelocityOffset) {
		float bearing = getAngleFast(ship.getLocation(), target) - angleOffset;
		return turnToFace(ship, angleOffset, bearing, angularVelocityOffset);
	}

	// port of AIUtils.turnToFace(receiver, heading, facing)
	public static boolean turnToFace(ShipAPI ship, float unusedHeading, float facing) {
		return turnToFace(ship, unusedHeading, facing, 0.0F);
	}

	// port of AIUtils.turnToFace(receiver, heading, facing, angularVelocityOffset).
	// The first parameter is never read by the vanilla body and is kept for
	// signature parity; the return value is meaningless in vanilla (always false).
	public static boolean turnToFace(ShipAPI ship, float unusedHeading, float facing, float angularVelocityOffset) {
		if (facing == Float.MAX_VALUE) {
			return false;
		}

		float angularVelocity = ship.getAngularVelocity() - angularVelocityOffset;
		float absAngularVelocity = Math.abs(angularVelocity);
		float turnDeceleration = ship.getEngineController().getTurnDeceleration();
		float decelerationTime = absAngularVelocity / turnDeceleration;
		float stopArc = absAngularVelocity * decelerationTime - 0.5F * turnDeceleration * decelerationTime * decelerationTime;
		float projectedFacing = ship.getFacing() + Math.signum(angularVelocity) * stopArc;
		float projectedDiff = getAngleDiff(projectedFacing, facing);
		float currentDiff = getAngleDiff(ship.getFacing(), facing);
		if (projectedDiff > 1.0F) {
			float turnDirection = getTurnDirection(ship.getFacing(), facing);
			if (Math.signum(angularVelocity) == Math.signum(turnDirection) && stopArc > currentDiff) {
				turnDirection = -turnDirection;
			}

			if (turnDirection < 0.0F) {
				ship.giveCommand(ShipCommand.TURN_RIGHT, null, -1);
			} else {
				if (!(turnDirection >= 0.0F)) {
					return false;
				}

				ship.giveCommand(ShipCommand.TURN_LEFT, null, -1);
			}
		}

		return false;
	}
}
