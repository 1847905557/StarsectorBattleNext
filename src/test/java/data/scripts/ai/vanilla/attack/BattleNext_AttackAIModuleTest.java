package data.scripts.ai.vanilla.attack;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleNext_AttackAIModuleTest {
	@Test
	void choosesVanillaAutofireManagerByShipType() throws Exception {
		CombatEngineAPI previous = Global.getCombatEngine();
		CombatEngineAPI engine = (CombatEngineAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{CombatEngineAPI.class}, (instance, method, args) -> null);
		try {
			Global.setCombatEngine(engine);
			assertInstanceOf(BattleNext_AutofireManagerV2.class, managerFor(false, false));
			assertInstanceOf(BattleNext_FighterAutofireManager.class, managerFor(true, false));
			assertInstanceOf(BattleNext_FighterAutofireManager.class, managerFor(false, true));
		} finally {
			Global.setCombatEngine(previous);
		}
	}

	private BattleNext_AutofireManager managerFor(boolean fighter, boolean drone) throws Exception {
		ShipAPI ship = (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ShipAPI.class}, (instance, method, args) -> switch (method.getName()) {
				case "isFighter" -> fighter;
				case "isDrone" -> drone;
				case "getWeaponGroupsCopy" -> List.of();
				case "getOriginalOwner" -> 0;
				default -> null;
			});
		BattleNext_AttackAIModule attack = new BattleNext_AttackAIModule(ship, new ShipwideAIFlags());
		Field field = BattleNext_AttackAIModule.class.getDeclaredField("autofireManager");
		field.setAccessible(true);
		return (BattleNext_AutofireManager) field.get(attack);
	}


	@Test
	void acceptsMissileSpecWithoutProjectileSpecInterface() {
		MissileSpecAPI spec = (MissileSpecAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{MissileSpecAPI.class}, (instance, method, args) -> null);

		assertSame(spec, BattleNext_AttackAIModule.requireMissileSpec(spec, "missile_weapon"));
	}

	@Test
	void rejectsNonMissileSpecWithWeaponId() {
		assertThrows(IllegalArgumentException.class,
				() -> BattleNext_AttackAIModule.requireMissileSpec(new Object(), "wrong_spec"));
	}
}
