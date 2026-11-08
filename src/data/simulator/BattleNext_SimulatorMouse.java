package data.simulator;

import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Cursor;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.InputImplementation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class BattleNext_SimulatorMouse implements InputImplementation {

	private final InputImplementation original;
	private int cursorX;
	private int cursorY;
	private boolean grabbed;
	private boolean closing;

	private BattleNext_SimulatorMouse(InputImplementation original) {
		this.original = original;
	}

	@LifecycleEntry
	public static BattleNext_SimulatorMouse getInstance() throws ReflectiveOperationException {
		VarHandle implementation = MethodHandles.privateLookupIn(Mouse.class, MethodHandles.lookup())
				.findStaticVarHandle(Mouse.class, "implementation", InputImplementation.class);
		InputImplementation current = (InputImplementation) implementation.get();
		if (current instanceof BattleNext_SimulatorMouse isolated) return isolated;
		BattleNext_SimulatorMouse isolated = new BattleNext_SimulatorMouse(current);
		implementation.set(isolated);
		isolated.createVirtualMouse();
		return isolated;
	}

	private void createVirtualMouse() throws ReflectiveOperationException {
		// Initialize the virtual device before Display can create a desktop mouse.
		Method create = Mouse.class.getDeclaredMethod("create", InputImplementation.class);
		create.setAccessible(true);
		create.invoke(null, this);
	}

	public void cleanup() throws ReflectiveOperationException {
		VarHandle implementation = MethodHandles.privateLookupIn(Mouse.class, MethodHandles.lookup())
				.findStaticVarHandle(Mouse.class, "implementation", InputImplementation.class);
		if (implementation.get() != this) return;
		closing = true;
		Mouse.destroy();
		implementation.set(original);
	}

	@Override
	public boolean hasWheel() {
		return true;
	}

	@Override
	public int getButtonCount() {
		return 3;
	}

	@Override
	public void createMouse() {

	}

	@Override
	public void destroyMouse() {
		if (closing) return;
		// Display recreation destroys Mouse; keep it virtual until explicit cleanup.
		try {
			createVirtualMouse();
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Unable to preserve simulator mouse isolation during window recreation", exception);
		}
	}

	@Override
	public void pollMouse(IntBuffer coordinates, ByteBuffer buttons) {
		coordinates.put(0, grabbed ? 0 : cursorX);
		coordinates.put(1, grabbed ? 0 : cursorY);
		coordinates.put(2, 0);
		for (int index = 0; index < buttons.capacity(); index++) {
			buttons.put(index, (byte) 0);
		}
	}

	@Override
	public void readMouse(ByteBuffer buffer) {

	}

	@Override
	public void grabMouse(boolean grab) {
		grabbed = grab;
	}

	@Override
	public int getNativeCursorCapabilities() {
		return Cursor.CURSOR_ONE_BIT_TRANSPARENCY | Cursor.CURSOR_8_BIT_ALPHA | Cursor.CURSOR_ANIMATION;
	}

	@Override
	public void setCursorPosition(int cursorX, int cursorY) {
		this.cursorX = cursorX;
		this.cursorY = cursorY;
	}

	@Override
	public void setNativeCursor(Object handle) {

	}

	@Override
	public int getMinCursorSize() {
		return 1;
	}

	@Override
	public int getMaxCursorSize() {
		return 64;
	}

	@Override
	public void createKeyboard() {

	}

	@Override
	public void destroyKeyboard() {

	}

	@Override
	public void pollKeyboard(ByteBuffer keys) {
		for (int index = 0; index < keys.capacity(); index++) {
			keys.put(index, (byte) 0);
		}
	}

	@Override
	public void readKeyboard(ByteBuffer buffer) {

	}

	@Override
	public Object createCursor(int width, int height, int hotspotX, int hotspotY, int imageCount, IntBuffer images, IntBuffer delays) {
		return new Object();
	}

	@Override
	public void destroyCursor(Object cursor) {

	}

	@Override
	public int getWidth() {
		return Display.getWidth();
	}

	@Override
	public int getHeight() {
		return Display.getHeight();
	}

	@Override
	public boolean isInsideWindow() {
		return false;
	}
}
