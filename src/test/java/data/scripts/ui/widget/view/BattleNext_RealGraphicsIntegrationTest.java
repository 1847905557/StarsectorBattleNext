package data.scripts.ui.widget.view;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.model.tournament.BattleNext_TournamentModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.tournament.BattleNext_StartedTournament;
import data.scripts.tournament.BattleNext_StartedTournament.ExecutionKind;
import data.scripts.tournament.BattleNext_TournamentEngine;
import data.scripts.ui.BattleNext_UiInputRouter;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_ExpandablePanel;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_VariantDetails;
import data.scripts.ui.widget.BattleNext_PanelBasedWidget.VisibleRows;
import data.test.fixture.BattleNext_TestGlContext;
import data.test.fixture.BattleNext_TestGlContext.State;
import data.test.fixture.BattleNext_TestTournamentViewFixture;
import data.test.fixture.BattleNext_TestTournamentViewFixture.Harness;
import data.test.fixture.BattleNext_TestVariantFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;

import java.awt.Color;
import java.awt.datatransfer.Clipboard;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Execution(ExecutionMode.SAME_THREAD)
class BattleNext_RealGraphicsIntegrationTest extends BattleNext_TestVariantFixture {

	private static final Color FOREGROUND = new Color(175, 175, 175, 180);
	private static final Color BACKGROUND = new Color(40, 60, 80);
	private static BattleNext_TestGlContext context;

