package data.test.fixture;

import data.scripts.tools.BattleNext_GeometryRenderer;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class BattleNext_TestGlContext implements AutoCloseable {

	private final Pbuffer buffer;
	private final String previousLibraryPath;
	private final Map<Field, Object> rendererState = new LinkedHashMap<>();

	public BattleNext_TestGlContext() throws Exception {

		previousLibraryPath = System.getProperty("org.lwjgl.librarypath");
		System.setProperty("org.lwjgl.librarypath", Path.of("../../starsector-core/native/windows")
				.toAbsolutePath().normalize().toString());
		Pbuffer created = null;
		try {
			created = new Pbuffer(800, 800, new PixelFormat(), null, null);
			created.makeCurrent();
			for (String name : new String[]{"initialized", "positionVbo", "uvVbo", "geometryCapacity", "geometryData"}) {
				Field field = BattleNext_GeometryRenderer.class.getDeclaredField(name);
				field.setAccessible(true);
				rendererState.put(field, field.get(null));
			}
			for (Field field : rendererState.keySet()) {
				switch (field.getName()) {
					case "initialized" -> field.setBoolean(null, false);
					case "positionVbo", "uvVbo" -> field.setInt(null, 0);
					case "geometryData" -> {
						FloatBuffer saved = (FloatBuffer) rendererState.get(field);
						FloatBuffer working = BufferUtils.createFloatBuffer(saved.capacity());
						FloatBuffer contents = saved.duplicate();
						contents.clear();
						working.put(contents);
						working.limit(saved.limit());
						working.position(saved.position());
						field.set(null, working);
					}
				}
			}
			GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
			GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT | GL11.GL_CLIENT_PIXEL_STORE_BIT);
			buffer = created;
		} catch (Exception | Error failure) {
			try {
				for (var entry : rendererState.entrySet()) {
					entry.getKey().set(null, entry.getValue());
				}
			} finally {
				try {
					if (created != null) {
						created.destroy();
					}
				} finally {
					restoreLibraryPath();
				}
			}
			throw failure;
		}
	}

	public void reset(int width, int height, float scale) throws Exception {

		buffer.makeCurrent();
		GL11.glPopClientAttrib();
		GL11.glPopAttrib();
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT | GL11.GL_CLIENT_PIXEL_STORE_BIT);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL11.glMatrixMode(GL11.GL_TEXTURE);
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glOrtho(0, width / scale, 0, height / scale, -1, 1);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
		GL11.glViewport(0, 0, width, height);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
	}

	public State captureState() {
		return new State();
	}

	@Override
	public void close() throws Exception {

		try {
			buffer.makeCurrent();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			for (Field field : rendererState.keySet()) {
				if (field.getName().equals("positionVbo") || field.getName().equals("uvVbo")) {
					GL15.glDeleteBuffers(field.getInt(null));
				}
			}
		} finally {
			try {
				for (var entry : rendererState.entrySet()) {
					entry.getKey().set(null, entry.getValue());
				}
			} finally {
				try {
					buffer.destroy();
				} finally {
					restoreLibraryPath();
				}
			}
		}
	}

	private void restoreLibraryPath() {
		if (previousLibraryPath == null) System.clearProperty("org.lwjgl.librarypath");
		else System.setProperty("org.lwjgl.librarypath", previousLibraryPath);
	}

	public static class State {
		private static final int[] INTEGER_PARAMETERS = {
				GL11.GL_MATRIX_MODE, GL15.GL_ARRAY_BUFFER_BINDING, GL11.GL_TEXTURE_BINDING_2D,
				GL11.GL_BLEND_SRC, GL11.GL_BLEND_DST, GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA,
				GL14.GL_BLEND_EQUATION, GL11.GL_ATTRIB_STACK_DEPTH, GL11.GL_CLIENT_ATTRIB_STACK_DEPTH,
				GL11.GL_MODELVIEW_STACK_DEPTH, GL11.GL_PROJECTION_STACK_DEPTH, GL11.GL_TEXTURE_STACK_DEPTH
		};
		private static final int[] ENABLE_PARAMETERS = {
				GL11.GL_BLEND, GL11.GL_SCISSOR_TEST, GL11.GL_TEXTURE_2D, GL11.GL_LINE_SMOOTH,
				GL11.GL_POINT_SMOOTH, GL11.GL_VERTEX_ARRAY, GL11.GL_TEXTURE_COORD_ARRAY
		};
		private static final int[] FLOAT_PARAMETERS = {GL11.GL_LINE_WIDTH, GL11.GL_POINT_SIZE};
		private static final int[] INTEGER_VECTORS = {GL11.GL_VIEWPORT, GL11.GL_SCISSOR_BOX};
		private static final int[] FLOAT_VECTORS = {
				GL11.GL_CURRENT_COLOR, GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX
		};
		private final int[] integers = new int[INTEGER_PARAMETERS.length];
		private final boolean[] enabled = new boolean[ENABLE_PARAMETERS.length];
		private final float[] floats = new float[FLOAT_PARAMETERS.length];
		private final int[][] integerVectors = new int[INTEGER_VECTORS.length][4];
		private final float[][] floatVectors = new float[FLOAT_VECTORS.length][];

		private State() {

			for (int index = 0; index < integers.length; index++) {
				integers[index] = GL11.glGetInteger(INTEGER_PARAMETERS[index]);
			}
			for (int index = 0; index < enabled.length; index++) {
				enabled[index] = GL11.glIsEnabled(ENABLE_PARAMETERS[index]);
			}
			for (int index = 0; index < floats.length; index++) {
				floats[index] = GL11.glGetFloat(FLOAT_PARAMETERS[index]);
			}
			IntBuffer integerValues = BufferUtils.createIntBuffer(16);
			for (int index = 0; index < integerVectors.length; index++) {
				integerValues.clear();
				GL11.glGetInteger(INTEGER_VECTORS[index], integerValues);
				integerValues.get(integerVectors[index]);
			}
			FloatBuffer floatValues = BufferUtils.createFloatBuffer(16);
			for (int index = 0; index < floatVectors.length; index++) {
				floatValues.clear();
				GL11.glGetFloat(FLOAT_VECTORS[index], floatValues);
				floatVectors[index] = new float[index == 0 ? 4 : 16];
				floatValues.get(floatVectors[index]);
			}
		}

		public void assertRestored() {

			State actual = new State();
			assertArrayEquals(integers, actual.integers, "Caller bindings, blend and stack ownership");
			assertArrayEquals(enabled, actual.enabled, "Caller server and client enables");
			assertArrayEquals(floats, actual.floats, 0.0001f, "Caller line and point sizes");
			for (int index = 0; index < integerVectors.length; index++) {
				assertArrayEquals(integerVectors[index], actual.integerVectors[index], "Caller viewport and clip");
			}
			for (int index = 0; index < floatVectors.length; index++) {
				assertArrayEquals(floatVectors[index], actual.floatVectors[index], 0.0001f, "Caller color and matrices");
			}
		}
	}
}
