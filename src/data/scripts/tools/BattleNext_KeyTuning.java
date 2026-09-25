package data.scripts.tools;

import org.lwjgl.input.Keyboard;

public class BattleNext_KeyTuning {

	private BattleNext_KeyTuning() {

	}

	public static int adjustHorizontalWrapped(int value, int size) {
		if (size <= 0) return value;
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) value++;
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) value--;
		return Math.floorMod(value, size);
	}

	public static int adjustHorizontalClamped(int value, int min, int max) {
		if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) value++;
		if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) value--;
		return Math.max(min, Math.min(max, value));
	}

	public static int adjustVerticalClamped(int value, int step, int min, int max) {
		if (Keyboard.isKeyDown(Keyboard.KEY_UP)) value += step;
		if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) value -= step;
		return Math.max(min, Math.min(max, value));
	}
}
