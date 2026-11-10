package data.scripts.tools;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.PositionAPI;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class BattleNext_GeometryRenderer {

	private static final int INITIAL_CAPACITY = 128;
	private static final FloatBuffer TEXTURE_UV = BufferUtils.createFloatBuffer(8);
	private static final FloatBuffer GEOMETRY_DATA = BufferUtils.createFloatBuffer(INITIAL_CAPACITY);
	private static final IntBuffer CLIP_BOX = BufferUtils.createIntBuffer(16);
	private static FloatBuffer batchPositions = BufferUtils.createFloatBuffer(INITIAL_CAPACITY * 2);
	private static FloatBuffer batchColors = BufferUtils.createFloatBuffer(INITIAL_CAPACITY * 4);
	private static int batchDepth;
	private static int batchMode;
	private static float batchWidth;
	private static int batchBuffer;

	private static int positionVbo;
	private static int uvVbo;
	private static boolean initialized;

	private BattleNext_GeometryRenderer() {

	}

	public static void fillRect(PositionAPI position, Color color, float alpha) {
		fillRect(position.getX(), position.getY(), position.getWidth(), position.getHeight(), color, alpha);
	}

	public static void fillRect(float x, float y, float width, float height, Color color, float alpha) {
		drawGeometry(new float[]{x, y, x + width, y, x, y + height,
				x + width, y, x + width, y + height, x, y + height}, GL11.GL_TRIANGLES, color, alpha, 1f);
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

		flush();
		ensureInitialized();

		GEOMETRY_DATA.clear();
		GEOMETRY_DATA.put(x).put(y).put(x + width).put(y).put(x).put(y + height)
				.put(x + width).put(y + height).flip();
		TEXTURE_UV.clear();
		TEXTURE_UV.put(0f).put(0f).put(maxU).put(0f).put(0f).put(maxV).put(maxU).put(maxV).flip();

		withState(() -> {
			upload(positionVbo, GEOMETRY_DATA);
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
		flush();

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
		flush();

		float xScale = Global.getSettings().getScreenWidthPixels()
				/ Math.max(1f, Global.getSettings().getScreenWidth());
		float yScale = Global.getSettings().getScreenHeightPixels()
				/ Math.max(1f, Global.getSettings().getScreenHeight());

		IntBuffer box = CLIP_BOX;
		box.clear();
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
		flush();
		if (state.enabled) {
			GL11.glEnable(GL11.GL_SCISSOR_TEST);
		} else {
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
		}
		GL11.glScissor(state.x, state.y, state.width, state.height);
	}

	private static void drawGeometry(float[] vertices, int mode, Color color, float alpha, float lineWidth) {
		boolean immediate = batchDepth == 0;
		if (immediate) beginBatch();
		if (mode == GL11.GL_LINE_LOOP || mode == GL11.GL_LINE_STRIP) {
			int segments = vertices.length / 2 - (mode == GL11.GL_LINE_STRIP ? 1 : 0);
			for (int index = 0; index < segments; index++) {
				int next = (index + 1) % (vertices.length / 2);
				append(new float[]{vertices[index * 2], vertices[index * 2 + 1], vertices[next * 2], vertices[next * 2 + 1]},
						GL11.GL_LINES, color, alpha, lineWidth);
			}
		} else append(vertices, mode, color, alpha, lineWidth);
		if (immediate) endBatch();
	}

	public static void beginBatch() {
		if (batchDepth++ > 0) return;
		batchBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POINT_BIT | GL11.GL_COLOR_BUFFER_BIT);
		GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
	}

	public static void endBatch() {
		if (--batchDepth > 0) return;
		flush();
		GL11.glPopClientAttrib();
		GL11.glPopAttrib();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, batchBuffer);
	}

	private static void append(float[] vertices, int mode, Color color, float alpha, float width) {
		if (batchPositions.position() > 0 && (mode != batchMode || width != batchWidth)) flush();

		batchMode = mode;
		batchWidth = width;
		if (batchPositions.remaining() < vertices.length) {
			int capacity = Math.max(batchPositions.capacity() * 2, batchPositions.position() + vertices.length);
			FloatBuffer positions = BufferUtils.createFloatBuffer(capacity);
			FloatBuffer colors = BufferUtils.createFloatBuffer(capacity * 2);
			batchPositions.flip();
			batchColors.flip();
			positions.put(batchPositions);
			colors.put(batchColors);
			batchPositions = positions;
			batchColors = colors;
		}

		batchPositions.put(vertices);
		for (int index = 0; index < vertices.length / 2; index++) {
			batchColors.put(color.getRed() / 255f).put(color.getGreen() / 255f).put(color.getBlue() / 255f)
					.put(color.getAlpha() / 255f * alpha);
		}
	}

	public static void flush() {
		if (batchPositions.position() == 0) return;
		batchPositions.flip();
		batchColors.flip();

		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glLineWidth(batchWidth);
		GL11.glPointSize(batchWidth);
		GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
		GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
		GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
		GL11.glVertexPointer(2, 0, batchPositions);
		GL11.glColorPointer(4, 0, batchColors);

		GL11.glDrawArrays(batchMode, 0, batchPositions.remaining() / 2);

		batchPositions.clear();
		batchColors.clear();
	}

	private static void ensureInitialized() {
		if (initialized) return;
		int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
		positionVbo = GL15.glGenBuffers();
		uvVbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionVbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, GEOMETRY_DATA.capacity() * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvVbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, TEXTURE_UV.capacity() * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
		initialized = true;
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
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_POINT_BIT
				| GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
		GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
		try {
			draw.run();
		} finally {
			GL11.glPopClientAttrib();
			GL11.glPopAttrib();
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
		}
	}

	public record GeometryClipState(boolean enabled, int x, int y, int width, int height) {

	}
}
