package data.scripts.ai.vanilla.maneuvers;

import com.fs.starfarer.api.combat.ShipAPI;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_StrafeTargetManeuverV2Test {
	@Test
	void stationCoverKeepsTaskWithMissingDeployedShip() {
		ShipAPI target = ship(null);
		ShipAPI unrelated = ship(null);
		assertTrue(BattleNext_StrafeTargetManeuverV2.isStationCoverTaskTarget(null, target));
		assertTrue(BattleNext_StrafeTargetManeuverV2.isStationCoverTaskTarget(target, target));
		assertFalse(BattleNext_StrafeTargetManeuverV2.isStationCoverTaskTarget(unrelated, target));
	}

	private ShipAPI ship(ShipAPI parent) {
		return (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ShipAPI.class}, (instance, method, args) ->
				"getParentStation".equals(method.getName()) ? parent : null);
	}
}
