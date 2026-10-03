package data.scripts.ai.vanilla.attack;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import data.scripts.ai.vanilla.BattleNext_ThreatData;
import data.scripts.ai.vanilla.BattleNext_ThreatEvalAI;
import data.scripts.ai.vanilla.maneuvers.BattleNext_ManeuverAPI;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

class BattleNext_FighterAutofireManagerTest {
	@Test
	void decisionRunsAfterPointFourToPointSevenCombatSeconds() {
		Harness harness = new Harness();
		harness.weapon(WeaponType.BALLISTIC, 0, false, false);
		BattleNext_FighterAutofireManager manager = harness.manager();
		manager.advance(0.3f, null, null);
		assertTrue(harness.toggles.isEmpty());
		manager.advance(0.4f, null, null);
		assertEquals(List.of("on"), harness.toggles);
	}

	@Test
	void shieldAndAvoidanceKeepPdGroupAutofiring() {
		Harness harness = new Harness();
		harness.evaluation.setPDGroup(true);
		harness.evaluation.setUsesEnergy(true);
		harness.weapon(WeaponType.ENERGY, 0, false, false, AIHints.PD);
		harness.flags.setFlag(AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS);
		harness.manager().advance(1f, null, new Vector2f(1f, 0f));
		assertTrue(harness.autofiring);
		assertEquals(List.of("on"), harness.toggles);
	}

	@Test
	void firingWeaponKeepsFighterGroupAutofiring() {
		Harness harness = new Harness();
		harness.weapon(WeaponType.BALLISTIC, 0, false, true);
		harness.manager().advance(1f, null, null);
		assertTrue(harness.autofiring);
	}

	@Test
	void noFluxFlagTurnsOffEnergyGroup() {
		Harness harness = new Harness();
		harness.autofiring = true;
		harness.evaluation.setUsesEnergy(true);
		harness.flags.setFlag(AIFlags.DO_NOT_USE_FLUX);
		harness.weapon(WeaponType.ENERGY, 0, false, false);
		harness.manager().advance(1f, null, new Vector2f(1f, 0f));
		assertFalse(harness.autofiring);
		assertEquals(List.of("off"), harness.toggles);
	}

	@Test
	void noFluxFlagLeavesNonEnergyGroupOn() {
		Harness harness = new Harness();
		harness.autofiring = true;
		harness.flags.setFlag(AIFlags.DO_NOT_USE_FLUX);
		harness.weapon(WeaponType.BALLISTIC, 0, false, false);
		harness.manager().advance(1f, null, null);
		assertTrue(harness.autofiring);
		assertTrue(harness.toggles.isEmpty());
	}

	@Test
	void nonEssentialFlagTurnsOffEnergyGroupWithoutPdPriority() {
		Harness harness = new Harness();
		harness.autofiring = true;
		harness.evaluation.setUsesEnergy(true);
		harness.flags.setFlag(AIFlags.DO_NOT_AUTOFIRE_NON_ESSENTIAL_GROUPS);
		harness.weapon(WeaponType.ENERGY, 0, false, false);
		harness.manager().advance(1f, null, null);
		assertFalse(harness.autofiring);
	}

	@Test
	void highFluxTurnsOffEnergyGroup() {
		Harness harness = new Harness();
		harness.autofiring = true;
		harness.fluxLevel = 0.9f;
		harness.currentFlux = 900f;
		harness.evaluation.setUsesEnergy(true);
		harness.weapon(WeaponType.ENERGY, 0, false, false);
		harness.manager().advance(1f, null, null);
		assertFalse(harness.autofiring);
		assertEquals(List.of("off"), harness.toggles);
	}

	@Test
	void insufficientRuntimeCanToggleOffThenOnWithinDecision() {
		Harness harness = new Harness();
		harness.autofiring = true;
		harness.fluxLevel = 0.05f;
		harness.currentFlux = 100f;
		harness.evaluation.setUsesEnergy(true);
		harness.evaluation.setFluxPerSecond(1000f);
		harness.weapon(WeaponType.ENERGY, 0, false, false);
		harness.manager().advance(1f, null, null);
		assertEquals(List.of("off", "on"), harness.toggles);
	}

	@Test
	void missileAmmoThresholdIsStrictlyAboveTwenty() {
		Harness low = new Harness();
		low.weapon(WeaponType.MISSILE, 20, true, false);
		low.manager().advance(1f, null, null);
		assertFalse(low.autofiring);

		Harness enough = new Harness();
		enough.weapon(WeaponType.MISSILE, 21, true, false);
		enough.manager().advance(1f, null, null);
		assertTrue(enough.autofiring);
	}

	@Test
	void strikeGroupIsNotAutofireUsable() {
		Harness harness = new Harness();
		harness.weapon(WeaponType.BALLISTIC, 0, false, false, AIHints.STRIKE);
		harness.manager().advance(1f, null, null);
		assertFalse(harness.autofiring);
	}

	@Test
	void holdFireStillIssuesCommandBeforeGroupDecision() {
		Harness harness = new Harness();
		harness.holdFire = true;
		harness.weapon(WeaponType.BALLISTIC, 0, false, false);
		harness.manager().advance(1f, null, null);
		assertEquals(List.of(ShipCommand.HOLD_FIRE), harness.commands);
	}

	@Test
	void baseThreatFactorIsPointEight() {
		Harness harness = new Harness();
		assertEquals(0.8f, harness.manager().getThreatFactor(null));
	}

