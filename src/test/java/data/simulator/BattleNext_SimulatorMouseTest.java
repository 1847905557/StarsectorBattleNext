package data.simulator;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.InputImplementation;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorMouseTest {

	@Test
	void virtualMouseContainsPositionGrabCursorAndInputWithoutCallingTheDesktopBackend() throws Exception {

		List<String> calls = new ArrayList<>();
		InputImplementation original = (InputImplementation) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{InputImplementation.class},
				(instance, method, arguments) -> {
			calls.add(method.getName());
			return null;
		});
		var constructor = BattleNext_SimulatorMouse.class.getDeclaredConstructor(InputImplementation.class);
		constructor.setAccessible(true);
		BattleNext_SimulatorMouse mouse = constructor.newInstance(original);
		IntBuffer coordinates = IntBuffer.allocate(3);
		ByteBuffer buttons = ByteBuffer.allocate(3);
		ByteBuffer events = ByteBuffer.allocate(32);
		for (int cycle = 0; cycle < 10; cycle++) {
			mouse.setCursorPosition(400, 300);
			mouse.setNativeCursor(new Object());
			buttons.put(0, (byte) 1);
			mouse.pollMouse(coordinates, buttons);
			assertArrayEquals(new int[]{400, 300, 0}, coordinates.array());
			assertArrayEquals(new byte[3], buttons.array());
			mouse.grabMouse(true);
			mouse.pollMouse(coordinates, buttons);
			assertArrayEquals(new int[3], coordinates.array());
			mouse.grabMouse(false);
			mouse.readMouse(events);
			assertEquals(0, events.position());
			assertFalse(mouse.isInsideWindow());
		}
		assertTrue(calls.isEmpty());
	}
}
