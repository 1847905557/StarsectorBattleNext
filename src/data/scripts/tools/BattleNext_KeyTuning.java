package data.scripts.tools;

import org.lwjgl.input.Keyboard;
import java.util.function.IntPredicate;

public class BattleNext_KeyTuning {

	// no final here
	private static IntPredicate keyPressed = Keyboard::isKeyDown;

	private BattleNext_KeyTuning() {

	}

	public static int adjustHorizontalWrapped(int value, int size) {
		return adjustWrapped(value, size, keyPressed.test(Keyboard.KEY_LEFT), keyPressed.test(Keyboard.KEY_RIGHT));
	}

	public static int adjustAdWrapped(int value, int size) {
		return adjustWrapped(value, size, keyPressed.test(Keyboard.KEY_A), keyPressed.test(Keyboard.KEY_D));
	}

	public static int adjustHorizontalClamped(int value, int min, int max) {
		return adjustClamped(value, 1, min, max, keyPressed.test(Keyboard.KEY_LEFT), keyPressed.test(Keyboard.KEY_RIGHT));
	}

	public static int adjustVerticalClamped(int value, int step, int min, int max) {
		return adjustClamped(value, step, min, max, keyPressed.test(Keyboard.KEY_DOWN), keyPressed.test(Keyboard.KEY_UP));
	}

	private static int adjustWrapped(int value, int size, boolean decrease, boolean increase) {
		if (size <= 0) return value;
		if (increase) value++;
		if (decrease) value--;
		return Math.floorMod(value, size);
	}

	private static int adjustClamped(int value, int step, int min, int max, boolean decrease, boolean increase) {
		if (increase) value += step;
		if (decrease) value -= step;
		return Math.max(min, Math.min(max, value));
	}
}
