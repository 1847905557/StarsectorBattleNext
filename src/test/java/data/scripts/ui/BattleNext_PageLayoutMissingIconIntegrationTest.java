package data.scripts.ui;

import com.fs.starfarer.api.graphics.SpriteAPI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_PageLayoutMissingIconIntegrationTest {
	private static final Color TINT = new Color(30, 60, 90);

	@Test
	void tintedFittedSpriteShrinksAndRestoresColorSizeCenterAndAlpha() {
		Color[] color = {Color.WHITE};
		float[] width = {64f};
		float[] height = {64f};
		float[] centerX = {4f};
		float[] centerY = {5f};
		float[] alpha = {0.8f};
		AtomicInteger renders = new AtomicInteger();
		SpriteAPI sprite = (SpriteAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SpriteAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getColor" -> color[0];
					case "getWidth" -> width[0];
					case "getHeight" -> height[0];
					case "getCenterX" -> centerX[0];
					case "getCenterY" -> centerY[0];
					case "getAlphaMult" -> alpha[0];
					case "setColor" -> { color[0] = (Color) args[0]; yield null; }
					case "setSize" -> {
						width[0] = (Float) args[0];
						height[0] = (Float) args[1];
						yield null;
					}
					case "setCenter" -> {
						centerX[0] = (Float) args[0];
						centerY[0] = (Float) args[1];
						yield null;
					}
					case "setAlphaMult" -> { alpha[0] = (Float) args[0]; yield null; }
					case "renderAtCenter" -> {
						assertEquals(TINT, color[0]);
						assertEquals(32f, width[0]);
						assertEquals(32f, height[0]);
						assertEquals(16f, centerX[0]);
						assertEquals(16f, centerY[0]);
						assertEquals(0.4f, alpha[0]);
						assertEquals(16f, (Float) args[0]);
						assertEquals(20f, (Float) args[1]);
						renders.incrementAndGet();
						yield null;
					}
					default -> null;
				});

		BattleNext_PageLayout.renderTintedFittedSprite(sprite, 16f, 20f, 32f, 32f, TINT, 0.5f);

		assertEquals(1, renders.get());
		assertEquals(Color.WHITE, color[0]);
		assertEquals(64f, width[0]);
		assertEquals(64f, height[0]);
		assertEquals(4f, centerX[0]);
		assertEquals(5f, centerY[0]);
		assertEquals(0.8f, alpha[0]);
	}

	@Test
	void fittedSpriteKeepsNativeSizeCentersBothAxesAndRestoresSharedState() {
		float[] width = {12f};
		float[] height = {24f};
		float[] centerX = {2f};
		float[] centerY = {3f};
		float[] alpha = {0.8f};
		AtomicInteger renders = new AtomicInteger();
		SpriteAPI sprite = (SpriteAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SpriteAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getWidth" -> width[0];
					case "getHeight" -> height[0];
					case "getCenterX" -> centerX[0];
					case "getCenterY" -> centerY[0];
					case "getAlphaMult" -> alpha[0];
					case "setSize" -> {
						width[0] = (Float) args[0];
						height[0] = (Float) args[1];
						yield null;
					}
					case "setCenter" -> {
						centerX[0] = (Float) args[0];
						centerY[0] = (Float) args[1];
						yield null;
					}
					case "setAlphaMult" -> { alpha[0] = (Float) args[0]; yield null; }
					case "renderAtCenter" -> {
						assertEquals(12f, width[0]);
						assertEquals(24f, height[0]);
						assertEquals(6f, centerX[0]);
						assertEquals(12f, centerY[0]);
						assertEquals(0.4f, alpha[0]);
						assertEquals(16f, (Float) args[0]);
						assertEquals(20f, (Float) args[1]);
						renders.incrementAndGet();
						yield null;
					}
					default -> null;
				});

		BattleNext_PageLayout.renderFittedSprite(sprite, 16f, 20f, 32f, 32f, 0.5f);

		assertEquals(1, renders.get());
		assertEquals(12f, width[0]);
		assertEquals(24f, height[0]);
		assertEquals(2f, centerX[0]);
		assertEquals(3f, centerY[0]);
		assertEquals(0.8f, alpha[0]);
	}

	@Test
	void missingIconTintsScalesAndRestoresSharedSprite() {
		Color original = Color.WHITE;
		Color[] color = {original};
		float[] width = {48f};
		float[] height = {48f};
		float[] alpha = {0.8f};
		AtomicInteger renders = new AtomicInteger();
		SpriteAPI sprite = (SpriteAPI) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SpriteAPI.class}, (instance, method, args) -> switch (method.getName()) {
					case "getColor" -> color[0];
					case "getWidth" -> width[0];
					case "getHeight" -> height[0];
					case "getAlphaMult" -> alpha[0];
					case "setColor" -> { color[0] = (Color) args[0]; yield null; }
					case "setSize" -> {
						width[0] = (Float) args[0];
						height[0] = (Float) args[1];
						yield null;
					}
					case "setAlphaMult" -> { alpha[0] = (Float) args[0]; yield null; }
					case "renderAtCenter" -> {
						assertEquals(TINT, color[0]);
						assertEquals(32f, width[0]);
						assertEquals(32f, height[0]);
						assertEquals(0.4f, alpha[0]);
						assertEquals(26f, (Float) args[0]);
						assertEquals(36f, (Float) args[1]);
						renders.incrementAndGet();
						yield null;
					}
					default -> null;
				});

		BattleNext_PageLayout.renderIcon(sprite, 10f, 20f, 32f,
				TINT, 0.5f);

		assertEquals(1, renders.get());
		assertEquals(original, color[0]);
		assertEquals(48f, width[0]);
		assertEquals(48f, height[0]);
		assertEquals(0.8f, alpha[0]);
		assertEquals("ui", BattleNext_PageLayout.MISSING_ICON_CATEGORY);
		assertEquals("checkmark_x", BattleNext_PageLayout.MISSING_ICON_KEY);
	}
}
