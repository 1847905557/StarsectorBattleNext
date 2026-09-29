package data.scripts.ai.vanilla;

import com.fs.starfarer.api.combat.ShipSystemAPI;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleNext_VentModuleTest {

	@Test
	void systemAmmoUsesRuntimeSentinel() {
		assertTrue(BattleNext_VentModule.systemUsesAmmo(system(0)));
		assertTrue(BattleNext_VentModule.systemUsesAmmo(system(1)));
		assertFalse(BattleNext_VentModule.systemUsesAmmo(system(Integer.MAX_VALUE)));
	}

	private ShipSystemAPI system(int ammo) {
		return (ShipSystemAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ShipSystemAPI.class}, (instance, method, args) ->
					"getAmmo".equals(method.getName()) ? ammo : null);
	}
}
