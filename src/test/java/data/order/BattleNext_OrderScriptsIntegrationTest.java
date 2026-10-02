package data.order;

import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import data.order.filter.HullSizeFilter;
import data.order.filter.SelfHpFilter;
import data.order.filter.TargetFluxFilter;
import data.order.filter.TargetHpFilter;
import data.order.filter.TargetSpeedFilter;
import data.order.starter.EscortStarter;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_OrderScriptsIntegrationTest {

	@Test
	void hullSizeSelectsLargestOrSmallestWithinAllowedSizes() {
		ShipAPI frigate = ship(ShipAPI.HullSize.FRIGATE, .4f, 400f, 100f, 100f);
		ShipAPI destroyer = ship(ShipAPI.HullSize.DESTROYER, .6f, 600f, 80f, 80f);
		ShipAPI cruiser = ship(ShipAPI.HullSize.CRUISER, .8f, 800f, 60f, 60f);
		HullSizeFilter filter = new HullSizeFilter();
		List<CombatEntityAPI> targets = new ArrayList<>(List.of(frigate, destroyer, cruiser));
		filter.filterTargets(null, targets, new String[]{"frigate", "cruiser", BattleNext_OrderUtils.HULL_SIZE_LARGEST}, null);
		assertEquals(List.of(cruiser), targets);
		targets = new ArrayList<>(List.of(frigate, destroyer, cruiser));
		filter.filterTargets(null, targets, new String[]{"frigate", "cruiser", BattleNext_OrderUtils.HULL_SIZE_SMALLEST}, null);
		assertEquals(List.of(frigate), targets);
		targets = new ArrayList<>(List.of(frigate, destroyer, cruiser));
		filter.filterTargets(null, targets, new String[]{"FRIGATE"}, null);
		assertTrue(targets.isEmpty());
	}

	@Test
	void numericFiltersRemoveEveryViolatingTarget() {
		ShipAPI low = ship(ShipAPI.HullSize.FRIGATE, .2f, 200f, 30f, 30f);
		ShipAPI middle = ship(ShipAPI.HullSize.DESTROYER, .5f, 500f, 60f, 60f);
		ShipAPI high = ship(ShipAPI.HullSize.CRUISER, .9f, 900f, 90f, 90f);
		List<CombatEntityAPI> targets = new ArrayList<>(List.of(low, middle, high));
		new TargetHpFilter().filterTargets(null, targets, new String[]{"30%", "70%"}, null);
		assertEquals(List.of(middle), targets);
		targets = new ArrayList<>(List.of(low, middle, high));
		new TargetFluxFilter().filterTargets(null, targets, new String[]{"30%", "70%"}, null);
		assertEquals(List.of(middle), targets);
		targets = new ArrayList<>(List.of(low, middle, high));
		new TargetSpeedFilter().filterTargets(low, targets, new String[]{"40", "80"}, null);
		assertEquals(List.of(middle), targets);
	}

	@Test
	void nonpositiveUpperBoundLeavesFollowingInstructionAvailable() {
		ShipAPI source = ship(ShipAPI.HullSize.FRIGATE, .4f, 400f, 100f, 100f);
		List<CombatEntityAPI> targets = new ArrayList<>(List.of(source));
		new SelfHpFilter().filterTargets(source, targets, new String[]{"0"}, null);
		assertTrue(targets.isEmpty());
		assertEquals(CombatAssignmentType.LIGHT_ESCORT, new EscortStarter().getCombatAssignmentType());
	}

	private ShipAPI ship(ShipAPI.HullSize size, float level, float hp, float flux, float speed) {
		FluxTrackerAPI tracker = (FluxTrackerAPI)Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{FluxTrackerAPI.class}, (proxy, method, args) -> {
					if ("isOverloaded".equals(method.getName())) return false;
					return null;
				});
		return (ShipAPI)Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipAPI.class}, (proxy, method, args) -> switch (method.getName()) {
					case "getHullSize" -> size;
					case "getHullLevel", "getFluxLevel" -> level;
					case "getHitpoints" -> hp;
					case "getCurrFlux" -> flux;
					case "getMaxSpeed" -> speed;
					case "getFluxTracker" -> tracker;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> null;
				});
	}
}
