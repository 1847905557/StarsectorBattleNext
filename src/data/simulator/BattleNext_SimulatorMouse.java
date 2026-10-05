package data.simulator;

import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.InputImplementation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class BattleNext_SimulatorMouse implements InputImplementation {

	private final InputImplementation original;
	private int cursorX;
	private int cursorY;
	private boolean grabbed;

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
		return isolated;
	}

	public void cleanup() throws ReflectiveOperationException {
		VarHandle implementation = MethodHandles.privateLookupIn(Mouse.class, MethodHandles.lookup())
				.findStaticVarHandle(Mouse.class, "implementation", InputImplementation.class);
		if (implementation.get() != this) return;
		implementation.set(original);
	}

	@Override
	public boolean hasWheel() {
		return original.hasWheel();
	}

	@Override
	public int getButtonCount() {
		return original.getButtonCount();
	}

	@Override
	public void createMouse() throws LWJGLException {
		original.createMouse();
	}

	@Override
	public void destroyMouse() {
		original.destroyMouse();
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
		return original.getNativeCursorCapabilities();
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
		return original.getMinCursorSize();
	}

	@Override
	public int getMaxCursorSize() {
		return original.getMaxCursorSize();
	}

	@Override
	public void createKeyboard() throws LWJGLException {
		original.createKeyboard();
	}

	@Override
	public void destroyKeyboard() {
		original.destroyKeyboard();
	}

	@Override
	public void pollKeyboard(ByteBuffer keys) {
		original.pollKeyboard(keys);
	}

	@Override
	public void readKeyboard(ByteBuffer buffer) {
		original.readKeyboard(buffer);
	}

	@Override
	public Object createCursor(int width, int height, int hotspotX, int hotspotY, int imageCount, IntBuffer images, IntBuffer delays) throws LWJGLException {
		return original.createCursor(width, height, hotspotX, hotspotY, imageCount, images, delays);
	}

	@Override
	public void destroyCursor(Object cursor) {
		original.destroyCursor(cursor);
	}

	@Override
	public int getWidth() {
		return original.getWidth();
	}

	@Override
	public int getHeight() {
		return original.getHeight();
	}

	@Override
	public boolean isInsideWindow() {
		return false;
	}
}
