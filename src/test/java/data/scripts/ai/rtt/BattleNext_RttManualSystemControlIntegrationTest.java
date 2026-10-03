package data.scripts.ai.rtt;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class BattleNext_RttManualSystemControlIntegrationTest {

	@Test
	void systemCommandUsesCursorAndPriorityExpiresAfterThreeCombatSeconds() {
		Vector2f mouseTarget = new Vector2f();
		AtomicInteger commands = new AtomicInteger();
		AtomicReference<Vector2f> commandedPoint = new AtomicReference<>();
		ShipAPI ship = (ShipAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ShipAPI.class},
				(instance, method, args) -> switch (method.getName()) {
					case "getMouseTarget" -> mouseTarget;
					case "giveCommand" -> {
						assertSame(ShipCommand.USE_SYSTEM, args[0]);
						commandedPoint.set((Vector2f) args[1]);
						commands.incrementAndGet();
						yield null;
					}
					default -> null;
				});
		ShipwideAIFlags flags = new ShipwideAIFlags();
		BattleNext_RttShipAI.RttManualSystemControl control =
				new BattleNext_RttShipAI.RttManualSystemControl(ship, flags);

		control.rttUseSystemAt(new Vector2f(200f, -300f));
		assertEquals(1, commands.get());
		assertEquals(200f, commandedPoint.get().x);
		assertEquals(-300f, mouseTarget.y);
		assertSame(commandedPoint.get(), flags.getCustom(AIFlags.SYSTEM_TARGET_COORDS));
		assertTrue(control.rttPriorityActive());

		mouseTarget.set(0f, 0f);
		control.rttAdvance(0f);
		assertEquals(200f, mouseTarget.x);
		assertTrue(control.rttPriorityActive());
		control.rttAdvance(2.99f);
		assertTrue(control.rttPriorityActive());
		control.rttAdvance(0.01f);
		assertFalse(control.rttPriorityActive());

		control.rttUseSystemAt(new Vector2f(50f, 60f));
		assertEquals(2, commands.get());
		assertTrue(control.rttPriorityActive());
	}
}
