package data.scripts.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class BattleNext_GeometryRenderer {

	private static final int INITIAL_CAPACITY = 128;
	private static final int FLOAT_BYTES = 4;
	private static final FloatBuffer TEXTURE_UV = BufferUtils.createFloatBuffer(8);
	private static FloatBuffer geometryData = BufferUtils.createFloatBuffer(INITIAL_CAPACITY);

	private static int positionVbo;
	private static int uvVbo;
	private static int geometryCapacity = INITIAL_CAPACITY;
	private static boolean initialized;

	private BattleNext_GeometryRenderer() {
	}

	public static void fillRect(PositionAPI position, Color color, float alpha) {
		fillRect(position.getX(), position.getY(), position.getWidth(), position.getHeight(), color, alpha);
	}

	public static void fillRect(float x, float y, float width, float height, Color color, float alpha) {
		drawGeometry(new float[]{x, y, x + width, y, x, y + height, x + width, y + height},
				GL11.GL_TRIANGLE_STRIP, color, alpha, 1f);
	}

	public static void lineLoop(float[] vertices, Color color, float alpha, float lineWidth) {
		drawGeometry(vertices, GL11.GL_LINE_LOOP, color, alpha, lineWidth);
	}

	public static void lineStrip(float[] vertices, Color color, float alpha, float lineWidth) {
		drawGeometry(vertices, GL11.GL_LINE_STRIP, color, alpha, lineWidth);
	}

	public static void drawPoint(float x, float y, float size, Color color, float alpha) {
		drawGeometry(new float[]{x, y}, GL11.GL_POINTS, color, alpha, size);
	}

	public static void texturedQuad(int texture, float x, float y, float width, float height,
			float maxU, float maxV, float red, float green, float blue, float alpha) {
		ensureInitialized();
		geometryData.clear();
		geometryData.put(x).put(y).put(x + width).put(y).put(x).put(y + height)
				.put(x + width).put(y + height).flip();
		TEXTURE_UV.clear();
		TEXTURE_UV.put(0f).put(0f).put(maxU).put(0f).put(0f).put(maxV).put(maxU).put(maxV).flip();
		withState(() -> {
			upload(positionVbo, geometryData);
			upload(uvVbo, TEXTURE_UV);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glColor4f(red, green, blue, alpha);
			bindArrays(true);
			GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
		});
	}

	public static void texturedFullscreen(int texture, float maxU, float maxV,
			int viewportWidth, int viewportHeight) {
		GL11.glPushAttrib(GL11.GL_TRANSFORM_BIT | GL11.GL_VIEWPORT_BIT | GL11.GL_ENABLE_BIT);
		GL11.glViewport(0, 0, viewportWidth, viewportHeight);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glDisable(GL11.GL_BLEND);
		texturedQuad(texture, -1f, -1f, 2f, 2f, maxU, maxV, 0.5f, 0.5f, 0.5f, 1f);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glPopAttrib();
	}

	public static GeometryClipState pushClip(PositionAPI position) {
		return pushClip(position.getX(), position.getY(), position.getWidth(), position.getHeight());
	}

	public static GeometryClipState pushClip(float x, float y, float width, float height) {
		float xScale = Global.getSettings().getScreenWidthPixels()
				/ Math.max(1f, Global.getSettings().getScreenWidth());
		float yScale = Global.getSettings().getScreenHeightPixels()
				/ Math.max(1f, Global.getSettings().getScreenHeight());
		IntBuffer box = BufferUtils.createIntBuffer(16);
		GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box);
		GeometryClipState state = new GeometryClipState(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), box.get(0), box.get(1),
				box.get(2), box.get(3));
		int clipX = Math.round(x * xScale);
		int clipY = Math.round(y * yScale);
		int clipWidth = Math.max(0, Math.round(width * xScale));
		int clipHeight = Math.max(0, Math.round(height * yScale));
		if (state.enabled) {
			int right = Math.min(clipX + clipWidth, state.x + state.width);
			int top = Math.min(clipY + clipHeight, state.y + state.height);
			clipX = Math.max(clipX, state.x);
			clipY = Math.max(clipY, state.y);
			clipWidth = Math.max(0, right - clipX);
			clipHeight = Math.max(0, top - clipY);
		}
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(clipX, clipY, clipWidth, clipHeight);
		return state;
	}

	public static void popClip(GeometryClipState state) {
		if (state.enabled) {
			GL11.glEnable(GL11.GL_SCISSOR_TEST);
		} else {
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
		}
		GL11.glScissor(state.x, state.y, state.width, state.height);
	}

	private static void drawGeometry(float[] vertices, int mode, Color color, float alpha, float lineWidth) {
		ensureInitialized();
		ensureCapacity(vertices.length);
		geometryData.clear();
		geometryData.put(vertices).flip();
		withState(() -> {
			upload(positionVbo, geometryData);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
					color.getAlpha() / 255f * alpha);
			if (mode == GL11.GL_POINTS) GL11.glPointSize(lineWidth);
			if (mode == GL11.GL_LINE_LOOP || mode == GL11.GL_LINE_STRIP) GL11.glLineWidth(lineWidth);
			bindArrays(false);
			GL11.glDrawArrays(mode, 0, vertices.length / 2);
		});
	}

	private static void ensureInitialized() {
		if (initialized) return;
		int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
		positionVbo = GL15.glGenBuffers();
		uvVbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionVbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, INITIAL_CAPACITY * FLOAT_BYTES, GL15.GL_DYNAMIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvVbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 8 * FLOAT_BYTES, GL15.GL_DYNAMIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
		initialized = true;
	}

	private static void ensureCapacity(int required) {
		if (required <= geometryCapacity) return;
		geometryCapacity = Math.max(required, geometryCapacity * 2);
		geometryData = BufferUtils.createFloatBuffer(geometryCapacity);
		int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionVbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, geometryCapacity * FLOAT_BYTES, GL15.GL_DYNAMIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
	}

	private static void upload(int buffer, FloatBuffer data) {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
		GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, data);
	}

	private static void bindArrays(boolean textured) {
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionVbo);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glVertexPointer(2, GL11.GL_FLOAT, 0, 0L);
		if (textured) {
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvVbo);
			GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
			GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0L);
		} else {
			GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		}
	}

	private static void withState(Runnable draw) {
		int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT
				| GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
		GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
		draw.run();
		GL11.glPopClientAttrib();
		GL11.glPopAttrib();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
	}

	public static class GeometryClipState {
		private final boolean enabled;
		private final int x;
		private final int y;
		private final int width;
		private final int height;

		private GeometryClipState(boolean enabled, int x, int y, int width, int height) {
			this.enabled = enabled;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}
}
