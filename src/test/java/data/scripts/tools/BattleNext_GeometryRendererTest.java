package data.scripts.tools;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;

class BattleNext_GeometryRendererTest {
	private static final Color FOREGROUND = new Color(175, 175, 175, 180);
	private static final Color BACKGROUND = new Color(40, 60, 80);

	@Test
	void primitivesCompositeTheirColorAlphaAndRestoreCallerStateIncludingOnFailure() throws Exception {
		String previousLibraryPath = System.getProperty("org.lwjgl.librarypath");
		System.setProperty("org.lwjgl.librarypath", Path.of("../../starsector-core/native/windows").toAbsolutePath().normalize().toString());
		Field initialized = BattleNext_GeometryRenderer.class.getDeclaredField("initialized");
		initialized.setAccessible(true);
		Pbuffer buffer = new Pbuffer(32, 32, new PixelFormat(), null, null);
		try {
			buffer.makeCurrent();
			initialized.setBoolean(null, false);
			GL11.glViewport(0, 0, 32, 32);
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			GL11.glOrtho(0, 32, 0, 32, -1, 1);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glLoadIdentity();
			GL11.glDisable(GL11.GL_LINE_SMOOTH);
			GL11.glDisable(GL11.GL_POINT_SMOOTH);
			GL11.glEnable(GL11.GL_SCISSOR_TEST);
			GL11.glScissor(0, 0, 24, 24);
			int callerBuffer = GL15.glGenBuffers();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, callerBuffer);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			Color foreground = FOREGROUND;
			Color background = BACKGROUND;
			ByteBuffer pixel = BufferUtils.createByteBuffer(4);
			IntBuffer clip = BufferUtils.createIntBuffer(16);
			FloatBuffer currentColor = BufferUtils.createFloatBuffer(16);
			for (int mode = 0; mode < 4; mode++) {
				boolean callerBlend = mode != 0;
				int callerEquation = mode == 2 ? GL14.GL_FUNC_REVERSE_SUBTRACT : GL14.GL_FUNC_ADD;
				float alphaMult = mode == 1 ? 0.5f : mode == 3 ? 0f : 1f;
				if (callerBlend) GL11.glEnable(GL11.GL_BLEND);
				else GL11.glDisable(GL11.GL_BLEND);
				GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GL14.glBlendEquation(callerEquation);
				GL11.glColor4f(0.2f, 0.4f, 0.6f, 0.8f);
				GL11.glEnable(GL11.GL_TEXTURE_2D);
				GL11.glLineWidth(3f);
				GL11.glPointSize(3f);
				GL11.glDisable(GL11.GL_SCISSOR_TEST);
				GL11.glClearColor(background.getRed() / 255f, background.getGreen() / 255f, background.getBlue() / 255f, 1f);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				GL11.glEnable(GL11.GL_SCISSOR_TEST);
				for (int primitive = 0; primitive < 4; primitive++) {
					GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
					switch (primitive) {
						case 0 -> BattleNext_GeometryRenderer.fillRect(4f, 4f, 24f, 24f, foreground, alphaMult);
						case 1 -> BattleNext_GeometryRenderer.lineLoop(new float[]{4f, 4f, 28f, 4f, 28f, 28f, 4f, 28f}, foreground, alphaMult, 2f);
						case 2 -> BattleNext_GeometryRenderer.lineStrip(new float[]{4f, 4f, 28f, 4f}, foreground, alphaMult, 2f);
						case 3 -> BattleNext_GeometryRenderer.drawPoint(16f, 16f, 2f, foreground, alphaMult);
					}
					GL11.glReadPixels(16, primitive == 1 || primitive == 2 ? 4 : 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
					float effectiveAlpha = foreground.getAlpha() / 255f * alphaMult;
					for (int component = 0; component < 3; component++) {
						int destination = component == 0 ? background.getRed() : component == 1 ? background.getGreen() : background.getBlue();
						float expected = 175f * effectiveAlpha + destination * (1f - effectiveAlpha);
						assertEquals(expected, pixel.get(component) & 255, 1f, "mode=" + mode + ", primitive=" + primitive);
					}
					GL11.glReadPixels(26, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
					assertEquals(background.getRed(), pixel.get(0) & 255);
					assertEquals(background.getGreen(), pixel.get(1) & 255);
					assertEquals(background.getBlue(), pixel.get(2) & 255);
					assertEquals(callerBlend, GL11.glIsEnabled(GL11.GL_BLEND));
					assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL11.GL_BLEND_SRC));
					assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL11.GL_BLEND_DST));
					assertEquals(GL11.GL_ZERO, GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
					assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
					assertEquals(callerEquation, GL11.glGetInteger(GL14.GL_BLEND_EQUATION));
					assertEquals(callerBuffer, GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
					assertTrue(GL11.glIsEnabled(GL11.GL_TEXTURE_2D));
					assertTrue(GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY));
					assertEquals(3f, GL11.glGetFloat(GL11.GL_LINE_WIDTH));
					assertEquals(3f, GL11.glGetFloat(GL11.GL_POINT_SIZE));
					GL11.glGetFloat(GL11.GL_CURRENT_COLOR, currentColor);
					assertEquals(0.2f, currentColor.get(0), 0.0001f);
					assertEquals(0.8f, currentColor.get(3), 0.0001f);
					GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
					assertTrue(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
					assertEquals(24, clip.get(2));
					assertEquals(24, clip.get(3));
				}
			}
			Method withState = BattleNext_GeometryRenderer.class.getDeclaredMethod("withState", Runnable.class);
			withState.setAccessible(true);
			IllegalStateException failure = new IllegalStateException("Injected geometry callback failure");
			InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> withState.invoke(null, (Runnable) () -> {
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ZERO);
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
				throw failure;
			}));
			assertSame(failure, thrown.getCause());
			assertTrue(GL11.glIsEnabled(GL11.GL_BLEND));
			assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL11.GL_BLEND_SRC));
			assertEquals(GL11.GL_ONE, GL11.glGetInteger(GL11.GL_BLEND_DST));
			assertEquals(GL11.GL_ZERO, GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
			assertEquals(GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
			assertEquals(callerBuffer, GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
			GL15.glDeleteBuffers(callerBuffer);
			assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
		} finally {
			initialized.setBoolean(null, false);
			buffer.destroy();
			if (previousLibraryPath == null) System.clearProperty("org.lwjgl.librarypath");
			else System.setProperty("org.lwjgl.librarypath", previousLibraryPath);
		}
	}
}