	@Test
	void virtualRowsFillEveryVisiblePixelAfterScrollJumpsAtDrawTime() throws Exception {
		context.reset(100, 100, 1f);
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (Set.of("getScreenWidth", "getScreenHeight", "getScreenWidthPixels", "getScreenHeightPixels")
					.contains(method.getName())) return 100f;
			try {
				return method.invoke(previous, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		Map<Integer, CustomPanelAPI> mounted = new LinkedHashMap<>();
		VisibleRows[] holder = new VisibleRows[1];
		try (VisibleRows rows = new VisibleRows(1000, index -> 20f, index -> {
			CustomPanelAPI canvas = holder[0].getPanel();
			CustomPanelAPI[] row = new CustomPanelAPI[1];
			row[0] = canvas.createCustomPanel(100f, 20f, new BaseCustomUIPanelPlugin() {
				@Override
				public void renderBelow(float alphaMult) {
					BattleNext_GeometryRenderer.fillRect(row[0].getPosition(), index % 2 == 0 ? Color.GREEN : Color.BLUE, alphaMult);
				}
			});
			mounted.put(index, row[0]);
			canvas.addComponent(row[0]).inTL(0f, 8f + index * 20f);
		}, index -> holder[0].getPanel().removeComponent(mounted.remove(index)), ignored -> false)) {
			holder[0] = rows;
			CustomPanelAPI owner = panel(100f, 100f, new BaseCustomUIPanelPlugin());
			TooltipMakerAPI viewport = owner.createUIElement(100f, 80f, true);
			rows.attach(owner, viewport, 100f, 8f);
			rows.getPanel().getPosition().setSize(100f, rows.height() + 16f);
			viewport.setHeightSoFar(rows.height() + 16f);
			owner.addUIElement(viewport).inTL(0f, 0f);
			ByteBuffer pixels = BufferUtils.createByteBuffer(100 * 100 * 4);
			for (float offset : new float[]{0f, 12000f, 4000f, 19800f, 0f}) {
				rows.update(0f, 80f);
				viewport.getExternalScroller().setYOffset(offset);
				GL11.glDisable(GL11.GL_SCISSOR_TEST);
				GL11.glClearColor(1f, 0f, 1f, 1f);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				State caller = context.captureState();
				rows.getPanel().getPlugin().renderBelow(1f);
				try {
					for (CustomPanelAPI row : mounted.values()) row.getPlugin().renderBelow(1f);
				} finally {
					rows.getPanel().getPlugin().render(1f);
				}
				caller.assertRestored();
				GL11.glReadPixels(0, 0, 100, 100, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
				for (int y = 20; y < 100; y++) {
					float contentY = 100f - y - 0.5f + offset - 8f;
					Color expected = contentY < 0f ? Color.MAGENTA : ((int) (contentY / 20f)) % 2 == 0 ? Color.GREEN : Color.BLUE;
					int pixel = (y * 100 + 10) * 4;
					assertEquals(expected.getRed(), pixels.get(pixel) & 255, "Red at scroll=" + offset + ", y=" + y);
					assertEquals(expected.getGreen(), pixels.get(pixel + 1) & 255, "Green at scroll=" + offset + ", y=" + y);
					assertEquals(expected.getBlue(), pixels.get(pixel + 2) & 255, "Blue at scroll=" + offset + ", y=" + y);
				}
				assertTrue(mounted.size() < 1000);
			}
			assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
		} finally {
			Global.setSettings(previous);
		}
		assertTrue(mounted.isEmpty());
	}

	@Test
	void wholeCardClipsItsChildrenToTheOuterScrollingViewport() throws Exception {

		context.reset(100, 100, 1f);
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, args) -> {
			if (Set.of("getScreenWidth", "getScreenHeight", "getScreenWidthPixels", "getScreenHeightPixels")
					.contains(method.getName())) return 100f;
			try {
				return method.invoke(previous, args);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		try {
			CustomPanelAPI owner = panel(100f, 100f, new BaseCustomUIPanelPlugin());
			TooltipMakerAPI viewport = owner.createUIElement(100f, 30f, true);
			CustomPanelAPI row = owner.createCustomPanel(100f, 144f, new BaseCustomUIPanelPlugin());
			TooltipMakerAPI content = row.createUIElement(100f, 0f, false);
			BattleNext_ExpandablePanel.Header header = new BattleNext_ExpandablePanel.Header(
					null, false, "", "Alpha", "", "", "", null);
			BattleNext_ExpandablePanel.add(content, viewport, row, 100f, true, 100f, header,
					() -> {}, ignored -> {}, body -> {});
			row.addUIElement(content).inTL(0f, 0f);
			viewport.addCustomDoNotSetPosition(row).getPosition().inTL(0f, 0f);
			viewport.setHeightSoFar(144f);
			owner.addUIElement(viewport).inTL(0f, 0f);
			CustomPanelAPI card = (CustomPanelAPI) tooltips.get(content).getComponents().get(0);
			CustomPanelAPI top = (CustomPanelAPI) children.get(card).get(0);
			CustomPanelAPI body = (CustomPanelAPI) children.get(card).get(1);
			ByteBuffer pixel = BufferUtils.createByteBuffer(4);
			for (boolean parentClip : List.of(false, true)) {
				GL11.glDisable(GL11.GL_SCISSOR_TEST);
				GL11.glClearColor(1f, 0f, 1f, 1f);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				if (parentClip) {
					GL11.glEnable(GL11.GL_SCISSOR_TEST);
					GL11.glScissor(0, 0, 30, 100);
				}
				State caller = context.captureState();
				card.getPlugin().renderBelow(1f);
				try {
					top.getPlugin().renderBelow(1f);
					body.getPlugin().renderBelow(1f);
					BattleNext_GeometryRenderer.fillRect(20f, 50f, 20f, 40f, Color.WHITE, 1f);
					top.getPlugin().render(1f);
				} finally {
					card.getPlugin().render(1f);
				}
				caller.assertRestored();
				for (int[] sample : new int[][]{{10, 60}, {25, 60}, {25, 80}, {35, 80}}) {
					GL11.glReadPixels(sample[0], sample[1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
					int expectedGreen = sample[1] == 80 && (!parentClip || sample[0] < 30) ? 255 : 0;
					assertEquals(255, pixel.get(0) & 255);
					assertEquals(expectedGreen, pixel.get(1) & 255);
					assertEquals(255, pixel.get(2) & 255);
				}
			}
			assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
		} finally {
			Global.setSettings(previous);
		}
	}

	@Test
	void fullscreenTextureFlushesEarlierGeometryBeforeChangingProjectionAndViewport() throws Exception {

		context.reset(32, 32, 1f);
		GL11.glClearColor(0f, 0f, 0f, 1f);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
		int texture = GL11.glGenTextures();
		try {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			ByteBuffer black = BufferUtils.createByteBuffer(4);
			black.put(new byte[]{0, 0, 0, (byte) 255}).flip();
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, black);

			State caller = context.captureState();
			BattleNext_GeometryRenderer.beginBatch();
			try {
				BattleNext_GeometryRenderer.fillRect(20f, 20f, 8f, 8f, Color.WHITE, 1f);
				BattleNext_GeometryRenderer.texturedFullscreen(texture, 1f, 1f, 16, 16);
			} finally {
				BattleNext_GeometryRenderer.endBatch();
			}

			ByteBuffer pixel = BufferUtils.createByteBuffer(4);
			GL11.glReadPixels(24, 24, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
			assertEquals(255, pixel.get(0) & 255);
			GL11.glReadPixels(8, 8, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
			assertEquals(0, pixel.get(0) & 255);
			caller.assertRestored();
		} finally {
			GL11.glDeleteTextures(texture);
		}
	}

	@BeforeAll
	static void createContext() throws Exception {
		context = new BattleNext_TestGlContext();
	}

	@AfterAll
	static void releaseContext() throws Exception {
		if (context != null) {
			context.close();
			context = null;
		}
	}

	@Test
	void primitivesCompositeTheirColorAlphaAndRestoreCallerStateIncludingOnFailure() throws Exception {

		context.reset(32, 32, 1f);
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(0, 0, 24, 24);
		int callerBuffer = GL15.glGenBuffers();
		try {
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
					State callerState = context.captureState();
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
					callerState.assertRestored();
				}
			}

			Method withState = BattleNext_GeometryRenderer.class.getDeclaredMethod("withState", Runnable.class);
			withState.setAccessible(true);
			IllegalStateException failure = new IllegalStateException("Injected geometry callback failure");
			State failureState = context.captureState();
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
			failureState.assertRestored();
		} finally {
			GL15.glDeleteBuffers(callerBuffer);
		}
		assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
	}

	@Test
	void realOpenGlCaretFollowsEditabilityFocusVisibilityAndBlink() throws Exception {

		context.reset(320, 64, 1f);
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		List<String> sounds = new ArrayList<>();
		Clipboard clipboard = new Clipboard("test");
		CustomPanelAPI owner = panel(300f, 28f, new BaseCustomUIPanelPlugin());
		TooltipMakerAPI host = owner.createUIElement(300f, 28f, false);
		BattleNext_TextField field = new BattleNext_TextField(router, owner, 300f, 28f, host);
		setField(field, "soundSink", (Consumer<String>) sounds::add);
		setField(field, "clipboardSupplier", (Supplier<Clipboard>) () -> clipboard);
		field.attach(host, owner);
		owner.addUIElement(host).inTL(0f, 0f);
		field.setText("abc");
		field.setColor(Color.WHITE);
		field.grabFocus(false);
		Field panelField = BattleNext_TextField.class.getDeclaredField("panel");
		panelField.setAccessible(true);
		CustomPanelAPI fieldPanel = (CustomPanelAPI) panelField.get(field);
		CustomUIPanelPlugin plugin = plugins.get(fieldPanel);
		try {
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glClearColor(1f, 0f, 1f, 1f);
			int horizontal = Math.round(field.getPosition().getX() + 3f
					+ field.getTextLabelAPI().computeTextWidth("abc"));
			int vertical = Math.round(field.getPosition().getY() + 14f);
			ByteBuffer pixel = BufferUtils.createByteBuffer(4);
			for (int phase = 0; phase < 7; phase++) {
				if (phase == 1) field.setEditable(false);
				if (phase == 2) field.setEditable(true);
				if (phase == 3) field.hideCursor();
				if (phase == 4) {
					field.showCursor();
					plugin.advance(0.6f);
				}
				if (phase == 5) field.grabFocus(false);
				if (phase == 6) router.getSession().finish(true);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				State callerState = context.captureState();
				plugin.render(1f);
				callerState.assertRestored();
				GL11.glReadPixels(horizontal, vertical, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
				boolean visible = phase == 0 || phase == 2 || phase == 5;
				assertEquals(255, pixel.get(0) & 255);
				assertEquals(visible ? 255 : 0, pixel.get(1) & 255, "Caret phase " + phase);
				assertEquals(255, pixel.get(2) & 255);
				assertEquals("abc", field.getText());
			}
			assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
		} finally {
			field.close();
			router.clear();
		}
	}

	@Test
	void realOpenGlKeepsTheTileHollowAndItsBorderTwoPixelsAtEachUiScale() throws Exception {

		Set<String> failedImages = new HashSet<>();
		shortenLabels = true;
		paragraphLineHeight = 15f;
		Variant originalVariant = source();
		JSONObject snapshot = BattleNext_VariantBuilder.fromVariant(originalVariant.getApi()).toJson();
		ShipVariantAPI registeredModule = originalVariant.getModules().get("module");
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SettingsAPI.class}, (instance, method, arguments) -> {
			if (method.getName().equals("getVariant") && arguments[0].equals("registered_module")) return registeredModule;
			if (method.getName().equals("getSprite") && arguments.length == 1
					&& failedImages.contains(arguments[0].toString())) {
				throw new IllegalArgumentException("Missing image: " + arguments[0]);
			}
			try {
				return method.invoke(previous, arguments);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
		}));
		BattleNext_VariantDetails details = new BattleNext_VariantDetails(() -> {});
		details.setVariantJson(snapshot);
		details.attach(panel(1000f, 601f, new BaseCustomUIPanelPlugin()));
		CustomPanelAPI row = (CustomPanelAPI) tooltips.get(lists(details).get("modules")).getComponents().get(0);
		CustomPanelAPI card = (CustomPanelAPI) children.get(row).get(0);
		CustomUIPanelPlugin plugin = plugins.get(card);
		PositionAPI bounds = position(120f, 120f);
		bounds.inTL(32f, -32f);
		setField(plugin, "position", bounds);
		try {
			for (float scale : new float[]{1f, 1.5f, 2f}) {
				context.reset(384, 384, scale);
				GL11.glDisable(GL11.GL_SCISSOR_TEST);
				GL11.glDisable(GL11.GL_LINE_SMOOTH);
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glClearColor(1f, 0f, 1f, 1f);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				GL11.glLineWidth(7f);
				State callerState = context.captureState();
				plugin.renderBelow(1f);
				callerState.assertRestored();
				assertEquals(7f, GL11.glGetFloat(GL11.GL_LINE_WIDTH));
				ByteBuffer pixels = BufferUtils.createByteBuffer(384 * 384 * 4);
				GL11.glReadPixels(0, 0, 384, 384, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
				int center = Math.round(92f * scale);
				int edge = Math.round(32f * scale);
				assertEquals(List.of(255, 0, 255), pixel(pixels, center, center));
				float borderAlpha = BattleNext_UiTheme.PANEL.getAlpha() / 255f;
				List<Integer> expectedBorder = List.of(
						Math.round(BattleNext_UiTheme.PANEL.getRed() * borderAlpha + 255f * (1f - borderAlpha)),
						Math.round(BattleNext_UiTheme.PANEL.getGreen() * borderAlpha),
						Math.round(BattleNext_UiTheme.PANEL.getBlue() * borderAlpha + 255f * (1f - borderAlpha)));
				int borderPixels = 0;
				for (int horizontal = edge - 4; horizontal <= edge + 4; horizontal++) {
					List<Integer> color = pixel(pixels, horizontal, center);
					if (!color.equals(List.of(255, 0, 255))) {
						for (int channel = 0; channel < 3; channel++) {
							assertEquals(expectedBorder.get(channel).intValue(), color.get(channel).intValue(), 1);
						}
						borderPixels++;
					}
				}
				assertEquals(2, borderPixels);

				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				GL11.glEnable(GL11.GL_SCISSOR_TEST);
				GL11.glScissor(edge + 3, 0, 384 - edge - 3, 384);
				callerState = context.captureState();
				plugin.renderBelow(1f);
				callerState.assertRestored();
				assertTrue(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
				GL11.glReadPixels(0, 0, 384, 384, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
				assertEquals(List.of(255, 0, 255), pixel(pixels, edge, center));
				assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
			}
		} finally {
			details.close();
		}
	}

	@Test
	void realOpenGlDrawsVisibleNodeAndRestoresParentClip() throws Exception {

		context.reset(800, 800, 1f);
		Global.setSettings(BattleNext_TestTournamentViewFixture.settings());
		GL11.glClearColor(1, 0, 1, 1);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(0, 0, 800, 800);
		Harness harness = new Harness();
		BattleNext_TournamentModel cup = new BattleNext_TournamentModel("cup", "Cup",
				BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
		List<String> fleetIds = List.of("F1", "F2");
		BattleNext_TournamentEngine engine = new BattleNext_TournamentEngine();
		BattleNext_TournamentView chart = BattleNext_TournamentView.createView(cup, null, fleetIds,
				Map.of("F1", "Fleet F1", "F2", "Fleet F2"));
		BattleNext_UiInputRouter router = new BattleNext_UiInputRouter();
		chart.attach(harness.getPanel(), harness.getHost(), harness.getViewport().getApi(), router, 700f);
		try {
			State callerState = context.captureState();
			chart.renderBelow(1f);
			chart.render(1f);
			callerState.assertRestored();
			IntBuffer clip = BufferUtils.createIntBuffer(16);
			GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
			assertEquals(800, clip.get(2));
			assertEquals(800, clip.get(3));
			ByteBuffer pixels = BufferUtils.createByteBuffer(800 * 800 * 4);
			GL11.glReadPixels(0, 0, 800, 800, GL11.GL_RGBA,
					GL11.GL_UNSIGNED_BYTE, pixels);
			for (int x : new int[]{0, 1, 2, 100, 699, 700}) {
				int offset = (100 * 800 + x) * 4;
				Color expected = x == 0 || x == 1 || x == 699 ? BattleNext_UiTheme.ACCENT
						: x == 700 ? Color.MAGENTA : Color.BLACK;

				assertEquals(expected.getRed(), pixels.get(offset) & 255, "Frame red at x=" + x);
				assertEquals(expected.getGreen(), pixels.get(offset + 1) & 255, "Frame green at x=" + x);
				assertEquals(expected.getBlue(), pixels.get(offset + 2) & 255, "Frame blue at x=" + x);
			}

			BattleNext_TournamentView.Node node = chart.nodes.get(0);
			float[] center = chart.project(node.x() + node.width() * 0.5f, node.y() + node.height() * 0.5f);
			int centerOffset = ((int) center[1] * 800 + (int) center[0]) * 4;
			assertEquals(0, pixels.get(centerOffset) & 255);
			assertEquals(0, pixels.get(centerOffset + 1) & 255);
			assertEquals(0, pixels.get(centerOffset + 2) & 255);
			int nodePixels = 0;
			for (int y = 40; y < 340; y++) {
				for (int x = 100; x < 600; x++) {
					int offset = (y * 800 + x) * 4;
					int red = pixels.get(offset) & 255;
					int green = pixels.get(offset + 1) & 255;
					int blue = pixels.get(offset + 2) & 255;
					if (red > 0 && red == green && green == blue) {
						nodePixels++;
					}
				}
			}
			assertTrue(nodePixels >= 100, "Visible node outline pixels: " + nodePixels);

			BattleNext_TournamentView.ViewState camera = chart.captureState();
			PositionAPI frame = harness.getChartBounds().getApi();
			float left = frame.getX();
			float bottom = frame.getY();
			float right = left + frame.getWidth();
			float top = bottom + frame.getHeight();
			for (float[] target : new float[][]{{left + frame.getWidth() * 0.5f, bottom},
					{left, bottom + frame.getHeight() * 0.5f}, {right, bottom + frame.getHeight() * 0.5f},
					{left + frame.getWidth() * 0.5f, top - 40f}}) {
				chart.restoreState(camera);
				float[] projected = chart.project(node.x() + node.width() * 0.5f, node.y() + node.height() * 0.5f);
				float scale = frame.getWidth() / BattleNext_TournamentView.CANVAS_WIDTH;
				chart.panBy((target[0] - projected[0]) / scale, (projected[1] - target[1]) / scale);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				callerState = context.captureState();
				chart.renderBelow(1f);
				chart.render(1f);
				callerState.assertRestored();
				GL11.glReadPixels(0, 0, 800, 800, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
				for (float[] sample : new float[][]{{left + 1f, bottom + frame.getHeight() * 0.5f},
						{right - 1f, bottom + frame.getHeight() * 0.5f}, {left + frame.getWidth() * 0.5f, bottom + 1f},
						{left + frame.getWidth() * 0.5f, top - 1f}}) {
					int offset = ((int) sample[1] * 800 + (int) sample[0]) * 4;
					assertEquals(BattleNext_UiTheme.ACCENT.getRed(), pixels.get(offset) & 255, "Frame red after panning");
					assertEquals(BattleNext_UiTheme.ACCENT.getGreen(), pixels.get(offset + 1) & 255, "Frame green after panning");
					assertEquals(BattleNext_UiTheme.ACCENT.getBlue(), pixels.get(offset + 2) & 255, "Frame blue after panning");
				}
				GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
				assertEquals(800, clip.get(2));
				assertEquals(800, clip.get(3));
			}

			chart.restoreState(camera);
			BattleNext_StartedTournament started = engine.start(cup, fleetIds, ExecutionKind.MANUAL);
			for (boolean completed : List.of(false, true)) {
				if (completed) {
					engine.recordRound(cup, started, "W1-1", "F1", "");
				}
				chart.update(cup, started, fleetIds, Map.of("F1", "First", "F2", "Second"));
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
				callerState = context.captureState();
				chart.renderBelow(1f);
				chart.render(1f);
				callerState.assertRestored();
				GL11.glReadPixels(0, 0, 800, 800, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
				Color expected = completed ? BattleNext_UiTheme.ACCENT : Misc.getHighlightColor();
				float[] border = chart.project(node.x() + node.width() * 0.5f, node.y());
				boolean found = false;
				for (int y = (int) border[1] - 3; y <= (int) border[1] + 3; y++) {
					for (int x = (int) border[0] - 3; x <= (int) border[0] + 3; x++) {
						int offset = (y * 800 + x) * 4;
						if ((pixels.get(offset) & 255) == expected.getRed()
								&& (pixels.get(offset + 1) & 255) == expected.getGreen()
								&& (pixels.get(offset + 2) & 255) == expected.getBlue()) found = true;
					}
				}
				assertTrue(found, "Native border color for completed=" + completed);
				GL11.glGetInteger(GL11.GL_SCISSOR_BOX, clip);
				assertEquals(800, clip.get(2));
				assertEquals(800, clip.get(3));
			}
			assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
		} finally {
			chart.close();
			router.clear();
		}
	}

	private List<Integer> pixel(ByteBuffer pixels, int horizontal, int vertical) {
		int offset = (vertical * 384 + horizontal) * 4;
		return List.of(pixels.get(offset) & 255, pixels.get(offset + 1) & 255, pixels.get(offset + 2) & 255);
	}
}
