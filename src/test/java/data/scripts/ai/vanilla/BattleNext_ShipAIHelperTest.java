package data.scripts.ai.vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.ai.vanilla.maneuvers.BattleNext_RetreatPositionEvaluator;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.util.vector.Vector2f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_ShipAIHelperTest {
	@Test
	void carrierTargetKeepsVanillaFlagPriority() {
		ShipAPI carrierFlagTarget = targetShip(1, false, false);
		ShipAPI maneuverTarget = targetShip(1, false, false);
		ShipAPI directTarget = targetShip(1, false, false);
		ShipwideAIFlags flags = new ShipwideAIFlags();
		flags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 10f, carrierFlagTarget);
		flags.setFlag(AIFlags.MANEUVER_TARGET, 10f, maneuverTarget);
		ShipAPI carrier = carrierShip(0, flags, directTarget);
		assertTrue(BattleNext_ShipAIHelper.getCarrierTarget(carrier) == carrierFlagTarget);
		flags.unsetFlag(AIFlags.CARRIER_FIGHTER_TARGET);
		assertTrue(BattleNext_ShipAIHelper.getCarrierTarget(carrier) == maneuverTarget);
		flags.unsetFlag(AIFlags.MANEUVER_TARGET);
		assertTrue(BattleNext_ShipAIHelper.getCarrierTarget(carrier) == directTarget);
	}

	@Test
	void carrierTargetTreatsNeutralFighterAsNeitherFriendNorEnemy() {
		ShipAPI neutralFighter = targetShip(100, true, false);
		ShipwideAIFlags flags = new ShipwideAIFlags();
		flags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 10f, neutralFighter);
		ShipAPI carrier = carrierShip(100, flags, neutralFighter);
		assertTrue(BattleNext_ShipAIHelper.getCarrierTarget(carrier) == neutralFighter);
		flags.unsetFlag(AIFlags.CARRIER_FIGHTER_TARGET);
		assertTrue(BattleNext_ShipAIHelper.getCarrierTarget(carrier) == neutralFighter);
	}

	@Test
	void carrierTargetSkipsFriendlyFighterFlag() {
		ShipAPI friendlyFighter = targetShip(0, true, false);
		ShipAPI enemy = targetShip(1, false, false);
		ShipwideAIFlags flags = new ShipwideAIFlags();
		flags.setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 10f, friendlyFighter);
		flags.setFlag(AIFlags.MANEUVER_TARGET, 10f, enemy);
		assertTrue(BattleNext_ShipAIHelper.getCarrierTarget(carrierShip(0, flags, null)) == enemy);
	}

	private ShipAPI carrierShip(int owner, ShipwideAIFlags flags, ShipAPI target) {
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "getOwner" -> owner;
				case "getAIFlags" -> flags;
				case "getShipTarget" -> target;
				default -> null;
			});
	}

	private ShipAPI targetShip(int owner, boolean fighter, boolean hulk) {
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "getOwner" -> owner;
				case "isFighter" -> fighter;
				case "isHulk" -> hulk;
				default -> null;
			});
	}

	@Test
	void dpsStrengthMultiplierUsesCurrentGameSetting() {
		SettingsAPI previous = Global.getSettings();
		SettingsAPI settings = (SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
				if ("getFloat".equals(method.getName()) && "dpsToHitStrengthMult".equals(args[0])) return 0.7f;
				return null;
			});
		try {
			Global.setSettings(settings);
			assertEquals(0.7f, BattleNext_ShipAIHelper.getDpsToHitStrengthMult());
		} finally {
			Global.setSettings(previous);
		}
	}

	@Test
	void stationFilterPolicyMatchesVanillaSubtypeRule() {
		BattleNext_ShipAIHelper.TargetValidityFilter generic = (query, at, candidate) -> true;
		assertTrue(generic.allowsSameStationOrShip());
		assertFalse(new BattleNext_RetreatPositionEvaluator(HullSize.FRIGATE, true, true, false).allowsSameStationOrShip());
		assertTrue(new BattleNext_RetreatPositionEvaluator(HullSize.FRIGATE, true, true, true).allowsSameStationOrShip());
	}

	@Test
	void isEscortingRequiresActivePortedBasicAi() {
		ShipAPI target = (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ShipAPI.class}, (instance, method, args) -> null);
		ShipwideAIFlags flags = new ShipwideAIFlags();
		flags.setFlag(AIFlags.MANEUVER_TARGET, 1f, target);
		ShipAPI fighter = (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "getCustomData" -> Map.of();
				case "getAIFlags" -> flags;
				default -> null;
			});
		assertTrue(BattleNext_ShipAIHelper.getEscortTarget(fighter) == target);
		assertFalse(BattleNext_ShipAIHelper.isEscorting(fighter));
	}

	@Test
	void blockingShipCacheRefreshesOnNextCombatFrame() {
		ShipAPI hulk = (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "isFighter" -> false;
				case "isHulk" -> true;
				case "getStationSlot" -> null;
				default -> null;
			});
		Map<String, Object> data = new HashMap<>();
		AtomicReference<List<ShipAPI>> ships = new AtomicReference<>(List.of(hulk));
		float[] time = {1f};
		CombatEngineAPI engine = (CombatEngineAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{CombatEngineAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "getCustomData" -> data;
				case "getShips" -> ships.get();
				case "getTotalElapsedTime" -> time[0];
				default -> null;
			});
		assertEquals(1, BattleNext_ShipAIHelper.getBlockingShips(engine).size());
		ships.set(List.of());
		assertEquals(1, BattleNext_ShipAIHelper.getBlockingShips(engine).size());
		time[0] = 2f;
		assertEquals(0, BattleNext_ShipAIHelper.getBlockingShips(engine).size());
	}

	@Test
	void collisionEntityListsCountSharedMissileOnce() {
		CombatEntityAPI missile = (CombatEntityAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{CombatEntityAPI.class}, (instance, method, args) -> null);
		List<CombatEntityAPI> output = new ArrayList<>();
		Set<CombatEntityAPI> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		BattleNext_ShipAIHelper.addUniqueCollisionEntities(output, seen, List.of(missile));
		BattleNext_ShipAIHelper.addUniqueCollisionEntities(output, seen, List.of(missile));
		assertEquals(1, output.size());
		assertTrue(output.get(0) == missile);
	}

	@Test
	void aiGridQueryUsesSquareHalfExtent() {
		Vector2f center = new Vector2f(0f, 0f);
		assertTrue(BattleNext_ShipAIHelper.isWithinAiGridQuery(center, new Vector2f(100f, 100f), 100f));
		assertFalse(BattleNext_ShipAIHelper.isWithinAiGridQuery(center, new Vector2f(101f, 0f), 100f));
		assertFalse(BattleNext_ShipAIHelper.isWithinAiGridQuery(center, new Vector2f(0f, -101f), 100f));
	}

	@Test
	void shipScoreSpeedUsesCruiseSpeedBeforeInactiveBoostRemoval() {
		ShipAPI inactive = shipWithSpeed(100f, 50f, false);
		ShipAPI active = shipWithSpeed(150f, 50f, true);
		assertEquals(150f, BattleNext_ShipAIHelper.getCruiseSpeed(inactive));
		assertEquals(100f, BattleNext_ShipAIHelper.getPlanningSpeedWithoutZeroFluxBoost(inactive));
		assertEquals(150f, BattleNext_ShipAIHelper.getCruiseSpeed(active));
		assertEquals(150f, BattleNext_ShipAIHelper.getPlanningSpeedWithoutZeroFluxBoost(active));
	}

	private ShipAPI shipWithSpeed(float modifiedMaxSpeed, float boost, boolean active) {
		MutableShipStatsAPI stats = (MutableShipStatsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{MutableShipStatsAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "getMaxSpeed" -> new MutableStat(modifiedMaxSpeed);
				case "getZeroFluxSpeedBoost" -> new MutableStat(boost);
				default -> null;
			});
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "getMutableStats" -> stats;
				case "isEngineBoostActive" -> active;
				default -> null;
			});
	}

	@Test
	void timedCombatStateExpiresAndDoesNotLeakIntoAnotherBattle() {
		CombatEngineAPI first = engineAt(5f);
		CombatEngineAPI second = engineAt(5f);
		BattleNext_ShipAIHelper.setCustomDataWithTimeout(first, "phase", true, 10f);
		assertEquals(true, BattleNext_ShipAIHelper.getCustomDataWithTimeout(first, "phase"));
		assertEquals(null, BattleNext_ShipAIHelper.getCustomDataWithTimeout(second, "phase"));
		CombatEngineAPI elapsed = engineAt(16f, first.getCustomData());
		assertEquals(null, BattleNext_ShipAIHelper.getCustomDataWithTimeout(elapsed, "phase"));
	}

	private CombatEngineAPI engineAt(float time) {
		return engineAt(time, new HashMap<>());
	}

	private CombatEngineAPI engineAt(float time, Map<String, Object> data) {
		return (CombatEngineAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{CombatEngineAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "getCustomData" -> data;
				case "getTotalElapsedTime" -> time;
				default -> null;
			});
	}

	@Test
	void energyUsesRemainingFluxCapacityAtCombatThresholds() {
		assertEquals(1000f, BattleNext_ShipAIHelper.getFluxAvailable(ship(0f)));
		assertEquals(800f, BattleNext_ShipAIHelper.getFluxAvailable(ship(200f)));
		assertEquals(200f, BattleNext_ShipAIHelper.getFluxAvailable(ship(800f)));
		assertEquals(100f, BattleNext_ShipAIHelper.getFluxAvailable(ship(900f)));
	}

	@Test
	void fleetStrengthUsesHullSlotWeights() {
		assertEquals(7f, BattleNext_ShipAIHelper.computeFleetStrength(shipWithSlots(
				List.of(slot(WeaponSize.SMALL), slot(WeaponSize.MEDIUM), slot(WeaponSize.LARGE)))));
	}

	@Test
	void deployedStrengthUsesCurrentHullFractionForShips() {
		assertEquals(50f, BattleNext_ShipAIHelper.getDeployedMemberStrength(deployed(false), shipWithHull(0f)));
		assertEquals(75f, BattleNext_ShipAIHelper.getDeployedMemberStrength(deployed(false), shipWithHull(50f)));
		assertEquals(100f, BattleNext_ShipAIHelper.getDeployedMemberStrength(deployed(false), shipWithHull(100f)));
		assertEquals(100f, BattleNext_ShipAIHelper.getDeployedMemberStrength(deployed(true), null));
	}

	private DeployedFleetMemberAPI deployed(boolean wing) {
		FleetMemberAPI member = (FleetMemberAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{FleetMemberAPI.class}, (instance, method, args) ->
					"getMemberStrength".equals(method.getName()) ? 100f : null);
		return (DeployedFleetMemberAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{DeployedFleetMemberAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getMember" -> member;
					case "isFighterWing" -> wing;
					default -> null;
				});
	}

	private ShipAPI shipWithHull(float hitpoints) {
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getHitpoints" -> hitpoints;
					case "getMaxHitpoints" -> 100f;
					default -> null;
				});
	}

	private ShipAPI shipWithSlots(List<WeaponSlotAPI> slots) {
		ShipHullSpecAPI hull = (ShipHullSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipHullSpecAPI.class}, (instance, method, args) ->
					"getAllWeaponSlotsCopy".equals(method.getName()) ? slots : null);
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipAPI.class}, (instance, method, args) ->
					"getHullSpec".equals(method.getName()) ? hull : null);
	}

	private WeaponSlotAPI slot(WeaponSize size) {
		return (WeaponSlotAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{WeaponSlotAPI.class}, (instance, method, args) ->
					"getSlotSize".equals(method.getName()) ? size : null);
	}

	private ShipAPI ship(float currentFlux) {
		FluxTrackerAPI flux = (FluxTrackerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{FluxTrackerAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getMaxFlux" -> 1000f;
					case "getCurrFlux" -> currentFlux;
					default -> null;
				});
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipAPI.class}, (instance, method, args) ->
					"getFluxTracker".equals(method.getName()) ? flux : null);
	}
}
