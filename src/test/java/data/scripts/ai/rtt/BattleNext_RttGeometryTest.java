package data.scripts.ai.rtt;

import com.fs.starfarer.api.combat.ShipAPI;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

class BattleNext_RttGeometryTest {

	@Test
	void rectangleIncludesShipCenterAtBoundary() {
		assertTrue(BattleNext_RttGeometry.inside(10f, 5f, 10f, 10f, 0f, 0f));
		assertFalse(BattleNext_RttGeometry.inside(11f, 5f, 10f, 10f, 0f, 0f));
	}

	@Test
	void hitUsesNearestCollisionCircle() {
		ShipAPI first = ship(0f, 0f, 100f);
		ShipAPI second = ship(10f, 0f, 100f);

		assertSame(second, BattleNext_RttGeometry.hit(List.of(first, second), 8f, 0f));
		assertEquals(null, BattleNext_RttGeometry.hit(List.of(first), 200f, 0f));
	}

	@Test
	void selectionReplacesOrAddsAndEmptyClickRespectsShift() {
		ShipAPI first = ship(0f, 0f, 20f);
		ShipAPI second = ship(100f, 0f, 20f);
		List<ShipAPI> candidates = List.of(first, second);
		Set<ShipAPI> initial = Set.of(first);

		assertEquals(Set.of(first, second), BattleNext_RttGeometry.select(initial, candidates,
				100f, 0f, 100f, 0f, false, true));
		assertEquals(Set.of(second), BattleNext_RttGeometry.select(initial, candidates,
				100f, 0f, 100f, 0f, false, false));
		assertEquals(Set.of(), BattleNext_RttGeometry.select(initial, candidates,
				200f, 0f, 200f, 0f, false, false));
		assertEquals(initial, BattleNext_RttGeometry.select(initial, candidates,
				200f, 0f, 200f, 0f, false, true));
		assertEquals(Set.of(first, second), BattleNext_RttGeometry.select(Set.of(), candidates,
				-5f, -5f, 105f, 5f, true, false));
	}

	@Test
	void formationPreservesLeftToRightOrderAndAvoidsOverlap() {
		ShipAPI left = ship(-2000f, -150f, 100f);
		ShipAPI center = ship(0f, 200f, 150f);
		ShipAPI right = ship(2500f, 50f, 80f);

		Map<ShipAPI, Vector2f> destinations = BattleNext_RttGeometry.formation(
				List.of(right, left, center), new Vector2f(100f, 100f), 12000f, 12000f);

		assertTrue(destinations.get(left).x < destinations.get(center).x);
		assertTrue(destinations.get(center).x < destinations.get(right).x);
		for (ShipAPI first : destinations.keySet()) {
			for (ShipAPI second : destinations.keySet()) {
				if (first == second) continue;
				float minimum = first.getCollisionRadius() + second.getCollisionRadius();
				assertTrue(Vector2f.sub(destinations.get(first), destinations.get(second), null).length() >= minimum);
			}
		}
	}

	@Test
	void formationHandlesCoincidentShips() {
		ShipAPI first = ship(0f, 0f, 100f);
		ShipAPI second = ship(0f, 0f, 100f);

		Map<ShipAPI, Vector2f> destinations = BattleNext_RttGeometry.formation(
				List.of(first, second), new Vector2f(), 12000f, 12000f);

		assertTrue(Vector2f.sub(destinations.get(first), destinations.get(second), null).length() >= 200f);
	}

	private ShipAPI ship(float x, float y, float radius) {
		Vector2f location = new Vector2f(x, y);
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ShipAPI.class},
				(proxy, method, args) -> switch (method.getName()) {
					case "getLocation" -> location;
					case "getCollisionRadius" -> radius;
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> null;
				});
	}
}
