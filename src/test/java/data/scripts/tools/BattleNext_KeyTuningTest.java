package data.scripts.tools;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

class BattleNext_KeyTuningTest {

	private final Set<Integer> keys = new HashSet<>();
	private Field keyboard;
	private Object previousKeyboard;

	@BeforeEach
	void bindKeyboardInput() throws Exception {
		keyboard = BattleNext_KeyTuning.class.getDeclaredField("keyPressed");
		keyboard.setAccessible(true);
		previousKeyboard = keyboard.get(null);
		keyboard.set(null, (IntPredicate) keys::contains);
	}

	@AfterEach
	void restoreKeyboardInput() throws Exception {
		keyboard.set(null, previousKeyboard);
	}

	@Test
	void wrappingClampingAndOpposingKeysUseTheSharedKeyboardInput() {
		keys.add(Keyboard.KEY_LEFT);
		assertEquals(1, BattleNext_KeyTuning.adjustHorizontalWrapped(0, 2));
		keys.clear();
		keys.add(Keyboard.KEY_RIGHT);
		assertEquals(0, BattleNext_KeyTuning.adjustHorizontalWrapped(1, 2));
		keys.add(Keyboard.KEY_LEFT);
		assertEquals(1, BattleNext_KeyTuning.adjustHorizontalWrapped(1, 2));
		keys.clear();
		keys.add(Keyboard.KEY_UP);
		assertEquals(110, BattleNext_KeyTuning.adjustVerticalClamped(100, 10, 10, 2000));
		assertEquals(2000, BattleNext_KeyTuning.adjustVerticalClamped(2000, 10, 10, 2000));
		keys.clear();
		keys.add(Keyboard.KEY_DOWN);
		assertEquals(10, BattleNext_KeyTuning.adjustVerticalClamped(10, 10, 10, 2000));
		keys.add(Keyboard.KEY_UP);
		assertEquals(100, BattleNext_KeyTuning.adjustVerticalClamped(100, 10, 10, 2000));
	}
}
