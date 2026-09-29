package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_ShipStatisticsTrackerTest {

	@Test
	void samplesFluxAndResetsDamageBetweenIntervals() {
		BattleNext_ShipStatisticsTracker tracker = new BattleNext_ShipStatisticsTracker();
		ShipAPI ship = ship();
		tracker.advance(ship, null, 2f, false);
		assertTrue(tracker.fitFluxLevel(5f).avgX > 0f);
		assertTrue(tracker.fitDistanceToEnemy(5f).avgX > 0f);
		assertFalse(tracker.isDistanceHistoryFull());

		tracker.addDamageTaken(90f);
		tracker.addDamageDealt(60f);
		tracker.advance(ship, null, 2f, false);
		assertTrue(tracker.fitDamageTaken(0f).avgX > 0f);
		assertTrue(tracker.fitDamageDealt(0f).avgX > 0f);

		tracker.advance(ship, null, 2f, false);
		assertEquals(0f, tracker.fitDamageTaken(0f).avgX, 0.0001f);
		assertEquals(0f, tracker.fitDamageDealt(0f).avgX, 0.0001f);
	}

	private ShipAPI ship() {
		FluxTrackerAPI flux = (FluxTrackerAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{FluxTrackerAPI.class}, (instance, method, args) ->
					"getFluxLevel".equals(method.getName()) ? 0.5f : null);
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getFluxTracker" -> flux;
					case "getHardFluxLevel" -> 0.25f;
					default -> null;
				});
	}
}
