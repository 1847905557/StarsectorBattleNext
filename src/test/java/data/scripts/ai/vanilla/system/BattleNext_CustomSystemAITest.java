package data.scripts.ai.vanilla.system;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_CustomSystemAITest {

	@Test
	void customScriptReceivesOneInitializationAndEachAdvance() throws JSONException {
		CombatEngineAPI original = Global.getCombatEngine();
		CombatEngineAPI engine = proxy(CombatEngineAPI.class, (method, args) -> null);
		Global.setCombatEngine(engine);
		try {
			AtomicReference<ShipSystemAPI> installedSystem = new AtomicReference<>();
			ShipAPI ship = proxy(ShipAPI.class, (method, args) -> "getSystem".equals(method) ? installedSystem.get() : null);
			ShipAPI target = proxy(ShipAPI.class, (method, args) -> null);
			ShipwideAIFlags flags = new ShipwideAIFlags();
			Vector2f missile = new Vector2f(1f, 2f);
			Vector2f collision = new Vector2f(3f, 4f);
			AtomicInteger initCalls = new AtomicInteger();
			AtomicInteger advanceCalls = new AtomicInteger();
			ShipSystemAIScript script = new ShipSystemAIScript() {
				@Override
				public void init(ShipAPI actualShip, ShipSystemAPI actualSystem, ShipwideAIFlags actualFlags, CombatEngineAPI actualEngine) {
					assertSame(ship, actualShip);
					assertSame(installedSystem.get(), actualSystem);
					assertSame(flags, actualFlags);
					assertSame(engine, actualEngine);
					initCalls.incrementAndGet();
				}

				@Override
				public void advance(float amount, Vector2f missileDir, Vector2f collisionDir, ShipAPI actualTarget) {
					assertEquals(0.25f, amount);
					assertSame(missile, missileDir);
					assertSame(collision, collisionDir);
					assertSame(target, actualTarget);
					advanceCalls.incrementAndGet();
				}
			};
			ShipSystemAPI configuredSystem = system(script);
			installedSystem.set(configuredSystem);
			BattleNext_SystemAI ai = BattleNext_SystemAIFactory.create(ship, configuredSystem, flags,
					null, null, null, null);

			assertEquals(1, initCalls.get());
			ai.advance(0.25f, missile, collision, target);
			ai.advance(0.25f, missile, collision, target);
			assertEquals(2, advanceCalls.get());
		} finally {
			Global.setCombatEngine(original);
		}
	}

	@Test
	void customScriptMissingFailsConfiguration() {
		ShipAPI ship = proxy(ShipAPI.class, (method, args) -> null);
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> BattleNext_SystemAIFactory.create(ship, system(null), new ShipwideAIFlags(), null, null, null, null));
		assertEquals("Error creating AI of type [CUSTOM] for ship system [test_system]", error.getMessage());
		assertInstanceOf(IllegalArgumentException.class, error.getCause());
		assertEquals("CUSTOM ship system AI has no aiScript: test_system", error.getCause().getMessage());
	}

	private ShipSystemAPI system(ShipSystemAIScript script) throws JSONException {
		JSONObject data = new JSONObject();
		data.put("aiType", "CUSTOM");
		ShipSystemSpecAPI spec = proxy(ShipSystemSpecAPI.class, (method, args) -> switch (method) {
			case "getAIScript" -> script;
			case "getSpecJson" -> data;
			case "getId" -> "test_system";
			default -> null;
		});
		return proxy(ShipSystemAPI.class, (method, args) -> "getSpecAPI".equals(method) ? spec : null);
	}

	private interface Call {
		Object invoke(String method, Object[] args);
	}

	private <T> T proxy(Class<T> type, Call call) {
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
				(instance, method, args) -> call.invoke(method.getName(), args)));
	}
}