	@Test
	void largerFasterThreatRaisesRuntimeThreshold() throws Exception {
		Harness baseline = new Harness();
		baseline.autofiring = true;
		baseline.fluxLevel = 0.15f;
		baseline.currentFlux = 100f;
		baseline.evaluation.setUsesEnergy(true);
		baseline.evaluation.setFluxPerSecond(300f);
		baseline.weapon(WeaponType.ENERGY, 0, false, false);
		baseline.manager().advance(1f, null, null);
		assertTrue(baseline.autofiring);

		Harness threatened = new Harness();
		threatened.autofiring = true;
		threatened.fluxLevel = 0.15f;
		threatened.currentFlux = 100f;
		threatened.evaluation.setUsesEnergy(true);
		threatened.evaluation.setFluxPerSecond(300f);
		threatened.weapon(WeaponType.ENERGY, 0, false, false);
		CombatEngineAPI previous = Global.getCombatEngine();
		CombatEngineAPI engine = (CombatEngineAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{CombatEngineAPI.class}, (instance, method, args) -> null);
		try {
			Global.setCombatEngine(engine);
			BattleNext_ManeuverAPI maneuver = (BattleNext_ManeuverAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{BattleNext_ManeuverAPI.class}, (instance, method, args) ->
					"getAIFlags".equals(method.getName()) ? threatened.flags : null);
			BattleNext_ThreatEvalAI threatEval = new BattleNext_ThreatEvalAI(threatened.ship, maneuver, null);
			ShipAPI enemy = (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "hashCode" -> System.identityHashCode(instance);
					case "equals" -> instance == args[0];
					case "getHullSize" -> HullSize.CRUISER;
					case "getMaxSpeed" -> 150f;
					case "isFighter" -> false;
					default -> null;
				});
			BattleNext_ThreatData threat = new BattleNext_ThreatData();
			Field shipField = BattleNext_ThreatData.class.getDeclaredField("ship");
			shipField.setAccessible(true);
			shipField.set(threat, enemy);
			Field threatMapField = BattleNext_ThreatEvalAI.class.getDeclaredField("threatMap");
			threatMapField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<ShipAPI, BattleNext_ThreatData> threatMap =
				(Map<ShipAPI, BattleNext_ThreatData>) threatMapField.get(threatEval);
			threatMap.put(enemy, threat);
			BattleNext_FighterAutofireManager manager = threatened.manager();
			assertEquals(0.34f, manager.getThreatFactor(threatEval), 0.00001f);
			manager.advance(1f, threatEval, null);
			assertFalse(threatened.autofiring);
			assertEquals(List.of("off"), threatened.toggles);
		} finally {
			Global.setCombatEngine(previous);
		}
	}

	private static class Harness {
		private final List<WeaponAPI> weapons = new ArrayList<>();
		private final List<String> toggles = new ArrayList<>();
		private final List<ShipCommand> commands = new ArrayList<>();
		private final ShipwideAIFlags flags = new ShipwideAIFlags();
		private final BattleNext_WeaponGroupEvaluation evaluation = new BattleNext_WeaponGroupEvaluation();
		private final WeaponGroupAPI group;
		private final ShipAPI ship;
		private boolean autofiring;
		private boolean holdFire;
		private float fluxLevel;
		private float currentFlux;

		Harness() {
			this.evaluation.setWeaponGroupIndex(0);
			this.group = (WeaponGroupAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{WeaponGroupAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "isAutofiring" -> autofiring;
					case "toggleOn" -> toggle(true);
					case "toggleOff" -> toggle(false);
					case "getWeaponsCopy" -> weapons;
					default -> null;
				});
			FluxTrackerAPI flux = (FluxTrackerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{FluxTrackerAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getMaxFlux" -> 1000f;
					case "getCurrFlux" -> currentFlux;
					default -> null;
				});
			this.ship = (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "isFighter" -> true;
					case "isDrone" -> false;
					case "isHoldFire" -> holdFire;
					case "getWeaponGroupsCopy" -> List.of(group);
					case "getUsableWeapons" -> weapons;
					case "getFluxTracker" -> flux;
					case "getFluxLevel" -> fluxLevel;
					case "getHullSize" -> HullSize.FRIGATE;
					case "getMaxSpeed" -> 100f;
					case "giveCommand" -> record((ShipCommand) args[0]);
					case "getOriginalOwner" -> 0;
					case "isNonCombat" -> false;
					default -> null;
				});
		}

		BattleNext_FighterAutofireManager manager() {
			Map<WeaponGroupAPI, BattleNext_WeaponGroupEvaluation> evaluations = new IdentityHashMap<>();
			evaluations.put(this.group, this.evaluation);
			return new BattleNext_FighterAutofireManager(this.ship, evaluations, this.flags);
		}

		void weapon(WeaponType type, int ammo, boolean usesAmmo, boolean firing, AIHints... hints) {
			EnumSet<AIHints> activeHints = EnumSet.noneOf(AIHints.class);
			for (AIHints hint : hints) {
				activeHints.add(hint);
			}
			WeaponSlotAPI slot = (WeaponSlotAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{WeaponSlotAPI.class}, (instance, method, args) ->
					"getArc".equals(method.getName()) ? 60f : null);
			WeaponAPI weapon = (WeaponAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{WeaponAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getType" -> type;
					case "getSize" -> WeaponSize.SMALL;
					case "getAmmo" -> ammo;
					case "usesAmmo" -> usesAmmo;
					case "isFiring" -> firing;
					case "hasAIHint" -> activeHints.contains(args[0]);
					case "getSlot" -> slot;
					case "isBeam", "isBurstBeam" -> false;
					default -> null;
				});
			this.weapons.add(weapon);
		}

		private Object toggle(boolean on) {
			this.autofiring = on;
			this.toggles.add(on ? "on" : "off");
			return null;
		}

		private Object record(ShipCommand command) {
			this.commands.add(command);
			return null;
		}
	}
}
